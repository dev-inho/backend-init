package cc.midolog.storage.jpa

import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionOperations
import org.springframework.transaction.support.TransactionTemplate

@SpringBootConfiguration
@EnableAutoConfiguration
class JpaTestApplication {
    @Bean
    fun jpaTransactionOperations(
        transactionManager: PlatformTransactionManager,
    ): TransactionOperations = TransactionTemplate(transactionManager)
}
