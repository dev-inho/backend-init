# State — multimodule-setup

- **Active Phase**: Phase 3 — 게이트웨이 핵심 (트랜잭션 ID + 라우팅)
- **Plan Slug**: multimodule-setup
- **Updated**: 2026-06-15

## Phase Progress

> 자동 생성 (ledger.mjs render-state) — 직접 수정 금지

### Phase 1 — 구조 설계 문서화 (ACTIVE)
- [x] G001 [Wave 1 — 설계 문서 작성 (병렬)] docs/ARCHITECTURE.md — 헥사고날 레이어, 의존 방향, 모듈 다이어그램
- [x] G002 [Wave 1 — 설계 문서 작성 (병렬)] docs/MODULE_GUIDE.md — 모듈별 책임/패키지 구조/build.gradle 의존 표
- [x] G003 [Wave 1 — 설계 문서 작성 (병렬)] docs/GATEWAY.md — 라우팅·프록시·트랜잭션 ID(X-Request-Id)·향후 요청 확인 화면 설계
- [x] G004 [Wave 1 — 설계 문서 작성 (병렬)] docs/FUTURE.md — 다중 인스턴스(수평 확장)/config 서버 설계(미구현, 설계만)
- [x] G005 [Wave 2 — README 골격 (Wave 1 의존)] README.md — 개요 + 모듈 표 + 빌드/실행 + docs 링크(상대경로)

### Phase 2 — 멀티모듈 골격 구현
- [x] G006 [Wave 1 — 루트 빌드 설정] settings.gradle (전체 모듈 include)
- [x] G007 [Wave 1 — 루트 빌드 설정] root build.gradle (plugins apply false, allprojects/subprojects)
- [x] G008 [Wave 1 — 루트 빌드 설정] gradle wrapper + .gitignore + .env.example
- [x] G009 [Wave 2 — 모듈별 골격 (병렬)] support:util / support:logging
- [x] G010 [Wave 2 — 모듈별 골격 (병렬)] core:domain (model/port 패키지 골격)
- [x] G011 [Wave 2 — 모듈별 골격 (병렬)] client:* / storage:mybatis (adapter 패키지 골격)
- [x] G012 [Wave 2 — 모듈별 골격 (병렬)] core:application / core:gateway / core:batch (부트 클래스 + 기본 yml)
- [x] G013 [Wave 3 — README 연동] README의 파일/문서 링크가 실제 생성 파일과 연동되도록 갱신

### Phase 3 — 게이트웨이 핵심 (트랜잭션 ID + 라우팅)
- [x] G014 [Wave 1 — 게이트웨이 필터/라우팅] RequestIdFilter (트랜잭션 ID 생성·전파)
- [x] G015 [Wave 1 — 게이트웨이 필터/라우팅] RouteConfig (path-prefix 라우팅)
- [x] G016 [Wave 1 — 게이트웨이 필터/라우팅] ProxyHandler (WebClient 프록시)
- [x] G017 [Wave 2 — 로깅 연동/통합 확인] MDC 로깅 연동 (트랜잭션 ID ↔ 로그)
- [x] G018 [Wave 2 — 로깅 연동/통합 확인] 게이트웨이↔비즈니스 서버 분기 통합 확인
- [x] G019 [Wave 2 — 로깅 연동/통합 확인] (향후) 요청 확인 화면 / config 서버 / 다중 인스턴스 — 별도 plan으로 승격
