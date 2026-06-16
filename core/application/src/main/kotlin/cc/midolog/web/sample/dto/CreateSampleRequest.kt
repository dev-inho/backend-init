package cc.midolog.web.sample.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/** 샘플 생성 요청 DTO. Kotlin에서 jakarta 제약은 @field: 타깃을 쓴다. */
data class CreateSampleRequest(
    @field:NotBlank(message = "id는 필수입니다")
    val id: String,

    @field:NotBlank(message = "name은 필수입니다")
    @field:Size(max = 255, message = "name은 255자 이하여야 합니다")
    val name: String,
)
