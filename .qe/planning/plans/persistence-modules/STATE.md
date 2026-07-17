# State — persistence-modules

- **Active Phase**: Complete
- **Plan Slug**: persistence-modules
- **Updated**: 2026-07-13

## Phase Progress

> ledger.mjs가 현재 저장소에 없어 Qplan에서 초기 수동 생성. 후속 실행 단계에서 ledger 도입 시 자동 렌더링으로 전환한다.

### Phase 1 — Persistence 경계 정리
- [x] G001 [Wave 1 — 의존성 경계 점검] `core:domain`에 Spring/JPA/MyBatis annotation 또는 storage 의존이 없는지 검증
- [x] G002 [Wave 1 — 의존성 경계 점검] `core:application`이 port만 바라보도록 compile/runtime 의존성 정리
- [x] G003 [Wave 1 — 의존성 경계 점검] storage adapter bean이 profile/condition 없이 중복 등록될 위험 점검
- [x] G004 [Wave 2 — MyBatis adapter 정리] `storage:mybatis` adapter에 `@Profile("mybatis")` 또는 조건부 설정 적용
- [x] G005 [Wave 2 — MyBatis adapter 정리] MyBatis datasource/mapper 설정 위치 정리
- [x] G006 [Wave 2 — MyBatis adapter 정리] MyBatis 활성화 시 sample repository port가 정상 주입되는지 테스트

### Phase 2 — JPA adapter 모듈 추가
- [x] G007 [Wave 1 — 모듈/Gradle 구성] `settings.gradle`에 `storage:jpa` 추가
- [x] G008 [Wave 1 — 모듈/Gradle 구성] `storage:jpa/build.gradle`에 Spring Data JPA, PostgreSQL/H2 test 의존성 추가
- [x] G009 [Wave 1 — 모듈/Gradle 구성] `core:application` runtime 의존 또는 profile별 wiring 전략 확정
- [x] G010 [Wave 2 — JPA 구현] `SampleJpaEntity`, `SampleJpaRepository`, `SampleJpaMapper` 작성
- [x] G011 [Wave 2 — JPA 구현] `JpaSampleRepositoryAdapter : SampleRepositoryPort` 구현
- [x] G012 [Wave 2 — JPA 구현] blocking persistence 경계 문서화 및 dispatcher/transaction 정책 적용
- [x] G013 [Wave 3 — Contract 검증] MyBatis/JPA adapter가 같은 port contract를 만족하는 테스트 작성
- [x] G014 [Wave 3 — Contract 검증] `mybatis`/`jpa` profile별 application context 충돌 검증

### Phase 3 — 문서화와 선택 UX 정리
- [x] G015 [Wave 1 — 문서 업데이트] README에 persistence module matrix 추가
- [x] G016 [Wave 1 — 문서 업데이트] docs/ARCHITECTURE 또는 MODULE_GUIDE에 domain/entity 분리 원칙 추가
- [x] G017 [Wave 1 — 문서 업데이트] JPA/MyBatis 선택 예시 명령과 profile 표기
- [x] G018 [Wave 2 — 최종 회귀 검증] `./gradlew clean test`
- [x] G019 [Wave 2 — 최종 회귀 검증] `mybatis` profile wiring smoke test
- [x] G020 [Wave 2 — 최종 회귀 검증] `jpa` profile wiring smoke test
- [x] G021 [Wave 2 — 최종 회귀 검증] README의 “검증된 범위/진행 중 범위” 최신화
