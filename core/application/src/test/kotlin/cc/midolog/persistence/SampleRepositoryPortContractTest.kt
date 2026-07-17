package cc.midolog.persistence

import cc.midolog.sample.model.Sample
import cc.midolog.sample.port.repository.SampleRepositoryPort
import cc.midolog.storage.jpa.sample.JpaSampleRepositoryAdapter
import cc.midolog.storage.jpa.sample.SampleJpaEntity
import cc.midolog.storage.jpa.sample.SampleJpaRepository
import cc.midolog.storage.sample.SampleMapper
import cc.midolog.storage.sample.SampleRepositoryAdapter
import java.lang.reflect.Proxy
import java.util.Optional
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.springframework.transaction.support.TransactionOperations
import kotlin.test.assertEquals

class SampleRepositoryPortContractTest {

    @Test
    fun `mybatis and jpa adapters save and load the same Sample contract`() = runBlocking {
        listOf(
            mybatisAdapter(),
            jpaAdapter(),
        ).forEach { repository ->
            val sample = Sample(id = "sample_contract_1000", name = "contract")

            assertEquals(sample, repository.save(sample))
            assertEquals(sample, repository.findById(sample.id))
        }
    }

    private fun mybatisAdapter(): SampleRepositoryPort =
        SampleRepositoryAdapter(
            object : SampleMapper {
                private val rows = mutableMapOf<String, Map<String, Any?>>()

                override fun selectById(id: String): Map<String, Any?>? = rows[id]

                override fun upsert(id: String, name: String): Int {
                    rows[id] = mapOf("id" to id, "name" to name)
                    return 1
                }
            },
        )

    private fun jpaAdapter(): SampleRepositoryPort {
        val rows = mutableMapOf<String, SampleJpaEntity>()
        val repository = Proxy.newProxyInstance(
            SampleJpaRepository::class.java.classLoader,
            arrayOf(SampleJpaRepository::class.java),
        ) { _, method, args ->
            when (method.name) {
                "findById" -> Optional.ofNullable(rows[args?.first() as String])
                "save" -> {
                    val entity = args?.first() as SampleJpaEntity
                    rows[entity.id] = entity
                    entity
                }
                else -> null
            }
        } as SampleJpaRepository

        return JpaSampleRepositoryAdapter(repository, TransactionOperations.withoutTransaction())
    }
}
