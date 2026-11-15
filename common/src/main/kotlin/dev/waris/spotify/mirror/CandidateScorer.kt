package dev.waris.spotify.mirror

import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo
import kotlin.math.abs

internal class CandidateScorer(
    private val durationToleranceMs: Long = 5000L,
    private val minScore: Double = 100.0,
) {
    fun score(
        target: AudioTrackInfo,
        candidate: AudioTrack,
    ): Double {
        val targetTitle = target.title.lowercase()
        val targetAuthor = target.author.lowercase()
        val candidateTitle = candidate.info.title.lowercase()
        val candidateAuthor = candidate.info.author.lowercase()
        val candidateCombined = "$candidateTitle $candidateAuthor"

        // combine weighted duration title and artist similarity scores
        val durationScore = scoreDuration(target.length, candidate.info.length)
        val titleScore = scoreTitle(targetTitle, candidateTitle)
        val artistScore = scoreArtist(targetAuthor, candidateCombined)
        val penaltyScore = scorePenalties(targetTitle, candidateCombined)

        return durationScore + titleScore + artistScore - penaltyScore
    }

    // find candidate with highest matching score above minimum threshold
    fun findBest(
        target: AudioTrackInfo,
        candidates: List<AudioTrack>,
    ): AudioTrack? {
        var bestTrack: AudioTrack? = null
        var bestScore = minScore

        for (candidate in candidates) {
            val s = score(target, candidate)
            if (s >= bestScore) {
                bestScore = s
                bestTrack = candidate
            }
        }
        return bestTrack
    }

    private fun scoreDuration(
        targetLength: Long,
        candidateLength: Long,
    ): Double {
        if (targetLength <= 0 || candidateLength <= 0) {
            return 20.0
        }
        val diff = abs(targetLength - candidateLength)
        return if (diff <= durationToleranceMs) {
            100.0 * (1.0 - (diff.toDouble() / (durationToleranceMs * 2.0)))
        } else {
            val excessMs = diff - durationToleranceMs
            -50.0 - ((excessMs / 1000.0) * 10.0)
        }
    }

    private fun scoreTitle(
        targetTitle: String,
        candidateTitle: String,
    ): Double {
        val targetTokens = tokenize(targetTitle)
        if (targetTokens.isEmpty()) {
            return 0.0
        }
        val candidateTokens = tokenize(candidateTitle).toSet()

        val matches = targetTokens.count { candidateTokens.contains(it) }
        return (matches.toDouble() / targetTokens.size) * 80.0
    }

    private fun scoreArtist(
        targetAuthor: String,
        candidateCombined: String,
    ): Double {
        val artists = targetAuthor.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (artists.isEmpty()) {
            return 0.0
        }

        val primaryArtist = artists[0]
        val primaryTokens = tokenize(primaryArtist)
        if (primaryTokens.isNotEmpty() && primaryTokens.all { candidateCombined.contains(it) }) {
            return 50.0
        }

        val allTokens = tokenize(targetAuthor)
        if (allTokens.isEmpty()) {
            return 0.0
        }
        val matches = allTokens.count { candidateCombined.contains(it) }
        return (matches.toDouble() / allTokens.size) * 40.0
    }

    private fun scorePenalties(
        targetTitle: String,
        candidateCombined: String,
    ): Double {
        var penalty = 0.0
        for (word in PENALTY_WORDS) {
            if (candidateCombined.contains(word) && !targetTitle.contains(word)) {
                penalty += 100.0
            }
        }
        return penalty
    }

    private fun tokenize(text: String): List<String> =
        text
            .lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.isNotBlank() }

    companion object {
        val PENALTY_WORDS: List<String> =
            listOf(
                "live",
                "cover",
                "remix",
                "karaoke",
                "sped up",
                "slowed",
                "acoustic",
                "instrumental",
                "tribute",
            )
    }
}
