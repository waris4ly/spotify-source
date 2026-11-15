package dev.waris.spotify.api.official

import dev.waris.spotify.api.Operation
import dev.waris.spotify.api.SpotifyApi
import dev.waris.spotify.api.SpotifyHttp
import dev.waris.spotify.auth.TokenKind
import dev.waris.spotify.model.Album
import dev.waris.spotify.model.Artist
import dev.waris.spotify.model.Isrc
import dev.waris.spotify.model.Playlist
import dev.waris.spotify.model.SpotifyId
import dev.waris.spotify.model.Track
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.apache.http.client.methods.HttpGet

internal class OfficialApi(
    private val http: SpotifyHttp,
    private val countryCode: String = "US",
    private val baseUrl: String = DEFAULT_BASE_URL,
    allowLocalFiles: Boolean = false,
) : SpotifyApi {
    private val parser = OfficialParser(allowLocalFiles)

    override val name: String = "official"

    // official client credentials flow does not allow reading public playlists
    override fun supports(operation: Operation): Boolean =
        when (operation) {
            is Operation.Search -> true
            is Operation.GetTrack -> true
            is Operation.GetAlbum -> true
            is Operation.GetArtist -> true
            is Operation.GetIsrc -> true
            is Operation.GetPlaylist,
            is Operation.GetEpisode,
            is Operation.GetShow,
            is Operation.Suggest,
            is Operation.Recommendations,
            is Operation.Similar,
            -> false
        }

    override fun tokenKindFor(operation: Operation): TokenKind? = TokenKind.CLIENT

    override fun search(
        query: String,
        limit: Int,
        offset: Int,
    ): List<Track> {
        val cappedLimit = limit.coerceIn(1, 10)
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        val url =
            "$baseUrl/search?q=$encoded&type=track&limit=$cappedLimit&offset=$offset&market=$countryCode"
        val json = http.execute(HttpGet(url), TokenKind.CLIENT)
        return parser.parseSearch(json)
    }

    override fun getTrack(id: SpotifyId): Track? {
        val url = "$baseUrl/tracks/$id?market=$countryCode"
        val json = http.execute(HttpGet(url), TokenKind.CLIENT)
        return parser.parseTrack(json)
    }

    override fun getAlbum(
        id: SpotifyId,
        limit: Int,
    ): Album? {
        val url = "$baseUrl/albums/$id?market=$countryCode"
        val json = http.execute(HttpGet(url), TokenKind.CLIENT)
        val album = parser.parseAlbum(json) ?: return null

        if (album.tracks.size >= album.totalTracks || album.tracks.size >= limit) {
            return album
        }

        var nextUrl: String? = json.get("tracks").get("next").text()
        val allTracks = album.tracks.toMutableList()

        while (!nextUrl.isNullOrEmpty() && allTracks.size < limit) {
            val pageJson = http.execute(HttpGet(nextUrl), TokenKind.CLIENT)
            val pageTracks = parser.parseAlbumTracksPage(pageJson, album.artworkUrl, album.artists)
            if (pageTracks.isEmpty()) {
                break
            }
            for (track in pageTracks) {
                allTracks.add(track)
                if (allTracks.size >= limit) {
                    break
                }
            }
            nextUrl = pageJson.get("next").text()
        }

        return album.copy(tracks = allTracks)
    }

    override fun getPlaylist(
        id: SpotifyId,
        limit: Int,
    ): Playlist? = null

    override fun getArtist(id: SpotifyId): Artist? {
        val url = "$baseUrl/artists/$id"
        val json = http.execute(HttpGet(url), TokenKind.CLIENT)
        return parser.parseArtist(json)
    }

    override fun getIsrc(id: SpotifyId): Isrc? = getTrack(id)?.isrc

    companion object {
        const val DEFAULT_BASE_URL: String = "https://api.spotify.com/v1"
    }
}
