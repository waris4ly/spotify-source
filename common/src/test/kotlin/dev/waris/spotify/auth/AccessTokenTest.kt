package dev.waris.spotify.auth

import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AccessTokenTest {
    @Test
    fun isExpiring_tokenWith120SecondsLifetimeNotExpiringRightAfterCreation() {
        val now = TimeSource.Monotonic.markNow()
        val token =
            AccessToken(
                value = "test-token",
                expiresAt = now + 120.seconds,
                expiresInSeconds = 120,
            )

        assertFalse(token.isExpiring(5.minutes))
    }

    @Test
    fun isExpiring_tokenWith3600SecondsLifetimeUsesFiveMinuteMargin() {
        val now = TimeSource.Monotonic.markNow()
        val freshToken =
            AccessToken(
                value = "test-token",
                expiresAt = now + 6.minutes,
                expiresInSeconds = 3600,
            )
        assertFalse(freshToken.isExpiring(5.minutes))

        val expiringToken =
            AccessToken(
                value = "test-token",
                expiresAt = now + 4.minutes,
                expiresInSeconds = 3600,
            )
        assertTrue(expiringToken.isExpiring(5.minutes))
    }
}
