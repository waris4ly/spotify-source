package dev.waris.spotify.model

internal data class Episode(
    val id: SpotifyId,
    val title: String,
    val showName: String,
    val durationMs: Long,
    val artworkUrl: String? = null,
    val explicit: Boolean = false,
    val isChapter: Boolean = false,
)
