# 게이트웨이 (Gateway)

> 모든 요청의 단일 진입점 역할을 수행하며, 트랜잭션 ID를 부여·전파하고 path-prefix 기반 라우팅으로 비즈니스 서버에 요청을 분기합니다.

## 1. 역할 및 책임

### 1.1 주요 기능
- **단일 진입점**: 모든 클라이언트 요청이 게이트웨이(포트 8080)를 통해 진입
- **트랜잭션 ID 관리**: 요청 헤더에서 트랜잭션 ID를 추출하거나 생성하여 전체 요청 생명주기에서 추적 가능하게 관리
- **라우팅·프록시**: path-prefix에 따라 비즈니스 서버(application, batch) 또는 자체 모니터링 엔드포인트로 분기
- **회복성 및 헬스체크**: 멱등 요청(GET/HEAD/OPTIONS)에 대한 자동 재시도 및 다운스트림 비즈니스 서버 능동 헬스체크 기반 라운드로빈 분산/장애 격리(fail-open)
- **메트릭 및 관측성**: Micrometer 연동 프록시 트래픽 계측(`gateway.proxy.requests`, `gateway.proxy.latency`, `gateway.routes.healthy`) 및 Spring Boot Actuator 기본 엔드포인트(`health,metrics`) 제공
- **요청 로깅 및 모니터링**: MDC(Mapped Diagnostic Context) 연동으로 로그 추적성 확보

### 1.2 동작 흐름
```
클라이언트 요청
    ↓
[게이트웨이 8080]
    ↓
1. HttpLoggingFilter (support:web, @Order(-2))
   - 하는 일: 최외곽 요청 시작 시각 계측 및 응답 완료 시(doFinally) 메타데이터(method, path, status, duration, requestId) INFO 로깅
   - 거절 시: 거절 없음 (관측 전용 통과). 뒤쪽 필터에서 429/401로 단축 종료되더라도 최외곽에서 완료 로그는 항상 기록
    ↓
2. AuthTokenRateLimitFilter (gateway:core, @Order(-1))
   - 하는 일: POST /api/auth/token 대상 클라이언트 IP별 요청 빈도 제한 (10회/60초, Redis Lua fail-open)
   - 거절 시: 429 Too Many Requests 반환 (본문 없음, 체인 중단) / Redis 오류 시 fail-open 통과
   - 단축 경로: 한도 초과 시 chain.filter를 호출하지 않고 즉시 응답하므로 뒤쪽 RequestIdFilter, JwtAuthFilter, RequestVisibilityFilter는 실행되지 않음
    ↓
3. RequestIdFilter (support:web, @Order(0))
   - 하는 일: X-Request-Id 헤더 추출(형식 검증) 또는 UUID 생성, 요청/응답 헤더 전파 및 Reactor Context/MDC 바인딩
   - 거절 시: 거절 없음 (요청 식별자 부여 후 통과)
    ↓
4. JwtAuthFilter (gateway:core, @Order(1))
   - 하는 일: Authorization Bearer 토큰 서명 및 유효기간 검증 (JwtCodec 위임, 검증 성공 시 SecurityContext 주입 없이 그대로 통과)
   - 경로 정책: /api/auth/, /actuator/, /batch/ 는 미검증 통과 / /api/, /internal/gateway/ 는 검증 필수
   - 거절 시: 401 Unauthorized 반환 (본문 없음, 체인 중단) / 미매핑 경로는 체인에 넘겨 404 위임
   - 단축 경로: 인증 실패 시 chain.filter를 호출하지 않고 즉시 응답하므로 뒤쪽 RequestVisibilityFilter는 실행되지 않음
    ↓
5. RequestVisibilityFilter (gateway:core, @Order(100), 조건부 활성화)
   - 하는 일: gateway.request-visibility.enabled=true 시 완료 시점(doFinally)에 요청 메타데이터를 인메모리 링 버퍼에 기록
   - 도달 조건: 앞선 보안/식별 필터를 통과하여 해당 필터(@Order(100))까지 도달한 요청만 기록 (429/401 단축 요청은 미기록)
   - 거절 시: 거절 없음 (관측 전용)
    ↓
라우팅 및 헬스체크 결정 (RouteConfig & GatewayRouteSelector)
    ├─ /api/** → application(8081) 프록시 (healthy 타겟 대상 라운드로빈, 모두 unhealthy 시 fail-open)
    ├─ /batch/** → batch(8082) 프록시 (단일 타겟, 헬스체크 대상 제외)
    ├─ /actuator/** → application(8081) 프록시 (주의: application은 /actuator/health 외 denyAll)
    └─ /internal/gateway/requests → gateway 자체 컨트롤러(8080)
    ↓
HTTP 프록시 중계 및 메트릭 계측 (ProxyHandler)
    ├─ 멱등 요청(GET/HEAD/OPTIONS) 실패 시 재시도 (최대 1..3회, 100ms 지수 backoff)
    ├─ 연결 실패 시 즉시 unhealthy 마킹 (Timeout/503 제외)
    └─ 메트릭 기록 (requests, latency, healthy gauge)
    ↓
응답 + X-Request-Id 헤더 반환 (최외곽 로깅 기록 및 도달 요청에 대한 가시성 이벤트 저장 완료)
```

---

## 2. 트랜잭션 ID (Request ID)

### 2.1 개요
트랜잭션 ID는 클라이언트가 보낸 요청에서 시작하여 모든 다운스트림 서버를 거쳐 로그에 일관되게 기록되는 추적 식별자입니다.

### 2.2 RequestIdFilter 구현
**패키지**: `cc.midolog.web.filter.RequestIdFilter` (`support:web`)
**타입**: WebFilter (`@Order(0)`)

#### 동작 방식
```kotlin
// 실제 소유 모듈: support:web
class RequestIdFilter : WebFilter {
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val requestId = resolveRequestId(exchange.request.headers.getFirst("X-Request-Id"))
        val mutated = exchange.mutate()
            .request(exchange.request.mutate().header("X-Request-Id", requestId).build())
            .build()
        mutated.response.headers.set("X-Request-Id", requestId)
        return chain.filter(mutated).contextWrite { it.put("X-Request-Id", requestId) }
    }
}
```

#### 주요 특징
- **요청 헤더 읽기**: `X-Request-Id` 헤더가 허용 형식이면 사용, 없거나 형식 위반이면 새 UUID 생성
- **다운스트림 전파**: ProxyHandler가 다운스트림 요청 시 해당 헤더 자동 포함
- **응답 포함**: 클라이언트가 응답 헤더에서 트랜잭션 ID를 확인 가능
- **MDC 연동**: 게이트웨이와 다운스트림 서버의 로그에 requestId가 자동으로 기록되어 요청 추적 용이

