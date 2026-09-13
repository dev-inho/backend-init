package cc.midolog.jwt

import io.jsonwebtoken.ExpiredJwtException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Date

class JwtCodecClockTest {

    private val secret = "12345678901234567890123456789012"
    private val t0Instant = Instant.parse("2026-09-13T10:00:00Z")
    private val zone = ZoneOffset.UTC

    @Test
    fun `fixed clock determines iat and exp deterministically`() {
        val clock = Clock.fixed(t0Instant, zone)
        val codec = JwtCodec(secret, clock)
        val ttlMillis = 60_000L

        val token = codec.issue("user123", ttlMillis)
        val claims = codec.parse(token)

        val expectedIat = Date.from(t0Instant)
        val expectedExp = Date.from(t0Instant.plusSeconds(60))

        assertEquals(expectedIat, claims.issuedAt)
        assertEquals(expectedExp, claims.expiration)
    }

    @Test
    fun `token parsed with clock before expiration succeeds and after expiration fails with ExpiredJwtException`() {
        val clockT0 = Clock.fixed(t0Instant, zone)
        val codecIssue = JwtCodec(secret, clockT0)
        val ttlMillis = 60_000L
        val token = codecIssue.issue("user123", ttlMillis)

        val codecBeforeExp = JwtCodec(secret, Clock.fixed(t0Instant.plusSeconds(59), zone))
        assertDoesNotThrow {
            val claims = codecBeforeExp.parse(token)
            assertEquals("user123", claims.subject)
        }

        val codecAfterExp = JwtCodec(secret, Clock.fixed(t0Instant.plusSeconds(61), zone))
        assertThrows<ExpiredJwtException> {
            codecAfterExp.parse(token)
        }
    }
}
