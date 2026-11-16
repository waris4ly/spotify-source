package dev.waris.spotify.mirror

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioItem
import com.sedmelluq.discord.lavaplayer.track.AudioReference
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo
import com.sedmelluq.discord.lavaplayer.track.BaseAudioTrack
import com.sedmelluq.discord.lavaplayer.track.BasicAudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor
import dev.waris.spotify.SpotifyOptions
import dev.waris.spotify.api.ApiRouter
import dev.waris.spotify.api.Operation
import dev.waris.spotify.api.SpotifyApi
import dev.waris.spotify.auth.AccessToken
import dev.waris.spotify.auth.TokenKind
import dev.waris.spotify.auth.TokenManager
import dev.waris.spotify.auth.TokenProvider
import dev.waris.spotify.model.Album
import dev.waris.spotify.model.Artist
import dev.waris.spotify.model.Episode
import dev.waris.spotify.model.Isrc
import dev.waris.spotify.model.Playlist
import dev.waris.spotify.model.Show
import dev.waris.spotify.model.SpotifyId
import dev.waris.spotify.model.Track
import java.io.DataInput
import java.io.DataOutput
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.TimeSource
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TrackMirrorTest {
    private lateinit var tokenManager: TokenManager
    private lateinit var playerManager: DefaultAudioPlayerManager
    private lateinit var fakeSourceManager: FakePlaybackSourceManager

    private class MockTokenProvider(
        override val kind: TokenKind,
    ) : TokenProvider {
        override val isCoolingDown: Boolean get() = false

        override fun fetchToken(): AccessToken =
            AccessToken("test-token", TimeSource.Monotonic.markNow() + 1.hours, 3600)

        override fun coolDown(duration: Duration) {}
    }

    private class FakeApi(
        override val name: String = "fakeApi",
        val isrcCalls: MutableList<SpotifyId> = mutableListOf(),
    ) : SpotifyApi {
        override fun supports(operation: Operation): Boolean = true

        override fun tokenKindFor(operation: Operation): TokenKind? = null

        override fun search(
            query: String,
            limit: Int,
            offset: Int,
        ): List<Track> = emptyList()

        override fun getTrack(id: SpotifyId): Track? = null

        override fun getAlbum(
            id: SpotifyId,
            limit: Int,
        ): Album? = null

        override fun getPlaylist(
            id: SpotifyId,
            limit: Int,
        ): Playlist? = null

        override fun getArtist(id: SpotifyId): Artist? = null

        override fun getIsrc(id: SpotifyId): Isrc? {
            isrcCalls.add(id)
            return null
        }

        override fun getEpisode(
            id: SpotifyId,
            isChapter: Boolean,
        ): Episode? = null

        override fun getShow(
            id: SpotifyId,
            isAudiobook: Boolean,
            limit: Int,
        ): Show? = null
    }

    private class FakePlaybackTrack(
        info: AudioTrackInfo,
    ) : BaseAudioTrack(info) {
        override fun process(executor: LocalAudioTrackExecutor) {}

        override fun makeClone(): AudioTrack = FakePlaybackTrack(trackInfo)

        override fun getSourceManager(): AudioSourceManager? = null
    }

    private class FakePlaybackSourceManager : AudioSourceManager {
        val loadItemCalls = mutableListOf<String>()
        var itemSupplier: ((String) -> AudioItem?)? = null

        override fun getSourceName(): String = "fake"

        override fun loadItem(
            manager: AudioPlayerManager,
            reference: AudioReference,
        ): AudioItem? {
            loadItemCalls.add(reference.identifier)
            return itemSupplier?.invoke(reference.identifier)
        }

        override fun isTrackEncodable(track: AudioTrack): Boolean = false

        override fun encodeTrack(
            track: AudioTrack,
            output: DataOutput,
        ) {}

        override fun decodeTrack(
            trackInfo: AudioTrackInfo,
            input: DataInput,
        ): AudioTrack = FakePlaybackTrack(trackInfo)

        override fun shutdown() {}
    }

    @BeforeEach
    fun setUp() {
        tokenManager =
            TokenManager(
                mapOf(
                    TokenKind.CLIENT to MockTokenProvider(TokenKind.CLIENT),
                    TokenKind.ANONYMOUS to MockTokenProvider(TokenKind.ANONYMOUS),
                ),
            )
        fakeSourceManager = FakePlaybackSourceManager()
        playerManager = DefaultAudioPlayerManager()
        playerManager.registerSourceManager(fakeSourceManager)
    }

    @AfterEach
    fun tearDown() {
        tokenManager.close()
        playerManager.shutdown()
    }

    @Test
    fun init_rejectsRecursiveSpotifyTemplates() {
        assertThrows(IllegalArgumentException::class.java) {
            TrackMirror(
                options = SpotifyOptions(mirrorProviders = listOf("spsearch:%query%")),
                apiRouter = ApiRouter(emptyList(), tokenManager),
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            TrackMirror(
                options =
                    SpotifyOptions(
                        mirrorProviders =
                            listOf(
                                "https://open.spotify.com/track/4eoOQgFQ0rLMKNP8KcUNWc",
                            ),
                    ),
                apiRouter = ApiRouter(emptyList(), tokenManager),
            )
        }
    }

    @Test
    fun resolve_resolvesUsingIsrcWhenPresent() {
        val options =
            SpotifyOptions(
                mirrorProviders =
                    listOf(
                        """fake:"%isrc%"""",
                        "fake:%query%",
                    ),
            )
        val mirror = TrackMirror(options, ApiRouter(emptyList(), tokenManager))

        val target =
            AudioTrackInfo(
                "Starboy",
                "The Weeknd, Daft Punk",
                230453L,
                "t1",
                false,
                "https://open.spotify.com/track/t1",
                null,
                "USUM71607007",
            )
        val candidate =
            FakePlaybackTrack(
                AudioTrackInfo(
                    "The Weeknd - Starboy ft. Daft Punk",
                    "The Weeknd",
                    230000L,
                    "c1",
                    false,
                    "https://fake.source/c1",
                    null,
                    null,
                ),
            )

        fakeSourceManager.itemSupplier = { query ->
            if (query == """fake:"USUM71607007"""") {
                BasicAudioPlaylist("Search", listOf(candidate), null, true)
            } else {
                null
            }
        }

        val result = mirror.resolve(target, playerManager, useCache = true)

        assertNotNull(result.track)
        assertEquals("The Weeknd - Starboy ft. Daft Punk", result.track.info.title)
        assertFalse(result.wasCached)
        assertEquals(listOf("""fake:"USUM71607007""""), fakeSourceManager.loadItemCalls)
    }

    @Test
    fun resolve_skipsIsrcWhenNullAndFallsBackToQuery() {
        val options =
            SpotifyOptions(
                mirrorProviders =
                    listOf(
                        """fake:"%isrc%"""",
                        "fake:%query%",
                    ),
            )
        val mirror = TrackMirror(options, ApiRouter(emptyList(), tokenManager))

        val target =
            AudioTrackInfo(
                "ocean eyes",
                "Billie Eilish",
                200379L,
                "t2",
                false,
                "https://open.spotify.com/track/t2",
                null,
                null,
            )
        val candidate =
            FakePlaybackTrack(
                AudioTrackInfo(
                    "Billie Eilish - ocean eyes",
                    "Billie Eilish",
                    200000L,
                    "c2",
                    false,
                    "https://fake.source/c2",
                    null,
                    null,
                ),
            )

        fakeSourceManager.itemSupplier = { query ->
            if (query == "fake:ocean eyes Billie Eilish") {
                BasicAudioPlaylist("Search", listOf(candidate), null, true)
            } else {
                null
            }
        }

        val result = mirror.resolve(target, playerManager, useCache = true)

        assertNotNull(result.track)
        assertEquals("Billie Eilish - ocean eyes", result.track.info.title)
        assertFalse(result.wasCached)
        assertEquals(listOf("fake:ocean eyes Billie Eilish"), fakeSourceManager.loadItemCalls)
    }

    @Test
    fun resolve_cachesWinnerAndReturnsCachedOnSubsequentCall() {
        val options =
            SpotifyOptions(
                mirrorProviders = listOf("fake:%query%"),
                mirrorCacheMinutes = 60,
            )
        val mirror = TrackMirror(options, ApiRouter(emptyList(), tokenManager))

        val target =
            AudioTrackInfo(
                "Starboy",
                "The Weeknd",
                230453L,
                "t1",
                false,
                "https://open.spotify.com/track/t1",
                null,
                null,
            )
        val candidate =
            FakePlaybackTrack(
                AudioTrackInfo(
                    "The Weeknd - Starboy",
                    "The Weeknd",
                    230000L,
                    "c1",
                    false,
                    "https://fake.source/c1",
                    null,
                    null,
                ),
            )

        fakeSourceManager.itemSupplier = {
            BasicAudioPlaylist("Search", listOf(candidate), null, true)
        }

        val first = mirror.resolve(target, playerManager, useCache = true)
        assertFalse(first.wasCached)
        assertEquals(1, fakeSourceManager.loadItemCalls.size)

        val second = mirror.resolve(target, playerManager, useCache = true)
        assertTrue(second.wasCached)
        assertEquals(1, fakeSourceManager.loadItemCalls.size)

        mirror.invalidate(target.identifier)
        val third = mirror.resolve(target, playerManager, useCache = true)
        assertFalse(third.wasCached)
        assertEquals(2, fakeSourceManager.loadItemCalls.size)
    }

    @Test
    fun resolve_throwsFriendlyExceptionWhenNoCandidatesMatch() {
        val options = SpotifyOptions(mirrorProviders = listOf("fake:%query%"))
        val mirror = TrackMirror(options, ApiRouter(emptyList(), tokenManager))

        val target =
            AudioTrackInfo(
                "Unmatched Song",
                "Unknown Artist",
                180000L,
                "t_none",
                false,
                "https://open.spotify.com/track/t_none",
                null,
                null,
            )

        fakeSourceManager.itemSupplier = {
            BasicAudioPlaylist("Search", emptyList(), null, true)
        }

        val exception =
            assertThrows(FriendlyException::class.java) {
                mirror.resolve(target, playerManager, useCache = true)
            }
        assertEquals(FriendlyException.Severity.COMMON, exception.severity)
        assertTrue(exception.message?.contains("No playback source found") == true)
    }

    @Test
    fun resolve_localTracksResolveAndCacheIndependentlyWithoutIsrcLookup() {
        val fakeApi = FakeApi()
        val options =
            SpotifyOptions(
                mirrorProviders = listOf("fake:%query%"),
                mirrorCacheMinutes = 60,
            )
        val apiRouter = ApiRouter(listOf(fakeApi), tokenManager)
        val mirror = TrackMirror(options, apiRouter)

        val localTrack1 =
            AudioTrackInfo(
                "Local Song 1",
                "Artist A",
                180000L,
                "local",
                false,
                "spotify:local:Local Song 1",
                null,
                null,
            )
        val localTrack2 =
            AudioTrackInfo(
                "Local Song 2",
                "Artist B",
                200000L,
                "local",
                false,
                "spotify:local:Local Song 2",
                null,
                null,
            )

        val candidate1 =
            FakePlaybackTrack(
                AudioTrackInfo(
                    "Local Song 1 - Artist A",
                    "Artist A",
                    180000L,
                    "c1",
                    false,
                    "https://fake.source/c1",
                    null,
                    null,
                ),
            )
        val candidate2 =
            FakePlaybackTrack(
                AudioTrackInfo(
                    "Local Song 2 - Artist B",
                    "Artist B",
                    200000L,
                    "c2",
                    false,
                    "https://fake.source/c2",
                    null,
                    null,
                ),
            )

        fakeSourceManager.itemSupplier = { query ->
            when (query) {
                "fake:Local Song 1 Artist A" ->
                    BasicAudioPlaylist("Search", listOf(candidate1), null, true)
                "fake:Local Song 2 Artist B" ->
                    BasicAudioPlaylist("Search", listOf(candidate2), null, true)
                else -> null
            }
        }

        val result1 = mirror.resolve(localTrack1, playerManager, useCache = true)
        assertEquals("Local Song 1 - Artist A", result1.track.info.title)
        assertFalse(result1.wasCached)

        val result2 = mirror.resolve(localTrack2, playerManager, useCache = true)
        assertEquals("Local Song 2 - Artist B", result2.track.info.title)
        assertFalse(result2.wasCached)

        // verify cached lookups
        val cached1 = mirror.resolve(localTrack1, playerManager, useCache = true)
        assertTrue(cached1.wasCached)
        assertEquals("Local Song 1 - Artist A", cached1.track.info.title)

        val cached2 = mirror.resolve(localTrack2, playerManager, useCache = true)
        assertTrue(cached2.wasCached)
        assertEquals("Local Song 2 - Artist B", cached2.track.info.title)

        // verify no ISRC calls made for "local"
        assertTrue(fakeApi.isrcCalls.isEmpty())
    }

    @Test
    fun resolve_concurrentResolutionsDeduplicateProviderLookupAndReturnDistinctInstances() {
        val options =
            SpotifyOptions(
                mirrorProviders = listOf("fake:%query%"),
                mirrorCacheMinutes = 60,
            )
        val mirror = TrackMirror(options, ApiRouter(emptyList(), tokenManager))

        val target =
            AudioTrackInfo(
                "Inflight Song",
                "Artist",
                180000L,
                "inflight1",
                false,
                "https://open.spotify.com/track/inflight1",
                null,
                null,
            )
        val candidate =
            FakePlaybackTrack(
                AudioTrackInfo(
                    "Inflight Song - Artist",
                    "Artist",
                    180000L,
                    "c1",
                    false,
                    "https://fake.source/c1",
                    null,
                    null,
                ),
            )

        val providerCalls = AtomicInteger(0)
        val enteredSupplier = CountDownLatch(1)
        val allowSupplierToComplete = CountDownLatch(1)

        fakeSourceManager.itemSupplier = {
            providerCalls.incrementAndGet()
            enteredSupplier.countDown()
            allowSupplierToComplete.await(2, TimeUnit.SECONDS)
            BasicAudioPlaylist("Search", listOf(candidate), null, true)
        }

        val executor = Executors.newFixedThreadPool(2)
        try {
            val future1 =
                CompletableFuture.supplyAsync(
                    { mirror.resolve(target, playerManager, useCache = true) },
                    executor,
                )

            assertTrue(enteredSupplier.await(2, TimeUnit.SECONDS))

            val future2 =
                CompletableFuture.supplyAsync(
                    { mirror.resolve(target, playerManager, useCache = true) },
                    executor,
                )

            allowSupplierToComplete.countDown()

            val result1 = future1.get(2, TimeUnit.SECONDS)
            val result2 = future2.get(2, TimeUnit.SECONDS)

            assertEquals(1, providerCalls.get())
            assertEquals("Inflight Song - Artist", result1.track.info.title)
            assertEquals("Inflight Song - Artist", result2.track.info.title)
            assertNotSame(result1.track, result2.track)
        } finally {
            executor.shutdownNow()
        }
    }
}
