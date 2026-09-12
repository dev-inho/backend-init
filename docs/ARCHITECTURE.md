# 아키텍처 (Architecture)

Spring Boot 4 + Kotlin 기반의 헥사고날(Ports & Adapters) 멀티모듈 백엔드 아키텍처입니다. 도메인 로직과 인프라를 명확히 분리하여 테스트 용이성과 확장성을 극대화합니다.

---

## 1. 개요

### 헥사고날 아키텍처 채택 이유

**도메인-인프라 분리**
- 도메인 계층이 외부 프레임워크(Spring, ORM, HTTP)에 의존하지 않음
- 도메인 로직은 순수 Kotlin으로 작성되어 변경에 강함
- 인프라 변경(DB, 메시지 큐 등)이 도메인에 영향을 주지 않음

**테스트 용이성**
- 도메인 모델을 단위 테스트할 때 Spring Context 로드 불필요
- Mock/Fake Adapter로 외부 의존성을 쉽게 치환
- 비즈니스 로직 테스트 실행 속도 향상

**확장성과 유연성**
- 동일한 도메인 포트에 여러 구현(Adapter)을 플러그인 가능
- 새로운 인프라 추가 시 기존 코드 수정 최소화
- 마이크로서비스 분할 시 계층 경계가 명확하여 분리 용이

### 멀티모듈 채택 이유

- **모듈별 독립 빌드**: 변경 영역을 최소화하여 CI/CD 성능 개선
- **의존성 명시화**: Gradle 의존성 선언으로 계층 규칙 강제
- **팀 협업**: 모듈별 담당자 지정 시 변경 충돌 감소
- **재사용성**: 공통 모듈(support)을 여러 서버에서 공유

---

## 2. 레이어 설명

### 2.1 Domain (cc.midolog.core.domain)

**책임**
- 도메인 모델(Entity, Value Object) 정의
- 비즈니스 규칙과 검증 로직
- Port 인터페이스 정의(외부 의존성을 역전시키는 계약)

**특징**
- 순수 Kotlin, 프레임워크 비의존
- Spring 어노테이션 불가
- JPA, Mybatis, 외부 라이브러리 사용 불가
- Port는 domain 내 interface로만 정의

**도메인 모델 및 포트 계약**
- **`StoredFile` vs `FileMeta` 구분**:
  - `StoredFile`: 실제 스토리지 I/O(저장 완료) 결과 및 확정된 파일 속성(`id`, `ownerId`, `storageKey`, `sizeBytes`, `contentType`, `checksum`, `status`)을 담는 값객체(Value Object).
  - `FileMeta`: 파일 생명주기 전체를 추적하는 DB 영속 메타데이터 모델. 업로드 대기(`PENDING`) 상태에서는 크기, 컨텐츠 타입, 체크섬 등이 미확정(nullable)이며 생성/수정 시각(`createdAt`, `updatedAt`)을 포함한다.
- **포트 계약**:
  - `FileStoragePort` (`cc.midolog.file.port.storage`): 청크 기반 비동기 스트리밍 저장(`store`), 읽기 청크 리더 반환(`load`), 멱등 삭제(`delete`), 존재 확인(`exists`)을 정의.
  - `FileMetaRepositoryPort` (`cc.midolog.file.port.repository`): 식별자 조회(`findById`), 저장/갱신(`save`), 상태 변경(`updateStatus`), 만료 대기 건 일괄 조회를 위한 **cutoff+limit 계약**(`findExpiredPending(cutoff, limit)`)을 정의.
- **레거시 포트 호환**:
  - `cc.midolog.sample.port.file.FileStoragePort`: `@Deprecated` 처리되어 신규 포트(`cc.midolog.file.port.storage.FileStoragePort`)로 대체되었으나, 기존 어댑터와의 하위 호환성을 위해 심볼을 유지한다.

