package dev.waris.spotify.api.internal

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser
import dev.waris.spotify.error.SpotifyException
import dev.waris.spotify.model.Album
import dev.waris.spotify.model.Artist
import dev.waris.spotify.model.Base62
import dev.waris.spotify.model.Episode
import dev.waris.spotify.model.Isrc
import dev.waris.spotify.model.Playlist
import dev.waris.spotify.model.Show
import dev.waris.spotify.model.SpotifyId
import dev.waris.spotify.model.Track

internal class InternalParser(
    private val allowLocalFiles: Boolean = false,
) {
    fun parseTrack(json: JsonBrowser): Track? {
        if (json.isNull) {
            return null
        }
        val node =
            when {
                !json.get("data").get("trackUnion").isNull -> json.get("data").get("trackUnion")
                !json.get("trackUnion").isNull -> json.get("trackUnion")
                !json.get("itemV2").get("data").isNull -> json.get("itemV2").get("data")
                !json.get("data").isNull -> json.get("data")
                else -> json
            }
        return parseTrackNode(node)
    }

    fun parseAlbum(
        json: JsonBrowser,
        maxTracks: Int = 500,
    ): Album? {
        if (json.isNull) {
            return null
        }
        val albumUnion =
            when {
                !json.get("data").get("albumUnion").isNull -> json.get("data").get("albumUnion")
                !json.get("albumUnion").isNull -> json.get("albumUnion")
                else -> return null
            }

        if (albumUnion.get("__typename").text() == "NotFound") {
            return null
        }

        val uri = albumUnion.get("uri").text() ?: return null
        val idText = uri.substringAfterLast(":")
        if (!Base62.isValid(idText)) {
            return null
        }

        val name = albumUnion.get("name").text() ?: return null
        val artists = parseArtists(albumUnion.get("artists"))
        val artworkUrl = pickWidestImage(albumUnion.get("coverArt").get("sources"))
        val totalTracks = albumUnion.get("tracksV2").get("totalCount").asInt(0)

        val trackItems = albumUnion.get("tracksV2").get("items")
        val tracks =
            buildList {
                if (trackItems.isList) {
                    for (item in trackItems.values()) {
                        val trackNode = if (!item.get("track").isNull) item.get("track") else item
                        val track =
                            parseTrackNode(trackNode, fallbackArtwork = artworkUrl) ?: continue
                        add(track)
                        if (size >= maxTracks) {
                            break
                        }
                    }
                }
            }

        return Album(
            id = SpotifyId(idText),
            name = name,
            artists = artists,
            tracks = tracks,
            totalTracks = if (totalTracks > 0) totalTracks else tracks.size,
            artworkUrl = artworkUrl,
        )
    }

    fun parseArtist(json: JsonBrowser): Artist? {
        if (json.isNull) {
            return null
        }
        val artistUnion =
            when {
                !json.get("data").get("artistUnion").isNull -> json.get("data").get("artistUnion")
                !json.get("artistUnion").isNull -> json.get("artistUnion")
                else -> return null
            }

        if (artistUnion.get("__typename").text() == "NotFound") {
            return null
        }

        val uri = artistUnion.get("uri").text() ?: return null
        val idText = uri.substringAfterLast(":")
        if (!Base62.isValid(idText)) {
            return null
        }

        val name = artistUnion.get("profile").get("name").text() ?: return null
        val artworkUrl =
            pickWidestImage(artistUnion.get("visuals").get("avatarImage").get("sources"))

        val topTracksItems = artistUnion.get("discography").get("topTracks").get("items")
        val topTracks =
            buildList {
                if (topTracksItems.isList) {
                    for (item in topTracksItems.values()) {
                        val trackNode = if (!item.get("track").isNull) item.get("track") else item
                        val track = parseTrackNode(trackNode) ?: continue
                        add(track)
                    }
                }
            }

        return Artist(
            id = SpotifyId(idText),
            name = name,
            artworkUrl = artworkUrl,
            topTracks = topTracks,
        )
    }

    fun parseSearch(json: JsonBrowser): List<Track> {
        if (json.isNull) {
            return emptyList()
        }
        val tracksV2 =
            when {
                !json
                    .get("data")
                    .get("searchV2")
                    .get("tracksV2")
                    .isNull ->
                    json.get("data").get("searchV2").get("tracksV2")
                !json.get("searchV2").get("tracksV2").isNull -> json.get("searchV2").get("tracksV2")
                else -> return emptyList()
            }

        val items = tracksV2.get("items")
        if (!items.isList) {
            return emptyList()
        }

        return buildList {
            for (item in items.values()) {
                val node =
                    when {
                        !item.get("item").get("data").isNull -> item.get("item").get("data")
                        !item.get("itemV2").get("data").isNull -> item.get("itemV2").get("data")
                        !item.get("track").isNull -> item.get("track")
                        else -> item
                    }
                val track = parseTrackNode(node) ?: continue
                add(track)
            }
        }
    }

    fun parsePlaylist(
        json: JsonBrowser,
        maxTracks: Int = 1000,
    ): Playlist {
        val playlistV2 =
            when {
                !json.get("data").get("playlistV2").isNull -> json.get("data").get("playlistV2")
                !json.get("playlistV2").isNull -> json.get("playlistV2")
                else -> throw SpotifyException("Playlist is private or not found")
            }

        val typeName = playlistV2.get("__typename").text()
        if (typeName == "NotFound" || playlistV2.isNull) {
            throw SpotifyException("Playlist is private or not found")
        }

        val uri = playlistV2.get("uri").text()
        val idText = uri?.substringAfterLast(":") ?: ""
        val id =
            if (Base62.isValid(idText)) {
                SpotifyId(idText)
            } else {
                SpotifyId("0000000000000000000000")
            }

        val name = playlistV2.get("name").text() ?: "Unknown Playlist"
        val owner =
            playlistV2
                .get("ownerV2")
                .get("data")
                .get("name")
                .text()
                ?: playlistV2.get("owner").get("name").text()
                ?: "Unknown"

        val imageItems = playlistV2.get("images").get("items")
        val imageSources =
            if (imageItems.isList && imageItems.values().isNotEmpty()) {
                imageItems.values()[0].get("sources")
            } else {
                JsonBrowser.NULL_BROWSER
            }
        val artworkUrl = pickWidestImage(imageSources)

        val totalTracks = playlistV2.get("content").get("totalCount").asInt(0)
        val items = playlistV2.get("content").get("items")
        val tracks = parsePlaylistTrackItems(items, maxTracks, artworkUrl)

        return Playlist(
            id = id,
            name = name,
            owner = owner,
            tracks = tracks,
            totalTracks = if (totalTracks > 0) totalTracks else tracks.size,
            artworkUrl = artworkUrl,
        )
    }

    fun parsePlaylistTracks(
        json: JsonBrowser,
        maxTracks: Int = 1000,
        fallbackArtwork: String? = null,
    ): List<Track> {
        val playlistV2 =
            when {
                !json.get("data").get("playlistV2").isNull -> json.get("data").get("playlistV2")
                !json.get("playlistV2").isNull -> json.get("playlistV2")
                else -> return emptyList()
            }
        val items = playlistV2.get("content").get("items")
        return parsePlaylistTrackItems(items, maxTracks, fallbackArtwork)
    }

    private fun parsePlaylistTrackItems(
        items: JsonBrowser,
        maxTracks: Int,
        fallbackArtwork: String?,
    ): List<Track> {
        if (!items.isList) {
            return emptyList()
        }
        return buildList {
            for (wrapper in items.values()) {
                // skip empty or unplayable playlist slots
                if (wrapper.isNull) {
                    continue
                }
                // unwrap polymorphic node schemas returned by spotify graphql
                val node =
                    when {
                        !wrapper.get("itemV2").get("data").isNull ->
                            wrapper.get("itemV2").get("data")
                        !wrapper.get("item").get("data").isNull -> wrapper.get("item").get("data")
                        !wrapper.get("item").isNull -> wrapper.get("item")
                        !wrapper.get("track").isNull -> wrapper.get("track")
                        else -> wrapper
                    }
                val track = parseTrackNode(node, wrapper, fallbackArtwork) ?: continue
                add(track)
                if (size >= maxTracks) {
                    break
                }
            }
        }
    }

    private fun parseTrackNode(
        node: JsonBrowser,
        wrapper: JsonBrowser = JsonBrowser.NULL_BROWSER,
        fallbackArtwork: String? = null,
    ): Track? {
        if (node.isNull) {
            return null
        }

        val typeName = node.get("__typename").text()
        val wrapperTypeName = wrapper.get("__typename").text()
        if (typeName == "Episode" ||
            typeName == "PodcastEpisode" ||
            typeName == "NotFound" ||
            wrapperTypeName == "EpisodeResponseWrapper"
        ) {
            return null
        }

        val uri = node.get("uri").text() ?: return null
        val isLocal =
            node.get("is_local").asBoolean(false) ||
                wrapper.get("is_local").asBoolean(false) ||
                uri.startsWith("spotify:local:")

        if (isLocal && !allowLocalFiles) {
            return null
        }

        if (!isLocal && !uri.startsWith("spotify:track:")) {
            return null
        }

        val idText = uri.substringAfterLast(":")
        if (!isLocal && !Base62.isValid(idText)) {
            return null
        }

        val id =
            if (isLocal) {
                SpotifyId("0000000000000000000000")
            } else {
                SpotifyId(idText)
            }

        val playable = node.get("playability").get("playable")
        if (!playable.isNull && !playable.asBoolean(true)) {
            return null
        }

        val name = node.get("name").text()
        if (name.isNullOrEmpty()) {
            return null
        }

        val durationMs =
            node.get("trackDuration").get("totalMilliseconds").asLong(0).let { ms ->
                if (ms > 0) ms else node.get("duration").get("totalMilliseconds").asLong(0)
            }

        val explicit =
            node.get("contentRating").get("label").text() == "EXPLICIT" ||
                node.get("explicit").asBoolean(false)

        val isrcText =
            node.get("externalIds").get("isrc").text()
                ?: node.get("external_ids").get("isrc").text()
        val isrc = if (!isrcText.isNullOrEmpty()) Isrc(isrcText) else null

        val artworkUrl =
            pickWidestImage(node.get("albumOfTrack").get("coverArt").get("sources"))
                ?: pickWidestImage(node.get("album").get("images"))
                ?: fallbackArtwork

        val artistsFromNode = parseArtists(node.get("artists"))
        val artists =
            if (artistsFromNode.isNotEmpty()) {
                artistsFromNode
            } else {
                val first = parseArtists(node.get("firstArtist"))
                val other = parseArtists(node.get("otherArtists"))
                first + other
            }

        return Track(
            id = id,
            title = name,
            artists = artists,
            durationMs = durationMs,
            artworkUrl = artworkUrl,
            isrc = isrc,
            explicit = explicit,
            isLocal = isLocal,
        )
    }

    fun parseEpisode(
        json: JsonBrowser,
        fallbackId: SpotifyId? = null,
        isChapter: Boolean = false,
    ): Episode? {
        if (json.isNull) {
            return null
        }

        val ep =
            when {
                !json.get("data").get("episodeUnionV2").isNull ->
                    json.get("data").get("episodeUnionV2")
                !json.get("episodeUnionV2").isNull -> json.get("episodeUnionV2")
                !json
                    .get("props")
                    .get("pageProps")
                    .get("state")
                    .get("data")
                    .get("entity")
                    .isNull ->
                    json
                        .get("props")
                        .get("pageProps")
                        .get("state")
                        .get("data")
                        .get("entity")
                else -> json
            }

        if (ep.get("__typename").text() == "NotFound") {
            return null
        }

        val title = ep.get("name").text() ?: ep.get("title").text() ?: return null
        val idText =
            ep.get("id").text()
                ?: ep.get("uri").text()?.substringAfterLast(":")
                ?: fallbackId?.value
                ?: return null
        if (!Base62.isValid(idText)) {
            return null
        }

        val showName =
            ep
                .get("podcastV2")
                .get("data")
                .get("name")
                .text()
                ?: ep
                    .get("audiobookV2")
                    .get("data")
                    .get("name")
                    .text()
                ?: ep.get("subtitle").text()
                ?: "Spotify Podcast"

        val durationMs =
            ep.get("duration").get("totalMilliseconds").asLong(0).let { ms ->
                if (ms > 0) {
                    ms
                } else {
                    ep.get("duration_ms").asLong(0).let { ms2 ->
                        if (ms2 > 0) ms2 else ep.get("duration").asLong(0)
                    }
                }
            }

        val artworkUrl =
            pickWidestImage(ep.get("coverArt").get("sources"))
                ?: pickWidestImage(ep.get("visualIdentity").get("image"))
                ?: ep.get("thumbnail_url").text()

        val explicit =
            ep.get("contentRating").get("label").text() == "EXPLICIT" ||
                ep.get("isExplicit").asBoolean(false)

        val actualIsChapter =
            isChapter || ep.get("__typename").text() == "Chapter" || !ep.get("audiobookV2").isNull

        return Episode(
            id = SpotifyId(idText),
            title = title,
            showName = showName,
            durationMs = durationMs,
            artworkUrl = artworkUrl,
            explicit = explicit,
            isChapter = actualIsChapter,
        )
    }

    fun parseShow(
        json: JsonBrowser,
        maxEpisodes: Int = 200,
        isAudiobook: Boolean = false,
    ): Show? {
        if (json.isNull) {
            return null
        }

        val showUnion =
            when {
                !json.get("data").get("podcastUnionV2").isNull ->
                    json.get("data").get("podcastUnionV2")
                !json.get("podcastUnionV2").isNull -> json.get("podcastUnionV2")
                else -> return null
            }

        if (showUnion.get("__typename").text() == "NotFound") {
            return null
        }

        val name = showUnion.get("name").text() ?: return null
        val uri = showUnion.get("uri").text() ?: return null
        val idText = uri.substringAfterLast(":")
        if (!Base62.isValid(idText)) {
            return null
        }

        val publisher =
            showUnion.get("publisher").get("name").text()
                ?: showUnion.get("author").get("name").text()
                ?: "Spotify"

        val artworkUrl = pickWidestImage(showUnion.get("coverArt").get("sources"))
        val actualIsAudiobook = isAudiobook || showUnion.get("__typename").text() == "Audiobook"

        val container =
            if (actualIsAudiobook) {
                showUnion.get("chaptersV2")
            } else {
                showUnion.get("episodesV2")
            }

        val totalEpisodes = container.get("totalCount").asInt(0)
        val items = container.get("items")
        val episodes =
            buildList {
                if (items.isList) {
                    for (item in items.values()) {
                        val epData =
                            if (!item.get("entity").get("data").isNull) {
                                item.get("entity").get("data")
                            } else {
                                item
                            }
                        val ep = parseEpisode(epData, isChapter = actualIsAudiobook) ?: continue
                        add(ep)
                        if (size >= maxEpisodes) {
                            break
                        }
                    }
                }
            }

        return Show(
            id = SpotifyId(idText),
            name = name,
            publisher = publisher,
            episodes = episodes,
            totalEpisodes = if (totalEpisodes > 0) totalEpisodes else episodes.size,
            artworkUrl = artworkUrl,
            isAudiobook = actualIsAudiobook,
        )
    }

    fun parseSearchSuggestions(json: JsonBrowser): List<Track> {
        if (json.isNull) {
            return emptyList()
        }
        val items =
            json
                .get("data")
                .get("searchV2")
                .get("topResultsV2")
                .get("itemsV2")
        if (!items.isList) {
            return emptyList()
        }

        return buildList {
            for (h in items.values()) {
                val item = h.get("item")
                if (item.isNull) continue
                val typeName = item.get("__typename").text()
                if (typeName == "SearchAutoCompleteEntity") {
                    val text = item.get("data").get("text").text() ?: continue
                    val uri = item.get("data").get("uri").text() ?: ""
                    val idText = uri.substringAfterLast(":").ifEmpty { "0000000000000000000000" }
                    val id =
                        if (Base62.isValid(idText)) {
                            SpotifyId(idText)
                        } else {
                            SpotifyId("0000000000000000000000")
                        }
                    add(
                        Track(
                            id = id,
                            title = text,
                            artists = listOf(Artist(id = id, name = "Spotify Suggestion")),
                            durationMs = 0,
                            artworkUrl = null,
                            isrc = null,
                            explicit = false,
                            isLocal = true,
                        ),
                    )
                } else {
                    val data = if (!item.get("data").isNull) item.get("data") else item
                    val track = parseTrackNode(data) ?: continue
                    add(track)
                }
            }
        }
    }

    fun parseRecommendations(
        json: JsonBrowser,
        maxTracks: Int = 10,
    ): List<Track> {
        if (json.isNull) {
            return emptyList()
        }
        val items =
            json
                .get("data")
                .get("seoRecommendedTrack")
                .get("items")
        if (!items.isList) {
            return emptyList()
        }
        return buildList {
            for (item in items.values()) {
                val data = if (!item.get("data").isNull) item.get("data") else item
                val track = parseTrackNode(data) ?: continue
                add(track)
                if (size >= maxTracks) break
            }
        }
    }

    fun parseSimilarAlbums(
        json: JsonBrowser,
        maxTracks: Int = 10,
    ): List<Track> {
        if (json.isNull) {
            return emptyList()
        }
        val items =
            json
                .get("data")
                .get("seoRecommendedTrackAlbum")
                .get("items")
        if (!items.isList) {
            return emptyList()
        }
        return buildList {
            for (item in items.values()) {
                val a = if (!item.get("data").isNull) item.get("data") else item
                val name = a.get("name").text() ?: continue
                val uri = a.get("uri").text() ?: continue
                val idText = uri.substringAfterLast(":")
                val id = if (Base62.isValid(idText)) SpotifyId(idText) else continue
                val artists = parseArtists(a.get("artists"))
                val artworkUrl = pickWidestImage(a.get("coverArt").get("sources"))
                add(
                    Track(
                        id = id,
                        title = name,
                        artists = artists,
                        durationMs = 0,
                        artworkUrl = artworkUrl,
                        isrc = null,
                        explicit = false,
                    ),
                )
                if (size >= maxTracks) break
            }
        }
    }

    private fun parseArtists(artistsNode: JsonBrowser): List<Artist> {
        if (artistsNode.isNull) {
            return emptyList()
        }
        val items = if (artistsNode.get("items").isList) artistsNode.get("items") else artistsNode
        if (!items.isList) {
            return emptyList()
        }

        return items.values().mapNotNull { a ->
            val name =
                a.get("profile").get("name").text()
                    ?: a.get("name").text()
                    ?: return@mapNotNull null
            val uri = a.get("uri").text()
            val idText = uri?.substringAfterLast(":")
            val id =
                if (!idText.isNullOrEmpty() && Base62.isValid(idText)) {
                    SpotifyId(idText)
                } else {
                    SpotifyId("0000000000000000000000")
                }
            Artist(id = id, name = name)
        }
    }

    private fun pickWidestImage(sources: JsonBrowser): String? {
        if (sources.isNull || !sources.isList) {
            return null
        }
        var bestUrl: String? = null
        var maxWidth = -1

        for (source in sources.values()) {
            val url = source.get("url").text() ?: continue
            val width = source.get("width").asInt(0)
            if (width > maxWidth || bestUrl == null) {
                maxWidth = width
                bestUrl = url
            }
        }
        return bestUrl
    }
}
