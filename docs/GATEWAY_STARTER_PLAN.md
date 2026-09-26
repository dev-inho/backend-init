# 게이트웨이 탑재식(Starter) 지원 및 로드맵

## 1. 갭 분석 (기능 매트릭스 대비)

현재 `gateway/core` 구현체와 상용 게이트웨이를 비교한 갭 분석입니다. (기준: 작은 팀의 백엔드 초기 프로젝트)

| 기능 | 현재 구현 상태 (`gateway/core`) | 갭 | 우선순위 | 근거 (`gateway/core` 기준) |
| --- | --- | --- | --- | --- |
| 라우팅 | 정적 URL 라우팅 (`application-url(s)`, `batch-url`) | 헤더/가중치/우선순위 기반 부재 | 하 | 초기 프로젝트는 단순 경로 매핑으로 충분함 (`GatewayRouteSelector.kt:15`) |
| 인증/인가 | JWT 검증 | OAuth2, API Key, mTLS 부재 | 하 | 현재 JWT로 사내/B2C 요구사항 대부분 커버 가능함 (`JwtAuthFilter.kt:14`) |
| Rate Limit | Redis 기반 Rate Limit (fail-open) | 다양한 알고리즘 부재 | 하 | fail-open 방식의 Lua 스크립트 기반 제한은 초기 트래픽 방어에 충분함 (`RedisRateLimiter.kt:12`) |
| 회복성 | 타임아웃(10s), 멱등 재시도(GET/HEAD/OPTIONS, 1..3회, 100ms 지수 backoff), 타깃별 서킷 브레이커 완료 | 해소 | 중 | 멱등 재시도(`GatewayRetryProperties.kt`, `ProxyHandler.kt`, PR #33)와 서킷 브레이커(`circuitbreaker/CircuitBreaker.kt`, `CircuitBreakerRegistry.kt`, PR #63) 도입 완료. 상세는 `docs/GATEWAY.md` 3.3절. |
| 관측성 | Request Id, 로깅, 가시성 필터, 능동 헬스체크(10s 주기, 3연속 실패 시 제외, fail-open), 메트릭(requests, latency, healthy 게이지), Actuator(health, metrics 기본 노출, prometheus opt-in) 완료 | 분산 추적(OTel-보류) | 하 | 능동 헬스체크(`GatewayRouteProperties.kt`, `GatewayRouteSelector.kt`, PR #35) 및 Micrometer 메트릭/Actuator 노출(PR #33) 도입 완료. OpenTelemetry는 보류 (`FUTURE.md`). |
| 요청/응답 변환| 바이트 배열 버퍼링 | 선언적 헤더/바디/경로 재작성 부재 | 하 | 현재 API 프록시 용도로는 충분함 (`ProxyHandler.kt:39`) |
| 캐싱 | 없음 | 응답 캐시 지원 안 함 | 하 | 백엔드 애플리케이션 단에서 처리 가능하므로 당장 필요하지 않음. |
| 서비스 디스커버리| 정적 목록 기반 라운드로빈 | 동적 디스커버리 부재 | 하 | 모드 3 도입 시에도 초기엔 정적 목록(DNS/IP)과 헬스체크로 대체 가능함. |

## 2. 탑재식(Starter) 설계 제안

### 2.1. 모드 1: 하나의 물리 서버, 한 프로세스 (Embedded)
application이 gateway-starter를 의존하여 동일 JVM 내에서 필터 체인 및 비즈니스 로직이 동작합니다.

- **In-process 직접 처리 설계**: 새 `DispatcherHandler` 분기를 구현하는 복잡한 계획 대신, `embedded` 모드에서는 functional routes(`RouteConfig`)와 프록시 빈을 등록하지 않는 설계를 채택했습니다. WebFlux에서 `RouterFunctionMapping`(-1)이 `RequestMappingHandlerMapping`(0)보다 우선순위가 높으므로, 프록시 라우트를 비워둠으로써 요청이 동일 JVM 내의 애플리케이션 `@RestController`로 직접 흘러가 처리됩니다.
- **인증 분담**: `embedded` 모드에서는 게이트웨이 `JwtAuthFilter`를 등록하지 않으며, 호스트 애플리케이션의 `SecurityConfig`가 인가 및 인증을 온전히 직접 담당합니다.

### 2.2. 모드 2: 독립 서버 (같은 머신, 별도 프로세스 / Standalone)
현재 `gateway:app`의 기본 구동 형태입니다. `gateway:starter` 의존성을 포함하는 얇은 부트 애플리케이션(`GatewayApplication`, 포트 8080)으로 실행되어 localhost의 다른 포트(8081, 8082)를 업스트림으로 프록시 중계합니다.

### 2.3. 모드 3: 게이트웨이 단독 실행 (다른 물리 서버 / Remote)
원격 업스트림과 통신합니다. 네트워크 타임아웃, 멱등 재시도 로직, 다운스트림 능동 헬스체크 및 fail-open 메커니즘(Phase 4, PR #33·#35)이 구현 완료되었으며, 현재 코드베이스에서는 standalone과 동일한 프록시 빈 묶음을 공유하고 설정값으로 원격 URL을 바라보도록 구성됩니다.

### 2.4. 공통 모듈 설계
- `gateway:core` (`gateway/core`): 핵심 필터 및 프록시 로직
- `gateway:autoconfigure` (`gateway/autoconfigure`): 조건부 빈 및 자동 설정 (`gateway.mode` 지원)
- `gateway:starter` (`gateway/starter`): 프로젝트 의존성 묶음 스타터
- `gateway:app` (`gateway/app`): 모드 2/3을 위한 단독 실행 애플리케이션 부트스트랩 (포트 8080)

### 2.5. Spring Cloud Gateway (SCG) 채택 vs 자체 구현 유지

SCG 라이브러리 임베드 모드(모드 1)는 공식적으로 지원되나, 아래의 득실을 고려해야 합니다.

- **현재 5개 필터의 SCG 대응표**:
  | 현재 자체 필터 | SCG 대응 방안 (대체/마이그레이션) |
  | --- | --- |
  | `RequestIdFilter` | `AddRequestHeader` 필터 활용 혹은 커스텀 GlobalFilter |
  | `AuthTokenRateLimitFilter` | `RequestRateLimiter` + `RedisRateLimiter` 사용 및 KeyResolver 커스텀 |
  | `JwtAuthFilter` | 기본 내장 없음. 커스텀 `GatewayFilter` 구현 필수 |
  | `RequestVisibilityFilter` | 커스텀 `GlobalFilter` 로깅 로직 작성 필수 |
  | `HttpLoggingFilter` | SCG Netty 로깅 튜닝 혹은 커스텀 필터 작성 |

- **SCG 채택 시 의존성 증가 비용**: Spring Boot 4.0.6 환경에 대응하는 SCG 5.0.x(`spring-cloud-starter-gateway-server-webflux`)의 Maven Central POM([URL](https://repo1.maven.org/maven2/org/springframework/cloud/spring-cloud-starter-gateway-server-webflux/5.0.3/spring-cloud-starter-gateway-server-webflux-5.0.3.pom))을 분석한 직접 의존성 목록입니다.
  | 기존 의존성과 겹침 (추가 비용 없음) | 새로 들어오는 직접 의존성 (추가 비용) |
  | --- | --- |
  | `spring-boot-starter-webflux` | `spring-cloud-starter` (Spring Cloud 공통 컨텍스트) |
  | | `spring-cloud-gateway-server-webflux` (SCG WebFlux 코어 서버) |

  위와 같이 Spring Cloud 컨텍스트 및 SCG 서버 모듈 등 새로운 의존성이 추가됩니다. 자체 구현 필터를 SCG의 `GatewayFilter` 인터페이스로 다시 작성해야 하는 로직 전환 비용도 큽니다.

- **결론**: 커스텀 로직(Visibility, JWT)이 강결합된 현재 "자체 구현을 유지"하면서 `gateway:starter` 체제로 모듈화하는 것이 초기 프로젝트에 훨씬 적합합니다.

## 3. 사용자 결정 사항 (확정)

1. **SCG 전면 교체 여부**: **자체 WebFlux 구현 유지 확정** (Spring Cloud Gateway 미채택).
2. **모드 1 라우팅 방식**: **In-process 직접 처리 확정** (별도의 복잡한 DispatcherHandler 구현 대신, embedded 모드에서 routes/proxy 빈을 등록하지 않아 동일 JVM의 host `@RestController`로 직접 흐르게 하는 설계 채택).
3. **설정 키 네이밍**: **`gateway.mode=embedded|standalone|remote` 확정** (기본값 없음, 필수값, 누락/오류 시 fail-fast).

## 4. 로드맵 (Phase 별 워커 브리프 크기 분할)

- **Phase 0: 사전 준비 [완료]**
  - 패키지 재배치 및 JWT 공유 모듈 분리 머지 완료.
- **Phase 1: `gateway:core` 및 `gateway:autoconfigure` 분할 [완료]**
  - 내용: 구 `core/gateway`를 `gateway/core`와 `gateway/autoconfigure`로 분리하고 자동 설정을 활성화.
  - 파일 경계: `settings.gradle`, `gateway/core/build.gradle`, `gateway/autoconfigure/build.gradle`, `GatewayAutoConfiguration.kt`, `GatewayModeProperties.kt`, Spring Boot 4 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
  - 가드 테스트: `gateway/autoconfigure/src/test/kotlin/cc/midolog/gateway/autoconfigure/GatewayAutoConfigurationTest.kt`.
- **Phase 2: `gateway:starter` 및 `gateway:app` 분리 [완료]**
  - 내용: `gateway:starter` 라이브러리를 생성하고 모드 2/3용 독립 부트 모듈인 `gateway:app`을 신설. 기존 부트 클래스 이동.
  - 파일 경계: `gateway/starter/build.gradle`, `gateway/app/build.gradle`, `gateway/app/src/main/kotlin/cc/midolog/GatewayApplication.kt`, `application.yml`, `application-local.yml`.
  - 가드 테스트: `gateway/app/src/test/kotlin/cc/midolog/gateway/GatewayAppIntegrationTest.kt`, `GatewayProfileConfigTest.kt`.
- **Phase 3 / L2: 모드 1 (Embedded) core:application 스타터 탑재 및 정리 (차기 과제)**
  - 내용: `core:application`에 `gateway:starter`를 탑재하고 `gateway.mode=embedded` 동작 검증.
  - W1 알려진 제약 정리: `gateway:core`의 `RequestVisibilityController` `@RestController`가 호스트 패키지 스캔에 잡히는 문제에 대해 core/autoconfigure 후속 패키지/설정 정리 진행.
  - 가드 테스트: `core:application` 기동 시 게이트웨이 공통 필터(레이트 리밋, 관측성)가 동작하고 애플리케이션 컨트롤러와 SecurityConfig로 요청이 직접 처리되는지 통합 테스트.
- **Phase 4 / L3: 모드 3 (Remote) 관측성·재시도 및 헬스체크 기능 추가 [완료]**
  - 내용: PR #33(재시도·메트릭)과 PR #35(능동 헬스체크)를 통해 멱등 요청(GET/HEAD/OPTIONS) 재시도 및 지수 backoff, 비멱등 요청 재시도 배제, 다운스트림 비즈니스 서버 능동 헬스체크 및 all-unhealthy 전체 fail-open, 수동 연결 실패 즉시 unhealthy 마킹, Micrometer 메트릭(`gateway.proxy.requests`, `gateway.proxy.latency`, `gateway.routes.healthy`), Spring Boot Actuator 기본 노출(`health,metrics`) 및 Prometheus opt-in 연동 완료.
  - 파일 경계: `gateway/core/src/main/kotlin/cc/midolog/gateway/config/GatewayRetryProperties.kt`, `gateway/core/src/main/kotlin/cc/midolog/gateway/config/GatewayRouteProperties.kt`, `gateway/core/src/main/kotlin/cc/midolog/gateway/proxy/ProxyHandler.kt`, `gateway/core/src/main/kotlin/cc/midolog/gateway/route/GatewayRouteSelector.kt`, `gateway/autoconfigure/src/main/kotlin/cc/midolog/gateway/autoconfigure/GatewayAutoConfiguration.kt`, `gateway/app/build.gradle`, `gateway/app/src/main/resources/application.yml`.
  - 가드 테스트: `ProxyHandlerTest`, `GatewayRetryPropertiesTest`, `GatewayRoutePropertiesTest`, `GatewayRouteSelectorTest`, `ActuatorEndpointIntegrationTest`.
  - PR 근거: PR #33 (`gateway retry-metrics`), PR #35 (`gateway health-check`).

## 5. 조사에서 확인 못 한 것

- KrakenD 엔터프라이즈의 응답 캐시 및 gRPC 백엔드 최신 지원 상세 스펙 미확인.
- Netflix Zuul 최신 버전(Zuul 2)의 HTTP/3 지원 여부 등 일부 문서 불충분 항목.
- NGINX Plus 외 OSS 버전만 사용할 경우 JWT 검증용 Lua 오픈소스 모듈의 프로덕션 안정성 미확인.
