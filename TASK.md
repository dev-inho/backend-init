# 일감 — gateway/core + gateway/autoconfigure 분할과 gateway.mode 조건부 자동 설정

## 근거

`docs/GATEWAY_STARTER_PLAN.md` 4절 Phase 1과 리드 브리프의 사용자 결정(2026-09-12)을 구현한다.

> Spring Cloud Gateway를 채택하지 않고 자체 구현을 유지한다. 모드 키는 `gateway.mode=embedded|standalone|remote`이며 기본값은 없다. embedded는 같은 JVM의 `@RestController`가 처리하므로 프록시 라우터와 `JwtAuthFilter`를 등록하지 않는다.

현재 `core/gateway`는 `GatewayApplication`의 `scanBasePackages = ["cc.midolog"]`에 기대어 `@Component`/`@Configuration`을 등록한다. starter 소비 호스트는 그 스캔 범위를 보장하지 않으므로, 이 PR에서 프레임워크 자동 설정 등록으로 바꾼다. Spring Boot 4 자동 설정 등록 파일은 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`이며 `spring.factories`는 쓰지 않는다.

이 PR은 Phase 1만 맡는다. `gateway/core`와 `gateway/autoconfigure`를 만들고 기존 gateway 로직/테스트를 옮긴다. 전환 중 `core/gateway`에는 `GatewayApplication`, yml, `GatewayProfileConfigTest`와 빌드 가능한 얇은 껍데기를 남긴다. `gateway/starter`, `gateway/app`, `.env.example`, 문서 갱신, `core/gateway` 최종 제거는 후속 W2/W3의 범위다. `core/application/**`, `core/domain/**`, `storage/**`, `support/**`는 읽기만 하고 수정하지 마라.

## 먼저 읽어라 (추측 금지)

1. `~/.claude/skills/orca-worker/SKILL.md` 전체를 먼저 읽고 그 순서를 따른다.
2. `CLAUDE.md`, `docs/GATEWAY_STARTER_PLAN.md:23-89`, `docs/CODE_CONVENTIONS.md:7-15,76-100,106-124`, 루트 `TASK.md`의 사용자 결정 1~6과 범위 경계.
3. `settings.gradle:1-22`, `core/gateway/build.gradle:1-23`, `core/gateway/src/main/kotlin/cc/midolog/GatewayApplication.kt:1-23`, 두 yml. W1 뒤에도 `:core:gateway`가 빌드·기동 가능한 전환 상태여야 한다.
4. 빈 후보 전체: `core/gateway/src/main/kotlin/cc/midolog/gateway/**`. 특히 현재 stereotype은 `GatewayClockConfig.kt:15`, `GatewayRouteProperties.kt:15-16`, `RouteConfig.kt:23`, `WebClientConfig.kt:23`, `AuthTokenRateLimitFilter.kt:34-35`, `JwtAuthFilter.kt:31-32`, `ProxyHandler.kt:32`, `RedisRateLimiter.kt:19`, `GatewayRouteSelector.kt:25`, `visibility/RequestEventStore.kt:17-18`, `RequestVisibilityController.kt:25`, `RequestVisibilityFilter.kt:33-35`, `RequestVisibilityProperties.kt:14-15`에 있다.
5. 생성자와 의존 방향: `RouteConfig.kt:13-28`은 표준 `HandlerFunction`만 받는다. `ProxyHandler.kt:32-36`은 `WebClient`와 `GatewayRouteSelector`, `AuthTokenRateLimitFilter.kt:34-38`은 `RateLimiter`, `RedisRateLimiter.kt:19-24`는 `ReactiveStringRedisTemplate`, `JwtAuthFilter.kt:31-36`은 `jwt.secret`, visibility 클래스들은 properties/store/clock 관계를 실제 코드로 확인한다.
6. fail-fast 문체·정책: `support/util/src/main/kotlin/cc/midolog/util/JwtSecretValidator.kt:20-36`와 테스트. `gateway.mode` 미설정은 같은 식으로 명확한 메시지를 남기며 컨텍스트 시작을 실패시켜야 한다.
7. 기존 테스트 전체: `core/gateway/src/test/kotlin/cc/midolog/gateway/**`. `RequestVisibilityTest.kt:143-155`의 `ApplicationContextRunner` 뼈대와 `GatewayPackageDependencyTest.kt:10-35`의 문자열 구조 가드를 살려 옮긴다. `GatewayProfileConfigTest`는 W2가 yml과 함께 옮기므로 이번에는 `core/gateway`에 둔다.
8. 읽기 전용 순서 근거: `support/web/src/main/kotlin/cc/midolog/web/filter/HttpLoggingFilter.kt:21-23`(-2), `RequestIdFilter.kt:20-22`(0). support:web 필터는 자동 설정에 넣지 말고 계속 호스트 스캔에 맡긴다.
9. 같은 자리를 만지는 다른 워커는 없다. W2는 이 PR이 main에 머지된 뒤에만 새 워크트리에서 시작한다.

## 해야 할 것

1. **재현부터**: 먼저 `gateway/autoconfigure`의 실패하는 `ApplicationContextRunner` 가드를 작성한다. 적어도 다음을 값으로 단언하라.
   - `gateway.mode=standalone`, 강한 `jwt.secret`, 유효한 route URL, visibility=true에서 `AuthTokenRateLimitFilter`, `RedisRateLimiter`/`RateLimiter`, `RequestVisibilityFilter`·store·controller, `GatewayClockConfig`의 clock, `routes`, `ProxyHandler`, `HeaderSanitizer` 사용 경로, `WebClient`, `GatewayRouteSelector`, `GatewayRouteProperties`, `JwtAuthFilter`가 자동 설정으로 존재한다. support:web의 두 필터는 이 컨텍스트에 없음을 전제로 하므로 gateway WebFilter 3개가 맞다.
   - `gateway.mode=remote`도 standalone과 같은 프록시/라우팅 빈 묶음을 가진다.
   - `gateway.mode=embedded`, visibility=true에서는 rate limit·visibility·clock 공통 빈은 존재하고 `routes`·`ProxyHandler`·`GatewayRouteSelector`·`GatewayRouteProperties`·프록시 WebClient·`JwtAuthFilter`는 없다.
   - mode 미설정은 컨텍스트 실패이며 원인 메시지에 `gateway.mode`와 허용값 `embedded|standalone|remote`가 드러난다. 기본값을 두지 마라. 알 수 없는 값도 명확히 실패시켜라.
   - `AutoConfiguration.imports` 리소스가 실제 `GatewayAutoConfiguration` FQCN을 등록했는지 가드한다.
2. **WebFlux 순서 실측 가드**: 실제 WebFlux application context에서 functional router의 `RouterFunctionMapping.order == -1`, annotated controller의 `RequestMappingHandlerMapping.order == 0`임을 측정하는 테스트를 남겨라. 이 때문에 embedded에 `routes` 빈이 남으면 `/api/**` controller보다 먼저 매칭되어 가로챈다는 사실을 `GatewayAutoConfiguration` 또는 embedded 조건 구성의 한국어 산문형 KDoc에 적어라. 숫자를 추측하거나 단순 상수 비교로 끝내지 말고 실제 mapping 빈을 컨텍스트에서 꺼내라.
3. **모듈 분할**:
   - `gateway/core`(`:gateway:core`)는 부트 애플리케이션이 아닌 라이브러리다. 재사용 동작 클래스와 기존 단위 테스트를 옮긴다. `org.springframework.boot` 플러그인을 적용해 bootJar를 만드는 모듈로 만들지 마라.
   - `gateway/autoconfigure`(`:gateway:autoconfigure`)는 `GatewayAutoConfiguration`, `GatewayModeProperties`와 조건부 빈 배선을 소유하고 `AutoConfiguration.imports`를 제공한다. Spring Boot 4 관례와 Kotlin proxy 제약을 지켜라.
   - 자동 설정은 호스트의 `scanBasePackages` 밖에서도 작동해야 한다. 기존 stereotype을 제거하고 명시적 `@Bean`, `@Import`, 제한된 내부 스캔 가운데 후보를 실제 의존 관계와 Boot 자동 설정 관례로 비교해 하나를 고르고 PR 본문에 근거를 적어라. 광범위한 `cc.midolog` component scan은 금지다.
   - 조건이 클래스에 흩어져 embedded에서 프록시 빈 일부가 새지 않도록 공통/프록시 묶음의 경계를 명확히 하라. standalone과 remote는 같은 조건 묶음이다.
   - `RequestVisibility*`의 기존 `gateway.request-visibility.enabled=true` 조건을 유지한다. `AuthTokenRateLimitFilter` + `RateLimiter`/`RedisRateLimiter`와 clock은 모든 세 모드 공통이다.
4. **전환 빌드 유지**: `settings.gradle`에 새 두 모듈을 추가하되 `include 'core:gateway'`는 W2까지 유지한다. `core/gateway/build.gradle`은 남은 부트 껍데기가 새 모듈의 자동 설정을 소비하도록 최소 변경한다. gateway 패키지 소스/단위 테스트를 중복 복사하지 말고 이동하라. W1에서는 `core/gateway` 디렉터리를 삭제하지 마라.
5. **구조 가드 유지**: `GatewayPackageDependencyTest`를 새 경로에 맞게 옮기고 실제 `config` 소스 루트를 검사하게 하라. `config` package가 `proxy`/`route`/`handler` import를 얻으면 실패하는 성질을 보존한다. 자동 설정 orchestration 클래스는 `cc.midolog.gateway.autoconfigure`처럼 이 기존 규칙의 대상과 구별하되, 우회가 아니라 역할 분리임을 설명하라.
6. 공개/internal 타입과 새 자동 설정 클래스에는 `docs/CODE_CONVENTIONS.md`의 한국어 산문형 KDoc을 적용한다. `@param`/`@return`/`@throws` 태그를 쓰지 않는다. 죽은 코드로 보여도 삭제하지 말고 발견만 보고한다.
7. **가드 빼기 실험**: 최소한 (a) embedded 프록시 묶음 조건 하나를 실제로 제거해 embedded 부재 가드가 FAIL, (b) mode 필수 검증을 제거해 미설정 실패 가드가 FAIL, (c) package dependency test 대상 config 파일에 금지 import를 임시 삽입해 FAIL임을 각각 출력으로 남기고 원복한다. WebFlux order 가드는 기대 order를 하나 틀리게 해 FAIL을 확인하고 원복한다. 각 실험 후 `git diff`로 원복을 확인한다.
8. 전체 기존 테스트를 옮긴 위치에서 통과시키고, 마지막에 `~/.claude/skills/orca-worker/preflight.sh './gradlew build' './gradlew -p build-logic test' 'git diff --check'`를 필터 없이 돌린다. `livePostgresTest`는 돌리지 않는다. Docker/Postgres/Redis 서버는 띄우지 마라.
9. 브랜치 `refactor/gateway-split-autoconfig`를 push하고 base=main PR을 `--body-file`로 연다. PR 본문에는 선택한 빈 등록 방식과 대안 비교, WebFlux mapping order 실측, preflight 원문, 네 가드 제거 FAIL 원문, `git diff origin/main --stat` 실제 출력, W2/W3가 문서에 반영할 사항을 포함한다. `TASK.md`, PR 작성용 파일, 로그는 커밋하지 마라.

## 완료 조건 (추가)

- 파일 경계: 수정 가능은 `settings.gradle`, `gateway/core/**`, `gateway/autoconfigure/**`, 전환용 `core/gateway/**`뿐이다. `core/gateway`에서는 gateway package 소스/테스트 이동과 build.gradle 의존 배선만 하고 app/yml/ProfileConfigTest 의미를 바꾸지 않는다.
- 기존 테스트 전부 초록이며 `:core:gateway`, `:gateway:core`, `:gateway:autoconfigure`가 전체 빌드에서 함께 검증된다.
- embedded에서 functional routes와 JwtAuthFilter가 정말 없고, 공통 rate-limit/visibility/clock은 실제 빈으로 존재한다.
- ⚠️ 가드 실험을 되돌릴 때 통째 치환하지 마라. `git checkout -- <파일>`은 그 파일의 의도한 새 변경까지 없앨 수 있으므로 임시 한 줄만 정확히 복원한 뒤 diff를 확인하라.

## 공통 규칙

- 운영 포트/DB/컨테이너는 없다. 확인용 서버도 이 W1에서는 띄우지 마라. 프로세스를 내릴 때는 네가 띄운 pid만 `kill <pid>`하며 `killall`·`pkill -f`는 금지다.
- 이 워크트리의 파일 수정·생성은 미리 허락됐다. 묻지 말고 진행하되 워크트리 밖과 범위 밖 파일은 건드리지 마라.
- 전체 검사는 `./gradlew build && ./gradlew -p build-logic test && git diff --check`. 필터 없이 끝까지 돌리고 종료 코드를 남겨라. zsh의 `PIPESTATUS`는 믿지 말고 `cmd > log 2>&1; echo $?`로 잰다.
- 실측한 것만 보고한다. 컴파일 통과를 값 검사로 대신하지 않는다. 워커 3개 Gradle 동시 실행 금지, 큰 로컬 모델(ollama 등) 금지.
- PR만 열고 머지하지 마라. `git add .`, force push, amend 금지. 보고는 커밋·push·실제 `gh pr view` 확인 뒤에 한다.
- 사람 확인을 기다리며 멈추지 마라. 답신은 `orca orchestration inbox --full --limit 200`에서 읽는다.
- 끝나면 네가 띄운 프로세스를 모두 내리고 PR 본문에 적는다.
- 보고: `orca orchestration send --run run_4eca2dbd9364 --type status --subject "gw-split-autoconfig — PR #<번호>" --body-file <보고파일>`

