package dev.waris.spotify.api

import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools
import dev.waris.spotify.auth.AccessToken
import dev.waris.spotify.auth.ClientCredentialsProvider
import dev.waris.spotify.auth.TokenKind
import dev.waris.spotify.auth.TokenManager
import dev.waris.spotify.error.SpotifyException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.apache.http.client.methods.HttpGet
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SpotifyHttpTest {
    private lateinit var server: MockWebServer
    private lateinit var httpInterfaceManager:
        com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager()
    }

    @AfterEach
    fun tearDown() {
        server.close()
        httpInterfaceManager.close()
    }

    @Test
    fun execute_401RetriesWithRefreshedToken() {
        server.enqueue(
            MockResponse
                .Builder()
                .code(401)
                .setHeader("Content-Type", "application/json")
                .body("""{"error": {"message": "Invalid token"}}""")
                .build(),
        )
        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .setHeader("Content-Type", "application/json")
                .body("""{"access_token": "token-second", "expires_in": 3600}""")
                .build(),
        )
        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .setHeader("Content-Type", "application/json")
                .body("""{"id": "track-1", "name": "ocean eyes"}""")
                .build(),
        )

        val provider =
            ClientCredentialsProvider(
                clientId = "test-client",
                clientSecret = "test-secret",
                httpInterfaceManager = httpInterfaceManager,
                tokenUrl = server.url("/api/token").toString(),
            )
        val tokenManager = TokenManager(mapOf(TokenKind.CLIENT to provider))
        tokenManager.setToken(
            TokenKind.CLIENT,
            AccessToken("token-first", TimeSource.Monotonic.markNow() + 1.hours),
        )

        val http =
            SpotifyHttp(
                httpInterfaceManager = httpInterfaceManager,
                tokenManager = tokenManager,
            )

        val result = http.execute(HttpGet(server.url("/v1/tracks/1").toString()), TokenKind.CLIENT)
        assertEquals("track-1", result.get("id").text())
        assertEquals("ocean eyes", result.get("name").text())
        tokenManager.close()
    }

    @Test
    fun execute_429WaitsAndRetriesWhenBelowCap() {
        server.enqueue(
            MockResponse
                .Builder()
                .code(429)
                .setHeader("Retry-After", "1")
                .setHeader("Content-Type", "application/json")
                .body("""{"error": {"message": "Rate limit exceeded"}}""")
                .build(),
        )
        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .setHeader("Content-Type", "application/json")
                .body("""{"id": "item-ok"}""")
                .build(),
        )

        val tokenManager = TokenManager(emptyMap())
        var sleptDuration: Duration? = null
        val http =
            SpotifyHttp(
                httpInterfaceManager = httpInterfaceManager,
                tokenManager = tokenManager,
                sleeper = { sleptDuration = it },
            )

        val result = http.execute(HttpGet(server.url("/v1/items").toString()), tokenKind = null)
        assertEquals("item-ok", result.get("id").text())
        assertEquals(1.seconds, sleptDuration)
        tokenManager.close()
    }

    @Test
    fun execute_429FailsFastWhenExceedingCap() {
        server.enqueue(
            MockResponse
                .Builder()
                .code(429)
                .setHeader("Retry-After", "30")
                .setHeader("Content-Type", "application/json")
                .body("""{"error": {"message": "Too many requests"}}""")
                .build(),
        )

        val tokenManager = TokenManager(emptyMap())
        val slept = AtomicBoolean(false)
        val http =
            SpotifyHttp(
                httpInterfaceManager = httpInterfaceManager,
                tokenManager = tokenManager,
                sleeper = { slept.set(true) },
            )

        val ex =
            assertThrows(SpotifyException.RateLimit::class.java) {
                http.execute(HttpGet(server.url("/v1/items").toString()), tokenKind = null)
            }
        assertEquals(30L, ex.retryAfterSeconds)
        assertFalse(ex.isQuotaExceeded)
        assertFalse(slept.get())
        tokenManager.close()
    }

    @Test
    fun execute_429WithRetryAfter5ThrowsRateLimitWithoutSleeping() {
        server.enqueue(
            MockResponse
                .Builder()
                .code(429)
                .setHeader("Retry-After", "5")
                .setHeader("Content-Type", "application/json")
                .body("""{"error": {"message": "Rate limit exceeded"}}""")
                .build(),
        )

        val tokenManager = TokenManager(emptyMap())
        val slept = AtomicBoolean(false)
        val http =
            SpotifyHttp(
                httpInterfaceManager = httpInterfaceManager,
                tokenManager = tokenManager,
                sleeper = { slept.set(true) },
            )

        val ex =
            assertThrows(SpotifyException.RateLimit::class.java) {
                http.execute(HttpGet(server.url("/v1/items").toString()), tokenKind = null)
            }
        assertEquals(5L, ex.retryAfterSeconds)
        assertFalse(ex.isQuotaExceeded)
        assertFalse(slept.get())
        tokenManager.close()
    }

    @Test
    fun execute_429RetriesOnlyOnceByDefault() {
        server.enqueue(
            MockResponse
                .Builder()
                .code(429)
                .setHeader("Retry-After", "1")
                .setHeader("Content-Type", "application/json")
                .body("""{"error": {"message": "Rate limit 1"}}""")
                .build(),
        )
        server.enqueue(
            MockResponse
                .Builder()
                .code(429)
                .setHeader("Retry-After", "1")
                .setHeader("Content-Type", "application/json")
                .body("""{"error": {"message": "Rate limit 2"}}""")
                .build(),
        )

        val tokenManager = TokenManager(emptyMap())
        val sleepCount = AtomicInteger(0)
        val http =
            SpotifyHttp(
                httpInterfaceManager = httpInterfaceManager,
                tokenManager = tokenManager,
                sleeper = { sleepCount.incrementAndGet() },
            )

        val ex =
            assertThrows(SpotifyException.RateLimit::class.java) {
                http.execute(HttpGet(server.url("/v1/items").toString()), tokenKind = null)
            }
        assertEquals(1L, ex.retryAfterSeconds)
        assertEquals(1, sleepCount.get())
        assertEquals(2, server.requestCount)
        tokenManager.close()
    }

    @Test
    fun execute_quotaExceededCoolsDownBackendImmediately() {
        server.enqueue(
            MockResponse
                .Builder()
                .code(429)
                .setHeader("Content-Type", "application/json")
                .body("""{"error": {"message": "Quota exhausted", "reason": "QUOTA_EXCEEDED"}}""")
                .build(),
        )

        val provider =
            ClientCredentialsProvider(
                clientId = "test-client",
                clientSecret = "test-secret",
                httpInterfaceManager = httpInterfaceManager,
                tokenUrl = server.url("/api/token").toString(),
            )
        val tokenManager = TokenManager(mapOf(TokenKind.CLIENT to provider))
        tokenManager.setToken(
            TokenKind.CLIENT,
            AccessToken("token-initial", TimeSource.Monotonic.markNow() + 1.hours),
        )

        val slept = AtomicBoolean(false)
        val http =
            SpotifyHttp(
                httpInterfaceManager = httpInterfaceManager,
                tokenManager = tokenManager,
                sleeper = { slept.set(true) },
            )

        val ex =
            assertThrows(SpotifyException.RateLimit::class.java) {
                http.execute(HttpGet(server.url("/v1/quota").toString()), TokenKind.CLIENT)
            }
        assertTrue(ex.isQuotaExceeded)
        assertFalse(slept.get())
        assertTrue(tokenManager.isCoolingDown(TokenKind.CLIENT))
        tokenManager.close()
    }

    @Test
    fun execute_403DoesNotRetry() {
        server.enqueue(
            MockResponse
                .Builder()
                .code(403)
                .setHeader("Content-Type", "application/json")
                .body("""{"error": {"message": "Forbidden"}}""")
                .build(),
        )

        val tokenManager = TokenManager(emptyMap())
        val http =
            SpotifyHttp(
                httpInterfaceManager = httpInterfaceManager,
                tokenManager = tokenManager,
            )

        assertThrows(SpotifyException::class.java) {
            http.execute(HttpGet(server.url("/v1/forbidden").toString()), tokenKind = null)
        }
        assertEquals(1, server.requestCount)
        tokenManager.close()
    }

    @Test
    fun execute_404ReturnsEmptyJson() {
        server.enqueue(
            MockResponse
                .Builder()
                .code(404)
                .setHeader("Content-Type", "application/json")
                .body("""{"error": {"message": "Not found"}}""")
                .build(),
        )

        val tokenManager = TokenManager(emptyMap())
        val http =
            SpotifyHttp(
                httpInterfaceManager = httpInterfaceManager,
                tokenManager = tokenManager,
            )

        val result = http.execute(HttpGet(server.url("/v1/notfound").toString()), tokenKind = null)
        assertTrue(result.isNull)
        tokenManager.close()
    }

    @Test
    fun execute_500RetriesOnce() {
        server.enqueue(
            MockResponse
                .Builder()
                .code(500)
                .setHeader("Content-Type", "application/json")
                .body("""{"error": {"message": "Internal server error"}}""")
                .build(),
        )
        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .setHeader("Content-Type", "application/json")
                .body("""{"status": "recovered"}""")
                .build(),
        )

        val tokenManager = TokenManager(emptyMap())
        val sleepCount = AtomicInteger(0)
        val http =
            SpotifyHttp(
                httpInterfaceManager = httpInterfaceManager,
                tokenManager = tokenManager,
                sleeper = { sleepCount.incrementAndGet() },
            )

        val result = http.execute(HttpGet(server.url("/v1/flaky").toString()), tokenKind = null)
        assertEquals("recovered", result.get("status").text())
        assertEquals(1, sleepCount.get())
        assertEquals(2, server.requestCount)
        tokenManager.close()
    }

    @Test
    fun execute_manySequentialRequestsCompleteWithoutPoolExhaustion() {
        val totalRequests = 50
        for (i in 1..totalRequests) {
            val code = if (i % 2 == 0) 200 else 404
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(code)
                    .setHeader("Content-Type", "application/json")
                    .body(if (code == 200) """{"id": "$i"}""" else """{"error": "not found"}""")
                    .build(),
            )
        }

        val tokenManager = TokenManager(emptyMap())
        val http =
            SpotifyHttp(
                httpInterfaceManager = httpInterfaceManager,
                tokenManager = tokenManager,
            )

        for (i in 1..totalRequests) {
            val result =
                http.execute(HttpGet(server.url("/v1/item/$i").toString()), tokenKind = null)
            if (i % 2 == 0) {
                assertEquals(i.toString(), result.get("id").text())
            } else {
                assertTrue(result.isNull)
            }
        }
        tokenManager.close()
    }
}
