package cc.midolog.user.port.repository

import cc.midolog.user.model.User
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

abstract class UserRepositoryPortContract {

    abstract fun port(): UserRepositoryPort
    open fun reset() {}

    @AfterEach
    fun tearDown() {
        reset()
    }

    @Test
    fun `save and load the same User contract`() = runBlocking {
        val repository = port()
        val user = User(
            id = "user_contract_1000",
            email = "contract@example.com",
            displayName = "Contract User",
        )

        assertEquals(user, repository.save(user))
        assertEquals(user, repository.findById(user.id))
    }

    @Test
    fun `findById returns null when not found`() = runBlocking {
        val repository = port()
        assertNull(repository.findById("unknown_id"))
    }
    
    @Test
    fun `save overwrites existing data (upsert)`() = runBlocking {
        val repository = port()
        val user = User(
            id = "user_contract_1000",
            email = "contract@example.com",
            displayName = "Contract User",
        )
        repository.save(user)
        
        val updated = User(
            id = "user_contract_1000",
            email = "updated@example.com",
            displayName = "Updated User",
        )
        repository.save(updated)
        
        assertEquals(updated, repository.findById(user.id))
    }
}
