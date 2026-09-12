package cc.midolog.persistence

import cc.midolog.file.model.FileMeta
import cc.midolog.file.model.FileStatus
import cc.midolog.file.port.repository.FileMetaRepositoryPort
import cc.midolog.storage.jpa.file.FileMetaJpaEntity
import cc.midolog.storage.jpa.file.FileMetaJpaMapper
import cc.midolog.storage.jpa.file.FileMetaJpaRepository
import cc.midolog.storage.jpa.file.JpaFileMetaRepositoryAdapter
import cc.midolog.storage.mybatis.file.FileMetaMapper
import cc.midolog.storage.mybatis.file.MyBatisFileMetaRepositoryAdapter
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.springframework.transaction.support.TransactionOperations
import java.lang.reflect.Proxy
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileMetaRepositoryPortContractTest {

    @Test
    fun `mybatis and jpa adapters save, find, update and expire pending the same FileMeta contract`() = runBlocking {
        val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        
        listOf(
            mybatisAdapter(),
            jpaAdapter(),
        ).forEach { repository ->
            val meta = FileMeta(
                id = "file_contract_1",
                ownerId = "owner_1",
                storageKey = "key_1",
                sizeBytes = 1024L,
                contentType = "text/plain",
                checksum = "hash123",
                status = FileStatus.PENDING,
                createdAt = now,
                updatedAt = now
            )

            // save
            val saved = repository.save(meta)
            assertEquals(meta, saved)

            // find
            assertEquals(meta, repository.findById(meta.id))

            // updateStatus (success)
            assertTrue(repository.updateStatus(meta.id, FileStatus.READY))
            
            // updateStatus (not found)
            assertFalse(repository.updateStatus("unknown_id", FileStatus.READY))

            // findExpiredPending
            // We'll test this via a simple proxy check or data check
            // For now, let's just save a pending and call it
            val pendingOld = meta.copy(id = "pending_old", status = FileStatus.PENDING, updatedAt = now.minusSeconds(100))
            repository.save(pendingOld)
            
            val pendingNew = meta.copy(id = "pending_new", status = FileStatus.PENDING, updatedAt = now)
            repository.save(pendingNew)

            val expired = repository.findExpiredPending(now.minusSeconds(50), 10)
            assertEquals(1, expired.size)
            assertEquals("pending_old", expired[0].id)
        }
    }

    private fun mybatisAdapter(): FileMetaRepositoryPort =
        MyBatisFileMetaRepositoryAdapter(
            object : FileMetaMapper {
                private val rows = mutableMapOf<String, MutableMap<String, Any?>>()

                override fun selectById(id: String): Map<String, Any?>? = rows[id]

                override fun upsert(
                    id: String,
                    ownerId: String,
                    storageKey: String,
                    sizeBytes: Long?,
                    contentType: String?,
                    checksum: String?,
                    status: String,
                    createdAt: Instant,
                    updatedAt: Instant
                ): Int {
                    rows[id] = mutableMapOf(
                        "id" to id,
                        "ownerId" to ownerId,
                        "storageKey" to storageKey,
                        "sizeBytes" to sizeBytes,
                        "contentType" to contentType,
                        "checksum" to checksum,
                        "status" to status,
                        "createdAt" to createdAt,
                        "updatedAt" to updatedAt
                    )
                    return 1
                }

                override fun updateStatus(id: String, status: String, updatedAt: Instant): Int {
                    val row = rows[id] ?: return 0
                    row["status"] = status
                    row["updatedAt"] = updatedAt
                    return 1
                }

                override fun findExpiredPending(
                    status: String,
                    cutoff: Instant,
                    limit: Int
                ): List<Map<String, Any?>> {
                    return rows.values
                        .filter { it["status"] == status && (it["updatedAt"] as Instant).isBefore(cutoff) }
                        .sortedBy { it["updatedAt"] as Instant }
                        .take(limit)
                }
            },
        )

    private fun jpaAdapter(): FileMetaRepositoryPort {
        val rows = mutableMapOf<String, FileMetaJpaEntity>()
        val repository = Proxy.newProxyInstance(
            FileMetaJpaRepository::class.java.classLoader,
            arrayOf(FileMetaJpaRepository::class.java),
        ) { _, method: java.lang.reflect.Method, args: Array<Any>? ->
            when (method.name) {
                "findById" -> Optional.ofNullable(rows[args?.first() as String])
                "save" -> {
                    val entity = args?.first() as FileMetaJpaEntity
                    rows[entity.id] = entity
                    entity
                }
                else -> null
            }
        } as FileMetaJpaRepository

        val emClass = Class.forName("jakarta.persistence.EntityManager")
        val queryClass = Class.forName("jakarta.persistence.Query")

        val entityManager = Proxy.newProxyInstance(
            emClass.classLoader,
            arrayOf(emClass)
        ) { _, method: java.lang.reflect.Method, args: Array<Any>? ->
            if (method.name == "createQuery") {
                val qString = args!![0] as String
                val params = mutableMapOf<String, Any>()
                var maxRes = Int.MAX_VALUE
                
                Proxy.newProxyInstance(
                    queryClass.classLoader,
                    arrayOf(queryClass, Class.forName("jakarta.persistence.TypedQuery"))
                ) { _, qMethod: java.lang.reflect.Method, qArgs: Array<Any>? ->
                    when (qMethod.name) {
                        "setParameter" -> {
                            val paramName = qArgs!![0] as String
                            val paramValue = qArgs[1]
                            params[paramName] = paramValue
                            null 
                        }
                        "setMaxResults" -> {
                            maxRes = qArgs!![0] as Int
                            null
                        }
                        "executeUpdate" -> {
                            val id = params["id"] as? String
                            val status = params["status"] as? FileStatus
                            if (id != null && status != null && rows.containsKey(id)) {
                                val entity = rows[id]!!
                                entity.status = status
                                entity.updatedAt = Instant.now()
                                1
                            } else 0
                        }
                        "getResultList" -> {
                            val status = params["status"] as? FileStatus
                            val cutoff = params["cutoff"] as? Instant
                            rows.values.filter { 
                                it.status == status && it.updatedAt.isBefore(cutoff) 
                            }.sortedBy { it.updatedAt }.take(maxRes)
                        }
                        else -> null
                    }
                }
            } else null
        }

        val adapterClass = JpaFileMetaRepositoryAdapter::class.java
        val constructor = adapterClass.constructors.first()
        return constructor.newInstance(repository, TransactionOperations.withoutTransaction(), entityManager) as FileMetaRepositoryPort
    }
}
