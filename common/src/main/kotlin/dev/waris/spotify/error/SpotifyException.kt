package dev.waris.spotify.error

internal open class SpotifyException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    class RateLimit(
        message: String,
        val retryAfterSeconds: Long? = null,
        val isQuotaExceeded: Boolean = false,
    ) : SpotifyException(message)

    class QueryRejected(
        val operationName: String,
        message: String = "persisted query not found for operation $operationName",
    ) : SpotifyException(message)
}
