package dev.waris.spotify

import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack
import com.sedmelluq.discord.lavaplayer.track.InternalAudioTrack
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor

public class SpotifyAudioTrack(
    trackInfo: AudioTrackInfo,
    private val sourceManager: SpotifyAudioSourceManager,
    public var canvasUrl: String? = null,
    public var colors: Map<String, String>? = null,
    public var previewUrl: String? = null,
) : DelegatedAudioTrack(trackInfo) {
    override fun process(executor: LocalAudioTrackExecutor) {
        val playerManager =
            sourceManager.playerManager
                ?: throw FriendlyException(
                    "AudioPlayerManager is not available for track playback",
                    FriendlyException.Severity.FAULT,
                    null,
                )

        val mirror = sourceManager.trackMirror
        val result = mirror.resolve(trackInfo, playerManager, useCache = true)
        val track =
            result.track as? InternalAudioTrack
                ?: throw FriendlyException(
                    "Mirror returned incompatible track type",
                    FriendlyException.Severity.FAULT,
                    null,
                )

        try {
            processDelegate(track, executor)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw e
        } catch (e: Exception) {
            if (e.cause is InterruptedException) {
                Thread.currentThread().interrupt()
                throw e
            }
            if (result.wasCached) {
                mirror.invalidate(trackInfo)
                val retryResult = mirror.resolve(trackInfo, playerManager, useCache = false)
                val retryTrack =
                    retryResult.track as? InternalAudioTrack
                        ?: throw FriendlyException(
                            "Mirror retry returned incompatible track type",
                            FriendlyException.Severity.FAULT,
                            null,
                        )
                try {
                    processDelegate(retryTrack, executor)
                } catch (retryInterrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw retryInterrupted
                }
            } else {
                throw e
            }
        }
    }

    override fun makeShallowClone(): SpotifyAudioTrack =
        SpotifyAudioTrack(trackInfo, sourceManager, canvasUrl, colors, previewUrl)

    override fun getSourceManager(): SpotifyAudioSourceManager = sourceManager
}
