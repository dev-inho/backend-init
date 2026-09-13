package cc.midolog.jwt

import cc.midolog.util.JwtSecretValidator
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import java.time.Clock
import java.util.Date
import javax.crypto.SecretKey

/**
 * JWT 토큰 발급 및 서명 검증 코덱.
 *
 * jjwt 라이브러리 의존성을 `support:jwt` 모듈 내에 격리하고, 시크릿 문자열 하나로 초기화한다.
 * 인스턴스 생성 시점(생성자)에 [JwtSecretValidator]를 통해 시크릿을 즉시 검증(fail-fast)하여,
 * 알려진 기본값이나 32바이트 미만의 취약한 키로 애플리케이션이 동작하는 것을 방지한다.
 */
class JwtCodec(
    secret: String,
    private val clock: Clock = Clock.systemUTC()
) {
    private val key: SecretKey = Keys.hmacShaKeyFor(JwtSecretValidator.validate(secret).toByteArray())

    /**
     * 지정한 주제([subject])를 담은 서명된 JWT 문자열을 발급한다.
     *
     * 발급 시각(`iat`)은 주입된 Clock 기준으로 설정되고, 유효 기간([ttlMillis], 기본값 1시간)을 더해 만료 시각(`exp`)을 지정한 뒤
     * HMAC-SHA 키로 서명한 JWS 문자열을 생성한다.
     */
    fun issue(subject: String, ttlMillis: Long = 3_600_000L): String {
        val now = clock.instant()
        return Jwts.builder()
            .subject(subject)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusMillis(ttlMillis)))
            .signWith(key)
            .compact()
    }

    /**
     * 서명이 유효하고 만료되지 않은 JWT 토큰을 파싱하여 페이로드 [Claims]를 반환한다.
     *
     * 서명 불일치 시 [io.jsonwebtoken.security.SignatureException], 구조 훼손 시 [io.jsonwebtoken.MalformedJwtException],
     * 유효기간 만료 시 [io.jsonwebtoken.ExpiredJwtException], 빈 문자열이거나 미지원 형식인 경우
     * [IllegalArgumentException] 또는 [io.jsonwebtoken.UnsupportedJwtException] 등 [io.jsonwebtoken.JwtException] 계열 예외를 던진다.
     */
    fun parse(token: String): Claims =
        Jwts.parser()
            .verifyWith(key)
            .clock { Date.from(clock.instant()) }
            .build()
            .parseSignedClaims(token)
            .payload
}
