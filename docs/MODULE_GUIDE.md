# 모듈 가이드 (Module Guide)

Spring Boot 4 + Kotlin 기반 헥사고날 멀티모듈 아키텍처의 10개 모듈별 책임, 패키지 구조, 의존성 정의 가이드.

---

## 목차
1. [모듈 개요](#모듈-개요)
2. [모듈별 상세 가이드](#모듈별-상세-가이드)
3. [헥사고날 의존 규칙](#헥사고날-의존-규칙)
4. [설정 파일 (settings.gradle)](#설정-파일)
5. [관련 문서](#관련-문서)

---

## 모듈 개요

| 계층 | 모듈 | 책임 | 주요 의존성 |
|------|------|------|----------|
| **Core Domain** | `core:domain` | 순수 Kotlin 도메인 모델 + 포트 인터페이스 | `support:util` |
| **Core Application** | `core:application` | 웹 컨트롤러 + 비즈니스 서비스 + 공통 설정 | `core:domain`, `support:util`, `support:logging`, `webflux`, `security`, `jjwt`, `redis-reactive` |
| **Core Gateway** | `core:gateway` | WebFilter + 라우팅 + 단일 진입점 | `support:logging`, `webflux`, `jjwt`, `redis-reactive` |
| **Core Batch** | `core:batch` | 배치/스케줄 작업 부트스트랩 | `core:domain`, `support:*` |
| **Client Storage File** | `client:storage-file` | 로컬 파일 저장 어댑터 | `core:domain` |
| **Storage MyBatis** | `storage:mybatis` | MyBatis + PostgreSQL 저장소 구현 | `core:domain`, `support:util`, `mybatis-spring-boot-starter` |
| **Storage JPA** | `storage:jpa` | Spring Data JPA + PostgreSQL 저장소 구현 | `core:domain`, `support:util`, `spring-boot-starter-data-jpa` |
| **Support Util** | `support:util` | 순수 Kotlin 유틸 (IdGenerator, 확장함수 등) | - |
| **Support Logging** | `support:logging` | 로깅 설정 (logback-classic, logback-spring.xml) | `spring-boot-dependencies` |
| **Support Web** | `support:web` | 공통 WebFlux 필터, API 응답, 예외 처리 | `support:logging`, `webflux` |

---

## 모듈별 상세 가이드

### 1. core:domain
**책임**: 순수 도메인 모델 및 포트(인터페이스) 정의. 어떤 외부 의존도 없는 순수 Kotlin.

**패키지 구조**:
```
cc.midolog
├── <context>.model          # 엔티티, 값 객체, Aggregate
├── <context>.port
│   ├── repository           # Repository 포트 인터페이스
│   └── client               # 외부 클라이언트 포트 인터페이스
└── common.exception         # 도메인 예외
```

**예시**:
```
cc.midolog
├── user.model
│   ├── User.kt
│   ├── Role.kt
│   └── UserAggregateRoot.kt
├── user.port.repository
│   └── UserRepositoryPort.kt
├── ai.port.client
│   └── AiClientPort.kt
└── common.exception
    ├── DomainException.kt
    └── UserNotFoundException.kt
```

**주요 의존성**:
- `support:util` (IdGenerator, 공통 유틸만 필요)

**build.gradle 예시**:
```groovy
dependencies {
    implementation project(':support:util')
    testImplementation 'org.jetbrains.kotlin:kotlin-test-junit5'
}
```

---

### 2. core:application
**책임**: 웹 계층(컨트롤러) + 비즈니스 계층(서비스) + 공통 설정(보안, Redis, 설정). 비즈니스 API 서버.

**패키지 구조**:
```
cc.midolog
├── web.<context>            # REST 컨트롤러
│   └── *Controller.kt
├── business.service.<context>
│   ├── *Service.kt
│   └── *UseCase.kt
├── business.support.<context>
│   ├── reader              # 데이터 읽기 헬퍼
│   └── writer              # 데이터 쓰기 헬퍼
├── common.config
│   ├── WebFluxConfig.kt
│   ├── RedisConfig.kt
│   └── CoroutineConfig.kt
├── common.security
│   ├── JwtTokenProvider.kt
│   ├── SecurityConfig.kt
│   └── JwtAuthenticationFilter.kt
└── common.advice
    └── GlobalExceptionHandler.kt
```

**예시**:
```
cc.midolog
├── web.user
│   └── UserController.kt
├── web.auth
│   └── AuthController.kt
├── business.service.user
│   ├── UserService.kt
│   └── UserCreationUseCase.kt
├── business.support.user
│   ├── reader
│   │   └── UserReader.kt
│   └── writer
│       └── UserWriter.kt
├── common.config
│   ├── WebFluxConfig.kt
│   └── CoroutineConfig.kt
└── common.security
    ├── JwtTokenProvider.kt
    └── SecurityConfig.kt
```

**부트 클래스**: `cc.midolog.ApplicationApplication`

**주요 의존성**:
- `implementation`: `core:domain`, `support:util`, `support:logging`
- `runtimeOnly`: `client:storage-file`, `storage:mybatis`, `storage:jpa` (어댑터는 런타임에만 주입)
- **Spring Boot**: `webflux`, `security`, `data-redis-reactive`
- **라이브러리**: `jjwt:0.12.6`, `spring-security-oauth2-jose`

**build.gradle 예시**:
```groovy
dependencies {
    implementation project(':core:domain')
    implementation project(':support:util')
    implementation project(':support:logging')
    
    runtimeOnly project(':client:storage-file')
    runtimeOnly project(':storage:mybatis')
    runtimeOnly project(':storage:jpa')
    
    implementation 'org.springframework.boot:spring-boot-starter-webflux'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis-reactive'
    implementation 'io.jsonwebtoken:jjwt-api:0.12.6'
    runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.6'
    runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.6'
    
    testImplementation 'org.jetbrains.kotlin:kotlin-test-junit5'
}
```

---

### 3. core:gateway
**책임**: 단일 진입점. WebFilter(요청 ID 생성/전파, JWT 인증, 레이트 제한) + 라우팅 설정 + 프록시 핸들러.

**패키지 구조**:
```
cc.midolog.gateway
├── filter
│   ├── JwtAuthFilter.kt
│   └── AuthTokenRateLimitFilter.kt
├── config
│   ├── RouteConfig.kt
│   └── WebClientConfig.kt
├── handler
│   ├── HeaderSanitizer.kt
│   └── ProxyHandler.kt
└── GatewayApplication.kt (부트 클래스)
```

`RequestIdFilter`, `HttpLoggingFilter`, 공통 API 응답/예외 처리는 `support:web`에 둔다. Gateway는 `support:web`에 의존해 공통 필터를 사용한다.

**부트 클래스**: `cc.midolog.GatewayApplication`

**주요 의존성**:
- `implementation`: `support:logging`, `support:util`, `support:web`
- **Spring Boot**: `webflux`
- **라이브러리**: `jjwt:0.12.6`, `spring-boot-starter-data-redis-reactive`

**build.gradle 예시**:
```groovy
dependencies {
    implementation project(':support:logging')
    implementation project(':support:util')
    implementation project(':support:web')

    implementation 'org.springframework.boot:spring-boot-starter-webflux'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis-reactive'
    implementation 'io.jsonwebtoken:jjwt-api:0.12.6'
    runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.6'
    runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.6'
    
    testImplementation 'org.jetbrains.kotlin:kotlin-test-junit5'
}
```

### Runtime Profile 원칙

- `application.yml`은 `spring.profiles.active`를 설정하지 않는다.
- 로컬 기본값은 각 모듈의 `application-local.yml`에 둔다.
- 실행자는 `SPRING_PROFILES_ACTIVE=local,mybatis`, `local,jpa`, `local`처럼 필요한 profile을 명시한다.
- 운영 환경은 환경변수나 secret manager로 `JWT_SECRET`, DB, Redis, Gateway route 값을 주입한다.

### Platform Option 원칙

- Gateway 다중 application 라우팅은 `gateway.routes.application-urls`가 있을 때만 라운드로빈으로 동작한다. 값이 없으면 `gateway.routes.application-url` 단일 target을 유지한다.
- Gateway request visibility는 `gateway.request-visibility.enabled=true`일 때만 filter/store/controller bean이 등록된다.
- request visibility는 method, path, status, request id, timestamp, duration만 저장한다. body, Authorization header, secret 값은 저장하지 않는다.
- Config Server와 OpenTelemetry는 기본 모듈이 아니라 후속 optional 확장으로 둔다.

---

### 4. core:batch
**책산**: 배치/스케줄 작업 부트스트랩. Spring Batch 또는 TaskScheduler 기반.

**패키지 구조**:
```
cc.midolog.batch
├── job
│   └── *BatchJob.kt
├── task
│   └── *ScheduledTask.kt
├── config
│   └── BatchConfig.kt
└── BatchApplication.kt (부트 클래스)
```

**부트 클래스**: `cc.midolog.batch.BatchApplication`

**주요 의존성**:
- `implementation`: `core:domain`, `support:util`, `support:logging`
- `runtimeOnly`: `storage:mybatis` (현재 batch persistence는 MyBatis 기준)
- **Spring Boot**: `batch`, `data-jpa` (필요 시)

**build.gradle 예시**:
```groovy
dependencies {
    implementation project(':core:domain')
    implementation project(':support:util')
    implementation project(':support:logging')
    
    runtimeOnly project(':storage:mybatis')
    
    implementation 'org.springframework.boot:spring-boot-starter-batch'
    
    testImplementation 'org.jetbrains.kotlin:kotlin-test-junit5'
}
```

---

### 5. client:storage-file
**책임**: 로컬 파일 저장 어댑터. FileStoragePort 포트 구현.

**패키지 구조**:
```
cc.midolog.storage.file
├── adapter
│   └── FileStorageAdapter.kt    # FileStoragePort 구현
└── config
    └── FileStorageConfig.kt
```

**주요 의존성**:
- `implementation`: `core:domain`

**build.gradle 예시**:
```groovy
dependencies {
    implementation project(':core:domain')
    
    testImplementation 'org.jetbrains.kotlin:kotlin-test-junit5'
}
```

---

### 6. storage:mybatis
**책임**: MyBatis + PostgreSQL 저장소 구현. *RepositoryAdapter + *Mapper + SQL 매핑 (resources/mapper/*.xml).

**패키지 구조**:
```
cc.midolog.storage.<context>
├── adapter
│   └── *RepositoryAdapter.kt    # *RepositoryPort 구현
├── mapper
│   └── *Mapper.kt               # MyBatis Mapper 인터페이스
└── entity
    └── *Entity.kt               # 저장소 전용 엔티티

resources/
└── mapper/
    └── *Mapper.xml              # SQL 매핑 파일
```

**예시**:
```
cc.midolog.storage.user
├── adapter
│   └── UserRepositoryAdapter.kt
├── mapper
│   └── UserMapper.kt
└── entity
    └── UserEntity.kt

resources/mapper/
└── UserMapper.xml
```

**주요 의존성**:
- `implementation`: `core:domain`, `support:util`
- **MyBatis**: `mybatis-spring-boot-starter:3.x`
- **Database**: `org.postgresql:postgresql:42.x`

**build.gradle 예시**:
```groovy
dependencies {
    implementation project(':core:domain')
    implementation project(':support:util')
    
    implementation 'org.mybatis.spring.boot:mybatis-spring-boot-starter:4.0.1'
    runtimeOnly 'org.postgresql:postgresql'
    
    testImplementation 'org.jetbrains.kotlin:kotlin-test-junit5'
}
```

---

### 7. storage:jpa
**책임**: Spring Data JPA + PostgreSQL 저장소 구현. JPA entity, repository, mapper, adapter를 domain 밖에 격리한다.
도메인은 어노테이션 없는 plain data class로 유지하고, `storage:jpa/build.gradle`의 DSL에서 JPA 생성 대상을 선언한다.

**패키지 구조**:
```
cc.midolog.storage.jpa.<context>
├── *JpaEntity.kt              # generated: JPA 저장소 전용 entity
├── *JpaRepository.kt          # generated: Spring Data repository
├── *JpaMapper.kt              # generated: domain model <-> JPA entity 변환
└── Jpa*RepositoryAdapter.kt   # hand-written: *RepositoryPort 구현
```

**현재 sample/user 구조**:
```
cc.midolog.storage.jpa.sample
└── JpaSampleRepositoryAdapter.kt

cc.midolog.storage.jpa.user
└── JpaUserRepositoryAdapter.kt

cc.midolog.storage.jpa.config
└── JpaStorageConfig.kt
```

`SampleJpaEntity`, `UserJpaEntity` 같은 entity/repository/mapper 타입은 `generateJpaDslSources` task가 build directory에 생성한다.

**주요 의존성**:
- `implementation`: `core:domain`, `support:util`
- **JPA**: `spring-boot-starter-data-jpa`
- **Database**: `org.postgresql:postgresql`
- **Test**: `spring-boot-starter-data-jpa-test`, `com.h2database:h2`

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

    entity('cc.midolog.sample.model.RelationChild') {
        table = 'relation_child'
        id = 'id'

        field('parentId') {
            relation = 'parent'
        }
        relation('parent') {
            type = 'manyToOne'
            target = 'cc.midolog.sample.model.RelationParent'
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
    runtimeOnly 'org.postgresql:postgresql'

    testImplementation 'org.springframework.boot:spring-boot-starter-data-jpa-test'
    testRuntimeOnly 'com.h2database:h2'
}
```

**Profile 원칙**:
- `JpaSampleRepositoryAdapter`, `JpaUserRepositoryAdapter`와 JPA repository scan은 `jpa` profile에서만 활성화한다.
- 운영 실행에서는 `mybatis`와 `jpa` persistence profile을 동시에 켜지 않는다.
- JPA `@Entity`, `@Table`, Spring Data repository는 `storage:jpa` 내부에만 둔다.
- domain model에 필드를 추가하면 생성 task가 JPA entity/mapper 필드를 따라 생성한다. table/id/field/relation 변경은 typed `jpaDsl { ... }` 선언에서 관리한다.
- `storage:jpa`는 `build-logic`의 `cc.midolog.jpa-dsl` 내부 Gradle plugin을 적용한다.
- `storage/jpa/build.gradle`은 typed DSL 선언과 storage dependency만 관리한다. generated source directory, sourceSets, task wiring, parser, validator, renderer는 `build-logic` plugin이 소유한다.
- pluginization Phase 2에서 typed DSL/validator/renderer ownership은 plugin으로 이동했다. Phase 3는 task/sourceSet wiring, external build directory 검증, DSL recipe, decision log/handoff 정리로 닫는다.
- scalar DSL은 `column`, `nullable`, `enumStrategy = 'STRING'`, `storageType` + `converter`를 지원한다.
- value-object converter는 `toStorage(domainType): storageType`, `toDomain(storageType): domainType` 함수를 제공한다.
- relation DSL은 `manyToOne`, `oneToMany`, `target`, `sourceField`, `joinColumn`, `referencedColumn`, `mappedBy`를 지원한다.
- relation fetch는 `FetchType.LAZY`로 생성한다.
- mapper는 deep graph persistence를 자동 수행하지 않는다. `oneToMany`는 `toDomain: 'emptyList'`처럼 explicit shallow policy로 lazy proxy 접근과 무한 재귀를 피한다.
- unsupported DSL 선언은 generated Kotlin compile error가 아니라 generator validation `GradleException`으로 실패해야 한다. 메시지는 domain class와 field/relation 이름을 포함해야 한다.
- generated source는 Gradle build directory 아래에만 생성된다. `storage/jpa/src/main/kotlin`에는 adapter/config/converter 같은 hand-written persistence code만 둔다.
- 실제 PostgreSQL driver/dialect confidence가 필요하면 Docker PostgreSQL을 띄우고 `DB_URL=jdbc:postgresql://localhost:<port>/backend ./gradlew :storage:jpa:livePostgresTest`를 실행한다. 일반 `test` task는 `live-postgres` tag를 제외한다.
- runtime schema는 `core:application/src/main/resources/db/migration`의 Flyway migration이 소유한다. domain/DSL 변경으로 generated JPA table이 바뀌면 migration도 같은 변경 단위에서 갱신한다.
- 현재 relation DSL은 simple parent-child relation만 지원한다. many-to-many, cascade remove, orphan removal, arbitrary deep graph persistence는 범위 밖이다.
- public Gradle plugin publishing은 현재 범위가 아니다. 여러 repository에서 재사용하거나 binary compatibility가 필요해질 때 별도 plan으로 승격한다.

### 8. support:util
**책임**: 순수 Kotlin 유틸. IdGenerator, 확장함수, 공통 헬퍼.

**패키지 구조**:
```
cc.midolog.util
├── id
│   └── IdGenerator.kt           # UUID, snowflake 등
├── extension
│   └── StringExtension.kt        # 문자열 확장함수 등
└── helper
    └── *Helper.kt               # 기타 유틸
```

**예시**:
```
cc.midolog.util.id.UuidGenerator
cc.midolog.util.extension.stringExtensions
cc.midolog.util.helper.DateHelper
```

**주요 의존성**:
- 없음 (순수 Kotlin, 외부 의존 없음)

**build.gradle 예시**:
```groovy
dependencies {
    testImplementation 'org.jetbrains.kotlin:kotlin-test-junit5'
}
```

---

### 9. support:logging
**책임**: 로깅 설정. logback-classic, logback-spring.xml을 통한 통일된 로깅 프로필.

**패키지 구조**:
```
resources/
├── logback-spring.xml           # Spring 프로필별 로깅 설정
├── logback-spring-dev.xml       # 개발 환경
└── logback-spring-prod.xml      # 운영 환경
```

**주요 의존성**:
- **Spring Boot**: `spring-boot-starter-logging` (자동 포함)
- **Logback**: `logback-classic`, `logback-spring`
- **SLF4J**: `slf4j-api` (spring-boot-starter-logging에 포함)

**build.gradle 예시**:
```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-logging'
    
    // runtime 사용 모듈에서 의존할 때:
    // runtimeOnly project(':support:logging')
}
```

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
core:application ──→ core:domain
    ↓                    ↑
core:gateway      client:* ──────┐
                  storage:* ─────┤
                  support:* ─────┘
    ↓
core:batch
```

**핵심 규칙**:
1. **상향식 의존**: 상위 계층(application, gateway, batch)은 하위 계층(domain, client, storage, support)에 의존.
2. **역전 원칙**: domain은 client/storage에 의존하지 않음. 대신 client/storage가 domain의 포트(인터페이스)를 구현.
3. **어댑터 주입**: application에서 client, storage는 `runtimeOnly`로 선언. Spring이 런타임에 자동 와이어링.
4. **공유 모듈**: support:util, support:logging은 모든 모듈에서 의존 가능 (단, 도메인 순수성 훼손 금지).
5. **경계 존중**: 모듈 간 직접 import 금지. 공개된 인터페이스(포트)만 사용.

---

## 설정 파일

### settings.gradle
```groovy
rootProject.name = "backend-init"

// Core modules
include 'core:domain'
include 'core:application'
include 'core:gateway'
include 'core:batch'

// Client adapters
include 'client:storage-file'

// Storage
include 'storage:mybatis'
include 'storage:jpa'

// Support
include 'support:util'
include 'support:logging'
```

### 빌드 순서
1. `support:util` → `support:logging` (의존도 없음)
2. `core:domain` (support만 의존)
3. `client:storage-file`, `storage:mybatis`, `storage:jpa` (domain 의존)
4. `core:application`, `core:gateway`, `core:batch` (위 모두 의존 가능)

Gradle은 자동으로 의존도를 계산하여 올바른 순서로 빌드합니다.

---

## 관련 문서

- [./ARCHITECTURE.md](./ARCHITECTURE.md) — 전체 아키텍처, 통신 흐름, 배포
- [./GATEWAY.md](./GATEWAY.md) — 게이트웨이 상세 가이드 (필터, 라우팅, 모니터링)
- [./FUTURE.md](./FUTURE.md) — 향후 확장 계획 (Config Server, 마이크로서비스 분리 등)

---

**작성일**: 2026-06-15  
**프로젝트**: backend-init (Spring Boot 4 + Kotlin 2.2.x)
