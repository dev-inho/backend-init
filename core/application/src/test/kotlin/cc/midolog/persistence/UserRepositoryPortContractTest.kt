package cc.midolog.persistence

import cc.midolog.storage.jpa.user.JpaUserRepositoryAdapter
import cc.midolog.storage.jpa.user.UserJpaEntity
import cc.midolog.storage.jpa.user.UserJpaRepository
import cc.midolog.storage.mybatis.user.UserMapper
import cc.midolog.storage.mybatis.user.MyBatisUserRepositoryAdapter
import cc.midolog.user.model.User
import cc.midolog.user.port.repository.UserRepositoryPort
import java.lang.reflect.Proxy
import java.util.Optional
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.springframework.transaction.support.TransactionOperations
import kotlin.test.assertEquals

class UserRepositoryPortContractTest {

    @Test
    fun `mybatis and jpa adapters save and load the same User contract`() = runBlocking {
        listOf(
            mybatisAdapter(),
            jpaAdapter(),
        ).forEach { repository ->
            val user = User(
                id = "user_contract_1000",
                email = "contract@example.com",
                displayName = "Contract User",
            )

            assertEquals(user, repository.save(user))
            assertEquals(user, repository.findById(user.id))
        }
    }

    private fun mybatisAdapter(): UserRepositoryPort =
        MyBatisUserRepositoryAdapter(
            object : UserMapper {
                private val rows = mutableMapOf<String, Map<String, Any?>>()

                override fun selectById(id: String): Map<String, Any?>? = rows[id]

                override fun upsert(id: String, email: String, displayName: String): Int {
                    rows[id] = mapOf(
                        "id" to id,
                        "email" to email,
                        "displayName" to displayName,
                    )
                    return 1
                }
            },
        )

    private fun jpaAdapter(): UserRepositoryPort {
        val rows = mutableMapOf<String, UserJpaEntity>()
        val repository = Proxy.newProxyInstance(
            UserJpaRepository::class.java.classLoader,
            arrayOf(UserJpaRepository::class.java),
        ) { _, method, args ->
            when (method.name) {
                "findById" -> Optional.ofNullable(rows[args?.first() as String])
                "save" -> {
                    val entity = args?.first() as UserJpaEntity
                    rows[entity.id] = entity
                    entity
                }
                else -> null
            }
        } as UserJpaRepository

        return JpaUserRepositoryAdapter(repository, TransactionOperations.withoutTransaction())
    }
}
