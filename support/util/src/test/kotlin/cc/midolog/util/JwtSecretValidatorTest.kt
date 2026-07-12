package cc.midolog.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class JwtSecretValidatorTest {

    @Test
    fun `rejects secret shorter than 32 UTF-8 bytes`() {
        assertThrows(IllegalStateException::class.java) {
            JwtSecretValidator.validate("too-short-secret")
        }
    }

    @Test
    fun `rejects blank secret`() {
        assertThrows(IllegalStateException::class.java) {
            JwtSecretValidator.validate("   ")
        }
    }

    @Test
    fun `rejects known default secret`() {
        assertThrows(IllegalStateException::class.java) {
            JwtSecretValidator.validate("change-me-please-use-a-strong-32byte-min-secret-key-0123456789")
        }
    }

    @Test
    fun `accepts a random secret with at least 32 UTF-8 bytes`() {
        val strongSecret = "0123456789abcdef0123456789abcdef-strong-random"
        assertEquals(strongSecret, JwtSecretValidator.validate(strongSecret))
    }
}
