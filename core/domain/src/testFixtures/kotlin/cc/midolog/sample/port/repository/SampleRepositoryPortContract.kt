package cc.midolog.sample.port.repository

import cc.midolog.sample.model.Sample
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

abstract class SampleRepositoryPortContract {

    abstract fun port(): SampleRepositoryPort
    open fun reset() {}

    @AfterEach
    fun tearDown() {
        reset()
    }

    @Test
    fun `save and load the same Sample contract`() = runBlocking {
        val repository = port()
        val sample = Sample(id = "sample_contract_1000", name = "contract")

        assertEquals(sample, repository.save(sample))
        assertEquals(sample, repository.findById(sample.id))
    }

    @Test
    fun `findById returns null when not found`() = runBlocking {
        val repository = port()
        assertNull(repository.findById("unknown_id"))
    }
    
    @Test
    fun `save overwrites existing data (upsert)`() = runBlocking {
        val repository = port()
        val sample = Sample(id = "sample_contract_1000", name = "contract")
        repository.save(sample)
        
        val updated = Sample(id = "sample_contract_1000", name = "contract_updated")
        repository.save(updated)
        
        assertEquals(updated, repository.findById(sample.id))
    }
}
