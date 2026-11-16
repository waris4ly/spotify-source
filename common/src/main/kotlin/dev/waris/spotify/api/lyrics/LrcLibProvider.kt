package dev.waris.spotify.api.lyrics

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser
import dev.waris.spotify.api.SpotifyHttp
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.math.abs
import org.apache.http.client.methods.HttpGet

internal class LrcLibProvider(
    private val http: SpotifyHttp,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val durationToleranceMs: Long = 2000,
) : LyricsProvider {
    override val name: String = "lrclib"

    override fun isAvailable(): Boolean = true

    override fun fetch(query: LyricsQuery): Lyrics? {
        // first credited artist only, lrclib matches poorly on "a, b, c"
        val artist = query.artist.substringBefore(',').trim()
        val url =
            "$baseUrl/search?track_name=${encode(query.title)}&artist_name=${encode(artist)}"
        val request =
            HttpGet(url).apply {
                setHeader("Accept", "application/json")
                setHeader("User-Agent", USER_AGENT)
            }

        val best =
            http
                .execute(request, tokenKind = null)
                .values()
                .filter { !it.get("instrumental").asBoolean(false) }
                .filter { entry ->
                    val seconds =
                        entry.get("duration").text()?.toDoubleOrNull()
                            ?: entry
                                .get("duration")
                                .asLong(0)
                                .takeIf { it > 0 }
                                ?.toDouble()
                            ?: return@filter false
                    query.durationMs <= 0 ||
                        abs(seconds * 1000 - query.durationMs) <= durationToleranceMs
                }
                // a synced entry always wins over a plain one
                .maxByOrNull { if (entry(it, "syncedLyrics").isNullOrBlank()) 0 else 1 }
                ?: return null

        val synced = parseSynced(entry(best, "syncedLyrics"))
        if (synced.isNotEmpty()) {
            return Lyrics(name, "LRCLIB", LyricsSync.LINE, null, false, synced)
        }

        val plain =
            entry(best, "plainLyrics")
                ?.trim()
                ?.lines()
                ?.map { LyricsLine(0, null, it.trim()) }
                .orEmpty()
        if (plain.none { it.text.isNotBlank() }) {
            return null
        }
        return Lyrics(name, "LRCLIB", LyricsSync.NONE, null, false, plain)
    }

    private fun entry(
        json: JsonBrowser,
        key: String,
    ): String? = json.get(key).text()

    private fun parseSynced(raw: String?): List<LyricsLine> {
        if (raw.isNullOrBlank()) {
            return emptyList()
        }
        val parsed =
            raw.lines().mapNotNull { row ->
                val match = LRC_LINE.matchEntire(row.trim()) ?: return@mapNotNull null
                val (minutes, seconds, fraction, text) = match.destructured
                val millis =
                    minutes.toLong() * 60_000 +
                        seconds.toLong() * 1000 +
                        fraction.padEnd(3, '0').take(3).toLong()
                millis to text.trim()
            }
        return parsed
            .sortedBy { it.first }
            .mapIndexed { index, (start, text) ->
                LyricsLine(start, parsed.getOrNull(index + 1)?.first, text)
            }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    companion object {
        const val DEFAULT_BASE_URL: String = "https://lrclib.net/api"
        private const val USER_AGENT: String =
            "spotify-source (https://github.com/waris4ly/spotify-source)"
        private val LRC_LINE = Regex("""^\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]\s?(.*)$""")
    }
}
