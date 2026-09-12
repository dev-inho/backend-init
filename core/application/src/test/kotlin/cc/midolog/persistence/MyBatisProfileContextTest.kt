package cc.midolog.persistence

import cc.midolog.sample.model.Sample
import cc.midolog.sample.port.repository.SampleRepositoryPort
import cc.midolog.storage.mybatis.sample.SampleMapper
import cc.midolog.storage.mybatis.sample.MyBatisSampleRepositoryAdapter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext

class MyBatisProfileContextTest {

    @Test
    fun `mybatis profile registers one SampleRepositoryPort adapter`() {
        AnnotationConfigApplicationContext().use { context ->
            context.environment.setActiveProfiles("mybatis")
            context.beanFactory.registerSingleton("sampleMapper", testMapper())
            context.register(MyBatisSampleRepositoryAdapter::class.java)

            context.refresh()

            assertEquals(1, context.getBeansOfType(SampleRepositoryPort::class.java).size)
        }
    }

    @Test
    fun `non mybatis profile does not register SampleRepositoryPort adapter`() {
        AnnotationConfigApplicationContext().use { context ->
            context.environment.setActiveProfiles("jpa")
            context.beanFactory.registerSingleton("sampleMapper", testMapper())
            context.register(MyBatisSampleRepositoryAdapter::class.java)

            context.refresh()

            assertEquals(0, context.getBeansOfType(SampleRepositoryPort::class.java).size)
        }
    }

    private fun testMapper(): SampleMapper =
        object : SampleMapper {
            override fun selectById(id: String): Map<String, Any?>? =
                mapOf("id" to id, "name" to "sample")

            override fun upsert(id: String, name: String): Int = 1
        }
}