**실제 구조**
```
core/domain/
├── file/
│   ├── model/
│   │   ├── FileMeta.kt
│   │   ├── FileStatus.kt
│   │   ├── PresignedRequest.kt
│   │   └── StoredFile.kt
│   └── port/
│       ├── repository/
│       │   └── FileMetaRepositoryPort.kt
│       └── storage/
│           ├── ChunkReader.kt
│           ├── ChunkWriter.kt
│           ├── FilePresignPort.kt
│           └── FileStoragePort.kt
├── jpadsl/fixture/
│   ├── RelationChild.kt
│   ├── RelationParent.kt
│   └── ScalarSample.kt
├── sample/
│   ├── model/
│   │   └── Sample.kt
│   └── port/
│       ├── cache/
│       │   └── SampleCachePort.kt
│       ├── file/
│       │   └── FileStoragePort.kt
│       └── repository/
│           └── SampleRepositoryPort.kt
└── user/
    ├── model/
    │   └── User.kt
    └── port/repository/
        └── UserRepositoryPort.kt
```

### 2.2 Application (cc.midolog.core.application)

**책임**
- 유스케이스(Application Service) 구현
- 비즈니스 흐름 조율
- Adapter(구현체) 주입 및 의존성 해결
- 트랜잭션 관리

**제공 API**
- `POST /api/auth/token`: 인증 토큰 발급
- `GET /api/sample/ping`, `GET /api/sample/{id}`, `POST /api/sample`: Sample API
- `POST /api/user`, `GET /api/user/{id}`: User API
- `POST /api/files`: 멀티파트 파일 업로드 (`HttpStatus.CREATED` 201). 크기 초과 시 413 Payload Too Large, 허용되지 않는 미디어 타입 시 415 Unsupported Media Type.
- `GET /api/files/{id}`: 파일 메타데이터 조회 (`ApiResponse<FileResponse>`)
- `GET /api/files/{id}/content`: 파일 스트리밍 다운로드 (`ResponseEntity<Flux<DataBuffer>>`, 청크 버퍼 기반 논블로킹 스트리밍)
- `DELETE /api/files/{id}`: 파일 및 메타데이터 삭제 (`ApiResponse<Unit>`)

**특징**
- Domain 및 Support(util, logging, web, jwt)에 의존
- Adapter(client, storage)는 **runtimeOnly**로 의존 (`client:storage-file`, `storage:mybatis`, `storage:jpa`, `storage:file-local` — 컴파일 의존성 제거)
- Spring Application Context 진입점
- WebFlux + Coroutine 사용

**실제 구조**
```
core/application/
├── ApplicationServer.kt
├── business/service/
│   ├── FileService.kt
│   ├── SampleService.kt
│   └── UserService.kt
├── common/security/
│   ├── JwtProvider.kt
│   └── SecurityConfig.kt
├── infra/cache/
│   └── RedisSampleCacheAdapter.kt
└── web/
    ├── auth/
    │   └── AuthController.kt
    ├── file/
    │   ├── FileController.kt
    │   ├── WebFluxChunkBridge.kt
    │   └── dto/
    │       └── FileResponse.kt
    ├── sample/
    │   ├── SampleController.kt
    │   ├── SampleStreamController.kt
    │   └── dto/
    │       ├── CreateSampleRequest.kt
    │       └── SampleResponse.kt
    └── user/
        ├── UserController.kt
        └── dto/
            ├── CreateUserRequest.kt
            └── UserResponse.kt
```

### 2.3 Gateway (cc.midolog.gateway.*)

**책임**
- 외부 HTTP 요청 수신 및 단일 진입점 역할
- 요청 분기(라우팅) 및 프록시 중계
- 요청 ID 전파 및 Rate Limiting
- 요청 관측성/가시성(Visibility) 제공
- Spring Boot 자동 설정 및 모드(`gateway.mode`) 지원

