package cc.midolog.user.port.repository

import cc.midolog.user.model.User

interface UserRepositoryPort {
    suspend fun findById(id: String): User?

    suspend fun save(user: User): User
}
