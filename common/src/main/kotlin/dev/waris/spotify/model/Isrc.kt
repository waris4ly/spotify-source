package dev.waris.spotify.model

@JvmInline
internal value class Isrc(
    val value: String,
) {
    val normalized: String
        get() = value.replace("-", "").uppercase()

    override fun toString(): String = value
}
