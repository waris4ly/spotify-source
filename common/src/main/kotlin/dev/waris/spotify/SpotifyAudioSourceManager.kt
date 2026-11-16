package dev.waris.spotify

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager
import com.sedmelluq.discord.lavaplayer.track.AudioItem
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioReference
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo
import com.sedmelluq.discord.lavaplayer.track.BasicAudioPlaylist
import dev.waris.spotify.api.ApiRouter
import dev.waris.spotify.api.SpotifyApi
import dev.waris.spotify.api.SpotifyHttp
import dev.waris.spotify.api.internal.ClientMetadata
import dev.waris.spotify.api.internal.InternalApi
import dev.waris.spotify.api.lyrics.LrcLibProvider
import dev.waris.spotify.api.lyrics.Lyrics
import dev.waris.spotify.api.lyrics.LyricsQuery
import dev.waris.spotify.api.lyrics.LyricsService
import dev.waris.spotify.api.lyrics.SpotifyLyricsProvider
import dev.waris.spotify.api.lyrics.TrackResolver
import dev.waris.spotify.api.official.OfficialApi
import dev.waris.spotify.auth.AccountProvider
import dev.waris.spotify.auth.AnonymousProvider
import dev.waris.spotify.auth.ClientCredentialsProvider
import dev.waris.spotify.auth.TokenKind
import dev.waris.spotify.auth.TokenManager
import dev.waris.spotify.auth.TokenProvider
import dev.waris.spotify.cache.InflightRequests
import dev.waris.spotify.cache.TtlCache
import dev.waris.spotify.convert.AudioItemFactory
import dev.waris.spotify.enrich.ArtworkColors
import dev.waris.spotify.enrich.CanvasLookup
import dev.waris.spotify.error.SpotifyException
import dev.waris.spotify.link.ShortLinkResolver
import dev.waris.spotify.link.SpotifyLink
import dev.waris.spotify.mirror.TrackMirror
import dev.waris.spotify.model.Base62
import dev.waris.spotify.model.SpotifyId
import java.io.DataInput
import java.io.DataOutput
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import java.util.function.Function
import kotlin.time.Duration.Companion.minutes
import org.apache.http.client.config.RequestConfig
import org.apache.http.impl.client.HttpClientBuilder
import org.slf4j.LoggerFactory

