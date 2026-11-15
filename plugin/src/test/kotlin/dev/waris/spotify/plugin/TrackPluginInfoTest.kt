package dev.waris.spotify.plugin

import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo
import com.sedmelluq.discord.lavaplayer.track.BasicAudioPlaylist
import dev.waris.spotify.SpotifyAudioPlaylist
import dev.waris.spotify.SpotifyAudioSourceManager
import dev.waris.spotify.SpotifyAudioTrack
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TrackPluginInfoTest {
    private val pluginInfo = TrackPluginInfo()

    @Test
    fun modifyAudioTrackPluginInfo_returnsFieldsForSpotifyTrack() {
        val info =
            AudioTrackInfo(
                "Song",
                "Artist",
                200000L,
                "id123",
                false,
                "https://open.spotify.com/track/id123",
                "https://example.com/art.jpg",
                "USUM71600787",
            )
        val source = SpotifyAudioSourceManager()
        val track =
            SpotifyAudioTrack(
                trackInfo = info,
                sourceManager = source,
                canvasUrl = "https://canvaz.scdn.co/video.mp4",
                colors = mapOf("dark" to "#000", "light" to "#fff"),
            )

        val json = pluginInfo.modifyAudioTrackPluginInfo(track)
        assertNotNull(json)
        assertEquals("track", json?.get("type")?.jsonPrimitive?.content)
        assertEquals(
            "https://open.spotify.com/track/id123",
            json?.get("url")?.jsonPrimitive?.content,
        )
        assertEquals("Artist", json?.get("author")?.jsonPrimitive?.content)
        assertEquals("https://example.com/art.jpg", json?.get("artworkUrl")?.jsonPrimitive?.content)
        assertEquals("USUM71600787", json?.get("isrc")?.jsonPrimitive?.content)
        assertEquals(
            "https://canvaz.scdn.co/video.mp4",
            json?.get("canvasUrl")?.jsonPrimitive?.content,
        )
        assertEquals(
            "#000",
            json
                ?.get("colors")
                ?.jsonObject
                ?.get("dark")
                ?.jsonPrimitive
                ?.content,
        )
    }

    @Test
    fun modifyAudioTrackPluginInfo_episodeAndLocal() {
        val source = SpotifyAudioSourceManager()

        val epInfo =
            AudioTrackInfo(
                "AI Episode",
                "Tech Podcast",
                1800000L,
                "ep123",
                false,
                "https://open.spotify.com/episode/ep123",
                null,
                null,
            )
        val epTrack = SpotifyAudioTrack(epInfo, source)
        val epJson = pluginInfo.modifyAudioTrackPluginInfo(epTrack)
        assertNotNull(epJson)
        assertEquals("episode", epJson?.get("type")?.jsonPrimitive?.content)
        assertEquals("Tech Podcast", epJson?.get("show")?.jsonPrimitive?.content)

        val localInfo =
            AudioTrackInfo(
                "My Local Song",
                "Local Artist",
                120000L,
                "local",
                false,
                "spotify:local:My%20Local%20Song",
                null,
                null,
            )
        val localTrack = SpotifyAudioTrack(localInfo, source)
        val localJson = pluginInfo.modifyAudioTrackPluginInfo(localTrack)
        assertNotNull(localJson)
        assertEquals("local", localJson?.get("type")?.jsonPrimitive?.content)
        assertTrue(
            localJson
                ?.get("localFile")
                ?.jsonPrimitive
                ?.content
                ?.toBoolean() ?: false,
        )
    }

    @Test
    fun modifyAudioPlaylistPluginInfo_returnsFieldsForSpotifyPlaylist() {
        val playlist =
            SpotifyAudioPlaylist(
                name = "My Playlist",
                tracks = emptyList(),
                selectedTrack = null,
                isSearchResult = false,
                type = "playlist",
                url = "https://open.spotify.com/playlist/p123",
                author = "Owner",
                artworkUrl = "https://example.com/cover.jpg",
                totalTracks = 42,
            )

        val json = pluginInfo.modifyAudioPlaylistPluginInfo(playlist)
        assertNotNull(json)
        assertEquals("playlist", json?.get("type")?.jsonPrimitive?.content)
        assertEquals(
            "https://open.spotify.com/playlist/p123",
            json?.get("url")?.jsonPrimitive?.content,
        )
        assertEquals("Owner", json?.get("author")?.jsonPrimitive?.content)
        assertEquals(
            "https://example.com/cover.jpg",
            json?.get("artworkUrl")?.jsonPrimitive?.content,
        )
        assertEquals("42", json?.get("totalTracks")?.jsonPrimitive?.content)
    }

    @Test
    fun modifyAudioPlaylistPluginInfo_returnsNullForGenericPlaylist() {
        val playlist = BasicAudioPlaylist("Generic", emptyList(), null, false)
        assertNull(pluginInfo.modifyAudioPlaylistPluginInfo(playlist))
    }
}
