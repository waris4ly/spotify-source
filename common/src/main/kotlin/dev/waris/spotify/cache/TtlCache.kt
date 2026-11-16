package dev.waris.spotify.cache

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource

internal class TtlCache<K : Any, V : Any>(
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val maxSize: Int = DEFAULT_MAX_SIZE,
    private val pruneInterval: Int = DEFAULT_PRUNE_INTERVAL,
) {
    private val map = ConcurrentHashMap<K, Entry<V>>()
    private val putCount = AtomicInteger(0)
    private val isPruning = AtomicBoolean(false)

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
        if (pruneInterval > 0 && putCount.incrementAndGet() % pruneInterval == 0) {
            tryPruneExpired()
        }
        if (map.size >= maxSize && !map.containsKey(key)) {
            evictEarliestExpiring()
        }
        val expiresAt = timeSource.markNow() + ttl
        map[key] = Entry(value, expiresAt)
    }

    private fun tryPruneExpired() {
        if (isPruning.compareAndSet(false, true)) {
            try {
                pruneExpired()
            } finally {
                isPruning.set(false)
            }
        }
    }

    private fun evictEarliestExpiring() {
        pruneExpired()
        if (map.size < maxSize) {
            return
        }
        val toRemove = (maxSize / 10).coerceAtLeast(1)
        val sortedKeys =
            map.entries
                .sortedByDescending { it.value.expiresAt.elapsedNow() }
                .take(toRemove)
                .map { it.key }
        sortedKeys.forEach { map.remove(it) }
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

    internal companion object {
        const val DEFAULT_MAX_SIZE: Int = 10_000
        const val DEFAULT_PRUNE_INTERVAL: Int = 50
    }
}