**모듈 구성 및 특징**
- **gateway:core**: 핵심 필터(`AuthTokenRateLimitFilter`, `JwtAuthFilter`), 라우팅(`RouteConfig`), 프록시(`ProxyHandler`), 라우트 선택(`GatewayRouteSelector`), 요청 가시성(`RequestVisibility*`)
- **gateway:autoconfigure**: `gateway.mode` 프로퍼티 검증 및 모드별 빈 자동 등록(`GatewayAutoConfiguration`), Spring Boot 4 `AutoConfiguration.imports`
- **gateway:starter**: `gateway:core`와 `gateway:autoconfigure` 의존성을 번들링하여 제공하는 스타터 라이브러리
- **gateway:app**: 독립 실행형 게이트웨이 부트 애플리케이션 (`GatewayApplication`, 포트 8080)
- **WebFlux 자체 구현**: 별도 외부 프레임워크(Spring Cloud Gateway 등) 의존성 없는 순수 WebFlux `router {}` 기반 라우팅
- **모드 지원 (`gateway.mode=embedded|standalone|remote`)**: 필수 설정값(기본값 없음, fail-fast). `embedded` 모드에서는 프록시 라우터/`JwtAuthFilter`를 등록하지 않고 동일 JVM의 `@RestController`가 직접 처리합니다.
- **X-Request-Id 전파**: `support:web`의 `RequestIdFilter`(@Order(0)) 및 `HttpLoggingFilter`(@Order(-2)) 연계
- **IP 기준 Rate Limiting**: `POST /api/auth/token` 대상 클라이언트 IP 기준 10회/60초 Redis Lua fail-open 방어

**실제 구조**
```
gateway/
├── core/
│   ├── config/
│   │   ├── GatewayClockConfig.kt
│   │   ├── GatewayRouteProperties.kt
│   │   ├── RouteConfig.kt
│   │   └── WebClientConfig.kt
│   ├── filter/
│   │   ├── AuthTokenRateLimitFilter.kt
│   │   └── JwtAuthFilter.kt
│   ├── proxy/
│   │   ├── HeaderSanitizer.kt
│   │   └── ProxyHandler.kt
│   ├── ratelimit/
│   │   ├── RateLimiter.kt
│   │   └── RedisRateLimiter.kt
│   ├── route/
│   │   └── GatewayRouteSelector.kt
│   └── visibility/
│       ├── RequestEventStore.kt
│       ├── RequestVisibilityController.kt
│       ├── RequestVisibilityEvent.kt
│       ├── RequestVisibilityFilter.kt
│       └── RequestVisibilityProperties.kt
├── autoconfigure/
│   ├── src/main/kotlin/cc/midolog/gateway/autoconfigure/
│   │   ├── GatewayAutoConfiguration.kt
│   │   └── GatewayModeProperties.kt
│   └── src/main/resources/META-INF/spring/
│       └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
├── starter/
│   └── build.gradle
└── app/
    ├── src/main/kotlin/cc/midolog/
    │   └── GatewayApplication.kt
    └── src/main/resources/
        ├── application.yml
        └── application-local.yml
```

### 2.4 Batch (cc.midolog.core.batch)

**책임**
- 정기 배치 작업 및 데이터 처리
- 스케줄 작업(Spring Batch, Spring Scheduler)
- 대용량 데이터 일괄 처리

**특징**
- Spring Batch 기반 Job/Step 실행
- `support:logging`만 의존 (Domain, Storage에 직접 의존하지 않음)
- Application 및 Gateway와 독립적으로 구동 가능한 단독 부트 애플리케이션

**실제 구조**
```
core/batch/
├── BatchApplication.kt
└── batch/job/
    └── SampleJobConfig.kt
```

### 2.5 Client (cc.midolog.client.*)

**책임**
- 외부 시스템 API 호출 및 스토리지 연동
- Domain Port 구현(Adapter)

**모듈**
- **client:storage-file** — 구버전 파일 스토리지 클라이언트 (`@Deprecated`). 구 `cc.midolog.sample.port.file.FileStoragePort` 포트 구현체.

**특징**
- 신규 파일 저장 기능은 `storage:file-local`로 대체되었으나, 기존 호출부와의 하위 호환성을 위해 유지
- `@Component`로 등록되어 기본 빈 이름 `localFileStorageAdapter`를 가지며, 신규 자동 설정 빈 `fileLocalStorageAdapter`와 충돌 없이 공존 (`FileStorageIntegrationTest` 가드)
- Application에서 runtimeOnly로 의존

