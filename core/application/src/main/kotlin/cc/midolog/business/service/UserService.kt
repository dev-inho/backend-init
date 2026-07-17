package cc.midolog.business.service

import cc.midolog.user.model.User
import cc.midolog.user.port.repository.UserRepositoryPort
import org.springframework.stereotype.Service

@Service
class UserService(
    private val userRepositoryPort: UserRepositoryPort,
) {
    suspend fun findById(id: String): User? =
        userRepositoryPort.findById(id)

    suspend fun save(user: User): User =
        userRepositoryPort.save(user)
}
