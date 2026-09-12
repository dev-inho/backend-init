package cc.midolog.storage.mybatis.user

import cc.midolog.user.model.User
import cc.midolog.user.port.repository.UserRepositoryPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository

@Profile("mybatis")
@Repository
class MyBatisUserRepositoryAdapter(
    private val userMapper: UserMapper,
) : UserRepositoryPort {
    override suspend fun findById(id: String): User? = withContext(Dispatchers.IO) {
        userMapper.selectById(id)?.let {
            User(
                id = it["id"] as String,
                email = it["email"] as String,
                displayName = it["displayName"] as String,
            )
        }
    }

    override suspend fun save(user: User): User = withContext(Dispatchers.IO) {
        val affectedRows = userMapper.upsert(user.id, user.email, user.displayName)
        check(affectedRows == 1) {
            "Expected to upsert one user row but affected $affectedRows rows"
        }
        user
    }
}
