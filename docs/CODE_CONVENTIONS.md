# 코드 컨벤션 및 품질 규칙 (Code Conventions)

이 문서는 backend-init 프로젝트의 코드 작성 원칙, KDoc 문체, 패키지 및 아키텍처 규칙, 구조 가드 검증 체계를 정의합니다.

---

## 1. KDoc 작성 원칙 (사용자 결정 ①)

모든 공개(public/internal) 인터페이스, 핵심 도메인 포트, 필터, 유틸리티에는 한국어 산문형 KDoc을 작성합니다.

### 문체 및 서술 규칙
- **첫 문장**: "무엇을 한다"를 단 한 줄로 명확하게 선언합니다.
- **본문 문단**: 단순 동작 설명 대신 **왜 이렇게 설계했는가(배경·트레이드오프)**, **제약 조건**, **호출 순서 및 전후 관계**, **보안/실패 정책(fail-open/fail-closed)**을 구체적으로 설명합니다.
- **태그 금지**: `@param`, `@return`, `@throws` 태그와 "매개변수:", "반환값:" 같은 별도 절 제목은 사용하지 않습니다. 필요한 설명은 본문 산문에 자연스럽게 녹여 씁니다.
- **코드 단순 재서술 금지**: 코드를 그대로 읽는 주석(예: "사용자 ID를 반환한다")은 작성하지 않습니다. 그런 자명한 함수나 프로퍼티는 주석을 생략합니다.

### 좋은 예시 (Best Practices)
- **`support/logging/src/main/kotlin/cc/midolog/logging/MaskingSupport.kt:5-14`**
  ```kotlin
  /**
   * 로그 메시지 텍스트에 임베드된 이메일/전화번호/카드번호 후보를 정규식으로 탐지해
   * util Masking(maskEmail/maskPhone/maskCard)으로 치환하는 순수 로직.
   *
   * 보안 원칙은 fail-closed다 — 미탐(탐지 실패)이 곧 평문 유출이므로, 흔한 구분자
   * 변형(하이픈/공백/점/슬래시)과 국제 전화 접두(+82), Amex 4-6-5 그룹까지 폭넓게
   * 탐지한다. 카드 패턴을 전화 패턴보다 먼저 처리해 자릿수가 겹치는 숫자열이
   * 카드로 우선 인식되도록 한다. 과탐(비-PII 숫자열을 마스킹)은 fail-closed 방향의
   * 허용 가능한 트레이드오프로 본다.
   */
  ```
- **`gateway/core/src/main/kotlin/cc/midolog/gateway/filter/AuthTokenRateLimitFilter.kt:14-33`**
  ```kotlin
  /**
   * 인증 토큰 발급 엔드포인트(POST /api/auth/token) 전용 무차별 대입(brute-force) 방어 필터.
   *
   * 필터 체인 순서 계약:
   * -2 HttpLoggingFilter(support:web) → -1 AuthTokenRateLimitFilter → 0 RequestIdFilter(support:web) → 1 JwtAuthFilter → 100 RequestVisibilityFilter
   *
   * 앞뒤 순서와 위치 이유:
   * 앞에는 최외곽 로깅 필터(cc.midolog.web.filter.HttpLoggingFilter, @Order(-2))가 위치해
   * 모든 인입 요청의 시작과 종료 메타데이터를 기록한다. 뒤에는 cc.midolog.web.filter.RequestIdFilter(@Order(0)),
   * [JwtAuthFilter](@Order(1)), cc.midolog.gateway.visibility.RequestVisibilityFilter(@Order(100))가
   * 실행된다. 토큰 발급 엔드포인트는 로그인 전(미인증 상태)에 호출되므로 [JwtAuthFilter]보다 앞단에서
   * 무차별 대입을 차단해야 한다. 또한 RequestIdFilter보다도 앞서 한도 초과(429)로 즉시 거절함으로써
   * 미인증 공격 트래픽에 대한 불필요한 컨텍스트 생성 비용을 선제적으로 줄인다.
   *
   * 동작 및 정책:
   * - 대상: POST /api/auth/token 하나뿐이며 그 외 경로는 그대로 통과한다.
   * - 키: 클라이언트 remote address 기준으로 [RateLimiter]를 조회한다.
   * - 한도 초과 시 429(TOO_MANY_REQUESTS)를 반환하고 응답 본문은 비워 limiter 내부 상태나 자격증명 정보를 노출하지 않는다.
   * - [RateLimiter] 장애 발생 시 경고 로그 후 요청을 통과시킨다(fail-open).
   */
  ```