### 2.3 트랜잭션 흐름 예시
```
클라이언트 요청:
  GET /api/users/1
  (헤더 없음)

게이트웨이:
  X-Request-Id 생성: 550e8400-e29b-41d4-a716-446655440000
  MDC에 등록, 응답 헤더에 추가

다운스트림(application:8081):
  X-Request-Id: 550e8400-e29b-41d4-a716-446655440000 ← 자동 전파
  로그: [550e8400...] UserService.getUser()

클라이언트 응답:
  200 OK
  X-Request-Id: 550e8400-e29b-41d4-a716-446655440000 ← 클라이언트가 추적 가능
```

---

## 3. 라우팅 및 프록시

### 3.0 패키지 구조
게이트웨이 모듈(`gateway/*`)의 소스 코드는 책임에 따라 다음과 같이 분리되어 있습니다:

```
gateway/
├── core/
│   └── src/main/kotlin/cc/midolog/gateway/
│       ├── config/
│       │   ├── GatewayClockConfig.kt
│       │   ├── GatewayRetryProperties.kt
│       │   ├── GatewayRouteProperties.kt
│       │   ├── RouteConfig.kt
│       │   └── WebClientConfig.kt
│       ├── filter/
│       │   ├── AuthTokenRateLimitFilter.kt
│       │   └── JwtAuthFilter.kt
│       ├── proxy/
│       │   ├── HeaderSanitizer.kt
│       │   └── ProxyHandler.kt
│       ├── ratelimit/
│       │   ├── RateLimiter.kt
│       │   └── RedisRateLimiter.kt
│       ├── route/
│       │   └── GatewayRouteSelector.kt
│       └── visibility/
│           ├── RequestEventStore.kt
│           ├── RequestVisibilityController.kt
│           ├── RequestVisibilityEvent.kt
│           ├── RequestVisibilityFilter.kt
│           └── RequestVisibilityProperties.kt
├── autoconfigure/
│   └── src/main/
│       ├── kotlin/cc/midolog/gateway/autoconfigure/
│       │   ├── GatewayAutoConfiguration.kt
│       │   └── GatewayModeProperties.kt
│       └── resources/META-INF/spring/
│           └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
├── starter/
│   └── build.gradle
└── app/
    ├── src/main/kotlin/cc/midolog/
    │   └── GatewayApplication.kt
    └── src/main/resources/
        ├── application.yml
        └── application-local.yml
```

### 3.1 RouteConfig 및 GatewayRouteSelector (라우팅 규칙)
**패키지**: `cc.midolog.gateway.config.RouteConfig`, `cc.midolog.gateway.route.GatewayRouteSelector`

현재 구현은 WebFlux `router {}` DSL을 사용하여 라우팅 규칙을 정의합니다.

```kotlin
// 실제 구현: RouteConfig.kt
@Configuration
class RouteConfig(
    private val proxyHandlerFunction: HandlerFunction<ServerResponse>,
) {
    @Bean
    fun routes(): RouterFunction<ServerResponse> = router {
        path("/api/**").invoke(proxyHandlerFunction::handle)
        path("/batch/**").invoke(proxyHandlerFunction::handle)
        path("/actuator/**").invoke(proxyHandlerFunction::handle)
    }
}
```

