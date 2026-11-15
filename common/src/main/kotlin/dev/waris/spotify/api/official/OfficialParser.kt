package dev.waris.spotify.api.official

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser
import dev.waris.spotify.model.Album
import dev.waris.spotify.model.Artist
import dev.waris.spotify.model.Base62
import dev.waris.spotify.model.Isrc
import dev.waris.spotify.model.Playlist
import dev.waris.spotify.model.SpotifyId
import dev.waris.spotify.model.Track

internal class OfficialParser(
    private val allowLocalFiles: Boolean = false,
) {
    fun parseTrack(
        json: JsonBrowser,
        fallbackArtwork: String? = null,
    ): Track? {
        if (json.isNull) {
            return null
        }

        val idText = json.get("id").text()
        if (idText.isNullOrEmpty() || !Base62.isValid(idText)) {
            return null
        }

        val name = json.get("name").text()
        if (name.isNullOrEmpty()) {
            return null
        }

        val isLocal = json.get("is_local").asBoolean(false)
        val type = json.get("type").text()
        // filter out podcast episodes and local files unless explicitly allowed
        if (type == "episode" || (isLocal && !allowLocalFiles)) {
            return null
        }

        val artists = parseArtists(json.get("artists"))
        val durationMs = json.get("duration_ms").asLong(0)
        val explicit = json.get("explicit").asBoolean(false)

        val isrcText = json.get("external_ids").get("isrc").text()
        val isrc = if (!isrcText.isNullOrEmpty()) Isrc(isrcText) else null

        val artworkUrl = pickWidestImage(json.get("album").get("images")) ?: fallbackArtwork

        return Track(
            id = SpotifyId(idText),
            title = name,
            artists = artists,
            durationMs = durationMs,
            artworkUrl = artworkUrl,
            isrc = isrc,
            explicit = explicit,
            isLocal = isLocal,
        )
    }

    fun parseAlbum(json: JsonBrowser): Album? {
        if (json.isNull) {
            return null
        }

        val idText = json.get("id").text()
        if (idText.isNullOrEmpty() || !Base62.isValid(idText)) {
            return null
        }

        val name = json.get("name").text()
        if (name.isNullOrEmpty()) {
            return null
        }

        val artists = parseArtists(json.get("artists"))
        val artworkUrl = pickWidestImage(json.get("images"))
        val totalTracks = json.get("total_tracks").asInt(0)

        val trackItems = json.get("tracks").get("items")
        val tracks = parseSimplifiedTracks(trackItems, artworkUrl, artists)

        return Album(
            id = SpotifyId(idText),
            name = name,
            artists = artists,
            tracks = tracks,
            totalTracks = if (totalTracks > 0) totalTracks else tracks.size,
            artworkUrl = artworkUrl,
        )
    }

    fun parseAlbumTracksPage(
        json: JsonBrowser,
        artworkUrl: String? = null,
        defaultArtists: List<Artist> = emptyList(),
    ): List<Track> {
        val items = if (json.get("items").isList) json.get("items") else json
        return parseSimplifiedTracks(items, artworkUrl, defaultArtists)
    }

    fun parseSimplifiedTracks(
        json: JsonBrowser,
        artworkUrl: String? = null,
        defaultArtists: List<Artist> = emptyList(),
    ): List<Track> {
        if (json.isNull || !json.isList) {
            return emptyList()
        }

        return json.values().mapNotNull { item ->
            if (item.isNull) {
                return@mapNotNull null
            }

            val idText = item.get("id").text()
            if (idText.isNullOrEmpty() || !Base62.isValid(idText)) {
                return@mapNotNull null
            }

            val name = item.get("name").text()
            if (name.isNullOrEmpty()) {
                return@mapNotNull null
            }

            val isLocal = item.get("is_local").asBoolean(false)
            if (isLocal && !allowLocalFiles) {
                return@mapNotNull null
            }

            val artists = parseArtists(item.get("artists")).ifEmpty { defaultArtists }
            val durationMs = item.get("duration_ms").asLong(0)
            val explicit = item.get("explicit").asBoolean(false)

            Track(
                id = SpotifyId(idText),
                title = name,
                artists = artists,
                durationMs = durationMs,
                artworkUrl = artworkUrl,
                isrc = null,
                explicit = explicit,
                isLocal = isLocal,
            )
        }
    }

    fun parseSearch(json: JsonBrowser): List<Track> {
        if (json.isNull) {
            return emptyList()
        }
        val items = json.get("tracks").get("items")
        if (items.isNull || !items.isList) {
            return emptyList()
        }
        return items.values().mapNotNull { item ->
            parseTrack(item)
        }
    }

    fun parseArtist(json: JsonBrowser): Artist? {
        if (json.isNull) {
            return null
        }
        val idText = json.get("id").text()
        if (idText.isNullOrEmpty() || !Base62.isValid(idText)) {
            return null
        }
        val name = json.get("name").text()
        if (name.isNullOrEmpty()) {
            return null
        }
        val artworkUrl = pickWidestImage(json.get("images"))
        return Artist(
            id = SpotifyId(idText),
            name = name,
            artworkUrl = artworkUrl,
        )
    }

    fun parsePlaylistMetadata(json: JsonBrowser): Playlist? {
        if (json.isNull) {
            return null
        }
        val idText = json.get("id").text()
        if (idText.isNullOrEmpty() || !Base62.isValid(idText)) {
            return null
        }
        val name = json.get("name").text() ?: "Unknown Playlist"
        val owner = json.get("owner").get("display_name").text() ?: "Unknown"
        val artworkUrl = pickWidestImage(json.get("images"))
        val total = json.get("tracks").get("total").asInt(0)

        return Playlist(
            id = SpotifyId(idText),
            name = name,
            owner = owner,
            tracks = emptyList(),
            totalTracks = total,
            artworkUrl = artworkUrl,
        )
    }

    private fun parseArtists(json: JsonBrowser): List<Artist> {
        if (json.isNull || !json.isList) {
            return emptyList()
        }
        return json.values().mapNotNull { a ->
            val name = a.get("name").text() ?: return@mapNotNull null
            val idText = a.get("id").text()
            val id =
                if (!idText.isNullOrEmpty() && Base62.isValid(idText)) {
                    SpotifyId(idText)
                } else {
                    SpotifyId("0000000000000000000000")
                }
            Artist(id = id, name = name)
        }
    }

    private fun pickWidestImage(images: JsonBrowser): String? {
        if (images.isNull || !images.isList) {
            return null
        }
        var bestUrl: String? = null
        var maxWidth = -1

        for (image in images.values()) {
            val url = image.get("url").text() ?: continue
            val width = image.get("width").asInt(0)
            if (width > maxWidth) {
                maxWidth = width
                bestUrl = url
            }
        }
        return bestUrl
    }
}
