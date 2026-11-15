package dev.waris.spotify.api.internal

import dev.waris.spotify.api.SpotifyHttp
import dev.waris.spotify.auth.TokenKind
import dev.waris.spotify.model.Base62
import dev.waris.spotify.model.Isrc
import dev.waris.spotify.model.SpotifyId
import org.apache.http.client.methods.HttpGet
import org.slf4j.LoggerFactory

internal class ClientMetadata(
    private val http: SpotifyHttp,
    private val baseUrl: String = DEFAULT_BASE_URL,
) {
    private val log = LoggerFactory.getLogger(ClientMetadata::class.java)

    fun fetchIsrc(id: SpotifyId): Isrc? {
        // convert base62 spotify id to 32 hex gid for spclient metadata lookup
        val hex = Base62.toHex(id.value)
        if (hex.isBlank()) {
            return null
        }

        val url = "$baseUrl$hex?market=from_token"
        val request =
            HttpGet(url).apply {
                setHeader("Accept", "application/json")
                setHeader("User-Agent", USER_AGENT)
            }

        val response =
            try {
                http.execute(request, TokenKind.ACCOUNT)
            } catch (e: Exception) {
                log.warn("client metadata lookup failed for {}: {}", id.value, e.message)
                return null
            }

        val externalIds = response.get("external_id")
        if (externalIds.isList) {
            for (item in externalIds.values()) {
                if (item.get("type").text() == "isrc") {
                    val isrcText = item.get("id").text()
                    if (!isrcText.isNullOrBlank()) {
                        return Isrc(isrcText)
                    }
                }
            }
        }

        return null
    }

    companion object {
        const val DEFAULT_BASE_URL: String =
            "https://spclient.wg.spotify.com/metadata/4/track/"
        private const val USER_AGENT: String =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/130.0.0.0"
    }
}
