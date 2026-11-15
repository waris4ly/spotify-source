package dev.waris.spotify.model

internal data class Artist(
    val id: SpotifyId,
    val name: String,
    val artworkUrl: String? = null,
    val topTracks: List<Track> = emptyList(),
)
