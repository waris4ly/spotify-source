package dev.waris.spotify.enrich

import dev.waris.spotify.api.SpotifyHttp
import dev.waris.spotify.api.internal.PersistedQueries
import dev.waris.spotify.auth.TokenKind
import java.nio.charset.StandardCharsets
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.slf4j.LoggerFactory

internal data class ExtractedColors(
    val dark: String? = null,
    val light: String? = null,
    val raw: String? = null,
)

internal class ArtworkColors(
    private val http: SpotifyHttp,
    private val persistedQueries: PersistedQueries = PersistedQueries(),
    private val endpointUrl: String = DEFAULT_ENDPOINT_URL,
) {
    private val log = LoggerFactory.getLogger(ArtworkColors::class.java)

    fun fetchColors(artworkUrl: String?): ExtractedColors? {
        if (artworkUrl.isNullOrBlank()) {
            return null
        }
        val match = IMAGE_ID_PATTERN.find(artworkUrl) ?: return null
        val imageId = match.groupValues[1]

        // extract dominant palette colors via pathfinder graphql query
        val hash = persistedQueries.hashFor(PersistedQueries.FETCH_EXTRACTED_COLORS)
        val body =
            """
            {
              "variables": {
                "imageUris": [
                  "spotify:image:$imageId"
                ]
              },
              "operationName": "${PersistedQueries.FETCH_EXTRACTED_COLORS}",
              "extensions": {
                "persistedQuery": {
                  "version": 1,
                  "sha256Hash": "$hash"
                }
              }
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
                http.execute(request, TokenKind.ANONYMOUS)
            } catch (e: Exception) {
                log.warn("failed extracting colors for image {}: {}", imageId, e.message)
                return null
            }

        val palette =
            response
                .get("data")
                .get("extractedColors")
                .values()
                .firstOrNull() ?: return null

        val dark = palette.get("colorDark").get("hex").text()
        val light = palette.get("colorLight").get("hex").text()
        val raw = palette.get("colorRaw").get("hex").text()

        if (dark == null && light == null && raw == null) {
            return null
        }

        return ExtractedColors(dark = dark, light = light, raw = raw)
    }

    companion object {
        const val DEFAULT_ENDPOINT_URL: String =
            "https://api-partner.spotify.com/pathfinder/v2/query"

        private val IMAGE_ID_PATTERN =
            Regex("""(?:/image/|spotify:image:)([a-f0-9]{32,40})""", RegexOption.IGNORE_CASE)
    }
}
