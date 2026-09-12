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
 * 라우트 선택 결과에 따라 요청을 다운스트림 서버로 중계하고 응답을 반환하는 프록시 핸들러.
 *
 * 요청 본문은 [DataBuffer] 스트림으로 다운스트림에 전달하지만, 다운스트림 응답은
 * `bodyToMono(ByteArray::class.java)`를 통해 메모리에 바이트 배열로 전체 버퍼링한 뒤 반환한다.
 *
 * 버퍼링 트레이드오프:
 * 응답 본문을 메모리에 일괄 적재함으로써 응답 상태 코드 및 헤더 조작, 에러 복구가 단순해지지만,
 * 대용량 응답(파일 다운로드 등) 수신 시 게이트웨이 JVM 힙 메모리 사용량이 급증할 수 있는 트레이드오프가 있다.
 *
 * 에러 처리:
 * 다운스트림 호출 중 [TimeoutException]이 발생하면 504(GATEWAY_TIMEOUT)로 변환하고,
 * 그 외 연결 실패나 네트워크 예외는 502(BAD_GATEWAY)로 변환한다.
 */
class ProxyHandler(
    private val proxyWebClient: WebClient,
    private val routeSelector: GatewayRouteSelector,
) : HandlerFunction<ServerResponse> {

    override fun handle(request: ServerRequest): Mono<ServerResponse> = proxy(request)

    /**
     * 클라이언트 요청을 대상 다운스트림 서버로 중계하고 버퍼링된 응답을 반환한다.
     *
     * 요청 경로에 따라 [GatewayRouteSelector]로 대상 URL을 결정하고, hop-by-hop 헤더를 정제한 뒤
     * 요청 본문을 스트리밍 전달한다. 다운스트림 응답 헤더 정제 및 본문 버퍼링을 수행하며,
     * 다운스트림 타임아웃 발생 시 504, 기타 장애 시 502 상태 코드로 폴백한다.
     */
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
