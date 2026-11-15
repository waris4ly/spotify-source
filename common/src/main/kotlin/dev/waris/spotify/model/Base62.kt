package dev.waris.spotify.model

import java.math.BigInteger

internal object Base62 {
    private const val ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private val BASE = BigInteger.valueOf(62)

    fun isValid(value: String): Boolean {
        if (value.isEmpty()) {
            return false
        }
        for (element in value) {
            if (element !in '0'..'9' && element !in 'a'..'z' && element !in 'A'..'Z') {
                return false
            }
        }
        return true
    }

    fun toHex(id: String): String {
        if (!isValid(id)) {
            return ""
        }
        var number = BigInteger.ZERO
        for (element in id) {
            val digit = ALPHABET.indexOf(element)
            if (digit < 0) {
                return ""
            }
            number = number.multiply(BASE).add(BigInteger.valueOf(digit.toLong()))
        }
        return number.toString(16).padStart(32, '0')
    }
}
