# 게이트웨이 (Gateway)

> 모든 요청의 단일 진입점 역할을 수행하며, 트랜잭션 ID를 부여·전파하고 path-prefix 기반 라우팅으로 비즈니스 서버에 요청을 분기합니다.

## 1. 역할 및 책임

### 1.1 주요 기능
- **단일 진입점**: 모든 클라이언트 요청이 게이트웨이(포트 8080)를 통해 진입
- **트랜잭션 ID 관리**: 요청 헤더에서 트랜잭션 ID를 추출하거나 생성하여 전체 요청 생명주기에서 추적 가능하게 관리
- **라우팅·프록시**: path-prefix에 따라 비즈니스 서버(application, batch) 또는 자체 모니터링 엔드포인트로 분기
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
라우팅 결정 (RouteConfig & GatewayRouteSelector)
    ├─ /api/** → application(8081) 프록시 (단일 URL 또는 라운드로빈)
    ├─ /batch/** → batch(8082) 프록시
    ├─ /actuator/** → application(8081) 프록시 (주의: application은 /actuator/health 외 denyAll)
    └─ /internal/gateway/requests → gateway 자체 컨트롤러(8080)
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
- **`GatewayRouteProperties`**: `gateway.routes` 접두사로 바인딩되며, 단일 애플리케이션 URL(`applicationUrl` / 환경변수 `GATEWAY_APPLICATION_URL`), 복수 애플리케이션 URL 목록(`applicationUrls` / 환경변수 `GATEWAY_APPLICATION_URLS`), 배치 URL(`batchUrl` / 환경변수 `GATEWAY_BATCH_URL`)을 관리합니다.
- **`GatewayRouteSelector` (라우트 선택 및 로드밸런싱)**:
  - `/batch/**` 경로는 배치 전용 서버(`batchUrl`)로 분기하고, `/api/**` 및 `/actuator/**` 등 그 외 모든 경로는 비즈니스 애플리케이션 서버로 분기합니다.
  - `applicationUrls`가 설정된 경우 `AtomicInteger` 기반 카운터로 라운드로빈 분산을 수행합니다. 카운터가 `Int.MAX_VALUE`에 도달하면 0으로 원자적 회전시켜 오버플로로 인한 음수 인덱스를 방지합니다 (`GatewayRouteSelectorTest`의 `multiple application urls are selected round robin`으로 검증).
  - 생성자 초기화 시점에 모든 URL의 http/https 스킴 및 호스트 존재 여부를 엄격히 검증하여 잘못된 설정 시 기동 단계에서 즉시 fail-fast(`IllegalStateException`)합니다 (`GatewayRouteSelectorTest`의 `invalid route url fails fast with a clear message`로 검증).

### 3.2 ProxyHandler (HTTP 프록시)
**패키지**: `cc.midolog.gateway.proxy.ProxyHandler`

WebClient를 사용하여 다운스트림 서버로 실제 요청을 전달합니다.

```kotlin
// 실제 구현: ProxyHandler.kt
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
    // ...
}
```

#### 요청 본문 스트리밍 / 응답 본문 버퍼링
`ProxyHandler`는 요청 본문과 응답 본문을 대칭적으로 다루지 않습니다:
- **요청 본문 스트리밍**: 클라이언트의 요청 본문은 `request.bodyToFlux(DataBuffer::class.java)` 및 `BodyInserters.fromDataBuffers(...)`를 통해 버퍼링 없이 다운스트림 서버로 스트리밍 전달됩니다 (GET 등 본문 없는 요청은 빈 스트림).
- **응답 본문 버퍼링**: 다운스트림 서버의 응답 본문은 `bodyToMono(ByteArray::class.java)`를 통해 메모리에 바이트 배열로 전체 버퍼링한 뒤 `ServerResponse.bodyValue(body)`로 클라이언트에 반환합니다 (`ProxyHandlerTest`의 `buffers downstream response body and sanitizes response headers`로 보증).

> **응답 버퍼링 트레이드오프**
> 응답 본문을 메모리에 일괄 적재(버퍼링)함으로써 응답 상태 코드 및 헤더 조작, 에러 복구 처리가 단순해지는 장점이 있습니다. 반면 대용량 응답(대용량 파일 다운로드, 대규모 JSON 목록 등)을 수신할 때 게이트웨이 인스턴스의 JVM 힙 메모리 사용량이 급증하여 OOM(Out of Memory) 위험이 발생할 수 있는 트레이드오프가 존재합니다. 대용량 응답의 스트리밍 전환(DataBuffer pass-through)은 [`./FUTURE.md`](./FUTURE.md)의 후보 과제입니다.

#### 에러 처리
- 다운스트림 호출 중 타임아웃(`TimeoutException`)이 발생하면 `504 Gateway Timeout`으로 변환합니다 (`ProxyHandlerTest`의 `returns gateway timeout when downstream call times out`로 검증).
- 그 외 연결 실패나 네트워크 오류는 `502 Bad Gateway`로 변환합니다.

#### 타임아웃 값 (`config/WebClientConfig.kt`)
다운스트림 응답 지연으로 인한 스레드 및 커넥션 자원 고갈을 방지하기 위해 다음 타임아웃을 적용합니다:
- **연결 타임아웃 (Connect Timeout)**: 3초 (`ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000`)
- **응답 타임아웃 (Response Timeout)**: 10초 (`responseTimeout(Duration.ofSeconds(10))`)
- **소켓 읽기 타임아웃 (Read Timeout)**: 10초 (`ReadTimeoutHandler(10, TimeUnit.SECONDS)`)

*(타임아웃 수치 3초/10초/10초는 초기 기본값이며 정책적 근거에 기반한 것이 아닙니다. 다운스트림 환경별 타임아웃 분리 및 재시도·서킷 브레이커 도입은 [`./FUTURE.md`](./FUTURE.md) 후보 과제입니다.)*

#### hop-by-hop 헤더 제거 (`proxy/HeaderSanitizer.kt`)
RFC 7230 §6.1 및 RFC 9110 §7.6.1 규격에 따라 단일 전송 레벨 연결에 국한된 헤더를 제거합니다:
- `Connection`, `Keep-Alive`, `Proxy-Authenticate`, `Proxy-Authorization`, `TE`, `Trailer`, `Transfer-Encoding`, `Upgrade` 및 클라이언트 `Connection` 헤더에 명시된 커스텀 홉 헤더 제거.
- 다운스트림 가상 호스트 라우팅과의 충돌 방지를 위해 `Host` 헤더 제거.
- 프록시 중계 과정에서 바이트 수 불일치 왜곡을 방지하기 위해 `Content-Length` 헤더 제거 (클라이언트가 재계산하도록 유도).
- 요청 중계 및 응답 반환 양방향으로 적용 (`ProxyHandlerTest`의 `forwards path query and sanitized headers to application route`로 검증).

### 3.3 설정 파일 (application.yml)
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

`application.yml`은 profile을 암묵 활성화하지 않습니다. 로컬 실행 시 `SPRING_PROFILES_ACTIVE=local JWT_SECRET=<32바이트 이상>`을 명시합니다 (`GatewayProfileConfigTest`로 검증).

`gateway.routes.application-urls`를 쉼표 구분 목록으로 지정하면 `/api/**`와 `/actuator/**` 대상 application 서버를 라운드로빈으로 선택합니다. 값이 없으면 기존 `gateway.routes.application-url` 단일 대상 설정을 그대로 사용합니다. `/batch/**`는 항상 `gateway.routes.batch-url` 단일 대상으로 전달합니다.

Request visibility는 기본 비활성화입니다. `GATEWAY_REQUEST_VISIBILITY_ENABLED=true`로 명시한 경우에만 `/internal/gateway/requests` endpoint와 in-memory event store가 활성화됩니다. 저장 항목은 method, path, status, request id, timestamp, duration으로 제한하며 request/response body와 Authorization header는 저장하지 않습니다. `/internal/gateway/**`는 JWT Bearer token 검증 대상입니다.

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
| `GatewayRouteSelector` / `GatewayRouteProperties` | **X** | **O** | **O** | 라우트 타겟 결정 및 라운드로빈 |
| `JwtAuthFilter` | **X** | **O** | **O** | `jwt.secret` 필수 검증, 프록시 진입 전 인증 |

- **공통 세 모드 (embedded, standalone, remote)**:
  `AuthTokenRateLimitFilter` 및 `RateLimiter`/`RedisRateLimiter`, `GatewayClockConfig`가 기본 등록되며, `gateway.request-visibility.enabled=true`인 경우 가시성 빈들(`RequestEventStore`, `RequestVisibilityFilter`, `RequestVisibilityController`)이 등록됩니다.
- **standalone 및 remote 모드**:
  공통 빈과 함께 프록시 빈 묶음(`RouteConfig`, `ProxyHandler`, `WebClientConfig`, `GatewayRouteSelector`, `GatewayRouteProperties`, `JwtAuthFilter`)이 활성화됩니다. 자동 설정 클래스인 `GatewayAutoConfiguration`은 `@ConditionalOnExpression("'\${gateway.mode:}' == 'standalone' || '\${gateway.mode:}' == 'remote'")` 어노테이션으로 이를 바인딩합니다. 현재 코드베이스에서 standalone과 remote는 동일한 프록시 빈 묶음을 공유하며 환경 설정값(타겟 URL 및 인프라 구성)으로 역할을 구분합니다.
- **embedded 모드 (In-process 직접 처리)**:
  `RouteConfig`, `ProxyHandler`, `WebClientConfig`, `GatewayRouteSelector`, `GatewayRouteProperties`, `JwtAuthFilter`를 전혀 등록하지 않습니다.
  - **설계 이유 및 실측 순서 근거**: WebFlux의 핸들러 매핑 우선순위는 `RouterFunctionMapping`이 `order = -1`이고, 컨트롤러 매핑인 `RequestMappingHandlerMapping`이 `order = 0`입니다. 만약 embedded 모드에서 프록시 라우트 빈(`RouteConfig`)이 등록되면, 동일 프로세스 내의 `@RestController`보다 Functional Router가 `/api/**` 요청을 먼저 가로채 다운스트림 호출을 시도하다가 장애(502/504)를 발생시킵니다. 따라서 routes와 프록시 빈을 등록하지 않아 동일 JVM의 컨트롤러가 직접 요청을 처리하도록 합니다 (`GatewayAutoConfigurationTest`의 `WebFlux 매핑 순서 실측 가드`로 보증).
  - 인증은 게이트웨이 `JwtAuthFilter` 대신 호스트 애플리케이션의 `SecurityConfig`가 직접 담당합니다.

### 4.3 알려진 제약 및 컴포넌트 스캔 방어
- `gateway:core` 모듈의 `RequestVisibilityController`에 `@RestController`가 부여되어 있어, 호스트 애플리케이션(`core:application`)이나 게이트웨이 앱(`gateway:app`)이 `cc.midolog` 패키지 스캔을 수행할 때 `gateway.request-visibility.enabled=false` 설정임에도 불구하고 컨트롤러가 먼저 빈으로 등록되는 현상이 발생할 수 있습니다.
- 현재 `gateway/app`은 `GatewayApplication`에서 `@ComponentScan(excludeFilters = [ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [RequestVisibilityController::class])])`로 방어하고 있습니다. L2에서 `core:application`에 스타터를 탑재하기 전 core/autoconfigure 후속 정리가 필요합니다.

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
> 따라서 게이트웨이를 통해 `/actuator/health` 외의 엔드포인트로 접근할 경우 다운스트림 서버에서 401 Unauthorized 또는 403 Forbidden 응답이 반환되므로 주의가 필요합니다.

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
  - `RequestVisibilityController`: `GET /internal/gateway/requests` 내부 조회 REST 컨트롤러.
  - `RequestVisibilityProperties`: `gateway.request-visibility.enabled`(기본 false), `gateway.request-visibility.capacity`(기본 200).
- **조건부 빈 등록**: `gateway.request-visibility.enabled=true`일 때만 저장소, 필터, 컨트롤러 빈이 활성화됩니다 (`RequestVisibilityTest`로 검증).
- **보안**: `/internal/gateway/**` 경로는 standalone/remote에서는 `JwtAuthFilter`의 검증 대상에 포함되며, embedded에서는 호스트 `SecurityConfig`가 담당합니다.
- **민감정보 보호**: 요청/응답 본문, 쿼리스트링, Authorization 헤더 등 PII 및 민감 자격증명은 일절 저장하지 않으며 method, path, status, requestId, timestamp, durationMs 메타데이터만 보관합니다.

#### 보장 테스트 (`gateway/core/src/test/kotlin/cc/midolog/gateway/visibility/RequestVisibilityTest.kt`)
- `visibility beans are disabled by default`
- `visibility beans are enabled only when property is true`
- `store keeps bounded recent events`
- `filter records minimal event data after response completion`
- `controller returns recent events without raw body or headers`
- `internal gateway requests require a valid jwt`

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
