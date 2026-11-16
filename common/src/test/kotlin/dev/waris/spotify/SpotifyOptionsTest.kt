package dev.waris.spotify

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpotifyOptionsTest {
    @Test
    fun lyricsOptions_validation() {
        assertThrows(IllegalArgumentException::class.java) {
            SpotifyOptions(lyricsProviders = emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            SpotifyOptions(lyricsProviders = listOf("invalid_provider"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            SpotifyOptions(lyricsCacheMinutes = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SpotifyOptions(lyricsMissCacheMinutes = -1)
        }
    }

    @Test
    fun toString_masksClientSecretAccountCookieAndAnonymousTokenUrl() {
        val options =
            SpotifyOptions(
                clientId = "public-client-id",
                clientSecret = "super-secret-key-12345",
                accountCookie = "sp_dc=secret-cookie-val-98765",
                anonymousTokenUrl = "https://secret-token-service.internal",
            )
        val str = options.toString()

        assertFalse(str.contains("super-secret-key-12345"))
        assertFalse(str.contains("secret-cookie-val-98765"))
        assertFalse(str.contains("https://secret-token-service.internal"))
        assertTrue(str.contains("clientSecret=***"))
        assertTrue(str.contains("accountCookie=***"))
        assertTrue(str.contains("anonymousTokenUrl=***"))
    }

    @Test
    fun toString_showsUnsetWhenSecretsAreBlank() {
        val options = SpotifyOptions()
        val str = options.toString()

        assertTrue(str.contains("clientSecret=<unset>"))
        assertTrue(str.contains("accountCookie=<unset>"))
        assertTrue(str.contains("anonymousTokenUrl=<unset>"))
    }
}
