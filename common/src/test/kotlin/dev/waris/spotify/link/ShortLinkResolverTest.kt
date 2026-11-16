package dev.waris.spotify.link

import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ShortLinkResolverTest {
    private lateinit var server: MockWebServer
    private lateinit var resolver: ShortLinkResolver

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        val httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager()
        resolver = ShortLinkResolver(httpInterfaceManager, maxHops = 5)
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    @Test
    fun isShortLink_detectsShortDomains() {
        assertTrue(resolver.isShortLink("https://spotify.link/abc1234"))
        assertTrue(resolver.isShortLink("https://spotify.app.link/xyz789"))
        assertTrue(resolver.isShortLink("https://sub.spotify.link/foobar"))
        assertFalse(resolver.isShortLink("https://open.spotify.com/track/1234"))
        assertFalse(resolver.isShortLink("spotify:track:1234"))
        assertFalse(resolver.isShortLink("spsearch:coldplay"))
    }

    @Test
    fun resolve_nonShortLink_returnsOriginalWithoutNetwork() {
        val original = "https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC"
        val resolved = resolver.resolve(original)
        assertEquals(original, resolved)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun resolve_singleRedirect_followsToDestination() {
        val target = "https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC"
        server.enqueue(
            MockResponse
                .Builder()
                .code(301)
                .setHeader("Location", target)
                .build(),
        )

        val testResolver =
            ShortLinkResolver(
                httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager(),
                pattern = Regex(".*"),
            )

        val resolved = testResolver.resolve(server.url("/short").toString())
        assertEquals(target, resolved)
    }

    @Test
    fun resolve_multiHopRedirect_followsUntilNonShortOrCanonical() {
        val intermediate = server.url("/hop2").toString()
        val target = "https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC"

        server.enqueue(
            MockResponse
                .Builder()
                .code(302)
                .setHeader("Location", intermediate)
                .build(),
        )
        server.enqueue(
            MockResponse
                .Builder()
                .code(302)
                .setHeader("Location", target)
                .build(),
        )

        val testResolver =
            ShortLinkResolver(
                httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager(),
                pattern = Regex(".*/(?:short|hop2).*"),
            )

        val resolved = testResolver.resolve(server.url("/short").toString())
        assertEquals(target, resolved)
    }

    @Test
    fun resolve_loopTerminatesEarly() {
        val hop1 = server.url("/hop1").toString()
        val hop2 = server.url("/hop2").toString()

        server.enqueue(
            MockResponse
                .Builder()
                .code(302)
                .setHeader("Location", hop2)
                .build(),
        )
        server.enqueue(
            MockResponse
                .Builder()
                .code(302)
                .setHeader("Location", hop1)
                .build(),
        )

        val testResolver =
            ShortLinkResolver(
                httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager(),
                pattern = Regex(".*"),
            )

        val resolved = testResolver.resolve(hop1)
        assertEquals(hop1, resolved)
    }

    @Test
    fun resolve_extractsCanonicalFromHtml() {
        val target = "https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC"
        val html = """<html><head><meta property="og:url" content="$target"/></head></html>"""

        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .setHeader("Content-Type", "text/html")
                .body(html)
                .build(),
        )

        val testResolver =
            ShortLinkResolver(
                httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager(),
                pattern = Regex(".*"),
            )

        val resolved = testResolver.resolve(server.url("/page").toString())
        assertEquals(target, resolved)
    }

    @Test
    fun resolve_bodyBeyond256KbIsNotRead() {
        val target = "https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC"
        val largePadding = "A".repeat(270 * 1024)
        val suffix = """<meta property="og:url" content="$target"/>"""
        val html = largePadding + suffix

        val landingUrl = server.url("/too-large").toString()
        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .setHeader("Content-Type", "text/html")
                .body(html)
                .build(),
        )

        val testResolver =
            ShortLinkResolver(
                httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager(),
                pattern = Regex(".*"),
            )

        val resolved = testResolver.resolve(landingUrl)
        assertEquals(landingUrl, resolved)
    }
}
