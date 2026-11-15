package dev.waris.spotify.api.internal

import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools
import dev.waris.spotify.SpotifyOptions
import dev.waris.spotify.api.SpotifyHttp
import dev.waris.spotify.auth.AnonymousProvider
import dev.waris.spotify.auth.TokenKind
import dev.waris.spotify.auth.TokenManager
import dev.waris.spotify.model.SpotifyId
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("live")
class InternalApiLiveTest {
    @Test
    fun loadRealPlaylistLive() {
        val httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager()
        try {
            val anonymousProvider = AnonymousProvider(httpInterfaceManager)
            val tokenManager = TokenManager(mapOf(TokenKind.ANONYMOUS to anonymousProvider))
            try {
                val spotifyHttp =
                    SpotifyHttp(
                        httpInterfaceManager = httpInterfaceManager,
                        tokenManager = tokenManager,
                    )

                val api =
                    InternalApi(
                        http = spotifyHttp,
                        options = SpotifyOptions(),
                    )

                val playlistId = SpotifyId("37i9dQZF1DXcBWIGoYBM5M")
                val playlist = api.getPlaylist(playlistId, limit = 5)

                assertNotNull(playlist)
                assertTrue(playlist?.name?.startsWith("Today") == true)
                assertTrue((playlist?.totalTracks ?: 0) >= 50)
                assertTrue((playlist?.tracks?.size ?: 0) in 1..5)
                val firstTrack = playlist?.tracks?.firstOrNull()
                assertNotNull(firstTrack)
                assertTrue(firstTrack?.title?.isNotEmpty() == true)
                assertTrue(firstTrack?.author?.isNotEmpty() == true)
            } finally {
                tokenManager.close()
            }
        } finally {
            httpInterfaceManager.close()
        }
    }
}
