package dev.waris.spotify.api.lyrics

internal interface LyricsProvider {
    val name: String

    fun isAvailable(): Boolean

    fun fetch(query: LyricsQuery): Lyrics?
}
