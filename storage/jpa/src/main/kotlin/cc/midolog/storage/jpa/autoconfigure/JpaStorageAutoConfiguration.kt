package cc.midolog.storage.jpa.autoconfigure

import cc.midolog.file.port.repository.FileMetaRepositoryPort
import cc.midolog.sample.port.repository.SampleRepositoryPort
import cc.midolog.user.port.repository.UserRepositoryPort
import cc.midolog.storage.jpa.file.JpaFileMetaRepositoryAdapter
import cc.midolog.storage.jpa.file.FileMetaJpaRepository
import cc.midolog.storage.jpa.sample.JpaSampleRepositoryAdapter
import cc.midolog.storage.jpa.sample.SampleJpaRepository
import cc.midolog.storage.jpa.user.JpaUserRepositoryAdapter
import cc.midolog.storage.jpa.user.UserJpaRepository
import com.querydsl.jpa.impl.JPAQueryFactory
import jakarta.persistence.EntityManager
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.Bean
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionOperations
import org.springframework.transaction.support.TransactionTemplate

@AutoConfiguration
@ConditionalOnProperty(prefix = "storage.persistence", name = ["provider"], havingValue = "jpa")
@EntityScan(basePackages = ["cc.midolog.storage.jpa"])
@EnableJpaRepositories(basePackages = ["cc.midolog.storage.jpa"])
class JpaStorageAutoConfiguration {

    @Bean
    fun jpaQueryFactory(em: EntityManager): JPAQueryFactory {
        return JPAQueryFactory(em)
    }

    @Bean
    fun jpaTransactionOperations(
        transactionManager: PlatformTransactionManager,
    ): TransactionOperations {
        return TransactionTemplate(transactionManager)
    }

    @Bean
    @ConditionalOnMissingBean(SampleRepositoryPort::class)
    fun sampleRepositoryPort(
        sampleJpaRepository: SampleJpaRepository,
        transactionOperations: TransactionOperations
    ): SampleRepositoryPort {
        return JpaSampleRepositoryAdapter(sampleJpaRepository, transactionOperations)
    }

    @Bean
    @ConditionalOnMissingBean(UserRepositoryPort::class)
    fun userRepositoryPort(
        userJpaRepository: UserJpaRepository,
        transactionOperations: TransactionOperations
    ): UserRepositoryPort {
        return JpaUserRepositoryAdapter(userJpaRepository, transactionOperations)
    }

    @Bean
    @ConditionalOnMissingBean(FileMetaRepositoryPort::class)
    fun fileMetaRepositoryPort(
        fileMetaJpaRepository: FileMetaJpaRepository,
        transactionOperations: TransactionOperations,
        jpaQueryFactory: JPAQueryFactory
    ): FileMetaRepositoryPort {
        return JpaFileMetaRepositoryAdapter(fileMetaJpaRepository, transactionOperations, jpaQueryFactory)
    }
}
