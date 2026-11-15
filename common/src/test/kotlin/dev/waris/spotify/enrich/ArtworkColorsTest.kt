package dev.waris.spotify.enrich

import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools
import dev.waris.spotify.api.SpotifyHttp
import dev.waris.spotify.auth.AccessToken
import dev.waris.spotify.auth.TokenKind
import dev.waris.spotify.auth.TokenManager
import dev.waris.spotify.auth.TokenProvider
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

class ArtworkColorsTest {
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
    fun fetchColors_success() {
        val json =
            """
            {
              "data": {
                "extractedColors": [
                  {
                    "colorDark": { "hex": "#121212" },
                    "colorLight": { "hex": "#f5f5f5" },
                    "colorRaw": { "hex": "#303030" }
                  }
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

        val tokenProvider =
            object : TokenProvider {
                override val kind = TokenKind.ANONYMOUS
                override val isCoolingDown = false

                override fun coolDown(duration: kotlin.time.Duration) {}

                override fun fetchToken() =
                    AccessToken(
                        value = "anon_token",
                        expiresAt = TimeSource.Monotonic.markNow() + 1.hours,
                    )
            }

        val httpManager = HttpClientTools.createDefaultThreadLocalManager()
        val tokenManager = TokenManager(mapOf(TokenKind.ANONYMOUS to tokenProvider))
        val http = SpotifyHttp(httpManager, tokenManager)
        val artworkColors = ArtworkColors(http, endpointUrl = server.url("/query").toString())

        val colors =
            artworkColors.fetchColors(
                "https://i.scdn.co/image/ab67616d0000b2735755e164993798e0c9ef7d7a",
            )
        assertNotNull(colors)
        assertEquals("#121212", colors?.dark)
        assertEquals("#f5f5f5", colors?.light)
        assertEquals("#303030", colors?.raw)
    }

    @Test
    fun fetchColors_invalidUrlReturnsNull() {
        val httpManager = HttpClientTools.createDefaultThreadLocalManager()
        val tokenManager = TokenManager(emptyMap())
        val http = SpotifyHttp(httpManager, tokenManager)
        val artworkColors = ArtworkColors(http, endpointUrl = server.url("/query").toString())

        val colors = artworkColors.fetchColors("https://example.com/some/image.png")
        assertNull(colors)
    }
}
