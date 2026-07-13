package cc.midolog.web.auth

import cc.midolog.common.security.JwtProvider
import cc.midolog.web.exception.ApiException
import cc.midolog.web.exception.ErrorCode
import cc.midolog.web.response.ApiResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.security.MessageDigest

/**
 * 데모용 토큰 발급 엔드포인트.
 *
 * `demo.auth.username`/`demo.auth.password` property(디폴트 없음)와 일치하는
 * 자격 증명이 제공될 때만 JWT를 발급한다. property가 설정되지 않았거나
 * 자격 증명이 일치하지 않으면 발급하지 않는다(fail-closed, 401).
 *
 * 자격 증명은 액세스 로그 노출을 피하기 위해 요청 본문(JSON)으로만 받고,
 * 비교는 타이밍 부채널을 막기 위해 두 필드 모두 상수 시간으로 수행한다.
 */
@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val jwtProvider: JwtProvider,
    @Value("\${demo.auth.username:}") private val configuredUsername: String,
    @Value("\${demo.auth.password:}") private val configuredPassword: String,
) {
    /** 데모 자격 증명을 검증하고 일치할 때만 JWT를 발급한다. */
    @PostMapping("/token")
    fun token(@RequestBody request: TokenRequest): ApiResponse<Map<String, String>> {
        if (configuredUsername.isBlank() || configuredPassword.isBlank()) {
            throw ApiException(ErrorCode.UNAUTHORIZED, "token issuance is disabled: demo credentials not configured")
        }
        val usernameMatches = constantTimeEquals(request.username, configuredUsername)
        val passwordMatches = constantTimeEquals(request.password, configuredPassword)
        if (!(usernameMatches and passwordMatches)) {
            throw ApiException(ErrorCode.UNAUTHORIZED, "invalid credentials")
        }
        return ApiResponse.ok(mapOf("token" to jwtProvider.issue(request.username)))
    }

    private fun constantTimeEquals(provided: String, expected: String): Boolean =
        MessageDigest.isEqual(provided.toByteArray(Charsets.UTF_8), expected.toByteArray(Charsets.UTF_8))
}

/** 토큰 발급 요청 본문. */
data class TokenRequest(
    val username: String,
    val password: String,
)
