package dev.waris.spotify.mirror

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioReference
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo
import dev.waris.spotify.SpotifyOptions
import dev.waris.spotify.api.ApiRouter
import dev.waris.spotify.api.internal.ClientMetadata
import dev.waris.spotify.cache.TtlCache
import dev.waris.spotify.model.SpotifyId
import kotlin.time.Duration.Companion.minutes
import org.slf4j.LoggerFactory

internal class TrackMirror(
    private val options: SpotifyOptions,
    private val apiRouter: ApiRouter,
    private val candidateScorer: CandidateScorer =
        CandidateScorer(options.mirrorDurationToleranceMs),
    private val clientMetadata: ClientMetadata? = null,
) {
    private val log = LoggerFactory.getLogger(TrackMirror::class.java)
    private val cache = TtlCache<String, AudioTrack>()

    init {
        for (provider in options.mirrorProviders) {
            validateProviderTemplate(provider)
        }
    }

    data class ResolveResult(
        val track: AudioTrack,
        val wasCached: Boolean,
    )

    fun close() {
        cache.clear()
    }

    fun resolve(
        trackInfo: AudioTrackInfo,
        playerManager: AudioPlayerManager,
        useCache: Boolean = true,
    ): ResolveResult {
        // return cached mirror track if previously resolved to save network calls
        if (useCache) {
            val cached = cache.get(trackInfo.identifier)
            if (cached != null) {
                log.debug("using cached mirror for {}", trackInfo.identifier)
                return ResolveResult(cached.makeClone(), wasCached = true)
            }
        }

        var isrc = trackInfo.isrc
        // defensively query client metadata or official api if track is missing isrc
        if (isrc.isNullOrBlank()) {
            isrc = fetchIsrcDefensively(trackInfo.identifier)
        }

        val primaryArtist =
            trackInfo.author
                .split(",")
                .firstOrNull()
                ?.trim() ?: ""
        val query =
            if (primaryArtist.isNotEmpty() &&
                !trackInfo.title.contains(primaryArtist, ignoreCase = true)
            ) {
                "${trackInfo.title} $primaryArtist"
            } else {
                trackInfo.title
            }

        // try mirror providers in order until a scored candidate matches
        for (providerTemplate in options.mirrorProviders) {
            val lookup = buildLookupString(providerTemplate, isrc, query) ?: continue
            val item =
                try {
                    playerManager.loadItemSync(AudioReference(lookup, null))
                } catch (e: Exception) {
                    log.debug("lookup failed for {}: {}", lookup, e.message)
                    null
                } ?: continue

            val candidates: List<AudioTrack> =
                when (item) {
                    is AudioTrack -> listOf(item)
                    is AudioPlaylist -> item.tracks
                    else -> emptyList()
                }

            val best = candidateScorer.findBest(trackInfo, candidates)
            if (best != null) {
                cache.put(
                    trackInfo.identifier,
                    best.makeClone(),
                    options.mirrorCacheMinutes.minutes,
                )
                return ResolveResult(best, wasCached = false)
            }
        }

        throw FriendlyException(
            "No playback source found for Spotify track: ${trackInfo.title} - ${trackInfo.author}",
            FriendlyException.Severity.COMMON,
            null,
        )
    }

    fun invalidate(identifier: String) {
        cache.remove(identifier)
    }

    private fun buildLookupString(
        template: String,
        isrc: String?,
        query: String,
    ): String? {
        if (template.contains("%isrc%")) {
            if (isrc.isNullOrBlank()) {
                return null
            }
            return template.replace("%isrc%", isrc)
        }
        if (template.contains("%query%")) {
            return template.replace("%query%", query)
        }
        return template
    }

    private fun fetchIsrcDefensively(identifier: String): String? {
        val isrc =
            try {
                apiRouter.getIsrc(SpotifyId(identifier))?.value
            } catch (e: Exception) {
                log.debug("failed to fetch official ISRC for {}: {}", identifier, e.message)
                null
            }
        if (!isrc.isNullOrBlank()) {
            return isrc
        }
        return try {
            clientMetadata?.fetchIsrc(SpotifyId(identifier))?.value
        } catch (e: Exception) {
            log.debug("failed to fetch client metadata ISRC for {}: {}", identifier, e.message)
            null
        }
    }

    private fun validateProviderTemplate(template: String) {
        val trimmed = template.trim()
        if (trimmed.startsWith("spsearch:") ||
            trimmed.startsWith("spotify:") ||
            trimmed.contains("spotify.com/")
        ) {
            throw IllegalArgumentException(
                "mirror provider template cannot use Spotify source or recursion: $template",
            )
        }
    }
}
