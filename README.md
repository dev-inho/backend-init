# backend-init

Spring Boot 4 + Kotlin 헥사고날 멀티모듈 백엔드 — 게이트웨이를 통한 요청 분기 및 트랜잭션 ID 관리

---

## 개요

**backend-init**은 Spring Boot 4 + Kotlin 기반 멀티모듈 백엔드 템플릿입니다. 현재는 설계 문서와 실행 가능한 초기 골격을 함께 정리하는 단계입니다:

- **게이트웨이 분기**: 클라이언트 요청을 게이트웨이(`gateway:app`)에서 받아 비즈니스 서버(`core:application`)로 라우팅
- **헥사고날 아키텍처**: Ports & Adapters 패턴으로 비즈니스 로직과 인프라를 분리
- **트랜잭션 ID 관리**: 모든 요청에 `X-Request-Id`를 부여하여 분산 환경에서 추적 가능
- **멀티모듈 구조**: 15개 모듈로 기능을 명확히 분리
- **반응형 스택**: WebFlux + Kotlin 코루틴으로 고성능 비동기 처리

---

## 기술 스택

| 항목 | 버전/상세 |
|------|---------|
| **JDK** | Java 17 |
| **언어** | Kotlin 2.2.x |
| **프레임워크** | Spring Boot 4.0.x |
| **웹 스택** | WebFlux + Kotlin Coroutines |
| **빌드 도구** | Gradle (Groovy DSL) |
| **데이터** | PostgreSQL + MyBatis/JPA 선택형 adapter |
| **패턴** | 헥사고날(Ports & Adapters) |

---

## 모듈 구조

프로젝트는 5개 범주(core, gateway, client, storage, support)의 15개 모듈로 구성됩니다.

### 모듈 개요

| 모듈 | 책임 |
|------|------|
| **core:application** | 비즈니스 로직 및 도메인 서비스 구현 (포트 8081) |
| **core:batch** | 배치 작업 및 스케줄링 (포트 8082) |
| **core:domain** | 프레임워크 비의존 도메인 모델 및 포트 |
| **gateway:core** | 게이트웨이 핵심 필터, 라우팅, 프록시, 관측성 컴포넌트 |
| **gateway:autoconfigure** | `gateway.mode` 기반 Spring Boot 자동 설정 및 조건부 빈 등록 |
| **gateway:starter** | 게이트웨이 탑재용 스타터 라이브러리 (core + autoconfigure) |
| **gateway:app** | 독립 실행형 Spring Boot 게이트웨이 서비스 (포트 8080) |
| **client:storage-file** | 파일 저장소 클라이언트 (레거시 Ports 적응자, `@Deprecated`) |
| **storage:mybatis** | MyBatis 데이터 접근 계층 (Adapters) |
| **storage:jpa** | Spring Data JPA 데이터 접근 계층 (Adapters) |
| **storage:file-local** | 로컬 파일 시스템 저장소 어댑터 및 자동 설정 (Fail-fast 검증) |
| **support:util** | 공유 유틸리티 및 헬퍼 함수 |
| **support:logging** | 통합 로깅 및 모니터링 |
| **support:web** | 공통 WebFlux 필터, API 응답, 예외 처리 |
| **support:jwt** | JWT 토큰 발급 및 검증 코덱 (`JwtCodec`) |

### Persistence 선택

| 구현 | 모듈 | Profile | 저장 방식 | 검증 상태 | 주의사항 |
|------|------|---------|----------|----------|----------|
| MyBatis | `storage:mybatis` | `mybatis` | XML mapper + PostgreSQL SQL | adapter/profile wiring 테스트 통과 | 로컬 실행 시 `local,mybatis`를 명시 |
| JPA | `storage:jpa` | `jpa` | Spring Data JPA entity/repository | H2 기반 adapter 테스트와 profile wiring 테스트 통과 | blocking JPA 호출은 `Dispatchers.IO`와 `TransactionOperations` 경계에서 실행 |

운영 실행에서는 `mybatis`와 `jpa` persistence profile을 동시에 켜지 않습니다. 둘을 동시에 활성화하면 같은 도메인 repository port 구현이 2개 등록될 수 있습니다.

### 도메인 예제

- `sample` 도메인은 gateway/application/storage smoke와 회귀 테스트를 위한 최소 예제로 유지합니다.
- `user` 도메인은 새 업무 도메인을 추가하는 표준 흐름을 보여주는 실전형 예제입니다: `core:domain` model/port → `core:application` service/controller/infra → `storage:mybatis` (`cc.midolog.storage.mybatis`) mapper/adapter → `storage:jpa` DSL/adapter → contract/profile test.
- `user` API는 사용자 생성/조회 persistence 예제만 제공합니다. 비밀번호 저장, 회원가입 보안, refresh token, role/permission 체계는 이 템플릿 예제 범위 밖입니다.

