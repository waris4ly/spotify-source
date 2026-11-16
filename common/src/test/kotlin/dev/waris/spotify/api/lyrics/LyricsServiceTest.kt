package dev.waris.spotify.api.lyrics

import dev.waris.spotify.cache.InflightRequests
import dev.waris.spotify.cache.TtlCache
import dev.waris.spotify.error.SpotifyException
import dev.waris.spotify.model.SpotifyId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LyricsServiceTest {
    private lateinit var timeSource: TestTimeSource
    private lateinit var cache: TtlCache<String, LyricsResult>

    @BeforeEach
    fun setUp() {
        timeSource = TestTimeSource()
        cache = TtlCache(timeSource = timeSource)
    }

    private fun dummyLyrics(
        name: String,
        sync: LyricsSync,
        text: String = "Test Line",
    ) = Lyrics(
        provider = name,
        providerLabel = name.uppercase(),
        sync = sync,
        language = "en",
        rightToLeft = false,
        lines = listOf(LyricsLine(0, 1000, text)),
    )

    private class TestProvider(
        override val name: String,
        var available: Boolean = true,
        var fetchHandler: (LyricsQuery) -> Lyrics? = { null },
    ) : LyricsProvider {
        val calls = AtomicInteger(0)

        override fun isAvailable(): Boolean = available

        override fun fetch(query: LyricsQuery): Lyrics? {
            calls.incrementAndGet()
            return fetchHandler(query)
        }
    }

    @Test
    fun load_firstProviderSynced_returnsImmediately() {
        val p1 =
            TestProvider("spotify") {
                dummyLyrics("spotify", LyricsSync.LINE, "Spotify synced")
            }
        val p2 =
            TestProvider("lrclib") {
                dummyLyrics("lrclib", LyricsSync.LINE, "LRCLIB synced")
            }

        val service =
            LyricsService(
                providers = listOf(p1, p2),
                foundTtl = 720.minutes,
                missingTtl = 30.minutes,
                cache = cache,
            )

        val query =
            LyricsQuery(
                title = "Title",
                artist = "Artist",
                durationMs = 180_000,
                spotifyId = SpotifyId("id123456789012345678"),
            )

        val result = service.load(query)
        assertNotNull(result)
        assertEquals("spotify", result!!.provider)
        assertEquals(1, p1.calls.get())
        assertEquals(0, p2.calls.get())
    }

    @Test
    fun load_syncedBeatsUnsynced_fromLaterProvider() {
        val p1 =
            TestProvider("spotify") {
                dummyLyrics("spotify", LyricsSync.NONE, "Spotify plain")
            }
        val p2 =
            TestProvider("lrclib") {
                dummyLyrics("lrclib", LyricsSync.LINE, "LRCLIB synced")
            }

        val service =
            LyricsService(
                providers = listOf(p1, p2),
                foundTtl = 720.minutes,
                missingTtl = 30.minutes,
                cache = cache,
            )

        val query =
            LyricsQuery(
                title = "Title",
                artist = "Artist",
                durationMs = 180_000,
                spotifyId = SpotifyId("id123456789012345678"),
            )

        val result = service.load(query)
        assertNotNull(result)
        assertEquals("lrclib", result!!.provider)
        assertEquals(LyricsSync.LINE, result.sync)
        assertEquals(1, p1.calls.get())
        assertEquals(1, p2.calls.get())
    }

    @Test
    fun load_allUnsynced_returnsFirstUnsynced() {
        val p1 =
            TestProvider("spotify") {
                dummyLyrics("spotify", LyricsSync.NONE, "Spotify plain")
            }
        val p2 =
            TestProvider("lrclib") {
                dummyLyrics("lrclib", LyricsSync.NONE, "LRCLIB plain")
            }

        val service =
            LyricsService(
                providers = listOf(p1, p2),
                foundTtl = 720.minutes,
                missingTtl = 30.minutes,
                cache = cache,
            )

        val query =
            LyricsQuery(
                title = "Title",
                artist = "Artist",
                durationMs = 180_000,
                spotifyId = SpotifyId("id123456789012345678"),
            )

        val result = service.load(query)
        assertNotNull(result)
        assertEquals("spotify", result!!.provider)
        assertEquals(LyricsSync.NONE, result.sync)
    }

    @Test
    fun load_missingCachedShort_expiresAfterMissingTtl() {
        val p1 = TestProvider("spotify") { null }

        val service =
            LyricsService(
                providers = listOf(p1),
                foundTtl = 720.minutes,
                missingTtl = 30.minutes,
                cache = cache,
            )

        val query =
            LyricsQuery(
                title = "Title",
                artist = "Artist",
                durationMs = 180_000,
                spotifyId = SpotifyId("id123456789012345678"),
            )

        assertNull(service.load(query))
        assertEquals(1, p1.calls.get())

        // cached missing - does not call provider again
        assertNull(service.load(query))
        assertEquals(1, p1.calls.get())

        // advance time past missing TTL
        timeSource += 30.minutes + 1.seconds
        assertNull(service.load(query))
        assertEquals(2, p1.calls.get())
    }

    @Test
    fun load_failedCachedOneMinute_expiresAfterFailedTtl() {
        val p1 =
            TestProvider("spotify") {
                throw SpotifyException("network failure")
            }

        val service =
            LyricsService(
                providers = listOf(p1),
                foundTtl = 720.minutes,
                missingTtl = 30.minutes,
                failedTtl = 1.minutes,
                cache = cache,
            )

        val query =
            LyricsQuery(
                title = "Title",
                artist = "Artist",
                durationMs = 180_000,
                spotifyId = SpotifyId("id123456789012345678"),
            )

        assertNull(service.load(query))
        assertEquals(1, p1.calls.get())

        // cached failure
        assertNull(service.load(query))
        assertEquals(1, p1.calls.get())

        // advance time past failed TTL (1 minute)
        timeSource += 1.minutes + 1.seconds
        assertNull(service.load(query))
        assertEquals(2, p1.calls.get())
    }

    @Test
    fun load_unavailableProvider_skipped() {
        val p1 =
            TestProvider("spotify", available = false) {
                dummyLyrics("spotify", LyricsSync.LINE)
            }
        val p2 =
            TestProvider("lrclib", available = true) {
                dummyLyrics("lrclib", LyricsSync.LINE)
            }

        val service =
            LyricsService(
                providers = listOf(p1, p2),
                foundTtl = 720.minutes,
                missingTtl = 30.minutes,
                cache = cache,
            )

        val query =
            LyricsQuery(
                title = "Title",
                artist = "Artist",
                durationMs = 180_000,
                spotifyId = SpotifyId("id123456789012345678"),
            )

        val result = service.load(query)
        assertNotNull(result)
        assertEquals("lrclib", result!!.provider)
        assertEquals(0, p1.calls.get())
        assertEquals(1, p2.calls.get())
    }

    @Test
    fun load_rateLimitFallsThrough_toNextProvider() {
        val p1 =
            TestProvider("spotify") {
                throw SpotifyException.RateLimit("rate limit")
            }
        val p2 =
            TestProvider("lrclib") {
                dummyLyrics("lrclib", LyricsSync.LINE)
            }

        val service =
            LyricsService(
                providers = listOf(p1, p2),
                foundTtl = 720.minutes,
                missingTtl = 30.minutes,
                cache = cache,
            )

        val query =
            LyricsQuery(
                title = "Title",
                artist = "Artist",
                durationMs = 180_000,
                spotifyId = SpotifyId("id123456789012345678"),
            )

        val result = service.load(query)
        assertNotNull(result)
        assertEquals("lrclib", result!!.provider)
        assertEquals(1, p1.calls.get())
        assertEquals(1, p2.calls.get())
    }

    @Test
    fun load_inflightDeduplication_executesFetchOnce() {
        val enterLatch = CountDownLatch(1)
        val releaseLatch = CountDownLatch(1)
        val p1 =
            TestProvider("spotify") {
                enterLatch.countDown()
                releaseLatch.await(5, TimeUnit.SECONDS)
                dummyLyrics("spotify", LyricsSync.LINE)
            }

        val inflight = InflightRequests<String, LyricsResult>()
        val service =
            LyricsService(
                providers = listOf(p1),
                foundTtl = 720.minutes,
                missingTtl = 30.minutes,
                cache = cache,
                inflight = inflight,
            )

        val query =
            LyricsQuery(
                title = "Title",
                artist = "Artist",
                durationMs = 180_000,
                spotifyId = SpotifyId("id123456789012345678"),
            )

        val executor = Executors.newFixedThreadPool(2)
        try {
            val future1 = executor.submit<Lyrics?> { service.load(query) }
            assertTrue(enterLatch.await(5, TimeUnit.SECONDS))

            val future2 = executor.submit<Lyrics?> { service.load(query) }
            releaseLatch.countDown()

            val result1 = future1.get(5, TimeUnit.SECONDS)
            val result2 = future2.get(5, TimeUnit.SECONDS)

            assertNotNull(result1)
            assertNotNull(result2)
            assertEquals("spotify", result1!!.provider)
            assertEquals("spotify", result2!!.provider)
            assertEquals(1, p1.calls.get())
        } finally {
            executor.shutdownNow()
        }
    }
}
