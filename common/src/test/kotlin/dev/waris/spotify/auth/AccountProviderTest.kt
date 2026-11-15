package dev.waris.spotify.auth

import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools
import dev.waris.spotify.error.SpotifyException
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AccountProviderTest {
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
    fun fetchToken_success() {
        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .setHeader("Content-Type", "application/json")
                .body(
                    """
                    {
                      "accessToken": "sp_account_token_123",
                      "accessTokenExpirationTimestampMs": ${System.currentTimeMillis() + 3600000}
                    }
                    """.trimIndent(),
                ).build(),
        )

        val provider =
            AccountProvider(
                accountCookie = "mock_sp_dc_cookie",
                httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager(),
                tokenUrl = server.url("/get_access_token").toString(),
            )

        val token = provider.fetchToken()
        assertNotNull(token)
        assertEquals("sp_account_token_123", token.value)

        val recorded = server.takeRequest()
        assertEquals("sp_dc=mock_sp_dc_cookie", recorded.headers["Cookie"])
    }

    @Test
    fun fetchToken_blankCookieThrows() {
        val provider =
            AccountProvider(
                accountCookie = "",
                httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager(),
                tokenUrl = server.url("/get_access_token").toString(),
            )

        assertThrows(SpotifyException::class.java) {
            provider.fetchToken()
        }
    }

    @Test
    fun fetchToken_errorStatusThrows() {
        server.enqueue(
            MockResponse
                .Builder()
                .code(401)
                .setHeader("Content-Type", "application/json")
                .body("""{"error": "invalid cookie"}""")
                .build(),
        )

        val provider =
            AccountProvider(
                accountCookie = "invalid_cookie",
                httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager(),
                tokenUrl = server.url("/get_access_token").toString(),
            )

        assertThrows(SpotifyException::class.java) {
            provider.fetchToken()
        }
    }
}
