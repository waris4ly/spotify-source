package dev.waris.spotify.model

@JvmInline
internal value class SpotifyId(
    val value: String,
) {
    init {
        require(Base62.isValid(value)) {
            "invalid spotify id: $value"
        }
    }

    override fun toString(): String = value
}
