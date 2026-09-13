package cc.midolog.examples.consumer

import cc.midolog.sample.model.Sample
import cc.midolog.sample.port.repository.SampleRepositoryPort
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * 독립 소비자 빌드에서 storage.persistence.provider=jpa 프로퍼티를 통한 Spring Data JPA 자동 구성 및 포트 동작 검증 테스트.
 *
 * 외부 아티팩트 좌표로 주입된 도메인 포트([SampleRepositoryPort])를 활용하여
 * H2 인메모리 환경에서 도메인 모델([Sample])의 save 및 findById 왕복 동작을 검증한다.
 */
@SpringBootTest(
    classes = [ArtifactConsumerApplication::class],
    properties = [
        "storage.persistence.provider=jpa",
        "spring.datasource.url=jdbc:h2:mem:consumer_jpa;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
    ]
)
class ArtifactConsumerJpaPersistenceTest {

    @Autowired
    private lateinit var sampleRepositoryPort: SampleRepositoryPort

    @Test
    fun `jpa provider roundtrip save and findById works via published domain port`() = runTest {
        val sample = Sample(id = "consumer-jpa-1", name = "Artifact Consumer JPA Sample")
        val saved = sampleRepositoryPort.save(sample)
        assertEquals(sample, saved)

        val found = sampleRepositoryPort.findById("consumer-jpa-1")
        assertEquals(sample, found)
    }
}
