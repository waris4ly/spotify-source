package dev.waris.spotify.link

import dev.waris.spotify.model.Base62
import dev.waris.spotify.model.SpotifyId

internal sealed interface SpotifyLink {
    data class Track(
        val id: SpotifyId,
    ) : SpotifyLink {
        val uri: String get() = "https://open.spotify.com/track/$id"
    }

    data class Album(
        val id: SpotifyId,
    ) : SpotifyLink {
        val uri: String get() = "https://open.spotify.com/album/$id"
    }

    data class Playlist(
        val id: SpotifyId,
    ) : SpotifyLink {
        val uri: String get() = "https://open.spotify.com/playlist/$id"
    }

    data class Artist(
        val id: SpotifyId,
    ) : SpotifyLink {
        val uri: String get() = "https://open.spotify.com/artist/$id"
    }

    data class Episode(
        val id: SpotifyId,
    ) : SpotifyLink {
        val uri: String get() = "https://open.spotify.com/episode/$id"
    }

    data class Chapter(
        val id: SpotifyId,
    ) : SpotifyLink {
        val uri: String get() = "https://open.spotify.com/chapter/$id"
    }

    data class Show(
        val id: SpotifyId,
    ) : SpotifyLink {
        val uri: String get() = "https://open.spotify.com/show/$id"
    }

    data class Audiobook(
        val id: SpotifyId,
    ) : SpotifyLink {
        val uri: String get() = "https://open.spotify.com/audiobook/$id"
    }

    data class Search(
        val query: String,
    ) : SpotifyLink

    data class Suggest(
        val query: String,
    ) : SpotifyLink

    data class Recommendations(
        val seed: String,
    ) : SpotifyLink

    data class Similar(
        val seed: String,
    ) : SpotifyLink

    companion object {
        private const val SEARCH_PREFIX = "spsearch:"
        private const val SUGGEST_PREFIX = "spsuggest:"
        private const val REC_PREFIX = "sprec:"
        private const val SIMILAR_PREFIX = "spsimilar:"

        private val URL_PATTERN =
            Regex(
                """^(?:https?://)?(?:open\.)?spotify\.com/(?:(?:intl-[a-zA-Z0-9_-]+|embed)/)*(?:user/[^/?#]+/)?(track|album|playlist|artist|episode|show|audiobook|chapter)/([0-9a-zA-Z]+)(?:[/?#]|${'$'})""",
            )

        private val URI_PATTERN =
            Regex(
                """^spotify:(?:user:[^:]+:)?(track|album|playlist|artist|episode|show|audiobook|chapter):([0-9a-zA-Z]+)(?:[?#]|${'$'})""",
            )

        fun parse(input: String): SpotifyLink? {
            val trimmed = input.trim()
            if (trimmed.isEmpty()) {
                return null
            }

            // handle search and discovery query prefixes first
            if (trimmed.startsWith(SEARCH_PREFIX)) {
                val query = trimmed.removePrefix(SEARCH_PREFIX).trim()
                return if (query.isNotEmpty()) Search(query) else null
            }

            if (trimmed.startsWith(SUGGEST_PREFIX)) {
                val query = trimmed.removePrefix(SUGGEST_PREFIX).trim()
                return if (query.isNotEmpty()) Suggest(query) else null
            }

            if (trimmed.startsWith(REC_PREFIX)) {
                val seed = trimmed.removePrefix(REC_PREFIX).trim()
                return if (seed.isNotEmpty()) Recommendations(seed) else null
            }

            if (trimmed.startsWith(SIMILAR_PREFIX)) {
                val seed = trimmed.removePrefix(SIMILAR_PREFIX).trim()
                return if (seed.isNotEmpty()) Similar(seed) else null
            }

            // match open spotify web links or native uri formats
            val urlMatch = URL_PATTERN.find(trimmed)
            if (urlMatch != null) {
                return create(urlMatch.groupValues[1], urlMatch.groupValues[2])
            }

            val uriMatch = URI_PATTERN.find(trimmed)
            if (uriMatch != null) {
                return create(uriMatch.groupValues[1], uriMatch.groupValues[2])
            }

            return null
        }

        private fun create(
            type: String,
            rawId: String,
        ): SpotifyLink? {
            if (!Base62.isValid(rawId)) {
                return null
            }
            val id = SpotifyId(rawId)
            return when (type) {
                "track" -> Track(id)
                "album" -> Album(id)
                "playlist" -> Playlist(id)
                "artist" -> Artist(id)
                "episode" -> Episode(id)
                "chapter" -> Chapter(id)
                "show" -> Show(id)
                "audiobook" -> Audiobook(id)
                else -> null
            }
        }
    }
}
