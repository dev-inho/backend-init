package cc.midolog.storage.jpa.file

import cc.midolog.file.port.repository.FileMetaRepositoryPort
import cc.midolog.file.port.repository.FileMetaRepositoryPortContract
import jakarta.persistence.EntityManager
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.transaction.support.TransactionOperations
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@DataJpaTest(
    properties = [
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.data.jpa.repositories.enabled=true",
    ]
)
class JpaFileMetaRepositoryPortContractTest : FileMetaRepositoryPortContract() {

    @Autowired
    private lateinit var fileMetaJpaRepository: FileMetaJpaRepository
    
    @Autowired
    private lateinit var entityManager: EntityManager
    
    private var testClock: Clock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneId.of("UTC"))

    @Autowired
    private lateinit var transactionManager: org.springframework.transaction.PlatformTransactionManager
    
    override fun port(): FileMetaRepositoryPort {
        return JpaFileMetaRepositoryAdapter(
            fileMetaJpaRepository,
            org.springframework.transaction.support.TransactionTemplate(transactionManager),
            entityManager,
            testClock
        )
    }

    override fun clock(): Clock {
        return testClock
    }

    @org.springframework.boot.SpringBootConfiguration
    @org.springframework.boot.persistence.autoconfigure.EntityScan("cc.midolog.storage.jpa.file")
    @org.springframework.data.jpa.repository.config.EnableJpaRepositories("cc.midolog.storage.jpa.file")
    class JpaTestConfig
}