### 플랫폼 옵션

- 다중 application instance는 `GATEWAY_APPLICATION_URLS`를 명시했을 때만 Gateway 내부 라운드로빈으로 사용합니다. 값이 없으면 기존 `GATEWAY_APPLICATION_URL` 단일 target을 사용합니다.
- request visibility는 기본 비활성화입니다. `GATEWAY_REQUEST_VISIBILITY_ENABLED=true`일 때만 `/internal/gateway/requests`가 활성화되며 JWT Bearer token이 필요합니다.
- Config Server와 OpenTelemetry exporter는 기본 템플릿에 포함하지 않습니다. 현재 baseline은 환경변수/secret manager 주입과 `X-Request-Id` 기반 추적입니다.

### 디렉토리 구조

```
backend-init/
├── settings.gradle          # Gradle 멀티모듈 설정
├── build.gradle             # 루트 빌드 설정
├── docs/                    # 설계 및 운영 문서
│   ├── ARCHITECTURE.md
│   ├── CODE_CONVENTIONS.md
│   ├── DEAD_CODE_CANDIDATES.md
│   ├── FUTURE.md
│   ├── GATEWAY.md
│   ├── GATEWAY_STARTER_PLAN.md
│   ├── JPA_DSL_MIGRATION_DRAFT_GENERATOR_PLAN.md
│   ├── JPA_DSL_RISK_REGISTER.md
│   ├── MODULE_GUIDE.md
│   └── research/
│       └── GATEWAY_FEATURE_MATRIX.md
├── core/
│   ├── application/
│   ├── batch/
│   └── domain/
├── gateway/
│   ├── app/
│   ├── autoconfigure/
│   ├── core/
│   └── starter/
├── client/
│   └── storage-file/
├── storage/
│   ├── file-local/
│   ├── mybatis/
│   └── jpa/
└── support/
    ├── util/
    ├── logging/
    ├── web/
    └── jwt/
```

---

## 빌드 & 실행

### 전체 빌드

```bash
# 모든 모듈 빌드
./gradlew build

# 테스트 실행
./gradlew test
```

> 루트 경로에 한글 등 non-ASCII 문자가 포함된 macOS 환경에서는 Kotlin 컴파일러가 project dependency 산출물을 읽지 못하는 경우가 있어, Gradle build directory를 자동으로 `/tmp/backend-init-gradle-build-*` 아래 ASCII 경로로 사용합니다. 필요하면 `-PbackendInitBuildRoot=/path/to/build-root` 또는 `BACKEND_INIT_BUILD_ROOT`로 변경할 수 있습니다.

### 개별 서비스 실행

```bash
# 독립 실행형 API 게이트웨이 (포트 8080, standalone 모드)
export JWT_SECRET="$(openssl rand -base64 48)"
SPRING_PROFILES_ACTIVE=local ./gradlew :gateway:app:bootRun

# API 게이트웨이 + request visibility 활성화
export JWT_SECRET="$(openssl rand -base64 48)"
GATEWAY_REQUEST_VISIBILITY_ENABLED=true SPRING_PROFILES_ACTIVE=local ./gradlew :gateway:app:bootRun

# 비즈니스 서버 (포트 8081)
SPRING_PROFILES_ACTIVE=local,mybatis ./gradlew :core:application:bootRun

# 비즈니스 서버 + JPA persistence
SPRING_PROFILES_ACTIVE=local,jpa ./gradlew :core:application:bootRun

# 배치 서버 (포트 8082)
SPRING_PROFILES_ACTIVE=local ./gradlew :core:batch:bootRun
```

`application.yml`은 profile을 암묵 활성화하지 않습니다. 로컬 기본값(DB/Redis endpoint, gateway target URL)은 각 모듈의 `application-local.yml`에만 둡니다.

#### 게이트웨이 기동 모드 (`gateway.mode`)
- `embedded`: 동일 JVM 내 애플리케이션(`core:application`) 탑재 모드. 프록시 라우터와 `JwtAuthFilter`를 등록하지 않고 in-process `@RestController`가 직접 요청을 처리합니다.
- `standalone`: 단독 프로세스로 실행되는 게이트웨이 서비스 (`gateway:app` 기본값).
- `remote`: 원격 전용 게이트웨이. 현재 구현은 standalone과 동일한 프록시 빈 묶음을 사용하며 설정값으로 구분합니다.

