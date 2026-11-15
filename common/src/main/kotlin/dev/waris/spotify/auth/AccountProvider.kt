package dev.waris.spotify.auth

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager
import dev.waris.spotify.error.SpotifyException
import java.io.IOException
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import org.apache.http.client.methods.HttpGet
import org.slf4j.LoggerFactory

internal class AccountProvider(
    private val accountCookie: String,
    private val httpInterfaceManager: HttpInterfaceManager,
    private val tokenUrl: String = DEFAULT_TOKEN_URL,
) : TokenProvider {
    private val log = LoggerFactory.getLogger(AccountProvider::class.java)

    override val kind: TokenKind = TokenKind.ACCOUNT

    private var coolDownUntil: ComparableTimeMark? = null

    override val isCoolingDown: Boolean
        get() = coolDownUntil?.let { !it.hasPassedNow() } ?: false

    override fun coolDown(duration: Duration) {
        coolDownUntil = TimeSource.Monotonic.markNow() + duration
    }

    override fun fetchToken(): AccessToken {
        if (accountCookie.isBlank()) {
            throw SpotifyException("accountCookie is not configured")
        }

        // exchange sp dc cookie for an account token that enables canvas and metadata
        val request =
            HttpGet(tokenUrl).apply {
                setHeader("Cookie", "sp_dc=$accountCookie")
                setHeader("User-Agent", USER_AGENT)
                setHeader("Accept", "application/json")
            }

        val response =
            try {
                httpInterfaceManager.getInterface().use { http ->
                    http.execute(request)
                }
            } catch (e: IOException) {
                throw SpotifyException("failed to request account token: ${e.message}", e)
            }

        val statusCode = response.statusLine.statusCode
        val content =
            response.entity?.content?.use {
                JsonBrowser.parse(it)
            } ?: JsonBrowser.NULL_BROWSER

        if (statusCode != 200) {
            throw SpotifyException("failed to obtain account token: status $statusCode")
        }

        val token =
            content.get("accessToken").text()
                ?: content.get("access_token").text()
                ?: throw SpotifyException("account token response missing accessToken")

        val expiresMs = content.get("accessTokenExpirationTimestampMs").asLong(0)
        val expiresInSeconds =
            if (expiresMs > 0) {
                ((expiresMs - System.currentTimeMillis()) / 1000).coerceAtLeast(60)
            } else {
                content.get("expires_in").asLong(3600)
            }

        val expiresAt = TimeSource.Monotonic.markNow() + expiresInSeconds.seconds
        return AccessToken(
            value = token,
            expiresAt = expiresAt,
            expiresInSeconds = expiresInSeconds,
        )
    }

    companion object {
        const val DEFAULT_TOKEN_URL: String =
            "https://open.spotify.com/get_access_token?reason=transport&productType=web-player"
        private const val USER_AGENT: String =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/130.0.0.0"
    }
}
