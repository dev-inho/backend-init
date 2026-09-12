package cc.midolog.persistence

import cc.midolog.sample.model.Sample
import cc.midolog.sample.port.repository.SampleRepositoryPort
import cc.midolog.storage.jpa.sample.JpaSampleRepositoryAdapter
import cc.midolog.storage.jpa.sample.SampleJpaRepository
import cc.midolog.storage.jpa.user.JpaUserRepositoryAdapter
import cc.midolog.storage.jpa.user.UserJpaRepository
import cc.midolog.storage.mybatis.sample.SampleMapper
import cc.midolog.storage.mybatis.sample.MyBatisSampleRepositoryAdapter
import cc.midolog.storage.mybatis.user.UserMapper
import cc.midolog.storage.mybatis.user.MyBatisUserRepositoryAdapter
import cc.midolog.user.port.repository.UserRepositoryPort
import java.lang.reflect.Proxy
import java.util.Optional
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.transaction.support.TransactionOperations

class PersistenceProfileContextTest {

    @Test
    fun `jpa profile registers one SampleRepositoryPort adapter`() {
        AnnotationConfigApplicationContext().use { context ->
            context.environment.setActiveProfiles("jpa")
            context.beanFactory.registerSingleton("sampleJpaRepository", repositoryProxy())
            context.beanFactory.registerSingleton("transactionOperations", TransactionOperations.withoutTransaction())
            context.register(JpaSampleRepositoryAdapter::class.java)
            context.register(MyBatisSampleRepositoryAdapter::class.java)

            context.refresh()

            assertEquals(1, context.getBeansOfType(SampleRepositoryPort::class.java).size)
        }
    }

    @Test
    fun `jpa profile registers one UserRepositoryPort adapter`() {
        AnnotationConfigApplicationContext().use { context ->
            context.environment.setActiveProfiles("jpa")
            context.beanFactory.registerSingleton("userJpaRepository", userRepositoryProxy())
            context.beanFactory.registerSingleton("transactionOperations", TransactionOperations.withoutTransaction())
            context.register(JpaUserRepositoryAdapter::class.java)
            context.register(MyBatisUserRepositoryAdapter::class.java)

            context.refresh()

            assertEquals(1, context.getBeansOfType(UserRepositoryPort::class.java).size)
        }
    }

    @Test
    fun `mybatis profile registers one SampleRepositoryPort adapter`() {
        AnnotationConfigApplicationContext().use { context ->
            context.environment.setActiveProfiles("mybatis")
            context.beanFactory.registerSingleton("sampleMapper", testMapper())
            context.beanFactory.registerSingleton("sampleJpaRepository", repositoryProxy())
            context.beanFactory.registerSingleton("transactionOperations", TransactionOperations.withoutTransaction())
            context.register(JpaSampleRepositoryAdapter::class.java)
            context.register(MyBatisSampleRepositoryAdapter::class.java)

            context.refresh()

            assertEquals(1, context.getBeansOfType(SampleRepositoryPort::class.java).size)
        }
    }

    @Test
    fun `mybatis profile registers one UserRepositoryPort adapter`() {
        AnnotationConfigApplicationContext().use { context ->
            context.environment.setActiveProfiles("mybatis")
            context.beanFactory.registerSingleton("userMapper", testUserMapper())
            context.beanFactory.registerSingleton("userJpaRepository", userRepositoryProxy())
            context.beanFactory.registerSingleton("transactionOperations", TransactionOperations.withoutTransaction())
            context.register(JpaUserRepositoryAdapter::class.java)
            context.register(MyBatisUserRepositoryAdapter::class.java)

            context.refresh()

            assertEquals(1, context.getBeansOfType(UserRepositoryPort::class.java).size)
        }
    }

    @Test
    fun `non persistence profile registers no SampleRepositoryPort adapter`() {
        AnnotationConfigApplicationContext().use { context ->
            context.environment.setActiveProfiles("test")
            context.beanFactory.registerSingleton("sampleMapper", testMapper())
            context.beanFactory.registerSingleton("sampleJpaRepository", repositoryProxy())
            context.beanFactory.registerSingleton("transactionOperations", TransactionOperations.withoutTransaction())
            context.register(JpaSampleRepositoryAdapter::class.java)
            context.register(MyBatisSampleRepositoryAdapter::class.java)

            context.refresh()

            assertEquals(0, context.getBeansOfType(SampleRepositoryPort::class.java).size)
        }
    }

    @Test
    fun `non persistence profile registers no UserRepositoryPort adapter`() {
        AnnotationConfigApplicationContext().use { context ->
            context.environment.setActiveProfiles("test")
            context.beanFactory.registerSingleton("userMapper", testUserMapper())
            context.beanFactory.registerSingleton("userJpaRepository", userRepositoryProxy())
            context.beanFactory.registerSingleton("transactionOperations", TransactionOperations.withoutTransaction())
            context.register(JpaUserRepositoryAdapter::class.java)
            context.register(MyBatisUserRepositoryAdapter::class.java)

            context.refresh()

            assertEquals(0, context.getBeansOfType(UserRepositoryPort::class.java).size)
        }
    }

    private fun testMapper(): SampleMapper =
        object : SampleMapper {
            override fun selectById(id: String): Map<String, Any?>? =
                mapOf("id" to id, "name" to "sample")

            override fun upsert(id: String, name: String): Int = 1
        }

    private fun testUserMapper(): UserMapper =
        object : UserMapper {
            override fun selectById(id: String): Map<String, Any?>? =
                mapOf("id" to id, "email" to "user@example.com", "displayName" to "User")

            override fun upsert(id: String, email: String, displayName: String): Int = 1
        }

    private fun repositoryProxy(): SampleJpaRepository =
        Proxy.newProxyInstance(
            SampleJpaRepository::class.java.classLoader,
            arrayOf(SampleJpaRepository::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "findById" -> Optional.empty<Any>()
                "save" -> error("save should not be called by profile wiring tests")
                else -> null
            }
        } as SampleJpaRepository

    private fun userRepositoryProxy(): UserJpaRepository =
        Proxy.newProxyInstance(
            UserJpaRepository::class.java.classLoader,
            arrayOf(UserJpaRepository::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "findById" -> Optional.empty<Any>()
                "save" -> error("save should not be called by profile wiring tests")
                else -> null
            }
        } as UserJpaRepository
}
