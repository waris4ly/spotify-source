package dev.waris.spotify.api.lyrics

import dev.waris.spotify.cache.InflightRequests
import dev.waris.spotify.cache.TtlCache
import dev.waris.spotify.error.SpotifyException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import org.slf4j.LoggerFactory

internal sealed interface LyricsResult {
    data class Found(
        val lyrics: Lyrics,
    ) : LyricsResult

    data object Missing : LyricsResult

    data object Failed : LyricsResult
}

internal class LyricsService(
    private val providers: List<LyricsProvider>,
    private val foundTtl: Duration,
    private val missingTtl: Duration,
    private val failedTtl: Duration = 1.minutes,
    private val cache: TtlCache<String, LyricsResult> =
        TtlCache(maxSize = DEFAULT_CACHE_MAX_SIZE),
    private val inflight: InflightRequests<String, LyricsResult> = InflightRequests(),
) {
    private val log = LoggerFactory.getLogger(LyricsService::class.java)

    fun load(query: LyricsQuery): Lyrics? {
        val key = query.cacheKey
        val result =
            cache.get(key)
                ?: inflight.execute(key) {
                    val fresh = lookup(query)
                    val ttl =
                        when (fresh) {
                            is LyricsResult.Found -> foundTtl
                            LyricsResult.Missing -> missingTtl
                            LyricsResult.Failed -> failedTtl
                        }
                    cache.put(key, fresh, ttl)
                    fresh
                }
        return (result as? LyricsResult.Found)?.lyrics
    }

    private fun lookup(query: LyricsQuery): LyricsResult {
        var unsynced: Lyrics? = null
        var anyFailure = false

        for (provider in providers) {
            if (!provider.isAvailable()) {
                continue
            }
            val lyrics =
                try {
                    provider.fetch(query)
                } catch (e: SpotifyException.RateLimit) {
                    anyFailure = true
                    log.warn("lyrics provider {} rate limited", provider.name)
                    null
                } catch (e: SpotifyException) {
                    anyFailure = true
                    log.warn("lyrics provider {} failed: {}", provider.name, e.message)
                    null
                } catch (e: Exception) {
                    anyFailure = true
                    log.warn("lyrics provider {} failed: {}", provider.name, e.message)
                    null
                }

            if (lyrics == null || lyrics.lines.isEmpty()) {
                continue
            }
            if (lyrics.sync == LyricsSync.LINE) {
                return LyricsResult.Found(lyrics)
            }
            if (unsynced == null) {
                unsynced = lyrics
            }
        }

        return when {
            unsynced != null -> LyricsResult.Found(unsynced)
            anyFailure -> LyricsResult.Failed
            else -> LyricsResult.Missing
        }
    }

    companion object {
        const val DEFAULT_CACHE_MAX_SIZE: Int = 20_000
    }
}
