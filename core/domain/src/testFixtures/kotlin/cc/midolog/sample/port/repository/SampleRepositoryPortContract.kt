package cc.midolog.sample.port.repository

import cc.midolog.sample.model.Sample
import cc.midolog.support.runTestBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

abstract class SampleRepositoryPortContract {
    protected abstract fun port(): SampleRepositoryPort

    @Test
    fun `save and load the same Sample contract`() = runTestBlocking {
        val sample = Sample(id = "sample-test-1", name = "Test Name")
        port().save(sample)
        
        val loaded = port().findById("sample-test-1")
        assertEquals(sample.id, loaded?.id)
        assertEquals(sample.name, loaded?.name)
    }

    @Test
    fun `findById returns null when not found`() = runTestBlocking {
        assertNull(port().findById("non-existent-sample"))
    }

    @Test
    fun `save overwrites existing data (upsert)`() = runTestBlocking {
        val sample = Sample(id = "sample-upsert-1", name = "Original")
        port().save(sample)
        
        val updated = Sample(id = "sample-upsert-1", name = "Updated")
        port().save(updated)
        
        val loaded = port().findById("sample-upsert-1")
        assertEquals("Updated", loaded?.name)
    }
}
