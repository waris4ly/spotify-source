package dev.waris.spotify.api.internal

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager
import dev.waris.spotify.SpotifyOptions
import dev.waris.spotify.api.Operation
import dev.waris.spotify.api.RetryPolicy
import dev.waris.spotify.api.SpotifyHttp
import dev.waris.spotify.auth.AccessToken
import dev.waris.spotify.auth.TokenKind
import dev.waris.spotify.auth.TokenManager
import dev.waris.spotify.auth.TokenProvider
import dev.waris.spotify.error.SpotifyException
import dev.waris.spotify.model.SpotifyId
import kotlin.time.Duration.Companion.hours
import kotlin.time.TimeSource
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class InternalApiTest {
    private lateinit var server: MockWebServer
    private lateinit var httpInterfaceManager: HttpInterfaceManager
    private lateinit var tokenManager: TokenManager
    private lateinit var spotifyHttp: SpotifyHttp
    private lateinit var api: InternalApi

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager()

        val provider =
            object : TokenProvider {
                override val kind: TokenKind = TokenKind.ANONYMOUS

                override fun fetchToken(): AccessToken =
                    AccessToken(
                        value = "anon-token-123",
                        expiresAt = TimeSource.Monotonic.markNow() + 1.hours,
                    )
            }

        tokenManager = TokenManager(mapOf(TokenKind.ANONYMOUS to provider))
        spotifyHttp =
            SpotifyHttp(
                httpInterfaceManager = httpInterfaceManager,
                tokenManager = tokenManager,
                retryPolicy =
                    RetryPolicy(
                        max401Retries = 1,
                        max5xxRetries = 0,
                        max429Retries = 0,
                    ),
                sleeper = {},
            )

        api =
            InternalApi(
                http = spotifyHttp,
                options = SpotifyOptions(),
                endpointUrl = server.url("/pathfinder/v2/query").toString(),
            )
    }

    @AfterEach
    fun tearDown() {
        server.close()
        httpInterfaceManager.close()
        tokenManager.close()
    }

    @Test
    fun supports_expectedOperations() {
        val id = SpotifyId("4cOdK2wGLETKBW3PvgPWqT")
        assertTrue(api.supports(Operation.Search("test")))
        assertTrue(api.supports(Operation.GetTrack(id)))
        assertTrue(api.supports(Operation.GetAlbum(id)))
        assertTrue(api.supports(Operation.GetPlaylist(id)))
        assertTrue(api.supports(Operation.GetArtist(id)))
        assertFalse(api.supports(Operation.GetIsrc(id)))
    }

    @Test
    fun getPlaylist_cutsShortWhenSubsequentPageFails() {
        val page1Json =
            """
            {
              "data": {
                "playlistV2": {
                  "__typename": "Playlist",
                  "uri": "spotify:playlist:37i9dQZF1DXcBWIGoYBM5M",
                  "name": "Today's Top Hits",
                  "ownerV2": {"data": {"name": "Spotify"}},
                  "images": {"items": []},
                  "content": {
                    "totalCount": 50,
                    "items": [
                      {
                        "itemV2": {
                          "data": {
                            "__typename": "Track",
                            "uri": "spotify:track:4cOdK2wGLETKBW3PvgPWqT",
                            "name": "Track One",
                            "trackDuration": {"totalMilliseconds": 200000},
                            "artists": {"items": [{"profile": {"name": "Artist One"}}]}
                          }
                        }
                      }
                    ]
                  }
                }
              }
            }
            """.trimIndent()

        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .setHeader("Content-Type", "application/json")
                .body(page1Json)
                .build(),
        )

        server.enqueue(
            MockResponse
                .Builder()
                .code(500)
                .setHeader("Content-Type", "application/json")
                .body("""{"error": "Internal Server Error"}""")
                .build(),
        )

        val id = SpotifyId("37i9dQZF1DXcBWIGoYBM5M")
        val playlist = api.getPlaylist(id, limit = 50)

        assertNotNull(playlist)
        assertEquals("Today's Top Hits", playlist?.name)
        assertEquals(50, playlist?.totalTracks)
        assertEquals(1, playlist?.tracks?.size)
        assertEquals("Track One", playlist?.tracks?.get(0)?.title)
    }

    @Test
    fun getPlaylist_firstPageFailureThrows() {
        server.enqueue(
            MockResponse
                .Builder()
                .code(500)
                .setHeader("Content-Type", "application/json")
                .body("""{"error": "Internal Server Error"}""")
                .build(),
        )

        val id = SpotifyId("37i9dQZF1DXcBWIGoYBM5M")
        assertThrows(SpotifyException::class.java) {
            api.getPlaylist(id, limit = 10)
        }
    }

    @Test
    fun getTrack_returnsParsedTrack() {
        val trackJson =
            """
            {
              "data": {
                "trackUnion": {
                  "__typename": "Track",
                  "uri": "spotify:track:4cOdK2wGLETKBW3PvgPWqT",
                  "name": "Never Gonna Give You Up",
                  "trackDuration": {"totalMilliseconds": 213573},
                  "artists": {"items": [{"profile": {"name": "Rick Astley"}}]},
                  "albumOfTrack": {
                    "coverArt": {
                      "sources": [{"url": "https://i.scdn.co/image/abc", "width": 640}]
                    }
                  }
                }
              }
            }
            """.trimIndent()

        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .setHeader("Content-Type", "application/json")
                .body(trackJson)
                .build(),
        )

        val track = api.getTrack(SpotifyId("4cOdK2wGLETKBW3PvgPWqT"))
        assertNotNull(track)
        assertEquals("Never Gonna Give You Up", track?.title)
        assertEquals("Rick Astley", track?.author)
        assertEquals(213573, track?.durationMs)
    }

    @Test
    fun search_queryWithSpecialAndControlCharactersProducesValidJson() {
        val query = "hello\nworld\t\"test\"\u0001foo"
        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .setHeader("Content-Type", "application/json")
                .body("""{"data": {"searchV2": {"tracksV2": {"items": []}}}}""")
                .build(),
        )

        api.search(query, limit = 10, offset = 0)

        val recorded = server.takeRequest()
        val requestBody = recorded.body?.utf8() ?: ""
        val parsedJson = JsonBrowser.parse(requestBody)
        assertFalse(parsedJson.isNull())
        val searchTerm = parsedJson.get("variables").get("searchTerm").text()
        assertEquals(query, searchTerm)
    }
}
