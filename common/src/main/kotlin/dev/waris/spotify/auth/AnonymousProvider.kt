package dev.waris.spotify.auth

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager
import dev.waris.spotify.error.SpotifyException
import java.io.IOException
import java.util.regex.Pattern
import org.apache.http.client.methods.HttpGet
import org.slf4j.LoggerFactory

internal class AnonymousProvider(
    private val httpInterfaceManager: HttpInterfaceManager,
    private val anonymousTokenUrl: String? = null,
    private val seedUrl: String = DEFAULT_SEED_URL,
) : BaseTokenProvider() {
    private val log = LoggerFactory.getLogger(AnonymousProvider::class.java)

    override val kind: TokenKind = TokenKind.ANONYMOUS

    override fun fetchToken(): AccessToken {
        if (!anonymousTokenUrl.isNullOrBlank()) {
            return fetchFromCustomService(anonymousTokenUrl)
        }
        return fetchFromWebEmbed()
    }

    private fun fetchFromCustomService(url: String): AccessToken {
        val request =
            HttpGet(url).apply {
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
                throw SpotifyException("anonymous token request to custom service failed", e)
            }

        if (statusCode != 200) {
            throw SpotifyException("custom anonymous token endpoint returned status $statusCode")
        }

        return parseAccessToken(content)
    }

    private fun fetchFromWebEmbed(): AccessToken {
        val request =
            HttpGet(seedUrl).apply {
                setHeader("User-Agent", USER_AGENT)
            }

        val html =
            try {
                httpInterfaceManager.getInterface().use { http ->
                    http.execute(request).use { response ->
                        if (response.statusLine.statusCode != 200) {
                            null
                        } else {
                            response.entity
                                ?.content
                                ?.bufferedReader(Charsets.UTF_8)
                                ?.readText()
                        }
                    }
                }
            } catch (e: IOException) {
                log.warn("failed to fetch spotify embed html: {}", e.message)
                null
            }

        if (html != null) {
            val matcher = NEXT_DATA_PATTERN.matcher(html)
            if (matcher.find()) {
                val jsonString = matcher.group(1)
                val json = JsonBrowser.parse(jsonString)
                val session = extractSession(json)
                val token = session.get("accessToken").text()
                if (!token.isNullOrEmpty()) {
                    return parseAccessToken(session)
                }
            }
        }

        return fetchFromDirectEndpoint()
    }

    private fun fetchFromDirectEndpoint(): AccessToken {
        val request =
            HttpGet(FALLBACK_TOKEN_URL).apply {
                setHeader("User-Agent", USER_AGENT)
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
                throw SpotifyException("fallback anonymous token request failed", e)
            }

        if (statusCode != 200) {
            throw SpotifyException("failed to obtain anonymous token: status $statusCode")
        }

        return parseAccessToken(content)
    }

    private fun extractSession(json: JsonBrowser): JsonBrowser {
        val direct =
            json
                .get(
                    "props",
                ).get("pageProps")
                .get("state")
                .get("data")
                .get("settings")
                .get("session")
        if (!direct.isNull) {
            return direct
        }
        return findSessionRecursively(json) ?: JsonBrowser.NULL_BROWSER
    }

    private fun findSessionRecursively(node: JsonBrowser): JsonBrowser? {
        if (node.isNull) {
            return null
        }
        val candidate = node.get("session")
        if (!candidate.isNull && !candidate.get("accessToken").isNull) {
            return candidate
        }
        if (node.isMap) {
            for (key in node.keys()) {
                val child = node.get(key)
                val found = findSessionRecursively(child)
                if (found != null) {
                    return found
                }
            }
        }
        return null
    }

    companion object {
        const val DEFAULT_SEED_URL: String =
            "https://open.spotify.com/embed/playlist/37i9dQZF1DXcBWIGoYBM5M"
        const val FALLBACK_TOKEN_URL: String =
            "https://open.spotify.com/get_access_token?reason=transport&productType=web-player"
        private const val USER_AGENT: String =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/130.0.0.0"

        private val NEXT_DATA_PATTERN =
            Pattern.compile(
                "<script id=\"__NEXT_DATA__\" type=\"application/json\">(.*?)</script>",
                Pattern.DOTALL,
            )
    }
}