**실제 구조**
```
client/storage-file/
└── cc/midolog/client/storage/
    └── LocalFileStorageAdapter.kt
```

### 2.6 Storage (cc.midolog.storage.*)

**책임**
- 데이터 영속성(Database Access) 및 파일 시스템 영속 저장
- Domain Port 구현(Adapter)

**모듈**
- **storage:mybatis** — MyBatis 기반 데이터 접근
- **storage:jpa** — Spring Data JPA 기반 데이터 접근
- **storage:file-local** — 로컬 파일 시스템 파일 저장소 어댑터 및 Spring Boot 4 자동 설정

**특징**
- Domain model을 저장소 전용 schema/entity/mapper에 매핑
- Domain Port(Repository, FileStoragePort) 구현
- MyBatis SQL query 또는 JPA repository 관리
- Persistence 구현은 profile로 하나만 선택 (`mybatis` vs `jpa`)
- **`storage:file-local` 자동 설정 및 Fail-Fast**:
  - `cc.midolog.file.port.storage.FileStoragePort` 구현체(`cc.midolog.storage.file.local.LocalFileStorageAdapter`) 제공.
  - Spring Boot 4 규격 `AutoConfiguration.imports`를 통해 `cc.midolog.storage.file.autoconfigure.FileStorageAutoConfiguration` 등록.
  - 충돌 방지를 위해 `@Bean("fileLocalStorageAdapter")` 명시적 이름 사용.
  - 기동 시 필수 프로퍼티 유효성을 검증하여 잘못된 설정 시 즉각 fail-fast (`storage.file.provider=local` 필수, `storage.file.local.root-dir` 절대 경로 필수, `storage.file.max-size-bytes` > 0, `storage.file.allowed-content-types`).
- **FileMeta 영속성 및 cutoff+limit 계약**:
  - `storage:mybatis`와 `storage:jpa`는 V2 Flyway 스키마(`file_meta` 테이블)를 바탕으로 `FileMetaRepositoryPort`를 구현.
  - 만료 대기 건 일괄 정리를 위한 `findExpiredPending(cutoff, limit)` cutoff+limit 계약 준수.
- **livePostgresTest 분리 격리**:
  - `storage:jpa`의 단위/통합 테스트는 H2 In-Memory DB로 수행되며, 실제 PostgreSQL 검증 태스크(`livePostgresTest`)는 기본 `./gradlew build` 및 `./gradlew test`에서 제외되어 별도로 실행.

**실제 구조**
```
storage/mybatis/
├── config/
│   └── MyBatisStorageConfig.kt
├── file/
│   ├── FileMetaMapper.kt
│   └── MyBatisFileMetaRepositoryAdapter.kt
├── sample/
│   ├── MyBatisSampleRepositoryAdapter.kt
│   └── SampleMapper.kt
└── user/
    ├── MyBatisUserRepositoryAdapter.kt
    └── UserMapper.kt

storage/jpa/
├── config/
│   └── JpaStorageConfig.kt
├── file/
│   ├── FileMetaJpaRepository.kt
│   └── JpaFileMetaRepositoryAdapter.kt
├── sample/
│   ├── JpaSampleRepositoryAdapter.kt
│   └── ScalarSampleCodeJpaConverter.kt
└── user/
    └── JpaUserRepositoryAdapter.kt

storage/file-local/
├── cc/midolog/storage/file/
│   ├── autoconfigure/
│   │   ├── FileStorageAutoConfiguration.kt
│   │   ├── FileStorageProperties.kt
│   │   └── FileStoragePropertiesValidator.kt
│   └── local/
│       └── LocalFileStorageAdapter.kt
└── resources/META-INF/spring/
    └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
```
*(참고: JPA Entity, Repository, Mapper는 `build-logic`의 `JpaDslPlugin`에 의해 빌드 시 `build/generated`에 자동 생성됩니다)*

