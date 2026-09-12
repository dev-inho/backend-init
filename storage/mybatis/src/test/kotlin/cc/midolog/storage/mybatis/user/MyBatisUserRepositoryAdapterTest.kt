package cc.midolog.storage.mybatis.user

import cc.midolog.user.model.User
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MyBatisMyBatisUserRepositoryAdapterTest {

    @Test
    fun `save delegates to mapper upsert and returns the saved user`() = runBlocking {
        val mapper = RecordingUserMapper(upsertResult = 1)
        val adapter = MyBatisUserRepositoryAdapter(mapper)
        val user = User(id = "user_1000", email = "stored@example.com", displayName = "Stored")

        val saved = adapter.save(user)

        assertEquals(user, saved)
        assertEquals(Triple("user_1000", "stored@example.com", "Stored"), mapper.lastUpsert)
    }

    @Test
    fun `save fails fast when mapper upsert affects no rows`() = runBlocking {
        val adapter = MyBatisUserRepositoryAdapter(RecordingUserMapper(upsertResult = 0))

        assertFailsWith<IllegalStateException> {
            adapter.save(User(id = "user_1001", email = "missing@example.com", displayName = "Missing"))
        }
    }

    @Test
    fun `findById maps mapper row to domain user`() = runBlocking {
        val adapter = MyBatisUserRepositoryAdapter(
            RecordingUserMapper(
                row = mapOf(
                    "id" to "user_1002",
                    "email" to "loaded@example.com",
                    "displayName" to "Loaded",
                ),
            ),
        )

        assertEquals(
            User(id = "user_1002", email = "loaded@example.com", displayName = "Loaded"),
            adapter.findById("user_1002"),
        )
    }

    private class RecordingUserMapper(
        private val row: Map<String, Any?>? = null,
        private val upsertResult: Int = 1,
    ) : UserMapper {
        var lastUpsert: Triple<String, String, String>? = null

        override fun selectById(id: String): Map<String, Any?>? = row

        override fun upsert(id: String, email: String, displayName: String): Int {
            lastUpsert = Triple(id, email, displayName)
            return upsertResult
        }
    }
}
