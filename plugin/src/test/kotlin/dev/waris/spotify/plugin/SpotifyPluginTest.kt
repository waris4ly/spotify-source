package dev.waris.spotify.plugin

import com.github.topi314.lavalyrics.LyricsManager
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager
import com.sedmelluq.discord.lavaplayer.track.AudioItem
import com.sedmelluq.discord.lavaplayer.track.AudioReference
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo
import dev.waris.spotify.SpotifyAudioSourceManager
import java.io.DataInput
import java.io.DataOutput
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpotifyPluginTest {
    private class ExistingSpotifySourceManager : AudioSourceManager {
        override fun getSourceName(): String = "spotify"

        override fun loadItem(
            manager: com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager,
            reference: AudioReference,
        ): AudioItem? = null

        override fun isTrackEncodable(track: AudioTrack): Boolean = false

        override fun encodeTrack(
            track: AudioTrack,
            output: DataOutput,
        ) {}

        override fun decodeTrack(
            trackInfo: AudioTrackInfo,
            input: DataInput,
        ): AudioTrack? = null

        override fun shutdown() {}
    }

    @Test
    fun configure_registersSpotifyAudioSourceManager() {
        val plugin = SpotifyPlugin(PluginSettings())
        val manager = DefaultAudioPlayerManager()
        try {
            plugin.configure(manager)
            val registered =
                manager.sourceManagers.filterIsInstance<SpotifyAudioSourceManager>()
            assertEquals(1, registered.size)
            assertEquals(manager, registered[0].playerManager)
        } finally {
            manager.shutdown()
        }
    }

    @Test
    fun configure_whenDisabled_doesNotRegisterSource() {
        val plugin = SpotifyPlugin(PluginSettings(enabled = false))
        val manager = DefaultAudioPlayerManager()
        try {
            plugin.configure(manager)
            val registered =
                manager.sourceManagers.filterIsInstance<SpotifyAudioSourceManager>()
            assertTrue(registered.isEmpty())
        } finally {
            manager.shutdown()
        }
    }

    @Test
    fun configure_whenMissingCredentialsAndInternalDisabled_staysUnregistered() {
        val plugin =
            SpotifyPlugin(
                PluginSettings(
                    clientId = "",
                    clientSecret = "",
                    internalBackend = false,
                ),
            )
        val manager = DefaultAudioPlayerManager()
        try {
            plugin.configure(manager)
            val registered =
                manager.sourceManagers.filterIsInstance<SpotifyAudioSourceManager>()
            assertTrue(registered.isEmpty())
        } finally {
            manager.shutdown()
        }
    }

    @Test
    fun configure_detectsExistingSpotifySourceAndRegistersAlongside() {
        val plugin = SpotifyPlugin(PluginSettings())
        val manager = DefaultAudioPlayerManager()
        try {
            manager.registerSourceManager(ExistingSpotifySourceManager())
            plugin.configure(manager)

            val spotifyManagers =
                manager.sourceManagers.filter {
                    it.sourceName.equals("spotify", ignoreCase = true)
                }
            assertEquals(2, spotifyManagers.size)
        } finally {
            manager.shutdown()
        }
    }

    @Test
    fun configure_withInvalidSettings_failsStartupWithClearMessage() {
        val settings = PluginSettings().apply { searchLimit = -5 }
        val plugin = SpotifyPlugin(settings)
        val manager = DefaultAudioPlayerManager()
        try {
            val ex =
                assertThrows(IllegalArgumentException::class.java) {
                    plugin.configure(manager)
                }
            assertTrue(ex.message?.contains("searchLimit") == true)
        } finally {
            manager.shutdown()
        }
    }

    @Test
    fun configure_registersSpotifyLyricsManager_whenEnabled() {
        val plugin = SpotifyPlugin(PluginSettings(lyrics = true))
        val lyricsManager = LyricsManager()
        try {
            plugin.configure(lyricsManager)
            val registered =
                lyricsManager.lyricsManagers.filterIsInstance<SpotifyLyricsManager>()
            assertEquals(1, registered.size)
        } finally {
            lyricsManager.shutdown()
        }
    }

    @Test
    fun configure_doesNotRegisterLyricsManager_whenLyricsDisabled() {
        val plugin = SpotifyPlugin(PluginSettings(lyrics = false))
        val lyricsManager = LyricsManager()
        try {
            plugin.configure(lyricsManager)
            val registered =
                lyricsManager.lyricsManagers.filterIsInstance<SpotifyLyricsManager>()
            assertTrue(registered.isEmpty())
        } finally {
            lyricsManager.shutdown()
        }
    }

    @Test
    fun configure_sharesLazySourceBetweenAudioPlayerManagerAndLyricsManager() {
        val plugin = SpotifyPlugin(PluginSettings(lyrics = true))
        val playerManager = DefaultAudioPlayerManager()
        val lyricsManager = LyricsManager()
        try {
            plugin.configure(lyricsManager)
            plugin.configure(playerManager)

            val source =
                playerManager.sourceManagers.filterIsInstance<SpotifyAudioSourceManager>().first()
            val lyricsSourceManager =
                lyricsManager.lyricsManagers.filterIsInstance<SpotifyLyricsManager>().first()

            val field =
                SpotifyLyricsManager::class.java.getDeclaredField("source").apply {
                    isAccessible = true
                }
            val underlyingSource = field.get(lyricsSourceManager)
            assertEquals(source, underlyingSource)
        } finally {
            playerManager.shutdown()
            lyricsManager.shutdown()
        }
    }
}
