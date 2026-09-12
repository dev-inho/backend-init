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

    @Profile("jpa")
    @SpringBootConfiguration
    @EntityScan("cc.midolog.storage.jpa.file")
    @EnableJpaRepositories("cc.midolog.storage.jpa.file")
    class JpaTestConfig {
        @Bean
        fun jpaTransactionOperations(
            transactionManager: PlatformTransactionManager,
        ): TransactionOperations = TransactionTemplate(transactionManager)
    }
}
