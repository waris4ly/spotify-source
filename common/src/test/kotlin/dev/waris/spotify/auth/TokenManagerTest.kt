package dev.waris.spotify.auth

import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools
import dev.waris.spotify.error.SpotifyException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TokenManagerTest {
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
    fun token_refreshesWhenEmpty() {
        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .setHeader("Content-Type", "application/json")
                .body("""{"access_token": "token-initial", "expires_in": 3600}""")
                .build(),
        )

        val provider =
            ClientCredentialsProvider(
                clientId = "test-client-id",
                clientSecret = "test-client-secret",
                httpInterfaceManager = httpInterfaceManager,
                tokenUrl = server.url("/api/token").toString(),
            )
        val manager = TokenManager(mapOf(TokenKind.CLIENT to provider))

        val token1 = manager.getToken(TokenKind.CLIENT)
        assertEquals("token-initial", token1.value)

        val token2 = manager.getToken(TokenKind.CLIENT)
        assertEquals("token-initial", token2.value)
        assertEquals(1, server.requestCount)
        manager.close()
    }

    @Test
    fun token_refreshesBeforeExpiry() {
        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .setHeader("Content-Type", "application/json")
                .body("""{"access_token": "token-fresh", "expires_in": 3600}""")
                .build(),
        )

        val provider =
            ClientCredentialsProvider(
                clientId = "test-client-id",
                clientSecret = "test-client-secret",
                httpInterfaceManager = httpInterfaceManager,
                tokenUrl = server.url("/api/token").toString(),
            )
        val manager =
            TokenManager(
                providers = mapOf(TokenKind.CLIENT to provider),
                refreshMargin = 5.minutes,
            )

        val expiringSoon =
            AccessToken(
                value = "token-old",
                expiresAt = TimeSource.Monotonic.markNow() + 3.minutes,
                expiresInSeconds = 180,
            )
        manager.setToken(TokenKind.CLIENT, expiringSoon)

        val token = manager.getToken(TokenKind.CLIENT)
        assertEquals("token-fresh", token.value)
        assertEquals(1, server.requestCount)
        manager.close()
    }

    @Test
    fun token_concurrentCallersShareSingleRefresh() {
        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .setHeader("Content-Type", "application/json")
                .body("""{"access_token": "token-shared", "expires_in": 3600}""")
                .build(),
        )

        val provider =
            ClientCredentialsProvider(
                clientId = "test-client-id",
                clientSecret = "test-client-secret",
                httpInterfaceManager = httpInterfaceManager,
                tokenUrl = server.url("/api/token").toString(),
            )
        val manager = TokenManager(mapOf(TokenKind.CLIENT to provider))

        val threadCount = 10
        val executor = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        val tokens = Array(threadCount) { "" }

        for (i in 0 until threadCount) {
            executor.submit {
                startLatch.await()
                try {
                    tokens[i] = manager.getToken(TokenKind.CLIENT).value
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        startLatch.countDown()
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS))
        executor.shutdown()

        assertEquals(1, server.requestCount)
        for (token in tokens) {
            assertEquals("token-shared", token)
        }
        manager.close()
    }

    @Test
    fun token_coolDownRejectsUntilExpired() {
        val provider =
            ClientCredentialsProvider(
                clientId = "test-client-id",
                clientSecret = "test-client-secret",
                httpInterfaceManager = httpInterfaceManager,
                tokenUrl = server.url("/api/token").toString(),
            )
        val manager = TokenManager(mapOf(TokenKind.CLIENT to provider))

        assertFalse(manager.isCoolingDown(TokenKind.CLIENT))
        manager.coolDown(TokenKind.CLIENT, 10.minutes)
        assertTrue(manager.isCoolingDown(TokenKind.CLIENT))

        val ex =
            assertThrows(SpotifyException.RateLimit::class.java) {
                manager.getToken(TokenKind.CLIENT)
            }
        assertTrue(ex.isQuotaExceeded)
        manager.close()
    }
}
