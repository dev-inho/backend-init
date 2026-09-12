package cc.midolog.jwt

import cc.midolog.util.JwtSecretValidator
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import java.util.Date
import javax.crypto.SecretKey

/**
 * JWT 토큰 발급 및 파싱 코덱.
 *
 * jjwt 의존성을 이 모듈(support:jwt)에 격리하고, 시크릿 문자열 하나로 초기화한다.
 * 초기화 시점에 [JwtSecretValidator]를 통해 시크릿을 검증한다.
 */
class JwtCodec(secret: String) {
    private val key: SecretKey = Keys.hmacShaKeyFor(JwtSecretValidator.validate(secret).toByteArray())

    /** [subject]를 담은 서명된 JWT를 발급한다. */
    fun issue(subject: String, ttlMillis: Long = 3_600_000L): String =
        Jwts.builder()
            .subject(subject)
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + ttlMillis))
            .signWith(key)
            .compact()

    /** 서명이 유효한 JWT를 파싱해 Claims를 반환한다. 검증 실패 시 [io.jsonwebtoken.JwtException] 등을 던진다. */
    fun parse(token: String): Claims =
        Jwts.parser().build().parseSignedClaims(token).payload
}
