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
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource
import kotlin.time.TimeSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ApiRouterResilienceTest {
    private lateinit var timeSource: TestTimeSource
    private lateinit var tokenManager: TokenManager

    private class FakeApi(
        override val name: String,
        private val supportedOps: Set<Class<out Operation>>,
        var trackHandler: (SpotifyId) -> Track? = { null },
        var isrcHandler: (SpotifyId) -> Isrc? = { null },
    ) : SpotifyApi {
        val calls = AtomicInteger(0)

        override fun supports(operation: Operation): Boolean =
            supportedOps.contains(operation::class.java)

        override fun tokenKindFor(operation: Operation): TokenKind? = null

        override fun search(
            query: String,
            limit: Int,
            offset: Int,
        ): List<Track> = emptyList()

        override fun getTrack(id: SpotifyId): Track? {
            calls.incrementAndGet()
            return trackHandler(id)
        }

        override fun getAlbum(
            id: SpotifyId,
            limit: Int,
        ): Album? = null

        override fun getPlaylist(
            id: SpotifyId,
            limit: Int,
        ): Playlist? = null

        override fun getArtist(id: SpotifyId): Artist? = null

        override fun getIsrc(id: SpotifyId): Isrc? {
            calls.incrementAndGet()
            return isrcHandler(id)
        }
    }

    private class SimpleTokenProvider(
        override val kind: TokenKind,
    ) : TokenProvider {
        override val isCoolingDown: Boolean get() = false

        override fun fetchToken(): AccessToken =
            AccessToken("token", TimeSource.Monotonic.markNow() + 1.hours, 3600)

        override fun coolDown(duration: Duration) {}
    }

    @BeforeEach
    fun setUp() {
        timeSource = TestTimeSource()
        tokenManager =
            TokenManager(
                mapOf(
                    TokenKind.CLIENT to SimpleTokenProvider(TokenKind.CLIENT),
                    TokenKind.ANONYMOUS to SimpleTokenProvider(TokenKind.ANONYMOUS),
                ),
            )
    }

    @Test
    fun rateLimitedBackend_isSkippedAndFallsBackToSecondary() {
        val internalTrack =
            Track(
                id = SpotifyId("t1"),
                title = "Internal Song",
                artists = listOf(Artist(SpotifyId("a1"), "Artist")),
                durationMs = 180000L,
            )

        val officialApi =
            FakeApi(
                name = "official",
                supportedOps = setOf(Operation.GetTrack::class.java),
                trackHandler = {
                    throw SpotifyException.RateLimit("rate limit", retryAfterSeconds = 60L)
                },
            )
        val internalApi =
            FakeApi(
                name = "internal",
                supportedOps = setOf(Operation.GetTrack::class.java),
                trackHandler = { internalTrack },
            )

        val router =
            ApiRouter(
                backends = listOf(officialApi, internalApi),
                tokenManager = tokenManager,
                timeSource = timeSource,
                defaultCooldown = 60.seconds,
            )

        val firstResult = router.getTrack(SpotifyId("t1"))
        assertEquals("Internal Song", firstResult?.title)
        assertEquals(1, officialApi.calls.get())
        assertEquals(1, internalApi.calls.get())
        assertTrue(router.isBackendCoolingDown("official"))

        val secondResult = router.getTrack(SpotifyId("t1"))
        assertEquals("Internal Song", secondResult?.title)
        assertEquals(1, officialApi.calls.get())
        assertEquals(2, internalApi.calls.get())
    }

    @Test
    fun coolingBackend_isRecoveredAfterDurationExpires() {
        var officialShouldFail = true
        val officialTrack =
            Track(
                id = SpotifyId("t1"),
                title = "Official Song",
                artists = listOf(Artist(SpotifyId("a1"), "Artist")),
                durationMs = 180000L,
            )
        val internalTrack =
            Track(
                id = SpotifyId("t1"),
                title = "Internal Song",
                artists = listOf(Artist(SpotifyId("a1"), "Artist")),
                durationMs = 180000L,
            )

        val officialApi =
            FakeApi(
                name = "official",
                supportedOps = setOf(Operation.GetTrack::class.java),
                trackHandler = {
                    if (officialShouldFail) {
                        throw SpotifyException.RateLimit("rate limit", retryAfterSeconds = 60L)
                    } else {
                        officialTrack
                    }
                },
            )
        val internalApi =
            FakeApi(
                name = "internal",
                supportedOps = setOf(Operation.GetTrack::class.java),
                trackHandler = { internalTrack },
            )

        val router =
            ApiRouter(
                backends = listOf(officialApi, internalApi),
                tokenManager = tokenManager,
                timeSource = timeSource,
                defaultCooldown = 60.seconds,
            )

        val res1 = router.getTrack(SpotifyId("t1"))
        assertEquals("Internal Song", res1?.title)
        assertTrue(router.isBackendCoolingDown("official"))

        timeSource += 60.seconds + 1.seconds
        assertFalse(router.isBackendCoolingDown("official"))

        officialShouldFail = false
        val res2 = router.getTrack(SpotifyId("t1"))
        assertEquals("Official Song", res2?.title)
        assertEquals(2, officialApi.calls.get())
        assertFalse(router.isBackendCoolingDown("official"))
    }

    @Test
    fun consecutiveFailures_triggersCooldown() {
        val internalTrack =
            Track(
                id = SpotifyId("t1"),
                title = "Internal Song",
                artists = listOf(Artist(SpotifyId("a1"), "Artist")),
                durationMs = 180000L,
            )

        val officialApi =
            FakeApi(
                name = "official",
                supportedOps = setOf(Operation.GetTrack::class.java),
                trackHandler = { throw SpotifyException("network timeout") },
            )
        val internalApi =
            FakeApi(
                name = "internal",
                supportedOps = setOf(Operation.GetTrack::class.java),
                trackHandler = { internalTrack },
            )

        val router =
            ApiRouter(
                backends = listOf(officialApi, internalApi),
                tokenManager = tokenManager,
                timeSource = timeSource,
                failureThreshold = 2,
                defaultCooldown = 60.seconds,
            )

        router.getTrack(SpotifyId("t1"))
        assertEquals(1, officialApi.calls.get())
        assertFalse(router.isBackendCoolingDown("official"))

        router.getTrack(SpotifyId("t1"))
        assertEquals(2, officialApi.calls.get())
        assertTrue(router.isBackendCoolingDown("official"))

        router.getTrack(SpotifyId("t1"))
        assertEquals(2, officialApi.calls.get())
        assertEquals(3, internalApi.calls.get())
    }

    @Test
    fun isrcLookup_sharesSingleCallForIdenticalConcurrentRequests() {
        val readyLatch = CountDownLatch(4)
        val startedLatch = CountDownLatch(1)
        val releaseLatch = CountDownLatch(1)
        val callCount = AtomicInteger(0)

        val officialApi =
            FakeApi(
                name = "official",
                supportedOps = setOf(Operation.GetIsrc::class.java),
                isrcHandler = {
                    callCount.incrementAndGet()
                    startedLatch.countDown()
                    releaseLatch.await(5, TimeUnit.SECONDS)
                    Isrc("USUM71600787")
                },
            )

        val router =
            ApiRouter(
                backends = listOf(officialApi),
                tokenManager = tokenManager,
                timeSource = timeSource,
            )

        val executor = Executors.newFixedThreadPool(4)
        try {
            val tasks =
                List(4) {
                    Callable {
                        readyLatch.countDown()
                        readyLatch.await(5, TimeUnit.SECONDS)
                        router.getIsrc(SpotifyId("trackshared1234567890"))
                    }
                }

            val futures = tasks.map { executor.submit(it) }
            startedLatch.await(5, TimeUnit.SECONDS)
            Thread.sleep(150)
            releaseLatch.countDown()

            val results = futures.map { it.get(5, TimeUnit.SECONDS) }

            assertEquals(1, callCount.get())
            for (res in results) {
                assertNotNull(res)
                assertEquals("USUM71600787", res?.value)
            }
        } finally {
            executor.shutdownNow()
        }
    }
}
