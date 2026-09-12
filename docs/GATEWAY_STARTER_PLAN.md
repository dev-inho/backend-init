# 게이트웨이 탑재식(Starter) 지원 및 로드맵

## 1. 갭 분석 (기능 매트릭스 대비)

현재 `core/gateway` 구현체와 상용 게이트웨이를 비교한 갭 분석입니다. (기준: 작은 팀의 백엔드 초기 프로젝트)

| 기능 | 현재 구현 상태 (`core/gateway`) | 갭 | 우선순위 | 근거 (`core/gateway` 기준) |
| --- | --- | --- | --- | --- |
| 라우팅 | 정적 URL 라우팅 (`application-url(s)`, `batch-url`) | 헤더/가중치/우선순위 기반 라우팅 부재 | 하 | 초기 프로젝트는 단순 경로 매핑으로 충분함. (`GatewayRouteSelector.kt`) |
| 인증/인가 | JWT 검증 (`JwtAuthFilter.kt:1`) | OAuth2, API Key, mTLS 부재 | 하 | 현재 JWT로 사내/B2C 요구사항 대부분 커버 가능함. |
| Rate Limit | Redis 기반 Rate Limit (`RedisRateLimiter.kt`, fail-open) | 다양한 알고리즘 부재 | 하 | fail-open 방식의 Lua 스크립트 기반 제한은 초기 트래픽 방어에 충분함. |
| 회복성 | 없음 (단순 Proxy) | 타임아웃, 재시도, 서킷 브레이커 부재 | 상 | 모드 3(원격 서버)에서 타임아웃/재시도 부재 시 전체 시스템 장애로 이어질 수 있음. (`ProxyHandler.kt`) |
| 관측성 | `RequestIdFilter.kt:0`, `HttpLoggingFilter.kt:-2`, 가시성 | 분산 추적(OTel - 보류됨), 메트릭 부재 | 중 | 로깅은 있으나 장애 인지를 위한 헬스체크 및 메트릭은 추후 필요함. (`FUTURE.md:97-101`) |
| 요청/응답 변환 | 바이트 배열 버퍼링 | 선언적 헤더/바디/경로 재작성 부재 | 하 | 현재 API 프록시 용도로는 충분함. (`ProxyHandler.kt:39`) |
| 캐싱 | 없음 | 응답 캐시 지원 안 함 | 하 | 백엔드 애플리케이션 단에서 처리 가능하므로 당장 필요하지 않음. |
| 서비스 디스커버리 | 정적 목록 기반 라운드로빈 | 헬스체크 부재 | 중 | 모드 3 도입 시 다운된 업스트림에 계속 요청하는 문제 발생 가능. 헬스체크는 필수. |

## 2. 탑재식(Starter) 설계 제안

### 2.1. 모드 1: 하나의 물리 서버, 한 프로세스
- **설명**: application이 gateway-starter를 의존하여 동일 JVM 내에서 필터 체인 및 라우팅이 동작.
- **이중 등록 문제**: `RequestIdFilter`와 `HttpLoggingFilter`가 `support:web`과 `gateway`에 모두 존재 시 이중 등록됨. `@ConditionalOnMissingBean` 또는 빈 등록 순서(Order) 재조정 및 조건부 빈 설정(`@ConditionalOnProperty("gateway.mode")`)으로 Gateway 측 필터만 활성화하도록 제어.
- **이중 인증 문제**: `SecurityConfig`와 `JwtAuthFilter`의 중복. Gateway에서 인증을 완료한 경우 HTTP 요청을 그대로 디스패치하되 내부 시큐리티 컨텍스트에 정보를 넘겨 Application의 시큐리티 체인을 우회하거나 가볍게 통과하도록 처리.
- **라우팅 방식 (후보)**:
    - **후보 1**: 인프로세스 디스패치 (HTTP 프록시 대신 WebFlux `DispatcherHandler` 로 직접 전달) - 성능 이점은 있으나 `ProxyHandler`의 WebClient 호출을 제거하고 구조를 크게 바꿔야 함.
    - **후보 2**: 루프백 HTTP (`localhost:포트` 로 자기 자신을 호출) - 구현이 단순하고 모드 간 `ProxyHandler` 로직 공유 가능. 단, 네트워크 오버헤드 존재.
    - **루프 위험**: 루프백 사용 시 자신을 무한 호출하는 문제 방지를 위해, 라우팅 대상이 자신일 경우(포트 일치) 필터 체인에서 예외를 던지는 가드 로직 필수.

### 2.2. 모드 2: 게이트웨이가 독립 서버 (같은 머신, 별도 프로세스)
- **설명**: 현재 유지 중인 형태. 포트 분리 및 localhost 업스트림 활용.
- **설계**: `gateway-starter` 의존성을 포함하는 얇은 부트 껍데기 애플리케이션(예: `GatewayApplication`)으로 분리.

