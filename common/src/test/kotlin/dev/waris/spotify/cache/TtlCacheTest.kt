package dev.waris.spotify.cache

import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TtlCacheTest {
    private lateinit var timeSource: TestTimeSource
    private lateinit var cache: TtlCache<String, String>

    @BeforeEach
    fun setUp() {
        timeSource = TestTimeSource()
        cache = TtlCache(timeSource)
    }

    @Test
    fun putAndGet_returnsValueBeforeExpiry() {
        cache.put("k1", "v1", 5.minutes)
        assertEquals("v1", cache.get("k1"))

        timeSource += 4.minutes
        assertEquals("v1", cache.get("k1"))
    }

    @Test
    fun get_returnsNullAfterExpiry() {
        cache.put("k1", "v1", 5.minutes)

        timeSource += 5.minutes + 1.seconds
        assertNull(cache.get("k1"))
        assertEquals(0, cache.size)
    }

    @Test
    fun remove_evictsEntryImmediately() {
        cache.put("k1", "v1", 10.minutes)
        assertEquals("v1", cache.remove("k1"))
        assertNull(cache.get("k1"))
        assertEquals(0, cache.size)
    }

    @Test
    fun clear_removesAllEntries() {
        cache.put("k1", "v1", 10.minutes)
        cache.put("k2", "v2", 10.minutes)
        assertEquals(2, cache.size)

        cache.clear()
        assertEquals(0, cache.size)
        assertNull(cache.get("k1"))
        assertNull(cache.get("k2"))
    }

    @Test
    fun pruneExpired_removesOnlyExpiredEntries() {
        cache.put("short", "v1", 2.minutes)
        cache.put("long", "v2", 10.minutes)

        timeSource += 3.minutes
        cache.pruneExpired()

        assertEquals(1, cache.size)
        assertNull(cache.get("short"))
        assertEquals("v2", cache.get("long"))
    }

    @Test
    fun overwriteKey_resetsTtl() {
        cache.put("k1", "initial", 5.minutes)
        timeSource += 4.minutes

        cache.put("k1", "updated", 5.minutes)
        timeSource += 3.minutes

        assertEquals("updated", cache.get("k1"))
    }
}
