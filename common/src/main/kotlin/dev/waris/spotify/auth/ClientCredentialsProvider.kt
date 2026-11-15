package dev.waris.spotify.auth

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager
import dev.waris.spotify.error.SpotifyException
import java.nio.charset.StandardCharsets
import kotlin.io.encoding.Base64
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.slf4j.LoggerFactory

internal class ClientCredentialsProvider(
    private val clientId: String,
    private val clientSecret: String,
    private val httpInterfaceManager: HttpInterfaceManager,
    private val tokenUrl: String = DEFAULT_TOKEN_URL,
) : TokenProvider {
    private val log = LoggerFactory.getLogger(ClientCredentialsProvider::class.java)

    override val kind: TokenKind = TokenKind.CLIENT

    private var coolDownUntil: ComparableTimeMark? = null

    override val isCoolingDown: Boolean
        get() = coolDownUntil?.let { !it.hasPassedNow() } ?: false

    override fun coolDown(duration: Duration) {
        coolDownUntil = TimeSource.Monotonic.markNow() + duration
    }

    override fun fetchToken(): AccessToken {
        if (clientId.isEmpty() || clientSecret.isEmpty()) {
            throw SpotifyException("missing client credentials")
        }

        // standard oauth2 client credentials flow for official developer keys
        val request =
            HttpPost(tokenUrl).apply {
                val credentials = "$clientId:$clientSecret"
                val auth = Base64.encode(credentials.encodeToByteArray())
                setHeader("Authorization", "Basic $auth")
                setHeader("Content-Type", "application/x-www-form-urlencoded")
                entity = StringEntity("grant_type=client_credentials", StandardCharsets.UTF_8)
            }

        val response =
            try {
                httpInterfaceManager.getInterface().use { http ->
                    http.execute(request)
                }
            } catch (e: Exception) {
                throw SpotifyException("token request failed", e)
            }

        val statusCode = response.statusLine.statusCode
        val content =
            response.entity?.content?.use {
                JsonBrowser.parse(it)
            } ?: JsonBrowser.NULL_BROWSER

        if (statusCode == 429) {
            val retryAfter = response.getFirstHeader("Retry-After")?.value?.toLongOrNull()
            val reason =
                content.get("error").get("reason").text()
                    ?: content.get("reason").text()
            val isQuota = reason == "QUOTA_EXCEEDED"
            log.warn("token rate limited quota_exceeded={}", isQuota)
            throw SpotifyException.RateLimit(
                message = "token endpoint rate limited",
                retryAfterSeconds = retryAfter,
                isQuotaExceeded = isQuota,
            )
        }

        if (statusCode != 200) {
            val message =
                content.get("error_description").text()
                    ?: content.get("error").text()
                    ?: "status $statusCode"
            throw SpotifyException("authentication failed: $message")
        }

        val token =
            content.get("access_token").text()
                ?: throw SpotifyException("token response missing access_token")
        val expiresIn = content.get("expires_in").asLong(3600)
        val expiresAt = TimeSource.Monotonic.markNow() + expiresIn.seconds
        return AccessToken(
            value = token,
            expiresAt = expiresAt,
            expiresInSeconds = expiresIn,
        )
    }

    companion object {
        const val DEFAULT_TOKEN_URL: String = "https://accounts.spotify.com/api/token"
    }
}
