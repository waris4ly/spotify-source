package dev.waris.spotify.api.lyrics

import dev.waris.spotify.api.ApiRouter
import dev.waris.spotify.model.SpotifyId
import dev.waris.spotify.model.Track
import kotlin.math.abs

internal class TrackResolver(
    private val apiRouter: ApiRouter,
    private val durationToleranceMs: Long = 3000,
) {
    fun resolve(query: LyricsQuery): SpotifyId? {
        query.isrc?.let { isrc ->
            val hit =
                apiRouter
                    .search("isrc:${isrc.normalized}", limit = 5)
                    .firstOrNull { closeInLength(it, query) }
            if (hit != null) {
                return hit.id
            }
        }

        val candidates = apiRouter.search("${query.title} ${query.artist}", limit = 10)
        return candidates
            .filter { closeInLength(it, query) }
            .map { it to score(it, query) }
            .filter { (_, score) -> score >= MIN_SCORE }
            .maxByOrNull { (_, score) -> score }
            ?.first
            ?.id
    }

    private fun closeInLength(
        track: Track,
        query: LyricsQuery,
    ): Boolean =
        query.durationMs <= 0 || abs(track.durationMs - query.durationMs) <= durationToleranceMs

    private fun score(
        track: Track,
        query: LyricsQuery,
    ): Int {
        val wantedTitle = normalize(query.title)
        val gotTitle = normalize(track.title)
        val titleScore =
            when {
                wantedTitle == gotTitle -> 3
                gotTitle.contains(wantedTitle) || wantedTitle.contains(gotTitle) -> 1
                else -> 0
            }
        val wantedArtist = normalize(query.artist)
        val artistScore =
            if (track.artists.any { normalize(it.name) in wantedArtist }) 2 else 0
        return if (titleScore == 0) 0 else titleScore + artistScore
    }

    private fun normalize(value: String): String =
        value
            .lowercase()
            .replace(BRACKETS, " ")
            .replace(REMASTER_SUFFIX, " ")
            .replace(NON_ALNUM, " ")
            .trim()
            .replace(SPACES, " ")

    private companion object {
        const val MIN_SCORE = 3
        val BRACKETS = Regex("""\(.*?\)|\[.*?]""")
        val REMASTER_SUFFIX = Regex("""\s-\s.*(remaster|version|edit|live).*""")
        val NON_ALNUM = Regex("""[^\p{L}\p{N}]+""")
        val SPACES = Regex("""\s+""")
    }
}
