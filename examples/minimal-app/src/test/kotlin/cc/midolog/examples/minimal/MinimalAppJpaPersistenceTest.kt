package cc.midolog.examples.minimal

import cc.midolog.sample.model.Sample
import cc.midolog.sample.port.repository.SampleRepositoryPort
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * storage.persistence.provider=jpa 프로퍼티를 통한 Spring Data JPA 자동 구성 및 포트 동작 실증 테스트.
 *
 * 외부 영속성 어댑터 클래스를 직접 참조하지 않고 오직 도메인 포트([SampleRepositoryPort]) 인터페이스만을 주입받아
 * H2 인메모리 환경에서 도메인 모델([Sample])의 save 및 findById 왕복 동작을 검증한다.
 */
@SpringBootTest(
    classes = [MinimalApplication::class],
    properties = [
        "storage.persistence.provider=jpa",
        "spring.datasource.url=jdbc:h2:mem:minimal_jpa;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
    ]
)
class MinimalAppJpaPersistenceTest {

    @Autowired
    private lateinit var sampleRepositoryPort: SampleRepositoryPort

    @Test
    fun `jpa provider roundtrip save and findById works via domain port`() = runTest {
        val sample = Sample(id = "sample-jpa-1", name = "Minimal JPA Sample")
        val saved = sampleRepositoryPort.save(sample)
        assertEquals(sample, saved)

        val found = sampleRepositoryPort.findById("sample-jpa-1")
        assertEquals(sample, found)
    }
}
