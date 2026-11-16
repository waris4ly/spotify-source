package dev.waris.spotify.api

import dev.waris.spotify.auth.TokenManager
import dev.waris.spotify.cache.InflightRequests
import dev.waris.spotify.error.SpotifyException
import dev.waris.spotify.model.Album
import dev.waris.spotify.model.Artist
import dev.waris.spotify.model.Episode
import dev.waris.spotify.model.Isrc
import dev.waris.spotify.model.Playlist
import dev.waris.spotify.model.Show
import dev.waris.spotify.model.SpotifyId
import dev.waris.spotify.model.Track
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import org.slf4j.LoggerFactory

internal class ApiRouter(
    private val backends: List<SpotifyApi>,
    private val tokenManager: TokenManager,
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val failureThreshold: Int = 2,
    private val defaultCooldown: Duration = 60.seconds,
) {
    private val log = LoggerFactory.getLogger(ApiRouter::class.java)
    private val backendCooldowns = ConcurrentHashMap<String, TimeMark>()
    private val consecutiveFailures = ConcurrentHashMap<String, Int>()
    private val isrcInflight = InflightRequests<String, Isrc?>()
    private val loggedQueryRejections = ConcurrentHashMap.newKeySet<String>()

    private fun handleQueryRejected(
        backendName: String,
        e: SpotifyException.QueryRejected,
    ) {
        if (loggedQueryRejections.add(e.operationName)) {
            log.warn("backend {} rejected query for operation {}", backendName, e.operationName)
        }
    }

    fun isBackendCoolingDown(name: String): Boolean {
        val cooldownMark = backendCooldowns[name] ?: return false
        if (cooldownMark.hasPassedNow()) {
            backendCooldowns.remove(name)
            consecutiveFailures.remove(name)
            return false
        }
        return true
    }

    fun coolDownBackend(
        name: String,
        duration: Duration = defaultCooldown,
    ) {
        log.warn("cooling down backend {} for {}", name, duration)
        backendCooldowns[name] = timeSource.markNow() + duration
    }

    private fun recordSuccess(name: String) {
        consecutiveFailures.remove(name)
    }

    // trip cooldown immediately on 429 rate limit or after reaching consecutive error threshold
    private fun recordFailure(
        name: String,
        isRateLimit: Boolean = false,
    ) {
        if (isRateLimit) {
            coolDownBackend(name)
            consecutiveFailures.remove(name)
            return
        }
        val count = consecutiveFailures.compute(name) { _, prev -> (prev ?: 0) + 1 } ?: 1
        if (count >= failureThreshold) {
            coolDownBackend(name)
            consecutiveFailures.remove(name)
        }
    }

    fun search(
        query: String,
        limit: Int = 10,
        offset: Int = 0,
    ): List<Track> {
        val operation = Operation.Search(query, limit, offset)
        for (backend in getRoutingOrder(operation)) {
            if (!canUseBackend(backend, operation)) {
                continue
            }
            try {
                val results = backend.search(query, limit, offset)
                recordSuccess(backend.name)
                return results
            } catch (e: SpotifyException.RateLimit) {
                recordFailure(backend.name, isRateLimit = true)
                log.warn("backend {} rate limited during search", backend.name)
            } catch (e: SpotifyException.QueryRejected) {
                handleQueryRejected(backend.name, e)
            } catch (e: SpotifyException) {
                recordFailure(backend.name, isRateLimit = false)
                log.warn("backend {} failed for search: {}", backend.name, e.message)
            }
        }
        return emptyList()
    }

    fun getTrack(id: SpotifyId): Track? {
        val operation = Operation.GetTrack(id)
        for (backend in getRoutingOrder(operation)) {
            if (!canUseBackend(backend, operation)) {
                continue
            }
            try {
                val track = backend.getTrack(id)
                if (track != null) {
                    recordSuccess(backend.name)
                    return track
                }
            } catch (e: SpotifyException.RateLimit) {
                recordFailure(backend.name, isRateLimit = true)
                log.warn("backend {} rate limited during track lookup", backend.name)
            } catch (e: SpotifyException.QueryRejected) {
                handleQueryRejected(backend.name, e)
            } catch (e: SpotifyException) {
                recordFailure(backend.name, isRateLimit = false)
                log.warn("backend {} failed for track {}: {}", backend.name, id.value, e.message)
            }
        }
        return null
    }

    fun getAlbum(
        id: SpotifyId,
        limit: Int = 500,
    ): Album? {
        val operation = Operation.GetAlbum(id, limit)
        for (backend in getRoutingOrder(operation)) {
            if (!canUseBackend(backend, operation)) {
                continue
            }
            try {
                val album = backend.getAlbum(id, limit)
                if (album != null) {
                    recordSuccess(backend.name)
                    return album
                }
            } catch (e: SpotifyException.RateLimit) {
                recordFailure(backend.name, isRateLimit = true)
                log.warn("backend {} rate limited during album lookup", backend.name)
            } catch (e: SpotifyException.QueryRejected) {
                handleQueryRejected(backend.name, e)
            } catch (e: SpotifyException) {
                recordFailure(backend.name, isRateLimit = false)
                log.warn("backend {} failed for album {}: {}", backend.name, id.value, e.message)
            }
        }
        return null
    }

    fun getPlaylist(
        id: SpotifyId,
        limit: Int = 1000,
    ): Playlist? {
        val operation = Operation.GetPlaylist(id, limit)
        for (backend in getRoutingOrder(operation)) {
            if (!canUseBackend(backend, operation)) {
                continue
            }
            try {
                val playlist = backend.getPlaylist(id, limit)
                if (playlist != null) {
                    recordSuccess(backend.name)
                    return playlist
                }
            } catch (e: SpotifyException.RateLimit) {
                recordFailure(backend.name, isRateLimit = true)
                log.warn("backend {} rate limited during playlist lookup", backend.name)
                throw e
            } catch (e: SpotifyException.QueryRejected) {
                handleQueryRejected(backend.name, e)
            } catch (e: SpotifyException) {
                recordFailure(backend.name, isRateLimit = false)
                log.warn("backend {} failed for playlist {}: {}", backend.name, id.value, e.message)
                throw e
            }
        }
        return null
    }

    fun getArtist(id: SpotifyId): Artist? {
        val operation = Operation.GetArtist(id)
        for (backend in getRoutingOrder(operation)) {
            if (!canUseBackend(backend, operation)) {
                continue
            }
            try {
                val artist = backend.getArtist(id) ?: continue
                if (artist.topTracks.isNotEmpty()) {
                    recordSuccess(backend.name)
                    return artist
                }
                if (backend.name == "official") {
                    val searchTracks = search(artist.name, limit = 10)
                    recordSuccess(backend.name)
                    return artist.copy(topTracks = searchTracks)
                }
            } catch (e: SpotifyException.RateLimit) {
                recordFailure(backend.name, isRateLimit = true)
                log.warn("backend {} rate limited during artist lookup", backend.name)
            } catch (e: SpotifyException.QueryRejected) {
                handleQueryRejected(backend.name, e)
            } catch (e: SpotifyException) {
                recordFailure(backend.name, isRateLimit = false)
                log.warn("backend {} failed for artist {}: {}", backend.name, id.value, e.message)
            }
        }
        return null
    }

    fun getIsrc(id: SpotifyId): Isrc? =
        isrcInflight.execute(id.value) {
            val operation = Operation.GetIsrc(id)
            for (backend in getRoutingOrder(operation)) {
                if (!canUseBackend(backend, operation)) {
                    continue
                }
                try {
                    val isrc = backend.getIsrc(id)
                    if (isrc != null) {
                        recordSuccess(backend.name)
                        return@execute isrc
                    }
                } catch (e: SpotifyException.RateLimit) {
                    recordFailure(backend.name, isRateLimit = true)
                    log.warn("backend {} rate limited during isrc lookup", backend.name)
                } catch (e: SpotifyException.QueryRejected) {
                    handleQueryRejected(backend.name, e)
                } catch (e: SpotifyException) {
                    recordFailure(backend.name, isRateLimit = false)
                    log.warn(
                        "backend {} failed for isrc {}: {}",
                        backend.name,
                        id.value,
                        e.message,
                    )
                }
            }
            null
        }

    fun getEpisode(
        id: SpotifyId,
        isChapter: Boolean = false,
    ): Episode? {
        val operation = Operation.GetEpisode(id, isChapter)
        for (backend in getRoutingOrder(operation)) {
            if (!canUseBackend(backend, operation)) {
                continue
            }
            try {
                val episode = backend.getEpisode(id, isChapter)
                if (episode != null) {
                    recordSuccess(backend.name)
                    return episode
                }
            } catch (e: SpotifyException.RateLimit) {
                recordFailure(backend.name, isRateLimit = true)
                log.warn("backend {} rate limited during episode lookup", backend.name)
            } catch (e: SpotifyException.QueryRejected) {
                handleQueryRejected(backend.name, e)
            } catch (e: SpotifyException) {
                recordFailure(backend.name, isRateLimit = false)
                log.warn(
                    "backend {} failed for episode {}: {}",
                    backend.name,
                    id.value,
                    e.message,
                )
            }
        }
        return null
    }

    fun getShow(
        id: SpotifyId,
        isAudiobook: Boolean = false,
        limit: Int = 200,
    ): Show? {
        val operation = Operation.GetShow(id, isAudiobook, limit)
        for (backend in getRoutingOrder(operation)) {
            if (!canUseBackend(backend, operation)) {
                continue
            }
            try {
                val show = backend.getShow(id, isAudiobook, limit)
                if (show != null) {
                    recordSuccess(backend.name)
                    return show
                }
            } catch (e: SpotifyException.RateLimit) {
                recordFailure(backend.name, isRateLimit = true)
                log.warn("backend {} rate limited during show lookup", backend.name)
            } catch (e: SpotifyException.QueryRejected) {
                handleQueryRejected(backend.name, e)
            } catch (e: SpotifyException) {
                recordFailure(backend.name, isRateLimit = false)
                log.warn(
                    "backend {} failed for show {}: {}",
                    backend.name,
                    id.value,
                    e.message,
                )
            }
        }
        return null
    }

    fun suggest(query: String): List<Track> {
        val operation = Operation.Suggest(query)
        for (backend in getRoutingOrder(operation)) {
            if (!canUseBackend(backend, operation)) {
                continue
            }
            try {
                val results = backend.suggest(query)
                recordSuccess(backend.name)
                return results
            } catch (e: SpotifyException.RateLimit) {
                recordFailure(backend.name, isRateLimit = true)
                log.warn("backend {} rate limited during suggest", backend.name)
            } catch (e: SpotifyException.QueryRejected) {
                handleQueryRejected(backend.name, e)
            } catch (e: SpotifyException) {
                recordFailure(backend.name, isRateLimit = false)
                log.warn("backend {} failed for suggest: {}", backend.name, e.message)
            }
        }
        return emptyList()
    }

    fun recommendations(
        seed: SpotifyId,
        limit: Int = 10,
    ): List<Track> {
        val operation = Operation.Recommendations(seed, limit)
        for (backend in getRoutingOrder(operation)) {
            if (!canUseBackend(backend, operation)) {
                continue
            }
            try {
                val results = backend.recommendations(seed, limit)
                recordSuccess(backend.name)
                return results
            } catch (e: SpotifyException.RateLimit) {
                recordFailure(backend.name, isRateLimit = true)
                log.warn("backend {} rate limited during recommendations", backend.name)
            } catch (e: SpotifyException.QueryRejected) {
                handleQueryRejected(backend.name, e)
            } catch (e: SpotifyException) {
                recordFailure(backend.name, isRateLimit = false)
                log.warn("backend {} failed for recommendations: {}", backend.name, e.message)
            }
        }
        return emptyList()
    }

    fun similar(
        seed: SpotifyId,
        limit: Int = 10,
    ): List<Track> {
        val operation = Operation.Similar(seed, limit)
        for (backend in getRoutingOrder(operation)) {
            if (!canUseBackend(backend, operation)) {
                continue
            }
            try {
                val results = backend.similar(seed, limit)
                recordSuccess(backend.name)
                return results
            } catch (e: SpotifyException.RateLimit) {
                recordFailure(backend.name, isRateLimit = true)
                log.warn("backend {} rate limited during similar", backend.name)
            } catch (e: SpotifyException.QueryRejected) {
                handleQueryRejected(backend.name, e)
            } catch (e: SpotifyException) {
                recordFailure(backend.name, isRateLimit = false)
                log.warn("backend {} failed for similar: {}", backend.name, e.message)
            }
        }
        return emptyList()
    }

    // prioritize official for tracks and albums while internal serves playlists and podcasts
    internal fun getRoutingOrder(operation: Operation): List<SpotifyApi> =
        when (operation) {
            is Operation.Search,
            is Operation.GetTrack,
            is Operation.GetAlbum,
            is Operation.GetIsrc,
            -> orderBackends("official", "internal")

            is Operation.GetPlaylist,
            is Operation.GetEpisode,
            is Operation.GetShow,
            is Operation.Suggest,
            is Operation.Recommendations,
            is Operation.Similar,
            -> orderBackends("internal")

            is Operation.GetArtist -> orderBackends("internal", "official")
        }

    private fun orderBackends(vararg preferredNames: String): List<SpotifyApi> {
        val map = backends.associateBy { it.name }
        return preferredNames.mapNotNull { map[it] }
    }

    // check backend operation support and bypass any backend currently cooling down
    private fun canUseBackend(
        backend: SpotifyApi,
        operation: Operation,
    ): Boolean {
        if (!backend.supports(operation)) {
            return false
        }
        if (isBackendCoolingDown(backend.name)) {
            log.debug("skipping backend {} because it is cooling down", backend.name)
            return false
        }
        val tokenKind = backend.tokenKindFor(operation)
        if (tokenKind != null && tokenManager.isCoolingDown(tokenKind)) {
            log.debug("skipping backend {} because {} is cooling down", backend.name, tokenKind)
            return false
        }
        return true
    }
}
