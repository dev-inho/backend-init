package cc.midolog.common.security

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.Date
import javax.crypto.SecretKey

/** HS256 JWT 발급기. 시크릿은 32바이트 이상이어야 한다. */
@Component
class JwtProvider(
    @Value("\${jwt.secret}") secret: String,
) {
    private val key: SecretKey = Keys.hmacShaKeyFor(secret.toByteArray())

    fun issue(subject: String, ttlMillis: Long = 3_600_000L): String =
        Jwts.builder()
            .subject(subject)
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + ttlMillis))
            .signWith(key)
            .compact()
}
