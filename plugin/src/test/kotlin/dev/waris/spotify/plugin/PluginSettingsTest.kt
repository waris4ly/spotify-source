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
    }

    @Test
    fun invalidLimits_throwIllegalArgumentException() {
        assertThrows(IllegalArgumentException::class.java) {
            PluginSettings(searchLimit = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PluginSettings(playlistLoadLimit = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PluginSettings(mirrorProviders = emptyList())
        }
    }

    @Test
    fun toOptions_mapsAllFieldsCorrectly() {
        val settings =
            PluginSettings(
                clientId = "cid",
                clientSecret = "csec",
                countryCode = "GB",
                searchLimit = 5,
            )
        val options = settings.toOptions()
        assertEquals("cid", options.clientId)
        assertEquals("csec", options.clientSecret)
        assertEquals("GB", options.countryCode)
        assertEquals(5, options.searchLimit)
    }
}
