package dev.waris.spotify.plugin

import com.github.topi314.lavalyrics.api.LyricsPluginInfoModifier
import com.github.topi314.lavalyrics.lyrics.AudioLyrics
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.springframework.stereotype.Service

@Service
public class LyricsPluginInfo : LyricsPluginInfoModifier {
    override fun modifyAudioLyricsPluginInfo(text: AudioLyrics): JsonObject? {
        if (text !is SpotifyAudioLyrics) {
            return null
        }
        return buildJsonObject {
            put("syncType", text.sync.name.lowercase())
            put("language", text.language)
            put("rightToLeft", text.rightToLeft)
        }
    }
}
