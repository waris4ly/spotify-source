package dev.waris.spotify.api.internal

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager
import dev.waris.spotify.SpotifyOptions
import dev.waris.spotify.api.Operation
import dev.waris.spotify.api.SpotifyApi
import dev.waris.spotify.api.SpotifyHttp
import dev.waris.spotify.auth.TokenKind
import dev.waris.spotify.error.SpotifyException
import dev.waris.spotify.model.Album
import dev.waris.spotify.model.Artist
import dev.waris.spotify.model.Episode
import dev.waris.spotify.model.Isrc
import dev.waris.spotify.model.Playlist
import dev.waris.spotify.model.Show
import dev.waris.spotify.model.SpotifyId
import dev.waris.spotify.model.Track
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern
import kotlin.math.min
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.slf4j.LoggerFactory

internal class InternalApi(
    private val http: SpotifyHttp,
    private val options: SpotifyOptions = SpotifyOptions(),
    private val parser: InternalParser = InternalParser(options.allowLocalFiles),
    private val persistedQueries: PersistedQueries = PersistedQueries(options.queryHashes),
    private val endpointUrl: String = DEFAULT_ENDPOINT_URL,
    private val httpInterfaceManager: HttpInterfaceManager? = null,
) : SpotifyApi {
    private val log = LoggerFactory.getLogger(InternalApi::class.java)

    override val name: String = "internal"

    override fun supports(operation: Operation): Boolean =
        when (operation) {
            is Operation.Search,
            is Operation.GetTrack,
            is Operation.GetAlbum,
            is Operation.GetPlaylist,
            is Operation.GetArtist,
            is Operation.GetEpisode,
            is Operation.GetShow,
            is Operation.Suggest,
            is Operation.Recommendations,
            is Operation.Similar,
            -> true

            is Operation.GetIsrc -> false
        }

    override fun tokenKindFor(operation: Operation): TokenKind? =
        if (supports(operation)) TokenKind.ANONYMOUS else null

    override fun search(
        query: String,
        limit: Int,
        offset: Int,
    ): List<Track> {
        val variables =
            JsonBrowser
                .newMap()
                .apply {
                    put("searchTerm", query)
                    put("offset", offset)
                    put("limit", limit)
                    put("numberOfTopResults", 5)
                    put("includeAudiobooks", false)
                    put("includeArtistHasConcertsField", false)
                    put("includePreReleases", false)
                }.format()
        val json = postQuery(PersistedQueries.SEARCH_DESKTOP, variables)
        return parser.parseSearch(json)
    }

    override fun getTrack(id: SpotifyId): Track? {
        val variables = """{"uri":"spotify:track:$id"}"""
        val json = postQuery(PersistedQueries.GET_TRACK, variables)
        return parser.parseTrack(json)
    }

    override fun getAlbum(
        id: SpotifyId,
        limit: Int,
    ): Album? {
        val pageLimit = min(300, limit)
        val variables =
            """
            {
              "uri": "spotify:album:$id",
              "locale": "en",
              "offset": 0,
              "limit": $pageLimit
            }
            """.trimIndent()
        val json = postQuery(PersistedQueries.GET_ALBUM, variables)
        val album = parser.parseAlbum(json, limit) ?: return null

        if (album.tracks.size >= album.totalTracks || album.tracks.size >= limit) {
            return album
        }

        val allTracks = album.tracks.toMutableList()
        var offset = allTracks.size
        var truncated = false
        // page through subsequent items until reaching target limit or end of collection
        while (allTracks.size < album.totalTracks && allTracks.size < limit) {
            val nextPageLimit = min(300, limit - allTracks.size)
            val nextVariables =
                """
                {
                  "uri": "spotify:album:$id",
                  "locale": "en",
                  "offset": $offset,
                  "limit": $nextPageLimit
                }
                """.trimIndent()
            val pageJson =
                try {
                    postQuery(PersistedQueries.GET_ALBUM, nextVariables)
                } catch (e: Exception) {
                    log.warn(
                        "album cut short id={} loaded={} total={}",
                        id.value,
                        allTracks.size,
                        album.totalTracks,
                    )
                    truncated = true
                    break
                }
            val pageAlbum =
                parser.parseAlbum(pageJson, nextPageLimit) ?: run {
                    truncated = true
                    break
                }
            if (pageAlbum.tracks.isEmpty()) {
                break
            }
            allTracks.addAll(pageAlbum.tracks)
            offset += pageAlbum.tracks.size
        }

        return album.copy(tracks = allTracks, truncated = truncated)
    }

    override fun getPlaylist(
        id: SpotifyId,
        limit: Int,
    ): Playlist? {
        val pageLimit = min(100, limit)
        val variables =
            """
            {
              "uri": "spotify:playlist:$id",
              "offset": 0,
              "limit": $pageLimit,
              "enableWatchFeedEntrypoint": false
            }
            """.trimIndent()
        val json = postQuery(PersistedQueries.FETCH_PLAYLIST, variables)
        val playlist = parser.parsePlaylist(json, limit)

        if (playlist.tracks.size >= playlist.totalTracks || playlist.tracks.size >= limit) {
            return playlist
        }

        val allTracks = playlist.tracks.toMutableList()
        var offset = allTracks.size
        var truncated = false
        while (allTracks.size < playlist.totalTracks && allTracks.size < limit) {
            val nextPageLimit = min(100, limit - allTracks.size)
            val nextVariables =
                """
                {
                  "uri": "spotify:playlist:$id",
                  "offset": $offset,
                  "limit": $nextPageLimit,
                  "enableWatchFeedEntrypoint": false
                }
                """.trimIndent()
            val pageJson =
                try {
                    postQuery(PersistedQueries.FETCH_PLAYLIST, nextVariables)
                } catch (e: Exception) {
                    log.warn(
                        "playlist cut short id={} loaded={} total={}",
                        id.value,
                        allTracks.size,
                        playlist.totalTracks,
                    )
                    truncated = true
                    break
                }
            val pageTracks =
                parser.parsePlaylistTracks(
                    json = pageJson,
                    maxTracks = nextPageLimit,
                    fallbackArtwork = playlist.artworkUrl,
                )
            if (pageTracks.isEmpty()) {
                break
            }
            allTracks.addAll(pageTracks)
            offset += pageTracks.size
        }

        return playlist.copy(tracks = allTracks, truncated = truncated)
    }

    override fun getArtist(id: SpotifyId): Artist? {
        val variables = """{"uri":"spotify:artist:$id","locale":"en","includePrerelease":false}"""
        val json = postQuery(PersistedQueries.QUERY_ARTIST_OVERVIEW, variables)
        return parser.parseArtist(json)
    }

    override fun getIsrc(id: SpotifyId): Isrc? = getTrack(id)?.isrc

    override fun getEpisode(
        id: SpotifyId,
        isChapter: Boolean,
    ): Episode? {
        val kind = if (isChapter) "chapter" else "episode"
        val variables = """{"uri":"spotify:$kind:${id.value}"}"""
        try {
            val response = postQuery(PersistedQueries.GET_EPISODE_OR_CHAPTER, variables)
            val parsed = parser.parseEpisode(response, fallbackId = id, isChapter = isChapter)
            if (parsed != null) {
                return parsed
            }
        } catch (e: Exception) {
            log.warn("graphql episode lookup failed for {}: {}", id.value, e.message)
        }

        // scrape web embed page if persisted query is missing or rejects the id
        return fallbackEpisodeLookup(id, kind, isChapter)
    }

    private fun fallbackEpisodeLookup(
        id: SpotifyId,
        kind: String,
        isChapter: Boolean,
    ): Episode? {
        if (httpInterfaceManager != null) {
            try {
                val embedUrl = "https://open.spotify.com/embed/$kind/${id.value}"
                val embedRequest =
                    HttpGet(embedUrl).apply {
                        setHeader("User-Agent", USER_AGENT)
                    }
                val html =
                    httpInterfaceManager.getInterface().use { client ->
                        client.execute(embedRequest).use { response ->
                            if (response.statusLine.statusCode in 200..299) {
                                response.entity
                                    ?.content
                                    ?.bufferedReader(Charsets.UTF_8)
                                    ?.readText()
                            } else {
                                null
                            }
                        }
                    }
                if (html != null) {
                    val matcher = NEXT_DATA_PATTERN.matcher(html)
                    if (matcher.find()) {
                        val json = JsonBrowser.parse(matcher.group(1))
                        val parsedEmbed =
                            parser.parseEpisode(json, fallbackId = id, isChapter = isChapter)
                        if (parsedEmbed != null) {
                            return parsedEmbed
                        }
                    }
                }
            } catch (e: Exception) {
                log.warn("embed episode lookup failed for {}: {}", id.value, e.message)
            }
        }

        try {
            val oembedUrl =
                "https://open.spotify.com/oembed?url=https://open.spotify.com/$kind/${id.value}"
            val oembedRequest =
                HttpGet(oembedUrl).apply {
                    setHeader("User-Agent", USER_AGENT)
                }
            val oembedResponse = http.execute(oembedRequest, null)
            val parsedOembed =
                parser.parseEpisode(oembedResponse, fallbackId = id, isChapter = isChapter)
            if (parsedOembed != null) {
                return parsedOembed
            }
        } catch (e: Exception) {
            log.warn("oembed episode lookup failed for {}: {}", id.value, e.message)
        }

        return null
    }

    override fun getShow(
        id: SpotifyId,
        isAudiobook: Boolean,
        limit: Int,
    ): Show? {
        val kind = "show"
        val op =
            if (isAudiobook) {
                PersistedQueries.QUERY_BOOK_CHAPTERS
            } else {
                PersistedQueries.QUERY_PODCAST_EPISODES
            }
        val firstLimit = min(limit, 100)
        val variables = """{"uri":"spotify:$kind:${id.value}","offset":0,"limit":$firstLimit}"""
        val response = postQuery(op, variables)
        val show =
            parser.parseShow(response, maxEpisodes = limit, isAudiobook = isAudiobook)
                ?: return null

        if (show.episodes.size >= limit || show.episodes.size >= show.totalEpisodes) {
            return show
        }

        val allEpisodes = show.episodes.toMutableList()
        var offset = allEpisodes.size
        var truncated = false
        while (allEpisodes.size < limit && offset < show.totalEpisodes) {
            val pageLimit = min(limit - allEpisodes.size, 100)
            val pageVariables =
                """{"uri":"spotify:$kind:${id.value}","offset":$offset,"limit":$pageLimit}"""
            val pageResponse =
                try {
                    postQuery(op, pageVariables)
                } catch (e: Exception) {
                    log.warn("failed paging show {}: {}", id.value, e.message)
                    truncated = true
                    break
                }
            val pageShow =
                parser.parseShow(pageResponse, maxEpisodes = pageLimit, isAudiobook = isAudiobook)
            if (pageShow == null || pageShow.episodes.isEmpty()) {
                break
            }
            allEpisodes.addAll(pageShow.episodes)
            offset += pageShow.episodes.size
        }

        return show.copy(episodes = allEpisodes, truncated = truncated)
    }

    override fun suggest(query: String): List<Track> {
        val variables =
            JsonBrowser
                .newMap()
                .apply {
                    put("query", query)
                }.format()
        val response = postQuery(PersistedQueries.SEARCH_SUGGESTIONS, variables)
        return parser.parseSearchSuggestions(response)
    }

    override fun recommendations(
        seed: SpotifyId,
        limit: Int,
    ): List<Track> {
        val variables = """{"uri":"spotify:track:${seed.value}","limit":$limit}"""
        val response = postQuery(PersistedQueries.GET_RECOMMENDATIONS, variables)
        return parser.parseRecommendations(response, limit)
    }

    override fun similar(
        seed: SpotifyId,
        limit: Int,
    ): List<Track> {
        val variables = """{"uri":"spotify:track:${seed.value}","limit":$limit}"""
        try {
            val response = postQuery(PersistedQueries.SIMILAR_ALBUMS, variables)
            val tracks = parser.parseSimilarAlbums(response, limit)
            if (tracks.isNotEmpty()) {
                return tracks
            }
        } catch (e: Exception) {
            log.warn("similar albums query failed for {}: {}", seed.value, e.message)
        }
        return recommendations(seed, limit)
    }

    private fun postQuery(
        operationName: String,
        variablesJson: String,
    ): JsonBrowser {
        val hash = persistedQueries.hashFor(operationName)
        val body =
            """
            {
              "variables": $variablesJson,
              "operationName": "$operationName",
              "extensions": {
                "persistedQuery": {
                  "version": 1,
                  "sha256Hash": "$hash"
                }
              }
            }
            """.trimIndent()

        val request =
            HttpPost(endpointUrl).apply {
                setHeader("Accept", "application/json")
                setHeader("Content-Type", "application/json; charset=utf-8")
                entity = StringEntity(body, StandardCharsets.UTF_8)
            }

        val response = http.execute(request, TokenKind.ANONYMOUS)
        val errors = response.get("errors")
        if (errors.isList && !errors.values().isEmpty()) {
            val firstError = errors.values()[0]
            val message = firstError.get("message").text() ?: "Unknown error"
            val code = firstError.get("extensions").get("code").text()
            if (code == "PERSISTED_QUERY_NOT_FOUND") {
                throw SpotifyException.QueryRejected(operationName)
            }
            throw SpotifyException("graphql query error ($operationName): $message")
        }
        return response
    }

    companion object {
        const val DEFAULT_ENDPOINT_URL: String =
            "https://api-partner.spotify.com/pathfinder/v2/query"
        private const val USER_AGENT: String =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/130.0.0.0"

        private val NEXT_DATA_PATTERN =
            Pattern.compile(
                "<script id=\"__NEXT_DATA__\" type=\"application/json\">(.*?)</script>",
                Pattern.DOTALL,
            )
    }
}
