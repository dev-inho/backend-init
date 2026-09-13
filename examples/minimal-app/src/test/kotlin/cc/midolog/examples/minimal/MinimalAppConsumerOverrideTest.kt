package cc.midolog.examples.minimal

import cc.midolog.sample.model.Sample
import cc.midolog.sample.port.repository.SampleRepositoryPort
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import

/**
 * 소비자 애플리케이션의 커스텀 도메인 포트 빈 정의 시 자동 구성 기본 어댑터 양보(@ConditionalOnMissingBean) 검증 테스트.
 *
 * Spring Boot의 기본 bean overriding 비활성화 상태(false)에서 소비자가 직접 정의한 SampleRepositoryPort 빈이
 * 유일하게 등록되며, 자동 구성 어댑터가 물러나는 확장 계약을 실증한다.
 * 어떠한 concrete adapter import, FQCN, 또는 문자열 클래스명 우회 검사 없이 오직 소비자 빈 타입 자체로 단언한다.
 */
@SpringBootTest(
    classes = [MinimalApplication::class],
    properties = [
        "storage.persistence.provider=jpa",
        "spring.datasource.url=jdbc:h2:mem:minimal_override;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
    ]
)
@Import(MinimalAppConsumerOverrideTest.CustomPortTestConfiguration::class)
class MinimalAppConsumerOverrideTest {

    class CustomSampleRepository : SampleRepositoryPort {
        private val store = mutableMapOf<String, Sample>()

        override suspend fun findById(id: String): Sample? = store[id]

        override suspend fun save(sample: Sample): Sample {
            store[sample.id] = sample
            return sample
        }
    }

    @TestConfiguration
    class CustomPortTestConfiguration {
        @Bean
        fun customSampleRepository(): SampleRepositoryPort {
            return CustomSampleRepository()
        }
    }

    @Autowired
    private lateinit var context: ApplicationContext

    @Autowired
    private lateinit var sampleRepositoryPort: SampleRepositoryPort

    @Test
    fun `consumer custom port bean overrides default adapter while bean overriding remains disabled`() = runTest {
        val sampleBeans = context.getBeansOfType(SampleRepositoryPort::class.java)
        assertEquals(1, sampleBeans.size, "Exactly one SampleRepositoryPort bean must be registered in the application context")

        val resolvedPort = sampleBeans.values.first()
        assertTrue(resolvedPort is CustomSampleRepository, "The registered port must be the consumer-defined custom bean")
        assertSame(sampleRepositoryPort, resolvedPort)

        val sample = Sample(id = "override-1", name = "Consumer Override")
        val saved = sampleRepositoryPort.save(sample)
        assertEquals(sample, saved)

        val found = sampleRepositoryPort.findById("override-1")
        assertEquals(sample, found)
    }
}