public open class SpotifyAudioSourceManager internal constructor(
    public val options: SpotifyOptions,
    private val httpInterfaceManager: HttpInterfaceManager,
    private val tokenManager: TokenManager,
    internal val apiRouter: ApiRouter,
    private val audioItemFactory: AudioItemFactory,
    internal val trackMirror: TrackMirror,
    internal val metadataCache: TtlCache<String, AudioItem> =
        TtlCache(maxSize = METADATA_CACHE_CAP),
    internal val inflightRequests: InflightRequests<String, AudioItem> = InflightRequests(),
    internal val shortLinkResolver: ShortLinkResolver = ShortLinkResolver(httpInterfaceManager),
    internal val artworkColors: ArtworkColors? = null,
    internal val canvasLookup: CanvasLookup? = null,
    internal val lyricsService: LyricsService? = null,
    internal val enrichmentExecutor: ExecutorService = createEnrichmentExecutor(),
) : AudioSourceManager,
    HttpConfigurable {
    private val log = LoggerFactory.getLogger(SpotifyAudioSourceManager::class.java)
    public var playerManager: AudioPlayerManager? = null

    public fun attach(manager: AudioPlayerManager) {
        playerManager = manager
    }

    public open fun loadLyrics(track: AudioTrack): Lyrics? =
        lyricsService?.load(LyricsQuery.from(track))

    public constructor(options: SpotifyOptions = SpotifyOptions()) : this(
        options,
        createHttpInterfaceManager(options),
    )

    private constructor(
        options: SpotifyOptions,
        httpInterfaceManager: HttpInterfaceManager,
    ) : this(
        options,
        httpInterfaceManager,
        createTokenManager(options, httpInterfaceManager),
    )

    private constructor(
        options: SpotifyOptions,
        httpInterfaceManager: HttpInterfaceManager,
        tokenManager: TokenManager,
    ) : this(
        options,
        httpInterfaceManager,
        tokenManager,
        createApiRouter(options, httpInterfaceManager, tokenManager),
        AudioItemFactory(),
    )

    internal constructor(
        options: SpotifyOptions,
        httpInterfaceManager: HttpInterfaceManager,
        tokenManager: TokenManager,
        apiRouter: ApiRouter,
        audioItemFactory: AudioItemFactory,
    ) : this(
        options = options,
        httpInterfaceManager = httpInterfaceManager,
        tokenManager = tokenManager,
        apiRouter = apiRouter,
        audioItemFactory = audioItemFactory,
        trackMirror =
            createTrackMirror(
                options,
                httpInterfaceManager,
                tokenManager,
                apiRouter,
            ),
        metadataCache = TtlCache(),
        inflightRequests = InflightRequests(),
        shortLinkResolver = ShortLinkResolver(httpInterfaceManager),
        artworkColors =
            if (options.extractColors) {
                ArtworkColors(SpotifyHttp(httpInterfaceManager, tokenManager))
            } else {
                null
            },
        canvasLookup =
            if (options.canvas && options.accountCookie.isNotBlank()) {
                CanvasLookup(SpotifyHttp(httpInterfaceManager, tokenManager))
            } else {
                null
            },
        lyricsService =
            if (options.lyrics) {
                createLyricsService(options, httpInterfaceManager, tokenManager, apiRouter)
            } else {
                null
            },
    )

    override fun getSourceName(): String = "spotify"

    override fun loadItem(
        manager: AudioPlayerManager,
        reference: AudioReference,
    ): AudioItem? {
        this.playerManager = manager
        val rawIdentifier = reference.identifier
        // unwrap short link redirects before checking url patterns
        val resolvedIdentifier =
            if (shortLinkResolver.isShortLink(rawIdentifier)) {
                shortLinkResolver.resolve(rawIdentifier) ?: rawIdentifier
            } else {
                rawIdentifier
            }
        val link = SpotifyLink.parse(resolvedIdentifier)
        if (link == null) {
            return if (isSpotifyIdentifier(rawIdentifier)) {
                AudioReference.NO_TRACK
            } else {
                null
            }
        }

        // bypass cache for search queries so results stay up to date
        if (link !is SpotifyLink.Search) {
            val cached = metadataCache.get(resolvedIdentifier)
            if (cached != null) {
                return cloneAudioItem(cached)
            }
        }

        // share identical concurrent calls so only one network query goes to spotify
        val item =
            inflightRequests.execute(resolvedIdentifier) {
                when (link) {
                    is SpotifyLink.Track -> loadTrack(link)
                    is SpotifyLink.Album -> loadAlbum(link)
                    is SpotifyLink.Playlist -> loadPlaylist(link)
                    is SpotifyLink.Artist -> loadArtist(link)
                    is SpotifyLink.Search -> loadSearch(link)
                    is SpotifyLink.Episode -> loadEpisode(link)
                    is SpotifyLink.Chapter -> loadChapter(link)
                    is SpotifyLink.Show -> loadShow(link)
                    is SpotifyLink.Audiobook -> loadAudiobook(link)
                    is SpotifyLink.Suggest -> loadSuggest(link)
                    is SpotifyLink.Recommendations -> loadRecommendations(link)
                    is SpotifyLink.Similar -> loadSimilar(link)
                }
            }

        val isTruncated = (item as? SpotifyAudioPlaylist)?.truncated == true
        if (item !== AudioReference.NO_TRACK && link !is SpotifyLink.Search && !isTruncated) {
            metadataCache.put(
                resolvedIdentifier,
                item,
                options.metadataCacheMinutes.minutes,
            )
        }

        return cloneAudioItem(item)
    }

    private fun cloneAudioItem(item: AudioItem): AudioItem =
        when (item) {
            is AudioTrack -> item.makeClone()
            is SpotifyAudioPlaylist ->
                SpotifyAudioPlaylist(
                    name = item.name,
                    tracks = item.tracks.map { it.makeClone() },
                    selectedTrack = item.selectedTrack?.makeClone(),
                    isSearchResult = item.isSearchResult,
                    type = item.type,
                    url = item.url,
                    author = item.author,
                    artworkUrl = item.artworkUrl,
                    totalTracks = item.totalTracks,
                    truncated = item.truncated,
                )

            is AudioPlaylist ->
                BasicAudioPlaylist(
                    item.name,
                    item.tracks.map { it.makeClone() },
                    item.selectedTrack?.makeClone(),
                    item.isSearchResult,
                )

            else -> item
        }

    private fun loadTrack(link: SpotifyLink.Track): AudioItem {
        val track = apiRouter.getTrack(link.id) ?: return AudioReference.NO_TRACK
        val audioTrack = audioItemFactory.toAudioTrack(track, this)
        enrichTrack(audioTrack, link.id)
        return audioTrack
    }

    private fun enrichTrack(
        audioTrack: SpotifyAudioTrack,
        id: SpotifyId,
    ) {
        if (artworkColors == null && canvasLookup == null) {
            return
        }

        val canvasFuture =
            if (canvasLookup != null) {
                CompletableFuture.supplyAsync(
                    {
                        try {
                            canvasLookup.fetchCanvasUrl(id)
                        } catch (e: InterruptedException) {
                            Thread.currentThread().interrupt()
                            null
                        }
                    },
                    enrichmentExecutor,
                )
            } else {
                null
            }

        val colorsFuture =
            if (artworkColors != null && !audioTrack.info.artworkUrl.isNullOrBlank()) {
                CompletableFuture.supplyAsync(
                    {
                        try {
                            artworkColors.fetchColors(audioTrack.info.artworkUrl)
                        } catch (e: InterruptedException) {
                            Thread.currentThread().interrupt()
                            null
                        }
                    },
                    enrichmentExecutor,
                )
            } else {
                null
            }

        val deadlineNanos =
            System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(ENRICHMENT_TIMEOUT_MS)

        try {
            if (canvasFuture != null) {
                val remainingMs =
                    TimeUnit.NANOSECONDS.toMillis(
                        (deadlineNanos - System.nanoTime()).coerceAtLeast(0),
                    )
                if (remainingMs > 0) {
                    canvasFuture.get(remainingMs, TimeUnit.MILLISECONDS)?.let {
                        audioTrack.canvasUrl = it
                    }
                } else {
                    log.debug("canvas lookup skipped due to expired deadline for {}", id.value)
                }
            }
        } catch (e: TimeoutException) {
            log.debug("canvas lookup timed out for {}: {}", id.value, e.message)
        } catch (e: Exception) {
            log.debug("canvas lookup failed for {}: {}", id.value, e.message)
        } finally {
            canvasFuture?.cancel(true)
        }

        try {
            if (colorsFuture != null) {
                val remainingMs =
                    TimeUnit.NANOSECONDS.toMillis(
                        (deadlineNanos - System.nanoTime()).coerceAtLeast(0),
                    )
                if (remainingMs > 0) {
                    colorsFuture.get(remainingMs, TimeUnit.MILLISECONDS)?.let { extracted ->
                        val map = mutableMapOf<String, String>()
                        extracted.dark?.let { map["dark"] = it }
                        extracted.light?.let { map["light"] = it }
                        extracted.raw?.let { map["raw"] = it }
                        if (map.isNotEmpty()) {
                            audioTrack.colors = map
                        }
                    }
                } else {
                    log.debug("colors lookup skipped due to expired deadline for {}", id.value)
                }
            }
        } catch (e: TimeoutException) {
            log.debug("colors lookup timed out for {}: {}", id.value, e.message)
        } catch (e: Exception) {
            log.debug("colors lookup failed for {}: {}", id.value, e.message)
        } finally {
            colorsFuture?.cancel(true)
        }
    }

    private fun loadAlbum(link: SpotifyLink.Album): AudioItem {
        val album =
            apiRouter.getAlbum(link.id, options.albumLoadLimit)
                ?: return AudioReference.NO_TRACK
        if (album.tracks.isEmpty()) {
            return AudioReference.NO_TRACK
        }
        return audioItemFactory.toAudioPlaylist(album, this)
    }

    private fun loadPlaylist(link: SpotifyLink.Playlist): AudioItem {
        val playlist =
            try {
                apiRouter.getPlaylist(link.id, options.playlistLoadLimit)
            } catch (e: SpotifyException) {
                throw FriendlyException(
                    "Spotify playlist is private or not found",
                    FriendlyException.Severity.COMMON,
                    e,
                )
            } ?: throw FriendlyException(
                "Spotify playlist is private or not found",
                FriendlyException.Severity.COMMON,
                null,
            )

        return audioItemFactory.toAudioPlaylist(playlist, this)
    }

    private fun loadArtist(link: SpotifyLink.Artist): AudioItem {
        val artist = apiRouter.getArtist(link.id) ?: return AudioReference.NO_TRACK
        if (artist.topTracks.isEmpty()) {
            return AudioReference.NO_TRACK
        }
        return audioItemFactory.toAudioPlaylist(artist, this)
    }

    private fun loadSearch(link: SpotifyLink.Search): AudioItem {
        val tracks = apiRouter.search(link.query, limit = options.searchLimit)
        if (tracks.isEmpty()) {
            return AudioReference.NO_TRACK
        }
        return audioItemFactory.toSearchPlaylist(link.query, tracks, this)
    }

    private fun loadEpisode(link: SpotifyLink.Episode): AudioItem {
        val episode =
            apiRouter.getEpisode(link.id, isChapter = false) ?: return AudioReference.NO_TRACK
        return audioItemFactory.toAudioTrack(episode, this)
    }

    private fun loadChapter(link: SpotifyLink.Chapter): AudioItem {
        val chapter =
            apiRouter.getEpisode(link.id, isChapter = true) ?: return AudioReference.NO_TRACK
        return audioItemFactory.toAudioTrack(chapter, this)
    }

    private fun loadShow(link: SpotifyLink.Show): AudioItem {
        val show =
            apiRouter.getShow(link.id, isAudiobook = false, limit = options.showLoadLimit)
                ?: return AudioReference.NO_TRACK
        if (show.episodes.isEmpty()) {
            return AudioReference.NO_TRACK
        }
        return audioItemFactory.toAudioPlaylist(show, this)
    }

    private fun loadAudiobook(link: SpotifyLink.Audiobook): AudioItem {
        val audiobook =
            apiRouter.getShow(link.id, isAudiobook = true, limit = options.showLoadLimit)
                ?: return AudioReference.NO_TRACK
        if (audiobook.episodes.isEmpty()) {
            return AudioReference.NO_TRACK
        }
        return audioItemFactory.toAudioPlaylist(audiobook, this)
    }

    private fun loadSuggest(link: SpotifyLink.Suggest): AudioItem {
        val tracks = apiRouter.suggest(link.query)
        if (tracks.isEmpty()) {
            return AudioReference.NO_TRACK
        }
        return audioItemFactory.toSearchPlaylist(link.query, tracks, this)
    }

    private fun loadRecommendations(link: SpotifyLink.Recommendations): AudioItem {
        val seedLink = SpotifyLink.parse(link.seed)
        val seedId =
            when (seedLink) {
                is SpotifyLink.Track -> seedLink.id
                else ->
                    if (Base62.isValid(link.seed)) {
                        SpotifyId(link.seed)
                    } else {
                        return AudioReference.NO_TRACK
                    }
            }
        val tracks = apiRouter.recommendations(seedId, limit = options.recommendationLimit)
        if (tracks.isEmpty()) {
            return AudioReference.NO_TRACK
        }
        return audioItemFactory.toRecommendationsPlaylist(
            "Spotify Recommendations for ${seedId.value}",
            tracks,
            this,
        )
    }

    private fun loadSimilar(link: SpotifyLink.Similar): AudioItem {
        val seedLink = SpotifyLink.parse(link.seed)
        val seedId =
            when (seedLink) {
                is SpotifyLink.Track -> seedLink.id
                else ->
                    if (Base62.isValid(link.seed)) {
                        SpotifyId(link.seed)
                    } else {
                        return AudioReference.NO_TRACK
                    }
            }
        val tracks = apiRouter.similar(seedId, limit = options.recommendationLimit)
        if (tracks.isEmpty()) {
            return AudioReference.NO_TRACK
        }
        return audioItemFactory.toRecommendationsPlaylist(
            "Similar to ${seedId.value}",
            tracks,
            this,
        )
    }

    private fun isSpotifyIdentifier(identifier: String): Boolean {
        val trimmed = identifier.trim()
        if (trimmed.startsWith("spsearch:") ||
            trimmed.startsWith("spsuggest:") ||
            trimmed.startsWith("sprec:") ||
            trimmed.startsWith("spsimilar:") ||
            trimmed.startsWith("spotify:") ||
            shortLinkResolver.isShortLink(trimmed)
        ) {
            return true
        }
        val host =
            try {
                val withScheme =
                    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                        trimmed
                    } else {
                        "https://$trimmed"
                    }
                URI.create(withScheme).host?.lowercase(Locale.ROOT)
            } catch (e: Exception) {
                null
            }
        return host == "open.spotify.com" || host == "spotify.com"
    }

    override fun isTrackEncodable(track: AudioTrack): Boolean = true

    override fun encodeTrack(
        track: AudioTrack,
        output: DataOutput,
    ) {
        val spotifyTrack = track as? SpotifyAudioTrack
        output.writeByte(TRACK_VERSION)

        val canvas = spotifyTrack?.canvasUrl
        if (canvas != null) {
            output.writeBoolean(true)
            output.writeUTF(canvas)
        } else {
            output.writeBoolean(false)
        }

        val colors = spotifyTrack?.colors
        if (colors != null) {
            output.writeBoolean(true)
            output.writeInt(colors.size)
            for ((key, value) in colors) {
                output.writeUTF(key)
                output.writeUTF(value)
            }
        } else {
            output.writeBoolean(false)
        }

        val preview = spotifyTrack?.previewUrl
        if (preview != null) {
            output.writeBoolean(true)
            output.writeUTF(preview)
        } else {
            output.writeBoolean(false)
        }
    }

    override fun decodeTrack(
        trackInfo: AudioTrackInfo,
        input: DataInput,
    ): AudioTrack {
        var canvasUrl: String? = null
        var colors: Map<String, String>? = null
        var previewUrl: String? = null

        try {
            val hasData = (input as? InputStream)?.let { it.available() > 0 } ?: true
            if (hasData) {
                val version = input.readByte().toInt()
                if (version == TRACK_VERSION) {
                    if (input.readBoolean()) {
                        canvasUrl = input.readUTF()
                    }
                    if (input.readBoolean()) {
                        val count = input.readInt()
                        val map = LinkedHashMap<String, String>(count)
                        for (i in 0 until count) {
                            val key = input.readUTF()
                            val value = input.readUTF()
                            map[key] = value
                        }
                        colors = map
                    }
                    if (input.readBoolean()) {
                        previewUrl = input.readUTF()
                    }
                }
            }
        } catch (_: EOFException) {
            // older track with no extra bytes or short input
        } catch (_: IOException) {
            // corrupt or truncated input
        }

        return SpotifyAudioTrack(
            trackInfo = trackInfo,
            sourceManager = this,
            canvasUrl = canvasUrl,
            colors = colors,
            previewUrl = previewUrl,
        )
    }

    override fun configureRequests(configurator: Function<RequestConfig, RequestConfig>) {
        httpInterfaceManager.configureRequests(configurator)
    }

    override fun configureBuilder(configurator: Consumer<HttpClientBuilder>) {
        httpInterfaceManager.configureBuilder(configurator)
    }

    override fun shutdown() {
        try {
            enrichmentExecutor.shutdownNow()
            enrichmentExecutor.awaitTermination(500, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            log.debug("error shutting down enrichment executor: {}", e.message)
        }
        try {
            metadataCache.clear()
            trackMirror.close()
        } catch (e: Exception) {
            log.debug("error closing cache or mirror: {}", e.message)
        }
        try {
            httpInterfaceManager.close()
        } catch (e: Exception) {
            log.debug("error closing http interface manager: {}", e.message)
        }
        try {
            tokenManager.close()
        } catch (e: Exception) {
            log.debug("error closing token manager: {}", e.message)
        }
    }

    private companion object {
        private const val METADATA_CACHE_CAP = 1_000
        private const val TRACK_VERSION = 1
        private const val ENRICHMENT_TIMEOUT_MS = 800L
        private val enrichmentThreadId = AtomicInteger(0)

        private fun createEnrichmentExecutor(): ExecutorService =
            Executors.newFixedThreadPool(2) { runnable ->
                Thread(
                    runnable,
                    "spotify-enrichment-${enrichmentThreadId.incrementAndGet()}",
                ).apply {
                    isDaemon = true
                }
            }

        private fun createHttpInterfaceManager(options: SpotifyOptions): HttpInterfaceManager {
            val manager = HttpClientTools.createDefaultThreadLocalManager()
            val timeoutMs = (options.requestTimeoutSeconds * 1000).coerceAtLeast(1000)
            manager.configureRequests { config ->
                RequestConfig
                    .copy(config)
                    .setConnectTimeout(timeoutMs)
                    .setConnectionRequestTimeout(timeoutMs)
                    .setSocketTimeout(timeoutMs)
                    .build()
            }
            return manager
        }

        private fun createTokenManager(
            options: SpotifyOptions,
            httpInterfaceManager: HttpInterfaceManager,
        ): TokenManager {
            val providers = mutableMapOf<TokenKind, TokenProvider>()
            if (options.clientId.isNotBlank() && options.clientSecret.isNotBlank()) {
                providers[TokenKind.CLIENT] =
                    ClientCredentialsProvider(
                        clientId = options.clientId,
                        clientSecret = options.clientSecret,
                        httpInterfaceManager = httpInterfaceManager,
                    )
            }
            if (options.internalBackend) {
                providers[TokenKind.ANONYMOUS] =
                    AnonymousProvider(
                        httpInterfaceManager = httpInterfaceManager,
                        anonymousTokenUrl = options.anonymousTokenUrl,
                    )
            }
            if (options.accountCookie.isNotBlank()) {
                providers[TokenKind.ACCOUNT] =
                    AccountProvider(
                        accountCookie = options.accountCookie,
                        httpInterfaceManager = httpInterfaceManager,
                    )
            }
            return TokenManager(providers)
        }

        private fun createTrackMirror(
            options: SpotifyOptions,
            httpInterfaceManager: HttpInterfaceManager,
            tokenManager: TokenManager,
            apiRouter: ApiRouter,
        ): TrackMirror {
            val clientMetadata =
                if (options.accountCookie.isNotBlank()) {
                    ClientMetadata(
                        SpotifyHttp(
                            httpInterfaceManager = httpInterfaceManager,
                            tokenManager = tokenManager,
                        ),
                    )
                } else {
                    null
                }
            return TrackMirror(
                options = options,
                apiRouter = apiRouter,
                clientMetadata = clientMetadata,
            )
        }

        private fun createApiRouter(
            options: SpotifyOptions,
            httpInterfaceManager: HttpInterfaceManager,
            tokenManager: TokenManager,
        ): ApiRouter {
            val spotifyHttp =
                SpotifyHttp(
                    httpInterfaceManager = httpInterfaceManager,
                    tokenManager = tokenManager,
                )
            val backends = mutableListOf<SpotifyApi>()
            if (options.clientId.isNotBlank() && options.clientSecret.isNotBlank()) {
                backends.add(
                    OfficialApi(
                        http = spotifyHttp,
                        countryCode = options.countryCode,
                        allowLocalFiles = options.allowLocalFiles,
                    ),
                )
            }
            if (options.internalBackend) {
                backends.add(
                    InternalApi(
                        http = spotifyHttp,
                        options = options,
                        httpInterfaceManager = httpInterfaceManager,
                    ),
                )
            }
            if (backends.isEmpty()) {
                LoggerFactory.getLogger(SpotifyAudioSourceManager::class.java).warn(
                    "no Spotify backends configured: official credentials missing and " +
                        "internal backend disabled",
                )
            }
            return ApiRouter(backends, tokenManager)
        }

        private fun createLyricsService(
            options: SpotifyOptions,
            httpInterfaceManager: HttpInterfaceManager,
            tokenManager: TokenManager,
            apiRouter: ApiRouter,
        ): LyricsService {
            val http = SpotifyHttp(httpInterfaceManager, tokenManager)
            val accountConfigured = options.accountCookie.isNotBlank()
            val resolver = TrackResolver(apiRouter)

            val providers =
                options.lyricsProviders.mapNotNull { name ->
                    when (name) {
                        "spotify" ->
                            SpotifyLyricsProvider(http, tokenManager, resolver, accountConfigured)

                        "lrclib" -> LrcLibProvider(http)
                        else -> null
                    }
                }

            if (!accountConfigured && "spotify" in options.lyricsProviders) {
                LoggerFactory
                    .getLogger(SpotifyAudioSourceManager::class.java)
                    .info("lyrics: accountCookie not set, spotify lyrics skipped")
            }

            return LyricsService(
                providers = providers,
                foundTtl = options.lyricsCacheMinutes.minutes,
                missingTtl = options.lyricsMissCacheMinutes.minutes,
            )
        }
    }
}
