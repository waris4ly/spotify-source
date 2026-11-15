package dev.waris.spotify.plugin

import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import dev.arbjerg.lavalink.api.AudioPluginInfoModifier
import dev.waris.spotify.SpotifyAudioPlaylist
import dev.waris.spotify.SpotifyAudioTrack
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.springframework.stereotype.Service

@Service
public class TrackPluginInfo : AudioPluginInfoModifier {
    // expose extended spotify metadata through lavalink plugin info hook
    override fun modifyAudioTrackPluginInfo(track: AudioTrack): JsonObject? {
        if (track !is SpotifyAudioTrack) {
            return null
        }
        val uri = track.info.uri ?: ""
        val type =
            when {
                uri.contains("/episode/") -> "episode"
                uri.contains("/chapter/") -> "chapter"
                uri.startsWith("spotify:local:") -> "local"
                else -> "track"
            }
        return buildJsonObject {
            put("type", type)
            put("url", uri)
            put("author", track.info.author)
            put("artworkUrl", track.info.artworkUrl)
            put("isrc", track.info.isrc)
            if (type == "episode" || type == "chapter") {
                put("show", track.info.author)
            }
            if (type == "local") {
                put("localFile", true)
            }
            if (!track.canvasUrl.isNullOrBlank()) {
                put("canvasUrl", track.canvasUrl)
            }
            val trackColors = track.colors
            if (trackColors != null && trackColors.isNotEmpty()) {
                put(
                    "colors",
                    buildJsonObject {
                        for ((k, v) in trackColors) {
                            put(k, v)
                        }
                    },
                )
            }
        }
    }

    override fun modifyAudioPlaylistPluginInfo(playlist: AudioPlaylist): JsonObject? {
        if (playlist is SpotifyAudioPlaylist) {
            return buildJsonObject {
                put("type", playlist.type)
                put("url", playlist.url)
                put("author", playlist.author)
                put("artworkUrl", playlist.artworkUrl)
                put("totalTracks", playlist.totalTracks)
            }
        }
        return null
    }
}
