package dev.waris.spotify.model

internal data class Track(
    val id: SpotifyId,
    val title: String,
    val artists: List<Artist>,
    val durationMs: Long,
    val artworkUrl: String? = null,
    val isrc: Isrc? = null,
    val explicit: Boolean = false,
    val isLocal: Boolean = false,
) {
    val author: String
        get() = artists.joinToString(", ") { it.name }.ifEmpty { "Unknown" }
}
