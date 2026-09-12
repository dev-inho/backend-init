package cc.midolog.storage.jpa.file

import cc.midolog.file.model.FileMeta
import cc.midolog.file.model.FileStatus
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Profile
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionOperations
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DataJpaTest(
    properties = [
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.data.jpa.repositories.enabled=true",
    ],
)
@ActiveProfiles("jpa")
@Import(JpaFileMetaRepositoryAdapter::class, JpaFileMetaRepositoryAdapterTest.JpaTestConfig::class)
class JpaFileMetaRepositoryAdapterTest {

    @Autowired
    private lateinit var adapter: JpaFileMetaRepositoryAdapter

    @Autowired
    private lateinit var fileMetaJpaRepository: FileMetaJpaRepository

    @Test
    fun `save persists file meta and updateStatus changes status from PENDING to READY`() = runBlocking {
        val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        val file = FileMeta(
            id = "file_jpa_1000",
            ownerId = "owner1",
            storageKey = "key1",
            sizeBytes = 100L,
            contentType = "text/plain",
            checksum = "hash",
            status = FileStatus.PENDING,
            createdAt = now,
            updatedAt = now
        )

        assertEquals(file, adapter.save(file))
        assertEquals(file, adapter.findById(file.id))

        assertTrue(adapter.updateStatus(file.id, FileStatus.READY))

        val updated = adapter.findById(file.id)!!
        assertEquals(FileStatus.READY, updated.status)
        assertTrue(updated.updatedAt.isAfter(now) || updated.updatedAt == now)
    }

    @Test
    fun `findExpiredPending returns only PENDING files older than cutoff and sorted ascending`() = runBlocking {
        val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)

        // PENDING but old
        val f1 = FileMeta("f1", "o1", "k1", 100L, "text", "hash", FileStatus.PENDING, now.minusSeconds(100), now.minusSeconds(100))
        adapter.save(f1)
        val f2 = FileMeta("f2", "o1", "k2", 100L, "text", "hash", FileStatus.PENDING, now.minusSeconds(90), now.minusSeconds(90))
        adapter.save(f2)

        // READY and old -> should be skipped
        val f3 = FileMeta("f3", "o1", "k3", 100L, "text", "hash", FileStatus.READY, now.minusSeconds(110), now.minusSeconds(110))
        adapter.save(f3)

        // PENDING but recent -> should be skipped
        val f4 = FileMeta("f4", "o1", "k4", 100L, "text", "hash", FileStatus.PENDING, now.minusSeconds(10), now.minusSeconds(10))
        adapter.save(f4)

        val results = adapter.findExpiredPending(now.minusSeconds(50), 1)
        assertEquals(1, results.size)
        assertEquals("f1", results[0].id)
    }

    @Profile("jpa")
    @SpringBootConfiguration
    @EntityScan("cc.midolog.storage.jpa.file")
    @EnableJpaRepositories("cc.midolog.storage.jpa.file")
    class JpaTestConfig {
        @Bean
        fun jpaTransactionOperations(
            transactionManager: PlatformTransactionManager,
        ): TransactionOperations = TransactionTemplate(transactionManager)

        @Bean
        fun jpaQueryFactory(em: jakarta.persistence.EntityManager) = com.querydsl.jpa.impl.JPAQueryFactory(em)
    }
}
