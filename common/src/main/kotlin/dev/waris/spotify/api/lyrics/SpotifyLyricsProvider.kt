package dev.waris.spotify.api.lyrics

import dev.waris.spotify.api.SpotifyHttp
import dev.waris.spotify.auth.TokenKind
import dev.waris.spotify.auth.TokenManager
import org.apache.http.client.methods.HttpGet

internal class SpotifyLyricsProvider(
    private val http: SpotifyHttp,
    private val tokenManager: TokenManager,
    private val resolver: TrackResolver,
    private val accountConfigured: Boolean,
    private val baseUrl: String = DEFAULT_BASE_URL,
) : LyricsProvider {
    override val name: String = "spotify"

    override fun isAvailable(): Boolean =
        accountConfigured && !tokenManager.isCoolingDown(TokenKind.ACCOUNT)

    override fun fetch(query: LyricsQuery): Lyrics? {
        val id = query.spotifyId ?: resolver.resolve(query) ?: return null

        val request =
            HttpGet("$baseUrl${id.value}?format=json&vocalRemoval=false").apply {
                setHeader("Accept", "application/json")
                setHeader("App-Platform", "WebPlayer")
                setHeader("User-Agent", USER_AGENT)
            }

        // 404 comes back as a null browser which means spotify has no lyrics for it
        val root = http.execute(request, TokenKind.ACCOUNT).get("lyrics")
        if (root.isNull) {
            return null
        }

        val synced = root.get("syncType").text() == "LINE_SYNCED"
        val rows = root.get("lines").values()

        val lines =
            rows.mapIndexed { index, row ->
                val start =
                    if (synced) {
                        row.get("startTimeMs").text()?.toLongOrNull()
                            ?: row.get("startTimeMs").asLong(0)
                    } else {
                        0L
                    }
                val end =
                    (
                        row.get("endTimeMs").text()?.toLongOrNull()
                            ?: row.get("endTimeMs").asLong(0)
                    ).takeIf { synced && it > start }
                        ?: rows
                            .getOrNull(index + 1)
                            ?.let { nextRow ->
                                nextRow.get("startTimeMs").text()?.toLongOrNull()
                                    ?: nextRow.get("startTimeMs").asLong(0)
                            }?.takeIf { synced && it > start }
                LyricsLine(
                    start,
                    end,
                    row
                        .get("words")
                        .text()
                        .orEmpty()
                        .trim(),
                )
            }

        if (lines.none { it.text.isNotBlank() }) {
            return null
        }

        return Lyrics(
            provider = name,
            providerLabel = root.get("providerDisplayName").text(),
            sync = if (synced) LyricsSync.LINE else LyricsSync.NONE,
            language = root.get("language").text(),
            rightToLeft = root.get("isRtlLanguage").asBoolean(false),
            lines = lines,
        )
    }

    companion object {
        const val DEFAULT_BASE_URL: String =
            "https://spclient.wg.spotify.com/color-lyrics/v2/track/"
        private const val USER_AGENT: String =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/130.0.0.0"
    }
}
