package dev.waris.spotify.api.internal

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser
import dev.waris.spotify.error.SpotifyException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InternalParserTest {
    private val parser = InternalParser(allowLocalFiles = false)

    @Test
    fun parsePlaylist_normal() {
        val json = loadFixture("playlist_normal.json")
        val playlist = parser.parsePlaylist(json)

        assertEquals("Today's Top Hits", playlist.name)
        assertEquals("Spotify", playlist.owner)
        assertEquals(50, playlist.totalTracks)
        assertEquals(2, playlist.tracks.size)

        val first = playlist.tracks[0]
        assertEquals("Never Gonna Give You Up", first.title)
        assertEquals("Rick Astley", first.author)
        assertEquals(213573, first.durationMs)
        assertEquals("GBARL9300134", first.isrc?.value)
        assertEquals(
            "https://i.scdn.co/image/ab67616d0000b2735755e164993798e0c9ef7d7a",
            first.artworkUrl,
        )

        val second = playlist.tracks[1]
        assertEquals("Shape of You", second.title)
        assertEquals("Ed Sheeran", second.author)
        assertEquals(233712, second.durationMs)
    }

    @Test
    fun parsePlaylist_skipsLocalFilesWhenDisallowed() {
        val json = loadFixture("playlist_local_file.json")
        val playlist = parser.parsePlaylist(json)

        assertEquals(1, playlist.tracks.size)
        assertEquals("Never Gonna Give You Up", playlist.tracks[0].title)
        assertFalse(playlist.tracks[0].isLocal)
    }

    @Test
    fun parsePlaylist_keepsLocalFilesWhenAllowed() {
        val localParser = InternalParser(allowLocalFiles = true)
        val json = loadFixture("playlist_local_file.json")
        val playlist = localParser.parsePlaylist(json)

        assertEquals(2, playlist.tracks.size)
        assertTrue(playlist.tracks[0].isLocal)
        assertEquals("Local Audio Track", playlist.tracks[0].title)
        assertEquals("Local Artist", playlist.tracks[0].author)
    }

    @Test
    fun parsePlaylist_skipsEpisodes() {
        val json = loadFixture("playlist_episode.json")
        val playlist = parser.parsePlaylist(json)

        assertEquals(1, playlist.tracks.size)
        assertEquals("Never Gonna Give You Up", playlist.tracks[0].title)
    }

    @Test
    fun parsePlaylist_skipsNullEntries() {
        val json = loadFixture("playlist_null_entry.json")
        val playlist = parser.parsePlaylist(json)

        assertEquals(1, playlist.tracks.size)
        assertEquals("Never Gonna Give You Up", playlist.tracks[0].title)
    }

    @Test
    fun parsePlaylist_skipsRemovedOrUnplayableTracks() {
        val json = loadFixture("playlist_removed_track.json")
        val playlist = parser.parsePlaylist(json)

        assertEquals(1, playlist.tracks.size)
        assertEquals("Never Gonna Give You Up", playlist.tracks[0].title)
    }

    @Test
    fun parsePlaylist_keepsDuplicates() {
        val json = loadFixture("playlist_duplicates.json")
        val playlist = parser.parsePlaylist(json)

        assertEquals(2, playlist.tracks.size)
        assertEquals("4cOdK2wGLETKBW3PvgPWqT", playlist.tracks[0].id.value)
        assertEquals("4cOdK2wGLETKBW3PvgPWqT", playlist.tracks[1].id.value)
    }

    @Test
    fun parsePlaylist_preservesUnicodeAndHandlesEmptyArtists() {
        val json = loadFixture("playlist_unicode.json")
        val playlist = parser.parsePlaylist(json)

        assertEquals("世界 Top 50 🎵", playlist.name)
        assertEquals("公式プレイリスト", playlist.owner)
        assertEquals(3, playlist.tracks.size)

        assertEquals("夜に駆ける", playlist.tracks[0].title)
        assertEquals("YOASOBI", playlist.tracks[0].author)

        assertEquals("Смерти больше нет", playlist.tracks[1].title)
        assertEquals("IC3PEAK", playlist.tracks[1].author)

        assertEquals("No Artist Song", playlist.tracks[2].title)
        assertEquals("Unknown", playlist.tracks[2].author)
    }

    @Test
    fun parsePlaylist_privateOrNotFoundThrows() {
        val json = loadFixture("playlist_private.json")
        val exception =
            assertThrows(SpotifyException::class.java) {
                parser.parsePlaylist(json)
            }
        assertTrue(exception.message?.contains("private or not found") == true)
    }

    @Test
    fun parseAlbum_normal() {
        val json = loadFixture("album.json")
        val album = parser.parseAlbum(json)

        assertNotNull(album)
        assertEquals("Global Warming", album?.name)
        assertEquals("Pitbull", album?.author)
        assertEquals(16, album?.totalTracks)
        assertEquals(1, album?.tracks?.size)

        val track = album?.tracks?.get(0)
        assertEquals("Feel This Moment", track?.title)
        assertEquals("Pitbull, Christina Aguilera", track?.author)
        assertEquals(229560, track?.durationMs)
    }

    @Test
    fun parseArtist_normal() {
        val json = loadFixture("artist.json")
        val artist = parser.parseArtist(json)

        assertNotNull(artist)
        assertEquals("Radiohead", artist?.name)
        assertEquals(
            "https://i.scdn.co/image/ab6761610000e5eb1d2004d2e0ccf3411f18e957",
            artist?.artworkUrl,
        )
        assertEquals(1, artist?.topTracks?.size)

        val topTrack = artist?.topTracks?.get(0)
        assertEquals("Creep", topTrack?.title)
        assertEquals("Radiohead", topTrack?.author)
    }

    @Test
    fun parseSearch_normal() {
        val json = loadFixture("search.json")
        val results = parser.parseSearch(json)

        assertEquals(1, results.size)
        assertEquals("Never Gonna Give You Up", results[0].title)
        assertEquals("Rick Astley", results[0].author)
    }

    @Test
    fun parseEpisode_normal() {
        val json = loadFixture("episode.json")
        val episode = parser.parseEpisode(json)

        assertNotNull(episode)
        assertEquals("The Future of AI", episode?.title)
        assertEquals("Tech Horizons", episode?.showName)
        assertEquals(3600000, episode?.durationMs)
        assertEquals("https://i.scdn.co/image/ab6765630000ba8aa1b2c3d4e5f6", episode?.artworkUrl)
        assertFalse(episode?.explicit ?: true)
        assertFalse(episode?.isChapter ?: true)
    }

    @Test
    fun parseChapter_normal() {
        val json = loadFixture("chapter.json")
        val chapter = parser.parseEpisode(json, isChapter = true)

        assertNotNull(chapter)
        assertEquals("Chapter 1: The Beginning", chapter?.title)
        assertEquals("Great Audio Book", chapter?.showName)
        assertEquals(1800000, chapter?.durationMs)
        assertTrue(chapter?.isChapter ?: false)
    }

    @Test
    fun parseShow_normal() {
        val json = loadFixture("show.json")
        val show = parser.parseShow(json)

        assertNotNull(show)
        assertEquals("Tech Horizons", show?.name)
        assertEquals("Tech Media Inc", show?.publisher)
        assertEquals(2, show?.totalEpisodes)
        assertEquals(2, show?.episodes?.size)
        assertEquals("The Future of AI", show?.episodes?.get(0)?.title)
        assertEquals("Quantum Computing", show?.episodes?.get(1)?.title)
        assertTrue(show?.episodes?.get(1)?.explicit ?: false)
    }

    @Test
    fun parseAudiobook_normal() {
        val json = loadFixture("audiobook.json")
        val audiobook = parser.parseShow(json, isAudiobook = true)

        assertNotNull(audiobook)
        assertEquals("Great Audio Book", audiobook?.name)
        assertEquals("Author Publisher", audiobook?.publisher)
        assertTrue(audiobook?.isAudiobook ?: false)
        assertEquals(1, audiobook?.episodes?.size)
        assertEquals("Chapter 1: The Beginning", audiobook?.episodes?.get(0)?.title)
    }

    @Test
    fun parseSearchSuggestions_normal() {
        val json = loadFixture("search_suggestions.json")
        val tracks = parser.parseSearchSuggestions(json)

        assertEquals(2, tracks.size)
        assertEquals("Taylor Swift", tracks[0].title)
        assertTrue(tracks[0].isLocal)
        assertEquals("Anti-Hero", tracks[1].title)
        assertEquals("Taylor Swift", tracks[1].author)
    }

    @Test
    fun parseRecommendations_normal() {
        val json = loadFixture("recommendations.json")
        val tracks = parser.parseRecommendations(json)

        assertEquals(1, tracks.size)
        assertEquals("SICKO MODE", tracks[0].title)
        assertEquals("Travis Scott", tracks[0].author)
    }

    @Test
    fun parseSimilarAlbums_normal() {
        val json = loadFixture("similar_albums.json")
        val tracks = parser.parseSimilarAlbums(json)

        assertEquals(1, tracks.size)
        assertEquals("ASTROWORLD", tracks[0].title)
        assertEquals("Travis Scott", tracks[0].author)
    }

    private fun loadFixture(path: String): JsonBrowser {
        val stream =
            javaClass.getResourceAsStream("/fixtures/internal/$path")
                ?: error("Fixture not found: $path")
        return stream.use { JsonBrowser.parse(it) }
    }
}
