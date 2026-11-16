package dev.waris.spotify

import com.sedmelluq.discord.lavaplayer.player.AudioConfiguration
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerOptions
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo
import com.sedmelluq.discord.lavaplayer.track.BaseAudioTrack
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor
import dev.waris.spotify.api.ApiRouter
import dev.waris.spotify.auth.TokenManager
import dev.waris.spotify.convert.AudioItemFactory
import dev.waris.spotify.mirror.TrackMirror
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpotifyAudioTrackTest {
    private class FakeAudioTrack(
        info: AudioTrackInfo,
        private val onProcess: () -> Unit,
    ) : BaseAudioTrack(info) {
        override fun process(executor: LocalAudioTrackExecutor) {
            onProcess()
        }

        override fun makeClone(): AudioTrack = this

        override fun getSourceManager(): AudioSourceManager? = null
    }

    private class FakeTrackMirror(
        options: SpotifyOptions,
        apiRouter: ApiRouter,
        private val onResolve: (useCache: Boolean) -> ResolveResult,
    ) : TrackMirror(options, apiRouter) {
        var invalidated = false
        var retryResolveCalled = false

        override fun resolve(
            trackInfo: AudioTrackInfo,
            playerManager: AudioPlayerManager,
            useCache: Boolean,
        ): ResolveResult {
            if (!useCache) {
                retryResolveCalled = true
            }
            return onResolve(useCache)
        }

        override fun invalidate(identifier: String) {
            invalidated = true
        }
    }

    private fun createSourceManager(
        mirror: TrackMirror,
        playerManager: AudioPlayerManager,
    ): SpotifyAudioSourceManager {
        val options = SpotifyOptions()
        val httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager()
        val tokenManager = TokenManager(emptyMap())
        val apiRouter = ApiRouter(emptyList(), tokenManager)
        val sm =
            SpotifyAudioSourceManager(
                options = options,
                httpInterfaceManager = httpInterfaceManager,
                tokenManager = tokenManager,
                apiRouter = apiRouter,
                audioItemFactory = AudioItemFactory(),
                trackMirror = mirror,
            )
        playerManager.registerSourceManager(sm)
        sm.attach(playerManager)
        return sm
    }

    @Test
    fun process_interruptedException_doesNotInvalidateOrRetryCache() {
        val trackInfo =
            AudioTrackInfo(
                "Title",
                "Artist",
                180000L,
                "id1",
                false,
                "https://open.spotify.com/track/id1",
            )
        val fakeTrack =
            FakeAudioTrack(trackInfo) {
                throw InterruptedException("interrupted")
            }

        val options = SpotifyOptions()
        val tokenManager = TokenManager(emptyMap())
        val apiRouter = ApiRouter(emptyList(), tokenManager)

        val fakeMirror =
            FakeTrackMirror(options, apiRouter) {
                TrackMirror.ResolveResult(fakeTrack, wasCached = true)
            }

        val playerManager = DefaultAudioPlayerManager()
        val sourceManager = createSourceManager(fakeMirror, playerManager)
        val track = SpotifyAudioTrack(trackInfo, sourceManager)

        val executor =
            LocalAudioTrackExecutor(fakeTrack, AudioConfiguration(), AudioPlayerOptions(), false, 0)

        assertThrows(InterruptedException::class.java) {
            track.process(executor)
        }

        assertFalse(fakeMirror.invalidated)
        assertFalse(fakeMirror.retryResolveCalled)
    }

    @Test
    fun process_friendlyExceptionOnCachedResult_retriesWithUseCacheFalse() {
        val trackInfo =
            AudioTrackInfo(
                "Title",
                "Artist",
                180000L,
                "id1",
                false,
                "https://open.spotify.com/track/id1",
            )

        var executionAttempts = 0
        val failingTrack =
            FakeAudioTrack(trackInfo) {
                executionAttempts++
                throw FriendlyException("failed", FriendlyException.Severity.FAULT, null)
            }
        val succeedingTrack =
            FakeAudioTrack(trackInfo) {
                executionAttempts++
            }

        val options = SpotifyOptions()
        val tokenManager = TokenManager(emptyMap())
        val apiRouter = ApiRouter(emptyList(), tokenManager)

        val fakeMirror =
            FakeTrackMirror(options, apiRouter) { useCache ->
                if (useCache) {
                    TrackMirror.ResolveResult(failingTrack, wasCached = true)
                } else {
                    TrackMirror.ResolveResult(succeedingTrack, wasCached = false)
                }
            }

        val playerManager = DefaultAudioPlayerManager()
        val sourceManager = createSourceManager(fakeMirror, playerManager)
        val track = SpotifyAudioTrack(trackInfo, sourceManager)

        val executor =
            LocalAudioTrackExecutor(
                failingTrack,
                AudioConfiguration(),
                AudioPlayerOptions(),
                false,
                0,
            )

        track.process(executor)

        assertTrue(fakeMirror.invalidated)
        assertTrue(fakeMirror.retryResolveCalled)
        assertEquals(2, executionAttempts)
    }
}
