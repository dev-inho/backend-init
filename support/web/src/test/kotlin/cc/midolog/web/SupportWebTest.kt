package cc.midolog.web

import cc.midolog.logging.LoggingMdc
import cc.midolog.web.exception.ApiException
import cc.midolog.web.exception.ErrorCode
import cc.midolog.web.filter.HttpLoggingFilter
import cc.midolog.web.filter.RequestIdFilter
import cc.midolog.web.handler.GlobalExceptionHandler
import cc.midolog.web.response.ApiResponse
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.MutablePropertyValues
import org.springframework.core.MethodParameter
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.FieldError
import org.springframework.web.bind.support.WebExchangeBindException
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono

/** ApiResponse factory 동작 검증. */
class ApiResponseTest {

    @Test
    fun `ok returns success true with OK code and default message`() {
        val response = ApiResponse.ok(mapOf("k" to "v"))
        assertTrue(response.success)
        assertEquals("OK", response.code)
        assertEquals("OK", response.message)
        assertEquals(mapOf("k" to "v"), response.data)
    }

    @Test
    fun `error returns success false with given code message and null data`() {
        val response = ApiResponse.error<String>("SOME_CODE", "문제가 발생했습니다")
        assertFalse(response.success)
        assertEquals("SOME_CODE", response.code)
        assertEquals("문제가 발생했습니다", response.message)
        assertNull(response.data)
    }
}

/** ErrorCode enum 값·HTTP 상태 매핑 검증. */
class ErrorCodeTest {

    @Test
    fun `has exactly five error codes with expected status mapping`() {
        assertEquals(5, ErrorCode.entries.size)
        assertEquals(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_INPUT.status)
        assertEquals(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND.status)
        assertEquals(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.status)
        assertEquals(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN.status)
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL.status)
    }
}

/** ApiException factory·메시지 폴백 검증. */
class ApiExceptionTest {

    @Test
    fun `notFound factory uses NOT_FOUND error code`() {
        val e = ApiException.notFound("sample not found: 1")
        assertEquals(ErrorCode.NOT_FOUND, e.errorCode)
        assertEquals("sample not found: 1", e.message)
    }

    @Test
    fun `invalidInput factory falls back to default message when detail is null`() {
        val e = ApiException.invalidInput()
        assertEquals(ErrorCode.INVALID_INPUT, e.errorCode)
        assertEquals(ErrorCode.INVALID_INPUT.defaultMessage, e.message)
    }
}

/** GlobalExceptionHandler의 4개 핸들러 동작 검증. */
class GlobalExceptionHandlerTest {

    private val handler = GlobalExceptionHandler()

    @Test
    fun `handleApiException maps to the error code status and name`() {
        val result = handler.handleApiException(ApiException(ErrorCode.FORBIDDEN, "no access"))
        assertEquals(HttpStatus.FORBIDDEN, result.statusCode)
        assertEquals("FORBIDDEN", result.body?.code)
        assertEquals("no access", result.body?.message)
        assertFalse(result.body!!.success)
    }

    @Test
    fun `handleValidation joins field errors into the message`() {
        val bindingResult = BeanPropertyBindingResult(TokenRequestSample(), "tokenRequestSample")
        bindingResult.addError(FieldError("tokenRequestSample", "username", "must not be blank"))
        val parameter = MethodParameter(
            TokenRequestSample::class.java.getDeclaredMethod("sample", String::class.java),
            0,
        )
        val e = WebExchangeBindException(parameter, bindingResult)

        val result = handler.handleValidation(e)

        assertEquals(HttpStatus.BAD_REQUEST, result.statusCode)
        assertEquals("INVALID_INPUT", result.body?.code)
        assertTrue(result.body!!.message.contains("username: must not be blank"))
    }

    @Test
    fun `handleResponseStatus maps 400 to INVALID_INPUT`() {
        val result = handler.handleResponseStatus(ResponseStatusException(HttpStatus.BAD_REQUEST))
        assertEquals(HttpStatus.BAD_REQUEST, result.statusCode)
        assertEquals("INVALID_INPUT", result.body?.code)
    }