**Domain/entity 분리 원칙**
- `core:domain`에는 Plain Kotlin model과 port만 둔다.
- JPA `@Entity`, `@Table`, Spring Data repository는 `storage:jpa` 내부에만 둔다.
- MyBatis mapper interface와 XML mapper는 `storage:mybatis` 내부에만 둔다.
- `core:application`은 `SampleRepositoryPort`, `UserRepositoryPort`, `FileMetaRepositoryPort`, `FileStoragePort` 같은 domain port만 사용하고 구체 storage 구현을 main source에서 import하지 않는다.
- 운영 실행에서는 `mybatis`와 `jpa` profile을 동시에 켜지 않는다.

### 2.7 Support (cc.midolog.support.*)

**책임**
- 횡단 관심사(Cross-Cutting Concerns)
- 여러 모듈에서 공유하는 유틸리티, 로깅, 웹 공통 처리 및 인증 토큰 코덱

**모듈**
- **support:util** — 공통 유틸리티 (String, Date/Time, Collection 확장, 널 안전성, 마스킹, 페이징, 재시도, 유효성 검증 등)
- **support:logging** — 통일된 로깅 설정 (Logback, MDC 바인딩, 개인정보/민감정보 마스킹, Reactor 연동)
- **support:web** — 웹 공통 계층 (RequestIdFilter, HttpLoggingFilter, ErrorCode, GlobalExceptionHandler, ApiResponse)
- **support:jwt** — JWT 공통 유틸리티 (JwtCodec 기반 발급, 파싱, 서명 검증)

**특징**
- 도메인 비즈니스 로직 불포함
- 상위 모듈에서 의존 가능 (단, support 내부에서는 util ← logging, jwt / logging, util ← web 의존)
- 상위 실행/비즈니스 모듈로의 역의존 금지

**실제 구조**
```
support/util/
├── IdGenerator.kt
├── JwtSecretValidator.kt
├── TimeProvider.kt
├── UtilDefaults.kt
├── Validation.kt
├── ext/
│   ├── CollectionExtensions.kt
│   ├── NullSafety.kt
│   └── StringExtensions.kt
├── mask/
│   └── Masking.kt
├── paging/
│   └── Page.kt
├── result/
│   └── Outcome.kt
└── retry/
    └── Retry.kt

support/logging/
├── LoggingMdc.kt
├── MaskingMessageConverter.kt
├── MaskingSupport.kt
└── ReactorMdc.kt

support/web/
├── exception/
│   ├── ApiException.kt
│   └── ErrorCode.kt
├── filter/
│   ├── HttpLoggingFilter.kt
│   └── RequestIdFilter.kt
├── handler/
│   └── GlobalExceptionHandler.kt
└── response/
    └── ApiResponse.kt

support/jwt/
└── JwtCodec.kt
```

---

## 3. 의존성 방향 규칙

### 3.1 의존성 흐름

```
상위(실행계층) → 하위(도메인/인프라) 단방향
```

