package dev.waris.spotify.api

import dev.waris.spotify.model.SpotifyId

internal sealed interface Operation {
    data class Search(
        val query: String,
        val limit: Int = 10,
        val offset: Int = 0,
    ) : Operation

    data class GetTrack(
        val id: SpotifyId,
    ) : Operation

    data class GetAlbum(
        val id: SpotifyId,
        val limit: Int = 500,
    ) : Operation

    data class GetPlaylist(
        val id: SpotifyId,
        val limit: Int = 1000,
    ) : Operation

    data class GetArtist(
        val id: SpotifyId,
    ) : Operation

    data class GetIsrc(
        val id: SpotifyId,
    ) : Operation

    data class GetEpisode(
        val id: SpotifyId,
        val isChapter: Boolean = false,
    ) : Operation

    data class GetShow(
        val id: SpotifyId,
        val isAudiobook: Boolean = false,
        val limit: Int = 200,
    ) : Operation

    data class Suggest(
        val query: String,
    ) : Operation

    data class Recommendations(
        val seed: SpotifyId,
        val limit: Int = 10,
    ) : Operation

    data class Similar(
        val seed: SpotifyId,
        val limit: Int = 10,
    ) : Operation
}
