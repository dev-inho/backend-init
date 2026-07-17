package cc.midolog.web.user.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

data class CreateUserRequest(
    @field:NotBlank
    val id: String,

    @field:Email
    @field:NotBlank
    val email: String,

    @field:NotBlank
    val displayName: String,
)
