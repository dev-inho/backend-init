package cc.midolog.gateway.config

import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * 프록시 중계용 [WebClient] 빈 설정.
 *
 * 느린 다운스트림 응답으로 인해 게이트웨이의 스레드 및 커넥션 자원이 고갈되지 않도록
 * 연결 타임아웃 3초(3,000ms), 응답 타임아웃 10초, 소켓 읽기 타임아웃 10초를 설정한다.
 *
 * 타임아웃 수치(3s/10s/10s)의 근거: 현재 코드와 문서상에 벤치마크나 정책적 근거가 없는
 * 초기 기본값이다(초기값, 근거 없음 — FUTURE 후보). 다운스트림 환경에 맞춘 설정 분리나
 * 재시도·서킷 브레이커 도입 검토가 필요하다.
 */
class WebClientConfig {
    @Bean
    fun proxyWebClient(): WebClient {
        val httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000)
            .responseTimeout(Duration.ofSeconds(10))
            .doOnConnected { conn -> conn.addHandlerLast(ReadTimeoutHandler(10, TimeUnit.SECONDS)) }
        return WebClient.builder()
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .build()
    }
}
