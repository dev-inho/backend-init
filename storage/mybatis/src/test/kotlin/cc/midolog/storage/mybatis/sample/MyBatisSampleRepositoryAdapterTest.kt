package cc.midolog.storage.mybatis.sample

import cc.midolog.sample.model.Sample
import kotlinx.coroutines.runBlocking
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MyBatisMyBatisSampleRepositoryAdapterTest {

    @Test
    fun `save delegates to mapper upsert and returns the saved sample`() = runBlocking {
        var lastUpsert: Pair<String, String>? = null
        val mapper = createFakeSampleMapper(
            upsertHandler = { id, name ->
                lastUpsert = id to name
                1
            },
        )
        val adapter = MyBatisSampleRepositoryAdapter(mapper)
        val sample = Sample(id = "sample_1000", name = "stored")

        val saved = adapter.save(sample)

        assertEquals(sample, saved)
        assertEquals("sample_1000" to "stored", lastUpsert)
    }

    @Test
    fun `save fails fast when mapper upsert affects no rows`() = runBlocking {
        val mapper = createFakeSampleMapper(upsertHandler = { _, _ -> 0 })
        val adapter = MyBatisSampleRepositoryAdapter(mapper)

        assertFailsWith<IllegalStateException> {
            adapter.save(Sample(id = "sample_1001", name = "missing"))
        }
    }

    @Test
    fun `findById maps mapper row to domain sample`() = runBlocking {
        val mapper = createFakeSampleMapper(
            row = mapOf("id" to "sample_1002", "name" to "loaded"),
        )
        val adapter = MyBatisSampleRepositoryAdapter(mapper)

        assertEquals(Sample(id = "sample_1002", name = "loaded"), adapter.findById("sample_1002"))
    }

    private fun createFakeSampleMapper(
        row: Map<String, Any?>? = null,
        upsertHandler: ((String, String) -> Int)? = null,
    ): SampleMapper {
        return Proxy.newProxyInstance(
            SampleMapper::class.java.classLoader,
            arrayOf(SampleMapper::class.java),
        ) { _, method, args ->
            when (method.name) {
                "selectOneMappedRow" -> row
                "upsert" -> upsertHandler?.invoke(args[0] as String, args[1] as String) ?: 1
                "toString" -> "FakeSampleMapper"
                "hashCode" -> 1
                "equals" -> false
                else -> null
            }
        } as SampleMapper
    }
}
