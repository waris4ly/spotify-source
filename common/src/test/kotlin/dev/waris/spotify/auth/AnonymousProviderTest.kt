package dev.waris.spotify.auth

import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager
import dev.waris.spotify.error.SpotifyException
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AnonymousProviderTest {
    private lateinit var server: MockWebServer
    private lateinit var httpInterfaceManager: HttpInterfaceManager

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager()
    }

    @AfterEach
    fun tearDown() {
        server.close()
        httpInterfaceManager.close()
    }

    @Test
    fun fetchToken_fromCustomService() {
        val customUrl = server.url("/api/token").toString()
        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .setHeader("Content-Type", "application/json")
                .body(
                    """
                    {
                      "accessToken": "custom-token-999",
                      "accessTokenExpirationTimestampMs": ${System.currentTimeMillis() + 3600000}
                    }
                    """.trimIndent(),
                ).build(),
        )

        val provider = AnonymousProvider(httpInterfaceManager, anonymousTokenUrl = customUrl)
        val token = provider.fetchToken()

        assertEquals("custom-token-999", token.value)
        assertTrue(token.expiresInSeconds in 3500..3600)
    }

    @Test
    fun fetchToken_fromWebEmbed() {
        val seedUrl = server.url("/embed/playlist/123").toString()
        val expiryMs = System.currentTimeMillis() + 3600000
        val html =
            """
            <!DOCTYPE html><html><body>
            <script id="__NEXT_DATA__" type="application/json">
            {
              "props": {
                "pageProps": {
                  "state": {
                    "data": {
                      "settings": {
                        "session": {
                          "accessToken": "embed-anon-token-abc",
                          "accessTokenExpirationTimestampMs": $expiryMs,
                          "isAnonymous": true
                        }
                      }
                    }
                  }
                }
              }
            }
            </script></body></html>
            """.trimIndent()

        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .setHeader("Content-Type", "text/html")
                .body(html)
                .build(),
        )

        val provider = AnonymousProvider(httpInterfaceManager, seedUrl = seedUrl)
        val token = provider.fetchToken()

        assertEquals("embed-anon-token-abc", token.value)
        assertTrue(token.expiresInSeconds in 3500..3600)
    }

    @Test
    fun fetchToken_customServiceFailureThrows() {
        val customUrl = server.url("/api/token").toString()
        server.enqueue(
            MockResponse
                .Builder()
                .code(500)
                .setHeader("Content-Type", "application/json")
                .body("""{"error": "Server error"}""")
                .build(),
        )

        val provider = AnonymousProvider(httpInterfaceManager, anonymousTokenUrl = customUrl)
        assertThrows(SpotifyException::class.java) {
            provider.fetchToken()
        }
    }
}
