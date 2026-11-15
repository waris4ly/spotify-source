package dev.waris.spotify.api

import dev.waris.spotify.auth.AccessToken
import dev.waris.spotify.auth.TokenKind
import dev.waris.spotify.auth.TokenManager
import dev.waris.spotify.auth.TokenProvider
import dev.waris.spotify.error.SpotifyException
import dev.waris.spotify.model.Album
import dev.waris.spotify.model.Artist
import dev.waris.spotify.model.Isrc
import dev.waris.spotify.model.Playlist
import dev.waris.spotify.model.SpotifyId
import dev.waris.spotify.model.Track
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.TimeSource
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ApiRouterTest {
    private lateinit var tokenManager: TokenManager
    private lateinit var officialBackend: TestBackend
    private lateinit var internalBackend: TestBackend
    private lateinit var router: ApiRouter

    private class TestProvider(
        override val kind: TokenKind,
        private val tokenValue: String = "test-token",
    ) : TokenProvider {
        private var cooling = false

        override val isCoolingDown: Boolean get() = cooling

        override fun fetchToken(): AccessToken =
            AccessToken(
                value = tokenValue,
                expiresAt = TimeSource.Monotonic.markNow() + 1.hours,
                expiresInSeconds = 3600,
            )

        override fun coolDown(duration: Duration) {
            cooling = true
        }
    }

    private class TestBackend(
        override val name: String,
        private val supportedOperations: Set<Class<out Operation>>,
        private val tokenKind: TokenKind?,
    ) : SpotifyApi {
        var searchHandler: ((String, Int, Int) -> List<Track>)? = null
        var trackHandler: ((SpotifyId) -> Track?)? = null
        var albumHandler: ((SpotifyId, Int) -> Album?)? = null
        var playlistHandler: ((SpotifyId, Int) -> Playlist?)? = null
        var artistHandler: ((SpotifyId) -> Artist?)? = null
        var isrcHandler: ((SpotifyId) -> Isrc?)? = null

        var searchCallCount = 0
        var trackCallCount = 0
        var albumCallCount = 0
        var playlistCallCount = 0
        var artistCallCount = 0
        var isrcCallCount = 0

        override fun supports(operation: Operation): Boolean =
            supportedOperations.contains(operation::class.java)

        override fun tokenKindFor(operation: Operation): TokenKind? = tokenKind

        override fun search(
            query: String,
            limit: Int,
            offset: Int,
        ): List<Track> {
            searchCallCount++
            return searchHandler?.invoke(query, limit, offset) ?: emptyList()
        }

        override fun getTrack(id: SpotifyId): Track? {
            trackCallCount++
            return trackHandler?.invoke(id)
        }

        override fun getAlbum(
            id: SpotifyId,
            limit: Int,
        ): Album? {
            albumCallCount++
            return albumHandler?.invoke(id, limit)
        }

        override fun getPlaylist(
            id: SpotifyId,
            limit: Int,
        ): Playlist? {
            playlistCallCount++
            return playlistHandler?.invoke(id, limit)
        }

        override fun getArtist(id: SpotifyId): Artist? {
            artistCallCount++
            return artistHandler?.invoke(id)
        }

        override fun getIsrc(id: SpotifyId): Isrc? {
            isrcCallCount++
            return isrcHandler?.invoke(id)
        }
    }

    @BeforeEach
    fun setUp() {
        tokenManager =
            TokenManager(
                mapOf(
                    TokenKind.CLIENT to TestProvider(TokenKind.CLIENT, "client-token"),
                    TokenKind.ANONYMOUS to TestProvider(TokenKind.ANONYMOUS, "anon-token"),
                ),
            )

        officialBackend =
            TestBackend(
                name = "official",
                supportedOperations =
                    setOf(
                        Operation.Search::class.java,
                        Operation.GetTrack::class.java,
                        Operation.GetAlbum::class.java,
                        Operation.GetArtist::class.java,
                        Operation.GetIsrc::class.java,
                    ),
                tokenKind = TokenKind.CLIENT,
            )

        internalBackend =
            TestBackend(
                name = "internal",
                supportedOperations =
                    setOf(
                        Operation.Search::class.java,
                        Operation.GetTrack::class.java,
                        Operation.GetAlbum::class.java,
                        Operation.GetPlaylist::class.java,
                        Operation.GetArtist::class.java,
                    ),
                tokenKind = TokenKind.ANONYMOUS,
            )

        router = ApiRouter(listOf(officialBackend, internalBackend), tokenManager)
    }

    @AfterEach
    fun tearDown() {
        tokenManager.close()
    }

    @Test
    fun getRoutingOrder_matchesConfiguredTable() {
        val searchOrder = router.getRoutingOrder(Operation.Search("test", 10, 0))
        assertEquals(listOf("official", "internal"), searchOrder.map { it.name })

        val trackOrder = router.getRoutingOrder(Operation.GetTrack(SpotifyId("123")))
        assertEquals(listOf("official", "internal"), trackOrder.map { it.name })

        val albumOrder = router.getRoutingOrder(Operation.GetAlbum(SpotifyId("123"), 10))
        assertEquals(listOf("official", "internal"), albumOrder.map { it.name })

        val playlistOrder = router.getRoutingOrder(Operation.GetPlaylist(SpotifyId("123"), 10))
        assertEquals(listOf("internal"), playlistOrder.map { it.name })

        val artistOrder = router.getRoutingOrder(Operation.GetArtist(SpotifyId("123")))
        assertEquals(listOf("internal", "official"), artistOrder.map { it.name })

        val isrcOrder = router.getRoutingOrder(Operation.GetIsrc(SpotifyId("123")))
        assertEquals(listOf("official", "internal"), isrcOrder.map { it.name })
    }

    @Test
    fun search_usesOfficialFirstAndSkipsInternalOnSuccess() {
        val expectedTrack =
            Track(
                id = SpotifyId("t1"),
                title = "Starboy",
                artists = listOf(Artist(SpotifyId("a1"), "The Weeknd")),
                durationMs = 230000L,
            )
        officialBackend.searchHandler = { _, _, _ -> listOf(expectedTrack) }

        val results = router.search("starboy", limit = 10)

        assertEquals(1, results.size)
        assertEquals("Starboy", results[0].title)
        assertEquals(1, officialBackend.searchCallCount)
        assertEquals(0, internalBackend.searchCallCount)
    }

    @Test
    fun search_fallsBackToInternalWhenOfficialThrows() {
        val expectedTrack =
            Track(
                id = SpotifyId("t2"),
                title = "Blinding Lights",
                artists = listOf(Artist(SpotifyId("a1"), "The Weeknd")),
                durationMs = 200000L,
            )
        officialBackend.searchHandler = { _, _, _ -> throw SpotifyException("network error") }
        internalBackend.searchHandler = { _, _, _ -> listOf(expectedTrack) }

        val results = router.search("blinding", limit = 10)

        assertEquals(1, results.size)
        assertEquals("Blinding Lights", results[0].title)
        assertEquals(1, officialBackend.searchCallCount)
        assertEquals(1, internalBackend.searchCallCount)
    }

    @Test
    fun getTrack_fallsBackToInternalWhenOfficialReturnsNull() {
        val expectedTrack =
            Track(
                id = SpotifyId("t1"),
                title = "Track One",
                artists = emptyList(),
                durationMs = 180000L,
            )
        officialBackend.trackHandler = { null }
        internalBackend.trackHandler = { expectedTrack }

        val result = router.getTrack(SpotifyId("t1"))

        assertNotNull(result)
        assertEquals("Track One", result?.title)
        assertEquals(1, officialBackend.trackCallCount)
        assertEquals(1, internalBackend.trackCallCount)
    }

    @Test
    fun getAlbum_fallsBackToInternalWhenOfficialFails() {
        val expectedAlbum =
            Album(
                id = SpotifyId("alb1"),
                name = "Test Album",
                artists = emptyList(),
                tracks = emptyList(),
            )
        officialBackend.albumHandler = { _, _ -> throw SpotifyException("500 Internal Error") }
        internalBackend.albumHandler = { _, _ -> expectedAlbum }

        val result = router.getAlbum(SpotifyId("alb1"))

        assertNotNull(result)
        assertEquals("Test Album", result?.name)
        assertEquals(1, officialBackend.albumCallCount)
        assertEquals(1, internalBackend.albumCallCount)
    }

    @Test
    fun getPlaylist_onlyCallsInternalAndThrowsOnError() {
        internalBackend.playlistHandler = { _, _ ->
            throw SpotifyException("playlist is private or does not exist")
        }

        assertThrows(SpotifyException::class.java) {
            router.getPlaylist(SpotifyId("pl1"))
        }
        assertEquals(0, officialBackend.playlistCallCount)
        assertEquals(1, internalBackend.playlistCallCount)
    }

    @Test
    fun getArtist_usesInternalFirstWhenTracksPresent() {
        val track =
            Track(
                id = SpotifyId("t1"),
                title = "Artist Hit",
                artists = emptyList(),
                durationMs = 190000L,
            )
        val artist =
            Artist(
                id = SpotifyId("art1"),
                name = "Popular Artist",
                topTracks = listOf(track),
            )
        internalBackend.artistHandler = { artist }

        val result = router.getArtist(SpotifyId("art1"))

        assertNotNull(result)
        assertEquals("Popular Artist", result?.name)
        assertEquals(1, result?.topTracks?.size)
        assertEquals(1, internalBackend.artistCallCount)
        assertEquals(0, officialBackend.artistCallCount)
    }

    @Test
    fun getArtist_fallsBackToOfficialSearchWhenInternalFails() {
        val searchTrack =
            Track(
                id = SpotifyId("t2"),
                title = "Search Hit",
                artists = emptyList(),
                durationMs = 210000L,
            )
        internalBackend.artistHandler = { throw SpotifyException("Internal failed") }
        officialBackend.artistHandler = {
            Artist(id = SpotifyId("art1"), name = "Popular Artist", topTracks = emptyList())
        }
        officialBackend.searchHandler = { query, _, _ ->
            assertEquals("Popular Artist", query)
            listOf(searchTrack)
        }

        val result = router.getArtist(SpotifyId("art1"))

        assertNotNull(result)
        assertEquals("Popular Artist", result?.name)
        assertEquals(1, result?.topTracks?.size)
        assertEquals("Search Hit", result?.topTracks?.get(0)?.title)
        assertEquals(1, internalBackend.artistCallCount)
        assertEquals(1, officialBackend.artistCallCount)
        assertEquals(1, officialBackend.searchCallCount)
    }

    @Test
    fun getIsrc_callsOfficialOnly() {
        val expectedIsrc = Isrc("USUM71607007")
        officialBackend.isrcHandler = { expectedIsrc }

        val result = router.getIsrc(SpotifyId("t1"))

        assertEquals(expectedIsrc, result)
        assertEquals(1, officialBackend.isrcCallCount)
        assertEquals(0, internalBackend.isrcCallCount)
    }

    @Test
    fun routing_skipsBackendCoolingDown() {
        tokenManager.coolDown(TokenKind.CLIENT, 1.hours)

        val track =
            Track(
                id = SpotifyId("t1"),
                title = "Internal Track",
                artists = emptyList(),
                durationMs = 120000L,
            )
        internalBackend.trackHandler = { track }

        val result = router.getTrack(SpotifyId("t1"))

        assertNotNull(result)
        assertEquals("Internal Track", result?.title)
        assertEquals(0, officialBackend.trackCallCount)
        assertEquals(1, internalBackend.trackCallCount)
    }
}
