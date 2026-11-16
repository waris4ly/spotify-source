package dev.waris.spotify.plugin

import com.github.topi314.lavalyrics.LyricsManager
import com.github.topi314.lavalyrics.api.LyricsManagerConfiguration
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import dev.arbjerg.lavalink.api.AudioPlayerManagerConfiguration
import dev.waris.spotify.SpotifyAudioSourceManager
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Service

@Service
@EnableConfigurationProperties(PluginSettings::class)
public class SpotifyPlugin(
    private val settings: PluginSettings,
) : AudioPlayerManagerConfiguration,
    LyricsManagerConfiguration {
    private val log = LoggerFactory.getLogger(SpotifyPlugin::class.java)

    private val source: SpotifyAudioSourceManager? by lazy { createSource() }

    override fun configure(manager: AudioPlayerManager): AudioPlayerManager {
        val active = source ?: return manager

        val hasExistingSpotify =
            manager.sourceManagers.any { it.sourceName.equals("spotify", ignoreCase = true) }
        if (hasExistingSpotify) {
            log.warn(
                "detected existing source manager named 'spotify' (possibly LavaSrc); " +
                    "ensure plugins.lavasrc.sources.spotify is false to avoid conflicts",
            )
        }

        manager.registerSourceManager(active)
        active.attach(manager)
        log.info("Spotify audio source registered successfully")
        return manager
    }

    override fun configure(manager: LyricsManager): LyricsManager {
        val active = source ?: return manager
        if (!settings.lyrics) {
            return manager
        }
        manager.registerLyricsManager(SpotifyLyricsManager(active))
        log.info("Spotify lyrics manager registered")
        return manager
    }

    private fun createSource(): SpotifyAudioSourceManager? {
        if (!settings.enabled) {
            log.info("Spotify plugin is disabled by configuration")
            return null
        }

        val missingOfficialCreds =
            settings.clientId.isBlank() || settings.clientSecret.isBlank()
        if (missingOfficialCreds && !settings.internalBackend) {
            log.warn(
                "Spotify official credentials missing and internalBackend is false; " +
                    "Spotify source stays unregistered",
            )
            return null
        }

        var effectiveOptions = settings.toOptions()
        if (settings.canvas && settings.accountCookie.isBlank()) {
            log.warn("canvas: true configured without accountCookie; disabling canvas feature")
            effectiveOptions = effectiveOptions.copy(canvas = false)
        }

        return SpotifyAudioSourceManager(effectiveOptions)
    }
}
