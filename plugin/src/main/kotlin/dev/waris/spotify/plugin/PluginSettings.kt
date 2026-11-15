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
) {
    // validate configuration values early to fail fast on invalid yaml settings
    init {
        require(searchLimit > 0) { "searchLimit must be greater than 0" }
        require(playlistLoadLimit > 0) { "playlistLoadLimit must be greater than 0" }
        require(albumLoadLimit > 0) { "albumLoadLimit must be greater than 0" }
        require(showLoadLimit > 0) { "showLoadLimit must be greater than 0" }
        require(recommendationLimit > 0) { "recommendationLimit must be greater than 0" }
        require(requestTimeoutSeconds > 0) { "requestTimeoutSeconds must be greater than 0" }
        require(mirrorProviders.isNotEmpty()) { "mirrorProviders must not be empty" }
        require(mirrorDurationToleranceMs >= 0) {
            "mirrorDurationToleranceMs cannot be negative"
        }
        require(metadataCacheMinutes >= 0) { "metadataCacheMinutes cannot be negative" }
        require(mirrorCacheMinutes >= 0) { "mirrorCacheMinutes cannot be negative" }
    }

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
        )
}
