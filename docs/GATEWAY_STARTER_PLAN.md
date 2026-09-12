# 게이트웨이 탑재식(Starter) 지원 및 로드맵

## 1. 갭 분석 (기능 매트릭스 대비)

현재 `core/gateway` 구현체와 상용 게이트웨이를 비교한 갭 분석입니다. (기준: 작은 팀의 백엔드 초기 프로젝트)

| 기능 | 현재 구현 상태 (`core/gateway`) | 갭 | 우선순위 | 근거 (`core/gateway` 기준) |
| --- | --- | --- | --- | --- |
| 라우팅 | 정적 URL 라우팅 (`application-url(s)`, `batch-url`) | 헤더/가중치/우선순위 기반 부재 | 하 | 초기 프로젝트는 단순 경로 매핑으로 충분함 (`GatewayRouteSelector.kt:15`) |
| 인증/인가 | JWT 검증 | OAuth2, API Key, mTLS 부재 | 하 | 현재 JWT로 사내/B2C 요구사항 대부분 커버 가능함 (`JwtAuthFilter.kt:14`) |
| Rate Limit | Redis 기반 Rate Limit (fail-open) | 다양한 알고리즘 부재 | 하 | fail-open 방식의 Lua 스크립트 기반 제한은 초기 트래픽 방어에 충분함 (`RedisRateLimiter.kt:12`) |
| 회복성 | 타임아웃 있음(10s), 재시도·CB 없음 | 서킷 브레이커, 재시도 제어 부재 | 상 | 모드 3(원격 서버)에서 일시적 네트워크 장애 방어를 위한 재시도가 필요함. 현재 타임아웃 설정(`WebClientConfig.kt:20-21`, `ProxyHandler.kt:47,63` 504 반환)은 존재함. |
| 관측성 | Request Id, 로깅, 가시성 필터 | 분산 추적(OTel-보류), 메트릭, 헬스체크 부재 | 중 | 헬스체크 부재 시 모드 3에서 다운된 업스트림에 계속 요청하는 문제 발생 (`FUTURE.md:97-101`) |
| 요청/응답 변환| 바이트 배열 버퍼링 | 선언적 헤더/바디/경로 재작성 부재 | 하 | 현재 API 프록시 용도로는 충분함 (`ProxyHandler.kt:39`) |
| 캐싱 | 없음 | 응답 캐시 지원 안 함 | 하 | 백엔드 애플리케이션 단에서 처리 가능하므로 당장 필요하지 않음. |
| 서비스 디스커버리| 정적 목록 기반 라운드로빈 | 동적 디스커버리 부재 | 하 | 모드 3 도입 시에도 초기엔 정적 목록(DNS/IP)과 헬스체크로 대체 가능함. |

## 2. 탑재식(Starter) 설계 제안

### 2.1. 모드 1: 하나의 물리 서버, 한 프로세스
application이 gateway-starter를 의존하여 동일 JVM 내에서 필터 체인 및 라우팅이 동작합니다. 이 모드에서 gateway가 라우팅하는 `/api/**` 타겟은 **같은 프로세스의 컨트롤러**가 됩니다.

**[라우팅 방식 후보 장단점]**
| 후보 | 장점 | 단점 (비용 및 복잡도) |
| --- | --- | --- |
| **A. 루프백 HTTP** (`localhost:포트`로 자기 자신 호출) | 구현이 가장 단순하고 기존 `ProxyHandler` 유지 가능. | 모든 요청이 네트워크 스택을 두 번 타며, 커넥션 풀과 타임아웃이 자신에게 걸림. 포트가 하나이므로 Gateway WebFilter 체인이 루프백 요청에 다시 걸려 무한루프를 막는 방어 로직 필수. |
| **B. In-process 디스패치** (내부 핸들러 직접 호출) | 네트워크 오버헤드 0. HTTP 커넥션 비용 및 타임아웃 중복 제거. 성능 최적화. | 구조를 크게 바꿔야 함. `ProxyHandler`의 WebClient 호출을 생략하고, WebFlux `DispatcherHandler`로 `ServerWebExchange`를 넘겨 애플리케이션의 `@RestController`를 직접 태우도록 분기 로직 필요. |

- **이중 인증 해결 방안**: `SecurityConfig.kt:39-69` 의 denyAll 폴백과 `JwtAuthFilter`가 동일 JVM에서 중복 실행되는 것을 방지해야 합니다. Gateway 필터에서 인증 성공 시 `ServerWebExchange`의 Attribute로 힌트를 넘기고, 애플리케이션 단의 `SecurityConfig`는 해당 힌트가 존재하면 인가(Authorization)를 통과시키도록 Security Matcher를 분리 구성하는 후보가 있습니다.

### 2.2. 모드 2: 독립 서버 (같은 머신, 별도 프로세스)
현재 유지 중인 형태입니다. `gateway-starter` 의존성을 포함하는 얇은 부트 껍데기 애플리케이션(`GatewayApplication`)으로 분리하여 localhost의 다른 포트를 업스트림으로 설정합니다.

