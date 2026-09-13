package cc.midolog.web.handler

import cc.midolog.logging.LoggingMdc
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange

class GlobalExceptionHandlerLoggingTest {

    @Test
    fun `handleUnexpected logs unexpected exception at ERROR level with requestId and exception details`() {
        val handler = GlobalExceptionHandler()
        val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)

        try {
            val requestId = "req-test-uuid-999"
            val exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/test")
                    .header(LoggingMdc.REQUEST_ID, requestId)
                    .build()
            )
            val exception = IllegalStateException("Something went catastrophically wrong")

            val response = handler.handleUnexpected(exception, exchange)
            assertEquals(500, response.statusCode.value())

            val errorLogs = appender.list.filter { it.level == Level.ERROR }
            assertEquals(1, errorLogs.size, "예상 밖 예외는 ERROR 레벨로 1회 로깅되어야 한다")

            val logEvent = errorLogs.first()
            assertTrue(
                logEvent.formattedMessage.contains(requestId),
                "로그 메시지에 requestId가 포함되어야 한다. 실제: ${logEvent.formattedMessage}"
            )
            assertTrue(
                logEvent.formattedMessage.contains("IllegalStateException"),
                "로그 메시지에 예외 클래스명이 포함되어야 한다. 실제: ${logEvent.formattedMessage}"
            )
            assertTrue(
                logEvent.formattedMessage.contains("Something went catastrophically wrong"),
                "로그 메시지에 예외 메시지가 포함되어야 한다. 실제: ${logEvent.formattedMessage}"
            )
            assertNotNull(
                logEvent.throwableProxy,
                "ERROR 로그에 예외 스택트레이스(Throwable)가 첨부되어야 한다"
            )
        } finally {
            logger.detachAppender(appender)
        }
    }
}
