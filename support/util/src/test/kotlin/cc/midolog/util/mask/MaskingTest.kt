package cc.midolog.util.mask

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class MaskingTest {

    // maskEmail tests
    @Test
    fun `maskEmail should expose only first character of local part`() {
        val result = Masking.maskEmail("john.doe@example.com")
        assertEquals("j*******@example.com", result)
    }

    @Test
    fun `maskEmail should keep domain intact`() {
        val result = Masking.maskEmail("contact@my-domain.co.kr")
        assertEquals("c******@my-domain.co.kr", result)
    }

    @Test
    fun `maskEmail with single character local part`() {
        val result = Masking.maskEmail("a@example.com")
        assertEquals("a@example.com", result)
    }

    @Test
    fun `maskEmail without @ symbol masks entire string`() {
        val result = Masking.maskEmail("invalid-email-format")
        assertEquals("********************", result)
    }

    @Test
    fun `maskEmail with empty string returns empty`() {
        val result = Masking.maskEmail("")
        assertEquals("", result)
    }

    @Test
    fun `maskEmail never exposes original sensitive local part`() {
        val original = "secret.password@domain.com"
        val result = Masking.maskEmail(original)
        assertFalse(result.contains("secret"), "Local part 'secret' should not appear in result")
        assertFalse(result.contains("password"), "Local part 'password' should not appear in result")
    }

    // maskPhone tests
    @Test
    fun `maskPhone should expose only last 4 digits`() {
        val result = Masking.maskPhone("010-1234-5678")
        // Expected: *** or **** at start, 5678 at end
        assertEquals(true, result.endsWith("5678"), "Should end with last 4 digits")
    }

    @Test
    fun `maskPhone with separators preserves separator positions`() {
        val result = Masking.maskPhone("010-1234-5678")
        // Check that result has separators at original positions (approximately)
        val dashCount = result.count { it == '-' }
        assertEquals(2, dashCount, "Should preserve 2 separators")
    }

    @Test
    fun `maskPhone without separators`() {
        val result = Masking.maskPhone("01012345678")
        assertEquals(11, result.length, "Should preserve original length")
        assertEquals("5678", result.takeLast(4), "Should expose last 4 digits")
    }

    @Test
    fun `maskPhone with 4 or fewer digits masks all`() {
        val result = Masking.maskPhone("123")
        assertEquals("***", result)
    }

    @Test
    fun `maskPhone with exactly 4 digits shows all`() {
        val result = Masking.maskPhone("1234")
        assertEquals("1234", result)
    }

    @Test
    fun `maskPhone with empty string returns empty`() {
        val result = Masking.maskPhone("")
        assertEquals("", result)
    }

    @Test
    fun `maskPhone with no digits masks entire string`() {
        val result = Masking.maskPhone("---")
        assertEquals("---", result)
    }

    @Test
    fun `maskPhone never exposes original sensitive digits`() {
        val original = "010-1234-5678"
        val result = Masking.maskPhone(original)
        assertFalse(result.contains("1234"), "Middle digits should not be exposed")
    }

    // maskCard tests
    @Test
    fun `maskCard should expose only last 4 digits`() {
        val result = Masking.maskCard("1234-5678-9012-3456")
        assertEquals("3456", result.takeLast(4), "Should expose last 4 digits")
    }

    @Test
    fun `maskCard with separators preserves separator positions`() {
        val result = Masking.maskCard("1234-5678-9012-3456")
        val dashCount = result.count { it == '-' }
        assertEquals(3, dashCount, "Should preserve 3 separators")
    }

    @Test
    fun `maskCard without separators`() {
        val result = Masking.maskCard("1234567890123456")
        assertEquals(16, result.length, "Should preserve original length")
        assertEquals("3456", result.takeLast(4), "Should expose last 4 digits")
    }

    @Test
    fun `maskCard with 4 or fewer digits masks all`() {
        val result = Masking.maskCard("123")
        assertEquals("***", result)
    }

    @Test
    fun `maskCard with exactly 4 digits shows all`() {
        val result = Masking.maskCard("1234")
        assertEquals("1234", result)
    }

    @Test
    fun `maskCard with empty string returns empty`() {
        val result = Masking.maskCard("")
        assertEquals("", result)
    }

    @Test
    fun `maskCard with no digits masks entire string`() {
        val result = Masking.maskCard("----")
        assertEquals("----", result)
    }

    @Test
    fun `maskCard never exposes original sensitive card number`() {
        val original = "1234-5678-9012-3456"
        val result = Masking.maskCard(original)
        assertFalse(result.contains("1234"), "First group should not be exposed")
        assertFalse(result.contains("5678"), "Second group should not be exposed")
        assertFalse(result.contains("9012"), "Third group should not be exposed")
    }

    // Integration tests
    @Test
    fun `all functions handle various special characters safely`() {
        // Email with special chars in local part
        val email1 = Masking.maskEmail("user+tag@example.com")
        assertEquals("u*******@example.com", email1)

        // Phone with various separators
        val phone1 = Masking.maskPhone("010 1234 5678")
        assertEquals(true, phone1.endsWith("5678"))

        // Card with dots as separators
        val card1 = Masking.maskCard("1234.5678.9012.3456")
        assertEquals("3456", card1.takeLast(4))
    }

    @Test
    fun `mask functions are idempotent on already masked values`() {
        val email = "a@example.com"
        val masked1 = Masking.maskEmail(email)
        val masked2 = Masking.maskEmail(masked1)
        assertEquals(masked1, masked2, "Masking already masked value should be stable")
    }
}
