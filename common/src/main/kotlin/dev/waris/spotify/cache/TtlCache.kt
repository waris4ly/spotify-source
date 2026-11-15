package dev.waris.spotify.cache

import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource

internal class TtlCache<K : Any, V : Any>(
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val maxSize: Int = 50_000,
) {
    private val map = ConcurrentHashMap<K, Entry<V>>()

    private data class Entry<V : Any>(
        val value: V,
        val expiresAt: TimeMark,
    ) {
        fun isExpired(): Boolean = expiresAt.hasPassedNow()
    }

    val size: Int get() = map.size

    fun get(key: K): V? {
        val entry = map[key] ?: return null
        if (entry.isExpired()) {
            map.remove(key, entry)
            return null
        }
        return entry.value
    }

    fun put(
        key: K,
        value: V,
        ttl: Duration,
    ) {
        if (map.size >= maxSize) {
            evictOldest()
        }
        val expiresAt = timeSource.markNow() + ttl
        map[key] = Entry(value, expiresAt)
    }

    private fun evictOldest() {
        val toRemove = maxSize / 10
        map.keys.take(toRemove).forEach { map.remove(it) }
    }

    fun remove(key: K): V? = map.remove(key)?.value

    fun clear() {
        map.clear()
    }

    fun pruneExpired() {
        val iterator = map.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.isExpired()) {
                iterator.remove()
            }
        }
    }
}
