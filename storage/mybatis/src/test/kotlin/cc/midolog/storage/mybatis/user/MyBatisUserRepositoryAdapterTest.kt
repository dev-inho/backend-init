package cc.midolog.storage.mybatis.user

import cc.midolog.user.model.User
import kotlinx.coroutines.runBlocking
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MyBatisMyBatisUserRepositoryAdapterTest {

    @Test
    fun `save delegates to mapper upsert and returns the saved user`() = runBlocking {
        var lastUpsert: Triple<String, String, String>? = null
        val mapper = createFakeUserMapper(
            upsertHandler = { id, email, displayName ->
                lastUpsert = Triple(id, email, displayName)
                1
            },
        )
        val adapter = MyBatisUserRepositoryAdapter(mapper)
        val user = User(id = "user_1000", email = "stored@example.com", displayName = "Stored")

        val saved = adapter.save(user)

        assertEquals(user, saved)
        assertEquals(Triple("user_1000", "stored@example.com", "Stored"), lastUpsert)
    }

    @Test
    fun `save fails fast when mapper upsert affects no rows`() = runBlocking {
        val mapper = createFakeUserMapper(upsertHandler = { _, _, _ -> 0 })
        val adapter = MyBatisUserRepositoryAdapter(mapper)

        assertFailsWith<IllegalStateException> {
            adapter.save(User(id = "user_1001", email = "missing@example.com", displayName = "Missing"))
        }
    }

    @Test
    fun `findById maps mapper row to domain user`() = runBlocking {
        val mapper = createFakeUserMapper(
            row = mapOf(
                "id" to "user_1002",
                "email" to "loaded@example.com",
                "display_name" to "Loaded",
            ),
        )
        val adapter = MyBatisUserRepositoryAdapter(mapper)

        assertEquals(
            User(id = "user_1002", email = "loaded@example.com", displayName = "Loaded"),
            adapter.findById("user_1002"),
        )
    }

    private fun createFakeUserMapper(
        row: Map<String, Any?>? = null,
        upsertHandler: ((String, String, String) -> Int)? = null,
    ): UserMapper {
        return Proxy.newProxyInstance(
            UserMapper::class.java.classLoader,
            arrayOf(UserMapper::class.java),
        ) { _, method, args ->
            when (method.name) {
                "selectOneMappedRow" -> row
                "upsert" -> upsertHandler?.invoke(args[0] as String, args[1] as String, args[2] as String) ?: 1
                "toString" -> "FakeUserMapper"
                "hashCode" -> 1
                "equals" -> false
                else -> null
            }
        } as UserMapper
    }
}
