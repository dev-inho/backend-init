package cc.midolog.web.user.dto

import cc.midolog.user.model.User

data class UserResponse(
    val id: String,
    val email: String,
    val displayName: String,
) {
    companion object {
        fun from(user: User): UserResponse =
            UserResponse(
                id = user.id,
                email = user.email,
                displayName = user.displayName,
            )
    }
}
