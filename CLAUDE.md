# CLAUDE.md

## Project Overview
- **Name**: backend-init
- **Description**: WebFlux `router {}` 기반 자체 게이트웨이와 Spring Boot 4 + Kotlin 멀티모듈 백엔드. 게이트웨이가 요청을 받아 서버로 분기하고, 트랜잭션 ID를 Header로 관리한다.
- **Monorepo Tool**: Gradle (multi-module)

## Tech Stack
- **언어**: Kotlin 2.2.x
- **프레임워크**: Spring Boot 4.0.x
- **빌드**: Gradle multi-module (Wrapper), Java Toolchain 17
- **게이트웨이**: WebFlux `router {}` 기반 자체 구현 (외부 게이트웨이 프레임워크 비의존)
- **저장소**: mavenCentral + Spring milestone

## Packages

| Package | Path | Description | Dependencies |
|---------|------|-------------|-------------|
| core:application | core/application | 비즈니스 애플리케이션 부트스트랩 | core:domain, support:util, support:logging, support:web, support:jwt (runtimeOnly: client:storage-file, storage:mybatis, storage:jpa, storage:file-local) |
| core:batch | core/batch | 배치/스케줄 작업 | support:logging |
| gateway:core | gateway/core | 게이트웨이 코어 필터, 라우팅, 프록시, 관측성 | support:logging, support:util, support:web, support:jwt |
| gateway:autoconfigure | gateway/autoconfigure | gateway.mode 기반 자동 설정 및 조건부 빈 등록 | gateway:core |
| gateway:starter | gateway/starter | 게이트웨이 탑재용 스타터 라이브러리 (core + autoconfigure) | gateway:core (api), gateway:autoconfigure (api) |
| gateway:app | gateway/app | 독립 실행형 Spring Boot 게이트웨이 서비스 (포트 8080) | gateway:starter, support:logging (test: support:web) |
| core:domain | core/domain | 도메인 모델 및 포트 인터페이스 | - |
| client:storage-file | client/storage-file | 외부 로컬 파일 저장소 연동 | core:domain |
| storage:mybatis | storage/mybatis | MyBatis 데이터 접근 계층 | core:domain, support:util |
| storage:jpa | storage/jpa | Spring Data JPA 데이터 접근 계층 | core:domain, support:util |
| storage:file-local | storage/file-local | 로컬 파일 시스템 저장 어댑터 및 자동 설정 | core:domain, support:util |
| support:util | support/util | 공유 유틸리티 및 순수 Kotlin 헬퍼 | - |
| support:logging | support/logging | 통합 로깅 및 마스킹 | support:util |
| support:web | support/web | 공통 WebFlux 필터, API 응답, 예외 처리 | support:logging, support:util |
| support:jwt | support/jwt | JWT 토큰 발급 및 검증 코덱 | support:util |
| examples:minimal-app | examples/minimal-app | 최소 소비자 레퍼런스 앱 (스토리지 스타터 자동 구성 및 포트 확장 실증) | core:domain, support:web, storage:jpa, storage:mybatis |

> 모듈별 상세 책임과 사용 가이드는 [docs/MODULE_GUIDE.md](docs/MODULE_GUIDE.md)를 참조하세요.

## Build & Run
```bash
# Build all packages (in dependency order)
./gradlew build

# Run standalone gateway (port 8080, requires JWT_SECRET >= 32 bytes)
export JWT_SECRET="$(openssl rand -base64 48)"
SPRING_PROFILES_ACTIVE=local ./gradlew :gateway:app:bootRun

# Run business application (port 8081)
SPRING_PROFILES_ACTIVE=local STORAGE_PERSISTENCE_PROVIDER=mybatis ./gradlew :core:application:bootRun

# Run tests
./gradlew test
```

## Build Order
1. `support:util`
2. `core:domain`, `support:logging`, `support:jwt`
3. `support:web`, `client:storage-file`, `storage:mybatis`, `storage:jpa`, `storage:file-local`
4. `gateway:core`
5. `gateway:autoconfigure`
6. `gateway:starter`
7. `core:batch`, `gateway:app`, `core:application`, `examples:minimal-app`

