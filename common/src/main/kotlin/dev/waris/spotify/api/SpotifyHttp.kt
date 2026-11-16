package dev.waris.spotify.api

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager
import dev.waris.spotify.auth.TokenKind
import dev.waris.spotify.auth.TokenManager
import dev.waris.spotify.error.SpotifyException
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import org.apache.http.client.methods.HttpUriRequest
import org.slf4j.LoggerFactory

internal class SpotifyHttp(
    private val httpInterfaceManager: HttpInterfaceManager,
    private val tokenManager: TokenManager,
    private val retryPolicy: RetryPolicy = RetryPolicy(),
    private val sleeper: (Duration) -> Unit = { duration ->
        Thread.sleep(duration.inWholeMilliseconds)
    },
) {
    private val log = LoggerFactory.getLogger(SpotifyHttp::class.java)

    fun execute(
        request: HttpUriRequest,
        tokenKind: TokenKind? = TokenKind.CLIENT,
    ): JsonBrowser {
        var retried401 = false
        var retryCount429 = 0
        var retried5xx = false

        while (true) {
            // attach fresh bearer token if this request kind requires auth
            if (tokenKind != null) {
                val token = tokenManager.getToken(tokenKind)
                request.setHeader("Authorization", "Bearer ${token.value}")
            }

            val (statusCode, content, retryAfterHeader) =
                try {
                    httpInterfaceManager.getInterface().use { http ->
                        http.execute(request).use { response ->
                            val code = response.statusLine.statusCode
                            val retryHeader = response.getFirstHeader("Retry-After")?.value
                            val body =
                                response.entity?.content?.use {
                                    JsonBrowser.parse(it)
                                } ?: JsonBrowser.NULL_BROWSER
                            Triple(code, body, retryHeader)
                        }
                    }
                } catch (e: IOException) {
                    // retry once on transient network drop before failing
                    if (!retried5xx && retryPolicy.max5xxRetries > 0) {
                        retried5xx = true
                        log.warn("network error retrying once")
                        sleeper(retryPolicy.retryDelay5xx)
                        continue
                    }
                    throw SpotifyException("request failed: ${e.message}", e)
                }

            if (statusCode in 200..299) {
                return content
            }

            when (statusCode) {
                // invalidate token and retry once if spotify rejects it with 401
                401 -> {
                    if (tokenKind != null && !retried401 && retryPolicy.max401Retries > 0) {
                        retried401 = true
                        log.warn("received 401 refreshing token and retrying")
                        tokenManager.invalidate(tokenKind)
                        continue
                    }
                    throw SpotifyException("unauthorized: 401")
                }

                403 -> {
                    log.warn("received 403 restricted access")
                    throw SpotifyException("forbidden: 403")
                }

                404 -> {
                    return JsonBrowser.NULL_BROWSER
                }

                429 -> {
                    val retryAfterSeconds =
                        retryAfterHeader?.toLongOrNull() ?: 1
                    val reason =
                        content.get("error").get("reason").text()
                            ?: content.get("reason").text()
                    val isQuota = reason == "QUOTA_EXCEEDED"

                    if (isQuota) {
                        log.warn("quota exceeded cooling backend")
                        if (tokenKind != null) {
                            tokenManager.coolDown(tokenKind, retryPolicy.quotaCoolDownDuration)
                        }
                        throw SpotifyException.RateLimit(
                            message = "quota exceeded",
                            isQuotaExceeded = true,
                        )
                    }

                    if (retryAfterSeconds.seconds <= retryPolicy.maxRetryAfter &&
                        retryCount429 < retryPolicy.max429Retries
                    ) {
                        retryCount429++
                        log.warn(
                            "rate limited waiting {}s retry {}/{}",
                            retryAfterSeconds,
                            retryCount429,
                            retryPolicy.max429Retries,
                        )
                        sleeper(retryAfterSeconds.seconds)
                        continue
                    }

                    throw SpotifyException.RateLimit(
                        message = "rate limited: retry after $retryAfterSeconds seconds",
                        retryAfterSeconds = retryAfterSeconds,
                        isQuotaExceeded = false,
                    )
                }

                in 500..599 -> {
                    if (!retried5xx && retryPolicy.max5xxRetries > 0) {
                        retried5xx = true
                        log.warn("server error {} retrying once", statusCode)
                        sleeper(retryPolicy.retryDelay5xx)
                        continue
                    }
                    throw SpotifyException("server error: $statusCode")
                }

                else -> {
                    throw SpotifyException("unexpected response: $statusCode")
                }
            }
        }
    }
}
