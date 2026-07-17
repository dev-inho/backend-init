package cc.midolog.storage.sample

import cc.midolog.sample.model.Sample
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SampleRepositoryAdapterTest {

    @Test
    fun `save delegates to mapper upsert and returns the saved sample`() = runBlocking {
        val mapper = RecordingSampleMapper(upsertResult = 1)
        val adapter = SampleRepositoryAdapter(mapper)
        val sample = Sample(id = "sample_1000", name = "stored")

        val saved = adapter.save(sample)

        assertEquals(sample, saved)
        assertEquals("sample_1000" to "stored", mapper.lastUpsert)
    }

    @Test
    fun `save fails fast when mapper upsert affects no rows`() = runBlocking {
        val adapter = SampleRepositoryAdapter(RecordingSampleMapper(upsertResult = 0))

        assertFailsWith<IllegalStateException> {
            adapter.save(Sample(id = "sample_1001", name = "missing"))
        }
    }

    @Test
    fun `findById maps mapper row to domain sample`() = runBlocking {
        val adapter = SampleRepositoryAdapter(
            RecordingSampleMapper(row = mapOf("id" to "sample_1002", "name" to "loaded")),
        )

        assertEquals(Sample(id = "sample_1002", name = "loaded"), adapter.findById("sample_1002"))
    }

    private class RecordingSampleMapper(
        private val row: Map<String, Any?>? = null,
        private val upsertResult: Int = 1,
    ) : SampleMapper {
        var lastUpsert: Pair<String, String>? = null

        override fun selectById(id: String): Map<String, Any?>? = row

        override fun upsert(id: String, name: String): Int {
            lastUpsert = id to name
            return upsertResult
        }
    }
}
