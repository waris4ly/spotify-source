package dev.waris.spotify.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Base62Test {
    @Test
    fun isValid_validBase62Strings() {
        assertTrue(Base62.isValid("4eoOQgFQ0rLMKNP8KcUNWc"))
        assertTrue(Base62.isValid("0"))
        assertTrue(Base62.isValid("Z"))
        assertTrue(Base62.isValid("abc123XYZ"))
    }

    @Test
    fun isValid_emptyString() {
        assertFalse(Base62.isValid(""))
    }

    @Test
    fun isValid_invalidCharacters() {
        assertFalse(Base62.isValid("abc!123"))
        assertFalse(Base62.isValid("abc-123"))
        assertFalse(Base62.isValid("abc 123"))
        assertFalse(Base62.isValid("4eoOQgFQ0rLMKNP8KcUNWc/"))
    }

    @Test
    fun toHex_validString() {
        val hex = Base62.toHex("4eoOQgFQ0rLMKNP8KcUNWc")
        assertEquals(32, hex.length)
        assertTrue(hex.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun toHex_invalidString() {
        assertEquals("", Base62.toHex("invalid!"))
    }
}