#### 설계 특징
- **`HandlerFunction` 주입**: 구체 핸들러 구현체(`cc.midolog.gateway.proxy.ProxyHandler`)에 직접 의존하지 않고 스프링 표준 `HandlerFunction<ServerResponse>` 인터페이스를 주입받습니다. 이는 `config` 패키지가 `proxy`나 `route` 패키지를 참조하지 못하도록 격리해 순환 의존성을 방지하기 위함이며, 이 아키텍처 규칙은 `GatewayPackageDependencyTest`(`config package does not import handler or route`)로 보장됩니다.
- **`GatewayRouteProperties`**: `gateway.routes` 접두사로 바인딩되며, 단일 애플리케이션 URL(`applicationUrl` / 환경변수 `GATEWAY_APPLICATION_URL`), 복수 애플리케이션 URL 목록(`applicationUrls` / 환경변수 `GATEWAY_APPLICATION_URLS`), 배치 URL(`batchUrl` / 환경변수 `GATEWAY_BATCH_URL`)과 함께 대상 서버의 가용성을 능동 점검하기 위한 `healthCheck: GatewayHealthCheckProperties` 설정을 관리합니다.
- **`GatewayRouteSelector` (라우트 선택, 능동 헬스체크 및 Fail-Open)**:
  - **라우팅 분기**: `/batch/**` 경로는 배치 전용 서버(`batchUrl`)로 분기하고, `/api/**` 및 `/actuator/**` 등 그 외 모든 경로는 비즈니스 애플리케이션 서버(`applicationTargets`)로 분기합니다.
  - **헬스체크 대상 및 배치 제외**: 상태 점검 및 가용성 추적은 오직 비즈니스 애플리케이션 서버 타겟(`applicationTargets`)에만 적용됩니다. 배치 서버(`batchUrl`)는 내부 작업용 단일 타겟으로서 헬스체크 대상에서 제외되며 상태를 별도로 추적하지 않습니다 (`ProxyHandlerTest`의 `Batch target connection failure does not change application target states`로 검증).
  - **초기 상태**: 모든 application 타겟은 기동 시 초기에 항상 정상(`isHealthy = true`, `successStreak = 0`, `failureStreak = 0`) 상태로 시작합니다.
  - **능동 헬스체크 (Active Health Check)**:
    - `gateway.routes.health-check.enabled=true` 시 백그라운드에서 `Flux.interval(properties.healthCheck.interval)`를 구독하여 주기적으로 각 application 타겟의 `${target}${properties.healthCheck.path}`로 HTTP GET 요청을 전송합니다.
    - **성공 판정 (2xx)**: `response.statusCode().is2xxSuccessful`인 경우 성공으로 간주합니다. 실패 연속 횟수(`failureStreak`)는 0으로 리셋되고 성공 연속 횟수(`successStreak`)가 1 증가합니다. 비정상(unhealthy) 상태였던 타겟은 `successStreak >= properties.healthCheck.healthyThreshold`에 도달하면 `isHealthy = true`로 복구되어 라우팅 대상에 복귀합니다 (`GatewayRouteSelectorTest`의 `A가 unhealthy인 뒤 1회 성공하면 기본 healthy-threshold=1에 따라 복귀하고 다시 라운드로빈에 참여한다`로 검증).
    - **실패 판정 (비2xx 및 오류)**: 비2xx 응답이 반환되거나 통신 예외/에러(`onErrorResume`)가 발생하면 실패로 간주합니다. 성공 연속 횟수(`successStreak`)는 0으로 리셋되고 실패 연속 횟수(`failureStreak`)가 1 증가합니다. 정상(healthy) 상태였던 타겟은 `failureStreak >= properties.healthCheck.unhealthyThreshold`에 도달하면 `isHealthy = false`로 전이되어 라우팅 대상에서 제외됩니다 (`GatewayRouteSelectorTest`의 `고정 Clock 가상 시간에서 A가 3회 연속 실패하면 unhealthy가 되어 이후 선택에서 제외되고 B만 선택된다`로 검증).
    - **Streak 리셋 규칙**: 성공/실패가 교차 발생하면 반대 상태의 streak 카운터는 즉시 0으로 리셋되므로, 오직 지정된 횟수만큼 연속으로 성공하거나 연속으로 실패해야만 상태가 전이됩니다 (`GatewayRouteSelectorTest`의 `커스텀 unhealthy healthy threshold가 연속 성공 실패만 세며 반대 결과가 streak을 리셋한다`로 검증).
  - **수동 연결 실패 마킹 (`markUnhealthy`)**:
    - 주기적 헬스체크 주기 사이에도, `ProxyHandler`에서 실제 클라이언트 요청을 중계하는 도중 `ConnectException`, `AnnotatedConnectException` 등 네트워크 레벨의 연결 실패(`isConnectionFailure`)가 발생하면 즉시 `routeSelector.markUnhealthy(targetUrl)`을 호출합니다.
    - 이 경우 threshold 주기를 기다리지 않고 즉각 해당 타겟의 `failureStreak`를 `unhealthyThreshold`로 채우고 `isHealthy = false`로 강제 전환하여, 후속 요청들이 연결 실패나 대기 시간 없이 즉시 다른 정상 타겟으로 라우팅되도록 보장합니다 (`ProxyHandlerTest`의 `Connection exception marks target as unhealthy immediately`로 검증).
    - **수동 마킹 제외 대상**: 타임아웃(`TimeoutException`)과 업스트림 503(`RetryableStatusCodeException(503)`)은 프로세스가 살아있고 일시적인 과부하/지연일 수 있으므로 수동 연결 실패 마킹 대상이 아닙니다 (`ProxyHandlerTest`의 `Timeout and 503 do not mark target as unhealthy`로 검증).
  - **Healthy-only 라운드로빈 및 전체 Fail-Open**:
    - 정상 상태(`targetStates[it]?.isHealthy == true`)인 타겟만 선별하여 `AtomicInteger` 기반 카운터로 라운드로빈 분산합니다. 카운터가 `Int.MAX_VALUE`에 도달하면 0으로 원자적 회전시켜 오버플로로 인한 음수 인덱스를 방지합니다.
    - **전체 Fail-Open 정책**: 만약 장애나 네트워크 파티션으로 인해 모든 application 타겟이 비정상(`healthyTargets.isEmpty()`)으로 판정되면, 게이트웨이가 트래픽을 거부하지 않고 설정된 전체 타겟(`applicationTargets`)으로 fallback하여 라운드로빈 분산을 계속 유지합니다 (all-unhealthy 전체 fail-open). 이는 헬스체크 자체의 오판이나 일시적 이슈로 전체 서비스가 전면 중단되는 것을 방지하기 위한 가용성 최우선 fail-open 트레이드오프입니다 (`GatewayRouteSelectorTest`의 `A와 B가 모두 unhealthy이면 A-B-A 전체 대상으로 fail-open한다`로 검증).
  - **리소스 정리 (`DisposableBean`)**:
    - `GatewayRouteSelector`는 Spring `DisposableBean`을 구현하여, 애플리케이션 컨텍스트 종료 시 `destroy()`가 호출되면서 백그라운드 헬스체크 subscription(`healthCheckDisposable?.dispose()`)을 안전하게 해제하여 스레드 및 메모리 누수를 방지합니다 (`GatewayRouteSelectorTest`의 `destroy 호출 시 subscription이 해제되어 WebClient 호출이 더 이상 발생하지 않는다`로 검증).
  - **기동 시 Fail-Fast 검증**:
    - 생성자 초기화 시점에 설정된 모든 URL의 http/https 스킴 및 호스트 존재 여부를 엄격히 검증하여 잘못된 설정 시 기동 단계에서 즉시 fail-fast(`IllegalStateException`)합니다 (`GatewayRouteSelectorTest`의 `invalid route url fails fast with a clear message`로 검증).

### 3.2 ProxyHandler (HTTP 프록시)
**패키지**: `cc.midolog.gateway.proxy.ProxyHandler`

WebClient를 사용하여 다운스트림 서버로 실제 요청을 전달하고, 멱등 요청 재시도와 메트릭 계측을 수행합니다.

```kotlin
// 실제 구현: ProxyHandler.kt
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
            // 요청 본문(POST/PUT/PATCH)을 스트리밍으로 다운스트림에 전달한다. GET 등은 빈 스트림.
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
            .doOnError { e ->
                if (isConnectionFailure(e)) {
                    routeSelector.markUnhealthy(targetUrl)
                }
            }
            .retryWhen(
                Retry.backoff(maxOf(0, retryProperties.maxAttempts - 1).toLong(), retryProperties.backoff).jitter(0.0)
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
    }
    // ...
}
```

#### 요청 본문 스트리밍 / 응답 본문 버퍼링
`ProxyHandler`는 요청 본문과 응답 본문을 대칭적으로 다루지 않습니다:
- **요청 본문 스트리밍**: 클라이언트의 요청 본문은 `request.bodyToFlux(DataBuffer::class.java)` 및 `BodyInserters.fromDataBuffers(...)`를 통해 버퍼링 없이 다운스트림 서버로 스트리밍 전달됩니다 (GET 등 본문 없는 요청은 빈 스트림).
- **응답 본문 버퍼링**: 다운스트림 서버의 응답 본문은 `bodyToMono(ByteArray::class.java)`를 통해 메모리에 바이트 배열로 전체 버퍼링한 뒤 `ServerResponse.bodyValue(body)`로 클라이언트에 반환합니다 (`ProxyHandlerTest`의 `buffers downstream response body and sanitizes response headers`로 보증).

> **응답 버퍼링 트레이드오프**
> 응답 본문을 메모리에 일괄 적재(버퍼링)함으로써 응답 상태 코드 및 헤더 조작, 에러 복구 처리가 단순해지는 장점이 있습니다. 반면 대용량 응답(대용량 파일 다운로드, 대규모 JSON 목록 등)을 수신할 때 게이트웨이 인스턴스의 JVM 힙 메모리 사용량이 급증하여 OOM(Out of Memory) 위험이 발생할 수 있는 트레이드오프가 존재합니다. 대용량 응답의 스트리밍 전환(DataBuffer pass-through)은 [`./FUTURE.md`](./FUTURE.md)의 후보 과제입니다.

