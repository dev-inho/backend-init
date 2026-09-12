package cc.midolog.storage.jpa.sample

import cc.midolog.sample.port.repository.SampleRepositoryPort
import cc.midolog.sample.port.repository.SampleRepositoryPortContract
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.support.TransactionTemplate

@DataJpaTest(properties = ["spring.jpa.hibernate.ddl-auto=create-drop"])
@EntityScan(basePackages = ["cc.midolog.storage.jpa"])
@TestPropertySource(properties = [
    "spring.datasource.url=jdbc:h2:mem:testdb_jpa_sample;DB_CLOSE_DELAY=-1"
])
class JpaSampleRepositoryPortContractTest : SampleRepositoryPortContract() {

    @Autowired
    private lateinit var repository: SampleJpaRepository

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    override fun port(): SampleRepositoryPort {
        return JpaSampleRepositoryAdapter(repository, transactionTemplate)
    }
}
