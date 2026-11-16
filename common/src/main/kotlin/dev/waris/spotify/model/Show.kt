package dev.waris.spotify.model

internal data class Show(
    val id: SpotifyId,
    val name: String,
    val publisher: String,
    val episodes: List<Episode>,
    val totalEpisodes: Int = episodes.size,
    val artworkUrl: String? = null,
    val isAudiobook: Boolean = false,
    val truncated: Boolean = false,
)
