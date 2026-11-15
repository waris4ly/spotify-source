package dev.waris.spotify.api.official

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser
import dev.waris.spotify.model.Isrc
import dev.waris.spotify.model.SpotifyId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OfficialParserTest {
    private val parser = OfficialParser()

    private fun loadFixture(path: String): JsonBrowser {
        val stream =
            javaClass.getResourceAsStream("/fixtures/official/$path")
                ?: throw IllegalArgumentException("fixture not found: $path")
        return stream.use { JsonBrowser.parse(it) }
    }

    @Test
    fun parseTrack_validTrackFixture() {
        val json = loadFixture("track.json")
        val track = parser.parseTrack(json)

        assertNotNull(track)
        assertEquals(SpotifyId("4eoOQgFQ0rLMKNP8KcUNWc"), track?.id)
        assertEquals("ocean eyes", track?.title)
        assertEquals("Billie Eilish", track?.author)
        assertEquals(200379L, track?.durationMs)
        assertEquals(Isrc("USUM71600787"), track?.isrc)
        assertEquals(
            "https://i.scdn.co/image/ab67616d0000b273d31267868dfd6ebfa77a6ed7",
            track?.artworkUrl,
        )
        assertFalse(track?.explicit ?: true)
        assertFalse(track?.isLocal ?: true)
    }

    @Test
    fun parseAlbum_validAlbumFixture() {
        val json = loadFixture("album.json")
        val album = parser.parseAlbum(json)

        assertNotNull(album)
        assertEquals(SpotifyId("7aJuG4TFXa2hmE4z1yxc3n"), album?.id)
        assertEquals("HIT ME HARD AND SOFT", album?.name)
        assertEquals("Billie Eilish", album?.author)
        assertEquals(10, album?.totalTracks)
        assertEquals(
            "https://i.scdn.co/image/ab67616d0000b27371d62ea7ea8a5be92d3c1f62",
            album?.artworkUrl,
        )
        assertEquals(2, album?.tracks?.size)
        assertEquals("SKINNY", album?.tracks?.get(0)?.title)
        assertEquals("LUNCH", album?.tracks?.get(1)?.title)
        assertTrue(album?.tracks?.get(1)?.explicit ?: false)
    }

    @Test
    fun parseAlbumTracksPage_validPagingFixture() {
        val json = loadFixture("album_tracks_page.json")
        val tracks =
            parser.parseAlbumTracksPage(
                json = json,
                artworkUrl = "https://example.com/artwork.jpg",
            )

        assertEquals(2, tracks.size)
        assertEquals("CHIHIRO", tracks[0].title)
        assertEquals("BIRDS OF A FEATHER", tracks[1].title)
        assertEquals("https://example.com/artwork.jpg", tracks[0].artworkUrl)
    }

    @Test
    fun parseSearch_validSearchFixture() {
        val json = loadFixture("search.json")
        val tracks = parser.parseSearch(json)

        assertEquals(1, tracks.size)
        assertEquals("ocean eyes", tracks[0].title)
        assertEquals("Billie Eilish", tracks[0].author)
        assertEquals(Isrc("USUM71600787"), tracks[0].isrc)
    }

    @Test
    fun parseSearch_malformedItemSkipsInvalidEntries() {
        val json = loadFixture("malformed_item.json")
        val tracks = parser.parseSearch(json)

        assertEquals(2, tracks.size)
        assertEquals("valid track 1", tracks[0].title)
        assertEquals("valid track 2", tracks[1].title)
    }

    @Test
    fun parseTrack_nullJsonReturnsNull() {
        assertNull(parser.parseTrack(JsonBrowser.NULL_BROWSER))
    }

    @Test
    fun parseAlbum_nullJsonReturnsNull() {
        assertNull(parser.parseAlbum(JsonBrowser.NULL_BROWSER))
    }
}
