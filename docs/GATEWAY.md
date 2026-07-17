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
RequestIdFilter (support:web, 트랜잭션 ID 부여/전파)
    ↓
라우팅 결정 (path-prefix)
    ├─ /api/** → application(8081) 프록시
    ├─ /batch/** → batch(8082) 프록시
    ├─ /actuator/** → application(8081) 프록시
    ↓
응답 + 트랜잭션 ID 헤더 반환
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

### 3.1 RouteConfig (라우팅 규칙)
**패키지**: `cc.midolog.gateway.config.RouteConfig`

현재 구현은 WebFlux Router DSL을 사용하여 라우팅 규칙을 정의합니다.

```kotlin
// 의사코드: RouteConfig
@Configuration
class RouteConfig {
    
    @Bean
    fun routerFunction(proxyHandler: ProxyHandler): RouterFunction<ServerResponse> =
        coRouter {
            path("/api/**").invoke(proxyHandler::proxy)
            path("/batch/**").invoke(proxyHandler::proxy)
            path("/actuator/**").invoke(proxyHandler::proxy)
        }
}
```

### 3.2 ProxyHandler (HTTP 프록시)
**패키지**: `cc.midolog.gateway.handler.ProxyHandler`

WebClient를 사용하여 다운스트림 서버로 실제 요청을 전달합니다.

```kotlin
// 의사코드: ProxyHandler
@Component
class ProxyHandler(
    private val webClient: WebClient,
    @Value("\${gateway.routes.application-url}") val applicationUrl: String,
    @Value("\${gateway.routes.batch-url}") val batchUrl: String
) {
    
    fun proxy(request: ServerRequest): Mono<ServerResponse> {
        val targetUrl = when {
            request.path().startsWith("/batch/") -> batchUrl
            else -> applicationUrl
        }

        return webClient.method(HttpMethod.valueOf(request.method().name()))
            .uri(targetUri(targetUrl, request.uri()))
            .headers { it.addAll(HeaderSanitizer.sanitize(request.headers().asHttpHeaders())) }
            .body(BodyInserters.fromDataBuffers(request.bodyToFlux(DataBuffer::class.java)))
            .exchangeToMono { response ->
                ServerResponse.status(response.statusCode())
                    .headers { it.addAll(HeaderSanitizer.sanitize(response.headers().asHttpHeaders())) }
                    .body(BodyInserters.fromDataBuffers(response.bodyToFlux(DataBuffer::class.java)))
            }
            .onErrorResume { gatewayError(it) }
    }
}
```

ProxyHandler는 query string을 포함한 raw URI를 보존하고, 응답 본문을 `ByteArray`로 모두 모으지 않고 `DataBuffer` stream으로 전달합니다. WebClient의 연결/응답 timeout은 `WebClientConfig`에서 설정하며, timeout은 `504 Gateway Timeout`, 기타 다운스트림 호출 실패는 `502 Bad Gateway`로 응답합니다.

### 3.3 설정 파일 (application.yml)
```yaml
server:
  port: 8080

spring:
  application:
    name: backend-gateway

gateway:
  routes:
    application-url: ${GATEWAY_APPLICATION_URL}
    application-urls: ${GATEWAY_APPLICATION_URLS:}
    batch-url: ${GATEWAY_BATCH_URL}
  request-visibility:
    enabled: false
    capacity: 200

---
# application-local.yml
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

`application.yml`은 profile을 암묵 활성화하지 않습니다. 로컬 실행 시 `SPRING_PROFILES_ACTIVE=local`을 명시합니다.

`gateway.routes.application-urls`를 쉼표 구분 목록으로 지정하면 `/api/**`와 `/actuator/**` 대상 application 서버를 라운드로빈으로 선택합니다. 값이 없으면 기존 `gateway.routes.application-url` 단일 대상 설정을 그대로 사용합니다. `/batch/**`는 항상 `gateway.routes.batch-url` 단일 대상으로 전달합니다.

Request visibility는 기본 비활성화입니다. `GATEWAY_REQUEST_VISIBILITY_ENABLED=true`로 명시한 경우에만 `/internal/gateway/requests` endpoint와 in-memory event store가 활성화됩니다. 저장 항목은 method, path, status, request id, timestamp, duration으로 제한하며 request/response body와 Authorization header는 저장하지 않습니다. `/internal/gateway/**`는 JWT Bearer token 검증 대상입니다.

---

## 4. 포트 및 라우팅 분기 요약

| 경로 | 대상 서버 | 포트 | 설명 |
|------|----------|------|------|
| `/api/**` | application | 8081 | 비즈니스 API 요청 (GET, POST, PUT, DELETE) |
| `/batch/**` | batch | 8082 | 배치/스케줄 작업 요청 |
| `/actuator/**` | application | 8081 | 헬스체크, 메트릭 엔드포인트 |
| `/internal/gateway/requests` | gateway | 8080 | request visibility 조회, 기본 비활성화, JWT 필요 |
| 기타 | 게이트웨이 자신 | 8080 | 404 Not Found |

---

## 5. 기타 필터 (설계)

### 5.1 JwtAuthFilter
**역할**: JWT 토큰 검증, 인증 흐름 제어

```kotlin
// 의사코드: JwtAuthFilter
@Component
class JwtAuthFilter : WebFilter {
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val token = exchange.request.headers.getFirst("Authorization")?.substringAfter("Bearer ")
        
        return if (token != null && validateToken(token)) {
            // 토큰 유효 → 다음 필터 진행
            chain.filter(exchange)
        } else {
            // 토큰 무효 → 401 Unauthorized 응답
            exchange.response.statusCode = HttpStatus.UNAUTHORIZED
            exchange.response.writeWith(Mono.empty())
        }
    }
}
```

### 5.2 RateLimitFilter
**역할**: Redis 기반 rate limiting (분산 환경 대응)

```kotlin
// 의사코드: RateLimitFilter
@Component
class RateLimitFilter(
    private val redisTemplate: StringRedisTemplate
) : WebFilter {
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val clientId = exchange.request.remoteAddress?.address?.hostAddress ?: "unknown"
        val key = "rate-limit:$clientId"
        val limit = 1000  // 분당 최대 요청 수
        
        val count = redisTemplate.opsForValue().increment(key) ?: 0
        redisTemplate.expire(key, Duration.ofMinutes(1))
        
        return if (count <= limit) {
            chain.filter(exchange)
        } else {
            exchange.response.statusCode = HttpStatus.TOO_MANY_REQUESTS
            exchange.response.writeWith(Mono.empty())
        }
    }
}
```

---

## 6. Request Visibility

### 6.1 목표
게이트웨이로 들어온 요청 목록을 최소 정보로 확인한다. 기본값은 비활성화이며, 명시적으로 켰을 때만 내부 조회 endpoint가 등록된다.

### 6.2 현재 기능
- **최근 요청 조회**: method, path, status, request id, timestamp, duration 표시
- **저장소**: bounded in-memory store
- **보안**: `/internal/gateway/**`는 JWT Bearer token 필요
- **민감정보 제한**: request/response body, Authorization header, secret 값은 저장하지 않음

### 6.3 후속 후보
- Redis-backed 분산 request event store
- HTML dashboard 또는 별도 운영 UI
- request id 기반 로그 조회 연동
- 경로/status/time range 필터링

자세한 내용은 [`./FUTURE.md`](./FUTURE.md) 참조.

---

## 7. 수평 확장 (Architecture Note)

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
