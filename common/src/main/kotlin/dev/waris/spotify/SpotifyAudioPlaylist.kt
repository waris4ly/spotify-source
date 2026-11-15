package dev.waris.spotify

import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.BasicAudioPlaylist

public class SpotifyAudioPlaylist(
    name: String,
    tracks: List<AudioTrack>,
    selectedTrack: AudioTrack?,
    isSearchResult: Boolean,
    public val type: String,
    public val url: String,
    public val author: String,
    public val artworkUrl: String?,
    public val totalTracks: Int,
) : BasicAudioPlaylist(name, tracks, selectedTrack, isSearchResult)
