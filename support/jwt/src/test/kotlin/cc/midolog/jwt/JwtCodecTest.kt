package cc.midolog.jwt

import io.jsonwebtoken.JwtException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class JwtCodecTest {

    private val secret = "12345678901234567890123456789012"
    private val otherSecret = "abcdefghijklmnopqrstuvwxyz123456"

    @Test
    fun `정상 토큰은 subject를 일치하게 반환한다`() {
        val codec = JwtCodec(secret)
        val token = codec.issue("user123", 10000L)
        val claims = codec.parse(token)
        
        assertEquals("user123", claims.subject)
    }

    @Test
    fun `다른 시크릿으로 서명한 토큰은 예외가 발생한다`() {
        val codec1 = JwtCodec(secret)
        val codec2 = JwtCodec(otherSecret)
        
        val token = codec1.issue("user123", 10000L)
        
        assertThrows<JwtException> {
            codec2.parse(token)
        }
    }

    @Test
    fun `만료된 토큰은 예외가 발생한다`() {
        val codec = JwtCodec(secret)
        val token = codec.issue("user123", -10000L)
        
        assertThrows<JwtException> {
            codec.parse(token)
        }
    }
}
