package cc.midolog.user.port.repository

import cc.midolog.user.model.User
import cc.midolog.support.runTestBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

abstract class UserRepositoryPortContract {
    protected abstract fun port(): UserRepositoryPort

    @Test
    fun `save and load the same User contract`() = runTestBlocking {
        val user = User(id = "user-test-1", email = "test@example.com", displayName = "Test User")
        port().save(user)
        
        val loaded = port().findById("user-test-1")
        assertEquals(user.id, loaded?.id)
        assertEquals(user.email, loaded?.email)
        assertEquals(user.displayName, loaded?.displayName)
    }

    @Test
    fun `findById returns null when not found`() = runTestBlocking {
        assertNull(port().findById("non-existent-user"))
    }

    @Test
    fun `save overwrites existing data (upsert)`() = runTestBlocking {
        val user = User(id = "user-upsert-1", email = "orig@example.com", displayName = "Orig")
        port().save(user)
        
        val updated = User(id = "user-upsert-1", email = "updated@example.com", displayName = "Updated")
        port().save(updated)
        
        val loaded = port().findById("user-upsert-1")
        assertEquals("updated@example.com", loaded?.email)
        assertEquals("Updated", loaded?.displayName)
    }
}
