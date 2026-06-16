# Requirements — multimodule-setup

> backend-init 멀티모듈 구조 셋업
> rootProject: `backend-init` · group: `cc.midolog` · base package: `cc.midolog`

## Functional Requirements

| ID | 우선순위 | 요구사항 |
|----|----------|----------|
| FR-01 | P0 | 참고 프로젝트(happ-api)를 상세 분석하여 아키텍처/모듈/의존관계 설계 문서를 작성한다. |
| FR-02 | P0 | 헥사고날(Ports & Adapters) 멀티모듈 골격을 Gradle로 구성한다 (core/client/storage/support 전체 세트). |
| FR-03 | P0 | 각 모듈이 컴파일 가능한 골격(부트 클래스, 패키지, build.gradle, 리소스)을 갖춘다. |
| FR-04 | P0 | 게이트웨이가 path-prefix로 비즈니스 서버를 분기하고 트랜잭션 ID(X-Request-Id)를 생성·전파한다. |
| FR-05 | P0 | README.md가 설계 문서/모듈 파일과 링크로 연동된다. |
| FR-06 | P1 | 의존 방향 규칙(domain 순수, application/batch→domain, gateway→support)을 빌드로 강제한다. |
| FR-07 | P2 | 게이트웨이 요청 확인 화면(모니터링) — 이번 마일스톤은 **설계 문서만**. |
| FR-08 | P2 | Config 서버(Spring Cloud Config) — 이번 마일스톤은 **설계 문서만**. |
| FR-09 | P2 | 비즈니스 서버 다중 인스턴스(수평 확장) — 이번 마일스톤은 **설계 문서만**. |

## Non-Functional Requirements

| ID | 우선순위 | 요구사항 |
|----|----------|----------|
| NFR-01 | P0 | Spring Boot 4.0.x · Kotlin 2.2.x · Java 17 · Gradle 멀티모듈. |
| NFR-02 | P0 | WebFlux(reactive) + 코루틴 스택. |
| NFR-03 | P1 | 비즈니스 서버는 무상태로 설계하여 다중 인스턴스 확장이 가능해야 한다. |
| NFR-04 | P1 | 트랜잭션 ID는 로깅(MDC)과 연동되어 추적 가능해야 한다. |

## Scope 경계
- **포함**: 구조 분석 문서, 멀티모듈 골격 구현, 게이트웨이 라우팅/트랜잭션 ID, README 연동.
- **이번 제외(문서 설계만)**: 요청 확인 화면, config 서버, 다중 인스턴스 운영, 비즈니스 도메인 로직(헥사고날 슬라이스 실구현).