### 나쁜 예시 (Anti-Patterns)
```kotlin
// 나쁜 예 1: 코드 단순 재서술형 (주석 불필요)
/** 사용자 목록을 조회한다. */
fun getUsers(): List<User>

// 나쁜 예 2: Javadoc 태그형
/**
 * 토큰을 발급합니다.
 * @param request 인증 요청 DTO
 * @return 발급된 JWT 문자열
 * @throws ApiException 인증 실패 시 401
 */
fun issueToken(request: TokenRequest): ApiResponse<TokenResponse>
```

---

## 2. core:domain 모듈 주석 및 문자열 금지 토큰

`core:domain` 모듈은 순수 도메인 로직과 포트 인터페이스만을 유지해야 하며, 특정 프레임워크나 ORM 라이브러리에 결합되지 않아야 합니다.

- `DomainPurityTest`는 컴파일 검사뿐만 아니라 소스 파일 텍스트에 대해 `.contains(token)` 문자열 검사를 수행합니다.
- 따라서 클래스/변수명뿐만 아니라 **주석(KDoc/인라인)이나 문자열 리터럴에도 아래 토큰을 작성하면 테스트가 실패**합니다.

### 금지 토큰 목록
- 패키지/라이브러리: `org.springframework`, `jakarta.persistence`, `javax.persistence`, `org.jetbrains.exposed`, `org.mybatis`, `com.google.devtools.ksp`
- 어노테이션 형태: `@Entity`, `@Table`, `@Id`, `@Column`, `@MappedSuperclass`, `@Embeddable`, `@Repository`, `@Component`, `@Service`, `@DomainEntity`, `@GenerateJpa`

---

## 3. 패키지 및 아키텍처 규칙

### 영속성 계층 (Storage) 및 영속성 경계
- **영속성 경계 대원칙**: “JPA·MyBatis 관심사는 `storage/jpa`, `storage/mybatis` 밖으로 나오지 않는다.”
- **영속성 어댑터 패키지**: `cc.midolog.storage.<기술>.<컨텍스트>` (예: `cc.midolog.storage.mybatis.sample`, `cc.midolog.storage.jpa.user`)
- **어댑터 클래스 명명**: 어댑터 클래스는 기술명을 명시적 접두어로 사용합니다 (`MyBatis*RepositoryAdapter`, `Jpa*RepositoryAdapter`).
- **storage/jpa 쿼리 작성 규칙**:
  - 동적 쿼리 및 조건부 조회/수정은 **QueryDSL (`JPAQueryFactory`) 또는 Spring Data repository method**만 사용합니다.
  - 문자열 기반 JPQL(`entityManager.createQuery(...)`) 사용은 전면 금지됩니다.
  - `SelfContainedQueryDslGuardTest` 가드가 `storage/jpa/src/main` 소스 전체에서 문자열 JPQL(`createQuery(`) 0건과 6종의 QueryDSL Q 클래스(`QSampleJpaEntity.java`, `QUserJpaEntity.java`, `QFileMetaJpaEntity.java`, `QScalarSampleJpaEntity.java`, `QRelationParentJpaEntity.java`, `QRelationChildJpaEntity.java`)의 정확한 파일 경로 존재를 검증합니다.
- **가드 대상 및 금지 import**:
  - `storage/**`, `build-logic/**`를 제외한 모든 모듈(`core/**`, `gateway/**`, `support/**`, `client/**`)의 실제 소스 트리(`src/main`, `src/test`, `src/testFixtures`) 내 `.kt`/`.java` 소스에서 아래 영속성 패키지 import를 전면 금지합니다 (`PersistenceBoundaryTest` 가드):
    - `jakarta.persistence`
    - `org.springframework.data.jpa`
    - `org.hibernate`
    - `org.mybatis`
    - `org.apache.ibatis`
    - `cc.midolog.storage.jpa`
    - `cc.midolog.storage.mybatis`
    - `com.querydsl`
- **문자열 예외**: 주석, KDoc, 일반 문자열 리터럴은 import가 아니므로 허용됩니다. 예를 들어 `FileStorageIntegrationTest`의 `spring.autoconfigure.exclude`에 사용된 `org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration` 등의 property 설정 문자열은 import가 아니므로 명시적으로 허용됩니다.
- **명시적 예외 두 가지**:
  1. `core/application/build.gradle`의 조합 루트 의존성:
     - 런타임 어댑터 탑재 (3개): `runtimeOnly project(':storage:mybatis')`, `runtimeOnly project(':storage:jpa')`, `runtimeOnly project(':storage:file-local')`
     - 테스트 런타임 어댑터 탑재 (2개): `testRuntimeOnly project(':storage:mybatis')`, `testRuntimeOnly project(':storage:jpa')`
     - 애플리케이션 실행 조합 루트(composition root)가 어댑터를 classpath에 싣는 유일한 자리로서 허용됩니다. 소스 코드 레벨에서는 컴파일 타임 의존(`implementation`, `testImplementation`)이 없으므로 import할 수 없습니다.
  2. `build-logic`의 JPA DSL 생성기: 빌드 및 코드 생성 도구 경계로 소스 스캔 대상 밖입니다.

