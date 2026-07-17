# SUMMARY_e0aff360 — JPA adapter 모듈 추가

## Implementation
- `storage:jpa` Gradle module added.
- JPA entity, repository, mapper, adapter, and profile-scoped config added under `storage:jpa`.
- `core:application` now has runtime/test dependency on `storage:jpa` without importing JPA implementation from main source.
- JPA repository auto-scan is disabled by default and enabled under the `jpa` profile.
- H2-backed JPA adapter test, profile wiring test, and MyBatis/JPA contract-style test added.

## Verification Run
- RED: `:storage:jpa` task was missing before implementation.
- GREEN: targeted JPA/profile/contract tests passed.
- Regression: `./gradlew clean test` passed.
- Static checks passed for domain purity, application main import boundary, destructive main DDL settings, and placeholder stubs.

## Notes for Verify
- Existing default profile remains `local,mybatis`.
- JPA test uses `spring.jpa.hibernate.ddl-auto=create-drop` only in test properties.
- Main configuration does not add destructive JPA DDL automation.
- Verify-stage fix: moved JPA transaction execution inside the `Dispatchers.IO` block through `TransactionOperations`, avoiding a misleading suspend-method `@Transactional` boundary.
- Final verification: targeted tests and `./gradlew clean test` passed after the transaction-boundary fix.
