package cc.midolog.customer.port.repository

import cc.midolog.support.runTestBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * CustomerRepositoryPort 구현체의 저장, 조회, 삭제 계약을 검증하는 추상 테스트 픽스처.
 *
 * JPA 및 MyBatis 영속성 어댑터는 이 계약을 상속하여 실제 데이터베이스 연산에서
 * 저장(upsert), 단건 조회, 삭제 무결성이 동일하게 유지되는지 증명해야 한다.
 */
abstract class CustomerRepositoryPortContract<T : Any, ID : Any> {

    protected abstract fun port(): CustomerRepositoryPort<T, ID>
    protected abstract fun sampleEntity(id: ID): T
    protected abstract fun updatedEntity(entity: T): T
    protected abstract fun idOf(entity: T): ID
    protected abstract fun newId(): ID

    @Test
    fun `save and load entity contract`() = runTestBlocking {
        val id = newId()
        val entity = sampleEntity(id)
        port().save(entity)

        val loaded = port().findById(id)
        assertNotNull(loaded)
        assertEquals(entity, loaded)
    }

    @Test
    fun `findById returns null when entity does not exist`() = runTestBlocking {
        val nonExistentId = newId()
        assertNull(port().findById(nonExistentId))
    }

    @Test
    fun `save updates existing entity (upsert)`() = runTestBlocking {
        val id = newId()
        val entity = sampleEntity(id)
        port().save(entity)

        val updated = updatedEntity(entity)
        port().save(updated)

        val loaded = port().findById(id)
        assertNotNull(loaded)
        assertEquals(updated, loaded)
    }

    @Test
    fun `deleteById removes existing entity and returns true`() = runTestBlocking {
        val id = newId()
        val entity = sampleEntity(id)
        port().save(entity)

        val deleted = port().deleteById(id)
        assertTrue(deleted)

        val loaded = port().findById(id)
        assertNull(loaded)
    }

    @Test
    fun `deleteById returns false when entity does not exist`() = runTestBlocking {
        val nonExistentId = newId()
        val deleted = port().deleteById(nonExistentId)
        assertFalse(deleted)
    }
}