| 모듈 / 계층 | 의존 대상 (프로젝트) | 설명 |
|------------|-------------------|------|
| `gateway:app` | `gateway:starter`, `support:logging` (test: `support:web`) | 독립 실행형 API 게이트웨이 서비스 부트스트랩 |
| `gateway:starter` | `gateway:core` (api), `gateway:autoconfigure` (api) | 게이트웨이 의존성 묶음 스타터 라이브러리 |
| `gateway:autoconfigure` | `gateway:core` | `gateway.mode` 기반 자동 설정 및 조건부 빈 등록 |
| `gateway:core` | `support:logging`, `support:util`, `support:web`, `support:jwt` | 요청 수신, 라우팅, Rate Limit, 인증, 관측성 핵심 로직 |
| `core:application` | `core:domain`, `support:util`, `support:logging`, `support:web`, `support:jwt`, (runtimeOnly) `client:storage-file`, `storage:mybatis`, `storage:jpa`, `storage:file-local` | 비즈니스 유스케이스 조율, REST API 제공, 어댑터 런타임 주입 |
| `core:batch` | `support:logging` | 정기 배치 작업 실행 (Spring Batch 기반, domain/storage 직접 의존 없음) |
| `client:storage-file` | `core:domain` | Domain FileStoragePort 구현 (로컬 파일 스토리지, 레거시 `@Deprecated`) |
| `storage:mybatis` | `core:domain`, `support:util` | Domain RepositoryPort 구현 (MyBatis SQL 매핑) |
| `storage:jpa` | `core:domain`, `support:util` | Domain RepositoryPort 구현 (Spring Data JPA 및 JPA DSL 생성 코드) |
| `storage:file-local` | `core:domain`, `support:util` | Domain FileStoragePort 구현 및 Spring Boot 4 자동 설정 (로컬 파일 스토리지) |
| `core:domain` | (없음) | 순수 Kotlin 도메인 모델 및 포트 인터페이스 (프로젝트 의존 0) |
| `support:util` | (없음) | 프로젝트 공통 유틸리티 (문자열, 컬렉션, 마스킹 등) |
| `support:logging` | `support:util` | 통합 로깅 및 MDC 유틸리티 |
| `support:web` | `support:logging`, `support:util` | 웹 공통 필터, 응답 래퍼, 전역 예외 처리 |
| `support:jwt` | `support:util` (api) | JWT 인코딩/디코딩 유틸리티 |
| `build-logic` | (Gradle composite build) | JPA DSL 코드 생성 및 Flyway 마이그레이션 검증/생성 플러그인 (`includeBuild`) |

### 3.2 runtimeOnly 의존성

Application, Batch는 Adapter(Client, Storage)를 **runtimeOnly**로 의존합니다.

**의미**
- Compile 시점에는 의존하지 않음 (Adapter 인터페이스 모름)
- Runtime 시점에만 Spring Context를 통해 Adapter 빈 주입

**장점**
- Application이 구현 세부(파일 저장소, DB 등)를 알지 못함
- 동일한 Domain Port에 여러 구현 가능
- 테스트 시 Mock Adapter로 쉽게 교체

**예시**
```kotlin
// Application은 Domain Port만 사용
@Service
class CreateUserUseCase(
    private val userRepository: UserRepository  // Domain Port (interface)
) {
    suspend fun execute(dto: CreateUserDto): User {
        return userRepository.save(User(...))
    }
}

// Runtime: MyBatis, JPA, Mock 구현 중 하나가 주입됨
// @Bean fun userRepository(): UserRepository = MybatisUserRepositoryAdapter(...)
```

### 3.3 반드시 지켜야 할 규칙

1. **Gateway ↔ Application 직접 의존 금지**: Gateway에서 Application Service를 직접 호출하지 않음. REST Controller를 Application 내에 두거나 별도 계층으로 분리.
2. **Batch ↔ Application 직접 호출 금지**: 동일 로직이 필요하면 Domain Service로 추출.
3. **Client/Storage 간 직접 의존 금지**: 모두 Domain Port를 통해 통신.
4. **Support 역의존 금지**: Support 모듈은 어떤 상위 모듈도 의존하지 않음.
5. **Domain 내 프레임워크 사용 금지**: Spring, JPA, Mybatis 어노테이션 불가.

---

## 4. 의존성 방향 다이어그램

