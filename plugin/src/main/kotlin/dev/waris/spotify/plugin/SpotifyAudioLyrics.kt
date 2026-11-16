package dev.waris.spotify.plugin

import com.github.topi314.lavalyrics.lyrics.AudioLyrics
import com.github.topi314.lavalyrics.lyrics.BasicAudioLyrics
import dev.waris.spotify.api.lyrics.LyricsSync

internal class SpotifyAudioLyrics(
    provider: String?,
    text: String?,
    lines: List<AudioLyrics.Line>?,
    val sync: LyricsSync,
    val language: String?,
    val rightToLeft: Boolean,
) : BasicAudioLyrics("spotify", provider, text, lines)
