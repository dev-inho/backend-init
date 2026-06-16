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
RequestIdFilter (트랜잭션 ID 부여/전파)
    ↓
라우팅 결정 (path-prefix)
    ├─ /api/** → application(8081) 프록시
    ├─ /batch/** → batch(8082) 프록시
    ├─ /actuator/** → application(8081) 프록시
    └─ /gateway/** → 게이트웨이 자체 핸들러
    ↓
응답 + 트랜잭션 ID 헤더 반환
```

---

## 2. 트랜잭션 ID (Request ID)

### 2.1 개요
트랜잭션 ID는 클라이언트가 보낸 요청에서 시작하여 모든 다운스트림 서버를 거쳐 로그에 일관되게 기록되는 추적 식별자입니다.

### 2.2 RequestIdFilter 구현
**패키지**: `cc.midolog.gateway.filter.RequestIdFilter`  
**타입**: WebFilter (@Order(0) — 모든 필터 최상단에서 실행)

#### 동작 방식
```kotlin
// 의사코드: RequestIdFilter
class RequestIdFilter : WebFilter {
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        // 1. 요청 헤더에서 X-Request-Id 확인
        val requestId = exchange.request.headers.getFirst("X-Request-Id")
            ?: UUID.randomUUID().toString()
        
        // 2. 요청 헤더에 주입 (없었으면 생성한 값)
        exchange.request.headers["X-Request-Id"] = requestId
        
        // 3. MDC에 등록 (로깅 시 자동 포함)
        MDC.put("requestId", requestId)
        
        // 4. 응답 헤더에도 추가
        exchange.response.headers.add("X-Request-Id", requestId)
        
        // 5. 다음 필터로 진행
        return chain.filter(exchange)
    }
}
```

#### 주요 특징
- **요청 헤더 읽기**: `X-Request-Id` 헤더가 있으면 사용, 없으면 새 UUID 생성
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

Spring Cloud Gateway 또는 WebFlux Router DSL을 사용하여 라우팅 규칙을 정의합니다.

```kotlin
// 의사코드: RouteConfig
@Configuration
class RouteConfig {
    
    @Bean
    fun routerFunction(proxyHandler: ProxyHandler): RouterFunction<ServerResponse> =
        coRouter {
            // API 요청 → application 서버로 프록시
            POST("/api/**").invoke { proxyHandler.proxy(it, "application") }
            GET("/api/**").invoke { proxyHandler.proxy(it, "application") }
            PUT("/api/**").invoke { proxyHandler.proxy(it, "application") }
            DELETE("/api/**").invoke { proxyHandler.proxy(it, "application") }
            
            // 배치 요청 → batch 서버로 프록시
            POST("/batch/**").invoke { proxyHandler.proxy(it, "batch") }
            GET("/batch/**").invoke { proxyHandler.proxy(it, "batch") }
            
            // 헬스체크/메트릭 → application 서버로 프록시
            GET("/actuator/**").invoke { proxyHandler.proxy(it, "application") }
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
    
    suspend fun proxy(exchange: ServerWebExchange, target: String): ServerResponse {
        // 1. 대상 URL 결정
        val targetUrl = when (target) {
            "application" -> applicationUrl       // http://localhost:8081
            "batch" -> batchUrl                   // http://localhost:8082
            else -> throw IllegalArgumentException("Unknown target: $target")
        }
        
        // 2. 실제 경로 추출 (e.g., /api/users/1)
        val path = exchange.request.path.value()
        val fullUrl = targetUrl + path
        
        // 3. 요청 헤더 복사 (X-Request-Id 포함)
        val headers = exchange.request.headers.toMutableMap()
        val requestId = headers["X-Request-Id"]?.firstOrNull() ?: UUID.randomUUID().toString()
        headers["X-Request-Id"] = listOf(requestId)
        
        // 4. WebClient로 다운스트림 요청
        val response = webClient
            .method(exchange.request.method!!)
            .uri(fullUrl)
            .headers { h -> headers.forEach { (k, v) -> h.addAll(k, v) } }
            .retrieve()
            .toEntity(ByteArray::class.java)
            .awaitSingle()
        
        // 5. 응답 반환
        return ServerResponse
            .status(response.statusCode)
            .headers { h -> response.headers.forEach { (k, v) -> h.addAll(k, v) } }
            .bodyValue(response.body ?: ByteArray(0))
    }
}
```

### 3.3 설정 파일 (application.yml)
```yaml
server:
  port: 8080
  servlet:
    context-path: /

gateway:
  routes:
    application-url: http://localhost:8081
    batch-url: http://localhost:8082

spring:
  application:
    name: gateway
```

---

## 4. 포트 및 라우팅 분기 요약

| 경로 | 대상 서버 | 포트 | 설명 |
|------|----------|------|------|
| `/api/**` | application | 8081 | 비즈니스 API 요청 (GET, POST, PUT, DELETE) |
| `/batch/**` | batch | 8082 | 배치/스케줄 작업 요청 |
| `/actuator/**` | application | 8081 | 헬스체크, 메트릭 엔드포인트 |
| `/gateway/**` | gateway 자신 | 8080 | 게이트웨이 모니터링 화면, 요청 목록 |
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

## 6. 향후 계획: 요청 확인 화면 (모니터링 대시보드)

### 6.1 목표
게이트웨이로 들어온 요청 목록을 실시간으로 확인하는 모니터링 화면을 제공합니다.

### 6.2 기능 (설계 단계)
- **요청 목록 조회**: 최근 요청들과 트랜잭션 ID, 경로, 상태 코드, 응답 시간 표시
- **필터링**: 경로, 상태 코드, 시간대 기준으로 요청 필터링
- **상세 조회**: 특정 요청의 헤더, 바디, 응답 정보 조회
- **로그 통합**: 같은 트랜잭션 ID로 다운스트림 서버 로그 함께 조회 (추후)

### 6.3 구현 계획
**이번 마일스톤(Phase 1)**: 게이트웨이 아키텍처 및 라우팅 완성  
**다음 마일스톤(Phase 2)**: 요청 저장소 추가, 대시보드 웹 UI 개발

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
