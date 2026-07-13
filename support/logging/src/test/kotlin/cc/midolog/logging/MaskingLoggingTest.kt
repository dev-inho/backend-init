package cc.midolog.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.LoggingEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

class MaskingLoggingTest {

    @Test
    fun `LoggingMdc REQUEST_ID is the standard X-Request-Id key`() {
        assertEquals("X-Request-Id", LoggingMdc.REQUEST_ID)
    }

    @Test
    fun `maskText masks an email embedded in a log message`() {
        val message = "user login failed for secret.user@example.com"
        val result = MaskingSupport.maskText(message)

        assertFalse(result.contains("secret.user@example.com"), "plaintext email must not leak")
        assertTrue(result.contains("@example.com"), "domain should remain for diagnosability")
    }

    @Test
    fun `maskText masks a domestic phone number embedded in a log message`() {
        val message = "contact number is 010-1234-5678 for this order"
        val result = MaskingSupport.maskText(message)

        assertFalse(result.contains("010-1234-5678"), "plaintext phone must not leak")
        assertTrue(result.contains("5678"), "last 4 digits should remain for diagnosability")
    }

    @Test
    fun `maskText masks a card number embedded in a log message`() {
        val message = "payment attempted with card 1234-5678-9012-3456"
        val result = MaskingSupport.maskText(message)

        assertFalse(result.contains("1234-5678-9012-3456"), "plaintext card must not leak")
        assertTrue(result.contains("3456"), "last 4 digits should remain for diagnosability")
    }

    @Test
    fun `maskText masks a card number without separators`() {
        val message = "card number 1234567890123456 charged"
        val result = MaskingSupport.maskText(message)

        assertFalse(result.contains("1234567890123456"), "plaintext card must not leak")
        assertTrue(result.contains("3456"), "last 4 digits should remain for diagnosability")
    }

    @Test
    fun `maskText masks multiple PII candidates in a single message`() {
        val message = "user secret.user@example.com called from 010-1234-5678, card 1234-5678-9012-3456"
        val result = MaskingSupport.maskText(message)

        assertFalse(result.contains("secret.user@example.com"))
        assertFalse(result.contains("010-1234-5678"))
        assertFalse(result.contains("1234-5678-9012-3456"))
    }

    @Test
    fun `maskText masks card numbers with dot slash and space separators and Amex grouping`() {
        val cards = listOf(
            "1234.5678.9012.3456",
            "1234/5678/9012/3456",
            "1234 5678 9012 3456",
            "3782 822463 10005",
        )
        for (raw in cards) {
            val result = MaskingSupport.maskText("payment with card $raw done")
            assertFalse(result.contains(raw), "plaintext card must not leak for input: $raw")
        }
    }

    @Test
    fun `maskText masks phone numbers with space dot and international formats`() {
        val phones = listOf(
            "010 1234 5678",
            "010.1234.5678",
            "+82-10-1234-5678",
        )
        for (raw in phones) {
            val result = MaskingSupport.maskText("call $raw please")
            assertFalse(result.contains(raw), "plaintext phone must not leak for input: $raw")
        }
    }

    @Test
    fun `maskText leaves plain text without PII untouched`() {
        val message = "gateway started successfully on port 8080"
        val result = MaskingSupport.maskText(message)

        assertEquals(message, result)
    }

    @Test
    fun `MaskingMessageConverter formats event message through MaskingSupport`() {
        val converter = MaskingMessageConverter()
        val logger = LoggerFactory.getLogger(MaskingLoggingTest::class.java) as Logger
        val event = LoggingEvent(
            MaskingLoggingTest::class.java.name,
            logger,
            Level.INFO,
            "email is secret.user@example.com",
            null,
            null,
        )

        val result = converter.convert(event)

        assertFalse(result.contains("secret.user@example.com"))
    }
}
