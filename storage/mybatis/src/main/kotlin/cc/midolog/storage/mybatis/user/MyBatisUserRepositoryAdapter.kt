package cc.midolog.storage.mybatis.user

import cc.midolog.user.model.User
import cc.midolog.user.port.repository.UserRepositoryPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository

/**
 * 사용자 저장소 포트(UserRepositoryPort)의 MyBatis 기반 영속성 어댑터.
 *
 * mybatis 프로파일(@Profile("mybatis"))에서 활성화된다.
 * 블로킹 JDBC(MyBatis) 호출을 Dispatchers.IO로 분리해 WebFlux 이벤트루프를 막지 않는다.
 * save 시 upsert 영향 행 수가 정확히 1건인지 check(affectedRows == 1)로 검증하여
 * 동시성 경합이나 부분 실패 발생 시 즉시 예외를 던져 무결성을 보장한다.
 */
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
