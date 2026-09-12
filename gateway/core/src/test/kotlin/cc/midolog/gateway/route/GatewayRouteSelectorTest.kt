package cc.midolog.gateway.route

import cc.midolog.gateway.config.GatewayRouteProperties
import cc.midolog.gateway.config.GatewayHealthCheckProperties
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.http.HttpStatus
import reactor.core.publisher.Mono
import reactor.test.scheduler.VirtualTimeScheduler
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GatewayRouteSelectorTest {

    private lateinit var webClient: WebClient
    private lateinit var clock: Clock
    private lateinit var meterRegistry: SimpleMeterRegistry
    private lateinit var vts: VirtualTimeScheduler

    private var isAHealthy = true
    private var isBHealthy = true
    private var callCount = 0

    @BeforeEach
    fun setUp() {
        vts = VirtualTimeScheduler.getOrSet()
        val exchangeFunction = ExchangeFunction { request ->
            callCount++
            val url = request.url().toString()
            val status = if (url.contains("application-a")) {
                if (isAHealthy) HttpStatus.OK else HttpStatus.INTERNAL_SERVER_ERROR
            } else if (url.contains("application-b")) {
                if (isBHealthy) HttpStatus.OK else HttpStatus.INTERNAL_SERVER_ERROR
            } else {
                HttpStatus.OK
            }
            Mono.just(ClientResponse.create(status).build())
        }
        webClient = WebClient.builder().exchangeFunction(exchangeFunction).build()
        clock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneId.of("UTC"))
        meterRegistry = SimpleMeterRegistry()
        isAHealthy = true
        isBHealthy = true
        callCount = 0
    }

    @AfterEach
    fun tearDown() {
        VirtualTimeScheduler.reset()
    }

    @Test
    fun `single application url remains the default target`() {
        val selector = GatewayRouteSelector(
            GatewayRouteProperties(
                applicationUrl = "http://application.internal",
                batchUrl = "http://batch.internal",
            ), webClient, clock, meterRegistry
        )

        assertEquals("http://application.internal", selector.selectTarget("/api/users"))
        assertEquals("http://application.internal", selector.selectTarget("/actuator/health"))
        assertEquals("http://batch.internal", selector.selectTarget("/batch/jobs"))
    }

    @Test
    fun `multiple application urls are selected round robin`() {
        val selector = GatewayRouteSelector(
            GatewayRouteProperties(
                applicationUrl = "http://fallback.internal",
                applicationUrls = listOf(
                    "http://application-a.internal",
                    "http://application-b.internal",
                ),
                batchUrl = "http://batch.internal",
            ), webClient, clock, meterRegistry
        )

        assertEquals("http://application-a.internal", selector.selectTarget("/api/users"))
        assertEquals("http://application-b.internal", selector.selectTarget("/api/users"))
        assertEquals("http://application-a.internal", selector.selectTarget("/actuator/health"))
        assertEquals("http://batch.internal", selector.selectTarget("/batch/jobs"))
    }

    @Test
    fun `invalid route url fails fast with a clear message`() {
        val error = assertFailsWith<IllegalStateException> {
            GatewayRouteSelector(
                GatewayRouteProperties(
                    applicationUrl = "application.internal",
                    batchUrl = "http://batch.internal",
                ), webClient, clock, meterRegistry
            )
        }

        assertEquals(
            "gateway.routes.application-url must be an absolute http(s) URL: application.internal",
            error.message,
        )
    }

    @Test
    fun `고정 Clock 가상 시간에서 A가 3회 연속 실패하면 unhealthy가 되어 이후 선택에서 제외되고 B만 선택된다`() {
        val properties = GatewayRouteProperties(
            applicationUrls = listOf("http://application-a.internal", "http://application-b.internal"),
            batchUrl = "http://batch.internal",
        )
        properties.healthCheck.enabled = true
        properties.healthCheck.interval = Duration.ofSeconds(10)
        properties.healthCheck.unhealthyThreshold = 3
        properties.healthCheck.healthyThreshold = 3

        val selector = GatewayRouteSelector(properties, webClient, clock, meterRegistry)

        isAHealthy = false
        vts.advanceTimeBy(Duration.ofSeconds(30))

        assertEquals("http://application-b.internal", selector.selectTarget("/api/test"))
        assertEquals("http://application-b.internal", selector.selectTarget("/api/test"))
    }

    @Test
    fun `A가 unhealthy인 뒤 1회 성공하면 기본 healthy-threshold=1에 따라 복귀하고 다시 라운드로빈에 참여한다`() {
        val properties = GatewayRouteProperties(
            applicationUrls = listOf("http://application-a.internal", "http://application-b.internal"),
            batchUrl = "http://batch.internal",
        )
        properties.healthCheck.enabled = true
        properties.healthCheck.interval = Duration.ofSeconds(10)
        properties.healthCheck.unhealthyThreshold = 3
        properties.healthCheck.healthyThreshold = 3
        properties.healthCheck.healthyThreshold = 1

        val selector = GatewayRouteSelector(properties, webClient, clock, meterRegistry)

        isAHealthy = false
        vts.advanceTimeBy(Duration.ofSeconds(30))
        assertEquals("http://application-b.internal", selector.selectTarget("/api/test"))

        isAHealthy = true
        vts.advanceTimeBy(Duration.ofSeconds(10))

        val target1 = selector.selectTarget("/api/test")
        val target2 = selector.selectTarget("/api/test")
        assertEquals(setOf("http://application-a.internal", "http://application-b.internal"), setOf(target1, target2))
    }

    @Test
    fun `커스텀 unhealthy healthy threshold가 연속 성공 실패만 세며 반대 결과가 streak을 리셋한다`() {
        val properties = GatewayRouteProperties(
            applicationUrls = listOf("http://application-a.internal", "http://application-b.internal"),
            batchUrl = "http://batch.internal",
        )
        properties.healthCheck.enabled = true
        properties.healthCheck.interval = Duration.ofSeconds(10)
        properties.healthCheck.unhealthyThreshold = 3
        properties.healthCheck.healthyThreshold = 3

        val selector = GatewayRouteSelector(properties, webClient, clock, meterRegistry)

        isAHealthy = false
        vts.advanceTimeBy(Duration.ofSeconds(20))
        isAHealthy = true
        vts.advanceTimeBy(Duration.ofSeconds(10))

        val target1 = selector.selectTarget("/api/test")
        val target2 = selector.selectTarget("/api/test")
        assertEquals(setOf("http://application-a.internal", "http://application-b.internal"), setOf(target1, target2))

        isAHealthy = false
        vts.advanceTimeBy(Duration.ofSeconds(30))
        assertEquals("http://application-b.internal", selector.selectTarget("/api/test"))
    }

    @Test
    fun `A와 B가 모두 unhealthy이면 A-B-A 전체 대상으로 fail-open한다`() {
        val properties = GatewayRouteProperties(
            applicationUrls = listOf("http://application-a.internal", "http://application-b.internal"),
            batchUrl = "http://batch.internal",
        )
        properties.healthCheck.enabled = true
        properties.healthCheck.interval = Duration.ofSeconds(10)
        properties.healthCheck.unhealthyThreshold = 1

        val selector = GatewayRouteSelector(properties, webClient, clock, meterRegistry)
        isAHealthy = false
        isBHealthy = false
        vts.advanceTimeBy(Duration.ofSeconds(10))

        val t1 = selector.selectTarget("/api/test")
        val t2 = selector.selectTarget("/api/test")
        val t3 = selector.selectTarget("/api/test")

        assertEquals(setOf("http://application-a.internal", "http://application-b.internal"), setOf(t1, t2))
        assertEquals(t1, t3)
    }

    @Test
    fun `proxy 연결 실패를 한 번 기록하면 해당 target이 즉시 unhealthy가 되고 다음 요청 선택에서 제외된다`() {
        val properties = GatewayRouteProperties(
            applicationUrls = listOf("http://application-a.internal", "http://application-b.internal"),
            batchUrl = "http://batch.internal",
        )
        properties.healthCheck.enabled = true
        val selector = GatewayRouteSelector(properties, webClient, clock, meterRegistry)

        selector.markUnhealthy("http://application-a.internal")

        assertEquals("http://application-b.internal", selector.selectTarget("/api/test"))
        assertEquals("http://application-b.internal", selector.selectTarget("/api/test"))
    }

    @Test
    fun `enabled=false이면 virtual time이 지나도 health WebClient 호출 0회이고 기존 라우팅 그대로다`() {
        val properties = GatewayRouteProperties(
            applicationUrls = listOf("http://application-a.internal", "http://application-b.internal"),
            batchUrl = "http://batch.internal",
        )
        properties.healthCheck.enabled = false
        val selector = GatewayRouteSelector(properties, webClient, clock, meterRegistry)

        vts.advanceTimeBy(Duration.ofSeconds(30))
        assertEquals(0, callCount)

        val t1 = selector.selectTarget("/api/test")
        val t2 = selector.selectTarget("/api/test")
        assertEquals(setOf("http://application-a.internal", "http://application-b.internal"), setOf(t1, t2))
    }

    @Test
    fun `MeterRegistry에 target별 gauge가 1과 0으로 기록된다`() {
        val properties = GatewayRouteProperties(
            applicationUrls = listOf("http://application-a.internal"),
            batchUrl = "http://batch.internal",
        )
        properties.healthCheck.enabled = true

        val selector = GatewayRouteSelector(properties, webClient, clock, meterRegistry)

        assertEquals(1.0, meterRegistry.get("gateway.routes.healthy").tag("target", "http://application-a.internal").gauge().value())

        selector.markUnhealthy("http://application-a.internal")

        assertEquals(0.0, meterRegistry.get("gateway.routes.healthy").tag("target", "http://application-a.internal").gauge().value())
    }

    @Test
    fun `healthy threshold 도달 전에는 복구되지 않는다`() {
        val properties = GatewayRouteProperties(
            applicationUrls = listOf("http://application-a.internal", "http://application-b.internal"),
            batchUrl = "http://batch.internal",
        )
        properties.healthCheck.enabled = true
        properties.healthCheck.unhealthyThreshold = 1
        properties.healthCheck.healthyThreshold = 3
        
        val selector = GatewayRouteSelector(properties, webClient, clock, meterRegistry)
        
        isAHealthy = false
        vts.advanceTimeBy(Duration.ofSeconds(10))
        assertEquals("http://application-b.internal", selector.selectTarget("/api/test"))
        
        isAHealthy = true
        vts.advanceTimeBy(Duration.ofSeconds(20)) // 2 successes, threshold is 3
        
        val t3 = selector.selectTarget("/api/test")
        val t4 = selector.selectTarget("/api/test")
        assertEquals(setOf("http://application-b.internal"), setOf(t3, t4)) // Still unhealthy
        
        vts.advanceTimeBy(Duration.ofSeconds(10)) // 3 successes
        
        val t5 = selector.selectTarget("/api/test")
        val t6 = selector.selectTarget("/api/test")
        assertEquals(setOf("http://application-a.internal", "http://application-b.internal"), setOf(t5, t6)) // Recovered
    }
}
