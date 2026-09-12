package cc.midolog.gateway.proxy

import cc.midolog.gateway.config.GatewayRetryProperties
import cc.midolog.gateway.route.GatewayRouteSelector
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.server.HandlerFunction
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.Exceptions
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import java.net.ConnectException
import java.net.URI
import java.util.concurrent.TimeoutException

/**
 * 라우트 선택 결과에 따라 요청을 다운스트림 서버로 중계하고 응답을 반환하는 프록시 핸들러.
 *
 * 요청 본문은 [DataBuffer] 스트림으로 다운스트림에 전달하지만, 다운스트림 응답은
 * `bodyToMono(ByteArray::class.java)`를 통해 메모리에 바이트 배열로 전체 버퍼링한 뒤 반환한다.
 *
 * 버퍼링 트레이드오프:
 * 응답 본문을 메모리에 일괄 적재함으로써 응답 상태 코드 및 헤더 조작, 에러 복구가 단순해지지만,
 * 대용량 응답(파일 다운로드 등) 수신 시 게이트웨이 JVM 힙 메모리 사용량이 급증할 수 있는 트레이드오프가 있다.
 *
 * 에러 처리 및 재시도:
 * 다운스트림 호출 중 [TimeoutException]이 발생하면 504(GATEWAY_TIMEOUT)로 변환하고,
 * 그 외 연결 실패나 네트워크 예외는 502(BAD_GATEWAY)로 변환한다.
 * 요청 본문이 스트리밍이므로 GET, HEAD, OPTIONS와 같이 멱등성이 보장되고 본문이 없는 메서드만 재시도한다.
 * POST, PUT, PATCH, DELETE 등은 네트워크 단절 시 이미 데이터 일부가 전송되었을 수 있어 재전송하지 않는다.
 */
class ProxyHandler(
    private val proxyWebClient: WebClient,
    private val routeSelector: GatewayRouteSelector,
    private val retryProperties: GatewayRetryProperties = GatewayRetryProperties(),
    private val meterRegistry: MeterRegistry? = null
) : HandlerFunction<ServerResponse> {

    override fun handle(request: ServerRequest): Mono<ServerResponse> = proxy(request)

    fun proxy(request: ServerRequest): Mono<ServerResponse> {
        val path = request.uri().rawPath
        val targetUrl = routeSelector.selectTarget(path)
        val method = request.method().name()

        val timerSample = meterRegistry?.let { Timer.start(it) }
        var attemptCount = 0

        return proxyWebClient
            .method(HttpMethod.valueOf(method))
            .uri(targetUri(targetUrl, request.uri()))
            .headers { it.addAll(HeaderSanitizer.sanitize(request.headers().asHttpHeaders())) }
            .body(BodyInserters.fromDataBuffers(request.bodyToFlux(DataBuffer::class.java)))
            .exchangeToMono { response ->
                val status = response.statusCode().value()
                if (status == 502 || status == 503 || status == 504) {
                    response.releaseBody().then(Mono.error(RetryableStatusCodeException(status)))
                } else {
                    val responseHeaders = HeaderSanitizer.sanitize(response.headers().asHttpHeaders())
                    response.bodyToMono(ByteArray::class.java)
                        .defaultIfEmpty(ByteArray(0))
                        .flatMap { body ->
                            ServerResponse.status(response.statusCode())
                                .headers { it.addAll(responseHeaders) }
                                .bodyValue(body)
                        }
                }
            }
            .doOnSubscribe { attemptCount++ }
            .retryWhen(
                Retry.backoff(maxOf(0, retryProperties.maxAttempts - 1).toLong(), retryProperties.backoff)
                    .filter { e ->
                        if (method != "GET" && method != "HEAD" && method != "OPTIONS") return@filter false
                        isRetryableError(e)
                    }
            )
            .onErrorResume { e ->
                gatewayError(e, method)
            }
            .doOnSuccess { response ->
                recordMetrics(targetUrl, response?.statusCode()?.value()?.toString() ?: "500", attemptCount > 1, timerSample)
            }
            .doOnError { e ->
                val unwrapped = Exceptions.unwrap(if (e.javaClass.simpleName == "RetryExhaustedException") e.cause ?: e else e)
                val status = if (unwrapped is TimeoutException) "504" else if (unwrapped is RetryableStatusCodeException) unwrapped.statusCode.toString() else "502"
                recordMetrics(targetUrl, status, attemptCount > 1, timerSample)
            }
    }

    private fun isRetryableError(e: Throwable): Boolean {
        val unwrapped = Exceptions.unwrap(e)
        if (unwrapped is TimeoutException || unwrapped is RetryableStatusCodeException) return true
        var cause: Throwable? = unwrapped
        while (cause != null) {
            if (cause is ConnectException || cause.javaClass.simpleName == "AnnotatedConnectException") return true
            cause = cause.cause
        }
        return false
    }

    private fun targetUri(targetUrl: String, requestUri: URI): URI =
        URI.create(buildString {
            append(targetUrl.trimEnd('/'))
            append(requestUri.rawPath)
            requestUri.rawQuery?.let { query ->
                append('?')
                append(query)
            }
        })

    private fun gatewayError(error: Throwable, method: String): Mono<ServerResponse> {
        val unwrapped = Exceptions.unwrap(if (error.javaClass.simpleName == "RetryExhaustedException") error.cause ?: error else error)

        val status = if (unwrapped is TimeoutException) {
            HttpStatus.GATEWAY_TIMEOUT
        } else if (unwrapped is RetryableStatusCodeException && (method == "GET" || method == "HEAD" || method == "OPTIONS")) {
            HttpStatus.valueOf(unwrapped.statusCode)
        } else {
            HttpStatus.BAD_GATEWAY
        }
        return ServerResponse.status(status).build()
    }

    private fun recordMetrics(target: String, status: String, retried: Boolean, timerSample: Timer.Sample?) {
        meterRegistry?.let { registry ->
            registry.counter("gateway.proxy.requests", "target", target, "status", status, "retried", retried.toString()).increment()
            timerSample?.stop(registry.timer("gateway.proxy.latency"))
        }
    }
}

class RetryableStatusCodeException(val statusCode: Int) : RuntimeException("Upstream returned $statusCode")
