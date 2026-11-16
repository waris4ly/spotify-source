package dev.waris.spotify.api.lyrics

import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools
import dev.waris.spotify.api.ApiRouter
import dev.waris.spotify.api.Operation
import dev.waris.spotify.api.SpotifyApi
import dev.waris.spotify.api.SpotifyHttp
import dev.waris.spotify.auth.AccessToken
import dev.waris.spotify.auth.TokenKind
import dev.waris.spotify.auth.TokenManager
import dev.waris.spotify.auth.TokenProvider
import dev.waris.spotify.model.Album
import dev.waris.spotify.model.Artist
import dev.waris.spotify.model.Isrc
import dev.waris.spotify.model.Playlist
import dev.waris.spotify.model.SpotifyId
import dev.waris.spotify.model.Track
import kotlin.time.Duration.Companion.hours
import kotlin.time.TimeSource
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SpotifyLyricsProviderTest {
    private lateinit var server: MockWebServer

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    private fun createProvider(
        accountConfigured: Boolean = true,
        isCoolingDown: Boolean = false,
        resolver: TrackResolver? = null,
    ): SpotifyLyricsProvider {
        val tokenProvider =
            object : TokenProvider {
                override val kind = TokenKind.ACCOUNT
                override val isCoolingDown = isCoolingDown

                override fun coolDown(duration: kotlin.time.Duration) {}

                override fun fetchToken() =
                    AccessToken(
                        value = "account_token",
                        expiresAt = TimeSource.Monotonic.markNow() + 1.hours,
                    )
            }

        val httpManager = HttpClientTools.createDefaultThreadLocalManager()
        val tokenManager = TokenManager(mapOf(TokenKind.ACCOUNT to tokenProvider))
        val http = SpotifyHttp(httpManager, tokenManager)
        val defaultResolver = TrackResolver(ApiRouter(emptyList(), tokenManager))
        return SpotifyLyricsProvider(
            http = http,
            tokenManager = tokenManager,
            resolver = resolver ?: defaultResolver,
            accountConfigured = accountConfigured,
            baseUrl = server.url("/color-lyrics/v2/track/").toString(),
        )
    }

    @Test
    fun fetch_syncedFixture_parsesWithDurations() {
        val json =
            javaClass
                .getResourceAsStream("/fixtures/lyrics/spotify_synced.json")!!
                .bufferedReader()
                .readText()

        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .setHeader("Content-Type", "application/json")
                .body(json)
                .build(),
        )

        val provider = createProvider()
        val query =
            LyricsQuery(
                title = "Test Track",
                artist = "Test Artist",
                durationMs = 210_000,
                spotifyId = SpotifyId("4eoOQgFQ0rLMKNP8KcUNWc"),
            )

        val lyrics = provider.fetch(query)
        assertNotNull(lyrics)
        assertEquals("spotify", lyrics!!.provider)
        assertEquals("Musixmatch", lyrics.providerLabel)
        assertEquals(LyricsSync.LINE, lyrics.sync)
        assertEquals("en", lyrics.language)
        assertFalse(lyrics.rightToLeft)
        assertEquals(4, lyrics.lines.size)

        val line0 = lyrics.lines[0]
        assertEquals(12000L, line0.startMs)
        assertEquals(15500L, line0.endMs)
        assertEquals("First line of the song", line0.text)

        val line1 = lyrics.lines[1]
        assertEquals(16000L, line1.startMs)
        assertEquals(20000L, line1.endMs)
        assertEquals("♪", line1.text)

        val line2 = lyrics.lines[2]
        assertEquals(20000L, line2.startMs)
        assertEquals(25000L, line2.endMs)
        assertEquals("Second line of the song", line2.text)

        val line3 = lyrics.lines[3]
        assertEquals(25000L, line3.startMs)
        assertEquals(29000L, line3.endMs)
        assertEquals("Final line", line3.text)

        val recorded = server.takeRequest()
        assertEquals("Bearer account_token", recorded.headers["Authorization"])
        assertEquals("WebPlayer", recorded.headers["App-Platform"])
    }

    @Test
    fun fetch_unsyncedFixture_givesSyncNoneAndZeroStarts() {
        val json =
            javaClass
                .getResourceAsStream("/fixtures/lyrics/spotify_unsynced.json")!!
                .bufferedReader()
                .readText()

        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .setHeader("Content-Type", "application/json")
                .body(json)
                .build(),
        )

        val provider = createProvider()
        val query =
            LyricsQuery(
                title = "Unsynced Track",
                artist = "Test Artist",
                durationMs = 180_000,
                spotifyId = SpotifyId("4eoOQgFQ0rLMKNP8KcUNWc"),
            )

        val lyrics = provider.fetch(query)
        assertNotNull(lyrics)
        assertEquals(LyricsSync.NONE, lyrics!!.sync)
        assertEquals(2, lyrics.lines.size)
        assertEquals(0L, lyrics.lines[0].startMs)
        assertNull(lyrics.lines[0].endMs)
        assertEquals("Just a plain line", lyrics.lines[0].text)
        assertEquals(0L, lyrics.lines[1].startMs)
        assertNull(lyrics.lines[1].endMs)
        assertEquals("Another plain line", lyrics.lines[1].text)
        assertEquals("Just a plain line\nAnother plain line", lyrics.plainText)
    }

    @Test
    fun fetch_notFound404_returnsNull() {
        server.enqueue(
            MockResponse
                .Builder()
                .code(404)
                .setHeader("Content-Type", "application/json")
                .body("{}")
                .build(),
        )

        val provider = createProvider()
        val query =
            LyricsQuery(
                title = "Unknown",
                artist = "Unknown",
                durationMs = 180_000,
                spotifyId = SpotifyId("4eoOQgFQ0rLMKNP8KcUNWc"),
            )

        val lyrics = provider.fetch(query)
        assertNull(lyrics)
    }

    @Test
    fun fetch_onlyWhitespaceLines_returnsNull() {
        val json =
            """
            {
              "lyrics": {
                "syncType": "LINE_SYNCED",
                "lines": [
                  { "startTimeMs": "1000", "endTimeMs": "2000", "words": "   " }
                ]
              }
            }
            """.trimIndent()

        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .setHeader("Content-Type", "application/json")
                .body(json)
                .build(),
        )

        val provider = createProvider()
        val query =
            LyricsQuery(
                title = "Whitespace",
                artist = "Artist",
                durationMs = 180_000,
                spotifyId = SpotifyId("4eoOQgFQ0rLMKNP8KcUNWc"),
            )

        val lyrics = provider.fetch(query)
        assertNull(lyrics)
    }

    @Test
    fun isAvailable_reflectsAccountAndCooldown() {
        val available = createProvider(accountConfigured = true, isCoolingDown = false)
        assertTrue(available.isAvailable())

        val unconfigured = createProvider(accountConfigured = false, isCoolingDown = false)
        assertFalse(unconfigured.isAvailable())

        val coolingDown = createProvider(accountConfigured = true, isCoolingDown = true)
        assertFalse(coolingDown.isAvailable())
    }

    @Test
    fun fetch_noSpotifyId_resolvesViaTrackResolver() {
        val json =
            javaClass
                .getResourceAsStream("/fixtures/lyrics/spotify_synced.json")!!
                .bufferedReader()
                .readText()

        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .setHeader("Content-Type", "application/json")
                .body(json)
                .build(),
        )

        val fakeApi =
            object : SpotifyApi {
                override val name: String = "official"

                override fun supports(operation: Operation): Boolean = operation is Operation.Search

                override fun tokenKindFor(operation: Operation): TokenKind? = null

                override fun search(
                    query: String,
                    limit: Int,
                    offset: Int,
                ): List<Track> =
                    listOf(
                        Track(
                            id = SpotifyId("resolvedId12345678901"),
                            title = "Non-Spotify Track",
                            artists =
                                listOf(
                                    Artist(SpotifyId("artist12345678901234"), "YouTube Artist"),
                                ),
                            durationMs = 210_000,
                        ),
                    )

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

                override fun getIsrc(id: SpotifyId): Isrc? = null
            }

        val tokenManager = TokenManager(emptyMap())
        val apiRouter = ApiRouter(listOf(fakeApi), tokenManager)
        val resolver = TrackResolver(apiRouter)

        val provider = createProvider(resolver = resolver)
        val query =
            LyricsQuery(
                title = "Non-Spotify Track",
                artist = "YouTube Artist",
                durationMs = 210_000,
                spotifyId = null,
            )

        val lyrics = provider.fetch(query)
        assertNotNull(lyrics)
        val recorded = server.takeRequest()
        assertTrue(recorded.url.encodedPath.contains("resolvedId12345678901"))
    }
}
