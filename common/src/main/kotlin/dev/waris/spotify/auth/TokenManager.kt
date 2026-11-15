package dev.waris.spotify.auth

import dev.waris.spotify.error.SpotifyException
import java.io.Closeable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import org.slf4j.LoggerFactory

internal class TokenManager(
    private val providers: Map<TokenKind, TokenProvider>,
    private val refreshMargin: Duration = 5.minutes,
) : Closeable {
    private val log = LoggerFactory.getLogger(TokenManager::class.java)

    private val tokens = ConcurrentHashMap<TokenKind, AccessToken>()
    private val refreshing = ConcurrentHashMap<TokenKind, CompletableFuture<AccessToken>>()

    private val executor =
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "spotify-token-manager").apply {
                isDaemon = true
            }
        }

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
    ): AccessToken {
        // share one refresh future across concurrent calls
        val future =
            refreshing.compute(kind) { _, existing ->
                if (existing != null && !existing.isDone) {
                    existing
                } else {
                    CompletableFuture.supplyAsync(
                        {
                            val token = provider.fetchToken()
                            tokens[kind] = token
                            log.info("token refreshed expires_in={}", token.expiresInSeconds)
                            token
                        },
                        executor,
                    )
                }
            } ?: throw SpotifyException("failed to initiate refresh for $kind")

        return try {
            future.join()
        } catch (e: CompletionException) {
            val cause = e.cause ?: e
            if (cause is SpotifyException) {
                throw cause
            }
            throw SpotifyException("failed to refresh token for $kind", cause)
        } finally {
            refreshing.remove(kind, future)
        }
    }

    override fun close() {
        executor.shutdownNow()
        tokens.clear()
        refreshing.clear()
    }
}
