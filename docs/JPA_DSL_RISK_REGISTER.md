# JPA DSL Risk Register

이 문서는 `core:domain`을 plain Kotlin으로 유지하면서 `storage:jpa`에서 typed DSL 기반 generated JPA mapping을 사용하는 현재 구조의 잔여 리스크를 정리한다.

## Current Verdict

Blocking 리스크는 없다.

현재까지 다음 리스크는 검증 또는 구조 변경으로 해소됐다.

| Area | Status | Evidence |
|------|--------|----------|
| 실제 PostgreSQL 연동 | resolved | `:storage:jpa:livePostgresTest --rerun-tasks` PASS |
| runtime schema ownership | resolved | Flyway V1 migration 적용 확인 |
| generated schema와 DB schema mismatch | resolved | Live smoke가 Flyway 적용 후 Hibernate `ddl-auto=validate`로 검증 |
| runtime destructive DDL | resolved | Application runtime은 Flyway를 사용하고, live smoke도 `create-drop` 대신 `validate` 사용 |
| Docker init SQL과 migration DDL 중복 | resolved | `docker/postgres/init.sql`은 bootstrap만 담당 |
| JPA repository runtime scan 누락 | resolved | `JpaStorageConfig`가 `cc.midolog.storage.jpa` 전체를 scan |
| domain purity | resolved | `core:domain`에는 JPA/Spring/MyBatis/KSP/custom persistence annotation을 두지 않음 |
| dual target 생성기 동기화 | resolved | 공유 DSL(`gradle/domain-entities.gradle`)로부터 JPA 모델(`generateJpaDslSources`)과 MyBatis 모델(`generateMyBatisDynamicSqlSources`) 2개 대상을 동시에 생성하여 영속성 계층 간 일관성 보장 |

## Residual Risks

### 1. Advanced Relation DSL

**Status**: accepted non-goal  
**Risk level**: medium when complex aggregate persistence is required  

현재 relation DSL은 simple parent-child relation만 지원한다.

지원하지 않는 범위:

- many-to-many
- cascade remove
- orphan removal
- deep graph persistence

현재 mapper는 deep graph를 자동 복원하지 않는다. `oneToMany`는 `toDomain = 'emptyList()'` 같은 explicit shallow policy로 lazy proxy 접근과 무한 재귀를 피한다.

**Escalation trigger**:

- 실제 업무 도메인에서 many-to-many가 필요해진다.
- aggregate 삭제 시 cascade/orphan 정책이 필요해진다.
- domain graph를 shallow mapper가 아니라 persistence graph로 복원해야 한다.

**Recommended next action when triggered**:

별도 `relation-dsl-expansion` plan을 만들고, cascade/delete semantics와 mapper recursion policy를 먼저 설계한다.

### 2. Plugin Publishing and Binary Compatibility

**Status**: accepted non-goal  
**Risk level**: low now, medium when reused across repositories  

현재 `cc.midolog.jpa-dsl`은 repository-private `build-logic` included build plugin이다. public plugin publishing, binary compatibility, release automation은 범위 밖이다.

**Escalation trigger**:

- 둘 이상의 repository에서 같은 DSL plugin을 재사용한다.
- plugin API compatibility를 유지해야 한다.
- versioned release 또는 artifact publishing이 필요하다.

**Recommended next action when triggered**:

별도 `jpa-dsl-plugin-publishing` plan을 만들고, 다음 항목을 포함한다.

- Gradle plugin marker publishing
- semantic versioning
- binary compatibility policy
- TestKit compatibility matrix
- changelog/release automation

### 3. Migration Draft Automation

**Status**: draft automation (verify-only) 구현됨  
**Risk level**: low (verify 태스크가 drift를 build 단계에서 조기 검출)  

현재 runtime schema는 Flyway가 소유하지만, migration SQL은 사람이 작성한다. domain data class 또는 `jpaDsl { ... }` 선언이 바뀌면 generated JPA mapping과 Flyway migration을 같은 변경 단위에서 함께 갱신해야 한다.

