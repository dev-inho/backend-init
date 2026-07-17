package cc.midolog.web.user

import cc.midolog.business.service.UserService
import cc.midolog.user.model.User
import cc.midolog.web.exception.ApiException
import cc.midolog.web.response.ApiResponse
import cc.midolog.web.user.dto.CreateUserRequest
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/users")
class UserController(
    private val userService: UserService,
) {
    @GetMapping("/{id}")
    suspend fun get(@PathVariable id: String): ApiResponse<User> {
        val user = userService.findById(id)
            ?: throw ApiException.notFound("user not found: $id")
        return ApiResponse.ok(user)
    }

    @PostMapping
    suspend fun create(@Valid @RequestBody request: CreateUserRequest): ApiResponse<User> {
        val saved = userService.save(
            User(
                id = request.id,
                email = request.email,
                displayName = request.displayName,
            ),
        )
        return ApiResponse.ok(saved)
    }
}
