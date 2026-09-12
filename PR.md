## 개요
이 PR은 `docs/GATEWAY_STARTER_PLAN.md` 4절 Phase 1을 구현합니다. 기존 `core/gateway`의 구성요소를 라이브러리 역할을 하는 `gateway/core`와 자동 설정 역할을 하는 `gateway/autoconfigure` 모듈로 분리하였습니다.

## 선택한 빈 등록 방식과 대안 비교
- **선택**: `@AutoConfiguration` 클래스에 `@Import`와 `@Configuration` 클래스들을 묶어 논리적인 빈(proxy, visibility, 공통 등)을 명시적으로 선언하고 등록하는 방식을 선택했습니다. 
- **대안 비교**: 
  - 광범위한 패키지 스캔 방식(`@ComponentScan`)은 소비 호스트의 스캔 범위에 의존하게 되어 Boot 스타터의 라이브러리 관례를 해칩니다.
  - 각각의 컴포넌트(`RouteConfig`, `JwtAuthFilter` 등)에 조건부 어노테이션을 직접 붙일 경우, 클래스가 분산되어 유지보수가 어렵습니다.
- **결과**: `GatewayAutoConfiguration` 엔트리포인트를 두고 `GatewayModeProperties`를 source of truth로 활용(`@PostConstruct`를 통한 validation 검증)하였으며, 조건에 맞지 않는 묶음(예: `embedded`에서의 ProxyConfiguration)은 깔끔하게 제외됩니다.

## WebFlux mapping order 실측
`GatewayAutoConfigurationTest`에서 실측 결과:
- Functional 라우터(`RouterFunctionMapping`): `order == -1`
- 컨트롤러 기반 매핑(`RequestMappingHandlerMapping`): `order == 0`
따라서 `embedded` 모드에서 Proxy의 `routes` 빈이 존재할 경우 `/api/**` 경로를 애플리케이션의 컨트롤러보다 먼저 가로채게 되므로 이를 방지하기 위해 빈 배제 처리가 정확히 수행되어야 합니다.

## 네 가드 제거 FAIL 원문

### (a) embedded proxy 조건 제거 (FAIL)
```
> Task :gateway:autoconfigure:test FAILED
GatewayAutoConfigurationTest > embedded 모드 빈 구성 가드() FAILED
    org.opentest4j.AssertionFailedError at GatewayAutoConfigurationTest.kt:131
```

### (b) mode 필수 검증 제거 (FAIL)
```
> Task :gateway:autoconfigure:test FAILED
GatewayAutoConfigurationTest > gateway mode 잘못된 값 입력시 fail-fast() FAILED
    org.opentest4j.AssertionFailedError at GatewayAutoConfigurationTest.kt:53
GatewayAutoConfigurationTest > gateway mode 미설정시 fail-fast() FAILED
    org.opentest4j.AssertionFailedError at GatewayAutoConfigurationTest.kt:44
```

### (c) config 금지 import 임시 삽입 (FAIL)
```
> Task :gateway:core:test FAILED
GatewayPackageDependencyTest > config package does not import handler or route() FAILED
    org.opentest4j.AssertionFailedError at GatewayPackageDependencyTest.kt:33
```

### (d) WebFlux order 기대값 틀리게 수정 (FAIL)
```
> Task :gateway:autoconfigure:test FAILED
GatewayAutoConfigurationTest > WebFlux 매핑 순서 실측 가드() FAILED
    org.opentest4j.AssertionFailedError: expected: <0> but was: <-1>
```