#### 재시도 정책 및 비멱등 메서드 제외 이유
- **비멱등 메서드 재시도 절대 금지 이유**: 요청 본문이 `DataBuffer` 스트림으로 버퍼링 없이 다운스트림에 전달되므로, 네트워크 단절이나 일시 오류가 발생했을 때 이미 요청 본문의 일부 바이트가 업스트림 서버로 흘러 들어갔을 수 있습니다. 비멱등 메서드(`POST`, `PUT`, `PATCH`, `DELETE`)를 재전송할 경우 결제 중복, 주문 중복 생성, 리소스 중복 수정/삭제 등 치명적인 부작용을 초래할 수 있으므로, 비멱등 메서드는 절대 재시도하지 않습니다 (`method != "GET" && method != "HEAD" && method != "OPTIONS"` 시 즉시 재시도 필터 거절).
- **재시도 대상 메서드**: `GET`, `HEAD`, `OPTIONS` (멱등성이 보장되고 요청 본문이 없는 메서드만 재시도 허용).
- **재시도 대상 오류 (`isRetryableError`)**:
  - 다운스트림 연결 실패 (`ConnectException`, `AnnotatedConnectException`)
  - 다운스트림 응답 타임아웃 (`TimeoutException`)
  - 업스트림 재시도 가능 HTTP 상태 코드 (`502 Bad Gateway`, `503 Service Unavailable`, `504 Gateway Timeout`)
- **비재시도 오류**: `500 Internal Server Error`, `501 Not Implemented` 등 업스트림 애플리케이션 에러 및 모든 비멱등 요청은 재시도하지 않습니다 (`ProxyHandlerTest`의 `Does not retry on 500 or 501`, `Non-idempotent requests do not retry on 503 and return 502 according to policy`로 검증).
- **재시도 횟수 및 백오프**:
  - `gateway.proxy.retry.max-attempts=1` (기본값 1, 허용 범위 1..3, 첫 번째 시도를 포함한 총 시도 횟수). 값이 1이면 재시도를 하지 않고, 3이면 첫 실패 후 최대 2회 추가 재시도합니다.
  - `gateway.proxy.retry.backoff=100ms` (기본값 100ms, 음수 불가, `jitter(0.0)`로 100ms, 200ms와 같이 결정론적 지수 backoff 적용) (`ProxyHandlerTest`의 `Backoff delays exponentially`로 검증).

#### 에러 처리 및 최종 상태 변환 (`gatewayError`)
재시도 시도가 모두 소진(`Exceptions.isRetryExhausted`)되거나 재시도 대상이 아닌 오류가 발생하면 최종 HTTP 응답으로 변환합니다:
- **멱등 요청의 502/503/504 상태 코드 보존**: 멱등 요청(GET/HEAD/OPTIONS)에서 업스트림이 502, 503, 504를 반환하여 재시도를 모두 소진한 경우, 게이트웨이가 임의의 502로 덮어쓰지 않고 업스트림이 반환했던 원래의 마지막 상태 코드(`502 Bad Gateway`, `503 Service Unavailable`, `504 Gateway Timeout`)를 그대로 보존하여 클라이언트에 반환합니다 (`HttpStatus.valueOf(unwrapped.statusCode)`, `ProxyHandlerTest`의 `Returns identical upstream status when max-attempts exhausted for idempotent requests`로 검증).
- **타임아웃 (`TimeoutException`)**: 다운스트림 호출 타임아웃 시 `504 Gateway Timeout`으로 변환합니다 (`ProxyHandlerTest`의 `returns gateway timeout when downstream call times out`, `Idempotent requests retry on TimeoutException`으로 검증).
- **비멱등 요청 및 기타 연결 실패/네트워크 오류**: `502 Bad Gateway`로 변환합니다. 비멱등 요청(POST/PUT/PATCH/DELETE)이 업스트림 503을 받았을 때도 재시도 없이 502 Bad Gateway로 변환됩니다 (`ProxyHandlerTest`의 `Non-idempotent requests do not retry on 503 and return 502 according to policy`로 검증).

#### 타임아웃 값 (`config/WebClientConfig.kt`)
다운스트림 응답 지연으로 인한 스레드 및 커넥션 자원 고갈을 방지하기 위해 다음 타임아웃을 적용합니다:
- **연결 타임아웃 (Connect Timeout)**: 3초 (`ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000`)
- **응답 타임아웃 (Response Timeout)**: 10초 (`responseTimeout(Duration.ofSeconds(10))`)
- **소켓 읽기 타임아웃 (Read Timeout)**: 10초 (`ReadTimeoutHandler(10, TimeUnit.SECONDS)`)

*(타임아웃 수치 3초/10초/10초는 초기 기본값입니다. 다운스트림 환경별 타임아웃 분리 및 서킷 브레이커 도입은 [`./FUTURE.md`](./FUTURE.md) 후보 과제입니다.)*

#### hop-by-hop 헤더 제거 (`proxy/HeaderSanitizer.kt`)
RFC 7230 §6.1 및 RFC 9110 §7.6.1 규격에 따라 단일 전송 레벨 연결에 국한된 헤더를 제거합니다:
- `Connection`, `Keep-Alive`, `Proxy-Authenticate`, `Proxy-Authorization`, `TE`, `Trailer`, `Transfer-Encoding`, `Upgrade` 및 클라이언트 `Connection` 헤더에 명시된 커스텀 홉 헤더 제거.
- 다운스트림 가상 호스트 라우팅과의 충돌 방지를 위해 `Host` 헤더 제거.
- 프록시 중계 과정에서 바이트 수 불일치 왜곡을 방지하기 위해 `Content-Length` 헤더 제거 (클라이언트가 재계산하도록 유도).
- 요청 중계 및 응답 반환 양방향으로 적용 (`ProxyHandlerTest`의 `forwards path query and sanitized headers to application route`로 검증).

### 3.3 설정 파일 및 프로퍼티 (application.yml)
게이트웨이 애플리케이션(`gateway:app`)의 실제 `application.yml` 및 `application-local.yml` 설정 파일 내용입니다:

