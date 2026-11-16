package dev.waris.spotify.convert

import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo
import com.sedmelluq.discord.lavaplayer.track.BasicAudioPlaylist
import dev.waris.spotify.SpotifyAudioPlaylist
import dev.waris.spotify.SpotifyAudioSourceManager
import dev.waris.spotify.SpotifyAudioTrack
import dev.waris.spotify.model.Album
import dev.waris.spotify.model.Artist
import dev.waris.spotify.model.Episode
import dev.waris.spotify.model.Playlist
import dev.waris.spotify.model.Show
import dev.waris.spotify.model.Track

internal class AudioItemFactory {
    fun toAudioTrack(
        track: Track,
        sourceManager: SpotifyAudioSourceManager,
    ): SpotifyAudioTrack {
        // local tracks use local identifier and uri format without isrc
        val uri =
            if (track.isLocal) {
                "spotify:local:${track.title}"
            } else {
                "https://open.spotify.com/track/${track.id.value}"
            }
        val identifier = if (track.isLocal) "local" else track.id.value
        val info =
            AudioTrackInfo(
                track.title,
                track.author,
                track.durationMs,
                identifier,
                false,
                uri,
                track.artworkUrl,
                track.isrc?.value,
            )
        return SpotifyAudioTrack(info, sourceManager)
    }

    fun toAudioTrack(
        episode: Episode,
        sourceManager: SpotifyAudioSourceManager,
    ): SpotifyAudioTrack {
        val kind = if (episode.isChapter) "chapter" else "episode"
        val info =
            AudioTrackInfo(
                episode.title,
                episode.showName,
                episode.durationMs,
                episode.id.value,
                false,
                "https://open.spotify.com/$kind/${episode.id.value}",
                episode.artworkUrl,
                null,
            )
        return SpotifyAudioTrack(info, sourceManager)
    }

    fun toAudioPlaylist(
        album: Album,
        sourceManager: SpotifyAudioSourceManager,
    ): AudioPlaylist {
        val tracks = album.tracks.map { toAudioTrack(it, sourceManager) }
        return SpotifyAudioPlaylist(
            name = album.name,
            tracks = tracks,
            selectedTrack = null,
            isSearchResult = false,
            type = "album",
            url = "https://open.spotify.com/album/${album.id.value}",
            author = album.author,
            artworkUrl = album.artworkUrl,
            totalTracks = album.totalTracks,
            truncated = album.truncated,
        )
    }

    fun toAudioPlaylist(
        playlist: Playlist,
        sourceManager: SpotifyAudioSourceManager,
    ): AudioPlaylist {
        val tracks = playlist.tracks.map { toAudioTrack(it, sourceManager) }
        return SpotifyAudioPlaylist(
            name = playlist.name,
            tracks = tracks,
            selectedTrack = null,
            isSearchResult = false,
            type = "playlist",
            url = "https://open.spotify.com/playlist/${playlist.id.value}",
            author = playlist.owner,
            artworkUrl = playlist.artworkUrl,
            totalTracks = playlist.totalTracks,
            truncated = playlist.truncated,
        )
    }

    fun toAudioPlaylist(
        artist: Artist,
        sourceManager: SpotifyAudioSourceManager,
    ): AudioPlaylist {
        val tracks = artist.topTracks.map { toAudioTrack(it, sourceManager) }
        return SpotifyAudioPlaylist(
            name = artist.name,
            tracks = tracks,
            selectedTrack = null,
            isSearchResult = false,
            type = "artist",
            url = "https://open.spotify.com/artist/${artist.id.value}",
            author = artist.name,
            artworkUrl = artist.artworkUrl,
            totalTracks = artist.topTracks.size,
        )
    }

    fun toAudioPlaylist(
        show: Show,
        sourceManager: SpotifyAudioSourceManager,
    ): AudioPlaylist {
        val tracks = show.episodes.map { toAudioTrack(it, sourceManager) }
        val kind = if (show.isAudiobook) "audiobook" else "show"
        return SpotifyAudioPlaylist(
            name = show.name,
            tracks = tracks,
            selectedTrack = null,
            isSearchResult = false,
            type = kind,
            url = "https://open.spotify.com/$kind/${show.id.value}",
            author = show.publisher,
            artworkUrl = show.artworkUrl,
            totalTracks = show.totalEpisodes,
            truncated = show.truncated,
        )
    }

    fun toRecommendationsPlaylist(
        name: String,
        tracks: List<Track>,
        sourceManager: SpotifyAudioSourceManager,
    ): AudioPlaylist {
        val audioTracks = tracks.map { toAudioTrack(it, sourceManager) }
        return SpotifyAudioPlaylist(
            name = name,
            tracks = audioTracks,
            selectedTrack = null,
            isSearchResult = false,
            type = "recommendations",
            url = "",
            author = "Spotify",
            artworkUrl = null,
            totalTracks = tracks.size,
        )
    }

    fun toSearchPlaylist(
        query: String,
        tracks: List<Track>,
        sourceManager: SpotifyAudioSourceManager,
    ): AudioPlaylist {
        val audioTracks = tracks.map { toAudioTrack(it, sourceManager) }
        return BasicAudioPlaylist("Search results for: $query", audioTracks, null, true)
    }
}
