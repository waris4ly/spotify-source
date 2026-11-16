package dev.waris.spotify.plugin

import com.github.topi314.lavalyrics.AudioLyricsManager
import com.github.topi314.lavalyrics.lyrics.AudioLyrics
import com.github.topi314.lavalyrics.lyrics.BasicAudioLyrics
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import dev.waris.spotify.SpotifyAudioSourceManager
import dev.waris.spotify.api.lyrics.LyricsSync
import java.time.Duration

internal class SpotifyLyricsManager(
    private val source: SpotifyAudioSourceManager,
) : AudioLyricsManager {
    override fun getSourceName(): String = "spotify"

    override fun loadLyrics(track: AudioTrack): AudioLyrics? {
        val lyrics = source.loadLyrics(track) ?: return null

        val lines: List<AudioLyrics.Line>? =
            if (lyrics.sync == LyricsSync.LINE) {
                lyrics.lines.map {
                    BasicAudioLyrics.BasicLine(
                        Duration.ofMillis(it.startMs),
                        it.endMs?.let { end -> Duration.ofMillis(end - it.startMs) },
                        it.text,
                    )
                }
            } else {
                null
            }

        return SpotifyAudioLyrics(
            provider = lyrics.providerLabel ?: lyrics.provider,
            text = lyrics.plainText,
            lines = lines,
            sync = lyrics.sync,
            language = lyrics.language,
            rightToLeft = lyrics.rightToLeft,
        )
    }

    override fun shutdown() = Unit
}
