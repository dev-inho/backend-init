package cc.midolog.gateway.proxy

import cc.midolog.gateway.route.GatewayRouteSelector
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.server.HandlerFunction
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.Exceptions
import reactor.core.publisher.Mono
import java.net.URI
import java.util.concurrent.TimeoutException

/**
 * 게이트웨이 프록시 핸들러.
 * path prefix로 대상 비즈니스 서버를 결정하고, hop-by-hop 헤더를 제거해 전달한다.
 * query string과 response stream은 그대로 보존한다.
 */
@Component
class ProxyHandler(
    private val proxyWebClient: WebClient,
    private val routeSelector: GatewayRouteSelector,
) : HandlerFunction<ServerResponse> {

    override fun handle(request: ServerRequest): Mono<ServerResponse> = proxy(request)

    fun proxy(request: ServerRequest): Mono<ServerResponse> {
        val path = request.uri().rawPath
        val targetUrl = routeSelector.selectTarget(path)

        return proxyWebClient
            .method(HttpMethod.valueOf(request.method().name()))
            .uri(targetUri(targetUrl, request.uri()))
            .headers { it.addAll(HeaderSanitizer.sanitize(request.headers().asHttpHeaders())) }
            // 요청 본문(POST/PUT/PATCH)을 스트리밍으로 다운스트림에 전달한다. GET 등은 빈 스트림.
            .body(BodyInserters.fromDataBuffers(request.bodyToFlux(DataBuffer::class.java)))
            .exchangeToMono { response ->
                val responseHeaders = HeaderSanitizer.sanitize(response.headers().asHttpHeaders())
                response.bodyToMono(ByteArray::class.java)
                    .defaultIfEmpty(ByteArray(0))
                    .flatMap { body ->
                        ServerResponse.status(response.statusCode())
                            .headers { it.addAll(responseHeaders) }
                            .bodyValue(body)
                    }
            }
            .onErrorResume { e -> gatewayError(e) }
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
        val unwrapped = Exceptions.unwrap(error)
        val status = if (unwrapped is TimeoutException) {
            HttpStatus.GATEWAY_TIMEOUT
        } else {
            HttpStatus.BAD_GATEWAY
        }
        return ServerResponse.status(status).build()
    }
}
