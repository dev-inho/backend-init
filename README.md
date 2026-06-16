# backend-init

Spring Boot 4 + Kotlin 헥사고날 멀티모듈 백엔드 — 게이트웨이를 통한 요청 분기 및 트랜잭션 ID 관리

---

## 개요

**backend-init**은 다음을 목표로 하는 마이크로서비스 기반 백엔드 플랫폼입니다:

- **게이트웨이 분기**: 클라이언트 요청을 게이트웨이(`core:gateway`)에서 받아 비즈니스 서버(`core:application`)로 라우팅
- **헥사고날 아키텍처**: Ports & Adapters 패턴으로 비즈니스 로직과 인프라를 분리
- **트랜잭션 ID 관리**: 모든 요청에 `X-Request-Id`를 부여하여 분산 환경에서 추적 가능
- **멀티모듈 구조**: 8개 모듈로 기능을 명확히 분리
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
| **데이터** | MyBatis (Reactive) |
| **패턴** | 헥사고날(Ports & Adapters) |

---

## 모듈 구조

프로젝트는 4개 범주(core, client, storage, support)의 8개 모듈로 구성됩니다.

### 모듈 개요

| 모듈 | 책임 |
|------|------|
| **core:application** | 비즈니스 로직 및 도메인 서비스 구현 (포트 8081) |
| **core:gateway** | API 게이트웨이, 요청 라우팅, 트랜잭션 ID 관리 (포트 8080) |
| **core:domain** | 도메인 모델 및 엔티티, 공유 비즈니스 규칙 |
| **core:batch** | 배치 작업 및 스케줄링 (포트 8082) |
| **client:storage-file** | 파일 저장소 클라이언트 (Ports 적응자) |
| **storage:mybatis** | MyBatis 데이터 접근 계층 (Adapters) |
| **support:util** | 공유 유틸리티 및 헬퍼 함수 |
| **support:logging** | 통합 로깅 및 모니터링 |

### 디렉토리 구조

```
backend-init/
├── settings.gradle          # Gradle 멀티모듈 설정
├── build.gradle             # 루트 빌드 설정
├── docs/                    # 설계 문서
│   ├── ARCHITECTURE.md
│   ├── MODULE_GUIDE.md
│   ├── GATEWAY.md
│   └── FUTURE.md
├── core/
│   ├── application/
│   ├── gateway/
│   ├── domain/
│   └── batch/
├── client/
│   └── storage-file/
├── storage/
│   └── mybatis/
└── support/
    ├── util/
    └── logging/
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

### 개별 서비스 실행

```bash
# API 게이트웨이 (포트 8080)
./gradlew :core:gateway:bootRun

# 비즈니스 서버 (포트 8081)
./gradlew :core:application:bootRun

# 배치 서버 (포트 8082)
./gradlew :core:batch:bootRun
```

### 포트 정보

- **Gateway**: 8080 — 클라이언트 요청 수신, 라우팅
- **Application**: 8081 — 비즈니스 로직 처리
- **Batch**: 8082 — 배치 작업 실행

---

## 문서 (Documentation)

설계 및 운영 관련 상세 문서는 `docs/` 디렉토리를 참조하세요:

- **[아키텍처](docs/ARCHITECTURE.md)** — 헥사고날 패턴, 의존성 방향, 모듈 간 상호작용 다이어그램
- **[모듈 가이드](docs/MODULE_GUIDE.md)** — 8개 모듈의 상세 가이드, 책임, 사용 방법
- **[게이트웨이](docs/GATEWAY.md)** — 게이트웨이 아키텍처, 트랜잭션 ID(X-Request-Id) 관리, 라우팅 규칙
- **[향후 설계](docs/FUTURE.md)** — 다중 인스턴스 지원, Config 서버, 요청 확인 화면 등 향후 계획

---

## 프로젝트 관리

- **QE 프레임워크**: `.qe/` 디렉토리 — 프로젝트 상태, 태스크, 체크리스트 관리
- **CLAUDE.md**: 개발 지침 및 규칙 (프로젝트 로컬)
- **QE_CONVENTIONS.md**: QE 프레임워크 컨벤션 및 표준

---

## 현재 상태

**Phase 1: 구조 설계 문서 완료**
- 헥사고날 아키텍처 설계 확정
- 8개 모듈 구조 및 책임 정의
- 4개 설계 문서 작성 완료 (ARCHITECTURE, MODULE_GUIDE, GATEWAY, FUTURE)

**Phase 2: 멀티모듈 골격 구현 예정**
- Gradle 멀티모듈 설정 및 의존성 정의
- 각 모듈 기본 구조 및 테스트 작성
- 게이트웨이와 비즈니스 서버 기본 구현

---

## 라이선스

[프로젝트 라이선스 정보 추가 예정]