#### 라우팅 및 인증 동작 확인
- 무인증 상태로 보호 API(예: `GET /api/sample/ping`)를 호출하면 `JwtAuthFilter`에 의해 즉시 `401 Unauthorized`가 반환됩니다.
- 라우팅 정상 동작 확인은 유효한 JWT Bearer 토큰을 포함하여 요청할 때 확인할 수 있습니다. 다운스트림 비즈니스 서버(8081)가 미기동 상태인 경우 연결 실패로 `502 Bad Gateway`(또는 타임아웃 시 `504 Gateway Timeout`)가 반환되어 프록시 중계 동작을 검증할 수 있습니다 (무인증 요청이 502를 반환하는 것이 아닙니다).

### 로컬 인프라 Smoke Test

```bash
# PostgreSQL(5432) + Redis(6380) 시작
docker compose up -d postgres redis

# 5432가 이미 사용 중이면 대체 포트로 시작
POSTGRES_PORT=55432 docker compose up -d postgres redis
DB_URL=jdbc:postgresql://localhost:55432/backend \
SPRING_PROFILES_ACTIVE=local,mybatis ./gradlew :core:application:test

# readiness 확인
docker compose ps

# 로컬 MyBatis profile로 application 테스트/기동
SPRING_PROFILES_ACTIVE=local,mybatis ./gradlew :core:application:test
SPRING_PROFILES_ACTIVE=local,mybatis ./gradlew :core:application:bootRun

# 로컬 JPA profile로 user/sample persistence adapter 검증
SPRING_PROFILES_ACTIVE=local,jpa ./gradlew :core:application:test

# generated JPA mapping을 실제 PostgreSQL에 대해 검증
DB_URL=jdbc:postgresql://localhost:55432/backend \
DB_USERNAME=backend \
DB_PASSWORD=backend \
./gradlew :storage:jpa:livePostgresTest
```

Runtime schema는 `core:application/src/main/resources/db/migration`의 Flyway migration이 소유합니다. `docker/postgres/init.sql`은 로컬 PostgreSQL bootstrap만 담당하고 table DDL은 중복 관리하지 않습니다.

JPA DSL의 해소된 리스크와 남은 non-blocking 리스크는 [JPA DSL Risk Register](docs/JPA_DSL_RISK_REGISTER.md)에 정리합니다.

### 품질 게이트

```bash
./gradlew build && ./gradlew -p build-logic test
```

CI는 의존성·시크릿 스캔만 돌린다(`.github/workflows/security.yml`); 빌드·테스트는 로컬 `./gradlew build && ./gradlew -p build-logic test`로 검증한다.

### 포트 정보

- **Gateway**: 8080 (`gateway:app`) — 클라이언트 요청 수신, 라우팅
- **Application**: 8081 — 비즈니스 로직 처리
- **Batch**: 8082 — 배치 작업 실행

---

## 문서 (Documentation)

설계 및 운영 관련 상세 문서는 `docs/` 디렉토리를 참조하세요:

- **[아키텍처](docs/ARCHITECTURE.md)** — 헥사고날 패턴, 의존성 방향, 모듈 간 상호작용 다이어그램
- **[모듈 가이드](docs/MODULE_GUIDE.md)** — 15개 모듈의 상세 가이드, 책임, 사용 방법
- **[코드 컨벤션](docs/CODE_CONVENTIONS.md)** — KDoc 문체 원칙, 도메인 금지 토큰, 패키지 규칙, 구조 가드 및 품질 검사 정책
- **[게이트웨이](docs/GATEWAY.md)** — 게이트웨이 아키텍처, 트랜잭션 ID(X-Request-Id) 관리, 라우팅 규칙
- **[향후 설계](docs/FUTURE.md)** — 다중 인스턴스 지원, Config 서버, 요청 확인 화면 등 향후 계획
- **[JPA DSL 마이그레이션 초안 생성기 계획](docs/JPA_DSL_MIGRATION_DRAFT_GENERATOR_PLAN.md)** — JPA DSL 및 Flyway 마이그레이션 초안 자동 생성기 설계 및 검증 계획
- **[JPA DSL 리스크 관리 대장](docs/JPA_DSL_RISK_REGISTER.md)** — 도메인 순수성을 유지하며 JPA DSL 매핑을 사용하는 아키텍처의 리스크 관리 대장
- **[JPA 저장소 가이드](storage/jpa/README.md)** — `storage:jpa` 모듈의 경계, DSL 코드 생성, adapter 구현 및 검증 가이드
- **[게이트웨이 스타터 로드맵](docs/GATEWAY_STARTER_PLAN.md)** — Spring Boot 기동 탑재형(starter) 게이트웨이 라이브러리화 3모드 로드맵
- **[미사용 코드 인벤토리](docs/DEAD_CODE_CANDIDATES.md)** — 삭제 후보 미사용 코드 및 중복 구현 목록과 정리 정책
- **[상용 게이트웨이 기능 매트릭스](docs/research/GATEWAY_FEATURE_MATRIX.md)** — 상용 API 게이트웨이 11종의 기능 비교 조사 매트릭스

