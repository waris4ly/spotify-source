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
import dev.waris.spotify.api.lyrics.Lyrics
import dev.waris.spotify.api.lyrics.LyricsLine
import dev.waris.spotify.api.lyrics.LyricsProvider
import dev.waris.spotify.api.lyrics.LyricsQuery
import dev.waris.spotify.api.lyrics.LyricsService
import dev.waris.spotify.api.lyrics.LyricsSync
import dev.waris.spotify.api.official.OfficialApi
import dev.waris.spotify.auth.AccessToken
import dev.waris.spotify.auth.TokenKind
import dev.waris.spotify.auth.TokenManager
import dev.waris.spotify.auth.TokenProvider
import dev.waris.spotify.convert.AudioItemFactory
import dev.waris.spotify.enrich.ArtworkColors
import dev.waris.spotify.enrich.CanvasLookup
import dev.waris.spotify.enrich.ExtractedColors
import dev.waris.spotify.mirror.TrackMirror
import dev.waris.spotify.model.SpotifyId
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
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
    fun loadItem_playlistCutShortByFailure_notCached() {
        val fixture = loadFixture("internal/playlist_normal.json")
        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .setHeader("Content-Type", "application/json")
                .body(fixture)
                .build(),
        )
        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .setHeader("Content-Type", "application/json")
                .body("""{"errors":[{"message":"fetch failed"}]}""")
                .build(),
        )

        val playlistRef =
            AudioReference("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M", null)
        val item = sourceManager.loadItem(playerManager, playlistRef)

        assertNotNull(item)
        assertTrue(item is SpotifyAudioPlaylist)
        val playlist = item as SpotifyAudioPlaylist
        assertEquals(2, playlist.tracks.size)
        assertTrue(playlist.truncated)

        assertNull(
            sourceManager.metadataCache.get(
                "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M",
            ),
        )

        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .setHeader("Content-Type", "application/json")
                .body(fixture)
                .build(),
        )
        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .setHeader("Content-Type", "application/json")
                .body("""{"errors":[{"message":"fetch failed"}]}""")
                .build(),
        )

        val secondItem = sourceManager.loadItem(playerManager, playlistRef)
        assertNotNull(secondItem)
        assertEquals(4, server.requestCount)
    }

    @Test
    fun loadItem_playlistTruncatedByLimit_isCached() {
        val limitedOptions =
            SpotifyOptions(
                clientId = "test-id",
                clientSecret = "test-secret",
                internalBackend = true,
                countryCode = "US",
                playlistLoadLimit = 2,
            )
        val spotifyHttp = SpotifyHttp(httpInterfaceManager, tokenManager)
        val internalApi =
            InternalApi(
                http = spotifyHttp,
                endpointUrl = server.url("/pathfinder/v2/query").toString(),
                options = limitedOptions,
            )
        val apiRouter = ApiRouter(listOf(internalApi), tokenManager)
        val limitedSourceManager =
            SpotifyAudioSourceManager(
                options = limitedOptions,
                httpInterfaceManager = httpInterfaceManager,
                tokenManager = tokenManager,
                apiRouter = apiRouter,
                audioItemFactory = AudioItemFactory(),
            )
        try {
            val fixture = loadFixture("internal/playlist_normal.json")
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(200)
                    .setHeader("Content-Type", "application/json")
                    .body(fixture)
                    .build(),
            )

            val playlistRef =
                AudioReference("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M", null)
            val item = limitedSourceManager.loadItem(playerManager, playlistRef)

            assertNotNull(item)
            assertTrue(item is SpotifyAudioPlaylist)
            val playlist = item as SpotifyAudioPlaylist
            assertEquals(2, playlist.tracks.size)
            assertFalse(playlist.truncated)

            val cached =
                limitedSourceManager.metadataCache.get(
                    "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M",
                )
            assertNotNull(cached)

            val secondItem = limitedSourceManager.loadItem(playerManager, playlistRef)
            assertNotNull(secondItem)
            assertEquals(1, server.requestCount)
        } finally {
            limitedSourceManager.shutdown()
        }
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
    fun encodeTrack_decodeTrack_preservesCanvasColorsAndPreview() {
        val trackInfo =
            AudioTrackInfo(
                "Track With Metadata",
                "Author",
                210000L,
                "metaId",
                false,
                "https://open.spotify.com/track/metaId",
                "https://example.com/art.jpg",
                "USUM71600787",
            )
        val originalTrack =
            SpotifyAudioTrack(
                trackInfo,
                sourceManager,
                canvasUrl = "https://canvas.spotify.com/canvas1.mp4",
                colors = mapOf("background" to "#121212", "text" to "#ffffff"),
                previewUrl = "https://p.scdn.co/mp3-preview/preview1.mp3",
            )

        val byteStream = ByteArrayOutputStream()
        val dataOutput = DataOutputStream(byteStream)
        sourceManager.encodeTrack(originalTrack, dataOutput)

        val dataInput = DataInputStream(ByteArrayInputStream(byteStream.toByteArray()))
        val decoded = sourceManager.decodeTrack(trackInfo, dataInput) as SpotifyAudioTrack

        assertEquals(originalTrack.canvasUrl, decoded.canvasUrl)
        assertEquals(originalTrack.colors, decoded.colors)
        assertEquals(originalTrack.previewUrl, decoded.previewUrl)
    }

    @Test
    fun encodeTrack_decodeTrack_allFieldsNull() {
        val trackInfo =
            AudioTrackInfo(
                "Track With Nulls",
                "Author",
                180000L,
                "nullId",
                false,
                "https://open.spotify.com/track/nullId",
                null,
                null,
            )
        val originalTrack =
            SpotifyAudioTrack(
                trackInfo,
                sourceManager,
                canvasUrl = null,
                colors = null,
                previewUrl = null,
            )

        val byteStream = ByteArrayOutputStream()
        val dataOutput = DataOutputStream(byteStream)
        sourceManager.encodeTrack(originalTrack, dataOutput)

        val dataInput = DataInputStream(ByteArrayInputStream(byteStream.toByteArray()))
        val decoded = sourceManager.decodeTrack(trackInfo, dataInput) as SpotifyAudioTrack

        assertNull(decoded.canvasUrl)
        assertNull(decoded.colors)
        assertNull(decoded.previewUrl)
    }

    @Test
    fun decodeTrack_toleratesEmptyPayloadFromOldEncoder() {
        val trackInfo =
            AudioTrackInfo(
                "Old Track",
                "Author",
                180000L,
                "oldId",
                false,
                "https://open.spotify.com/track/oldId",
                null,
                null,
            )
        val dataInput = DataInputStream(ByteArrayInputStream(ByteArray(0)))
        val decoded = sourceManager.decodeTrack(trackInfo, dataInput) as SpotifyAudioTrack

        assertNotNull(decoded)
        assertNull(decoded.canvasUrl)
        assertNull(decoded.colors)
        assertNull(decoded.previewUrl)
    }

    @Test
    fun decodeTrack_toleratesTruncatedPayload() {
        val trackInfo =
            AudioTrackInfo(
                "Truncated Track",
                "Author",
                180000L,
                "truncId",
                false,
                "https://open.spotify.com/track/truncId",
                null,
                null,
            )
        val byteStream = ByteArrayOutputStream()
        val dataOutput = DataOutputStream(byteStream)
        dataOutput.writeByte(1)
        dataOutput.writeBoolean(true)

        val dataInput = DataInputStream(ByteArrayInputStream(byteStream.toByteArray()))
        val decoded = sourceManager.decodeTrack(trackInfo, dataInput) as SpotifyAudioTrack

        assertNotNull(decoded)
        assertNull(decoded.canvasUrl)
        assertNull(decoded.colors)
        assertNull(decoded.previewUrl)
    }

    @Test
    fun attach_attachesPlayerManagerWithoutCallingLoadItem() {
        val isolatedSource = SpotifyAudioSourceManager()
        val isolatedManager = DefaultAudioPlayerManager()
        try {
            isolatedSource.attach(isolatedManager)
            val trackInfo =
                AudioTrackInfo(
                    "Track Without Load",
                    "Author",
                    180000L,
                    "standaloneId",
                    false,
                    "https://open.spotify.com/track/standaloneId",
                )
            val dataInput = DataInputStream(ByteArrayInputStream(ByteArray(0)))
            val decoded = isolatedSource.decodeTrack(trackInfo, dataInput)

            assertNotNull(decoded)
            assertNotNull(isolatedSource.playerManager)
            assertEquals(isolatedManager, isolatedSource.playerManager)
        } finally {
            isolatedSource.shutdown()
            isolatedManager.shutdown()
        }
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

    @Test
    fun loadItem_enrichmentNeverFinishingDoesNotDelayBeyondDeadlineAndLeavesFieldsNull() {
        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .setHeader("Content-Type", "application/json")
                .body(loadFixture("official/track.json"))
                .build(),
        )

        val spotifyHttp =
            SpotifyHttp(
                httpInterfaceManager = httpInterfaceManager,
                tokenManager = tokenManager,
            )
        val slowCanvas =
            object : CanvasLookup(spotifyHttp) {
                override fun fetchCanvasUrl(trackId: SpotifyId): String? {
                    Thread.sleep(10000)
                    return "https://canvas.url"
                }
            }
        val slowColors =
            object : ArtworkColors(spotifyHttp) {
                override fun fetchColors(artworkUrl: String?): ExtractedColors? {
                    Thread.sleep(10000)
                    return ExtractedColors(dark = "#000", light = "#fff", raw = "#123")
                }
            }

        val officialApi =
            OfficialApi(
                http = spotifyHttp,
                baseUrl = server.url("/v1/").toString(),
                countryCode = "US",
            )
        val router = ApiRouter(listOf(officialApi), tokenManager)
        val options = SpotifyOptions()
        val manager =
            SpotifyAudioSourceManager(
                options = options,
                httpInterfaceManager = httpInterfaceManager,
                tokenManager = tokenManager,
                apiRouter = router,
                audioItemFactory = AudioItemFactory(),
                trackMirror = TrackMirror(options, router),
                canvasLookup = slowCanvas,
                artworkColors = slowColors,
            )

        val startTime = System.currentTimeMillis()
        try {
            val track =
                manager.loadItem(
                    playerManager,
                    AudioReference("https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT", null),
                ) as? SpotifyAudioTrack
            val elapsed = System.currentTimeMillis() - startTime

            assertNotNull(track)
            assertNull(track?.canvasUrl)
            assertNull(track?.colors)
            assertTrue(elapsed < 1400, "Expected elapsed < 1400ms but was ${elapsed}ms")
        } finally {
            manager.shutdown()
        }
    }

    @Test
    fun loadItem_failingEnrichmentLeavesFieldsNullAndDoesNotThrow() {
        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .setHeader("Content-Type", "application/json")
                .body(loadFixture("official/track.json"))
                .build(),
        )

        val spotifyHttp =
            SpotifyHttp(
                httpInterfaceManager = httpInterfaceManager,
                tokenManager = tokenManager,
            )
        val failingCanvas =
            object : CanvasLookup(spotifyHttp) {
                override fun fetchCanvasUrl(trackId: SpotifyId): String? =
                    throw RuntimeException("canvas backend failure")
            }
        val failingColors =
            object : ArtworkColors(spotifyHttp) {
                override fun fetchColors(artworkUrl: String?): ExtractedColors? =
                    throw RuntimeException("colors backend failure")
            }

        val officialApi =
            OfficialApi(
                http = spotifyHttp,
                baseUrl = server.url("/v1/").toString(),
                countryCode = "US",
            )
        val router = ApiRouter(listOf(officialApi), tokenManager)
        val options = SpotifyOptions()
        val manager =
            SpotifyAudioSourceManager(
                options = options,
                httpInterfaceManager = httpInterfaceManager,
                tokenManager = tokenManager,
                apiRouter = router,
                audioItemFactory = AudioItemFactory(),
                trackMirror = TrackMirror(options, router),
                canvasLookup = failingCanvas,
                artworkColors = failingColors,
            )

        try {
            val track =
                manager.loadItem(
                    playerManager,
                    AudioReference("https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT", null),
                ) as? SpotifyAudioTrack

            assertNotNull(track)
            assertNull(track?.canvasUrl)
            assertNull(track?.colors)
        } finally {
            manager.shutdown()
        }
    }

    @Test
    fun shutdown_terminatesEnrichmentExecutorAndLeavesNoLiveThreads() {
        val options = SpotifyOptions()
        val router = ApiRouter(emptyList(), tokenManager)
        val manager =
            SpotifyAudioSourceManager(
                options = options,
                httpInterfaceManager = httpInterfaceManager,
                tokenManager = tokenManager,
                apiRouter = router,
                audioItemFactory = AudioItemFactory(),
                trackMirror = TrackMirror(options, router),
            )

        manager.enrichmentExecutor.submit { Thread.sleep(50) }

        manager.shutdown()

        assertTrue(manager.enrichmentExecutor.isShutdown)
        assertTrue(manager.enrichmentExecutor.isTerminated)

        val liveEnrichmentThreads =
            Thread.getAllStackTraces().keys.filter {
                it.name.startsWith("spotify-enrichment") && it.isAlive
            }
        assertTrue(liveEnrichmentThreads.isEmpty(), "Found live threads: $liveEnrichmentThreads")
    }

    @Test
    fun loadItem_unrelatedUrlContainingSpotifyDotCom_returnsNull() {
        val item =
            sourceManager.loadItem(
                playerManager,
                AudioReference("https://example.com/?u=open.spotify.com/track/x", null),
            )
        assertNull(item)
    }

    @Test
    fun loadLyrics_whenLyricsDisabled_returnsNull() {
        val manager = SpotifyAudioSourceManager(SpotifyOptions(lyrics = false))
        val track =
            SpotifyAudioTrack(
                AudioTrackInfo(
                    "Track",
                    "Artist",
                    200_000,
                    "4eoOQgFQ0rLMKNP8KcUNWc",
                    false,
                    "https://open.spotify.com/track/4eoOQgFQ0rLMKNP8KcUNWc",
                ),
                sourceManager,
            )
        assertNull(manager.loadLyrics(track))
    }

    @Test
    fun loadLyrics_whenLyricsServiceProvided_delegatesToService() {
        val lyrics =
            Lyrics(
                provider = "spotify",
                providerLabel = "Musixmatch",
                sync = LyricsSync.LINE,
                language = "en",
                rightToLeft = false,
                lines = listOf(LyricsLine(0, 1000, "Words")),
            )
        val provider =
            object : LyricsProvider {
                override val name = "test"

                override fun isAvailable() = true

                override fun fetch(query: LyricsQuery) = lyrics
            }
        val service = LyricsService(listOf(provider), 10.minutes, 10.minutes)
        val options = SpotifyOptions()
        val router = ApiRouter(emptyList(), tokenManager)
        val manager =
            SpotifyAudioSourceManager(
                options = options,
                httpInterfaceManager = httpInterfaceManager,
                tokenManager = tokenManager,
                apiRouter = router,
                audioItemFactory = AudioItemFactory(),
                trackMirror = TrackMirror(options, router),
                lyricsService = service,
            )

        val track =
            SpotifyAudioTrack(
                AudioTrackInfo(
                    "Track",
                    "Artist",
                    200_000,
                    "4eoOQgFQ0rLMKNP8KcUNWc",
                    false,
                    "https://open.spotify.com/track/4eoOQgFQ0rLMKNP8KcUNWc",
                ),
                manager,
            )

        val loaded = manager.loadLyrics(track)
        assertNotNull(loaded)
        assertEquals("spotify", loaded!!.provider)
        assertEquals("Musixmatch", loaded.providerLabel)
    }
}