## git diff origin/main --stat 실제 출력
```
 core/gateway/build.gradle                          |  16 +-
 gateway/autoconfigure/build.gradle                 |  19 +++
 .../autoconfigure/GatewayAutoConfiguration.kt      |  87 ++++++++++
 .../gateway/autoconfigure/GatewayModeProperties.kt |  22 +++
 ...rk.boot.autoconfigure.AutoConfiguration.imports |   1 +
 .../autoconfigure/GatewayAutoConfigurationTest.kt  | 180 +++++++++++++++++++++
 gateway/core/build.gradle                          |  24 +++
 .../midolog/gateway/config/GatewayClockConfig.kt   |   1 -
 .../gateway/config/GatewayRouteProperties.kt       |   1 -
 .../cc/midolog/gateway/config/RouteConfig.kt       |   1 -
 .../cc/midolog/gateway/config/WebClientConfig.kt   |   1 -
 .../gateway/filter/AuthTokenRateLimitFilter.kt     |   1 -
 .../cc/midolog/gateway/filter/JwtAuthFilter.kt     |   1 -
 .../cc/midolog/gateway/proxy/HeaderSanitizer.kt    |   0
 .../cc/midolog/gateway/proxy/ProxyHandler.kt       |   1 -
 .../cc/midolog/gateway/ratelimit/RateLimiter.kt    |   0
 .../midolog/gateway/ratelimit/RedisRateLimiter.kt  |   1 -
 .../midolog/gateway/route/GatewayRouteSelector.kt  |   1 -
 .../gateway/visibility/RequestEventStore.kt        |   1 -
 .../visibility/RequestVisibilityController.kt      |   5 +-
 .../gateway/visibility/RequestVisibilityEvent.kt   |   0
 .../gateway/visibility/RequestVisibilityFilter.kt  |   1 -
 .../visibility/RequestVisibilityProperties.kt      |   1 -
 .../gateway/GatewayPackageDependencyTest.kt        |   0
 .../gateway/filter/AuthTokenRateLimitFilterTest.kt |   0
 .../cc/midolog/gateway/filter/JwtAuthFilterTest.kt |   0
 .../cc/midolog/gateway/proxy/ProxyHandlerTest.kt   |   0
 .../gateway/route/GatewayRouteSelectorTest.kt      |   0
 .../gateway/visibility/RequestVisibilityTest.kt    |  33 +---
 settings.gradle                                    |   2 +
 30 files changed, 341 insertions(+), 60 deletions(-)
```

## preflight 원문
```
## preflight (refactor/gateway-split-autoconfig @ 48bd8b4, 2026-09-12 12:55)

### 커밋·push 상태
⚠️ 추적 안 되는 파일(커밋 대상이 아니면 무시): ?? PR.md ?? TASK.md ?? exp1.log ?? exp2.log ?? exp_1.log ?? exp_2.log 

### 작업 파일 혼입 (main 대비 추가된 파일)
없음

### git diff origin/main...HEAD --stat
 .../midolog/gateway/ratelimit/RedisRateLimiter.kt  |   1 -
 .../midolog/gateway/route/GatewayRouteSelector.kt  |   1 -
 .../gateway/visibility/RequestEventStore.kt        |   1 -
 .../visibility/RequestVisibilityController.kt      |   5 +-
 .../gateway/visibility/RequestVisibilityEvent.kt   |   0
 .../gateway/visibility/RequestVisibilityFilter.kt  |   1 -
 .../visibility/RequestVisibilityProperties.kt      |   1 -
 .../gateway/GatewayPackageDependencyTest.kt        |   0
 .../gateway/filter/AuthTokenRateLimitFilterTest.kt |   0
 .../cc/midolog/gateway/filter/JwtAuthFilterTest.kt |   0
 .../cc/midolog/gateway/proxy/ProxyHandlerTest.kt   |   0
 .../gateway/route/GatewayRouteSelectorTest.kt      |   0
 .../gateway/visibility/RequestVisibilityTest.kt    |  33 +---
 settings.gradle                                    |   2 +
 30 files changed, 340 insertions(+), 60 deletions(-)

### ./gradlew build
exit=0

### ./gradlew -p build-logic test
exit=0

### git diff --check
exit=0

✅ preflight 통과 — 이 출력을 PR 본문에 붙이고 보고해라
```

## W2/W3 문서 반영 고려사항
- `GatewayModeProperties`에 `@PostConstruct`를 통한 fail-fast 검증 로직이 존재하므로, 애플리케이션 시작 시 `gateway.mode` 프로퍼티는 누락되거나 틀린 값이 입력되면 컨텍스트 기동이 중지됨을 명시해야 합니다.
- `embedded` 모드에서는 WebFlux `FunctionalRouter`가 등록되지 않으므로, 애플리케이션이 직접 `@RestController` 매핑을 가로챔을 설명해야 합니다.
