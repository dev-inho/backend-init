package cc.midolog.storage.jpa.sample

import cc.midolog.sample.port.repository.SampleRepositoryPort
import cc.midolog.sample.port.repository.SampleRepositoryPortContract
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.transaction.support.TransactionOperations

@DataJpaTest(
    properties = [
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.data.jpa.repositories.enabled=true",
    ]
)
class JpaSampleRepositoryPortContractTest : SampleRepositoryPortContract() {

    @Autowired
    private lateinit var sampleJpaRepository: SampleJpaRepository

    @Autowired
    private lateinit var transactionManager: org.springframework.transaction.PlatformTransactionManager

    override fun port(): SampleRepositoryPort {
        return JpaSampleRepositoryAdapter(
            sampleJpaRepository,
            org.springframework.transaction.support.TransactionTemplate(transactionManager)
        )
    }

    @org.springframework.boot.SpringBootConfiguration
    @org.springframework.boot.persistence.autoconfigure.EntityScan("cc.midolog.storage.jpa.sample")
    @org.springframework.data.jpa.repository.config.EnableJpaRepositories("cc.midolog.storage.jpa.sample")
    class JpaTestConfig
}
