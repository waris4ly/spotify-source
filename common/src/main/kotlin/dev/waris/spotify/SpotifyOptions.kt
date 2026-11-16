package dev.waris.spotify

public data class SpotifyOptions(
    val clientId: String = "",
    val clientSecret: String = "",
    val accountCookie: String = "",
    val anonymousTokenUrl: String = "",
    val countryCode: String = "US",
    val internalBackend: Boolean = true,
    val searchLimit: Int = 10,
    val playlistLoadLimit: Int = 1000,
    val albumLoadLimit: Int = 500,
    val showLoadLimit: Int = 200,
    val allowLocalFiles: Boolean = false,
    val extractColors: Boolean = false,
    val canvas: Boolean = false,
    val recommendationLimit: Int = 10,
    val requestTimeoutSeconds: Int = 10,
    val mirrorProviders: List<String> =
        listOf(
            """ytsearch:"%isrc%"""",
            "ytsearch:%query%",
        ),
    val mirrorDurationToleranceMs: Long = 5000,
    val metadataCacheMinutes: Int = 30,
    val mirrorCacheMinutes: Int = 360,
    val queryHashes: Map<String, String> = emptyMap(),
    val lyrics: Boolean = true,
    val lyricsProviders: List<String> = listOf("spotify", "lrclib"),
    val lyricsCacheMinutes: Int = 720,
    val lyricsMissCacheMinutes: Int = 30,
) {
    init {
        require(searchLimit in 1..50) { "searchLimit must be between 1 and 50" }
        require(playlistLoadLimit in 1..10_000) { "playlistLoadLimit must be between 1 and 10,000" }
        require(albumLoadLimit in 1..10_000) { "albumLoadLimit must be between 1 and 10,000" }
        require(showLoadLimit in 1..10_000) { "showLoadLimit must be between 1 and 10,000" }
        require(recommendationLimit in 1..50) { "recommendationLimit must be between 1 and 50" }
        require(requestTimeoutSeconds in 1..300) {
            "requestTimeoutSeconds must be between 1 and 300"
        }
        require(metadataCacheMinutes >= 0) { "metadataCacheMinutes cannot be negative" }
        require(mirrorCacheMinutes >= 0) { "mirrorCacheMinutes cannot be negative" }
        require(mirrorDurationToleranceMs >= 0) { "mirrorDurationToleranceMs cannot be negative" }
        require(mirrorProviders.isNotEmpty()) { "mirrorProviders cannot be empty" }
        require(lyricsProviders.isNotEmpty()) { "lyricsProviders cannot be empty" }
        require(lyricsProviders.all { it in SUPPORTED_LYRICS_PROVIDERS }) {
            "lyricsProviders may only contain: spotify, lrclib"
        }
        require(lyricsCacheMinutes >= 0) { "lyricsCacheMinutes cannot be negative" }
        require(lyricsMissCacheMinutes >= 0) { "lyricsMissCacheMinutes cannot be negative" }
    }

    override fun toString(): String {
        val secretMask = if (clientSecret.isBlank()) "<unset>" else "***"
        val cookieMask = if (accountCookie.isBlank()) "<unset>" else "***"
        val anonUrlMask = if (anonymousTokenUrl.isBlank()) "<unset>" else "***"
        return "SpotifyOptions(clientId=$clientId, clientSecret=$secretMask, " +
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

    public companion object {
        public val SUPPORTED_LYRICS_PROVIDERS: Set<String> = setOf("spotify", "lrclib")
    }
}
