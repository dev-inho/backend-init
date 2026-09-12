package cc.midolog.web.user.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

/**
 * 신규 사용자 등록 요청 본문을 역직렬화하고 검증하는 DTO.
 *
 * 웹 계층의 Bean Validation 제약(@field:NotBlank, @field:Email)을 도메인 모델로부터 격리하고 외부 요청 계약을 고정하기 위해 둔다.
 * 도메인 내부 필드 구성이 변경되더라도 API 클라이언트 요청 명세를 안정적으로 유지할 수 있다.
 */
data class CreateUserRequest(
    @field:NotBlank
    val id: String,

    @field:Email
    @field:NotBlank
    val email: String,

    @field:NotBlank
    val displayName: String,
)
