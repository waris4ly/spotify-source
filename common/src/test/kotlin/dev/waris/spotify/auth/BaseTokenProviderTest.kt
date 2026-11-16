package dev.waris.spotify.auth

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser
import dev.waris.spotify.error.SpotifyException
import kotlin.time.Duration.Companion.minutes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BaseTokenProviderTest {
    @Test
    fun parseAccessToken_handlesExpiresInFormat() {
        val json =
            JsonBrowser.parse(
                """
                {
                    "access_token": "token-expires-in-1800",
                    "expires_in": 1800
                }
                """.trimIndent(),
            )

        val token = parseAccessToken(json)
        assertEquals("token-expires-in-1800", token.value)
        assertEquals(1800L, token.expiresInSeconds)
        assertFalse(token.isExpired())
    }

    @Test
    fun parseAccessToken_handlesTimestampMsFormat() {
        val futureExpiryMs = System.currentTimeMillis() + 120_000L
        val json =
            JsonBrowser.parse(
                """
                {
                    "accessToken": "token-timestamp-ms",
                    "accessTokenExpirationTimestampMs": $futureExpiryMs
                }
                """.trimIndent(),
            )

        val token = parseAccessToken(json)
        assertEquals("token-timestamp-ms", token.value)
        assertTrue(token.expiresInSeconds in 115L..120L)
        assertFalse(token.isExpired())
    }

    @Test
    fun parseAccessToken_enforcesSixtySecondsFloorOnPastTimestamp() {
        val pastExpiryMs = System.currentTimeMillis() - 50_000L
        val json =
            JsonBrowser.parse(
                """
                {
                    "token": "token-floor-60",
                    "accessTokenExpirationTimestampMs": $pastExpiryMs
                }
                """.trimIndent(),
            )

        val token = parseAccessToken(json)
        assertEquals("token-floor-60", token.value)
        assertEquals(60L, token.expiresInSeconds)
    }

    @Test
    fun parseAccessToken_missingTokenThrowsSpotifyException() {
        val json = JsonBrowser.parse("""{"error": "no token"}""")
        assertThrows(SpotifyException::class.java) {
            parseAccessToken(json)
        }
    }

    @Test
    fun baseTokenProvider_coolDownUpdatesCooldownState() {
        val provider =
            object : BaseTokenProvider() {
                override val kind: TokenKind = TokenKind.CLIENT

                override fun fetchToken(): AccessToken = throw UnsupportedOperationException()
            }

        assertFalse(provider.isCoolingDown)
        provider.coolDown(5.minutes)
        assertTrue(provider.isCoolingDown)
    }
}
