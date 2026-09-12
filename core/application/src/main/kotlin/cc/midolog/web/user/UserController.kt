package cc.midolog.web.user

import cc.midolog.business.service.UserService
import cc.midolog.user.model.User
import cc.midolog.web.exception.ApiException
import cc.midolog.web.response.ApiResponse
import cc.midolog.web.user.dto.CreateUserRequest
import cc.midolog.web.user.dto.UserResponse
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * `/api/users` 경로 접두를 가지는 사용자 리소스 웹 엔드포인트.
 *
 * 내부 도메인 모델([User])이 API 스펙으로 직접 노출되지 않도록 모든 응답을 표준 봉투 [ApiResponse]와 전용 응답 DTO [UserResponse]로 감싸서 반환한다.
 * 도메인 모델 직접 누출 여부는 [cc.midolog.web.ControllerResponseTypeTest]에 의해 엄격하게 검증된다.
 */
@RestController
@RequestMapping("/api/users")
class UserController(
    private val userService: UserService,
) {
    /**
     * 식별자로 사용자를 조회하여 200 OK와 [UserResponse]를 반환한다.
     *
     * 일치하는 사용자가 존재하지 않으면 [ApiException.notFound] 예외를 발생시켜 HTTP 404 상태 코드로 응답한다.
     */
    @GetMapping("/{id}")
    suspend fun get(@PathVariable id: String): ApiResponse<UserResponse> {
        val user = userService.findById(id)
            ?: throw ApiException.notFound("user not found: $id")
        return ApiResponse.ok(UserResponse.from(user))
    }

    /**
     * 신규 사용자를 생성하거나 갱신한 뒤 200 OK와 [UserResponse]를 반환한다.
     *
     * 요청 본문([CreateUserRequest])은 [@Valid]에 의해 유효성 검증을 거치며, 제약 조건 위반 시 글로벌 예외 핸들러를 통해 400 Bad Request 에러 응답으로 변환된다.
     */
    @PostMapping
    suspend fun create(@Valid @RequestBody request: CreateUserRequest): ApiResponse<UserResponse> {
        val saved = userService.save(
            User(
                id = request.id,
                email = request.email,
                displayName = request.displayName,
            ),
        )
        return ApiResponse.ok(UserResponse.from(saved))
    }
}
