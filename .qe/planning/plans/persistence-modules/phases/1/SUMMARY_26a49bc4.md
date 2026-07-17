# SUMMARY_26a49bc4 — Persistence 경계 정리

## Result
- Phase 1 완료.
- `core:domain`은 Spring/JPA/MyBatis annotation과 import 없이 Plain Kotlin/Java domain 경계를 유지한다.
- MyBatis 구현은 `storage:mybatis` adapter에 격리했고 `mybatis` profile에서만 repository bean이 등록된다.
- `SampleRepositoryAdapter.save`의 TODO stub을 제거하고 mapper `upsert`로 실제 저장 경로를 연결했다.

## Verification
- `./gradlew :core:domain:test --tests 'cc.midolog.sample.DomainPurityTest' :core:application:test --tests 'cc.midolog.persistence.MyBatisProfileContextTest' :storage:mybatis:test --tests 'cc.midolog.storage.sample.SampleRepositoryAdapterTest'` PASS
- `./gradlew clean test` PASS
- `rg` 경계 검사 PASS: domain persistence/framework token 없음, application main storage 구현 import 없음, TODO/FIXME/lorem ipsum 없음

## Residual Risks
- `SampleMapper.xml`의 `ON CONFLICT`는 PostgreSQL 방언이다. 실제 DB integration test는 Phase 2/3에서 보강해야 한다.
- 현재 기본 active profile은 `local,mybatis`다. JPA adapter 추가 후에는 profile 선택 UX와 중복 bean 방지 contract test를 확정해야 한다.
- JPA entity는 반드시 `storage:jpa` 내부에 두고 `core:domain` 모델에는 ORM annotation을 넣지 않는다.

## Next
- Phase 2: `storage:jpa` 모듈 추가 및 JPA adapter 구현.
