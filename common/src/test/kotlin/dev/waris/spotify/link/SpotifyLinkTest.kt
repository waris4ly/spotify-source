package dev.waris.spotify.link

import dev.waris.spotify.model.SpotifyId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SpotifyLinkTest {
    @Test
    fun parse_httpsTrackUrl() {
        val link = SpotifyLink.parse("https://open.spotify.com/track/4eoOQgFQ0rLMKNP8KcUNWc")
        val track = assertInstanceOf(SpotifyLink.Track::class.java, link)
        assertEquals(SpotifyId("4eoOQgFQ0rLMKNP8KcUNWc"), track.id)
        assertEquals("https://open.spotify.com/track/4eoOQgFQ0rLMKNP8KcUNWc", track.uri)
    }

    @Test
    fun parse_httpAlbumUrl() {
        val link = SpotifyLink.parse("http://open.spotify.com/album/7aJuG4TFXa2hmE4z1yxc3n")
        val album = assertInstanceOf(SpotifyLink.Album::class.java, link)
        assertEquals(SpotifyId("7aJuG4TFXa2hmE4z1yxc3n"), album.id)
        assertEquals("https://open.spotify.com/album/7aJuG4TFXa2hmE4z1yxc3n", album.uri)
    }

    @Test
    fun parse_schemelessPlaylistUrl() {
        val link = SpotifyLink.parse("open.spotify.com/playlist/0Gf1aUizIT2TLKlu2qYlh2")
        val playlist = assertInstanceOf(SpotifyLink.Playlist::class.java, link)
        assertEquals(SpotifyId("0Gf1aUizIT2TLKlu2qYlh2"), playlist.id)
        assertEquals("https://open.spotify.com/playlist/0Gf1aUizIT2TLKlu2qYlh2", playlist.uri)
    }

    @Test
    fun parse_artistUrl() {
        val link = SpotifyLink.parse("https://open.spotify.com/artist/6qqNVTkY8uBg9cP3Jd7DAH")
        val artist = assertInstanceOf(SpotifyLink.Artist::class.java, link)
        assertEquals(SpotifyId("6qqNVTkY8uBg9cP3Jd7DAH"), artist.id)
        assertEquals("https://open.spotify.com/artist/6qqNVTkY8uBg9cP3Jd7DAH", artist.uri)
    }

    @Test
    fun parse_intlRegionalUrl() {
        val url = "https://open.spotify.com/intl-pt/track/4IpaCWsYoiRLG4CihCt1Pd"
        val link = SpotifyLink.parse(url)
        val track = assertInstanceOf(SpotifyLink.Track::class.java, link)
        assertEquals(SpotifyId("4IpaCWsYoiRLG4CihCt1Pd"), track.id)
    }

    @Test
    fun parse_embedUrl() {
        val link = SpotifyLink.parse("https://open.spotify.com/embed/track/6dOtVTDdiauQNBQEDOtlAB")
        val track = assertInstanceOf(SpotifyLink.Track::class.java, link)
        assertEquals(SpotifyId("6dOtVTDdiauQNBQEDOtlAB"), track.id)
    }

    @Test
    fun parse_legacyUserPlaylistUrl() {
        val link =
            SpotifyLink.parse(
                "https://open.spotify.com/user/filtr.br/playlist/5HmZtuuIDMtIy21kylqhx6",
            )
        val playlist = assertInstanceOf(SpotifyLink.Playlist::class.java, link)
        assertEquals(SpotifyId("5HmZtuuIDMtIy21kylqhx6"), playlist.id)
    }

    @Test
    fun parse_urlWithQueryAndFragment() {
        val url =
            "https://open.spotify.com/track/4eoOQgFQ0rLMKNP8KcUNWc?si=abc&context=spotify#0:30"
        val link = SpotifyLink.parse(url)
        val track = assertInstanceOf(SpotifyLink.Track::class.java, link)
        assertEquals(SpotifyId("4eoOQgFQ0rLMKNP8KcUNWc"), track.id)
    }

    @Test
    fun parse_urlWithTrailingSlash() {
        val link = SpotifyLink.parse("https://open.spotify.com/track/4eoOQgFQ0rLMKNP8KcUNWc/")
        val track = assertInstanceOf(SpotifyLink.Track::class.java, link)
        assertEquals(SpotifyId("4eoOQgFQ0rLMKNP8KcUNWc"), track.id)
    }

    @Test
    fun parse_uriTrack() {
        val link = SpotifyLink.parse("spotify:track:4eoOQgFQ0rLMKNP8KcUNWc")
        val track = assertInstanceOf(SpotifyLink.Track::class.java, link)
        assertEquals(SpotifyId("4eoOQgFQ0rLMKNP8KcUNWc"), track.id)
    }

    @Test
    fun parse_uriAlbum() {
        val link = SpotifyLink.parse("spotify:album:7aJuG4TFXa2hmE4z1yxc3n")
        val album = assertInstanceOf(SpotifyLink.Album::class.java, link)
        assertEquals(SpotifyId("7aJuG4TFXa2hmE4z1yxc3n"), album.id)
    }

    @Test
    fun parse_uriPlaylist() {
        val link = SpotifyLink.parse("spotify:playlist:1QscjgKRfrGpiDFMyT9rwO")
        val playlist = assertInstanceOf(SpotifyLink.Playlist::class.java, link)
        assertEquals(SpotifyId("1QscjgKRfrGpiDFMyT9rwO"), playlist.id)
    }

    @Test
    fun parse_uriArtist() {
        val link = SpotifyLink.parse("spotify:artist:6qqNVTkY8uBg9cP3Jd7DAH")
        val artist = assertInstanceOf(SpotifyLink.Artist::class.java, link)
        assertEquals(SpotifyId("6qqNVTkY8uBg9cP3Jd7DAH"), artist.id)
    }

    @Test
    fun parse_uriLegacyUserPlaylist() {
        val link = SpotifyLink.parse("spotify:user:filtr.br:playlist:5HmZtuuIDMtIy21kylqhx6")
        val playlist = assertInstanceOf(SpotifyLink.Playlist::class.java, link)
        assertEquals(SpotifyId("5HmZtuuIDMtIy21kylqhx6"), playlist.id)
    }

    @Test
    fun parse_searchQuery() {
        val link = SpotifyLink.parse("spsearch:ocean eyes billie eilish")
        val search = assertInstanceOf(SpotifyLink.Search::class.java, link)
        assertEquals("ocean eyes billie eilish", search.query)
    }

    @Test
    fun parse_searchQueryTrimsWhitespace() {
        val link = SpotifyLink.parse("spsearch:   ocean eyes   ")
        val search = assertInstanceOf(SpotifyLink.Search::class.java, link)
        assertEquals("ocean eyes", search.query)
    }

    @Test
    fun parse_inputWithSurroundingWhitespace() {
        val link = SpotifyLink.parse("  spotify:track:4eoOQgFQ0rLMKNP8KcUNWc  ")
        val track = assertInstanceOf(SpotifyLink.Track::class.java, link)
        assertEquals(SpotifyId("4eoOQgFQ0rLMKNP8KcUNWc"), track.id)
    }

    @Test
    fun parse_emptyInput() {
        assertNull(SpotifyLink.parse(""))
        assertNull(SpotifyLink.parse("   "))
    }

    @Test
    fun parse_unrelatedUrl() {
        assertNull(SpotifyLink.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertNull(SpotifyLink.parse("https://google.com"))
    }

    @Test
    fun parse_unrelatedSearchPrefix() {
        assertNull(SpotifyLink.parse("ytsearch:ocean eyes"))
    }

    @Test
    fun parse_unknownType() {
        assertNull(SpotifyLink.parse("https://open.spotify.com/unknown/4eoOQgFQ0rLMKNP8KcUNWc"))
        assertNull(SpotifyLink.parse("spotify:unknown:4eoOQgFQ0rLMKNP8KcUNWc"))
    }

    @Test
    fun parse_invalidIdCharacters() {
        assertNull(SpotifyLink.parse("https://open.spotify.com/track/4eoOQgFQ0rLMKNP8KcUNWc!"))
        assertNull(SpotifyLink.parse("spotify:track:4eoOQgFQ0rLMKNP8KcUNWc!"))
    }

    @Test
    fun parse_missingId() {
        assertNull(SpotifyLink.parse("https://open.spotify.com/track/"))
        assertNull(SpotifyLink.parse("spotify:track:"))
    }

    @Test
    fun parse_emptySearchQuery() {
        assertNull(SpotifyLink.parse("spsearch:"))
        assertNull(SpotifyLink.parse("spsearch:   "))
    }

    @Test
    fun parse_episodeAndShowLinks() {
        val epLink = SpotifyLink.parse("https://open.spotify.com/episode/5125459801234567890123")
        val episode = assertInstanceOf(SpotifyLink.Episode::class.java, epLink)
        assertEquals(SpotifyId("5125459801234567890123"), episode.id)

        val showLink = SpotifyLink.parse("spotify:show:1234567890123456789012")
        val show = assertInstanceOf(SpotifyLink.Show::class.java, showLink)
        assertEquals(SpotifyId("1234567890123456789012"), show.id)

        val chapterLink =
            SpotifyLink.parse(
                "https://open.spotify.com/chapter/7125459801234567890123",
            )
        val chapter = assertInstanceOf(SpotifyLink.Chapter::class.java, chapterLink)
        assertEquals(SpotifyId("7125459801234567890123"), chapter.id)

        val audioLink = SpotifyLink.parse("spotify:audiobook:3234567890123456789012")
        val audiobook = assertInstanceOf(SpotifyLink.Audiobook::class.java, audioLink)
        assertEquals(SpotifyId("3234567890123456789012"), audiobook.id)
    }

    @Test
    fun parse_searchPrefixes() {
        val suggest = SpotifyLink.parse("spsuggest:taylor swift")
        val suggestLink = assertInstanceOf(SpotifyLink.Suggest::class.java, suggest)
        assertEquals("taylor swift", suggestLink.query)

        val rec = SpotifyLink.parse("sprec:4eoOQgFQ0rLMKNP8KcUNWc")
        val recLink = assertInstanceOf(SpotifyLink.Recommendations::class.java, rec)
        assertEquals("4eoOQgFQ0rLMKNP8KcUNWc", recLink.seed)

        val similar = SpotifyLink.parse("spsimilar:4eoOQgFQ0rLMKNP8KcUNWc")
        val similarLink = assertInstanceOf(SpotifyLink.Similar::class.java, similar)
        assertEquals("4eoOQgFQ0rLMKNP8KcUNWc", similarLink.seed)
    }
}