```mermaid
graph TD
    GW_APP["Gateway App<br/>(gateway:app)"]
    GW_STARTER["Gateway Starter<br/>(gateway:starter)"]
    GW_AUTO["Gateway Autoconfigure<br/>(gateway:autoconfigure)"]
    GW_CORE["Gateway Core<br/>(gateway:core)"]
    APP["Application<br/>(core:application)"]
    BAT["Batch<br/>(core:batch)"]
    DOM["Domain<br/>(core:domain)"]
    CSF["Client:Storage<br/>(client:storage-file)"]
    MYB["Storage:Mybatis<br/>(storage:mybatis)"]
    JPA["Storage:JPA<br/>(storage:jpa)"]
    SFL["Storage:File-Local<br/>(storage:file-local)"]
    LOG["Support:Logging<br/>(support:logging)"]
    UTL["Support:Util<br/>(support:util)"]
    WEB["Support:Web<br/>(support:web)"]
    JWT["Support:Jwt<br/>(support:jwt)"]
    BL["Build-Logic<br/>(build-logic, includeBuild)"]

    GW_APP -->|depends| GW_STARTER
    GW_APP -->|depends| LOG

    GW_STARTER -->|api| GW_CORE
    GW_STARTER -->|api| GW_AUTO

    GW_AUTO -->|depends| GW_CORE

    GW_CORE -->|depends| LOG
    GW_CORE -->|depends| UTL
    GW_CORE -->|depends| WEB
    GW_CORE -->|depends| JWT

    APP -->|depends| DOM
    APP -->|depends| UTL
    APP -->|depends| LOG
    APP -->|depends| WEB
    APP -->|depends| JWT
    APP -.->|runtimeOnly| CSF
    APP -.->|runtimeOnly| MYB
    APP -.->|runtimeOnly| JPA
    APP -.->|runtimeOnly| SFL

    BAT -->|depends| LOG

    CSF -->|depends| DOM

    MYB -->|depends| DOM
    MYB -->|depends| UTL

    JPA -->|depends| DOM
    JPA -->|depends| UTL

    SFL -->|depends| DOM
    SFL -->|depends| UTL

    WEB -->|depends| LOG
    WEB -->|depends| UTL

    JWT -->|depends| UTL

    LOG -->|depends| UTL

    BL -.->|plugin| JPA

    style DOM fill:#e1f5ff
    style UTL fill:#f3e5f5
    style LOG fill:#f3e5f5
    style WEB fill:#f3e5f5
    style JWT fill:#f3e5f5
    style GW_APP fill:#fff3e0
    style GW_STARTER fill:#fff3e0
    style GW_AUTO fill:#fff3e0
    style GW_CORE fill:#fff3e0
    style APP fill:#fff3e0
    style BAT fill:#fff3e0
    style CSF fill:#f1f8e9
    style MYB fill:#f1f8e9
    style JPA fill:#f1f8e9
    style SFL fill:#f1f8e9
    style BL fill:#eceff1
```

**범례**
- 파란색(Domain): 도메인 계층 — 프레임워크 비의존, 순수 로직
- 보라색(Support): 공통 모듈 — 여러 계층에서 공유, 역의존 금지
- 주황색(실행/게이트웨이 계층): Gateway(app, starter, autoconfigure, core), Application, Batch — 요청/작업 실행 진입점 및 게이트웨이 라이브러리
- 초록색(Adapter): Client, Storage — Domain Port 구현
- 회색(Build-Logic): Gradle Composite Build — JPA DSL 코드 생성 및 스키마 검증 플러그인

---

## 5. 요청 흐름 개요

### 5.1 일반적인 비즈니스 요청 흐름

```
Client HTTP Request
       ↓
    Gateway (gateway:app)
       ├─ Transaction ID 생성
       ├─ 요청 추적(MDC)
       └─ Application으로 라우팅
            ↓
    Application (core:application)
       ├─ REST Controller 처리
       ├─ Use Case(Application Service) 호출
       │  (Domain Port 인터페이스 사용)
       └─ Response 반환
            ↓
    Domain (core:domain)
       ├─ Entity 생성/검증
       ├─ Business Rule 실행
       └─ Port 호출 (추상적)
            ↓
    Adapter (Client/Storage)
       ├─ Domain Port 구현
       ├─ 외부 시스템 호출 (DB, API 등)
       └─ 결과 반환
            ↓
    Logging (support:logging)
       └─ 트랜잭션 ID와 함께 로그 기록
```

### 5.2 분산 환경에서의 흐름

```
Client Request
       ↓
    Gateway Instance (로드 밸런서 뒤)
       ├─ Transaction ID 생성
       ├─ 라우팅 규칙 적용
       └─ Business Server N으로 분기
            ↓
    Application Instance 1 (무상태)
       ├─ Domain Port 호출
       └─ Response 반환
    
    Application Instance 2 (무상태)
       ├─ Domain Port 호출
       └─ Response 반환
    
    Application Instance N (무상태)
       ├─ Domain Port 호출
       └─ Response 반환
            ↓
    Storage (공유 DB)
       └─ 데이터 영속성
```

