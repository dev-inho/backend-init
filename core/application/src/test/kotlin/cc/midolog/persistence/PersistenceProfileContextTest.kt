package cc.midolog.persistence

import cc.midolog.sample.port.repository.SampleRepositoryPort
import cc.midolog.user.port.repository.UserRepositoryPort
import cc.midolog.file.port.repository.FileMetaRepositoryPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.transaction.support.TransactionOperations
import java.lang.reflect.Proxy
import java.time.Clock
import java.util.Optional

class PersistenceProfileContextTest {

    @Test
    fun `jpa profile registers one SampleRepositoryPort adapter`() {
        AnnotationConfigApplicationContext().use { context ->
            context.environment.setActiveProfiles("jpa")
            registerAllDummyBeans(context)
            registerAdapters(context)
            context.refresh()

            val beans = context.getBeansOfType(SampleRepositoryPort::class.java)
            assertEquals(1, beans.size)
            assertEquals("JpaSampleRepositoryAdapter", beans.values.first().javaClass.simpleName)
        }
    }

    @Test
    fun `jpa profile registers one UserRepositoryPort adapter`() {
        AnnotationConfigApplicationContext().use { context ->
            context.environment.setActiveProfiles("jpa")
            registerAllDummyBeans(context)
            registerAdapters(context)
            context.refresh()

            val beans = context.getBeansOfType(UserRepositoryPort::class.java)
            assertEquals(1, beans.size)
            assertEquals("JpaUserRepositoryAdapter", beans.values.first().javaClass.simpleName)
        }
    }

    @Test
    fun `jpa profile registers one FileMetaRepositoryPort adapter`() {
        AnnotationConfigApplicationContext().use { context ->
            context.environment.setActiveProfiles("jpa")
            registerAllDummyBeans(context)
            registerAdapters(context)
            context.refresh()

            val beans = context.getBeansOfType(FileMetaRepositoryPort::class.java)
            assertEquals(1, beans.size)
            assertEquals("JpaFileMetaRepositoryAdapter", beans.values.first().javaClass.simpleName)
        }
    }

    @Test
    fun `non persistence profile registers no adapters`() {
        AnnotationConfigApplicationContext().use { context ->
            context.environment.setActiveProfiles("test")
            registerAllDummyBeans(context)
            registerAdapters(context)
            context.refresh()

            assertEquals(0, context.getBeansOfType(SampleRepositoryPort::class.java).size)
        }
    }

    private fun registerAllDummyBeans(context: AnnotationConfigApplicationContext) {
        registerDummyBean(context, "cc.midolog.storage.jpa.sample.SampleJpaRepository", "sampleJpaRepository")
        registerDummyBean(context, "cc.midolog.storage.jpa.user.UserJpaRepository", "userJpaRepository")
        registerDummyBean(context, "cc.midolog.storage.jpa.file.FileMetaJpaRepository", "fileMetaJpaRepository")
        
        val txBd = org.springframework.beans.factory.support.RootBeanDefinition(TransactionOperations::class.java)
        txBd.instanceSupplier = java.util.function.Supplier { TransactionOperations.withoutTransaction() }
        context.registerBeanDefinition("transactionOperations", txBd)
        
        registerDummyBean(context, "jakarta.persistence.EntityManager", "entityManager")
        
        val clockBd = org.springframework.beans.factory.support.RootBeanDefinition(Clock::class.java)
        clockBd.instanceSupplier = java.util.function.Supplier { Clock.systemUTC() }
        context.registerBeanDefinition("clock", clockBd)
    }

    private fun registerAdapters(context: AnnotationConfigApplicationContext) {
        val classNames = listOf(
            "cc.midolog.storage.jpa.sample.JpaSampleRepositoryAdapter",
            "cc.midolog.storage.mybatis.sample.MyBatisSampleRepositoryAdapter",
            "cc.midolog.storage.jpa.user.JpaUserRepositoryAdapter",
            "cc.midolog.storage.mybatis.user.MyBatisUserRepositoryAdapter",
            "cc.midolog.storage.jpa.file.JpaFileMetaRepositoryAdapter",
            "cc.midolog.storage.mybatis.file.MyBatisFileMetaRepositoryAdapter"
        )
        for (name in classNames) {
            try {
                context.register(Class.forName(name))
            } catch (e: ClassNotFoundException) {
                // Ignore if not present in classpath
            }
        }
    }

    private fun registerDummyBean(context: AnnotationConfigApplicationContext, className: String, beanName: String) {
        val clazz = Class.forName(className)
        val proxy = Proxy.newProxyInstance(clazz.classLoader, arrayOf(clazz)) { _, method, _ ->
            when (method.name) {
                "findById" -> Optional.empty<Any>()
                "save" -> error("save should not be called by profile wiring tests")
                "toString" -> "DummyProxy"
                "hashCode" -> 0
                "equals" -> false
                else -> null
            }
        }
        val bd = org.springframework.beans.factory.support.RootBeanDefinition()
        bd.targetType = clazz
        bd.instanceSupplier = java.util.function.Supplier { proxy }
        context.registerBeanDefinition(beanName, bd)
    }
}
