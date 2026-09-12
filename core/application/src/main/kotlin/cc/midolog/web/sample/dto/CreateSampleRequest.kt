package cc.midolog.web.sample.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * 샘플 리소스 등록 요청 본문을 표현하고 검증하는 DTO.
 *
 * 웹 계층의 Bean Validation 검증 규칙을 도메인 엔티티로부터 분리하고 외부 클라이언트와의 요청 계약을 고정하기 위해 사용한다.
 * Kotlin에서는 유효성 검증 애너테이션이 프로퍼티 백킹 필드에 적용되도록 @field 타깃을 명시한다.
 */
data class CreateSampleRequest(
    @field:NotBlank(message = "id는 필수입니다")
    val id: String,

    @field:NotBlank(message = "name은 필수입니다")
    @field:Size(max = 255, message = "name은 255자 이하여야 합니다")
    val name: String,
)
