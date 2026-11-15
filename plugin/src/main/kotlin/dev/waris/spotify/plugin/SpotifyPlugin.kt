package dev.waris.spotify.plugin

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
) : AudioPlayerManagerConfiguration {
    private val log = LoggerFactory.getLogger(SpotifyPlugin::class.java)

    override fun configure(manager: AudioPlayerManager): AudioPlayerManager {
        if (!settings.enabled) {
            log.info("Spotify plugin is disabled by configuration")
            return manager
        }

        // warn if another spotify source manager like lavasrc is already registered
        val hasExistingSpotify =
            manager.sourceManagers.any { it.sourceName.equals("spotify", ignoreCase = true) }
        if (hasExistingSpotify) {
            log.warn(
                "detected existing source manager named 'spotify' (possibly LavaSrc); " +
                    "ensure plugins.lavasrc.sources.spotify is false to avoid conflicts",
            )
        }

        val missingOfficialCreds =
            settings.clientId.isBlank() || settings.clientSecret.isBlank()
        if (missingOfficialCreds && !settings.internalBackend) {
            log.warn(
                "Spotify official credentials missing and internalBackend is false; " +
                    "Spotify source stays unregistered",
            )
            return manager
        }

        var effectiveOptions = settings.toOptions()
        // canvas video loops require an account cookie so disable gracefully if cookie is missing
        if (settings.canvas && settings.accountCookie.isBlank()) {
            log.warn("canvas: true configured without accountCookie; disabling canvas feature")
            effectiveOptions = effectiveOptions.copy(canvas = false)
        }

        val sourceManager = SpotifyAudioSourceManager(effectiveOptions)
        manager.registerSourceManager(sourceManager)
        log.info("Spotify audio source registered successfully")
        return manager
    }
}
