package dev.waris.spotify

import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.player.event.TrackExceptionEvent
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioReference
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import dev.lavalink.youtube.YoutubeAudioSourceManager
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
import java.util.concurrent.atomic.AtomicReference
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
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
                AndroidMusic(),
                Ios(),
                Tv(),
                WebEmbedded(),
                MWeb(),
                Music(),
                Web(),
                AndroidVr(),
            )
        val youtubeOauth = System.getenv("YOUTUBE_REFRESH_TOKEN").orEmpty()
        if (youtubeOauth.isNotBlank()) {
            youtubeSource.useOauth2(youtubeOauth, true)
        }
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
        val failure = AtomicReference<Throwable?>(null)
        player.addListener { event ->
            if (event is TrackExceptionEvent) {
                failure.set(event.exception)
            }
        }
        player.playTrack(track)

        var framesReceived = 0
        val deadline = System.currentTimeMillis() + 25_000L
        while (System.currentTimeMillis() < deadline && framesReceived < 100) {
            if (failure.get() != null) {
                break
            }
            val frame = player.provide(100, TimeUnit.MILLISECONDS)
            if (frame != null && frame.data != null && frame.data.isNotEmpty()) {
                framesReceived++
            }
        }

        val error = failure.get()
        if (error != null) {
            val msg = error.message.orEmpty()
            val causeMsg = error.cause?.message.orEmpty()
            val isBotBlock =
                msg.contains("bot", ignoreCase = true) ||
                    msg.contains("login", ignoreCase = true) ||
                    msg.contains("All clients failed", ignoreCase = true) ||
                    causeMsg.contains("bot", ignoreCase = true) ||
                    causeMsg.contains("login", ignoreCase = true) ||
                    causeMsg.contains("All clients failed", ignoreCase = true) ||
                    error.cause
                        ?.javaClass
                        ?.name
                        ?.contains("AllClientsFailedException") == true

            Assumptions.assumeFalse(
                isBotBlock,
                "YouTube mirror blocked runner IP ($msg)",
            )
            throw error
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
