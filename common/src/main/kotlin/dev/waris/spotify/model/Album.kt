package dev.waris.spotify.model

internal data class Album(
    val id: SpotifyId,
    val name: String,
    val artists: List<Artist>,
    val tracks: List<Track>,
    val totalTracks: Int = tracks.size,
    val artworkUrl: String? = null,
) {
    val author: String
        get() = artists.joinToString(", ") { it.name }.ifEmpty { "Unknown" }
}