## Shared Dependencies
| Dependency | Version | Used By |
|-----------|---------|---------|
| kotlin-reflect | 2.2.x | all modules |
| kotlin-test-junit5 | 2.2.x | test (all) |

## Project Structure
```
backend-init/
├── build-logic/
├── docker/
│   └── postgres/
├── docs/
├── settings.gradle
├── build.gradle
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
│   ├── jpa/
│   └── mybatis/
├── support/
│   ├── jwt/
│   ├── logging/
│   ├── util/
│   └── web/
└── examples/
    └── minimal-app/
```

## Constraints
- Do not modify files outside the project scope
- Confirm before destructive actions
- Follow existing code conventions
- 모듈 경계 준수 (공유 모듈 경유 없는 교차 import 금지)
- support 등 공유 모듈 변경 시 의존 모듈 전체 테스트
- 게이트웨이는 트랜잭션 ID를 생성·전파하며, 비즈니스 서버는 무상태(수평 확장 가능)로 설계
- **도메인 순수성 유지**: `core:domain`은 프레임워크 비의존 순수 Kotlin으로 유지하며 Spring/JPA/MyBatis 어노테이션 및 패키지 토큰을 일절 포함하지 않는다 (`DomainPurityTest` 가드)
- **게이트웨이 설정 의존 방향**: `cc.midolog.gateway.config` 패키지는 하위 프록시/라우트 패키지(`gateway.proxy`, `gateway.route` 등)를 역방향으로 참조하지 않는다 (`GatewayPackageDependencyTest` 가드)
- **컨트롤러 응답 DTO 분리**: `cc.midolog.web`의 컨트롤러는 도메인 엔티티를 ApiResponse에 직접 반환하지 않고 전용 응답 DTO(`*Response`)를 사용한다 (`ControllerResponseTypeTest` 가드)
- **파일 저장소 빈 공존 및 호환 가드**: 자동 설정 빈 `fileLocalStorageAdapter`(`storage:file-local`)와 레거시 빈 `localFileStorageAdapter`(`client:storage-file`)가 빈 이름 충돌 없이 공존하며 두 `FileStoragePort`가 정상 등록된다 (`FileStorageIntegrationTest` 가드)
- **KDoc 문체**: 한국어 산문형으로 목적과 제약/이유를 서술하며 Javadoc 태그(`@param` 등) 및 코드 재서술을 금지한다 (상세: [docs/CODE_CONVENTIONS.md](docs/CODE_CONVENTIONS.md) 참조)

## Goals
- 게이트웨이 단일 진입점 + 서버 분기 라우팅 구성
- 트랜잭션 ID Header 부여/전파 및 게이트웨이 요청 확인 화면
- Config 서버 도입 검토, 비즈니스 서버 다중 인스턴스 대응

## QE 툴킷

이 프로젝트에 QE 스킬셋이 로드되어 있습니다. 손에 익으면 반복 작업이 눈에 띄게 줄어듭니다:

- **구현/수정** → `/Qgenerate-spec` + `/Qrun-task` — 스펙 먼저 확정하면 back-and-forth 없이 한 번에 깔끔하게
- **커밋** → `/Qcommit` — AI 흔적 없는 자연스러운 커밋 메시지
- **디버깅** → `/Qsystematic-debugging` — 가설 기반으로 원인을 좁혀가는 방식, 막히는 시간 단축
- **버전 관리** → `/Mbump` — 모든 매니페스트 한 번에 원자적 업데이트

> `QE_CONVENTIONS.md`에 카테고리별 스킬 목록과 워크플로우 규칙이 정리되어 있다.
> 작업을 수행하기 전에 이 문서를 참조하여 해당 작업에 맞는 스킬이 있는지 확인하고, 있으면 직접 구현 대신 스킬을 우선 사용할 것.

## Task Log
- 작업 이력은 git log와 `docs/` 문서를 참조한다.
