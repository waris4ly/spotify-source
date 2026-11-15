package dev.waris.spotify

import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioReference
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import dev.lavalink.youtube.YoutubeAudioSourceManager
import dev.lavalink.youtube.clients.Android
import dev.lavalink.youtube.clients.AndroidMusic
import dev.lavalink.youtube.clients.AndroidVr
import dev.lavalink.youtube.clients.Ios
import dev.lavalink.youtube.clients.MWeb
import dev.lavalink.youtube.clients.Music
import dev.lavalink.youtube.clients.Tv
import dev.lavalink.youtube.clients.TvHtml5Simply
import dev.lavalink.youtube.clients.Web
import dev.lavalink.youtube.clients.WebEmbedded
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("live")
class SpotifyLiveSmokeTest {
    private lateinit var playerManager: DefaultAudioPlayerManager
    private lateinit var spotifySource: SpotifyAudioSourceManager
    private lateinit var youtubeSource: YoutubeAudioSourceManager

    @BeforeEach
    fun setUp() {
        val clientId = System.getenv("SPOTIFY_CLIENT_ID") ?: ""
        val clientSecret = System.getenv("SPOTIFY_CLIENT_SECRET") ?: ""

        val options =
            SpotifyOptions(
                clientId = clientId,
                clientSecret = clientSecret,
                internalBackend = true,
                extractColors = true,
                countryCode = "US",
                mirrorProviders =
                    listOf(
                        """ytmsearch:"%isrc%"""",
                        "ytmsearch:%query%",
                        """ytsearch:"%isrc%"""",
                        "ytsearch:%query%",
                    ),
            )

        playerManager = DefaultAudioPlayerManager()
        youtubeSource =
            YoutubeAudioSourceManager(
                TvHtml5Simply(),
                Android(),
                AndroidMusic(),
                Ios(),
                Tv(),
                WebEmbedded(),
                MWeb(),
                Music(),
                Web(),
                AndroidVr(),
            )
        spotifySource = SpotifyAudioSourceManager(options)

        playerManager.registerSourceManager(spotifySource)
        playerManager.registerSourceManager(youtubeSource)
    }

    @AfterEach
    fun tearDown() {
        playerManager.shutdown()
    }

    @Test
    fun liveSmoke_loadsAndPlaysTrackFrames() {
        val trackLink = "https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT"
        val item = playerManager.loadItemSync(AudioReference(trackLink, null))

        assertNotNull(item)
        assertTrue(item is AudioTrack)
        val track = item as AudioTrack

        val player = playerManager.createPlayer()
        player.playTrack(track)

        var framesReceived = 0
        val deadline = System.currentTimeMillis() + 25_000L
        while (System.currentTimeMillis() < deadline && framesReceived < 100) {
            val frame = player.provide(100, TimeUnit.MILLISECONDS)
            if (frame != null && frame.data != null && frame.data.isNotEmpty()) {
                framesReceived++
            }
        }

        assertTrue(
            framesReceived >= 50,
            "expected at least 50 frames (~1s audio), received $framesReceived",
        )
    }

    @Test
    fun liveSmoke_loadsPublicPlaylist() {
        val playlistLink = "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M"
        val item = playerManager.loadItemSync(AudioReference(playlistLink, null))

        assertNotNull(item)
        assertTrue(item is AudioPlaylist)
        val playlist = item as AudioPlaylist
        assertTrue(playlist.tracks.isNotEmpty())
    }

    @Test
    fun liveSmoke_loadsSearchSuggestions() {
        val item = playerManager.loadItemSync(AudioReference("spsuggest:queen", null))
        assertNotNull(item)
        assertTrue(item is AudioPlaylist)
        val playlist = item as AudioPlaylist
        assertTrue(playlist.tracks.isNotEmpty())
    }

    @Test
    fun liveSmoke_loadsRecommendations() {
        val item =
            playerManager.loadItemSync(
                AudioReference("sprec:4cOdK2wGLETKBW3PvgPWqT", null),
            )
        assertNotNull(item)
        assertTrue(item is AudioPlaylist)
        val playlist = item as AudioPlaylist
        assertTrue(playlist.tracks.isNotEmpty())
    }

    @Test
    fun liveSmoke_loadsPublicEpisode() {
        val episodeLink = "https://open.spotify.com/episode/5e9srgDiD2PkHztkuuMjQF"
        val item = playerManager.loadItemSync(AudioReference(episodeLink, null))
        assertNotNull(item)
        assertTrue(item is AudioTrack)
    }

    @Test
    fun liveSmoke_extractsArtworkColors() {
        val colors =
            spotifySource.artworkColors?.fetchColors(
                "https://i.scdn.co/image/ab67616d0000b2735755e164993798e0c9ef7d7a",
            )
        assertNotNull(colors)
    }
}
