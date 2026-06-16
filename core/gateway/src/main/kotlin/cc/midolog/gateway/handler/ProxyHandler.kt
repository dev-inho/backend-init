package cc.midolog.gateway.handler

import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.publisher.Mono

/**
 * 게이트웨이 프록시 핸들러.
 * path prefix로 대상 비즈니스 서버를 결정하고, hop-by-hop 헤더를 제거해 전달한다.
 */
@Component
class ProxyHandler(
    private val proxyWebClient: WebClient,
    @Value("\${gateway.routes.application-url}") private val applicationUrl: String,
    @Value("\${gateway.routes.batch-url}") private val batchUrl: String,
) {
    fun proxy(request: ServerRequest): Mono<ServerResponse> {
        val path = request.path()
        val targetUrl = when {
            path.startsWith("/api/") || path.startsWith("/actuator/") -> applicationUrl
            path.startsWith("/batch/") -> batchUrl
            else -> applicationUrl
        }

        return proxyWebClient
            .method(HttpMethod.valueOf(request.method().name()))
            .uri("$targetUrl$path")
            .headers { it.addAll(HeaderSanitizer.sanitize(request.headers().asHttpHeaders())) }
            // 요청 본문(POST/PUT/PATCH)을 스트리밍으로 다운스트림에 전달한다. GET 등은 빈 스트림.
            .body(BodyInserters.fromDataBuffers(request.bodyToFlux(DataBuffer::class.java)))
            .exchangeToMono { response ->
                response.bodyToMono(ByteArray::class.java)
                    .defaultIfEmpty(ByteArray(0))
                    .flatMap { bytes ->
                        ServerResponse.status(response.statusCode())
                            .headers { it.addAll(HeaderSanitizer.sanitize(response.headers().asHttpHeaders())) }
                            .bodyValue(bytes)
                    }
            }
    }
}