---

## 프로젝트 관리

- **CLAUDE.md**: 개발 지침 및 규칙 (프로젝트 로컬)
- **QE_CONVENTIONS.md**: QE 프레임워크 컨벤션 및 표준
- **docs/CODE_CONVENTIONS.md**: 코드 스타일, KDoc 작성 규칙, 구조 가드 및 품질 정책

---

## 현재 상태

**검증된 범위**
- 15개 Gradle 멀티모듈 설정 및 모듈 간 의존성 연결
- `gateway:core`: JWT 인증 필터, 토큰 발급 엔드포인트(`POST /api/auth/token`)에 대한 IP 기준 rate limit(10회/60초, Redis, fail-open), 프록시(`cc.midolog.gateway.proxy.*`) 및 라우팅, optional 다중 application 라운드로빈, 기본 비활성화 request visibility (요청 ID 필터는 `support:web` 소유 및 호스트 스캔 연계)
- `gateway:autoconfigure`: `gateway.mode` 프로퍼티 기반 조건부 자동 설정 및 Boot 4 imports 등록
- `gateway:starter`: `gateway:core` 및 `gateway:autoconfigure`를 통합한 탑재용 스타터 라이브러리
- `gateway:app`: 독립 실행형 Spring Boot 4 게이트웨이 애플리케이션 (8080 포트)
- `core:application`: sample API, user 생성/조회 API, file API 4종(`POST /api/files`, `GET /api/files/{id}`, `GET /api/files/{id}/content` 스트리밍, `DELETE /api/files/{id}`), 전용 응답 DTO(`*Response`), Redis 캐시 인프라(`cc.midolog.infra.cache.RedisSampleCacheAdapter`), 인증 토큰 발급, SecurityConfig, 예외 응답 처리
- `core:domain`: Spring/JPA/MyBatis annotation 없는 Plain Kotlin domain model/port (`DomainPurityTest` 가드)
- `storage:file-local`: Local FS 기반 파일 저장 어댑터(`cc.midolog.storage.file.local.*`), Spring Boot 4 `AutoConfiguration.imports` 기반 자동 설정(`FileStorageAutoConfiguration`), Fail-fast 설정 검증(`storage.file.provider=local`, `storage.file.local.root-dir`, `storage.file.max-size-bytes`), 레거시 빈(`localFileStorageAdapter`)과의 공존 가드(`FileStorageIntegrationTest` 통과)
- `storage:mybatis`: `mybatis` profile adapter(`cc.midolog.storage.mybatis.*`), `MyBatis*RepositoryAdapter` (Sample/User 및 `MyBatisFileMetaRepositoryAdapter`), mapper upsert, cutoff+limit 계약(`findExpiredPending`), V2 Flyway `file_meta`, sample/user profile wiring 테스트
- `storage:jpa`: `jpa` profile adapter, `Jpa*RepositoryAdapter` (Sample/User 및 `JpaFileMetaRepositoryAdapter`), DSL 생성 JPA entity/repository/mapper, JPA 픽스처(`cc.midolog.jpadsl.fixture`), H2 기반 sample/user adapter 테스트, live PostgreSQL 분리 검증(`livePostgresTest`)
- `client:storage-file`, `support:*`: 도메인 포트/어댑터(레거시 `FileStoragePort` 호환 유지), `support:web`(`RequestIdFilter`, `HttpLoggingFilter`), `support:jwt`(`JwtCodec`) 및 공통 유틸리티/웹/로깅 골격
- `./gradlew build && ./gradlew -p build-logic test` 통과

**backend-init-evolution 완료 범위**
- A — 운영형 템플릿화: Gateway proxy correctness, profile local 분리, docs 정합성
- B — 도메인 예제 확장: sample smoke 유지 + user 실전 예제, MyBatis/JPA contract/profile 검증
- C — 플랫폼화: Gateway 내부 라운드로빈, request visibility 최소 기능, Config Server/OpenTelemetry 보류 결정

**후속 후보**
- Docker PostgreSQL/Redis live smoke test와 migration 전략 보강
- security/dependency scan CI workflow 운영 정책 보강
- request visibility Redis-backed store 또는 별도 UI
- OpenTelemetry exporter/collector optional module

이 저장소는 프로덕션 서비스가 아니라, 백엔드 초기화 템플릿과 아키텍처 실험을 검증 가능한 형태로 정리하는 프로젝트입니다.

---

## 라이선스

[프로젝트 라이선스 정보 추가 예정]
