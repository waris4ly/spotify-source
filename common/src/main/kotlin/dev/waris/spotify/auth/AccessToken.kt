package dev.waris.spotify.auth

import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

internal data class AccessToken(
    val value: String,
    val expiresAt: ComparableTimeMark,
    val expiresInSeconds: Long = 3600,
) {
    fun isExpiring(margin: Duration = 5.minutes): Boolean {
        val now = TimeSource.Monotonic.markNow()
        return expiresAt - now <= margin
    }

    fun isExpired(): Boolean = expiresAt.hasPassedNow()
}
