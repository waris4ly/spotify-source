package dev.waris.spotify.api.lyrics

public enum class LyricsSync {
    LINE,
    NONE,
}

public data class LyricsLine(
    val startMs: Long,
    val endMs: Long?,
    val text: String,
)

public data class Lyrics(
    val provider: String,
    val providerLabel: String?,
    val sync: LyricsSync,
    val language: String?,
    val rightToLeft: Boolean,
    val lines: List<LyricsLine>,
) {
    public val plainText: String
        get() = lines.joinToString("\n") { it.text }
}
