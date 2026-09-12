package cc.midolog.common.security

import cc.midolog.jwt.JwtCodec
import io.jsonwebtoken.Claims
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * HS256 JWT 발급/검증기.
 * 실제 암호화 및 토큰 파싱 동작은 [JwtCodec]에 위임한다.
 */
@Component
class JwtProvider(
    @Value("\${jwt.secret}") secret: String,
) {
    private val codec = JwtCodec(secret)

    /** [subject]를 담은 서명된 JWT를 발급한다. 만료는 현재 시각 기준 [ttlMillis](기본 1시간) 후. */
    fun issue(subject: String, ttlMillis: Long = 3_600_000L): String =
        codec.issue(subject, ttlMillis)

    /** 서명이 유효한 JWT를 파싱해 Claims를 반환한다. 서명 검증 실패 시 [io.jsonwebtoken.JwtException]을 던진다. */
    fun parse(token: String): Claims =
        codec.parse(token)
}
