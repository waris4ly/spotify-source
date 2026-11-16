package dev.waris.spotify.plugin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PluginSettingsTest {
    @Test
    fun defaultSettings_matchSpecification() {
        val settings = PluginSettings()
        assertTrue(settings.enabled)
        assertEquals("", settings.clientId)
        assertEquals("", settings.clientSecret)
        assertEquals("", settings.accountCookie)
        assertEquals("", settings.anonymousTokenUrl)
        assertEquals("US", settings.countryCode)
        assertTrue(settings.internalBackend)
        assertEquals(10, settings.searchLimit)
        assertEquals(1000, settings.playlistLoadLimit)
        assertEquals(500, settings.albumLoadLimit)
        assertEquals(200, settings.showLoadLimit)
        assertFalse(settings.allowLocalFiles)
        assertFalse(settings.extractColors)
        assertFalse(settings.canvas)
        assertEquals(10, settings.recommendationLimit)
        assertEquals(10, settings.requestTimeoutSeconds)
        assertEquals(5000L, settings.mirrorDurationToleranceMs)
        assertEquals(30, settings.metadataCacheMinutes)
        assertEquals(360, settings.mirrorCacheMinutes)
        assertEquals(2, settings.mirrorProviders.size)
        assertTrue(settings.lyrics)
        assertEquals(listOf("spotify", "lrclib"), settings.lyricsProviders)
        assertEquals(720, settings.lyricsCacheMinutes)
        assertEquals(30, settings.lyricsMissCacheMinutes)
    }

    @Test
    fun toOptions_invalidLimits_throwIllegalArgumentException() {
        val settings = PluginSettings()
        settings.searchLimit = 0
        val ex1 =
            assertThrows(IllegalArgumentException::class.java) {
                settings.toOptions()
            }
        assertTrue(ex1.message?.contains("searchLimit") == true)

        settings.searchLimit = 10
        settings.playlistLoadLimit = -1
        val ex2 =
            assertThrows(IllegalArgumentException::class.java) {
                settings.toOptions()
            }
        assertTrue(ex2.message?.contains("playlistLoadLimit") == true)

        settings.playlistLoadLimit = 1000
        settings.mirrorProviders = emptyList()
        val ex3 =
            assertThrows(IllegalArgumentException::class.java) {
                settings.toOptions()
            }
        assertTrue(ex3.message?.contains("mirrorProviders") == true)
    }

    @Test
    fun toOptions_mapsAllFieldsCorrectly() {
        val settings =
            PluginSettings(
                clientId = "cid",
                clientSecret = "csec",
                countryCode = "GB",
                searchLimit = 5,
                lyrics = false,
                lyricsProviders = listOf("lrclib"),
                lyricsCacheMinutes = 120,
                lyricsMissCacheMinutes = 15,
            )
        val options = settings.toOptions()
        assertEquals("cid", options.clientId)
        assertEquals("csec", options.clientSecret)
        assertEquals("GB", options.countryCode)
        assertEquals(5, options.searchLimit)
        assertEquals(false, options.lyrics)
        assertEquals(listOf("lrclib"), options.lyricsProviders)
        assertEquals(120, options.lyricsCacheMinutes)
        assertEquals(15, options.lyricsMissCacheMinutes)
    }

    @Test
    fun toString_masksClientSecretAccountCookieAndAnonymousTokenUrl() {
        val settings =
            PluginSettings(
                clientId = "public-client-id",
                clientSecret = "super-secret-key-12345",
                accountCookie = "sp_dc=secret-cookie-val-98765",
                anonymousTokenUrl = "https://secret-token-service.internal",
            )
        val str = settings.toString()

        assertFalse(str.contains("super-secret-key-12345"))
        assertFalse(str.contains("secret-cookie-val-98765"))
        assertFalse(str.contains("https://secret-token-service.internal"))
        assertTrue(str.contains("clientSecret=***"))
        assertTrue(str.contains("accountCookie=***"))
        assertTrue(str.contains("anonymousTokenUrl=***"))
    }

    @Test
    fun toString_showsUnsetWhenSecretsAreBlank() {
        val settings = PluginSettings()
        val str = settings.toString()

        assertTrue(str.contains("clientSecret=<unset>"))
        assertTrue(str.contains("accountCookie=<unset>"))
        assertTrue(str.contains("anonymousTokenUrl=<unset>"))
    }
}
