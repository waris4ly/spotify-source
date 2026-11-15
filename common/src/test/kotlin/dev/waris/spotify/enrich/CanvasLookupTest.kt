package dev.waris.spotify.enrich

import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools
import dev.waris.spotify.api.SpotifyHttp
import dev.waris.spotify.auth.AccessToken
import dev.waris.spotify.auth.TokenKind
import dev.waris.spotify.auth.TokenManager
import dev.waris.spotify.auth.TokenProvider
import dev.waris.spotify.model.SpotifyId
import kotlin.time.Duration.Companion.hours
import kotlin.time.TimeSource
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CanvasLookupTest {
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

    @Test
    fun fetchCanvasUrl_success() {
        val json =
            """
            {
              "canvases": [
                {
                  "id": "canvas_123",
                  "canvas_url": "https://canvaz.scdn.co/upload/video.mp4",
                  "track_uri": "spotify:track:4eoOQgFQ0rLMKNP8KcUNWc"
                }
              ]
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

        val tokenProvider =
            object : TokenProvider {
                override val kind = TokenKind.ACCOUNT
                override val isCoolingDown = false

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
        val canvasLookup = CanvasLookup(http, endpointUrl = server.url("/canvases").toString())

        val canvasUrl = canvasLookup.fetchCanvasUrl(SpotifyId("4eoOQgFQ0rLMKNP8KcUNWc"))
        assertNotNull(canvasUrl)
        assertEquals("https://canvaz.scdn.co/upload/video.mp4", canvasUrl)

        val recorded = server.takeRequest()
        assertEquals("Bearer account_token", recorded.headers["Authorization"])
    }

    @Test
    fun fetchCanvasUrl_errorReturnsNull() {
        server.enqueue(
            MockResponse
                .Builder()
                .code(404)
                .setHeader("Content-Type", "application/json")
                .body("""{"error": "not found"}""")
                .build(),
        )

        val tokenProvider =
            object : TokenProvider {
                override val kind = TokenKind.ACCOUNT
                override val isCoolingDown = false

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
        val canvasLookup = CanvasLookup(http, endpointUrl = server.url("/canvases").toString())

        val canvasUrl = canvasLookup.fetchCanvasUrl(SpotifyId("4eoOQgFQ0rLMKNP8KcUNWc"))
        assertNull(canvasUrl)
    }
}
