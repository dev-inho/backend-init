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
- **`core/gateway/src/main/kotlin/cc/midolog/gateway/filter/AuthTokenRateLimitFilter.kt:14-23`**
  ```kotlin
  /**
   * 인증 토큰 발급 엔드포인트(POST /api/auth/token) 전용 brute-force 방어 필터.
   * - 대상: POST /api/auth/token 하나뿐이며, 그 외 경로는 그대로 통과한다.
   * - key: 클라이언트 remote address 기준으로 [RateLimiter]를 조회한다.
   * - 한도를 초과하면 429를 반환하고, 응답 본문은 비워 limiter 내부 상태나
   *   자격증명 관련 정보를 노출하지 않는다.
   * - [RateLimiter] 조회 자체가 실패해도 경고 로그 후 요청을 통과시킨다(fail-open).
   *
   * [JwtAuthFilter](@Order(1))보다 먼저 실행되도록 순서를 그보다 낮게 둔다.
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

### 영속성 계층 (Storage)
- 영속성 어댑터 패키지: `cc.midolog.storage.<기술>.<컨텍스트>` (예: `cc.midolog.storage.mybatis.sample`, `cc.midolog.storage.jpa.user`)
- 어댑터 클래스 명명: 어댑터 클래스는 기술명을 명시적 접두어로 사용합니다 (`MyBatis*RepositoryAdapter`, `Jpa*RepositoryAdapter`).

### 웹 계층 및 응답 DTO
- 컨트롤러 응답 DTO 필수: `cc.midolog.web` 하위 컨트롤러는 도메인 모델(`Sample`, `User`)을 `ApiResponse`로 직접 반환하지 않고 전용 응답 DTO(`web/<ctx>/dto/*Response`)를 사용해야 합니다.

### 게이트웨이 패키지 의존 방향
- 설정 격리: `cc.midolog.gateway.config` 패키지는 하위 프록시/라우트 패키지(`cc.midolog.gateway.proxy`, `cc.midolog.gateway.route`, `cc.midolog.gateway.handler`)를 import하지 않습니다. 의존성 순환을 방지하기 위해 설정은 상위에서 하위를 일방향으로 주입받아야 합니다.

### 인프라 어댑터
- 인프라 격리: Redis, 외부 스토리지 등 인프라 어댑터 구현체는 비즈니스 계층과 분리하여 `infra/` 패키지 아래 위치시킵니다 (예: `cc.midolog.infra.cache.RedisSampleCacheAdapter`).

---

## 4. 구조 가드 (Architecture Guards)

아키텍처 규칙은 구두 합의에 그치지 않고 자동화된 테스트 가드로 강제합니다.

1. **`DomainPurityTest`** (`core/domain/src/test/kotlin/cc/midolog/sample/DomainPurityTest.kt`)
   - 지키는 것: `core:domain` 소스 전반에 프레임워크/ORM 어노테이션 및 패키지 참조 문자열이 유입되는 것을 원천 차단.
2. **`GatewayPackageDependencyTest`** (`core/gateway/src/test/kotlin/cc/midolog/gateway/GatewayPackageDependencyTest.kt`)
   - 지키는 것: 게이트웨이 `config` 패키지가 하위 `handler`, `proxy`, `route` 패키지를 역참조하여 순환 참조를 형성하는 것을 차단.
3. **`ControllerResponseTypeTest`** (`core/application/src/test/kotlin/cc/midolog/web/ControllerResponseTypeTest.kt`)
   - 지키는 것: 웹 컨트롤러에서 도메인 엔티티를 클라이언트에 직접 노출하지 않고 전용 DTO로 변환하여 응답하도록 보장.

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
