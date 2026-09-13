package cc.midolog.web.filter

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource
import org.slf4j.LoggerFactory
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import reactor.core.publisher.Mono

/**
 * HttpLoggingFilter에 TestTimeSource를 주입하여 시간 경과가 정확히 durationMs로 기록되는지 검증하는 값 가드 테스트.
 */
class HttpLoggingFilterDurationTest {

    @Test
    fun `logs elapsed duration measured by injected timeSource`() {
        val timeSource = TestTimeSource()
        val filter = HttpLoggingFilter(timeSource)

        val logger = LoggerFactory.getLogger(HttpLoggingFilter::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)
        logger.level = Level.INFO

        try {
            val exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/test"),
            )

            filter.filter(exchange) {
                timeSource += 1500.milliseconds
                Mono.empty()
            }.block()

            assertEquals(1, appender.list.size)
            val message = appender.list[0].formattedMessage
            assertTrue(
                message.contains("durationMs=1500"),
                "Expected durationMs=1500 in log message but was: $message",
            )
        } finally {
            logger.detachAppender(appender)
        }
    }
}
