package dev.waris.spotify.enrich

import dev.waris.spotify.api.SpotifyHttp
import dev.waris.spotify.auth.TokenKind
import dev.waris.spotify.model.SpotifyId
import java.nio.charset.StandardCharsets
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.slf4j.LoggerFactory

internal open class CanvasLookup(
    private val http: SpotifyHttp,
    private val endpointUrl: String = DEFAULT_ENDPOINT_URL,
) {
    private val log = LoggerFactory.getLogger(CanvasLookup::class.java)

    open fun fetchCanvasUrl(trackId: SpotifyId): String? {
        // canvas video lookup uses spclient cache endpoint with account token
        val body =
            """
            {
              "tracks": [
                {
                  "track_uri": "spotify:track:${trackId.value}"
                }
              ]
            }
            """.trimIndent()

        val request =
            HttpPost(endpointUrl).apply {
                setHeader("Accept", "application/json")
                setHeader("Content-Type", "application/json; charset=utf-8")
                entity = StringEntity(body, StandardCharsets.UTF_8)
            }

        val response =
            try {
                http.execute(request, TokenKind.ACCOUNT)
            } catch (e: Exception) {
                log.warn("failed canvas lookup for {}: {}", trackId.value, e.message)
                return null
            }

        val canvas =
            response.get("canvases").values().firstOrNull()
                ?: response.get("canvasesList").values().firstOrNull()
                ?: response
                    .get("data")
                    .get("canvasesList")
                    .values()
                    .firstOrNull()
                ?: return null

        return canvas.get("canvas_url").text()
            ?: canvas.get("canvasUrl").text()
            ?: canvas.get("url").text()
    }

    companion object {
        const val DEFAULT_ENDPOINT_URL: String =
            "https://spclient.wg.spotify.com/canvaz-cache/v0/canvases"
    }
}
