package dev.waris.spotify.api.internal

import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools
import dev.waris.spotify.api.SpotifyHttp
import dev.waris.spotify.auth.AccessToken
import dev.waris.spotify.auth.TokenKind
import dev.waris.spotify.auth.TokenManager
import dev.waris.spotify.auth.TokenProvider
import dev.waris.spotify.model.Base62
import dev.waris.spotify.model.SpotifyId
import kotlin.time.Duration.Companion.hours
import kotlin.time.TimeSource
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ClientMetadataTest {
    private lateinit var server: MockWebServer

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    @Test
    fun fetchIsrc_success() {
        val json =
            """
            {
              "external_id": [
                {
                  "type": "ean",
                  "id": "1234567890"
                },
                {
                  "type": "isrc",
                  "id": "USUM71503487"
                }
              ]
            }
            """.trimIndent()

        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .setHeader("Content-Type", "application/json")
                .body(json)
                .build(),
        )

        val tokenProvider =
            object : TokenProvider {
                override val kind = TokenKind.ACCOUNT
                override val isCoolingDown = false

                override fun coolDown(duration: kotlin.time.Duration) {}

                override fun fetchToken() =
                    AccessToken(
                        value = "account_token",
                        expiresAt = TimeSource.Monotonic.markNow() + 1.hours,
                    )
            }

        val httpManager = HttpClientTools.createDefaultThreadLocalManager()
        val tokenManager = TokenManager(mapOf(TokenKind.ACCOUNT to tokenProvider))
        val http = SpotifyHttp(httpManager, tokenManager)
        val clientMetadata =
            ClientMetadata(http, baseUrl = server.url("/metadata/4/track/").toString())

        val id = SpotifyId("4eoOQgFQ0rLMKNP8KcUNWc")
        val isrc = clientMetadata.fetchIsrc(id)

        assertNotNull(isrc)
        assertEquals("USUM71503487", isrc?.value)

        val recorded = server.takeRequest()
        val expectedHex = Base62.toHex(id.value)
        assertEquals("/metadata/4/track/$expectedHex?market=from_token", recorded.target)
        assertEquals("Bearer account_token", recorded.headers["Authorization"])
    }

    @Test
    fun fetchIsrc_missingIsrcReturnsNull() {
        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .setHeader("Content-Type", "application/json")
                .body("""{"external_id": [{"type": "upc", "id": "111"}]}""")
                .build(),
        )

        val tokenProvider =
            object : TokenProvider {
                override val kind = TokenKind.ACCOUNT
                override val isCoolingDown = false

                override fun coolDown(duration: kotlin.time.Duration) {}

                override fun fetchToken() =
                    AccessToken(
                        value = "account_token",
                        expiresAt = TimeSource.Monotonic.markNow() + 1.hours,
                    )
            }

        val httpManager = HttpClientTools.createDefaultThreadLocalManager()
        val tokenManager = TokenManager(mapOf(TokenKind.ACCOUNT to tokenProvider))
        val http = SpotifyHttp(httpManager, tokenManager)
        val clientMetadata =
            ClientMetadata(http, baseUrl = server.url("/metadata/4/track/").toString())

        val isrc = clientMetadata.fetchIsrc(SpotifyId("4eoOQgFQ0rLMKNP8KcUNWc"))
        assertNull(isrc)
    }
}