### 웹 계층 및 응답 DTO
- 컨트롤러 응답 DTO 필수: `cc.midolog.web` 하위 컨트롤러는 도메인 모델(`Sample`, `User`)을 `ApiResponse`로 직접 반환하지 않고 전용 응답 DTO(`web/<ctx>/dto/*Response`)를 사용해야 합니다.

### 게이트웨이 4모듈 경계 및 아키텍처 규칙
- **의존 방향 및 모듈 경계**:
  - `gateway:app` → `gateway:starter` → `gateway:core` + `gateway:autoconfigure` 단방향 흐름을 유지합니다.
  - `gateway:autoconfigure`는 `gateway:core`에 의존하며, `gateway:core`는 상위 실행 모듈을 알지 못하고 `support:*`(`logging`, `util`, `web`, `jwt`) 모듈만 의존합니다.
  - 하위 모듈에서 상위 모듈로의 역방향 참조는 엄격히 금지됩니다.
- **설정 패키지 격리**: `cc.midolog.gateway.config` 패키지는 하위 프록시/라우트 패키지(`cc.midolog.gateway.proxy`, `cc.midolog.gateway.route`, `cc.midolog.gateway.handler`)를 import하지 않습니다 (`GatewayPackageDependencyTest` 가드).
- **자동 설정 명시 등록 및 컴포넌트 스캔 배제**:
  - `gateway:autoconfigure`는 광범위한 패키지 컴포넌트 스캔(@ComponentScan)을 사용하지 않고, `@AutoConfiguration`, `@Configuration(proxyBeanMethods = false)`, `@Import`, `@Bean`을 통해 필요한 설정과 빈을 명시적으로 등록합니다.
  - Spring Boot 4 표준에 따라 자동 설정 클래스는 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`에 FQCN을 등록하며, 레거시 `spring.factories`는 사용하지 않습니다.
- **`gateway.mode` 필수 및 Fail-fast 원칙**:
  - 게이트웨이는 `gateway.mode`(`embedded`, `standalone`, `remote`) 설정이 필수이며 기본값을 제공하지 않습니다. 잘못된 값이거나 누락 시 기동 단계에서 즉시 fail-fast합니다.
- **Embedded 무라우팅 원칙**:
  - `embedded` 모드에서는 functional routes(`RouteConfig`)와 프록시 빈을 등록하지 않습니다. WebFlux에서 `RouterFunctionMapping`(-1)이 컨트롤러 매핑(0)보다 우선순위가 높기 때문에, 라우트를 비워둠으로써 동일 JVM 내의 `@RestController`가 요청을 직접 처리하도록 합니다.
- **support:web 웹 필터 호스트 스캔 경계**:
  - `support:web`의 `HttpLoggingFilter`(@Order(-2))와 `RequestIdFilter`(@Order(0))는 호스트 애플리케이션의 `cc.midolog` 패키지 스캔으로 자동 감지 및 등록되며, `gateway:autoconfigure`가 별도로 등록하지 않습니다.

### 인프라 어댑터 및 자동 설정
- 인프라 격리: Redis, 외부 스토리지 등 인프라 어댑터 구현체는 비즈니스 계층과 분리하여 `infra/` 패키지 아래 위치시킵니다 (예: `cc.midolog.infra.cache.RedisSampleCacheAdapter`). application의 인프라·설정 코드는 `infra/`로 통일하며 `common/` 패키지를 금지합니다.
- **자동 설정 @Bean 이름 충돌 방지 규칙**:
  - `@AutoConfiguration` 클래스에서 등록하는 `@Bean`의 이름은 호스트 애플리케이션의 패키지 컴포넌트 스캔에 의해 감지되는 레거시 `@Component`의 기본 빈 이름(`decapitalize(ClassName)`)과 충돌하지 않도록 명시적 빈 이름을 부여해야 합니다.
  - **현재 예시**: `client:storage-file`의 `@Component class LocalFileStorageAdapter`는 Spring의 기본 명명 규칙에 따라 `localFileStorageAdapter`로 등록됩니다. 반면 신규 자동 설정 모듈 `storage:file-local`의 `FileStorageAutoConfiguration`에서는 `@Bean("fileLocalStorageAdapter")`로 명시적 이름을 지정함으로써, 호스트 컴포넌트 스캔과 자동 설정이 함께 로드될 때 빈 이름 충돌(`BeanDefinitionOverrideException`) 없이 두 어댑터가 컨텍스트 내에 안전하게 공존하도록 보장합니다.

---

## 4. 구조 가드 (Architecture Guards)

아키텍처 규칙은 구두 합의에 그치지 않고 자동화된 테스트 가드로 강제합니다.

1. **`DomainPurityTest`** (`core/domain/src/test/kotlin/cc/midolog/sample/DomainPurityTest.kt`)
   - 지키는 것: `core:domain` 소스 전반에 프레임워크/ORM 어노테이션 및 패키지 참조 문자열이 유입되는 것을 원천 차단.
2. **`GatewayPackageDependencyTest`** (`gateway/core/src/test/kotlin/cc/midolog/gateway/GatewayPackageDependencyTest.kt`)
   - 지키는 것: 게이트웨이 `config` 패키지가 하위 `handler`, `proxy`, `route` 패키지를 역참조하여 순환 참조를 형성하는 것을 차단.
3. **`ControllerResponseTypeTest`** (`core/application/src/test/kotlin/cc/midolog/web/ControllerResponseTypeTest.kt`)
   - 지키는 것: 웹 컨트롤러에서 도메인 엔티티를 클라이언트에 직접 노출하지 않고 전용 DTO로 변환하여 응답하도록 보장.
4. **`FileStorageIntegrationTest`** (`core/application/src/test/kotlin/cc/midolog/storage/FileStorageIntegrationTest.kt`)
   - 지키는 것: 호스트 패키지 스캔(`cc.midolog`)과 `FileStorageAutoConfiguration`이 함께 로드될 때 레거시 빈 `localFileStorageAdapter`와 신규 빈 `fileLocalStorageAdapter`가 이름 충돌 없이 공존하며 두 `FileStoragePort`가 정상 등록되도록 보장.
5. **`ApplicationPackageStructureTest`** (`core/application/src/test/kotlin/cc/midolog/ApplicationPackageStructureTest.kt`)
   - 지키는 것: `core:application` 내 인프라·설정 코드를 `infra/`로 통일하고 `common/` 패키지 사용을 원천 차단.
6. **`PersistenceBoundaryTest`** (`core/application/src/test/kotlin/cc/midolog/PersistenceBoundaryTest.kt`)
   - 지키는 것: `storage/jpa`, `storage/mybatis` 외 모듈(`core`, `gateway`, `support`, `client`) 소스 트리 전체에서 JPA, MyBatis, Hibernate, QueryDSL(`com.querydsl`) 등 영속성 관심사의 import 유출을 원천 차단.
7. **`SelfContainedQueryDslGuardTest`** (`storage/jpa/src/test/kotlin/cc/midolog/storage/jpa/sample/SelfContainedQueryDslGuardTest.kt`)
   - 지키는 것: `storage/jpa/src/main` 내 문자열 JPQL(`createQuery(`) 0건 유지 및 6개 QueryDSL Q 클래스(`QSampleJpaEntity`, `QUserJpaEntity`, `QFileMetaJpaEntity`, `QScalarSampleJpaEntity`, `QRelationParentJpaEntity`, `QRelationChildJpaEntity`)의 정확한 파일 경로 존재를 검증.

---

## 5. 전체 검사 명령 및 개발 환경 정책

### 전체 검사 명령
모든 변경 사항은 아래 명령으로 완전히 검증합니다:
```bash
./gradlew build && ./gradlew -p build-logic test && git diff --check
```
- `build-logic` 테스트는 별도 프로젝트 플래그(`-p build-logic test`)로 수행해야 합니다.

### Docker 컨테이너 기동 불필요
- 이 프로젝트는 로컬 개발 및 CI에서 Docker 컨테이너(PostgreSQL, Redis)를 띄우지 않고도 모든 단위 및 통합 테스트가 인메모리(H2 등) 및 모킹 환경에서 통과하도록 설계되었습니다.
- 실제 DB 대상 검증(`:storage:jpa:livePostgresTest`)은 선택적 명령으로만 수행합니다.

---

## 6. 죽은 코드 및 레거시 정책 (사용자 결정 ②)

- **임의 삭제 금지**: 사용되지 않거나 참조가 없는 것처럼 보이는 public 심볼/파일이라도 임의로 삭제하지 않습니다.
- **인벤토리 등재**: 삭제 대상 후보를 발견한 경우, 먼저 `docs/DEAD_CODE_CANDIDATES.md`에 파일 경로, 라인 번호, 미사용 근거를 기록하고 리뷰를 통해 정리 순서를 결정합니다.