```yaml
# gateway/app/src/main/resources/application.yml
server:
  port: 8080

spring:
  application:
    name: backend-gateway

gateway:
  mode: standalone
  routes:
    application-url: ${GATEWAY_APPLICATION_URL}
    application-urls: ${GATEWAY_APPLICATION_URLS:}
    batch-url: ${GATEWAY_BATCH_URL}
  rate-limit:
    auth-token:
      limit: 10
      window-seconds: 60
  request-visibility:
    enabled: false
    capacity: 200

jwt:
  secret: ${JWT_SECRET}

management:
  endpoints:
    web:
      exposure:
        include: health,metrics

---
# gateway/app/src/main/resources/application-local.yml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6380}

gateway:
  routes:
    application-url: ${GATEWAY_APPLICATION_URL:http://localhost:8081}
    application-urls: ${GATEWAY_APPLICATION_URLS:}
    batch-url: ${GATEWAY_BATCH_URL:http://localhost:8082}
  request-visibility:
    enabled: ${GATEWAY_REQUEST_VISIBILITY_ENABLED:false}
    capacity: ${GATEWAY_REQUEST_VISIBILITY_CAPACITY:200}
```

#### 재시도, 헬스체크 및 관리 엔드포인트 핵심 설정 키

게이트웨이의 재시도 정책(`GatewayRetryProperties`), 헬스체크(`GatewayHealthCheckProperties`), 액추에이터 엔드포인트 노출은 아래 프로퍼티 키로 제어됩니다:

| 설정 키 | 기본값 | 허용 범위 / 형식 | 설명 |
|---|:---:|:---:|---|
| `gateway.proxy.retry.max-attempts` | `1` | `1..3` | 첫 번째 시도를 포함한 총 시도 횟수. 1이면 재시도 없음, 3이면 첫 실패 후 최대 2회 재시도 (`GatewayRetryPropertiesTest`로 검증). |
| `gateway.proxy.retry.backoff` | `100ms` | Duration (음수 불가) | 재시도 시 적용할 기본 백오프 간격. `jitter(0.0)`의 결정론적 지수 backoff가 적용됨 (`GatewayRetryPropertiesTest`로 검증). |
| `gateway.routes.health-check.enabled` | `false` | `true`, `false` | 백엔드 application 타겟 대상 능동 헬스체크 활성화 여부 (`GatewayRoutePropertiesTest`로 검증). |
| `gateway.routes.health-check.path` | `/actuator/health` | String (`/` 시작 필수) | 헬스체크 핑(ping)을 전송할 엔드포인트 상대 경로 (`GatewayRoutePropertiesTest`로 검증). |
| `gateway.routes.health-check.interval` | `10s` | Duration (`> 0s`) | 헬스체크 주기 (`GatewayRoutePropertiesTest`로 검증). |
| `gateway.routes.health-check.unhealthy-threshold` | `3` | Int (`> 0`) | 타겟을 비정상(unhealthy)으로 판정하여 라우팅에서 제외하기 위한 연속 실패 횟수 (`GatewayRoutePropertiesTest`로 검증). |
| `gateway.routes.health-check.healthy-threshold` | `1` | Int (`> 0`) | 제외된 타겟을 정상(healthy)으로 복구하여 라우팅에 복귀시키기 위한 연속 성공 횟수 (`GatewayRoutePropertiesTest`로 검증). |
| `management.endpoints.web.exposure.include` | `health,metrics` | 콤마 구분 문자열 | 게이트웨이 자체 Spring Boot Actuator 웹 노출 엔드포인트 목록. prometheus는 기본 노출되지 않으며 명시적 opt-in 필요 (`ActuatorEndpointIntegrationTest`로 검증). |

`application.yml`은 profile을 암묵 활성화하지 않습니다. 로컬 실행 시 `SPRING_PROFILES_ACTIVE=local JWT_SECRET=<32바이트 이상>`을 명시합니다 (`GatewayProfileConfigTest`로 검증).

`gateway.routes.application-urls`를 쉼표 구분 목록으로 지정하면 `/api/**`와 `/actuator/**` 대상 application 서버를 라운드로빈으로 선택합니다. 값이 없으면 기존 `gateway.routes.application-url` 단일 대상 설정을 그대로 사용합니다. `/batch/**`는 항상 `gateway.routes.batch-url` 단일 대상으로 전달합니다.

Request visibility는 기본 비활성화입니다. `GATEWAY_REQUEST_VISIBILITY_ENABLED=true`로 명시한 경우에만 `/internal/gateway/requests` endpoint와 in-memory event store가 활성화됩니다. 저장 항목은 method, path, status, request id, timestamp, duration으로 제한하며 request/response body와 Authorization header는 저장하지 않습니다. `/internal/gateway/**`는 JWT Bearer token 검증 대상입니다.

---

### 3.4 관측성 및 메트릭 (Metrics & Actuator)

게이트웨이는 프록시 트래픽과 라우트 대상의 가용성 상태를 모니터링하기 위해 Micrometer 기반의 메트릭을 기본 제공합니다.

#### 1) 3대 핵심 메트릭 규격

| 메트릭 이름 | 미터 타입 | 태그 (Tags) | 값 / 상태 의미 및 특성 |
|---|:---:|---|---|
| `gateway.proxy.requests` | Counter | `target` (다운스트림 타겟 URL)<br>`status` (HTTP 응답 상태 코드 문자열)<br>`retried` (`"true"` 또는 `"false"`) | 프록시 중계 완료 시 요청 건수를 집계합니다. 1회 이상의 재시도를 거쳐 완료된 경우 `retried="true"` 태그가 부여됩니다 (`ProxyHandlerTest`로 검증). |
| `gateway.proxy.latency` | Timer | `target` (다운스트림 타겟 URL) | 프록시 요청의 왕복 지연 시간(Latency)을 계측합니다 (`ProxyHandlerTest`로 검증). |
| `gateway.routes.healthy` | Gauge | `target` (다운스트림 application 타겟 URL) | 각 application 타겟의 정상 여부를 나타내는 게이지.<br>- `1.0`: 정상 (healthy)<br>- `0.0`: 비정상 (unhealthy)<br>**중요 특성**: 모든 타겟이 비정상이어도 라우팅은 가용성을 위해 fail-open으로 전체 분산하지만, 이 게이지 값은 상태를 정직하게 반영하여 **fail-open 중에도 0.0을 유지**합니다 (`GatewayRouteSelectorTest`로 검증). |

#### 2) 모듈 경계 및 Actuator 노출 정책 (gateway:core vs gateway:app)

- **`gateway:core` (`gateway/core`) 의존성 경계**:
  - `gateway:core`는 순수 라이브러리 모듈로서 Spring Boot Actuator 의존성을 포함하지 않으며 `io.micrometer:micrometer-core`에만 의존합니다.
  - `MeterRegistry`는 optional(`ObjectProvider<MeterRegistry>`)로 주입받으며, 레지스트리 빈이 컨텍스트에 없더라도(`meterRegistry == null`) 예외 없이 정상 동작하고 메트릭 계측만 건너뜁니다 (`ProxyHandlerTest`의 `Works without MeterRegistry without exceptions`, `GatewayRouteSelectorTest`의 `MeterRegistry가 null일 때도 상태 전이나 선택에 예외가 발생하지 않는다`로 검증).
