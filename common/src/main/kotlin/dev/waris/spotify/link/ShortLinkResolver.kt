package dev.waris.spotify.link

import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager
import java.io.IOException
import java.net.URI
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpGet
import org.slf4j.LoggerFactory

internal class ShortLinkResolver(
    private val httpInterfaceManager: HttpInterfaceManager,
    private val maxHops: Int = DEFAULT_MAX_HOPS,
    private val pattern: Regex = SHORT_LINK_PATTERN,
) {
    private val log = LoggerFactory.getLogger(ShortLinkResolver::class.java)

    fun isShortLink(url: String): Boolean {
        val trimmed = url.trim()
        return pattern.containsMatchIn(trimmed)
    }

    fun resolve(url: String): String? {
        var currentUrl = url.trim()
        if (!isShortLink(currentUrl)) {
            return currentUrl
        }

        var hops = 0
        val visited = mutableSetOf<String>()

        // follow redirects manually up to max hops to unwrap short urls
        while (hops < maxHops) {
            visited.add(currentUrl)
            val request =
                HttpGet(currentUrl).apply {
                    config = RequestConfig.custom().setRedirectsEnabled(false).build()
                    setHeader("User-Agent", USER_AGENT)
                }

            val (statusCode, location, body) =
                try {
                    httpInterfaceManager.getInterface().use { http ->
                        val response = http.execute(request)
                        val code = response.statusLine.statusCode
                        val loc = response.getFirstHeader("Location")?.value
                        val entityText =
                            if (code in 200..299) {
                                response.entity
                                    ?.content
                                    ?.bufferedReader(Charsets.UTF_8)
                                    ?.readText()
                            } else {
                                null
                            }
                        Triple(code, loc, entityText)
                    }
                } catch (e: IOException) {
                    log.warn("failed resolving short link {}: {}", currentUrl, e.message)
                    return currentUrl
                }

            // follow 3xx redirect location header
            if (statusCode in 300..399 && !location.isNullOrBlank()) {
                val nextUrl =
                    try {
                        URI.create(currentUrl).resolve(location).toString()
                    } catch (e: IllegalArgumentException) {
                        location
                    }
                hops++
                currentUrl = nextUrl
                if (currentUrl in visited) {
                    log.warn("redirect loop detected for short link: {}", currentUrl)
                    return currentUrl
                }
                if (!isShortLink(currentUrl)) {
                    return currentUrl
                }
            } else if (statusCode in 200..299 && body != null) {
                // extract canonical url from page meta tags when final landing is 200
                val canonical = extractCanonicalUrl(body)
                if (canonical != null) {
                    return canonical
                }
                return currentUrl
            } else {
                return currentUrl
            }
        }

        log.warn("max redirect hops ({}) exceeded for short link {}", maxHops, url)
        return currentUrl
    }

    private fun extractCanonicalUrl(html: String): String? {
        val ogMatch = OG_URL_PATTERN.find(html)
        if (ogMatch != null) {
            return ogMatch.groupValues[1]
        }
        val canonicalMatch = CANONICAL_URL_PATTERN.find(html)
        if (canonicalMatch != null) {
            return canonicalMatch.groupValues[1]
        }
        return null
    }

    companion object {
        const val DEFAULT_MAX_HOPS: Int = 5
        private const val USER_AGENT: String =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/130.0.0.0"

        val SHORT_LINK_PATTERN: Regex =
            Regex(
                """^https?://(?:[a-zA-Z0-9_-]+\.)?spotify\.(?:link|app\.link)/.*""",
                RegexOption.IGNORE_CASE,
            )

        private val OG_URL_PATTERN =
            Regex(
                """<meta\s+(?:property|name)=["']og:url["']\s+content=["'](https?://[^"']+)["']""",
                RegexOption.IGNORE_CASE,
            )

        private val CANONICAL_URL_PATTERN =
            Regex(
                """<link\s+rel=["']canonical["']\s+href=["'](https?://[^"']+)["']""",
                RegexOption.IGNORE_CASE,
            )
    }
}