    @Test
    fun `handleResponseStatus maps 415 to UNSUPPORTED_MEDIA_TYPE literal`() {
        val result = handler.handleResponseStatus(ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE))
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, result.statusCode)
        assertEquals("UNSUPPORTED_MEDIA_TYPE", result.body?.code)
    }

    @Test
    fun `handleResponseStatus maps other statuses to REQUEST_ERROR literal`() {
        val result = handler.handleResponseStatus(ResponseStatusException(HttpStatus.FORBIDDEN))
        assertEquals(HttpStatus.FORBIDDEN, result.statusCode)
        assertEquals("REQUEST_ERROR", result.body?.code)
    }

    @Test
    fun `handleUnexpected maps to 500 without leaking the original message`() {
        val result = handler.handleUnexpected(RuntimeException("internal secret detail"))
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.statusCode)
        assertEquals("INTERNAL", result.body?.code)
        assertFalse(result.body!!.message.contains("internal secret detail"))
    }
}

private class TokenRequestSample {
    @Suppress("UNUSED_PARAMETER")
    fun sample(username: String) {}
}

/** RequestIdFilter — LoggingMdc.REQUEST_ID 정합·형식검증·전파 검증. */
class RequestIdFilterTest {

    private val filter = RequestIdFilter()

    @Test
    fun `HEADER constant is aligned with LoggingMdc REQUEST_ID`() {
        assertEquals(LoggingMdc.REQUEST_ID, RequestIdFilter.HEADER)
    }

    @Test
    fun `generates X-Request-Id when absent`() {
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/test"))
        filter.filter(exchange) { Mono.empty() }.block()
        assertNotNull(exchange.response.headers.getFirst(LoggingMdc.REQUEST_ID))
    }

    @Test
    fun `keeps a valid existing X-Request-Id`() {
        val given = "fixed-request-id"
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/test").header(LoggingMdc.REQUEST_ID, given),
        )
        filter.filter(exchange) { Mono.empty() }.block()
        assertEquals(given, exchange.response.headers.getFirst(LoggingMdc.REQUEST_ID))
    }

    @Test
    fun `regenerates X-Request-Id when the format is invalid`() {
        val malicious = "bad id\nInjected-Log-Line"
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/test").header(LoggingMdc.REQUEST_ID, malicious),
        )
        filter.filter(exchange) { Mono.empty() }.block()
        val result = exchange.response.headers.getFirst(LoggingMdc.REQUEST_ID)
        assertNotNull(result)
        assertNotEquals(malicious, result)
    }

    @Test
    fun `propagates X-Request-Id to the downstream request headers`() {
        val given = "downstream-request-id"
        var downstreamHeader: String? = null
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/test").header(LoggingMdc.REQUEST_ID, given),
        )

        filter.filter(exchange) { mutatedExchange ->
            downstreamHeader = mutatedExchange.request.headers.getFirst(LoggingMdc.REQUEST_ID)
            Mono.empty()
        }.block()

        assertEquals(given, downstreamHeader)
    }
}

/** HttpLoggingFilter — 메타데이터만 로깅하고 바디·쿼리스트링은 로깅하지 않는지 검증. */
class HttpLoggingFilterTest {

    private val filter = HttpLoggingFilter()

    @Test
    fun `logs only method path status duration and request id metadata`() {
        val logger = LoggerFactory.getLogger(HttpLoggingFilter::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)
        logger.level = Level.INFO

        try {
            val secretBody = "password=super-secret-value"
            val exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/sample/echo?token=leaked-query-value")
                    .header(LoggingMdc.REQUEST_ID, "log-test-request-id"),
            )
            exchange.response.headers.set(LoggingMdc.REQUEST_ID, "log-test-request-id")

            filter.filter(exchange) { Mono.empty() }.block()

            assertEquals(1, appender.list.size)
            val message = appender.list[0].formattedMessage
            assertTrue(message.contains("POST"))
            assertTrue(message.contains("/api/sample/echo"))
            assertTrue(message.contains("log-test-request-id"))
            assertFalse(message.contains(secretBody))
            assertFalse(message.contains("leaked-query-value"))
        } finally {
            logger.detachAppender(appender)
        }
    }
}
