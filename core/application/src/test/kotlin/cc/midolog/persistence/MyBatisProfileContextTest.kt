package cc.midolog.persistence

import cc.midolog.sample.port.repository.SampleRepositoryPort
import cc.midolog.user.port.repository.UserRepositoryPort
import cc.midolog.file.port.repository.FileMetaRepositoryPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import java.lang.reflect.Proxy
import java.time.Clock

class MyBatisProfileContextTest {

    @Test
    fun `mybatis profile registers one SampleRepositoryPort adapter`() {
        AnnotationConfigApplicationContext().use { context ->
            context.environment.setActiveProfiles("mybatis")
            registerAllDummyBeans(context)
            registerAdapters(context)
            context.refresh()

            val beans = context.getBeansOfType(SampleRepositoryPort::class.java)
            assertEquals(1, beans.size)
            assertEquals("MyBatisSampleRepositoryAdapter", beans.values.first().javaClass.simpleName)
        }
    }

    @Test
    fun `mybatis profile registers one UserRepositoryPort adapter`() {
        AnnotationConfigApplicationContext().use { context ->
            context.environment.setActiveProfiles("mybatis")
            registerAllDummyBeans(context)
            registerAdapters(context)
            context.refresh()

            val beans = context.getBeansOfType(UserRepositoryPort::class.java)
            assertEquals(1, beans.size)
            assertEquals("MyBatisUserRepositoryAdapter", beans.values.first().javaClass.simpleName)
        }
    }

    @Test
    fun `mybatis profile registers one FileMetaRepositoryPort adapter`() {
        AnnotationConfigApplicationContext().use { context ->
            context.environment.setActiveProfiles("mybatis")
            registerAllDummyBeans(context)
            registerAdapters(context)
            context.refresh()

            val beans = context.getBeansOfType(FileMetaRepositoryPort::class.java)
            assertEquals(1, beans.size)
            assertEquals("MyBatisFileMetaRepositoryAdapter", beans.values.first().javaClass.simpleName)
        }
    }

    @Test
    fun `non mybatis profile does not register adapters`() {
        AnnotationConfigApplicationContext().use { context ->
            context.environment.setActiveProfiles("test")
            registerAllDummyBeans(context)
            registerAdapters(context)
            context.refresh()

            assertEquals(0, context.getBeansOfType(SampleRepositoryPort::class.java).size)
        }
    }

    private fun registerAllDummyBeans(context: AnnotationConfigApplicationContext) {
        registerDummyBean(context, "cc.midolog.storage.mybatis.sample.SampleMapper", "sampleMapper")
        registerDummyBean(context, "cc.midolog.storage.mybatis.user.UserMapper", "userMapper")
        registerDummyBean(context, "cc.midolog.storage.mybatis.file.FileMetaMapper", "fileMetaMapper")
        
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
                // Ignore
            }
        }
    }

    private fun registerDummyBean(context: AnnotationConfigApplicationContext, className: String, beanName: String) {
        val clazz = Class.forName(className)
        val proxy = Proxy.newProxyInstance(clazz.classLoader, arrayOf(clazz)) { _, method, _ -> 
            when (method.name) {
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
