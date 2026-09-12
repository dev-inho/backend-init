# 모듈 가이드 (Module Guide)

Spring Boot 4 + Kotlin 기반 헥사고날 멀티모듈 아키텍처의 15개 모듈 및 build-logic 빌드 도구별 책임, 패키지 구조, 의존성 정의 가이드.

---

## 목차
1. [모듈 개요](#모듈-개요)
2. [모듈별 상세 가이드](#모듈별-상세-가이드)
   - [1. core:application](#1-coreapplication)
   - [2. core:batch](#2-corebatch)
   - [3. gateway:core](#3-gatewaycore)
   - [4. gateway:autoconfigure](#4-gatewayautoconfigure)
   - [5. gateway:starter](#5-gatewaystarter)
   - [6. gateway:app](#6-gatewayapp)
   - [7. core:domain](#7-coredomain)
   - [8. client:storage-file](#8-clientstorage-file)
   - [9. storage:mybatis](#9-storagemybatis)
   - [10. storage:jpa](#10-storagejpa)
   - [11. storage:file-local](#11-storagefile-local)
   - [12. support:util](#12-supportutil)
   - [13. support:logging](#13-supportlogging)
   - [14. support:web](#14-supportweb)
   - [15. support:jwt](#15-supportjwt)
   - [16. build-logic](#16-build-logic)
3. [헥사고날 의존 규칙](#헥사고날-의존-규칙)
4. [설정 파일 (settings.gradle)](#설정-파일)
5. [관련 문서](#관련-문서)

---

## 모듈 개요

| 계층 | 모듈 | 책임 | 주요 의존성 |
|------|------|------|----------|
| **Core Application** | `core:application` | 웹 컨트롤러 + 비즈니스 서비스 + 공통 설정 | `core:domain`, `support:util`, `support:logging`, `support:web`, `support:jwt`, `client:storage-file`(runtime), `storage:mybatis`(runtime), `storage:jpa`(runtime), `storage:file-local`(runtime), `webflux`, `security`, `data-redis-reactive`, `actuator`, `validation`, `flyway` |
| **Core Batch** | `core:batch` | 배치/스케줄 작업 부트스트랩 | `support:logging`, `spring-boot-starter-batch`, `spring-boot-starter-jdbc`, `postgresql`(runtime), `jackson-module-kotlin` |
| **Gateway Core** | `gateway:core` | WebFilter + 라우팅 + 프록시 + 관측성 핵심 로직 | `support:logging`, `support:util`, `support:web`, `support:jwt`, `webflux`, `data-redis-reactive`, `jackson-module-kotlin` |
| **Gateway Autoconfigure** | `gateway:autoconfigure` | `gateway.mode` 기반 자동 설정 및 조건부 빈 등록 | `gateway:core`, `spring-boot-autoconfigure`, `webflux`, `data-redis-reactive` |
| **Gateway Starter** | `gateway:starter` | 게이트웨이 탑재용 스타터 라이브러리 (core + autoconfigure) | `gateway:core`(api), `gateway:autoconfigure`(api), `webflux`(api), `data-redis-reactive`(api) |
| **Gateway App** | `gateway:app` | 독립 실행형 API 게이트웨이 부트 애플리케이션 (포트 8080) | `gateway:starter`, `support:logging` |
| **Core Domain** | `core:domain` | 순수 Kotlin 도메인 모델 + 포트 인터페이스 | 없음 (외부 라이브러리 및 프레임워크 비의존 순수 Kotlin) |
| **Client Storage File** | `client:storage-file` | 로컬 파일 저장 어댑터 (레거시 `@Deprecated`) | `core:domain`, `webflux`, `kotlinx-coroutines-reactor` |
| **Storage MyBatis** | `storage:mybatis` | MyBatis + PostgreSQL 저장소 구현 | `core:domain`, `support:util`, `mybatis-spring-boot-starter:4.0.1`, `postgresql`(runtime), `jackson-module-kotlin` |
| **Storage JPA** | `storage:jpa` | Spring Data JPA + PostgreSQL 저장소 구현 | `core:domain`, `support:util`, `spring-boot-starter-data-jpa`, `postgresql`(runtime), `cc.midolog.jpa-dsl`(plugin) |
| **Storage File Local** | `storage:file-local` | 로컬 파일 시스템 저장 어댑터 및 Spring Boot 4 자동 설정 | `core:domain`, `support:util`, `spring-boot-starter`, `kotlinx-coroutines-core` |
| **Support Util** | `support:util` | 순수 Kotlin 유틸 (IdGenerator, 확장함수, 유효성 검증 등) | 없음 (순수 Kotlin) |
| **Support Logging** | `support:logging` | 로깅 설정 (logback-classic, logback-spring.xml, Reactor MDC) | `support:util`, `logback-classic`, `reactor-core`, `logstash-logback-encoder:8.0` |
| **Support Web** | `support:web` | 공통 WebFlux 필터, API 응답 봉투, 전역 예외 처리 | `support:logging`, `support:util`, `webflux` |
| **Support JWT** | `support:jwt` | JJWT 라이브러리 격리 및 토큰 발급/파싱 코덱 | `support:util`, `jjwt-api:0.12.6`, `jjwt-impl`(runtime), `jjwt-jackson`(runtime) |

---

## 모듈별 상세 가이드

### 1. core:application
**책임**: 웹 계층(REST 컨트롤러) + 비즈니스 계층(서비스) + 인프라 캐시 + 보안 및 앱 설정. 비즈니스 API 서버.

**패키지 구조**:
```
cc.midolog
├── ApplicationServer.kt
├── business
│   └── service
│       ├── FileService.kt
│       ├── SampleService.kt
│       └── UserService.kt
├── common
│   └── security
│       ├── JwtProvider.kt
│       └── SecurityConfig.kt
├── infra
│   └── cache
│       └── RedisSampleCacheAdapter.kt
└── web
    ├── auth
    │   └── AuthController.kt
    ├── file
    │   ├── FileController.kt
    │   ├── WebFluxChunkBridge.kt
    │   └── dto
    │       └── FileResponse.kt
    ├── sample
    │   ├── SampleController.kt
    │   ├── SampleStreamController.kt
    │   └── dto
    │       ├── CreateSampleRequest.kt
    │       └── SampleResponse.kt
    └── user
        ├── UserController.kt
        └── dto
            ├── CreateUserRequest.kt
            └── UserResponse.kt
```

**부트 클래스**: `cc.midolog.ApplicationServer`

**제공 API**:
- **인증**: `POST /api/auth/token`
- **Sample**: `GET /api/sample/ping`, `GET /api/sample/{id}`, `POST /api/sample`
- **User**: `POST /api/user`, `GET /api/user/{id}`
- **File**:
  - `POST /api/files`: 멀티파트 파일 업로드 (`HttpStatus.CREATED` 201, `ApiResponse<FileResponse>`). 크기 초과 시 413 Payload Too Large, 허용되지 않는 미디어 타입 시 415 Unsupported Media Type.
  - `GET /api/files/{id}`: 파일 메타데이터 조회 (`ApiResponse<FileResponse>`).
  - `GET /api/files/{id}/content`: 파일 스트리밍 다운로드 (`ResponseEntity<Flux<DataBuffer>>`, 청크 버퍼 기반 논블로킹 스트리밍).
  - `DELETE /api/files/{id}`: 파일 및 메타데이터 삭제 (`ApiResponse<Unit>`).

**주요 의존성**:
- `implementation`: `core:domain`, `support:util`, `support:logging`, `support:web`, `support:jwt`
- `runtimeOnly`: `client:storage-file`, `storage:mybatis`, `storage:jpa`, `storage:file-local`, `org.flywaydb:flyway-database-postgresql`
- **Spring Boot**: `webflux`, `security`, `data-redis-reactive`, `actuator`, `validation`, `flyway`
- **라이브러리**: `reactor-kotlin-extensions`, `kotlinx-coroutines-reactor`, `tools.jackson.module:jackson-module-kotlin`

**build.gradle 예시**:
```groovy
dependencies {
    implementation project(':core:domain')
    implementation project(':support:util')
    implementation project(':support:logging')
    implementation project(':support:web')
    implementation project(':support:jwt')

    runtimeOnly project(':client:storage-file')
    runtimeOnly project(':storage:mybatis')
    runtimeOnly project(':storage:jpa')
    runtimeOnly project(':storage:file-local')

    implementation 'org.springframework.boot:spring-boot-starter-webflux'
    implementation 'io.projectreactor.kotlin:reactor-kotlin-extensions'
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-reactor'
    implementation 'tools.jackson.module:jackson-module-kotlin'

    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis-reactive'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-flyway'
    runtimeOnly 'org.flywaydb:flyway-database-postgresql'

    testImplementation 'org.springframework.boot:spring-boot-starter-webflux-test'
    testImplementation 'org.jetbrains.kotlinx:kotlinx-coroutines-test'
    testImplementation 'org.springframework.security:spring-security-test'
    testImplementation project(':storage:mybatis')
    testImplementation project(':storage:jpa')
}
```

- **이 모듈의 가드**:
  - `cc.midolog.web.ControllerResponseTypeTest` (컨트롤러 반환 타입이 도메인 모델을 직접 노출하지 않고 `ApiResponse` 봉투 규약을 준수하는지 검증).
  - `cc.midolog.storage.FileStorageIntegrationTest` (호스트 scanBasePackages = ["cc.midolog"]와 `FileStorageAutoConfiguration`이 함께 로드될 때 레거시 빈 `localFileStorageAdapter`와 신규 자동 설정 빈 `fileLocalStorageAdapter`의 이름 충돌 없이 두 `FileStoragePort`가 공존함을 가드).
- **정리 후보**: [docs/DEAD_CODE_CANDIDATES.md](./DEAD_CODE_CANDIDATES.md) (#3 `SampleService.findPair`, #4 `SampleController.echo`, #5 `SampleStreamController.stream`, #20 `CreateSampleRequest`).

---

### 2. core:batch
**책임**: 배치/스케줄 작업 부트스트랩. Spring Batch 및 JDBC 메타데이터 기반 배치 작업 처리.

**패키지 구조**:
```
cc.midolog
├── BatchApplication.kt
└── batch
    └── job
        └── SampleJobConfig.kt
```

**부트 클래스**: `cc.midolog.BatchApplication`

**주요 의존성**:
- `implementation`: `support:logging`
- **Spring Boot**: `spring-boot-starter-batch`, `spring-boot-starter-jdbc`
- **Database**: `org.postgresql:postgresql` (runtimeOnly)
- **라이브러리**: `tools.jackson.module:jackson-module-kotlin`

**build.gradle 예시**:
```groovy
dependencies {
    implementation project(':support:logging')

    implementation 'org.springframework.boot:spring-boot-starter-batch'
    implementation 'org.springframework.boot:spring-boot-starter-jdbc'
    runtimeOnly 'org.postgresql:postgresql'
    implementation 'tools.jackson.module:jackson-module-kotlin'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.batch:spring-batch-test'
}
```

- **이 모듈의 가드**: 해당 없음.
- **정리 후보**: [docs/DEAD_CODE_CANDIDATES.md](./DEAD_CODE_CANDIDATES.md) (#19 `SampleJobConfig`).

---

### 3. gateway:core
**책임**: WebFilter(인증, 레이트 제한, 요청 관측) + 라우팅 설정(`RouteConfig`) + 프록시 핸들러(`ProxyHandler`) + 라우트 선택(`GatewayRouteSelector`) + 요청 가시성(`RequestVisibility`) 등 게이트웨이의 핵심 도메인 및 비즈니스 컴포넌트 제공.

**패키지 구조**:
```
gateway/core/src/main/kotlin/cc/midolog/gateway/
├── config/
│   ├── GatewayClockConfig.kt
│   ├── GatewayRouteProperties.kt
│   ├── RouteConfig.kt
│   └── WebClientConfig.kt
├── filter/
│   ├── AuthTokenRateLimitFilter.kt
│   └── JwtAuthFilter.kt
├── proxy/
│   ├── HeaderSanitizer.kt
│   └── ProxyHandler.kt
├── ratelimit/
│   ├── RateLimiter.kt
│   └── RedisRateLimiter.kt
├── route/
│   └── GatewayRouteSelector.kt
└── visibility/
    ├── RequestEventStore.kt
    ├── RequestVisibilityController.kt
    ├── RequestVisibilityEvent.kt
    ├── RequestVisibilityFilter.kt
    └── RequestVisibilityProperties.kt
```

> **필터 책임 및 스캔 경계**: `support:web`의 `HttpLoggingFilter`(@Order(-2))와 `RequestIdFilter`(@Order(0))는 호스트 애플리케이션의 패키지 스캔(`cc.midolog`) 담당이며 `gateway:autoconfigure`가 등록하지 않습니다. `gateway:core`는 `support:web`에 의존하여 이들 필터와 연계 동작합니다.

**주요 의존성**:
- `implementation`: `project(':support:logging')`, `project(':support:util')`, `project(':support:web')`, `project(':support:jwt')`
- `implementation`: `org.springframework.boot:spring-boot-starter-webflux`
- `implementation`: `io.projectreactor.kotlin:reactor-kotlin-extensions`
- `implementation`: `org.jetbrains.kotlinx:kotlinx-coroutines-reactor`
- `implementation`: `tools.jackson.module:jackson-module-kotlin`
- `implementation`: `org.springframework.boot:spring-boot-starter-data-redis-reactive`
- `testImplementation`: `org.springframework.boot:spring-boot-starter-webflux-test`

**build.gradle 예시**:
```groovy
plugins {
    id 'io.spring.dependency-management'
}

dependencyManagement {
    imports {
        mavenBom "org.springframework.boot:spring-boot-dependencies:4.0.6"
    }
}

dependencies {
    implementation project(':support:logging')
    implementation project(':support:util')
    implementation project(':support:web')
    implementation project(':support:jwt')

    implementation 'org.springframework.boot:spring-boot-starter-webflux'
    implementation 'io.projectreactor.kotlin:reactor-kotlin-extensions'
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-reactor'
    implementation 'tools.jackson.module:jackson-module-kotlin'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis-reactive'

    testImplementation 'org.springframework.boot:spring-boot-starter-webflux-test'
}
```

- **이 모듈의 가드**: `gateway/core/src/test/kotlin/cc/midolog/gateway/GatewayPackageDependencyTest.kt` (`config` 패키지가 `handler`, `proxy`, `route` 패키지를 역참조하지 않도록 의존 방향 잠금).
- **단위/통합 테스트**:
  - `gateway/core/src/test/kotlin/cc/midolog/gateway/filter/AuthTokenRateLimitFilterTest.kt`
  - `gateway/core/src/test/kotlin/cc/midolog/gateway/filter/JwtAuthFilterTest.kt`
  - `gateway/core/src/test/kotlin/cc/midolog/gateway/proxy/ProxyHandlerTest.kt`
  - `gateway/core/src/test/kotlin/cc/midolog/gateway/route/GatewayRouteSelectorTest.kt`
  - `gateway/core/src/test/kotlin/cc/midolog/gateway/visibility/RequestVisibilityTest.kt`

---

### 4. gateway:autoconfigure
**책임**: `gateway.mode` 프로퍼티(`embedded`, `standalone`, `remote`)를 검증하고 모드 조건에 따라 게이트웨이 빈을 등록하는 Spring Boot 4 자동 설정 라이브러리.

**패키지 및 리소스 구조**:
```
gateway/autoconfigure/
├── src/main/kotlin/cc/midolog/gateway/autoconfigure/
│   ├── GatewayAutoConfiguration.kt
│   └── GatewayModeProperties.kt
└── src/main/resources/META-INF/spring/
    └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

**자동 설정 등록 및 모드 정책**:
- Spring Boot 4 규격에 따라 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`에 `cc.midolog.gateway.autoconfigure.GatewayAutoConfiguration`이 등록됩니다 (`spring.factories`는 사용하지 않음).
- `gateway.mode`는 필수 프로퍼티로 기본값이 없으며, `embedded`, `standalone`, `remote` 중 하나가 아니면 컨텍스트 기동을 즉시 실패시킵니다 (fail-fast: `"gateway.mode must be exactly one of: embedded, standalone, remote. Found: '${mode ?: "null"}'"`).
- **공통 등록 빈**: `AuthTokenRateLimitFilter`, `RedisRateLimiter`, `GatewayClockConfig` (UTC Clock), `RequestVisibility` 관련 빈(`gateway.request-visibility.enabled=true` 조건부).
- **standalone / remote 모드**: 공통 빈 외에 `RouteConfig` (routes RouterFunction), `ProxyHandler`, `WebClientConfig` (proxyWebClient), `GatewayRouteSelector`, `GatewayRouteProperties`, `JwtAuthFilter`(`jwt.secret` 필수 검증)를 추가 등록합니다. (현재 구현에서 standalone과 remote는 동일한 프록시 빈 묶음을 공유하며 환경 설정값으로 구분합니다.)
- **embedded 모드**: functional routes(`RouteConfig`)와 프록시 빈을 등록하지 않습니다. WebFlux의 Functional Router(`RouterFunctionMapping`, order=-1)가 컨트롤러 매핑(`RequestMappingHandlerMapping`, order=0)보다 우선순위가 높으므로, 프록시 라우트 빈을 제외함으로써 동일 JVM 내 `@RestController`가 요청을 직접 처리하도록 구성합니다.

**주요 의존성**:
- `implementation`: `project(':gateway:core')`
- `implementation`: `org.springframework.boot:spring-boot-autoconfigure`
- `implementation`: `org.springframework.boot:spring-boot-starter-webflux`
- `implementation`: `org.springframework.boot:spring-boot-starter-data-redis-reactive`
- `testImplementation`: `org.springframework.boot:spring-boot-starter-test`
- `testImplementation`: `io.projectreactor:reactor-test`

**build.gradle 예시**:
```groovy
plugins {
    id 'io.spring.dependency-management'
}

dependencyManagement {
    imports {
        mavenBom "org.springframework.boot:spring-boot-dependencies:4.0.6"
    }
}

dependencies {
    implementation project(':gateway:core')
    implementation 'org.springframework.boot:spring-boot-autoconfigure'
    implementation 'org.springframework.boot:spring-boot-starter-webflux'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis-reactive'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'io.projectreactor:reactor-test'
}
```

- **이 모듈의 가드**: `gateway/autoconfigure/src/test/kotlin/cc/midolog/gateway/autoconfigure/GatewayAutoConfigurationTest.kt` (mode 미설정/부적합 fail-fast, 모드별 빈 등록 검증, WebFlux order 실측 가드, AutoConfiguration.imports 리소스 가드).

---

### 5. gateway:starter
**책임**: 게이트웨이 코어(`gateway:core`)와 자동 설정(`gateway:autoconfigure`), 그리고 필수 런타임 의존성(`webflux`, `data-redis-reactive`)을 `api`로 일괄 노출하는 스타터 라이브러리(java-library).

**특징**:
- 자체 Kotlin 코드는 없으며, 게이트웨이 탑재 시 의존성 관리를 단순화하는 의존성 번들 모듈입니다.
- 독립 서비스(`gateway:app`)나 추후 임베디드 호스트(`core:application`)는 이 스타터 모듈 하나만 의존하여 게이트웨이의 모든 기능을 활성화할 수 있습니다.

**build.gradle 예시**:
```groovy
plugins {
    id 'java-library'
    id 'io.spring.dependency-management'
}

dependencyManagement {
    imports {
        mavenBom "org.springframework.boot:spring-boot-dependencies:4.0.6"
    }
}

dependencies {
    api project(':gateway:core')
    api project(':gateway:autoconfigure')
    api 'org.springframework.boot:spring-boot-starter-webflux'
    api 'org.springframework.boot:spring-boot-starter-data-redis-reactive'
}
```

---

### 6. gateway:app
**책임**: 독립 실행형 Spring Boot 4 게이트웨이 마이크로서비스 (포트 8080). `gateway:starter`를 탑재하여 외부 요청을 수신하고 비즈니스 애플리케이션(8081) 또는 배치 서버(8082)로 프록시 중계합니다.

**패키지 구조**:
```
gateway/app/
├── src/main/kotlin/cc/midolog/
│   └── GatewayApplication.kt
├── src/main/resources/
│   ├── application.yml
│   └── application-local.yml
└── src/test/kotlin/cc/midolog/gateway/
    ├── GatewayAppIntegrationTest.kt
    └── config/
        └── GatewayProfileConfigTest.kt
```

**부트 클래스**: `cc.midolog.GatewayApplication`

**W1 알려진 제약 및 스캔 방어**:
- `gateway:core`의 `RequestVisibilityController`가 `@RestController`로 선언되어 있어, 최상위 `cc.midolog` 패키지 스캔 시 `request-visibility.enabled=false` 환경에서도 강제 등록되는 문제가 있습니다.
- `GatewayApplication`에서는 이를 방어하기 위해 `@ComponentScan(excludeFilters = [ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [RequestVisibilityController::class])])`로 해당 컨트롤러를 스캔에서 제외하고, `GatewayAutoConfiguration`의 조건부 `@Bean` 등록에 전적으로 위임합니다.
- (참고: L2에서 `core:application` 스타터 탑재 전 core/autoconfigure 후속 정리가 필요합니다.)

**주요 의존성**:
- `implementation`: `project(':gateway:starter')`
- `implementation`: `project(':support:logging')`
- `testImplementation`: `org.springframework.boot:spring-boot-starter-test`
- `testImplementation`: `io.projectreactor:reactor-test`
- `testImplementation`: `project(':support:web')`

**build.gradle 예시**:
```groovy
plugins {
    id 'org.jetbrains.kotlin.plugin.spring'
    id 'org.springframework.boot'
    id 'io.spring.dependency-management'
}

dependencyManagement {
    imports {
        mavenBom "org.springframework.boot:spring-boot-dependencies:4.0.6"
    }
}

dependencies {
    implementation project(':gateway:starter')
    implementation project(':support:logging')

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'io.projectreactor:reactor-test'
    testImplementation project(':support:web')
}
```

### Runtime Profile 및 실행 원칙
- `application.yml`은 `gateway.mode: standalone`을 기본 명시하며 `spring.profiles.active`를 설정하지 않습니다.
- 로컬 실행 시 `SPRING_PROFILES_ACTIVE=local JWT_SECRET=<32바이트 이상> ./gradlew :gateway:app:bootRun`을 사용합니다.
- 무인증 상태로 보호 API(예: `/api/sample/ping`) 호출 시 `JwtAuthFilter`가 401 Unauthorized를 반환하며, 유효한 JWT를 전달했으나 업스트림(8081)이 부재한 경우 연결 실패로 502 Bad Gateway(또는 타임아웃 시 504 Gateway Timeout)가 반환되어 프록시 라우팅을 검증할 수 있습니다.

- **이 모듈의 가드**:
  - `gateway/app/src/test/kotlin/cc/midolog/gateway/GatewayAppIntegrationTest.kt` (컨텍스트 로드, standalone 필수 빈, visibility 빈, support:web 패키지 스캔 등록 검증).
  - `gateway/app/src/test/kotlin/cc/midolog/gateway/config/GatewayProfileConfigTest.kt` (프로파일 및 기본 standalone 모드 프로퍼티 검증).

---

### 7. core:domain
**책임**: 순수 도메인 모델 및 포트(인터페이스) 정의. 어떤 외부 프레임워크나 라이브러리 의존도 없는 순수 Kotlin 모듈.

**패키지 구조**:
```
cc.midolog
├── file
│   ├── model
│   │   ├── FileMeta.kt
│   │   ├── FileStatus.kt
│   │   ├── PresignedRequest.kt
│   │   └── StoredFile.kt
│   └── port
│       ├── repository
│       │   └── FileMetaRepositoryPort.kt
│       └── storage
│           ├── ChunkReader.kt
│           ├── ChunkWriter.kt
│           ├── FilePresignPort.kt
│           └── FileStoragePort.kt
├── jpadsl.fixture
│   ├── RelationChild.kt
│   ├── RelationParent.kt
│   └── ScalarSample.kt
├── sample
│   ├── model
│   │   └── Sample.kt
│   └── port
│       ├── cache
│       │   └── SampleCachePort.kt
│       ├── file
│       │   └── FileStoragePort.kt
│       └── repository
│           └── SampleRepositoryPort.kt
└── user
    ├── model
    │   └── User.kt
    └── port
        └── repository
            └── UserRepositoryPort.kt
```

**도메인 모델 및 포트 계약**:
- **`StoredFile` vs `FileMeta` 구분**:
  - `StoredFile`: 스토리지 I/O(저장 완료) 결과 및 확정된 파일 속성(`id`, `ownerId`, `storageKey`, `sizeBytes`, `contentType`, `checksum`, `status`)을 담는 값객체(Value Object).
  - `FileMeta`: 파일의 생명주기 전체를 추적하는 DB 영속 메타데이터 모델. 업로드 대기(`PENDING`) 상태에서는 크기, 컨텐츠 타입, 체크섬이 미확정(nullable)이며, 만료 추적을 위한 시간 속성(`createdAt`, `updatedAt`)을 포함한다.
- **포트 계약**:
  - `FileStoragePort` (`cc.midolog.file.port.storage`): 청크 기반 비동기 스트리밍 저장(`store`), 읽기 청크 리더 반환(`load`), 멱등 삭제(`delete`), 존재 확인(`exists`)을 정의.
  - `FileMetaRepositoryPort` (`cc.midolog.file.port.repository`): 식별자 조회(`findById`), 저장/갱신(`save`), 상태 변경(`updateStatus`), 만료 대기 건 일괄 조회를 위한 **cutoff+limit 계약**(`findExpiredPending(cutoff, limit)`)을 정의.
- **레거시 포트 호환**:
  - `cc.midolog.sample.port.file.FileStoragePort`: `@Deprecated` 처리되어 신규 포트(`cc.midolog.file.port.storage.FileStoragePort`)로 대체되었으나, 기존 어댑터와의 하위 호환성을 위해 심볼을 유지한다.

**주요 의존성**:
- 없음 (프레임워크 비의존 순수 Kotlin 모듈)

**build.gradle**:
```groovy
// 프레임워크 비의존 순수 Kotlin 모듈 (model + port 인터페이스)
dependencies {
}
```

- **이 모듈의 가드**: `cc.midolog.sample.DomainPurityTest` (도메인 소스 내 Spring, JPA, MyBatis, 어노테이션 등이 들어가지 않도록 검증하는 순수성 가드).
- **정리 후보**: [docs/DEAD_CODE_CANDIDATES.md](./DEAD_CODE_CANDIDATES.md) (#1 레거시 `FileStoragePort`).

---

### 8. client:storage-file
**책임**: 구버전 로컬 파일 저장 어댑터 (`@Deprecated`). 구 `cc.midolog.sample.port.file.FileStoragePort` 포트 구현체.

신규 파일 저장소 표준 구현은 `storage:file-local`로 대체되었으며, 하위 호환성을 위해 유지됩니다.

**패키지 구조**:
```
cc.midolog.client.storage
└── LocalFileStorageAdapter.kt
```

**빈 등록 및 공존 정책**:
- `@Component`로 선언되어 Spring의 기본 빈 명명 규칙에 따라 `localFileStorageAdapter` 이름으로 등록됩니다.
- 신규 자동 설정 모듈(`storage:file-local`)의 `@Bean("fileLocalStorageAdapter")`와 빈 이름이 서로 다르므로 충돌 없이 공존하며, `core:application`의 `FileStorageIntegrationTest`가 두 포트 구현체의 정상 등록을 가드합니다.

**주요 의존성**:
- `implementation`: `core:domain`
- **Spring Boot**: `spring-boot-starter-webflux`
- **라이브러리**: `kotlinx-coroutines-reactor`

**build.gradle 예시**:
```groovy
dependencies {
    implementation project(':core:domain')

    implementation 'org.springframework.boot:spring-boot-starter-webflux'
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-reactor'
}
```

- **이 모듈의 가드**: 해당 없음.
- **정리 후보**: [docs/DEAD_CODE_CANDIDATES.md](./DEAD_CODE_CANDIDATES.md) (#2 `LocalFileStorageAdapter`).

---

### 9. storage:mybatis
**책임**: MyBatis + PostgreSQL 저장소 구현. `*RepositoryAdapter` + `*Mapper` + SQL 매핑 (`resources/mapper/{context}/*Mapper.xml`).

**패키지 구조**:
```
cc.midolog.storage.mybatis
├── config
│   └── MyBatisStorageConfig.kt
├── file
│   ├── FileMetaMapper.kt
│   └── MyBatisFileMetaRepositoryAdapter.kt
├── sample
│   ├── MyBatisSampleRepositoryAdapter.kt
│   └── SampleMapper.kt
└── user
    ├── MyBatisUserRepositoryAdapter.kt
    └── UserMapper.kt

resources/mapper/
├── file
│   └── FileMetaMapper.xml
├── sample
│   └── SampleMapper.xml
└── user
    └── UserMapper.xml
```

**File 도메인 지원**:
- `MyBatisFileMetaRepositoryAdapter`: `core:domain`의 `FileMetaRepositoryPort` 구현체. V2 Flyway 마이그레이션(`V2__create_file_meta.sql`)으로 생성된 `file_meta` 테이블을 대상으로 `upsert`, `selectById`, `updateStatus`를 처리합니다.
- **cutoff+limit 계약 준수**: `findExpiredPending(cutoff, limit)` 메서드를 통해 PENDING 상태이면서 cutoff 시각 이전에 갱신된 만료 레코드를 `limit` 건수만큼 정렬 조회합니다.

**주요 의존성**:
- `implementation`: `core:domain`, `support:util`
- **MyBatis**: `mybatis-spring-boot-starter:4.0.1`
- **Database**: `org.postgresql:postgresql` (runtimeOnly)
- **라이브러리**: `kotlinx-coroutines-core`, `tools.jackson.module:jackson-module-kotlin`

**build.gradle 예시**:
```groovy
dependencies {
    implementation project(':core:domain')
    implementation project(':support:util')

    implementation 'org.mybatis.spring.boot:mybatis-spring-boot-starter:4.0.1'
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-core'
    runtimeOnly 'org.postgresql:postgresql'
    implementation 'tools.jackson.module:jackson-module-kotlin'

    testImplementation 'org.mybatis.spring.boot:mybatis-spring-boot-starter-test:4.0.1'
}
```

- **이 모듈의 가드**: `cc.midolog.storage.mybatis.config.MyBatisStorageConfigTest` (`@MapperScan`이 `sample`, `user`, `file` 패키지를 포함하는지 검증).
- **정리 후보**: [docs/DEAD_CODE_CANDIDATES.md](./DEAD_CODE_CANDIDATES.md) (#12 `UserMapper.xml` SQL 별칭 중복).

---

### 10. storage:jpa
**책임**: Spring Data JPA + PostgreSQL 저장소 구현. JPA entity, repository, mapper, adapter를 domain 밖에 격리한다.
도메인은 어노테이션 없는 plain data class로 유지하고, `storage:jpa/build.gradle`의 DSL에서 JPA 생성 대상을 선언한다.

**패키지 구조**:
```
cc.midolog.storage.jpa
├── config
│   └── JpaStorageConfig.kt
├── file
│   └── JpaFileMetaRepositoryAdapter.kt
├── sample
│   ├── JpaSampleRepositoryAdapter.kt
│   └── ScalarSampleCodeJpaConverter.kt
└── user
    └── JpaUserRepositoryAdapter.kt
```

※ `SampleJpaEntity`, `UserJpaEntity`, `FileMetaJpaEntity` 및 `FileMetaJpaRepository`, `FileMetaJpaMapper` 같은 entity/repository/mapper 타입은 `generateJpaDslSources` task가 build 디렉터리(`build/generated`)에 자동 생성한다.

**File 도메인 지원 및 livePostgresTest 격리**:
- `JpaFileMetaRepositoryAdapter`: `FileMetaRepositoryPort` 포트 구현체로, blocking JPA 호출을 `Dispatchers.IO` 및 `TransactionOperations` 경계 내에서 안전하게 실행합니다.
- `FileMetaRepositoryPort`의 cutoff+limit 계약(`findExpiredPending(cutoff, limit)`)을 `entityManager` 쿼리 파라미터(`setMaxResults(limit)`)를 통해 구현합니다.
- **테스트 분리 정책**: 기본 `./gradlew build` 및 `./gradlew test`에서는 H2 In-Memory DB로 어댑터를 검증하며, 실제 PostgreSQL DB 연결이 필요한 `livePostgresTest` 태스크는 기본 빌드 실행에서 제외되어 선택적으로만 수행됩니다.

**주요 의존성**:
- `implementation`: `core:domain`, `support:util`
- **JPA**: `spring-boot-starter-data-jpa`
- **Database**: `org.postgresql:postgresql` (runtimeOnly)
- **라이브러리**: `kotlinx-coroutines-core`
- **Test**: `spring-boot-starter-data-jpa-test`, `spring-boot-starter-flyway`, `com.h2database:h2`(testRuntimeOnly), `flyway-database-postgresql`(testRuntimeOnly)

**build.gradle 예시**:
```groovy
plugins {
    id 'cc.midolog.jpa-dsl'
}

jpaDsl {
    entity('cc.midolog.sample.model.Sample') {
        table = 'sample'
        id = 'id'
    }

    entity('cc.midolog.user.model.User') {
        table = 'app_user'
        id = 'id'

        field('displayName') {
            column = 'display_name'
        }
    }

    entity('cc.midolog.file.model.FileMeta') {
        table = 'file_meta'
        id = 'id'
        field('ownerId') { column = 'owner_id' }
        field('storageKey') { column = 'storage_key' }
        field('status') { enumStrategy = 'STRING' }
        field('sizeBytes') { column = 'size_bytes'; nullable = true }
        field('contentType') { column = 'content_type'; nullable = true }
        field('checksum') { nullable = true }
        field('createdAt') { column = 'created_at' }
        field('updatedAt') { column = 'updated_at' }
    }

    entity('cc.midolog.jpadsl.fixture.ScalarSample') {
        table = 'scalar_sample'
        id = 'id'

        field('displayName') {
            column = 'display_name'
        }
        field('nickname') {
            nullable = true
        }
        field('status') {
            enumStrategy = 'STRING'
        }
        field('code') {
            column = 'code_value'
            storageType = 'String'
            converter = 'cc.midolog.storage.jpa.sample.ScalarSampleCodeJpaConverter'
        }
    }

    entity('cc.midolog.jpadsl.fixture.RelationParent') {
        table = 'relation_parent'
        id = 'id'

        relation('children') {
            type = 'oneToMany'
            target = 'cc.midolog.jpadsl.fixture.RelationChild'
            mappedBy = 'parent'
            toDomain = 'emptyList()'
        }
    }

    entity('cc.midolog.jpadsl.fixture.RelationChild') {
        table = 'relation_child'
        id = 'id'

        field('parentId') {
            relation = 'parent'
        }
        relation('parent') {
            type = 'manyToOne'
            target = 'cc.midolog.jpadsl.fixture.RelationParent'
            sourceField = 'parentId'
            joinColumn = 'parent_id'
            referencedColumn = 'id'
        }
    }
}

dependencies {
    implementation project(':core:domain')
    implementation project(':support:util')

    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-core'
    runtimeOnly 'org.postgresql:postgresql'

    testImplementation 'org.springframework.boot:spring-boot-starter-data-jpa-test'
    testImplementation 'org.springframework.boot:spring-boot-starter-flyway'
    testRuntimeOnly 'com.h2database:h2'
    testRuntimeOnly 'org.flywaydb:flyway-database-postgresql'
}
```

### JPA DSL 요약

JPA DSL은 `core:domain`의 어노테이션 없는 순수 data class를 읽어 JPA Entity, Spring Data Repository, Domain Mapper 소스코드를 build 디렉터리에 자동 생성합니다. 스칼라 매핑, ENUM 전략, 커스텀 컨버터, 부모-자식 관계(`manyToOne`, `oneToMany`)를 선언할 수 있으며, 지원하지 않는 DSL 선언은 컴파일 전에 검증 태스크에서 차단됩니다. 상세 명세와 PostgreSQL 실환경 스모크 테스트 가이드는 [storage/jpa/README.md](../storage/jpa/README.md)를 참고하십시오.

- **이 모듈의 가드**: `cc.midolog.storage.jpa.sample.JpaDslGeneratedSourceTest` (JPA DSL 생성 소스코드 검증), `validateJpaDslGeneratorNegativeCases` 태스크.
- **정리 후보**: [docs/DEAD_CODE_CANDIDATES.md](./DEAD_CODE_CANDIDATES.md).

---

### 11. storage:file-local
**책임**: 로컬 파일 시스템 기반의 파일 영속 저장소 어댑터 및 Spring Boot 4 표준 자동 설정. `core:domain`의 `cc.midolog.file.port.storage.FileStoragePort` 포트를 구현합니다.

**패키지 구조**:
```
cc.midolog.storage.file
├── autoconfigure
│   ├── FileStorageAutoConfiguration.kt
│   ├── FileStorageProperties.kt
│   └── FileStoragePropertiesValidator.kt
└── local
    └── LocalFileStorageAdapter.kt

resources/META-INF/spring/
└── org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

**자동 설정 및 빈 등록**:
- **AutoConfiguration.imports 표준 채택**: `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`에 `cc.midolog.storage.file.autoconfigure.FileStorageAutoConfiguration`을 선언하여 Spring Boot 4 표준 방식으로 자동 등록합니다.
- **충돌 방지 빈 명명 규칙**: 레거시 `client:storage-file`의 `@Component` 기본 빈 이름(`localFileStorageAdapter`)과 충돌을 방지하기 위해 `@Bean("fileLocalStorageAdapter")`로 명시적 이름을 부여하여 컨텍스트 내 공존을 보장합니다.
- **조건부 등록**: `@ConditionalOnProperty(name = ["storage.file.provider"], havingValue = "local")`를 통해 `storage.file.provider=local`일 때만 어댑터 빈을 등록합니다.

**설정 프로퍼티 및 Fail-Fast 유효성 검증 (`FileStoragePropertiesValidator`)**:
애플리케이션 기동 시 `InitializingBean.afterPropertiesSet()`에서 필수 프로퍼티를 엄격히 검증하여 부적합 시 즉시 `IllegalStateException`으로 fail-fast합니다:
- `storage.file.provider`: 필수값이며 반드시 `"local"`이어야 함 (누락 또는 오타 시 기동 실패).
- `storage.file.local.root-dir`: 필수값이며 반드시 **절대 경로**여야 함 (누락 또는 상대 경로 시 기동 실패).
- `storage.file.max-size-bytes`: 0보다 커야 함 (기본값: `10485760`, 즉 10MB).
- `storage.file.allowed-content-types`: 허용 MIME 타입 목록 (기본값: 빈 목록 = 전체 허용).

**주요 의존성**:
- `implementation project(':core:domain')`
- `implementation project(':support:util')`
- `implementation 'org.springframework.boot:spring-boot-starter'`
- `implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-core'`

**build.gradle 예시**:
```groovy
dependencies {
    implementation project(':core:domain')
    implementation project(':support:util')

    implementation 'org.springframework.boot:spring-boot-starter'
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-core'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.jetbrains.kotlinx:kotlinx-coroutines-test'
}
```

- **이 모듈의 가드**:
  - `cc.midolog.storage.file.autoconfigure.FileStorageAutoConfigurationTest` (provider 누락/오타 fail-fast, 상대경로 root-dir fail-fast, max-size-bytes fail-fast, imports 파일 무결성, 컴포넌트 스캔 중복 시 단일 빈 보장, 레거시 빈 `localFileStorageAdapter` 공존 검증).
  - `cc.midolog.storage.file.local.LocalFileStorageAdapterTest` (로컬 FS 저장, 청크 스트리밍 읽기, 멱등 삭제, 파일 존재 확인 기능 검증).

---

### 12. support:util
**책임**: 순수 Kotlin 유틸리티. IdGenerator, 시간 제공자, 확장함수, 유효성 검증, 페이징/결과/재시도 헬퍼.

**패키지 구조**:
```
cc.midolog.util
├── ext
│   ├── CollectionExtensions.kt
│   ├── NullSafety.kt
│   └── StringExtensions.kt
├── mask
│   └── Masking.kt
├── paging
│   └── Page.kt
├── result
│   └── Outcome.kt
├── retry
│   └── Retry.kt
├── IdGenerator.kt
├── JwtSecretValidator.kt
├── TimeProvider.kt
├── UtilDefaults.kt
└── Validation.kt
```

**주요 의존성**:
- 없음 (외부 의존성 없는 순수 Kotlin 모듈)

**build.gradle**:
```groovy
// 순수 Kotlin 유틸리티 모듈
dependencies {
}
```

- **이 모듈의 가드**: `cc.midolog.util.ExistingUtilCompatibilityTest` (유틸리티 API 시그니처 및 하위 호환성 유지 가드).
- **정리 후보**: [docs/DEAD_CODE_CANDIDATES.md](./DEAD_CODE_CANDIDATES.md) (#8 `UtilDefaults`, #9 `CollectionExtensions.orEmpty`, #10 `NullSafety`/`Validation` 중복, #11 미사용 컴포넌트군, #16 `NullSafety.ifNull`, #17 `CollectionExtensions.chunkedBy`, #18 `StringExtensions`).

---

### 13. support:logging
**책임**: 로깅 공통 설정 및 컨텍스트 전파. logback-spring.xml, Reactor Context MDC 전파, 민감정보 마스킹 로깅.

**패키지 구조**:
```
cc.midolog.logging
├── LoggingMdc.kt
├── MaskingMessageConverter.kt
├── MaskingSupport.kt
└── ReactorMdc.kt

resources/
└── logback-spring.xml
```

**주요 의존성**:
- `implementation`: `support:util`
- **Logback**: `ch.qos.logback:logback-classic`
- **Reactor**: `io.projectreactor:reactor-core`
- **Logstash**: `net.logstash.logback:logstash-logback-encoder:8.0`

**build.gradle 예시**:
```groovy
dependencies {
    implementation project(':support:util')

    implementation 'ch.qos.logback:logback-classic'
    implementation 'io.projectreactor:reactor-core'
    implementation 'net.logstash.logback:logstash-logback-encoder:8.0'

    testImplementation 'io.projectreactor:reactor-test'
}
```

- **이 모듈의 가드**: `cc.midolog.logging.MaskingLoggingTest`, `cc.midolog.logging.ReactorMdcTest` (민감정보 마스킹 및 리액티브 MDC 전파 가드).
- **정리 후보**: [docs/DEAD_CODE_CANDIDATES.md](./DEAD_CODE_CANDIDATES.md).

---

### 14. support:web
**책임**: 공통 WebFlux 필터, 표준 API 응답 봉투, 전역 예외 처리 핸들러.

**패키지 구조**:
```
cc.midolog.web
├── exception
│   ├── ApiException.kt
│   └── ErrorCode.kt
├── filter
│   ├── HttpLoggingFilter.kt
│   └── RequestIdFilter.kt
├── handler
│   └── GlobalExceptionHandler.kt
└── response
    └── ApiResponse.kt
```

#### 전체 필터 실행 순서

| 순서 (`@Order`) | 필터 | 모듈 | 책임 및 동작 |
|:---:|---|---|---|
| `-2` | `HttpLoggingFilter` | `support:web` | 최외곽에서 요청 시작 시각을 기록하고, 완료 시점에 `method`, `path`, `status`, `durationMs`, `requestId` 메타데이터를 INFO로 로깅 (바디/쿼리스트링 제외). |
| `-1` | `AuthTokenRateLimitFilter` | `gateway:core` | `POST /api/auth/token`에 대해 클라이언트 IP 기준 10회/60초(`gateway.rate-limit.auth-token.*`), Redis Lua 원자 카운터 (Redis 장애 시 fail-open). |
| `0` | `RequestIdFilter` | `support:web` | `X-Request-Id` 헤더를 검증하거나 UUID를 신규 생성하여 다운스트림 요청/응답 헤더에 전파하고 Reactor Context 및 MDC에 바인딩. |
| `1` | `JwtAuthFilter` | `gateway:core` | 공개 경로(`/api/auth/`, `/actuator/`, `/batch/`)는 무검증 통과, 보호 경로(`/api/`, `/internal/gateway/`)는 Bearer JWT 서명 검증 (실패 시 401, 성공 시 컨텍스트 주입 없이 그대로 통과). |
| `100` | `RequestVisibilityFilter` | `gateway:core` | 요청 관측 활성화(`gateway.request-visibility.enabled=true`) 시 메타데이터 이벤트를 인메모리 저장소에 기록. |

#### 표준 API 응답 봉투 (ApiResponse)

모든 API 응답은 `cc.midolog.web.response.ApiResponse` 봉투로 감싸서 반환합니다.
- `ApiResponse.ok(data, message = "OK")`: 성공 응답 (`success = true`, `code = "OK"`, 데이터 포함)
- `ApiResponse.error(code, message)`: 실패 응답 (`success = false`, `data = null`)

```kotlin
data class ApiResponse<T>(
    val success: Boolean,
    val code: String,
    val message: String,
    val data: T? = null,
)
```

#### ErrorCode ↔ HTTP 상태 매핑 및 전역 예외 처리

`GlobalExceptionHandler`(`@RestControllerAdvice`)는 발생하는 예외를 표준 `ApiResponse` 에러 형식으로 일관되게 변환합니다.

| ErrorCode | HTTP Status | 기본 메시지 | 설명 |
|---|---|---|---|
| `INVALID_INPUT` | `400 BAD_REQUEST` | "잘못된 요청입니다" | 요청 파라미터 또는 바디 유효성 검증 실패 |
| `NOT_FOUND` | `404 NOT_FOUND` | "리소스를 찾을 수 없습니다" | 대상 리소스 미존재 |
| `UNAUTHORIZED` | `401 UNAUTHORIZED` | "인증이 필요합니다" | 인증 토큰 누락 또는 유효하지 않음 |
| `FORBIDDEN` | `403 FORBIDDEN` | "접근 권한이 없습니다" | 리소스 접근 권한 부족 |
| `INTERNAL` | `500 INTERNAL_SERVER_ERROR` | "서버 오류가 발생했습니다" | 서버 내부 미처리 예외 |

- **예외 매핑 동작**:
  1. `ApiException`: `e.errorCode.status` 상태 코드로 응답하며, 본문에 `ApiResponse.error(e.errorCode.name, e.message)` 반환.
  2. `WebExchangeBindException` (`@Valid` 유효성 검증 실패): `400 BAD_REQUEST`, code는 `INVALID_INPUT`, 필드 에러 메시지를 취합하여 반환.
  3. `ResponseStatusException`: 프레임워크 예외 상태 코드를 보존 (`400`은 `INVALID_INPUT`, `415`는 `UNSUPPORTED_MEDIA_TYPE`, 그 외는 `REQUEST_ERROR`). 내부 스택트레이스/상세 메시지 노출 차단.
  4. `Exception` (일반 미처리 예외): `500 INTERNAL_SERVER_ERROR`, code는 `INTERNAL`. 내부 예외 메시지를 외부에 노출하지 않고 기본 메시지로 마스킹.

#### 컴포넌트 자동 등록 (Base Packages Scan)

`core:application`의 `ApplicationServer`는 `@SpringBootApplication(scanBasePackages = ["cc.midolog"])`로 지정되어 있고, `gateway:app`의 `GatewayApplication`은 `cc.midolog` 패키지 루트에 위치하므로, 두 서버 기동 시 `support:web`에 정의된 `@Component`(`HttpLoggingFilter`, `RequestIdFilter`)와 `@RestControllerAdvice`(`GlobalExceptionHandler`)가 별도 추가 설정 없이 컴포넌트 스캔을 통해 스프링 빈으로 자동 등록됩니다 (단, `GatewayApplication`은 `RequestVisibilityController` 빈 충돌 방지를 위해 ComponentScan exclude를 명시합니다).

**주요 의존성**:
- `implementation`: `support:logging`, `support:util`
- **Spring Boot**: `spring-boot-starter-webflux`

**build.gradle 예시**:
```groovy
dependencies {
    implementation project(':support:logging')
    implementation project(':support:util')

    implementation 'org.springframework.boot:spring-boot-starter-webflux'

    testImplementation 'io.projectreactor:reactor-test'
    testImplementation 'org.springframework.boot:spring-boot-starter-webflux-test'
}
```

- **이 모듈의 가드**: `support/web/src/test/kotlin/cc/midolog/web/SupportWebTest.kt` (파일 내 6개 테스트 클래스: `ApiResponseTest`, `ErrorCodeTest`, `ApiExceptionTest`, `GlobalExceptionHandlerTest`, `RequestIdFilterTest`, `HttpLoggingFilterTest`).
- **정리 후보**: [docs/DEAD_CODE_CANDIDATES.md](./DEAD_CODE_CANDIDATES.md) (#6 `ApiException.invalidInput`, #7 `ErrorCode.FORBIDDEN`).

---

### 15. support:jwt
**책임**: JJWT(Java JWT) 라이브러리 의존성을 단일 모듈로 격리하고, JWT 토큰 발급 및 서명 검증/파싱 기능을 담당하는 `JwtCodec` 제공.

**패키지 구조**:
```
cc.midolog.jwt
└── JwtCodec.kt
```

#### JwtCodec 동작
- 초기화 시점에 주입받은 시크릿 문자열을 `cc.midolog.util.JwtSecretValidator`로 검증한 후 HMAC-SHA 키를 생성합니다.
- `issue(subject, ttlMillis)`: 지정된 subject와 유효 기간으로 서명된 JWT를 생성합니다.
- `parse(token)`: 토큰의 서명을 검증하고 클레임(`Claims`)을 파싱하여 반환합니다.

**주요 의존성**:
- `api`: `support:util`
- `api`: `io.jsonwebtoken:jjwt-api:0.12.6`
- `runtimeOnly`: `io.jsonwebtoken:jjwt-impl:0.12.6`, `io.jsonwebtoken:jjwt-jackson:0.12.6`

**build.gradle 예시**:
```groovy
plugins {
    id 'java-library'
}

dependencies {
    api project(':support:util')
    api 'io.jsonwebtoken:jjwt-api:0.12.6'
    runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.6'
    runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.6'
}
```

- **이 모듈의 가드**: `cc.midolog.jwt.JwtCodecTest` (토큰 발급 및 파싱/검증 동작 가드).
- **정리 후보**: [docs/DEAD_CODE_CANDIDATES.md](./DEAD_CODE_CANDIDATES.md) (#14 JWT 키 생성 및 검증 로직 중복).

---

### 16. build-logic
**책임**: 내부 Gradle 플러그인(`cc.midolog.jpa-dsl`) 및 스키마 마이그레이션 도구 빌드 로직 제공.

**주요 태스크 (6개)**:
1. `jpaDslPluginInfo`: `cc.midolog.jpa-dsl` 플러그인 스캐폴딩 상태 보고 태스크.
2. `validateJpaDslPluginScaffold`: 플러그인 확장 설정 유효성 검증 태스크.
3. `generateJpaDslSources`: 도메인 모델 클래스를 분석하여 JPA Entity, Repository, Mapper 소스코드를 build 디렉터리에 자동 생성하는 태스크.
4. `validateJpaDslGeneratorNegativeCases`: 지원하지 않는 DSL 선언 시 `GradleException`으로 올바르게 실패하는지 검증하는 태스크.
5. `generateMigrationDraft`: 도메인 모델 스키마와 Flyway 기존 마이그레이션 간 차이점을 분석해 신규 마이그레이션 SQL 초안을 생성하는 태스크.
6. `verifyMigrationDraft`: 마이그레이션 초안과 도메인 기대 스키마 간 일치 여부를 검증하는 태스크.

**검증 명령어**:
```bash
./gradlew -p build-logic test
```

- **이 모듈의 가드**: `cc.midolog.buildlogic.jpadsl.JpaDslPluginTest`, `cc.midolog.buildlogic.jpadsl.MigrationDraftTest`.
- **정리 후보**: [docs/DEAD_CODE_CANDIDATES.md](./DEAD_CODE_CANDIDATES.md) (#13 `jpaDslPluginInfo`, `validateJpaDslPluginScaffold`).

---

## 헥사고날 의존 규칙

### 새 도메인 추가 절차

`user` 도메인은 새 업무 도메인을 추가할 때 따를 기준 예제다. `sample`은 smoke/regression 용도로 남겨 두고, 실제 업무 흐름은 `user`처럼 별도 bounded context로 추가한다.

1. `core:domain`에 `<context>.model.*` domain data class와 `<context>.port.repository.*RepositoryPort`를 추가한다.
2. `core:application`에 application service를 추가하고, concrete storage import 없이 domain port만 주입한다.
3. REST API가 필요하면 `web.<context>` controller와 DTO를 추가한다. controller는 request/response 변환만 담당하고 저장 구현을 직접 알지 않는다.
4. MyBatis를 지원하려면 `storage:mybatis`에 mapper interface, XML mapper, `@Profile("mybatis")` repository adapter를 추가한다.
5. JPA를 지원하려면 `storage:jpa/build.gradle`의 `jpaDsl { ... }`에 domain class/table/id/field/relation 매핑을 선언하고, `@Profile("jpa")` repository adapter를 추가한다.
6. 같은 port contract를 MyBatis/JPA adapter 모두에 적용하는 테스트를 추가하고, profile wiring 테스트에서 profile별 port bean이 하나만 등록되는지 검증한다.
7. `core:domain` purity test가 Spring/JPA/MyBatis annotation 유입을 막는지 확인한 뒤 `./gradlew test`를 실행한다.
8. JPA DSL이 실패하면 generated Kotlin을 고치지 말고 `jpaDsl { ... }` 선언이나 domain data class를 수정한다. unsupported DSL은 plugin validation 단계에서 실패해야 한다.

보안 주의: `user` 예제는 persistence 구조 예시이며 인증 구현이 아니다. 비밀번호 저장, password hashing, refresh token, session, role/permission 정책은 별도 보안 설계와 테스트 없이 템플릿 예제로 추가하지 않는다.

### MyBatis/JPA 선택 기준

- MyBatis는 SQL을 명시적으로 통제해야 하거나 기존 PostgreSQL 쿼리/튜닝 자산을 그대로 쓰는 서비스에 적합하다. mapper XML과 adapter 테스트로 SQL 경계를 검증한다.
- JPA는 단순 CRUD 중심 도메인과 Spring Data repository 생태계를 활용할 때 적합하다. 이 프로젝트에서는 domain model을 오염시키지 않기 위해 `storage:jpa/build.gradle` DSL로 JPA entity/repository/mapper를 생성한다.
- 두 구현은 같은 repository port contract를 만족해야 한다. 운영 profile은 `mybatis` 또는 `jpa` 중 하나만 활성화한다.

### 계층별 의존 방향
```
[게이트웨이 계층 의존 흐름]
gateway:app
    │ (depends)
    ▼
gateway:starter ─────────┐ (api)
    │ (api)              │
    ▼                    ▼
gateway:autoconfigure ──→ gateway:core ──→ support:* (web, jwt, logging, util)

[비즈니스 및 도메인 계층 의존 흐름]
core:application ──→ core:domain
    │ (runtimeOnly)      ▲
    ├────────────────────┤
    ▼                    │ (implements port)
client:storage-file ─────┤
storage:mybatis ─────────┤
storage:jpa ─────────────┤
storage:file-local ──────┘
```

**핵심 규칙**:
1. **상향식 의존**: 상위 계층(application, gateway:app, batch)은 하위 계층(domain, client, storage, support, gateway:core/autoconfigure/starter)에 의존.
2. **역전 원칙**: domain은 client/storage에 의존하지 않음. 대신 client/storage가 domain의 포트(인터페이스)를 구현.
3. **어댑터 주입**: application에서 client, storage는 `runtimeOnly`로 선언. Spring이 런타임에 자동 와이어링.
4. **공유 모듈**: support:util, support:logging, support:web, support:jwt는 상위 모듈에서 필요에 따라 의존 가능 (단, domain은 프레임워크 비의존 순수 Kotlin 유지).
5. **경계 존중**: 모듈 간 직접 import 금지. 공개된 인터페이스(포트)만 사용.

---

## 설정 파일

### settings.gradle
```groovy
rootProject.name = 'backend-init'

includeBuild 'build-logic'

// core
include 'core:application'
include 'core:batch'
include 'core:domain'

// gateway
include 'gateway:core'
include 'gateway:autoconfigure'
include 'gateway:starter'
include 'gateway:app'

// client
include 'client:storage-file'

// storage
include 'storage:mybatis'
include 'storage:jpa'
include 'storage:file-local'

// support
include 'support:util'
include 'support:logging'
include 'support:web'
include 'support:jwt'
```

### 빌드 순서
1. `support:util` (외부 의존성 없음)
2. `core:domain`, `support:logging`, `support:jwt` (`support:util` 의존 / 도메인은 의존성 0)
3. `support:web`, `client:storage-file`, `storage:mybatis`, `storage:jpa`, `storage:file-local` (`support:*`, `core:domain` 의존)
4. `gateway:core` (`support:web`, `support:jwt`, `support:logging`, `support:util` 의존)
5. `gateway:autoconfigure` (`gateway:core` 의존)
6. `gateway:starter` (`gateway:core`, `gateway:autoconfigure` 의존)
7. `core:batch`, `gateway:app`, `core:application` (도메인, 어댑터, 스타터/지원 모듈 의존)

Gradle은 자동으로 의존도를 계산하여 올바른 순서로 빌드합니다.

---

## 관련 문서

- [./ARCHITECTURE.md](./ARCHITECTURE.md) — 전체 아키텍처, 통신 흐름, 배포
- [./GATEWAY.md](./GATEWAY.md) — 게이트웨이 상세 가이드 (필터, 라우팅, 모니터링)
- [./FUTURE.md](./FUTURE.md) — 향후 확장 계획 (Config Server, 마이크로서비스 분리 등)
- [../storage/jpa/README.md](../storage/jpa/README.md) — JPA DSL 상세 명세 및 PostgreSQL 스모크 테스트 가이드
- [./DEAD_CODE_CANDIDATES.md](./DEAD_CODE_CANDIDATES.md) — 죽은 코드 및 중복 코드 후보 목록

---

**작성일**: 2026-09-12
**프로젝트**: backend-init (Spring Boot 4 + Kotlin 2.2.x)
