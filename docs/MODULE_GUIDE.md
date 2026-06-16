# 모듈 가이드 (Module Guide)

Spring Boot 4 + Kotlin 기반 헥사고날 멀티모듈 아키텍처의 8개 모듈별 책임, 패키지 구조, 의존성 정의 가이드.

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
| **Support Util** | `support:util` | 순수 Kotlin 유틸 (IdGenerator, 확장함수 등) | - |
| **Support Logging** | `support:logging` | 로깅 설정 (logback-classic, logback-spring.xml) | `spring-boot-dependencies` |

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
- `runtimeOnly`: `client:storage-file`, `storage:mybatis` (어댑터는 런타임에만 주입)
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
│   ├── RequestIdFilter.kt
│   ├── JwtAuthFilter.kt
│   └── RateLimitFilter.kt
├── config
│   └── RouteConfig.kt
├── handler
│   └── ProxyHandler.kt
└── GatewayApplication.kt (부트 클래스)
```

**부트 클래스**: `cc.midolog.GatewayApplication`

**주요 의존성**:
- `implementation`: `support:logging`
- **Spring Cloud**: `spring-cloud-starter-gateway`, `spring-cloud-starter-config` (검토 중)
- **Spring Boot**: `webflux`
- **라이브러리**: `jjwt:0.12.6`, `spring-boot-starter-data-redis-reactive`

**build.gradle 예시**:
```groovy
dependencies {
    implementation project(':support:logging')
    
    implementation 'org.springframework.cloud:spring-cloud-starter-gateway'
    implementation 'org.springframework.boot:spring-boot-starter-webflux'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis-reactive'
    implementation 'io.jsonwebtoken:jjwt-api:0.12.6'
    runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.6'
    runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.6'
    
    testImplementation 'org.jetbrains.kotlin:kotlin-test-junit5'
}
```

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
- `runtimeOnly`: `storage:mybatis`
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

### 7. support:util
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

### 8. support:logging
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

// Support
include 'support:util'
include 'support:logging'
```

### 빌드 순서
1. `support:util` → `support:logging` (의존도 없음)
2. `core:domain` (support만 의존)
3. `client:storage-file`, `storage:mybatis` (domain 의존)
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