**Implemented** (`jpa-dsl-migration-draft-generator`, `docs/JPA_DSL_MIGRATION_DRAFT_GENERATOR_PLAN.md`):

- `verifyMigrationDraft` — `jpaDsl` expected schema와 현재 Flyway schema를 DB 없이 정적 비교, drift가 있으면 build FAIL. `livePostgresTest`의 `ddl-auto=validate`보다 앞단에서 검출.
- `generateMigrationDraft` — 다음 버전 draft SQL을 `build/generated/migration-draft/`에 생성. 자동 적용·destructive DDL 자동 생성 없음(manual review).
- Evidence: `./gradlew -p build-logic test` (7/7 PASS), `:storage:jpa:verifyMigrationDraft` PASS, 음성-path(V1 제거) BUILD FAILED 확인.

아래 남은 항목(자동 적용, destructive 자동 마이그레이션)은 여전히 non-goal이다.

**Escalation trigger**:

- domain/DSL schema 변경 빈도가 높아진다.
- migration 누락으로 `livePostgresTest` 또는 runtime boot가 자주 실패한다.
- generated JPA model과 Flyway migration 간 diff를 사전에 보고 싶어진다.

**Implemented action**:

별도 plan([`docs/JPA_DSL_MIGRATION_DRAFT_GENERATOR_PLAN.md`](./JPA_DSL_MIGRATION_DRAFT_GENERATOR_PLAN.md))을 수립하고 구현 완료됨:

- 구현 파일:
  - `build-logic/src/main/kotlin/cc/midolog/buildlogic/jpadsl/VerifyMigrationDraftTask.kt`
  - `build-logic/src/main/kotlin/cc/midolog/buildlogic/jpadsl/GenerateMigrationDraftTask.kt`
  - `build-logic/src/main/kotlin/cc/midolog/buildlogic/jpadsl/MigrationDraftEngine.kt`
  - `build-logic/src/main/kotlin/cc/midolog/buildlogic/jpadsl/FlywaySchemaParser.kt`
  - `build-logic/src/main/kotlin/cc/midolog/buildlogic/jpadsl/ExpectedSchemaBuilder.kt`
  - `build-logic/src/main/kotlin/cc/midolog/buildlogic/jpadsl/SchemaDiffer.kt`
  - `build-logic/src/main/kotlin/cc/midolog/buildlogic/jpadsl/MigrationDraftRenderer.kt`
- 등록 태스크:
  - `:storage:jpa:verifyMigrationDraft` (`jpaDsl` expected schema와 현재 Flyway schema 정적 비교, drift 발생 시 빌드 실패)
  - `:storage:jpa:generateMigrationDraft` (`build/generated/migration-draft/` 디렉터리에 다음 버전 draft SQL 생성)
- 충족 범위:
  - `jpaDsl` model에서 expected table/column/FK metadata 추출
  - 현재 Flyway latest schema와 expected metadata 비교
  - migration draft SQL 생성
  - destructive migration은 자동 생성하지 않고 manual review 요구 유지

## Operating Rules

- `core:domain`에는 persistence annotation을 추가하지 않는다.
- generated Kotlin은 직접 수정하지 않는다.
- domain field 또는 DSL mapping 변경 시 Flyway migration을 같은 PR/commit에서 갱신한다.
- runtime에서는 destructive `ddl-auto=create`, `create-drop`, `drop`을 사용하지 않는다.
- live PostgreSQL confidence가 필요하면 다음 명령을 실행한다.

```bash
DB_URL=jdbc:postgresql://localhost:55432/backend \
DB_USERNAME=backend \
DB_PASSWORD=backend \
./gradlew :storage:jpa:livePostgresTest --rerun-tasks
```

## Verification Baseline

현재 risk baseline은 다음 검증이 통과한 상태를 기준으로 한다.

```bash
./gradlew test
DB_URL=jdbc:postgresql://localhost:55432/backend \
DB_USERNAME=backend \
DB_PASSWORD=backend \
./gradlew :storage:jpa:livePostgresTest --rerun-tasks
git diff --check
```
