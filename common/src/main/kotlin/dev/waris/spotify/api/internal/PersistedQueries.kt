package dev.waris.spotify.api.internal

internal class PersistedQueries(
    private val overrides: Map<String, String> = emptyMap(),
) {
    fun hashFor(operationName: String): String =
        overrides[operationName]
            ?: DEFAULT_HASHES[operationName]
            ?: error("Unknown query operation: $operationName")

    companion object {
        const val FETCH_PLAYLIST: String = "fetchPlaylist"
        const val GET_ALBUM: String = "getAlbum"
        const val GET_TRACK: String = "getTrack"
        const val QUERY_ARTIST_OVERVIEW: String = "queryArtistOverview"
        const val SEARCH_DESKTOP: String = "searchDesktop"
        const val GET_EPISODE_OR_CHAPTER: String = "getEpisodeOrChapter"
        const val QUERY_PODCAST_EPISODES: String = "queryPodcastEpisodes"
        const val QUERY_BOOK_CHAPTERS: String = "queryBookChapters"
        const val SEARCH_SUGGESTIONS: String = "searchSuggestions"
        const val GET_RECOMMENDATIONS: String = "internalLinkRecommenderTrack"
        const val SIMILAR_ALBUMS: String = "similarAlbumsBasedOnThisTrack"
        const val FETCH_EXTRACTED_COLORS: String = "fetchExtractedColors"

        private val DEFAULT_HASHES =
            mapOf(
                FETCH_PLAYLIST to
                    "e4b2953f160e58e38ac025d79b5a9b3aceee5c4c716598e9830bfceb69faff5f",
                GET_ALBUM to
                    "b9bfabef66ed756e5e13f68a942deb60bd4125ec1f1be8cc42769dc0259b4b10",
                GET_TRACK to
                    "612585ae06ba435ad26369870deaae23b5c8800a256cd8a57e08eddc25a37294",
                QUERY_ARTIST_OVERVIEW to
                    "ae0e2958a4ab645b35ca19ac04d0495ae12d9c5d7b7286217674801a9aab281a",
                SEARCH_DESKTOP to
                    "fcad5a3e0d5af727fb76966f06971c19cfa2275e6ff7671196753e008611873c",
                GET_EPISODE_OR_CHAPTER to
                    "5f77db47e5a2ce6680330bb61317aa09e17ddd1d189513cb218e7cd3e43288be",
                QUERY_PODCAST_EPISODES to
                    "3539d746cf882f3909660de40b4ef472b3f5893a0761d617c282541de5d412c5",
                QUERY_BOOK_CHAPTERS to
                    "8f342d1c624755901657fa65cbb80dd3bacbcca2f6d802f570ae3269d59a403e",
                SEARCH_SUGGESTIONS to
                    "f244254b94c0e824d458ac216e0f8406a73f2f69a9ff52831a2f78fa774ff6ce",
                GET_RECOMMENDATIONS to
                    "c77098ee9d6ee8ad3eb844938722db60570d040b49f41f5ec6e7be9160a7c86b",
                SIMILAR_ALBUMS to
                    "1d1f93a737498adca2c892c73af87fc0b052afe4e1a33c989540c32413dfae17",
                FETCH_EXTRACTED_COLORS to
                    "36e90fcaea00d47c695fce31874efeb2519b97d4cd0ee1abfb4f8dc9348596ea",
            )
    }
}
