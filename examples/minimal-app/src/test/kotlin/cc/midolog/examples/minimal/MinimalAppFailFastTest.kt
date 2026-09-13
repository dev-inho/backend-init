package cc.midolog.examples.minimal

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder

/**
 * storage.persistence.provider 필수 프로퍼티 부재 시 fail-fast 동작을 검증하는 테스트.
 *
 * 어떠한 저장소 내부 구현 클래스도 직접 참조하지 않고 오직 MinimalApplication의 부트스트랩 경로를 통해
 * 프로퍼티 누락 시 컨텍스트 초기화가 즉시 실패하며, 원인 예외 체인 메시지에 필수 프로퍼티 키와 지원되는 값(jpa, mybatis)이
 * 포함되는지 검증한다.
 */
class MinimalAppFailFastTest {

    @Test
    fun `context startup fails fast when storage persistence provider property is missing`() {
        val exception = assertThrows<Throwable> {
            SpringApplicationBuilder(MinimalApplication::class.java)
                .web(WebApplicationType.NONE)
                .properties(
                    "spring.datasource.url=jdbc:h2:mem:failfast;DB_CLOSE_DELAY=-1",
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
