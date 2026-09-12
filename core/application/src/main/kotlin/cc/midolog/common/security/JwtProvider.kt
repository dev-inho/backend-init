package cc.midolog.common.security

import cc.midolog.jwt.JwtCodec
import io.jsonwebtoken.Claims
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * 애플리케이션 계층의 HS256 JWT 발급 및 서명 검증 컴포넌트.
 *
 * 토큰 서명 생성 및 파싱 로직을 직접 구현하지 않고 support:jwt 모듈의 [JwtCodec]에 전적으로 위임한다.
 * 이는 core:gateway 등 다른 시스템 컴포넌트와 중복 구현 없이 동일한 보안 규격과 파싱 규칙을 일관되게 공유하기 위함이다.
 */
@Component
class JwtProvider(
    @Value("\${jwt.secret}") secret: String,
) {
    private val codec = JwtCodec(secret)

    /**
     * 지정한 주체 식별자(subject)를 담아 서명된 JWT 문자열을 발급한다.
     *
     * 유효 시간(ttlMillis)을 지정하지 않으면 발급 시점 기준 기본값인 1시간(3,600,000ms) 후 만료된다.
     */
    fun issue(subject: String, ttlMillis: Long = 3_600_000L): String =
        codec.issue(subject, ttlMillis)

    /**
     * 전달받은 JWT 문자열의 서명과 유효성을 검증하고 페이로드 클레임을 추출한다.
     *
     * 토큰 서명이 불일치하거나 유효기간이 만료된 경우 [io.jsonwebtoken.JwtException] 계열 예외를 발생시키므로 호출부의 인증 예외 처리가 필요하다.
     */
    fun parse(token: String): Claims =
        codec.parse(token)
}
