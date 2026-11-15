package dev.waris.spotify.cache

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException

internal class InflightRequests<K : Any, V> {
    private val map = ConcurrentHashMap<K, CompletableFuture<V>>()

    val activeCount: Int get() = map.size

    fun execute(
        key: K,
        block: () -> V,
    ): V {
        var created = false
        // first caller creates the future while concurrent callers wait on it
        val future =
            map.computeIfAbsent(key) {
                created = true
                CompletableFuture()
            }

        if (created) {
            try {
                val result = block()
                future.complete(result)
                return result
            } catch (t: Throwable) {
                future.completeExceptionally(t)
                throw t
            } finally {
                // remove finished task so future requests run fresh
                map.remove(key, future)
            }
        }

        // wait for the inflight call to finish and reuse its value
        return try {
            future.join()
        } catch (e: CompletionException) {
            throw e.cause ?: e
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }
    }
}
