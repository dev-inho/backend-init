# CLAUDE.md

## Project Overview
- **Name**: backend-init
- **Description**: Spring Cloud Gateway 기반 Spring Boot 4 + Kotlin 멀티모듈 백엔드. 게이트웨이가 요청을 받아 서버로 분기하고, 트랜잭션 ID를 Header로 관리한다.
- **Monorepo Tool**: Gradle (multi-module)

## Tech Stack
- **언어**: Kotlin 2.2.x
- **프레임워크**: Spring Boot 4.0.x
- **빌드**: Gradle multi-module (Wrapper), Java Toolchain 17
- **인프라(예정)**: Spring Cloud Gateway, Spring Cloud Config(검토 중)
- **저장소**: mavenCentral + Spring milestone

## Packages

| Package | Path | Description | Dependencies |
|---------|------|-------------|-------------|
| application | core/application | 비즈니스 애플리케이션 부트스트랩 | domain, client, storage, support |
| gateway | core/gateway | 요청 수신·라우팅·트랜잭션 ID·모니터링 화면 | domain, support |
| domain | core/domain | 도메인 모델/규칙 | support |
| batch | core/batch | 배치/스케줄 작업 | domain, storage, support |
| client | client/* | 외부 시스템 연동 (ai, storage-file) | support |
| storage | storage/mybatis | 영속성(데이터 접근) | domain, support |
| support | support/* | 횡단 관심사 (util, logging) | - |

## Build & Run
```bash
# Build all packages (in dependency order)
./gradlew build

# Run specific package
./gradlew :core:gateway:bootRun
./gradlew :core:application:bootRun

# Run tests
./gradlew test
```

## Build Order
1. support (util, logging)
2. domain, client, storage
3. application, gateway, batch

## Shared Dependencies
| Dependency | Version | Used By |
|-----------|---------|---------|
| kotlin-reflect | 2.2.x | all modules |
| kotlin-test-junit5 | 2.2.x | test (all) |

## Project Structure
```
backend-init/
├── settings.gradle
├── build.gradle
├── core/
│   ├── application/
│   ├── gateway/
│   ├── domain/
│   └── batch/
├── client/
│   ├── ai/
│   └── storage-file/
├── storage/
│   └── mybatis/
└── support/
    ├── util/
    └── logging/
```

## Constraints
- Do not modify files outside the project scope
- Confirm before destructive actions
- Follow existing code conventions
- 모듈 경계 준수 (공유 모듈 경유 없는 교차 import 금지)
- support 등 공유 모듈 변경 시 의존 모듈 전체 테스트
- 게이트웨이는 트랜잭션 ID를 생성·전파하며, 비즈니스 서버는 무상태(수평 확장 가능)로 설계

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
- **작업 이력 및 상태**: `.qe/TASK_LOG.md` 참조
