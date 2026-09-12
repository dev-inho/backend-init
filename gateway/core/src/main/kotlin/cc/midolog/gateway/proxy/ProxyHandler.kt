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
                    Mono.error(RetryableStatusCodeException(status))
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
                        val unwrapped = Exceptions.unwrap(e)
                        unwrapped is TimeoutException || unwrapped is ConnectException || unwrapped is RetryableStatusCodeException
                    }
            )
            .onErrorResume { e ->
                gatewayError(e)
            }
            .doOnSuccess { response ->
                recordMetrics(targetUrl, response?.statusCode()?.value()?.toString() ?: "500", attemptCount > 1, timerSample)
            }
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

    private fun gatewayError(error: Throwable): Mono<ServerResponse> {
        val unwrapped = Exceptions.unwrap(if (error.javaClass.simpleName == "RetryExhaustedException") error.cause ?: error else error)
        val status = if (unwrapped is TimeoutException) {
            HttpStatus.GATEWAY_TIMEOUT
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
