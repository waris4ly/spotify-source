package dev.waris.spotify.model

internal data class Playlist(
    val id: SpotifyId,
    val name: String,
    val owner: String,
    val tracks: List<Track>,
    val totalTracks: Int = tracks.size,
    val artworkUrl: String? = null,
)
