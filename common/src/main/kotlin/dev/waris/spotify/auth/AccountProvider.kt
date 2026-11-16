package dev.waris.spotify.auth

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager
import dev.waris.spotify.error.SpotifyException
import java.io.IOException
import org.apache.http.client.methods.HttpGet
import org.slf4j.LoggerFactory

internal class AccountProvider(
    private val accountCookie: String,
    private val httpInterfaceManager: HttpInterfaceManager,
    private val tokenUrl: String = DEFAULT_TOKEN_URL,
) : BaseTokenProvider() {
    private val log = LoggerFactory.getLogger(AccountProvider::class.java)

    override val kind: TokenKind = TokenKind.ACCOUNT

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

        val (statusCode, content) =
            try {
                httpInterfaceManager.getInterface().use { http ->
                    http.execute(request).use { response ->
                        val code = response.statusLine.statusCode
                        val parsed =
                            response.entity?.content?.use {
                                JsonBrowser.parse(it)
                            } ?: JsonBrowser.NULL_BROWSER
                        code to parsed
                    }
                }
            } catch (e: IOException) {
                throw SpotifyException("failed to request account token: ${e.message}", e)
            }

        if (statusCode != 200) {
            throw SpotifyException("failed to obtain account token: status $statusCode")
        }

        return parseAccessToken(content)
    }

    companion object {
        const val DEFAULT_TOKEN_URL: String =
            "https://open.spotify.com/get_access_token?reason=transport&productType=web-player"
        private const val USER_AGENT: String =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/130.0.0.0"
    }
}
