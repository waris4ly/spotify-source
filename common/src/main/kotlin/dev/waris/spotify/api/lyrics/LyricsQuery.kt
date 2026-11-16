package dev.waris.spotify.api.lyrics

import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import dev.waris.spotify.SpotifyAudioTrack
import dev.waris.spotify.model.Base62
import dev.waris.spotify.model.Isrc
import dev.waris.spotify.model.SpotifyId

internal data class LyricsQuery(
    val title: String,
    val artist: String,
    val durationMs: Long,
    val isrc: Isrc? = null,
    val spotifyId: SpotifyId? = null,
) {
    val cacheKey: String
        get() =
            spotifyId?.value
                ?: isrc?.normalized
                ?: "${artist.lowercase()}|${title.lowercase()}|${durationMs / 1000}"

    companion object {
        fun from(track: AudioTrack): LyricsQuery {
            val info = track.info
            val isSpotifyTrack =
                track is SpotifyAudioTrack &&
                    info.uri?.contains("/track/") == true &&
                    Base62.isValid(info.identifier)
            return LyricsQuery(
                title = info.title,
                artist = info.author,
                durationMs = info.length,
                isrc = info.isrc?.takeIf { it.isNotBlank() }?.let(::Isrc),
                spotifyId = if (isSpotifyTrack) SpotifyId(info.identifier) else null,
            )
        }
    }
}
