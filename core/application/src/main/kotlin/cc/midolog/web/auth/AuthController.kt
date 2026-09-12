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
 * 데모 환경용 인증 토큰 발급 엔드포인트.
 *
 * 환경 변수 또는 프로퍼티(`demo.auth.username`, `demo.auth.password`)와 일치하는 자격 증명이 전달될 때만 인증 JWT를 발급한다.
 * 자격 증명 설정이 비어 있거나 일치하지 않는 경우 토큰 발급을 전면 차단하고 401 Unauthorized 에러를 발생시킨다(fail-closed).
 * 자격 증명 정보의 액세스 로그 유출을 방지하기 위해 요청 본문(JSON)으로만 수신하며, 타이밍 부채널 공격을 방어하기 위해 상수 시간 문자열 비교를 적용한다.
 */
@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val jwtProvider: JwtProvider,
    @Value("\${demo.auth.username:}") private val configuredUsername: String,
    @Value("\${demo.auth.password:}") private val configuredPassword: String,
) {
    /**
     * 데모 자격 증명을 검증하여 유효한 경우 200 OK와 함께 신규 발급된 JWT 토큰 맵을 반환한다.
     *
     * 서버에 설정된 데모 자격 증명이 누락되었거나 일치하지 않으면 [ApiException]을 발생시켜 401 상태 코드를 응답한다.
     */
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

/**
 * 토큰 발급을 위해 클라이언트가 제출하는 사용자명과 비밀번호를 담은 요청 DTO.
 */
data class TokenRequest(
    val username: String,
    val password: String,
)
