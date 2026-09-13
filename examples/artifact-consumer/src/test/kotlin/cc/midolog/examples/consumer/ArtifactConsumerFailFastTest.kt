package cc.midolog.examples.consumer

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder

/**
 * 독립 소비자 빌드에서 storage.persistence.provider 프로퍼티 부재 시 fail-fast 동작을 검증하는 테스트.
 *
 * 프로퍼티 누락 시 부트스트랩 컨텍스트 초기화가 즉시 실패하며,
 * 원인 예외 메시지에 3가지 핵심 토큰('storage.persistence.provider', 'jpa', 'mybatis')이 포함되는지 확인한다.
 */
class ArtifactConsumerFailFastTest {

    @Test
    fun `consumer context startup fails fast when storage persistence provider property is missing`() {
        val exception = assertThrows<Throwable> {
            SpringApplicationBuilder(ArtifactConsumerApplication::class.java)
                .web(WebApplicationType.NONE)
                .properties(
                    "spring.datasource.url=jdbc:h2:mem:consumer_failfast;DB_CLOSE_DELAY=-1",
                    "spring.flyway.enabled=false"
                )
                .run()
        }

        val messages = mutableListOf<String>()
        var current: Throwable? = exception
        while (current != null) {
            current.message?.let { messages.add(it) }
            current = current.cause
        }
        val combinedMessage = messages.joinToString("\n")

        assertTrue(
            combinedMessage.contains("storage.persistence.provider"),
            "Error cause chain must mention 'storage.persistence.provider'. Actual: $combinedMessage"
        )
        assertTrue(
            combinedMessage.contains("jpa"),
            "Error cause chain must mention 'jpa'. Actual: $combinedMessage"
        )
        assertTrue(
            combinedMessage.contains("mybatis"),
            "Error cause chain must mention 'mybatis'. Actual: $combinedMessage"
        )
    }
}