- **`gateway:app` (`gateway/app`) 실행 모듈 및 Actuator 엔드포인트**:
  - `gateway:app`은 독립 실행형 서비스로서 `org.springframework.boot:spring-boot-starter-actuator` 및 `io.micrometer:micrometer-registry-prometheus` 의존성을 가집니다.
  - **기본 노출 (`management.endpoints.web.exposure.include=health,metrics`)**:
    - 기본 설정으로 게이트웨이 자체의 `/actuator/health`와 `/actuator/metrics` 엔드포인트가 노출됩니다 (`ActuatorEndpointIntegrationTest`로 검증).
  - **Prometheus Opt-In 정책**:
    - `micrometer-registry-prometheus` 라이브러리 의존성은 빌드에 탑재되어 있으나, 엔드포인트 자체는 보안 및 불필요한 노출 방지를 위해 기본적으로 노출되지 않습니다 (`ActuatorEndpointIntegrationTest`의 `health and metrics endpoints are exposed by default, prometheus is not`로 검증).
    - Prometheus 스크랩 엔드포인트를 활성화하려면 `management.endpoints.web.exposure.include=health,metrics,prometheus`와 같이 명시적으로 프로퍼티를 설정(opt-in)해야 합니다 (`PrometheusEndpointIntegrationTest`의 `prometheus endpoint is exposed when explicitly included`로 검증).
    - prometheus 엔드포인트가 명시적으로 노출되지 않은 상태에서 `/actuator/prometheus`로 요청이 들어올 경우, 게이트웨이 자체 액추에이터 핸들러가 처리하지 않고 와일드카드 프록시 라우트(`/actuator/**`)로 흘러가며, 백엔드 application 서버에 해당 엔드포인트가 없으면 502 Bad Gateway(또는 백엔드 401/403/404)가 반환됩니다.

---

## 4. 게이트웨이 기동 모드 (gateway.mode) 정책

게이트웨이는 단일 JVM 인프로세스 탑재부터 독립 실행 및 원격 분리까지 유연하게 대응하기 위해 `gateway.mode` 프로퍼티를 필수로 요구합니다.

### 4.1 프로퍼티 및 Fail-fast 검증
- **설정 키**: `gateway.mode`
- **허용 값**: `embedded`, `standalone`, `remote` (기본값 없음)
- **Fail-fast 정책**: 프로퍼티가 누락되었거나 허용된 세 값이 아닌 경우, 애플리케이션 기동 단계에서 즉시 예외를 발생시키고 종료합니다 (`GatewayModeProperties.kt`의 `@PostConstruct validate()`):
  ```
  gateway.mode must be exactly one of: embedded, standalone, remote. Found: '${mode ?: "null"}'
  ```
