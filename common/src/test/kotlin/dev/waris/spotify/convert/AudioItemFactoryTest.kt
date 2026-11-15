package dev.waris.spotify.convert

import com.sedmelluq.discord.lavaplayer.track.BasicAudioPlaylist
import dev.waris.spotify.SpotifyAudioSourceManager
import dev.waris.spotify.SpotifyOptions
import dev.waris.spotify.model.Album
import dev.waris.spotify.model.Artist
import dev.waris.spotify.model.Isrc
import dev.waris.spotify.model.Playlist
import dev.waris.spotify.model.SpotifyId
import dev.waris.spotify.model.Track
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AudioItemFactoryTest {
    private val factory = AudioItemFactory()
    private lateinit var sourceManager: SpotifyAudioSourceManager

    @BeforeEach
    fun setUp() {
        sourceManager = SpotifyAudioSourceManager(SpotifyOptions())
    }

    @AfterEach
    fun tearDown() {
        sourceManager.shutdown()
    }

    @Test
    fun toAudioTrack_mapsAllFieldsCorrectly() {
        val artist1 = Artist(id = SpotifyId("art1"), name = "The Weeknd")
        val artist2 = Artist(id = SpotifyId("art2"), name = "Daft Punk")
        val track =
            Track(
                id = SpotifyId("4eoOQgFQ0rLMKNP8KcUNWc"),
                title = "Starboy",
                artists = listOf(artist1, artist2),
                durationMs = 230453L,
                artworkUrl = "https://i.scdn.co/image/ab67616d0000b273d31267868dfd6ebfa77a6ed7",
                isrc = Isrc("USUM71607007"),
            )

        val audioTrack = factory.toAudioTrack(track, sourceManager)

        assertEquals("Starboy", audioTrack.info.title)
        assertEquals("The Weeknd, Daft Punk", audioTrack.info.author)
        assertEquals(230453L, audioTrack.info.length)
        assertEquals("4eoOQgFQ0rLMKNP8KcUNWc", audioTrack.info.identifier)
        assertFalse(audioTrack.info.isStream)
        assertEquals("https://open.spotify.com/track/4eoOQgFQ0rLMKNP8KcUNWc", audioTrack.info.uri)
        assertEquals(
            "https://i.scdn.co/image/ab67616d0000b273d31267868dfd6ebfa77a6ed7",
            audioTrack.info.artworkUrl,
        )
        assertEquals("USUM71607007", audioTrack.info.isrc)
        assertEquals(sourceManager, audioTrack.sourceManager)
    }

    @Test
    fun toAudioPlaylist_fromAlbum_mapsCorrectly() {
        val artist = Artist(id = SpotifyId("art1"), name = "Artist 1")
        val track1 =
            Track(
                id = SpotifyId("track1"),
                title = "Track 1",
                artists = listOf(artist),
                durationMs = 180000L,
            )
        val track2 =
            Track(
                id = SpotifyId("track2"),
                title = "Track 2",
                artists = listOf(artist),
                durationMs = 200000L,
            )
        val album =
            Album(
                id = SpotifyId("album1"),
                name = "Test Album",
                artists = listOf(artist),
                tracks = listOf(track1, track2),
                totalTracks = 2,
                artworkUrl = "https://example.com/art.jpg",
            )

        val playlist = factory.toAudioPlaylist(album, sourceManager)

        assertTrue(playlist is BasicAudioPlaylist)
        assertEquals("Test Album", playlist.name)
        assertEquals(2, playlist.tracks.size)
        assertNull(playlist.selectedTrack)
        assertFalse(playlist.isSearchResult)
        assertEquals("Track 1", playlist.tracks[0].info.title)
        assertEquals("Track 2", playlist.tracks[1].info.title)
    }

    @Test
    fun toAudioPlaylist_fromPlaylist_mapsCorrectly() {
        val artist = Artist(id = SpotifyId("art1"), name = "Various Artists")
        val track =
            Track(
                id = SpotifyId("track1"),
                title = "Playlist Track",
                artists = listOf(artist),
                durationMs = 210000L,
            )
        val playlistModel =
            Playlist(
                id = SpotifyId("playlist1"),
                name = "My Favorites",
                owner = "user1",
                tracks = listOf(track),
                totalTracks = 1,
                artworkUrl = null,
            )

        val playlist = factory.toAudioPlaylist(playlistModel, sourceManager)

        assertTrue(playlist is BasicAudioPlaylist)
        assertEquals("My Favorites", playlist.name)
        assertEquals(1, playlist.tracks.size)
        assertNull(playlist.selectedTrack)
        assertFalse(playlist.isSearchResult)
        assertEquals("Playlist Track", playlist.tracks[0].info.title)
    }

    @Test
    fun toAudioPlaylist_fromArtist_mapsCorrectly() {
        val artist = Artist(id = SpotifyId("art1"), name = "The Artist")
        val track =
            Track(
                id = SpotifyId("track1"),
                title = "Top Hit",
                artists = listOf(artist),
                durationMs = 195000L,
            )
        val artistWithTracks =
            Artist(
                id = SpotifyId("art1"),
                name = "The Artist",
                topTracks = listOf(track),
            )

        val playlist = factory.toAudioPlaylist(artistWithTracks, sourceManager)

        assertTrue(playlist is BasicAudioPlaylist)
        assertEquals("The Artist", playlist.name)
        assertEquals(1, playlist.tracks.size)
        assertNull(playlist.selectedTrack)
        assertFalse(playlist.isSearchResult)
        assertEquals("Top Hit", playlist.tracks[0].info.title)
    }

    @Test
    fun toSearchPlaylist_flagsSearchResult() {
        val artist = Artist(id = SpotifyId("art1"), name = "Found Artist")
        val track =
            Track(
                id = SpotifyId("track1"),
                title = "Found Track",
                artists = listOf(artist),
                durationMs = 120000L,
            )

        val playlist = factory.toSearchPlaylist("starboy", listOf(track), sourceManager)

        assertTrue(playlist is BasicAudioPlaylist)
        assertEquals("Search results for: starboy", playlist.name)
        assertEquals(1, playlist.tracks.size)
        assertNull(playlist.selectedTrack)
        assertTrue(playlist.isSearchResult)
    }
}
