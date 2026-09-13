package cc.midolog.storage.mybatis.user

import cc.midolog.storage.mybatis.user.UserDynamicSqlSupport.user
import cc.midolog.user.model.User
import cc.midolog.user.port.repository.UserRepositoryPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mybatis.dynamic.sql.util.kotlin.mybatis3.select

/**
 * 사용자 저장소 포트(UserRepositoryPort)의 MyBatis 기반 영속성 어댑터.
 *
 * 블로킹 JDBC 호출을 Dispatchers.IO로 격리하여 WebFlux 이벤트루프를 차단하지 않는다.
 * 조회는 생성된 UserDynamicSqlSupport를 통한 MyBatis Dynamic SQL로 수행하며,
 * 저장은 단일 행 갱신 무결성을 보장하기 위해 affectedRows == 1을 엄격히 검증한다.
 */
class MyBatisUserRepositoryAdapter(
    private val userMapper: UserMapper,
) : UserRepositoryPort {

    override suspend fun findById(id: String): User? = withContext(Dispatchers.IO) {
        val selectStatement = select(
            UserDynamicSqlSupport.id,
            UserDynamicSqlSupport.email,
            UserDynamicSqlSupport.displayName,
        ) {
            from(user)
            where { UserDynamicSqlSupport.id isEqualTo id }
        }
        val row = userMapper.selectOneMappedRow(selectStatement) ?: return@withContext null
        User(
            id = (row["id"] ?: row["ID"]) as String,
            email = (row["email"] ?: row["EMAIL"]) as String,
            displayName = (row["display_name"] ?: row["displayName"] ?: row["DISPLAY_NAME"]) as String,
        )
    }

    override suspend fun save(user: User): User = withContext(Dispatchers.IO) {
        val affectedRows = userMapper.upsert(user.id, user.email, user.displayName)
        check(affectedRows == 1) {
            "Expected to upsert one user row but affected $affectedRows rows"
        }
        user
    }
}
