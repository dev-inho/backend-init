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

/** 프록시용 WebClient — 연결/응답 타임아웃을 설정해 느린 다운스트림이 게이트웨이를 묶지 않게 한다. */
@Configuration
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
