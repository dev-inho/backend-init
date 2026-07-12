package cc.midolog.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Phase 1 재설계 이후에도 기존 util 공개 API의 시그니처와 동작이
 * 그대로 유지되는지(behavior-preserving) 고정하는 회귀 테스트.
 */
class ExistingUtilCompatibilityTest {

    @Test
    fun `IdGenerator generate keeps prefix underscore 12-hex format`() {
        val id = IdGenerator.generate("order")
        assertTrue(id.startsWith("order_"))
        val suffix = id.removePrefix("order_")
        assertEquals(12, suffix.length)
        assertTrue(suffix.all { it.isLetterOrDigit() })
    }

    @Test
    fun `SystemTimeProvider returns the injected clock instant`() {
        val fixed = Instant.parse("2026-01-01T00:00:00Z")
        val provider: TimeProvider = SystemTimeProvider(Clock.fixed(fixed, ZoneOffset.UTC))
        assertEquals(fixed, provider.now())
    }

    @Test
    fun `Validation requireNotBlank returns value and rejects blank`() {
        assertEquals("ok", Validation.requireNotBlank("ok", "field"))
        assertThrows(IllegalArgumentException::class.java) {
            Validation.requireNotBlank("  ", "field")
        }
    }

    @Test
    fun `Validation requireNotNull returns value and rejects null`() {
        assertEquals(7, Validation.requireNotNull(7, "field"))
        assertThrows(IllegalArgumentException::class.java) {
            Validation.requireNotNull<Int>(null, "field")
        }
    }

    @Test
    fun `Validation requirePositive returns value and rejects non-positive`() {
        assertEquals(3L, Validation.requirePositive(3L, "field"))
        assertThrows(IllegalArgumentException::class.java) {
            Validation.requirePositive(0L, "field")
        }
    }

    @Test
    fun `orThrow returns non-null value and throws on null`() {
        assertEquals("v", "v".orThrow { "missing" })
        assertThrows(IllegalArgumentException::class.java) {
            (null as String?).orThrow { "missing" }
        }
    }

    @Test
    fun `JwtSecretValidator still accepts a strong secret and rejects the known default`() {
        val strong = "0123456789abcdef0123456789abcdef-strong"
        assertEquals(strong, JwtSecretValidator.validate(strong))
        assertThrows(IllegalStateException::class.java) {
            JwtSecretValidator.validate("change-me-please-use-a-strong-32byte-min-secret-key-0123456789")
        }
        assertNotNull(JwtSecretValidator.MIN_BYTE_LENGTH)
    }
}