### 2.3. 모드 3: 게이트웨이 단독 실행 (다른 물리 서버)
원격 업스트림과 통신합니다. 네트워크 타임아웃, 재시도 로직, 다운스트림 헬스체크 메커니즘(`중` 우선순위 기능)의 도입이 필요합니다.

### 2.4. 공통 모듈 설계
- `gateway-core`: 핵심 필터 및 프록시 로직
- `gateway-autoconfigure`: 조건부 빈(Auto-configuration)
- `gateway-starter`: 프로젝트 의존성 묶음
- `apps/gateway-app`: 모드 2/3을 위한 단독 실행 애플리케이션 부트스트랩

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

- **결론**: 커스텀 로직(Visibility, JWT)이 강결합된 현재 "자체 구현을 유지"하면서 `gateway-starter` 체제로 모듈화하는 것이 초기 프로젝트에 훨씬 적합합니다.

## 3. 🔶 사용자 결정 필요 항목

1. **SCG 전면 교체 여부**: 위 2.5절의 비교를 바탕으로 자체 코드 유지 vs SCG 도입 최종 결정.
2. **모드 1 라우팅 방식**: 네트워크 오버헤드를 감수하고 빠른 구현(루프백 HTTP)을 할지, 구조를 뜯어고쳐 성능 이점(In-process 디스패치)을 취할지.
3. **설정 키 네이밍**: `gateway.mode=embedded|standalone|remote` 등 속성명 결정.

## 4. 로드맵 (Phase 별 워커 브리프 크기 분할)

- **Phase 0: 사전 준비**
  - 선행 조건: 현재 워커들이 진행 중인 S1~S3(패키지 재배치 및 JWT 공유) 머지 완료.
- **Phase 1: `gateway-core` 및 `autoconfigure` 분할 (난이도: 중)**
  - 내용: `core/gateway` 를 `gateway-core` 와 `gateway-autoconfigure` 로 분리하고 자동 설정을 활성화.
  - 파일 경계: `settings.gradle`, `core/gateway/build.gradle`, 자동 설정 클래스 및 `spring.factories`. 테스트 파일 이동.
  - 선행 조건: Phase 0 완료.
  - 가드 테스트: Auto-config로 필터 5개가 컨텍스트에 정상 로드되는지 확인하는 SpringBootTest.
- **Phase 2: `gateway-app` 껍데기 분리 (난이도: 하)**
  - 내용: `gateway-starter` 를 생성하고 모드 2/3용 독립 부트 모듈인 `apps/gateway-app` 을 신설. 기존 부트 클래스 이동.
  - 파일 경계: `apps/gateway-app/` 신설, `GatewayApplication.kt` 이동, `support:web` 필터 순서 프로퍼티 조정.
  - 선행 조건: Phase 1 완료.
  - 가드 테스트: `gateway-app` 단독 기동 시 8080 포트로 예외 없이 기동되는지 확인.
- **Phase 3: 모드 1 (Embedded) 라우팅 분기 구현 (난이도: 상)**
  - 내용: 🔶결정된 라우팅 방식(루프백/In-process) 구현. `SecurityConfig` 이중 인증 우회 및 무한 루프 방지 처리.
  - 파일 경계: `ProxyHandler.kt`, `SecurityConfig.kt`, `gateway-autoconfigure`.
  - 선행 조건: Phase 2 완료 및 사용자 결정 2 완료.
  - 가드 테스트: 애플리케이션 임베드 구동 환경에서 외부 요청이 Gateway 필터(가시성, JWT 등)를 거쳐 내부 컨트롤러 응답까지 도달하는지 통합 테스트.
- **Phase 4: 모드 3 (원격) 관측성 및 헬스체크 기능 추가 (난이도: 중)**
  - 내용: 갭 분석에서 "중" 우선순위였던 백엔드 헬스체크 및 `ProxyHandler`의 재시도(Retry) 도입. 메트릭 연동.
  - 파일 경계: `WebClientConfig.kt`, `ProxyHandler.kt`, `GatewayRouteSelector.kt`.
  - 선행 조건: Phase 3 완료.
  - 가드 테스트: 업스트림이 503 반환 시 N회 재시도 동작 확인. 헬스체크 실패 노드는 라운드로빈에서 제외되는지 확인.

## 5. 조사에서 확인 못 한 것

- KrakenD 엔터프라이즈의 응답 캐시 및 gRPC 백엔드 최신 지원 상세 스펙 미확인.
- Netflix Zuul 최신 버전(Zuul 2)의 HTTP/3 지원 여부 등 일부 문서 불충분 항목.
- NGINX Plus 외 OSS 버전만 사용할 경우 JWT 검증용 Lua 오픈소스 모듈의 프로덕션 안정성 미확인.
