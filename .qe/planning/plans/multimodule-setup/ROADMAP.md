# Roadmap — multimodule-setup

> backend-init: Spring Boot 4 + Kotlin 헥사고날 멀티모듈 백엔드
> rootProject `backend-init` · group `cc.midolog` · base package `cc.midolog`
> 흐름: **문서부터 → 골격 구현 → README 연동**

## 참고 아키텍처 요약 (happ-api)
- Gradle 멀티모듈 · Spring Boot 4.0.6 · Kotlin 2.2.21 · Java 17 · WebFlux + 코루틴
- 헥사고날(Ports & Adapters):
  - `core:domain` — 순수 Kotlin, model + port 인터페이스 (support:util만 의존)
  - `core:application` — web(controller) + business(service/support reader·writer) + common(config/security). domain·support는 implementation, client·storage adapter는 **runtimeOnly** 주입
  - `core:gateway` — WebFilter(RequestId/JWT/RateLimit) + RouteConfig + ProxyHandler(WebClient). support:logging만 의존
  - `core:batch` — 배치 부트
  - `client:*` — 외부 연동 adapter (domain port 구현)
  - `storage:mybatis` — 영속 adapter (repository port 구현)
  - `support:util`(순수), `support:logging`(logback)
- 게이트웨이 분기: `/api/**`→application(8081), `/batch/**`→batch(8082), `X-Request-Id` 생성·전파

## Phase 1 — 구조 설계 문서화 (ACTIVE)
**Goal**: 참고 분석 결과를 우리 프로젝트 기준 설계 문서로 확정하고 README 골격을 만든다. (검증: docs/ 문서 + README가 존재하고 모듈/의존/게이트웨이 설계가 명세됨)
**Requirements**: FR-01, FR-05, FR-07, FR-08, FR-09

### Wave 1 — 설계 문서 작성 (병렬)
- docs/ARCHITECTURE.md — 헥사고날 레이어, 의존 방향, 모듈 다이어그램
- docs/MODULE_GUIDE.md — 모듈별 책임/패키지 구조/build.gradle 의존 표
- docs/GATEWAY.md — 라우팅·프록시·트랜잭션 ID(X-Request-Id)·향후 요청 확인 화면 설계
- docs/FUTURE.md — 다중 인스턴스(수평 확장)/config 서버 설계(미구현, 설계만)

### Wave 2 — README 골격 (Wave 1 의존)
- README.md — 개요 + 모듈 표 + 빌드/실행 + docs 링크(상대경로)

## Phase 2 — 멀티모듈 골격 구현
**Goal**: `./gradlew build`가 통과하는 헥사고날 멀티모듈 골격을 만든다. (검증: 빌드 성공 + 모든 모듈/부트 클래스 존재)
**Requirements**: FR-02, FR-03, FR-06, NFR-01, NFR-02

### Wave 1 — 루트 빌드 설정
- settings.gradle (전체 모듈 include)
- root build.gradle (plugins apply false, allprojects/subprojects)
- gradle wrapper + .gitignore + .env.example

### Wave 2 — 모듈별 골격 (병렬)
- support:util / support:logging
- core:domain (model/port 패키지 골격)
- client:* / storage:mybatis (adapter 패키지 골격)
- core:application / core:gateway / core:batch (부트 클래스 + 기본 yml)

### Wave 3 — README 연동
- README의 파일/문서 링크가 실제 생성 파일과 연동되도록 갱신

## Phase 3 — 게이트웨이 핵심 (트랜잭션 ID + 라우팅)
**Goal**: 게이트웨이가 요청을 분기하고 트랜잭션 ID를 전파하며 로깅(MDC)에 연동된다. (검증: /api·/batch 프록시 동작 + X-Request-Id 응답/로그 확인)
**Requirements**: FR-04, NFR-03, NFR-04

### Wave 1 — 게이트웨이 필터/라우팅
- RequestIdFilter (트랜잭션 ID 생성·전파)
- RouteConfig (path-prefix 라우팅)
- ProxyHandler (WebClient 프록시)

### Wave 2 — 로깅 연동/통합 확인
- MDC 로깅 연동 (트랜잭션 ID ↔ 로그)
- 게이트웨이↔비즈니스 서버 분기 통합 확인
- (향후) 요청 확인 화면 / config 서버 / 다중 인스턴스 — 별도 plan으로 승격

## Wave Model 요약
| Phase | Waves | 핵심 산출물 |
|-------|-------|------------|
| Phase 1 | 2 | docs/ARCHITECTURE·MODULE_GUIDE·GATEWAY·FUTURE, README |
| Phase 2 | 3 | settings/build.gradle, 9개 모듈 골격, README 연동 |
| Phase 3 | 2 | RequestIdFilter, RouteConfig, ProxyHandler, MDC 로깅 |
