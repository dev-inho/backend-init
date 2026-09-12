package cc.midolog.storage.jpa.user

import cc.midolog.user.port.repository.UserRepositoryPort
import cc.midolog.user.port.repository.UserRepositoryPortContract
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.transaction.support.TransactionOperations

@DataJpaTest(
    properties = [
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.data.jpa.repositories.enabled=true",
    ]
)
class JpaUserRepositoryPortContractTest : UserRepositoryPortContract() {

    @Autowired
    private lateinit var userJpaRepository: UserJpaRepository

    @Autowired
    private lateinit var transactionManager: org.springframework.transaction.PlatformTransactionManager

    override fun port(): UserRepositoryPort {
        return JpaUserRepositoryAdapter(userJpaRepository, org.springframework.transaction.support.TransactionTemplate(transactionManager))
    }

    @org.springframework.boot.SpringBootConfiguration
    @org.springframework.boot.persistence.autoconfigure.EntityScan("cc.midolog.storage.jpa.user")
    @org.springframework.data.jpa.repository.config.EnableJpaRepositories("cc.midolog.storage.jpa.user")
    class JpaTestConfig
}
