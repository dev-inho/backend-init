# SUMMARY_ece2ac36 — 문서화와 선택 UX 정리

## Result
- README를 9개 모듈 구조와 `storage:mybatis`/`storage:jpa` 병행 persistence 구조에 맞게 갱신했다.
- README에 persistence module matrix와 `local,mybatis` / `local,jpa` 실행 예시를 추가했다.
- `docs/ARCHITECTURE.md`에 domain/entity 분리 원칙과 MyBatis/JPA adapter profile 선택 원칙을 추가했다.
- `docs/MODULE_GUIDE.md`에 `storage:jpa` 모듈 섹션을 추가하고 application runtime adapter 의존성을 갱신했다.

## Verification
- `./gradlew :core:domain:test --tests 'cc.midolog.sample.DomainPurityTest' :core:application:test --tests 'cc.midolog.persistence.PersistenceProfileContextTest'` PASS
- `./gradlew clean test` PASS
- Link file existence check PASS: README/docs의 local markdown links target 존재
- Stale wording search PASS: `8개 모듈`, `MyBatis (Reactive)`, `구현 예정`, `운영 완료`, `실제 DB 검증`, main 문서의 파괴적 JPA DDL 예시 없음

## Residual Risks
- live PostgreSQL smoke test는 수행하지 않았다.
- 운영 DB migration 도구(Flyway/Liquibase)와 schema 전략은 이 계획 범위 밖이다.

## Next
- `persistence-modules` plan complete.