- **Spring Boot 4 자동 설정 엔트리포인트**:
  `gateway/autoconfigure/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 리소스 파일에 `cc.midolog.gateway.autoconfigure.GatewayAutoConfiguration`이 선언되어 동작합니다 (`spring.factories` 레거시 방식 미사용).

### 4.2 모드별 빈 구성 정책

| 구성 요소 | embedded | standalone | remote | 비고 |
|---|:---:|:---:|:---:|---|
| `AuthTokenRateLimitFilter` + `RedisRateLimiter` | O | O | O | IP 기준 10회/60초, Redis Lua fail-open |
| `GatewayClockConfig` (UTC Clock) | O | O | O | 공통 시간 빈 |
| `RequestVisibility` (Filter/Store/Controller) | 조건부 | 조건부 | 조건부 | `gateway.request-visibility.enabled=true` 시 |
| `RouteConfig` (`routes: RouterFunction`) | **X** | **O** | **O** | WebFlux functional routes |
| `ProxyHandler` (`HeaderSanitizer` 사용) | **X** | **O** | **O** | HTTP 프록시 핸들러 |
| `WebClientConfig` (`proxyWebClient`) | **X** | **O** | **O** | 프록시 전용 WebClient |
| `GatewayRouteSelector` / `GatewayRouteProperties` | **X** | **O** | **O** | 라우트 타겟 결정, 헬스체크 및 라운드로빈 |
| `GatewayRetryProperties` | **X** | **O** | **O** | 프록시 멱등 재시도 설정 |
| `JwtAuthFilter` | **X** | **O** | **O** | `jwt.secret` 필수 검증, 프록시 진입 전 인증 |

- **공통 세 모드 (embedded, standalone, remote)**:
  `AuthTokenRateLimitFilter` 및 `RateLimiter`/`RedisRateLimiter`, `GatewayClockConfig`가 기본 등록되며, `gateway.request-visibility.enabled=true`인 경우 가시성 빈들(`RequestEventStore`, `RequestVisibilityFilter`, `RequestVisibilityHandler`, `visibilityRoutes`)이 등록됩니다.
- **standalone 및 remote 모드**:
  공통 빈과 함께 프록시 빈 묶음(`RouteConfig`, `ProxyHandler`, `WebClientConfig`, `GatewayRouteSelector`, `GatewayRouteProperties`, `GatewayRetryProperties`, `JwtAuthFilter`)이 활성화됩니다. 자동 설정 클래스인 `GatewayAutoConfiguration`은 `@ConditionalOnExpression("'\${gateway.mode:}' == 'standalone' || '\${gateway.mode:}' == 'remote'")` 어노테이션으로 이를 바인딩합니다. 현재 코드베이스에서 standalone과 remote는 동일한 프록시 빈 묶음을 공유하며 환경 설정값(타겟 URL 및 인프라 구성)으로 역할을 구분합니다.
- **embedded 모드 (In-process 직접 처리)**:
  호스트 애플리케이션(`core:application`)에 `gateway:starter`를 의존성으로 탑재하여 동작합니다. `RouteConfig`, `ProxyHandler`, `WebClientConfig`, `GatewayRouteSelector`, `GatewayRouteProperties`, `GatewayRetryProperties`, `JwtAuthFilter` 등 프록시 관련 빈을 전혀 등록하지 않습니다.
  - **정확한 필터 순서**: 호스트의 스프링 시큐리티가 앞단에 개입하므로 `-100 Spring Security → -2 HttpLoggingFilter → -1 AuthTokenRateLimitFilter → 0 RequestIdFilter → 100 RequestVisibilityFilter` 순서로 동작합니다.
  - **인증 및 라우팅**: 게이트웨이 자체 `JwtAuthFilter` 대신 호스트 애플리케이션의 `SecurityConfig`가 JWT 인증을 전담합니다. 프록시용 `RouteConfig`가 없으므로 애플리케이션의 컨트롤러(`RequestMappingHandlerMapping`, `order=0`)가 직접 비즈니스 로직을 처리합니다. 단, 가시성 조회용 `visibilityRoutes` 빈은 오직 `/internal/gateway/requests` 경로에만 반응하는 Functional Router(`RouterFunctionMapping`, `order=-1`)로 등록되어 해당 경로만 컨트롤러보다 먼저 가로채어 처리합니다.
  - **가시성 설정 정책**: 가시성 기능(`request-visibility`)은 운영 환경 성능을 위해 기본적으로 `false`로 비활성화되며, `local` 프로파일 환경에서만 명시적으로 `true`로 활성화됩니다.


---

## 5. 포트 및 라우팅 분기 요약

| 경로 | 대상 서버 | 포트 | 설명 |
|------|----------|------|------|
| `/api/**` | application | 8081 | 비즈니스 API 요청 (단일 대상 또는 라운드로빈) |
| `/batch/**` | batch | 8082 | 배치/스케줄 작업 요청 |
| `/actuator/**` | application | 8081 | 헬스체크 및 메트릭 엔드포인트 중계 (아래 주의 박스 참조) |
| `/internal/gateway/requests` | gateway | 8080 | request visibility 조회 (기본 비활성화, JWT Bearer 토큰 필수) |
| 기타 | 게이트웨이 자신 | 8080 | 404 Not Found |

> [!WARNING] `/actuator/**` 프록시와 백엔드 보안 정책 (`SecurityConfig.kt`)
> 게이트웨이는 `/actuator/**` 경로로 들어오는 모든 요청을 `application` 서버(8081)로 투명하게 프록시합니다.
> 그러나 다운스트림 `application` 서버의 Spring Security 설정은 `/actuator/health` 엔드포인트만 익명 허용(`permitAll()`)하고, 그 외의 모든 액추에이터 엔드포인트(예: `/actuator/beans`, `/actuator/env`, `/actuator/metrics` 등)에 대해 `anyExchange().denyAll()` 규칙을 적용합니다.
> 따라서 게이트웨이를 통해 백엔드의 `/actuator/health` 외의 엔드포인트로 접근할 경우 다운스트림 서버에서 401 Unauthorized 또는 403 Forbidden 응답이 반환되므로 주의가 필요합니다.
> 반면 게이트웨이 자체(8080)의 모니터링을 위한 액추에이터 엔드포인트는 `gateway:app`에서 직접 서빙되며, 기본적으로 `health,metrics`가 노출되고 prometheus는 opt-in 방식으로 제공됩니다 (3.4절 참조).

---

## 6. 구현된 필터

### 6.1 JwtAuthFilter
**패키지**: `cc.midolog.gateway.filter.JwtAuthFilter` (`gateway:core`)
**순서**: `@Order(1)`
**설정 키**: `jwt.secret: ${JWT_SECRET}`

인입 요청의 Authorization Bearer 토큰 서명과 유효성을 검증하는 보안 필터입니다.

#### 실제 동작 및 설계 특징
- **토큰 검증 위임**: 토큰 파싱 및 서명 검증은 공통 모듈인 `support:jwt`의 `JwtCodec`에 위임합니다. 게이트웨이와 백엔드 애플리케이션(`core/application`)이 동일한 시크릿과 파싱 규칙을 공유하며, `jjwt` 라이브러리 의존성을 `support:jwt` 모듈로 격리합니다.
- **SecurityContext 미주입 통과**: 토큰 검증 성공 시 SecurityContext에 인증 정보를 주입하지 않고 체인으로 그대로 통과(`chain.filter(exchange)`)시킵니다.
- **초기화 fail-fast**: 생성자 시점에 `JwtSecretValidator.validate(secret)`를 거쳐 시크릿 강도를 검증하며, 유효하지 않은 시크릿(길이 부족 등)이 주어지면 애플리케이션 기동 시 즉시 예외(`IllegalStateException`)를 던져 fail-fast합니다.
- **경로 정책**:
  - **통과 경로** (`/api/auth/`, `/actuator/`, `/batch/`): 로그인 및 토큰 발급 엔드포인트(`/api/auth/`), 인프라 헬스체크(`/actuator/`), 사내 배치 작업(`/batch/`)은 인증 없이 통과시킵니다.
  - **검증 경로** (`/api/`, `/internal/gateway/`): 일반 비즈니스 API(`/api/`)와 게이트웨이 내부 관측 엔드포인트(`/internal/gateway/`)는 Bearer 토큰 검증을 필수로 적용합니다.
  - **기타 경로**: 라우터에서 매핑되지 않는 경로는 검증을 건너뛰고 체인으로 넘겨 404 등 표준 처리로 위임합니다.
- **401 응답 정책**:
  - Authorization 헤더 누락, Bearer 접두사 불일치, 서명 만료/위조 등 검증 실패 시 응답 본문 없이 `401 Unauthorized` 상태 코드만 반환하고 종료합니다 (`exchange.response.statusCode = HttpStatus.UNAUTHORIZED`, `setComplete()`). 공격자에게 내부 스택트레이스나 구체적인 실패 원인을 노출하지 않기 위함입니다.

#### 보장 테스트 (`gateway/core/src/test/kotlin/cc/midolog/gateway/filter/JwtAuthFilterTest.kt`)
- `passes request with a valid Bearer token`
- `returns 401 when Authorization header is missing`
- `returns 401 when Bearer token is tampered`
- `fails fast when constructed with an invalid secret`

---

### 6.2 AuthTokenRateLimitFilter
**패키지**: `cc.midolog.gateway.filter.AuthTokenRateLimitFilter` (`gateway:core`)
**순서**: `@Order(-1)`
**설정 키**: `gateway.rate-limit.auth-token.limit` (기본값: 10), `gateway.rate-limit.auth-token.window-seconds` (기본값: 60)

인증 토큰 발급 엔드포인트(`POST /api/auth/token`) 전용 무차별 대입(brute-force) 방어 필터입니다.

#### 필터 순서 계약 및 위치 이유
필터 체인 순서: `-2 HttpLoggingFilter` → `-1 AuthTokenRateLimitFilter` → `0 RequestIdFilter` → `1 JwtAuthFilter` → `100 RequestVisibilityFilter`

토큰 발급 엔드포인트는 로그인 전(미인증 상태)에 호출되므로 `JwtAuthFilter`(@Order(1))보다 앞단에서 무차별 대입을 차단해야 합니다. 또한 `RequestIdFilter`(@Order(0))보다도 앞서 한도 초과(429)로 조기 거절함으로써 미인증 공격 트래픽에 대한 불필요한 트랜잭션 컨텍스트 생성 비용을 선제적으로 줄입니다.

#### 실제 동작 및 정책
- **대상 엔드포인트**: `POST /api/auth/token` 단 하나만 대상이며, `GET /api/auth/token`이나 다른 모든 API 경로는 제한 없이 즉시 체인을 통과합니다.
- **Rate Limit 식별 키**: `rate-limit:auth-token:<client-ip>` (**클라이언트 IP(`remoteAddress` 호스트 주소) 기준**이며, 토큰 기준이 아님).
- **인터페이스 및 Lua 원자적 스크립트**:
  - `RateLimiter` 인터페이스(`cc.midolog.gateway.ratelimit.RateLimiter`)와 `RedisRateLimiter`(`cc.midolog.gateway.ratelimit.RedisRateLimiter`) 구현체로 분리되어 있습니다.
  - `ReactiveStringRedisTemplate`을 사용하며, `INCR`와 첫 증가 시 `EXPIRE` 설정을 하나의 Lua 스크립트로 묶어 원자적으로 실행합니다. INCR와 EXPIRE 사이에서 장애가 발생해도 TTL 없는 영구 키가 남지 않습니다.
- **한도 초과 응답**: 허용 한도(기본 60초 내 10회) 초과 시 본문 없는 `429 Too Many Requests` 상태 코드로 종료하여 내부 상태나 카운터를 노출하지 않습니다.
- **장애 격리 (fail-open)**: Redis 명령 실패, 네트워크 단절, 타임아웃 등 저장소 예외 발생 시 경고 로그를 남기고 요청을 통과시킵니다(fail-open). 토큰 발급 자체는 백엔드 인증 로직이 별도로 검증하므로, rate limiter의 장애가 전체 서비스 불능으로 이어지지 않도록 가용성을 우선합니다.

#### 보장 테스트 (`gateway/core/src/test/kotlin/cc/midolog/gateway/filter/AuthTokenRateLimitFilterTest.kt`)
- `passes request when under the limit`
- `returns 429 when the limit is exceeded`
- `does not limit GET requests to the token endpoint`
- `does not limit other api paths`
- `does not limit actuator paths`
- `does not limit batch paths`
- `fails open when the rate limiter errors`

---

## 7. Request Visibility

### 7.1 목표
게이트웨이로 들어온 요청 목록을 최소 메타데이터로 확인합니다. 기본값은 비활성화이며, 명시적으로 활성화했을 때만 내부 조회 엔드포인트와 저장소가 등록됩니다.

### 7.2 현재 기능
- **컴포넌트 구성** (`cc.midolog.gateway.visibility.*`, `gateway:core`):
  - `RequestEventStore`: `ArrayDeque` 기반의 bounded in-memory 링 버퍼 저장소. 설정된 용량(`capacity`, 기본값 200)을 유지하며 동기화(`@Synchronized`)로 스레드 안전성 보장.
  - `RequestVisibilityFilter`: `@Order(100)` 필터. 체인 최하단에서 응답 완료 시점(`doFinally`)에 요청 메타데이터를 이벤트 저장소에 기록 (단, 앞선 필터에서 429/401로 조기 거절되어 체인이 단축된 요청은 이 필터에 도달하지 않으므로 저장되지 않으며, 최외곽 `HttpLoggingFilter`만 로깅을 남김). `GatewayClockConfig`의 `Clock` 빈을 주입받아 정확한 시각 계측.
  - `RequestVisibilityHandler` 및 `visibilityRoutes`: `GET /internal/gateway/requests` 내부 조회 엔드포인트를 제공하는 핸들러 및 라우터.
  - `RequestVisibilityProperties`: `gateway.request-visibility.enabled`(기본 false), `gateway.request-visibility.capacity`(기본 200).
- **조건부 빈 등록**: `gateway.request-visibility.enabled=true`일 때만 저장소, 필터, 핸들러, 라우터 빈이 활성화됩니다 (`GatewayAutoConfigurationTest`의 `visibility beans are enabled only when property is true`로 검증).
- **보안**: `/internal/gateway/**` 경로는 standalone/remote에서는 `JwtAuthFilter`의 검증 대상에 포함되며, embedded에서는 호스트 `SecurityConfig`가 담당합니다.
- **민감정보 보호**: 요청/응답 본문, 쿼리스트링, Authorization 헤더 등 PII 및 민감 자격증명은 일절 저장하지 않으며 method, path, status, requestId, timestamp, durationMs 메타데이터만 보관합니다.

#### 보장 테스트

**`gateway/core/src/test/kotlin/cc/midolog/gateway/visibility/RequestVisibilityTest.kt`**
- `store keeps bounded recent events`
- `filter records minimal event data after response completion`
- `controller returns recent events without raw body or headers`
- `internal gateway requests require a valid jwt`

**`gateway/autoconfigure/src/test/kotlin/cc/midolog/gateway/autoconfigure/GatewayAutoConfigurationTest.kt`**
- `visibility beans are disabled by default`
- `visibility beans are enabled only when property is true`

### 7.3 후속 후보
- Redis-backed 분산 request event store
- HTML dashboard 또는 별도 운영 UI
- request id 기반 로그 조회 연동
- 경로/status/time range 필터링

자세한 내용은 [`./FUTURE.md`](./FUTURE.md) 참조.

---

## 8. 수평 확장 (Architecture Note)

게이트웨이가 단일 진입점이므로, 비즈니스 서버(application, batch)는 무상태로 설계되어 다중 인스턴스 배포가 가능합니다.

### 7.1 확장 시나리오
```
클라이언트
    ↓
[게이트웨이] (단일 또는 로드밸런서 뒤)
    ├─ /api/** → application:8081 (인스턴스 1)
    │            application:8081 (인스턴스 2)
    │            application:8081 (인스턴스 3) — 로드밸런서로 분산
    │
    └─ /batch/** → batch:8082 (인스턴스 1)
                    batch:8082 (인스턴스 2) — 필요시 분산
```

### 7.2 설계 고려사항
- **게이트웨이 라우팅**: ProxyHandler의 targetUrl에 로드밸런서 주소 설정
- **세션 관리**: 트랜잭션 ID는 요청 헤더로 관리하므로 서버 간 상태 동기화 불필요
- **캐싱**: 다운스트림 서버의 캐시 일관성은 각 서버의 책임

---

## 관련 문서

- [`./ARCHITECTURE.md`](./ARCHITECTURE.md) — 전체 멀티모듈 아키텍처 개요
- [`./MODULE_GUIDE.md`](./MODULE_GUIDE.md) — 각 모듈별 역할 및 개발 가이드
- [`./FUTURE.md`](./FUTURE.md) — 향후 계획 및 마일스톤
