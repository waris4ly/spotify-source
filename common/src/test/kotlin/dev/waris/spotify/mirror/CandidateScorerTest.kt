package dev.waris.spotify.mirror

import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo
import com.sedmelluq.discord.lavaplayer.track.BaseAudioTrack
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CandidateScorerTest {
    private val scorer = CandidateScorer(durationToleranceMs = 5000L, minScore = 100.0)

    private class FakeAudioTrack(
        info: AudioTrackInfo,
    ) : BaseAudioTrack(info) {
        override fun process(executor: LocalAudioTrackExecutor) {}

        override fun makeClone(): AudioTrack = FakeAudioTrack(trackInfo)

        override fun getSourceManager(): AudioSourceManager? = null
    }

    private fun mockCandidate(
        title: String,
        author: String,
        length: Long,
    ): AudioTrack {
        val info =
            AudioTrackInfo(
                title,
                author,
                length,
                "cand_id",
                false,
                "https://example.com/watch?v=cand_id",
                null,
                null,
            )
        return FakeAudioTrack(info)
    }

    @Test
    fun score_perfectMatch_returnsHighScore() {
        val target =
            AudioTrackInfo(
                "Starboy",
                "The Weeknd, Daft Punk",
                230453L,
                "t1",
                false,
                "https://open.spotify.com/track/t1",
                null,
                "USUM71607007",
            )
        val candidate =
            mockCandidate(
                title = "The Weeknd - Starboy (Official Music Video) ft. Daft Punk",
                author = "TheWeekndVEVO",
                length = 230000L,
            )

        val score = scorer.score(target, candidate)

        assertTrue(score >= 200.0)
    }

    @Test
    fun score_durationOutsideTolerance_penalizesHeavy() {
        val target =
            AudioTrackInfo(
                "Starboy",
                "The Weeknd",
                200000L,
                "t1",
                false,
                "https://open.spotify.com/track/t1",
                null,
                null,
            )
        val candidate =
            mockCandidate(
                title = "The Weeknd - Starboy",
                author = "The Weeknd",
                length = 230000L,
            )

        val score = scorer.score(target, candidate)

        assertTrue(score < 50.0)
    }

    @Test
    fun score_penalizesCoverUnlessTargetHasCover() {
        val targetStudio =
            AudioTrackInfo(
                "Creep",
                "Radiohead",
                238640L,
                "t1",
                false,
                "https://open.spotify.com/track/t1",
                null,
                null,
            )
        val coverCandidate =
            mockCandidate(
                title = "Radiohead - Creep (Cover by Someone)",
                author = "Someone",
                length = 238000L,
            )
        val studioCandidate =
            mockCandidate(
                title = "Radiohead - Creep (Official Audio)",
                author = "Radiohead",
                length = 238000L,
            )

        val coverScore = scorer.score(targetStudio, coverCandidate)
        val studioScore = scorer.score(targetStudio, studioCandidate)

        assertTrue(studioScore > coverScore + 80.0)

        val targetCover =
            AudioTrackInfo(
                "Creep (Cover)",
                "Someone",
                238000L,
                "t2",
                false,
                "https://open.spotify.com/track/t2",
                null,
                null,
            )
        val targetCoverScore = scorer.score(targetCover, coverCandidate)
        assertTrue(targetCoverScore >= 150.0)
    }

    @Test
    fun score_penalizesRemixUnlessTargetHasRemix() {
        val targetOriginal =
            AudioTrackInfo(
                "Levitating",
                "Dua Lipa",
                203000L,
                "t1",
                false,
                "https://open.spotify.com/track/t1",
                null,
                null,
            )
        val remixCandidate =
            mockCandidate(
                title = "Dua Lipa - Levitating (The Blessed Madonna Remix)",
                author = "Dua Lipa",
                length = 203000L,
            )

        val score = scorer.score(targetOriginal, remixCandidate)
        assertTrue(score < 150.0)

        val targetRemix =
            AudioTrackInfo(
                "Levitating (The Blessed Madonna Remix)",
                "Dua Lipa",
                203000L,
                "t1",
                false,
                "https://open.spotify.com/track/t1",
                null,
                null,
            )
        val scoreWhenTargetIsRemix = scorer.score(targetRemix, remixCandidate)
        assertTrue(scoreWhenTargetIsRemix >= 200.0)
    }

    @Test
    fun findBest_picksHighestScoringAboveThreshold() {
        val target =
            AudioTrackInfo(
                "ocean eyes",
                "Billie Eilish",
                200379L,
                "t1",
                false,
                "https://open.spotify.com/track/t1",
                null,
                null,
            )

        val poorCandidate =
            mockCandidate(
                title = "Random Song - ocean eyes mention",
                author = "Random",
                length = 150000L,
            )
        val coverCandidate =
            mockCandidate(
                title = "Billie Eilish - ocean eyes (Live Acoustic Cover)",
                author = "Acoustic Singer",
                length = 200000L,
            )
        val officialCandidate =
            mockCandidate(
                title = "Billie Eilish - ocean eyes (Official Music Video)",
                author = "Billie Eilish",
                length = 200000L,
            )

        val best = scorer.findBest(target, listOf(poorCandidate, coverCandidate, officialCandidate))

        assertNotNull(best)
        assertEquals(officialCandidate.info.title, best?.info?.title)
    }

    @Test
    fun findBest_returnsNullWhenAllBelowThreshold() {
        val target =
            AudioTrackInfo(
                "ocean eyes",
                "Billie Eilish",
                200379L,
                "t1",
                false,
                "https://open.spotify.com/track/t1",
                null,
                null,
            )

        val unrelatedCandidate =
            mockCandidate(
                title = "Completely Different Song",
                author = "Unknown",
                length = 300000L,
            )

        val best = scorer.findBest(target, listOf(unrelatedCandidate))

        assertNull(best)
    }
}
