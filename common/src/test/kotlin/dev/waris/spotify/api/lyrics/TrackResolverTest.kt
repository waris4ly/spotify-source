package dev.waris.spotify.api.lyrics

import dev.waris.spotify.api.ApiRouter
import dev.waris.spotify.api.Operation
import dev.waris.spotify.api.SpotifyApi
import dev.waris.spotify.auth.TokenKind
import dev.waris.spotify.auth.TokenManager
import dev.waris.spotify.model.Album
import dev.waris.spotify.model.Artist
import dev.waris.spotify.model.Isrc
import dev.waris.spotify.model.Playlist
import dev.waris.spotify.model.SpotifyId
import dev.waris.spotify.model.Track
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TrackResolverTest {
    private fun createResolver(
        searchHandler: (String, Int, Int) -> List<Track>,
        durationToleranceMs: Long = 3000,
    ): TrackResolver {
        val fakeApi =
            object : SpotifyApi {
                override val name: String = "official"

                override fun supports(operation: Operation): Boolean = operation is Operation.Search

                override fun tokenKindFor(operation: Operation): TokenKind? = null

                override fun search(
                    query: String,
                    limit: Int,
                    offset: Int,
                ): List<Track> = searchHandler(query, limit, offset)

                override fun getTrack(id: SpotifyId): Track? = null

                override fun getAlbum(
                    id: SpotifyId,
                    limit: Int,
                ): Album? = null

                override fun getPlaylist(
                    id: SpotifyId,
                    limit: Int,
                ): Playlist? = null

                override fun getArtist(id: SpotifyId): Artist? = null

                override fun getIsrc(id: SpotifyId): Isrc? = null
            }

        val tokenManager = TokenManager(emptyMap())
        val router = ApiRouter(listOf(fakeApi), tokenManager)
        return TrackResolver(router, durationToleranceMs = durationToleranceMs)
    }

    @Test
    fun resolve_isrcHit_winsImmediately() {
        val resolver =
            createResolver(
                searchHandler = { query, _, _ ->
                    if (query.startsWith("isrc:")) {
                        listOf(
                            Track(
                                id = SpotifyId("isrcTrackId1234567890"),
                                title = "Some Track",
                                artists = listOf(Artist(SpotifyId("artist1"), "Some Artist")),
                                durationMs = 200_000,
                            ),
                        )
                    } else {
                        emptyList()
                    }
                },
            )

        val query =
            LyricsQuery(
                title = "Different Title",
                artist = "Different Artist",
                durationMs = 200_000,
                isrc = Isrc("USRC12345678"),
            )

        val result = resolver.resolve(query)
        assertEquals(SpotifyId("isrcTrackId1234567890"), result)
    }

    @Test
    fun resolve_isrcWrongDuration_fallsBackToFuzzySearch() {
        val resolver =
            createResolver(
                searchHandler = { query, _, _ ->
                    if (query.startsWith("isrc:")) {
                        // duration mismatch: 300s vs 200s (diff 100s > 3s)
                        listOf(
                            Track(
                                id = SpotifyId("wrongDurationTrackId1"),
                                title = "Some Track",
                                artists = listOf(Artist(SpotifyId("artist1"), "Some Artist")),
                                durationMs = 300_000,
                            ),
                        )
                    } else {
                        listOf(
                            Track(
                                id = SpotifyId("fuzzyHitTrackId12345"),
                                title = "Wanted Song",
                                artists = listOf(Artist(SpotifyId("artist2"), "Wanted Artist")),
                                durationMs = 200_500,
                            ),
                        )
                    }
                },
            )

        val query =
            LyricsQuery(
                title = "Wanted Song",
                artist = "Wanted Artist",
                durationMs = 200_000,
                isrc = Isrc("USRC12345678"),
            )

        val result = resolver.resolve(query)
        assertEquals(SpotifyId("fuzzyHitTrackId12345"), result)
    }

    @Test
    fun resolve_remasterAndBracketNoise_normalizedAndMatched() {
        val resolver =
            createResolver(
                searchHandler = { _, _, _ ->
                    listOf(
                        Track(
                            id = SpotifyId("remasterTrackId12345"),
                            title = "Bohemian Rhapsody - Remastered 2011",
                            artists = listOf(Artist(SpotifyId("queen"), "Queen")),
                            durationMs = 354_000,
                        ),
                    )
                },
            )

        val query =
            LyricsQuery(
                title = "Bohemian Rhapsody (Official Video)",
                artist = "Queen",
                durationMs = 355_000,
            )

        val result = resolver.resolve(query)
        assertEquals(SpotifyId("remasterTrackId12345"), result)
    }

    @Test
    fun resolve_lowScore_returnsNull() {
        val resolver =
            createResolver(
                searchHandler = { _, _, _ ->
                    listOf(
                        Track(
                            id = SpotifyId("unrelatedTrackId1234"),
                            title = "Completely Different Song",
                            artists = listOf(Artist(SpotifyId("other"), "Other Artist")),
                            durationMs = 200_000,
                        ),
                    )
                },
            )

        val query =
            LyricsQuery(
                title = "Wanted Song",
                artist = "Wanted Artist",
                durationMs = 200_000,
            )

        val result = resolver.resolve(query)
        assertNull(result)
    }

    @Test
    fun resolve_zeroDuration_ignoresDurationTolerance() {
        val resolver =
            createResolver(
                searchHandler = { _, _, _ ->
                    listOf(
                        Track(
                            id = SpotifyId("zeroDurationMatch1234"),
                            title = "Wanted Song",
                            artists = listOf(Artist(SpotifyId("artist"), "Wanted Artist")),
                            durationMs = 450_000,
                        ),
                    )
                },
            )

        val query =
            LyricsQuery(
                title = "Wanted Song",
                artist = "Wanted Artist",
                durationMs = 0, // duration unknown
            )

        val result = resolver.resolve(query)
        assertEquals(SpotifyId("zeroDurationMatch1234"), result)
    }
}
