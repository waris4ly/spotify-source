package dev.waris.spotify.api.lyrics

import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools
import dev.waris.spotify.api.SpotifyHttp
import dev.waris.spotify.auth.TokenManager
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LrcLibProviderTest {
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

    private fun createProvider(durationToleranceMs: Long = 2000): LrcLibProvider {
        val httpManager = HttpClientTools.createDefaultThreadLocalManager()
        val tokenManager = TokenManager(emptyMap())
        val http = SpotifyHttp(httpManager, tokenManager)
        return LrcLibProvider(
            http = http,
            baseUrl = server.url("/api").toString(),
            durationToleranceMs = durationToleranceMs,
        )
    }

    @Test
    fun fetch_syncedLyrics_parsedCorrectly() {
        val json =
            javaClass
                .getResourceAsStream("/fixtures/lyrics/lrclib_search.json")!!
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
                artist = "Test Artist, Featured Artist",
                durationMs = 210_000,
            )

        val lyrics = provider.fetch(query)
        assertNotNull(lyrics)
        assertEquals("lrclib", lyrics!!.provider)
        assertEquals("LRCLIB", lyrics.providerLabel)
        assertEquals(LyricsSync.LINE, lyrics.sync)
        assertEquals(3, lyrics.lines.size)

        val line0 = lyrics.lines[0]
        assertEquals(12500L, line0.startMs)
        assertEquals(18000L, line0.endMs)
        assertEquals("First synced line", line0.text)

        val line1 = lyrics.lines[1]
        assertEquals(18000L, line1.startMs)
        assertEquals(25750L, line1.endMs)
        assertEquals("Second synced line", line1.text)

        val line2 = lyrics.lines[2]
        assertEquals(25750L, line2.startMs)
        assertNull(line2.endMs)
        assertEquals("Third synced line", line2.text)

        val recorded = server.takeRequest()
        assertNull(recorded.headers["Authorization"])
        assertEquals(
            "spotify-source (https://github.com/waris4ly/spotify-source)",
            recorded.headers["User-Agent"],
        )
        assertTrue(recorded.url.encodedQuery!!.contains("artist_name=Test+Artist"))
    }

    @Test
    fun fetch_lrcTimestampFormats_handled() {
        val syncedText =
            "[01:05:20] Colon format\\n" +
                "[02:10.5] One digit fraction\\n[03:15] No fraction"
        val json =
            """
            [
              {
                "id": 10,
                "trackName": "Formats",
                "artistName": "Artist",
                "duration": 180,
                "instrumental": false,
                "syncedLyrics": "$syncedText"
              }
            ]
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
                title = "Formats",
                artist = "Artist",
                durationMs = 180_000,
            )

        val lyrics = provider.fetch(query)
        assertNotNull(lyrics)
        assertEquals(3, lyrics!!.lines.size)

        // [01:05:20] -> 65_000 + 200 = 65200 ms
        assertEquals(65200L, lyrics.lines[0].startMs)
        assertEquals("Colon format", lyrics.lines[0].text)

        // [02:10.5] -> 130_000 + 500 = 130500 ms
        assertEquals(130500L, lyrics.lines[1].startMs)
        assertEquals("One digit fraction", lyrics.lines[1].text)

        // [03:15] -> 195_000 ms
        assertEquals(195000L, lyrics.lines[2].startMs)
        assertEquals("No fraction", lyrics.lines[2].text)
    }

    @Test
    fun fetch_plainLyricsFallback_whenNoSynced() {
        val json =
            """
            [
              {
                "id": 20,
                "trackName": "Plain Only",
                "artistName": "Artist",
                "duration": 150,
                "instrumental": false,
                "plainLyrics": "Line one\nLine two",
                "syncedLyrics": null
              }
            ]
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
                title = "Plain Only",
                artist = "Artist",
                durationMs = 150_000,
            )

        val lyrics = provider.fetch(query)
        assertNotNull(lyrics)
        assertEquals(LyricsSync.NONE, lyrics!!.sync)
        assertEquals(2, lyrics.lines.size)
        assertEquals("Line one", lyrics.lines[0].text)
        assertEquals("Line two", lyrics.lines[1].text)
        assertEquals("Line one\nLine two", lyrics.plainText)
    }

    @Test
    fun fetch_instrumentalOnly_returnsNull() {
        val json =
            """
            [
              {
                "id": 30,
                "trackName": "Instrumental",
                "artistName": "Artist",
                "duration": 180,
                "instrumental": true,
                "plainLyrics": null,
                "syncedLyrics": null
              }
            ]
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
                title = "Instrumental",
                artist = "Artist",
                durationMs = 180_000,
            )

        val lyrics = provider.fetch(query)
        assertNull(lyrics)
    }

    @Test
    fun fetch_durationExceedsTolerance_returnsNull() {
        val json =
            """
            [
              {
                "id": 40,
                "trackName": "Wrong Length",
                "artistName": "Artist",
                "duration": 220,
                "instrumental": false,
                "plainLyrics": "Hello world"
              }
            ]
            """.trimIndent()

        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .setHeader("Content-Type", "application/json")
                .body(json)
                .build(),
        )

        val provider = createProvider(durationToleranceMs = 2000)
        val query =
            LyricsQuery(
                title = "Wrong Length",
                artist = "Artist",
                durationMs = 210_000, // 210s vs 220s (diff 10s > 2s tolerance)
            )

        val lyrics = provider.fetch(query)
        assertNull(lyrics)
    }
}
