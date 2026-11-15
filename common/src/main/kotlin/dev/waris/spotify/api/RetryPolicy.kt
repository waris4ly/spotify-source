package dev.waris.spotify.api

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

internal data class RetryPolicy(
    val max401Retries: Int = 1,
    val max429Retries: Int = 2,
    val maxRetryAfter: Duration = 10.seconds,
    val max5xxRetries: Int = 1,
    val retryDelay5xx: Duration = 500.milliseconds,
    val quotaCoolDownDuration: Duration = 5.minutes,
)
