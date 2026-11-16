package dev.waris.spotify.plugin

import dev.waris.spotify.SpotifyOptions
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("plugins.spotify")
public data class PluginSettings(
    var enabled: Boolean = true,
    var clientId: String = "",
    var clientSecret: String = "",
    var accountCookie: String = "",
    var anonymousTokenUrl: String = "",
    var countryCode: String = "US",
    var internalBackend: Boolean = true,
    var searchLimit: Int = 10,
    var playlistLoadLimit: Int = 1000,
    var albumLoadLimit: Int = 500,
    var showLoadLimit: Int = 200,
    var allowLocalFiles: Boolean = false,
    var extractColors: Boolean = false,
    var canvas: Boolean = false,
    var recommendationLimit: Int = 10,
    var requestTimeoutSeconds: Int = 10,
    var mirrorProviders: List<String> =
        listOf(
            """ytsearch:"%isrc%"""",
            "ytsearch:%query%",
        ),
    var mirrorDurationToleranceMs: Long = 5000,
    var metadataCacheMinutes: Int = 30,
    var mirrorCacheMinutes: Int = 360,
    var queryHashes: Map<String, String> = emptyMap(),
    var lyrics: Boolean = true,
    var lyricsProviders: List<String> = listOf("spotify", "lrclib"),
    var lyricsCacheMinutes: Int = 720,
    var lyricsMissCacheMinutes: Int = 30,
) {
    internal fun toOptions(): SpotifyOptions =
        SpotifyOptions(
            clientId = clientId,
            clientSecret = clientSecret,
            accountCookie = accountCookie,
            anonymousTokenUrl = anonymousTokenUrl,
            countryCode = countryCode,
            internalBackend = internalBackend,
            searchLimit = searchLimit,
            playlistLoadLimit = playlistLoadLimit,
            albumLoadLimit = albumLoadLimit,
            showLoadLimit = showLoadLimit,
            allowLocalFiles = allowLocalFiles,
            extractColors = extractColors,
            canvas = canvas,
            recommendationLimit = recommendationLimit,
            requestTimeoutSeconds = requestTimeoutSeconds,
            mirrorProviders = mirrorProviders,
            mirrorDurationToleranceMs = mirrorDurationToleranceMs,
            metadataCacheMinutes = metadataCacheMinutes,
            mirrorCacheMinutes = mirrorCacheMinutes,
            queryHashes = queryHashes,
            lyrics = lyrics,
            lyricsProviders = lyricsProviders,
            lyricsCacheMinutes = lyricsCacheMinutes,
            lyricsMissCacheMinutes = lyricsMissCacheMinutes,
        )

    override fun toString(): String {
        val secretMask = if (clientSecret.isBlank()) "<unset>" else "***"
        val cookieMask = if (accountCookie.isBlank()) "<unset>" else "***"
        val anonUrlMask = if (anonymousTokenUrl.isBlank()) "<unset>" else "***"
        return "PluginSettings(enabled=$enabled, clientId=$clientId, clientSecret=$secretMask, " +
            "accountCookie=$cookieMask, anonymousTokenUrl=$anonUrlMask, " +
            "countryCode=$countryCode, internalBackend=$internalBackend, " +
            "searchLimit=$searchLimit, playlistLoadLimit=$playlistLoadLimit, " +
            "albumLoadLimit=$albumLoadLimit, showLoadLimit=$showLoadLimit, " +
            "allowLocalFiles=$allowLocalFiles, extractColors=$extractColors, " +
            "canvas=$canvas, recommendationLimit=$recommendationLimit, " +
            "requestTimeoutSeconds=$requestTimeoutSeconds, mirrorProviders=$mirrorProviders, " +
            "mirrorDurationToleranceMs=$mirrorDurationToleranceMs, " +
            "metadataCacheMinutes=$metadataCacheMinutes, " +
            "mirrorCacheMinutes=$mirrorCacheMinutes, queryHashes=$queryHashes, " +
            "lyrics=$lyrics, lyricsProviders=$lyricsProviders, " +
            "lyricsCacheMinutes=$lyricsCacheMinutes, " +
            "lyricsMissCacheMinutes=$lyricsMissCacheMinutes)"
    }
}
