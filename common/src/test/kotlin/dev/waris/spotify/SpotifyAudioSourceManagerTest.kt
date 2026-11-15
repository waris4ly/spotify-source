package dev.waris.spotify

import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager
import com.sedmelluq.discord.lavaplayer.tools.io.MessageInput
import com.sedmelluq.discord.lavaplayer.tools.io.MessageOutput
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioReference
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo
import dev.waris.spotify.api.ApiRouter
import dev.waris.spotify.api.SpotifyHttp
import dev.waris.spotify.api.internal.InternalApi
import dev.waris.spotify.api.official.OfficialApi
import dev.waris.spotify.auth.AccessToken
import dev.waris.spotify.auth.TokenKind
import dev.waris.spotify.auth.TokenManager
import dev.waris.spotify.auth.TokenProvider
import dev.waris.spotify.convert.AudioItemFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.TimeSource
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SpotifyAudioSourceManagerTest {
    private lateinit var server: MockWebServer
    private lateinit var httpInterfaceManager: HttpInterfaceManager
    private lateinit var tokenManager: TokenManager
    private lateinit var sourceManager: SpotifyAudioSourceManager
    private lateinit var playerManager: DefaultAudioPlayerManager

    private class MockTokenProvider(
        override val kind: TokenKind,
    ) : TokenProvider {
        override val isCoolingDown: Boolean get() = false

        override fun fetchToken(): AccessToken =
            AccessToken(
                value = "test-${kind.name.lowercase()}-token",
                expiresAt = TimeSource.Monotonic.markNow() + 1.hours,
                expiresInSeconds = 3600,
            )

        override fun coolDown(duration: Duration) {}
    }

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()

        httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager()
        tokenManager =
            TokenManager(
                mapOf(
                    TokenKind.CLIENT to MockTokenProvider(TokenKind.CLIENT),
                    TokenKind.ANONYMOUS to MockTokenProvider(TokenKind.ANONYMOUS),
                ),
            )
        tokenManager.setToken(
            TokenKind.CLIENT,
            AccessToken("test-client-token", TimeSource.Monotonic.markNow() + 1.hours, 3600),
        )
        tokenManager.setToken(
            TokenKind.ANONYMOUS,
            AccessToken("test-anon-token", TimeSource.Monotonic.markNow() + 1.hours, 3600),
        )

        val spotifyHttp = SpotifyHttp(httpInterfaceManager, tokenManager)
        val officialBaseUrl = server.url("/v1").toString().removeSuffix("/")
        val internalEndpointUrl = server.url("/pathfinder/v2/query").toString()

        val options =
            SpotifyOptions(
                clientId = "test-id",
                clientSecret = "test-secret",
                internalBackend = true,
                countryCode = "US",
            )

        val officialApi =
            OfficialApi(
                http = spotifyHttp,
                baseUrl = officialBaseUrl,
                countryCode = "US",
            )
        val internalApi =
            InternalApi(
                http = spotifyHttp,
                endpointUrl = internalEndpointUrl,
                options = options,
            )

        val apiRouter = ApiRouter(listOf(officialApi, internalApi), tokenManager)
        sourceManager =
            SpotifyAudioSourceManager(
                options = options,
                httpInterfaceManager = httpInterfaceManager,
                tokenManager = tokenManager,
                apiRouter = apiRouter,
                audioItemFactory = AudioItemFactory(),
            )

        playerManager = DefaultAudioPlayerManager()
        playerManager.registerSourceManager(sourceManager)
    }

    @AfterEach
    fun tearDown() {
        server.close()
        sourceManager.shutdown()
        playerManager.shutdown()
    }

    private fun loadFixture(path: String): String {
        val stream =
            javaClass.getResourceAsStream("/fixtures/$path")
                ?: throw IllegalArgumentException("fixture not found: $path")
        return stream.bufferedReader().use { it.readText() }
    }

    @Test
    fun getSourceName_returnsSpotify() {
        assertEquals("spotify", sourceManager.sourceName)
    }

    @Test
    fun loadItem_trackUrl_loadsSpotifyAudioTrack() {
        val fixture = loadFixture("official/track.json")
        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .setHeader("Content-Type", "application/json")
                .body(fixture)
                .build(),
        )

        val item =
            sourceManager.loadItem(
                playerManager,
                AudioReference("https://open.spotify.com/track/4eoOQgFQ0rLMKNP8KcUNWc", null),
            )

        assertNotNull(item)
        assertTrue(item is SpotifyAudioTrack)
        val track = item as SpotifyAudioTrack
        assertEquals("ocean eyes", track.info.title)
        assertEquals("Billie Eilish", track.info.author)
        assertEquals(200379L, track.info.length)
        assertEquals("4eoOQgFQ0rLMKNP8KcUNWc", track.info.identifier)
        assertEquals("USUM71600787", track.info.isrc)
        assertEquals("https://open.spotify.com/track/4eoOQgFQ0rLMKNP8KcUNWc", track.info.uri)
        assertEquals(
            "https://i.scdn.co/image/ab67616d0000b273d31267868dfd6ebfa77a6ed7",
            track.info.artworkUrl,
        )
        assertFalse(track.info.isStream)
    }

    @Test
    fun loadItem_trackUri_loadsSpotifyAudioTrack() {
        val fixture = loadFixture("official/track.json")
        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .setHeader("Content-Type", "application/json")
                .body(fixture)
                .build(),
        )

        val item =
            sourceManager.loadItem(
                playerManager,
                AudioReference("spotify:track:4eoOQgFQ0rLMKNP8KcUNWc", null),
            )

        assertNotNull(item)
        assertTrue(item is SpotifyAudioTrack)
        val track = item as SpotifyAudioTrack
        assertEquals("ocean eyes", track.info.title)
    }

    @Test
    fun loadItem_albumUrl_loadsAudioPlaylist() {
        val fixture =
            loadFixture("official/album.json").replace(
                Regex(""""next":\s*"[^"]*""""),
                """"next": null""",
            )
        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .setHeader("Content-Type", "application/json")
                .body(fixture)
                .build(),
        )

        val item =
            sourceManager.loadItem(
                playerManager,
                AudioReference("https://open.spotify.com/album/7aJuG4TFXa2hmE4z1yxc3n", null),
            )

        assertNotNull(item)
        assertTrue(item is AudioPlaylist)
        val playlist = item as AudioPlaylist
        assertEquals("HIT ME HARD AND SOFT", playlist.name)
        assertFalse(playlist.isSearchResult)
        assertEquals(2, playlist.tracks.size)
        assertEquals("SKINNY", playlist.tracks[0].info.title)
        assertEquals("LUNCH", playlist.tracks[1].info.title)
    }

    @Test
    fun loadItem_playlistUrl_loadsAudioPlaylist() {
        val fixture = loadFixture("internal/playlist_normal.json")
        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .setHeader("Content-Type", "application/json")
                .body(fixture)
                .build(),
        )

        val item =
            sourceManager.loadItem(
                playerManager,
                AudioReference("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M", null),
            )

        assertNotNull(item)
        assertTrue(item is AudioPlaylist)
        val playlist = item as AudioPlaylist
        assertEquals("Today's Top Hits", playlist.name)
        assertFalse(playlist.isSearchResult)
        assertEquals(2, playlist.tracks.size)
        assertEquals("Never Gonna Give You Up", playlist.tracks[0].info.title)
    }

    @Test
    fun loadItem_playlistPrivate_throwsFriendlyException() {
        val fixture = loadFixture("internal/playlist_private.json")
        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .setHeader("Content-Type", "application/json")
                .body(fixture)
                .build(),
        )

        val exception =
            assertThrows(FriendlyException::class.java) {
                sourceManager.loadItem(
                    playerManager,
                    AudioReference(
                        "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M",
                        null,
                    ),
                )
            }
        assertEquals(FriendlyException.Severity.COMMON, exception.severity)
        assertTrue(exception.message?.contains("private or not found") == true)
    }

    @Test
    fun loadItem_artistUrl_loadsAudioPlaylist() {
        val fixture = loadFixture("internal/artist.json")
        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .setHeader("Content-Type", "application/json")
                .body(fixture)
                .build(),
        )

        val item =
            sourceManager.loadItem(
                playerManager,
                AudioReference("https://open.spotify.com/artist/4Z8W4fKeB5YxbusRsdQVPb", null),
            )

        assertNotNull(item)
        assertTrue(item is AudioPlaylist)
        val playlist = item as AudioPlaylist
        assertEquals("Radiohead", playlist.name)
        assertFalse(playlist.isSearchResult)
        assertEquals(1, playlist.tracks.size)
        assertEquals("Creep", playlist.tracks[0].info.title)
    }

    @Test
    fun loadItem_searchQuery_loadsSearchPlaylist() {
        val fixture = loadFixture("official/search.json")
        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .setHeader("Content-Type", "application/json")
                .body(fixture)
                .build(),
        )

        val item =
            sourceManager.loadItem(
                playerManager,
                AudioReference("spsearch:ocean eyes", null),
            )

        assertNotNull(item)
        assertTrue(item is AudioPlaylist)
        val playlist = item as AudioPlaylist
        assertTrue(playlist.isSearchResult)
        assertEquals("Search results for: ocean eyes", playlist.name)
        assertEquals(1, playlist.tracks.size)
        assertEquals("ocean eyes", playlist.tracks[0].info.title)
    }

    @Test
    fun loadItem_searchEmpty_returnsNoTrack() {
        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .setHeader("Content-Type", "application/json")
                .body("""{"tracks": {"items": []}}""")
                .build(),
        )

        val item =
            sourceManager.loadItem(
                playerManager,
                AudioReference("spsearch:unknown nonexistent query", null),
            )

        assertEquals(AudioReference.NO_TRACK, item)
    }

    @Test
    fun loadItem_nonSpotifyUrl_returnsNull() {
        val item =
            sourceManager.loadItem(
                playerManager,
                AudioReference("https://www.youtube.com/watch?v=dQw4w9WgXcQ", null),
            )

        assertNull(item)
    }

    @Test
    fun loadItem_unknownSpotifyUrl_returnsNoTrack() {
        val item =
            sourceManager.loadItem(
                playerManager,
                AudioReference("https://open.spotify.com/unknown/12345", null),
            )

        assertEquals(AudioReference.NO_TRACK, item)
    }

    @Test
    fun loadItem_invalidSpotifyUri_returnsNoTrack() {
        val item =
            sourceManager.loadItem(
                playerManager,
                AudioReference("spotify:track:invalid_characters!!!", null),
            )

        assertEquals(AudioReference.NO_TRACK, item)
    }

    @Test
    fun isTrackEncodable_returnsTrue() {
        val trackInfo =
            AudioTrackInfo(
                "title",
                "author",
                1000L,
                "id1",
                false,
                "https://open.spotify.com/track/id1",
                null,
                null,
            )
        val track = SpotifyAudioTrack(trackInfo, sourceManager)

        assertTrue(sourceManager.isTrackEncodable(track))
    }

    @Test
    fun encodeAndDecodeTrack_preservesTrackDetails() {
        val trackInfo =
            AudioTrackInfo(
                "Test Title",
                "Test Author",
                240000L,
                "testIdentifier",
                false,
                "https://open.spotify.com/track/testIdentifier",
                "https://example.com/art.jpg",
                "USUM71600787",
            )
        val originalTrack = SpotifyAudioTrack(trackInfo, sourceManager)

        val byteStream = ByteArrayOutputStream()
        val dataOutput = DataOutputStream(byteStream)
        sourceManager.encodeTrack(originalTrack, dataOutput)

        val dataInput = DataInputStream(ByteArrayInputStream(byteStream.toByteArray()))
        val decoded = sourceManager.decodeTrack(trackInfo, dataInput)

        assertNotNull(decoded)
        assertTrue(decoded is SpotifyAudioTrack)
        assertEquals(trackInfo.title, decoded.info.title)
        assertEquals(trackInfo.author, decoded.info.author)
        assertEquals(trackInfo.length, decoded.info.length)
        assertEquals(trackInfo.identifier, decoded.info.identifier)
        assertEquals(trackInfo.uri, decoded.info.uri)
        assertEquals(trackInfo.artworkUrl, decoded.info.artworkUrl)
        assertEquals(trackInfo.isrc, decoded.info.isrc)
        assertEquals(sourceManager, decoded.sourceManager)
    }

    @Test
    fun playerManager_encodeAndDecodeTrack_roundtrip() {
        val trackInfo =
            AudioTrackInfo(
                "Starboy",
                "The Weeknd",
                230000L,
                "4eoOQgFQ0rLMKNP8KcUNWc",
                false,
                "https://open.spotify.com/track/4eoOQgFQ0rLMKNP8KcUNWc",
                "https://example.com/art.jpg",
                "USUM71607007",
            )
        val originalTrack = SpotifyAudioTrack(trackInfo, sourceManager)

        val outputStream = ByteArrayOutputStream()
        playerManager.encodeTrack(MessageOutput(outputStream), originalTrack)

        val inputStream = ByteArrayInputStream(outputStream.toByteArray())
        val decodedHolder = playerManager.decodeTrack(MessageInput(inputStream))

        assertNotNull(decodedHolder)
        val decodedTrack = decodedHolder?.decodedTrack
        assertNotNull(decodedTrack)
        assertTrue(decodedTrack is SpotifyAudioTrack)
        assertEquals("Starboy", decodedTrack?.info?.title)
        assertEquals("The Weeknd", decodedTrack?.info?.author)
        assertEquals("4eoOQgFQ0rLMKNP8KcUNWc", decodedTrack?.info?.identifier)
        assertEquals("USUM71607007", decodedTrack?.info?.isrc)
    }

    @Test
    fun loadItem_identicalConcurrentRequests_shareSingleCall() {
        val json = loadFixture("official/track.json")
        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .setHeader("Content-Type", "application/json")
                .body(json)
                .build(),
        )

        val trackUrl = "https://open.spotify.com/track/4eoOQgFQ0rLMKNP8KcUNWc"
        val executor = Executors.newFixedThreadPool(4)
        try {
            val tasks =
                List(4) {
                    Callable {
                        sourceManager.loadItem(playerManager, AudioReference(trackUrl, null))
                    }
                }
            val futures = tasks.map { executor.submit(it) }
            val results = futures.map { it.get(5, TimeUnit.SECONDS) }

            assertEquals(1, server.requestCount)
            for (res in results) {
                assertNotNull(res)
                assertTrue(res is AudioTrack)
                assertEquals("ocean eyes", (res as AudioTrack).info.title)
            }
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun loadItem_subsequentCall_usesMetadataCache() {
        val json = loadFixture("official/track.json")
        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .setHeader("Content-Type", "application/json")
                .body(json)
                .build(),
        )

        val ref =
            AudioReference("https://open.spotify.com/track/4eoOQgFQ0rLMKNP8KcUNWc", null)
        val first = sourceManager.loadItem(playerManager, ref)
        assertNotNull(first)
        assertTrue(first is AudioTrack)
        assertEquals(1, server.requestCount)

        val second = sourceManager.loadItem(playerManager, ref)
        assertNotNull(second)
        assertTrue(second is AudioTrack)
        assertEquals(1, server.requestCount)
        assertEquals("ocean eyes", (second as AudioTrack).info.title)
    }

    @Test
    fun loadItem_episode_loadsAudioTrack() {
        val json = loadFixture("internal/episode.json")
        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .setHeader("Content-Type", "application/json")
                .body(json)
                .build(),
        )

        val ref =
            AudioReference("https://open.spotify.com/episode/5125459801234567890123", null)
        val item = sourceManager.loadItem(playerManager, ref)

        assertNotNull(item)
        assertTrue(item is AudioTrack)
        val track = item as AudioTrack
        assertEquals("The Future of AI", track.info.title)
        assertEquals("Tech Horizons", track.info.author)
        assertNull(track.info.isrc)
    }

    @Test
    fun loadItem_show_loadsPlaylist() {
        val json = loadFixture("internal/show.json")
        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .setHeader("Content-Type", "application/json")
                .body(json)
                .build(),
        )

        val ref = AudioReference("https://open.spotify.com/show/1234567890123456789012", null)
        val item = sourceManager.loadItem(playerManager, ref)

        assertNotNull(item)
        assertTrue(item is SpotifyAudioPlaylist)
        val playlist = item as SpotifyAudioPlaylist
        assertEquals("Tech Horizons", playlist.name)
        assertEquals("show", playlist.type)
        assertEquals(2, playlist.tracks.size)
        assertEquals("The Future of AI", playlist.tracks[0].info.title)
        assertEquals("Quantum Computing", playlist.tracks[1].info.title)
    }

    @Test
    fun loadItem_spsuggest_loadsSearchPlaylist() {
        val json = loadFixture("internal/search_suggestions.json")
        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .setHeader("Content-Type", "application/json")
                .body(json)
                .build(),
        )

        val ref = AudioReference("spsuggest:taylor", null)
        val item = sourceManager.loadItem(playerManager, ref)

        assertNotNull(item)
        assertTrue(item is AudioPlaylist)
        val playlist = item as AudioPlaylist
        assertTrue(playlist.isSearchResult)
        assertEquals(2, playlist.tracks.size)
    }

    @Test
    fun loadItem_sprec_loadsRecommendationsPlaylist() {
        val json = loadFixture("internal/recommendations.json")
        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .setHeader("Content-Type", "application/json")
                .body(json)
                .build(),
        )

        val ref = AudioReference("sprec:2xLMifQCjDGFmkHkpNLD9h", null)
        val item = sourceManager.loadItem(playerManager, ref)

        assertNotNull(item)
        assertTrue(item is SpotifyAudioPlaylist)
        val playlist = item as SpotifyAudioPlaylist
        assertEquals(1, playlist.tracks.size)
        assertEquals("SICKO MODE", playlist.tracks[0].info.title)
    }

    @Test
    fun loadItem_spsimilar_loadsSimilarPlaylist() {
        val json = loadFixture("internal/similar_albums.json")
        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .setHeader("Content-Type", "application/json")
                .body(json)
                .build(),
        )

        val ref = AudioReference("spsimilar:41GuZcammIkupRtYGDsvVa", null)
        val item = sourceManager.loadItem(playerManager, ref)

        assertNotNull(item)
        assertTrue(item is SpotifyAudioPlaylist)
        val playlist = item as SpotifyAudioPlaylist
        assertEquals(1, playlist.tracks.size)
        assertEquals("ASTROWORLD", playlist.tracks[0].info.title)
    }

    @Test
    fun loadItem_localFileDirectUrl_returnsNoTrack() {
        val ref = AudioReference("spotify:local:Artist:Album:Song:200", null)
        val item = sourceManager.loadItem(playerManager, ref)
        assertEquals(AudioReference.NO_TRACK, item)
    }
}
