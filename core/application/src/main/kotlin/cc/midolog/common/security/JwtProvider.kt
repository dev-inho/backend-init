package cc.midolog.common.security

import cc.midolog.util.JwtSecretValidator
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.Date
import javax.crypto.SecretKey

/**
 * HS256 JWT 발급/검증기. 시크릿은 기동 시 [JwtSecretValidator]로 검증하며,
 * 규칙(32바이트 이상, 빈 값·알려진 기본값 거부)을 위반하면 fail-fast 한다.
 */
@Component
class JwtProvider(
    @Value("\${jwt.secret}") secret: String,
) {
    private val key: SecretKey = Keys.hmacShaKeyFor(JwtSecretValidator.validate(secret).toByteArray())

    /** [subject]를 담은 서명된 JWT를 발급한다. 만료는 현재 시각 기준 [ttlMillis](기본 1시간) 후. */
    fun issue(subject: String, ttlMillis: Long = 3_600_000L): String =
        Jwts.builder()
            .subject(subject)
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + ttlMillis))
            .signWith(key)
            .compact()

    /** 서명이 유효한 JWT를 파싱해 Claims를 반환한다. 서명 검증 실패 시 [io.jsonwebtoken.JwtException]을 던진다. */
    fun parse(token: String): Claims =
        Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload
}
