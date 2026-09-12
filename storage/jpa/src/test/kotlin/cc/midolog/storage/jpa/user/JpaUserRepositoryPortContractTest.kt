package cc.midolog.storage.jpa.user

import cc.midolog.user.port.repository.UserRepositoryPort
import cc.midolog.user.port.repository.UserRepositoryPortContract
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.support.TransactionTemplate

@DataJpaTest(properties = ["spring.jpa.hibernate.ddl-auto=create-drop"])
@EntityScan(basePackages = ["cc.midolog.storage.jpa"])
@TestPropertySource(properties = [
    "spring.datasource.url=jdbc:h2:mem:testdb_jpa_user;DB_CLOSE_DELAY=-1"
])
class JpaUserRepositoryPortContractTest : UserRepositoryPortContract() {

    @Autowired
    private lateinit var repository: UserJpaRepository

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    override fun port(): UserRepositoryPort {
        return JpaUserRepositoryAdapter(repository, transactionTemplate)
    }
}
