package dev.waris.spotify.auth

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser
import dev.waris.spotify.error.SpotifyException
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

internal abstract class BaseTokenProvider : TokenProvider {
    @Volatile
    private var coolDownUntil: ComparableTimeMark? = null

    override val isCoolingDown: Boolean
        get() = coolDownUntil?.let { !it.hasPassedNow() } ?: false

    override fun coolDown(duration: Duration) {
        coolDownUntil = TimeSource.Monotonic.markNow() + duration
    }
}

internal fun parseAccessToken(
    json: JsonBrowser,
    token: String? = null,
): AccessToken {
    val tokenValue =
        token
            ?: json.get("accessToken").text()
            ?: json.get("access_token").text()
            ?: json.get("token").text()
            ?: throw SpotifyException("token response missing access token")

    val expiresMs = json.get("accessTokenExpirationTimestampMs").asLong(0)
    val expiresInSeconds =
        if (expiresMs > 0) {
            ((expiresMs - System.currentTimeMillis()) / 1000).coerceAtLeast(60)
        } else {
            json.get("expires_in").asLong(3600)
        }

    val expiresAt = TimeSource.Monotonic.markNow() + expiresInSeconds.seconds
    return AccessToken(
        value = tokenValue,
        expiresAt = expiresAt,
        expiresInSeconds = expiresInSeconds,
    )
}
