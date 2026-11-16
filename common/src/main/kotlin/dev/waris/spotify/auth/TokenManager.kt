package dev.waris.spotify.auth

import dev.waris.spotify.error.SpotifyException
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import org.slf4j.LoggerFactory

internal class TokenManager(
    private val providers: Map<TokenKind, TokenProvider>,
    private val refreshMargin: Duration = 5.minutes,
) : Closeable {
    private val log = LoggerFactory.getLogger(TokenManager::class.java)

    private val tokens = ConcurrentHashMap<TokenKind, AccessToken>()
    private val locks = TokenKind.entries.associateWith { Any() }

    fun getToken(kind: TokenKind): AccessToken {
        val provider =
            providers[kind]
                ?: throw SpotifyException("no token provider for $kind")

        if (provider.isCoolingDown) {
            throw SpotifyException.RateLimit(
                message = "provider for $kind is cooling down",
                isQuotaExceeded = true,
            )
        }

        // return cached token if it has plenty of lifetime left
        val cached = tokens[kind]
        if (cached != null && !cached.isExpiring(refreshMargin)) {
            return cached
        }

        return refresh(kind, provider)
    }

    fun invalidate(kind: TokenKind) {
        tokens.remove(kind)
    }

    fun setToken(
        kind: TokenKind,
        token: AccessToken,
    ) {
        tokens[kind] = token
    }

    fun isCoolingDown(kind: TokenKind): Boolean = providers[kind]?.isCoolingDown ?: false

    fun coolDown(
        kind: TokenKind,
        duration: Duration,
    ) {
        providers[kind]?.coolDown(duration)
        invalidate(kind)
    }

    private fun refresh(
        kind: TokenKind,
        provider: TokenProvider,
    ): AccessToken =
        synchronized(locks.getValue(kind)) {
            // another caller may have refreshed while we waited on the lock
            val cached = tokens[kind]
            if (cached != null && !cached.isExpiring(refreshMargin)) {
                return cached
            }
            val token =
                try {
                    provider.fetchToken()
                } catch (e: SpotifyException) {
                    throw e
                } catch (e: Exception) {
                    throw SpotifyException("failed to refresh token for $kind", e)
                }
            tokens[kind] = token
            log.info("token refreshed expires_in={}", token.expiresInSeconds)
            token
        }

    override fun close() {
        tokens.clear()
    }
}
