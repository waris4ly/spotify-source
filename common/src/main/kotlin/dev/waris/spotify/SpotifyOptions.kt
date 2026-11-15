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
    }
}