### 5.3 배치 작업 흐름

```
Spring Batch / Spring Scheduler
       ↓
    Batch (core:batch)
       ├─ Job 실행
       ├─ Step/Tasklet 실행
       └─ 데이터 처리
            ↓
    Logging (support:logging)
       └─ 배치 완료 로그 기록
```

---

## 6. 설계 원칙

### 6.1 도메인 주도 설계 (Domain-Driven Design)

- 도메인 모델이 비즈니스 규칙의 중심
- Port를 통한 의존성 역전(Dependency Inversion)
- 인프라 변경에 따른 도메인 영향 최소화

### 6.2 무상태 설계 (Stateless)

- Application 인스턴스가 상태를 보유하지 않음
- 수평 확장(Horizontal Scaling) 가능
- 서버 인스턴스 추가/제거 가능

### 6.3 높은 응집도, 낮은 결합도 (High Cohesion, Low Coupling)

- 각 모듈이 명확한 책임 보유
- 모듈 간 의존성 최소화
- 계층 경계를 넘는 직접 접근 금지

### 6.4 테스트 용이성 (Testability)

- Domain을 Spring 없이 단위 테스트 가능
- Mock/Fake Adapter로 외부 의존성 제거
- Integration Test는 Application 계층에서 수행

---

## 7. 모듈 간 통신

### 7.1 상위 계층 → 하위 계층

**인터페이스 기반 호출**
```kotlin
// Application에서 Domain Port 호출
interface UserRepository {  // Domain Port
    suspend fun findById(id: UserId): User?
    suspend fun save(user: User): User
}

// Mybatis Adapter가 구현
@Component
class MybatisUserRepositoryAdapter : UserRepository {
    override suspend fun findById(id: UserId): User? = ...
    override suspend fun save(user: User): User = ...
}
```

### 7.2 하위 계층 → 상위 계층 (포트)

**도메인 이벤트를 통한 느슨한 결합**
```kotlin
// Domain에서 이벤트 발행
class UserCreatedEvent(val userId: UserId, val email: Email)

// Application에서 이벤트 구독
@EventListener
fun onUserCreated(event: UserCreatedEvent) {
    // 메일 발송, 통계 업데이트 등
}
```

---

## 8. 관련 문서

- [MODULE_GUIDE.md](./MODULE_GUIDE.md) — 각 모듈의 상세 가이드 및 사용 방법
- [GATEWAY.md](./GATEWAY.md) — Gateway 라우팅, Transaction ID, 대시보드 설정
- [FUTURE.md](./FUTURE.md) — 향후 계획 (Config Server, 마이크로서비스 분할 등)

---

## 9. 빌드 및 의존성 순서

```
1단계: 빌드 로직 및 기반 모듈
├─ build-logic (includeBuild)
└─ support:util (의존성 없음)

2단계: 도메인 및 기반 지원 모듈
├─ core:domain (의존성 없음)
├─ support:logging (util 의존)
└─ support:jwt (util 의존)

3단계: 어댑터 및 웹 공통 모듈
├─ support:web (logging, util 의존)
├─ client:storage-file (domain 의존)
├─ storage:mybatis (domain, util 의존)
└─ storage:jpa (domain, util 의존)

4단계: 게이트웨이 코어 모듈
└─ gateway:core (logging, util, web, jwt 의존)

5단계: 게이트웨이 자동 설정 빌드
└─ gateway:autoconfigure (gateway:core 의존)

6단계: 게이트웨이 스타터 라이브러리 빌드
└─ gateway:starter (gateway:core, gateway:autoconfigure 의존)

7단계: 실행 계층 빌드
├─ core:batch (logging 의존)
├─ gateway:app (gateway:starter, logging 의존)
└─ core:application (domain, logging, util, web, jwt 의존 및 client, storage runtimeOnly)
```

Gradle에서 자동으로 의존성 순서대로 빌드합니다.

---

마지막 업데이트: 2026-09-12
