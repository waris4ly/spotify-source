package dev.waris.spotify.auth

import kotlin.time.Duration

internal interface TokenProvider {
    val kind: TokenKind

    val isCoolingDown: Boolean
        get() = false

    fun coolDown(duration: Duration) {}

    fun fetchToken(): AccessToken
}
