# Roadmap — persistence-modules

> persistence 구현을 브랜치가 아니라 모듈로 분리한다.
> 목표 구조: `core:domain` 순수 모델/포트 + `storage:mybatis` adapter + `storage:jpa` adapter.

## Phase 1 — Persistence 경계 정리
**Goal**: 도메인/애플리케이션/persistence 의존 경계를 확정하고 MyBatis adapter를 선택 가능한 구현으로 정리한다. (검증: domain에 persistence annotation 없음 + MyBatis profile 빌드/테스트 통과)
**Requirements**: FR-01, FR-02, FR-04, NFR-01, NFR-03, NFR-04

### Wave 1 — 의존성 경계 점검
- `core:domain`에 Spring/JPA/MyBatis annotation 또는 storage 의존이 없는지 검증
- `core:application`이 port만 바라보도록 compile/runtime 의존성 정리
- storage adapter bean이 profile/condition 없이 중복 등록될 위험 점검

### Wave 2 — MyBatis adapter 정리
- `storage:mybatis` adapter에 `@Profile("mybatis")` 또는 조건부 설정 적용
- MyBatis datasource/mapper 설정 위치 정리
- MyBatis 활성화 시 sample repository port가 정상 주입되는지 테스트

## Phase 2 — JPA adapter 모듈 추가
**Goal**: `storage:jpa` 모듈을 추가해 JPA entity/repository/adapter를 domain 밖에 격리한다. (검증: `jpa` profile 테스트 통과 + domain 순수성 유지)
**Requirements**: FR-03, FR-05, FR-06, NFR-01, NFR-02, NFR-04, NFR-05

### Wave 1 — 모듈/Gradle 구성
- `settings.gradle`에 `storage:jpa` 추가
- `storage:jpa/build.gradle`에 Spring Data JPA, PostgreSQL/H2 test 의존성 추가
- `core:application` runtime 의존 또는 profile별 wiring 전략 확정

### Wave 2 — JPA 구현
- `SampleJpaEntity`, `SampleJpaRepository`, `SampleJpaMapper` 작성
- `JpaSampleRepositoryAdapter : SampleRepositoryPort` 구현
- blocking persistence 경계 문서화 및 필요 시 coroutine dispatcher/transaction 정책 적용

### Wave 3 — Contract 검증
- MyBatis/JPA adapter가 같은 port contract를 만족하는 테스트 작성
- `mybatis`/`jpa` profile별 application context 충돌 검증

## Phase 3 — 문서화와 선택 UX 정리
**Goal**: 사용자가 persistence 구현을 명확히 선택하고 실행할 수 있게 문서/설정을 정리한다. (검증: README 명령과 profile 설명으로 재현 가능)
**Requirements**: FR-07, NFR-03, NFR-05

### Wave 1 — 문서 업데이트
- README에 persistence module matrix 추가
- docs/ARCHITECTURE 또는 MODULE_GUIDE에 domain/entity 분리 원칙 추가
- JPA/MyBatis 선택 예시 명령과 profile 표기

### Wave 2 — 최종 회귀 검증
- `./gradlew clean test`
- `mybatis` profile wiring smoke test
- `jpa` profile wiring smoke test
- README의 “검증된 범위/진행 중 범위” 최신화

## Wave Model 요약
| Phase | Waves | 핵심 산출물 |
|-------|-------|------------|
| Phase 1 | 2 | domain 순수성 점검, MyBatis adapter profile 정리 |
| Phase 2 | 3 | `storage:jpa` 모듈, JPA adapter, contract/profile 테스트 |
| Phase 3 | 2 | 문서화, profile별 실행 UX, 최종 회귀 검증 |