### 2.3. 모드 3: 게이트웨이 단독 실행 (다른 물리 서버)
- **설명**: 원격 업스트림 통신.
- **설계**: `ProxyHandler`에 네트워크 타임아웃, 재시도 속성 추가. 서비스 목록은 정적 유지하되 헬스체크 로직 도입. 설정은 `docs/FUTURE.md:31-50` 에 따라 Config Server 대신 파일이나 환경 변수로 제공.

### 2.4. 공통 설계 방안
- **모듈 분할 제안**:
    - `gateway-core`: 핵심 로직 (스프링 자동 설정 배제).
    - `gateway-autoconfigure`: `@AutoConfiguration` 기반의 조건부 빈 설정.
    - `gateway-starter`: 프로젝트 의존성 묶음.
    - `gateway-app`: 모드 2, 3을 기동하기 위한 독립 부트 모듈.
- **설정 키**: `gateway.mode=embedded | standalone | remote`
- **JWT 및 Rate Limit 위치**: 각 모드에서 최초 인입점인 게이트웨이 프로세스(JVM) 내부에서 동작하여 백엔드 부하 방지.
- **X-Request-Id 전파**: `RequestIdFilter`에서 생성 후 HTTP Header를 통해 원격 또는 루프백 호출 시 전파.

### 2.5. Spring Cloud Gateway 도입 vs 자체 구현 유지 비교
- **SCG 도입**: 다양한 내장 필터와 서킷 브레이커, 타임아웃 지원이 강력하나, 현재 프로젝트 규모에 비해 무겁고 의존성 관리가 복잡해짐.
- **자체 구현 유지**: 가볍고 현재의 요구사항(`RequestVisibilityFilter` 등)에 완벽히 맞춤화되어 있으며, starter 화 하기도 직관적임.

## 3. 추천안 및 🔶 사용자 결정 필요 항목

**추천안**: 자체 구현을 유지하면서 모듈을 분리하여 `gateway-starter` 방식으로 전환합니다. 모드 1의 라우팅은 초기 복잡도를 낮추기 위해 **루프백 HTTP** 방식을 추천합니다.

- 🔶 **사용자 결정 필요 1**: 자체 게이트웨이 코드를 유지할 것인가, 아니면 Spring Cloud Gateway로 전면 교체할 것인가?
- 🔶 **사용자 결정 필요 2**: 모드 1 (Embedded) 환경에서의 라우팅 방식 (루프백 HTTP vs 인프로세스 디스패치)
- 🔶 **사용자 결정 필요 3**: 모드 설정을 위한 프로퍼티 키 명칭 (`gateway.mode` 등)

## 4. 로드맵 (Phase 별 워커 브리프 크기 분할)

- **Phase 0: S1~S3 선행 작업 완료 대기**
    - 현재 진행 중인 패키지 재배치 및 JWT 공유 등의 작업 완료 후 시작.
- **Phase 1: 게이트웨이 모듈 분리**
    - 내용: `core/gateway`를 `gateway-core`, `gateway-autoconfigure`, `gateway-starter`로 모듈 분할.
    - 파일: `core/gateway/build.gradle` 수정 및 새 모듈 생성.
    - 예상 난이도: 하
    - 가드 테스트: Auto-config를 통해 기존 필터 빈들이 정상 로드되는지 컨텍스트 테스트.
- **Phase 2: 모드 1 기반 필터 충돌 해결**
    - 내용: `gateway.mode=embedded` 조건에 따라 `RequestIdFilter`, `HttpLoggingFilter` 의 중복 등록 방지(`@ConditionalOnMissingBean` 활용).
    - 파일: `gateway-autoconfigure`, `support/web/` 필터 설정부.
    - 예상 난이도: 중
    - 가드 테스트: 모드 1(애플리케이션과 통합) 기동 시 빈 충돌 예외 미발생 및 필터 체인 1회 실행 확인.
- **Phase 3: 무한 루프 방지 및 모드 3 원격 타임아웃 구현**
    - 내용: `ProxyHandler`에 라우팅 루프 방지 검증 로직과 외부 호출용 타임아웃/재시도 설정 추가.
    - 파일: `ProxyHandler.kt`, 설정 프로퍼티 클래스.
    - 예상 난이도: 중
    - 가드 테스트: 라우팅 목적지가 현재 서버의 로컬 포트와 동일할 때 초기화 시 또는 라우팅 시도 시 예외 던짐 확인.
- **Phase 4: 독립 실행형 앱 (gateway-app) 구성**
    - 내용: 모드 2, 3 전용의 기동용 Spring Boot 껍데기 모듈 구성.
    - 파일: 새로운 애플리케이션 엔트리포인트 및 `application.yml`.
    - 예상 난이도: 하
    - 가드 테스트: `gateway-app` 포트 충돌 없이 기동되는지 통합 테스트 구동.
