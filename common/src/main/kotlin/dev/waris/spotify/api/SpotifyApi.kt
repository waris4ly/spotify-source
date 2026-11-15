package dev.waris.spotify.api

import dev.waris.spotify.auth.TokenKind
import dev.waris.spotify.model.Album
import dev.waris.spotify.model.Artist
import dev.waris.spotify.model.Episode
import dev.waris.spotify.model.Isrc
import dev.waris.spotify.model.Playlist
import dev.waris.spotify.model.Show
import dev.waris.spotify.model.SpotifyId
import dev.waris.spotify.model.Track

internal interface SpotifyApi {
    val name: String

    fun supports(operation: Operation): Boolean

    fun tokenKindFor(operation: Operation): TokenKind?

    fun search(
        query: String,
        limit: Int = 10,
        offset: Int = 0,
    ): List<Track>

    fun getTrack(id: SpotifyId): Track?

    fun getAlbum(
        id: SpotifyId,
        limit: Int = 500,
    ): Album?

    fun getPlaylist(
        id: SpotifyId,
        limit: Int = 1000,
    ): Playlist?

    fun getArtist(id: SpotifyId): Artist?

    fun getIsrc(id: SpotifyId): Isrc?

    fun getEpisode(
        id: SpotifyId,
        isChapter: Boolean = false,
    ): Episode? = null

    fun getShow(
        id: SpotifyId,
        isAudiobook: Boolean = false,
        limit: Int = 200,
    ): Show? = null

    fun suggest(query: String): List<Track> = emptyList()

    fun recommendations(
        seed: SpotifyId,
        limit: Int = 10,
    ): List<Track> = emptyList()

    fun similar(
        seed: SpotifyId,
        limit: Int = 10,
    ): List<Track> = emptyList()
}
