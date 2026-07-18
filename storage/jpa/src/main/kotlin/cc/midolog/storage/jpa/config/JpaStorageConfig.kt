package cc.midolog.storage.jpa.config

import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionOperations
import org.springframework.transaction.support.TransactionTemplate

@Profile("jpa")
@Configuration
@EntityScan("cc.midolog.storage.jpa")
@EnableJpaRepositories("cc.midolog.storage.jpa")
class JpaStorageConfig {

    @Bean
    fun jpaTransactionOperations(
        transactionManager: PlatformTransactionManager,
    ): TransactionOperations = TransactionTemplate(transactionManager)
}
