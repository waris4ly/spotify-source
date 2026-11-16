package dev.waris.spotify.plugin

import com.github.topi314.lavalyrics.lyrics.BasicAudioLyrics
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor
import dev.waris.spotify.SpotifyAudioSourceManager
import dev.waris.spotify.SpotifyOptions
import dev.waris.spotify.api.lyrics.Lyrics
import dev.waris.spotify.api.lyrics.LyricsLine
import dev.waris.spotify.api.lyrics.LyricsSync
import java.time.Duration
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpotifyLyricsManagerTest {
    private fun createDummyTrack(): AudioTrack {
        val info =
            AudioTrackInfo(
                "Song",
                "Artist",
                200_000,
                "4eoOQgFQ0rLMKNP8KcUNWc",
                false,
                "https://open.spotify.com/track/4eoOQgFQ0rLMKNP8KcUNWc",
            )
        return object : DelegatedAudioTrack(info) {
            override fun process(executor: LocalAudioTrackExecutor) = Unit
        }
    }

    private class StubSourceManager(
        var lyricsToReturn: Lyrics? = null,
    ) : SpotifyAudioSourceManager(SpotifyOptions()) {
        override fun loadLyrics(track: AudioTrack): Lyrics? = lyricsToReturn
    }

    @Test
    fun getSourceName_returnsSpotify() {
        val manager = SpotifyLyricsManager(StubSourceManager())
        assertEquals("spotify", manager.sourceName)
    }

    @Test
    fun loadLyrics_syncedLyrics_mapsToTimedLinesWithDuration() {
        val lyrics =
            Lyrics(
                provider = "spotify",
                providerLabel = "Musixmatch",
                sync = LyricsSync.LINE,
                language = "en",
                rightToLeft = false,
                lines =
                    listOf(
                        LyricsLine(12000L, 15500L, "First line"),
                        LyricsLine(16000L, null, "Second line"),
                    ),
            )

        val source = StubSourceManager(lyrics)
        val manager = SpotifyLyricsManager(source)
        val track = createDummyTrack()

        val audioLyrics = manager.loadLyrics(track)
        assertNotNull(audioLyrics)
        assertTrue(audioLyrics is SpotifyAudioLyrics)
        val spotifyLyrics = audioLyrics as SpotifyAudioLyrics

        assertEquals("spotify", spotifyLyrics.sourceName)
        assertEquals("Musixmatch", spotifyLyrics.provider)
        assertEquals("First line\nSecond line", spotifyLyrics.text)
        assertEquals(LyricsSync.LINE, spotifyLyrics.sync)
        assertEquals("en", spotifyLyrics.language)
        assertEquals(false, spotifyLyrics.rightToLeft)

        val lines = spotifyLyrics.lines
        assertNotNull(lines)
        val nonNullLines = lines!!
        assertEquals(2, nonNullLines.size)

        assertEquals(Duration.ofMillis(12000), nonNullLines[0].timestamp)
        assertEquals(Duration.ofMillis(3500), nonNullLines[0].duration)
        assertEquals("First line", nonNullLines[0].line)

        assertEquals(Duration.ofMillis(16000), nonNullLines[1].timestamp)
        assertNull(nonNullLines[1].duration)
        assertEquals("Second line", nonNullLines[1].line)
    }

    @Test
    fun loadLyrics_unsyncedLyrics_mapsToNullLines() {
        val lyrics =
            Lyrics(
                provider = "lrclib",
                providerLabel = null,
                sync = LyricsSync.NONE,
                language = null,
                rightToLeft = false,
                lines =
                    listOf(
                        LyricsLine(0L, null, "Line one"),
                        LyricsLine(0L, null, "Line two"),
                    ),
            )

        val source = StubSourceManager(lyrics)
        val manager = SpotifyLyricsManager(source)
        val track = createDummyTrack()

        val audioLyrics = manager.loadLyrics(track)
        assertNotNull(audioLyrics)
        assertTrue(audioLyrics is SpotifyAudioLyrics)
        val spotifyLyrics = audioLyrics as SpotifyAudioLyrics

        assertEquals("lrclib", spotifyLyrics.provider)
        assertEquals("Line one\nLine two", spotifyLyrics.text)
        assertEquals(LyricsSync.NONE, spotifyLyrics.sync)
        assertNull(spotifyLyrics.lines)
    }

    @Test
    fun loadLyrics_sourceReturnsNull_returnsNull() {
        val source = StubSourceManager(null)
        val manager = SpotifyLyricsManager(source)
        val track = createDummyTrack()

        assertNull(manager.loadLyrics(track))
    }

    @Test
    fun shutdown_doesNotThrow() {
        val manager = SpotifyLyricsManager(StubSourceManager())
        assertDoesNotThrow { manager.shutdown() }
    }

    @Test
    fun lyricsPluginInfo_modifiesSpotifyLyrics() {
        val modifier = LyricsPluginInfo()
        val spotifyLyrics =
            SpotifyAudioLyrics(
                provider = "Musixmatch",
                text = "Hello world",
                lines = emptyList(),
                sync = LyricsSync.LINE,
                language = "es",
                rightToLeft = false,
            )

        val json = modifier.modifyAudioLyricsPluginInfo(spotifyLyrics)
        assertNotNull(json)
        assertEquals("line", json!!["syncType"]?.jsonPrimitive?.content)
        assertEquals("es", json["language"]?.jsonPrimitive?.content)
        assertEquals(false, json["rightToLeft"]?.jsonPrimitive?.boolean)

        val genericLyrics =
            BasicAudioLyrics("other", "genius", "text", emptyList())
        assertNull(modifier.modifyAudioLyricsPluginInfo(genericLyrics))
    }
}
