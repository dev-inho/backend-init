package cc.midolog.web.auth

import cc.midolog.common.response.ApiResponse
import cc.midolog.common.security.JwtProvider
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 데모용 토큰 발급 엔드포인트. */
@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val jwtProvider: JwtProvider,
) {
    @PostMapping("/token")
    fun token(@RequestParam(defaultValue = "demo-user") username: String): ApiResponse<Map<String, String>> =
        ApiResponse.ok(mapOf("token" to jwtProvider.issue(username)))
}
