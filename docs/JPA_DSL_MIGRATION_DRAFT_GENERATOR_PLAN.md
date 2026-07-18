# JPA DSL Migration Draft Generator — Plan

이 문서는 `JPA_DSL_RISK_REGISTER.md`의 Residual Risk #3 (Migration Draft Automation)을 착수하기 위한 plan이다.

## Scope Statement

**목표**: `jpaDsl { ... }` 선언 + 도메인 data class에서 도출한 expected schema와 현재 Flyway latest schema를 비교해, 다음 `V{n+1}__*.sql` **draft**를 생성하고 drift를 보고한다.

**첫 단계 non-goal (리스크 레지스터 규칙 준수)**:

- 자동 적용(auto-apply) 하지 않는다. 사람이 review 후 커밋한다.
- destructive DDL(`DROP TABLE`, `DROP COLUMN`, 타입 축소)은 자동 생성하지 않고 warning으로만 보고한다.
- runtime `ddl-auto`는 `validate` 유지. 이 생성기는 build/CI 단계 도구다.

## Current State (검증된 근거)

| 항목 | 위치 | 사실 |
|------|------|------|
| Expected schema 메타 | `build-logic/src/main/kotlin/cc/midolog/buildlogic/jpadsl/JpaDslExtension.kt` | `JpaEntitySpec(table, id, fields, relations)` 리스트 = `JpaDslExtension.specs()` |
| 도메인 컬럼/타입 원본 | `DomainSourceParser.kt` | data class 생성자 → `List<DomainProperty(name, type)>` |
| FK 정보 | `JpaRelationSpec` | owning side = `type=="manyToOne"`: `joinColumn` → target table의 `referencedColumn` |
| 현재 schema | `core/application/src/main/resources/db/migration/V1__create_storage_tables.sql` | 단일 migration. naming `V{n}__{desc}.sql` |
| drift 감지 | `storage/jpa/.../LivePostgresJpaMappingSmokeTest.kt` (`ddl-auto=validate`) | reactive only. 사전 diff 태스크 없음 ← **채울 gap** |
| 생성 태스크 | `JpaDslPlugin.kt` `generateJpaDslSources` | metadata를 이미 in-memory로 구축. SQL은 미생성 |

## Metadata → Expected Schema 규칙

기존 renderer(`JpaDslRenderer`, `GenerateJpaDslSourcesTask`)와 **동일한** 해석 규칙을 재사용해야 drift가 없다.

- **컬럼 집합**: `spec.domainClass`의 data class 생성자 전체 property. relation-only property(`oneToMany` 대상)는 컬럼 아님.
- **컬럼명**: `JpaFieldSpec.column ?: property.name`
- **nullability**: `JpaFieldSpec.nullable ?: property.type.endsWith("?")` (NOT NULL 여부)
- **저장 타입**: `JpaFieldSpec.storageType ?: property.type` → SQL 타입 매핑 필요 (아래 참조)
- **PK**: `spec.id` (단일 컬럼)
- **FK**: `spec.relations` 중 `type=="manyToOne"` → `spec.table.joinColumn` REFERENCES `target.table.referencedColumn`

### Kotlin → SQL 타입 매핑 (초안, 확정 필요)

현재 V1이 모든 문자열/enum을 `VARCHAR(255)`, id를 `VARCHAR(64)`로 쓴다. 매핑 테이블은 명시적 정책으로 확정해야 한다 (Open Question 참조).

| Kotlin storageType | SQL (제안) |
|--------------------|-----------|
| `String` (id) | `VARCHAR(64)` |
| `String` (일반) | `VARCHAR(255)` |
| enum (`enumStrategy=STRING`) | `VARCHAR(255)` |
| `Int` / `Long` | `INTEGER` / `BIGINT` |
| `Boolean` | `BOOLEAN` |
| `Instant` / `LocalDateTime` | `TIMESTAMP` |

## Phases

### Phase 1 — Expected Schema Model 추출
`JpaDslExtension.specs()` + `DomainSourceParser`를 소비해 `ExpectedTable(name, columns[], pk, fks[])` 순수 데이터 모델을 만든다. 위 해석 규칙을 `GenerateJpaDslSourcesTask`와 공유(중복 로직 금지, DRY).

**산출**: build-logic 내 `ExpectedSchemaModel.kt` + 단위 테스트(기존 5개 entity 스펙 → 기대 테이블 5개 검증).

### Phase 2 — Current Schema 파싱
현재 Flyway migration 집합을 파싱해 `ActualTable` 모델을 만든다.

- **선택 A (권장)**: SQL 파일을 정적 파싱 (DB 불필요, CI 친화적, 결정적).
- 선택 B: 실 DB에 Flyway 적용 후 information_schema 조회 (정확하지만 DB 의존).

권장은 A. 단, `CREATE TABLE IF NOT EXISTS` / `CONSTRAINT ... FOREIGN KEY` / `INSERT`(seed는 무시) 파싱 범위를 명확히 한정한다.

### Phase 3 — Diff & Draft SQL 생성
`ExpectedTable` vs `ActualTable` diff → 다음 버전 draft SQL.

- 신규 테이블 → `CREATE TABLE`
- 신규 컬럼 → `ALTER TABLE ADD COLUMN`
- 신규 FK → `ADD CONSTRAINT`
- **destructive(삭제/축소)** → 생성하지 않고 `-- WARNING: manual review required` 주석 + 콘솔 경고
- draft 파일명: `V{latest+1}__generated_draft.sql`, 기본은 별도 out 디렉터리에 쓰고 migration 폴더에 자동 복사하지 않음.

### Phase 4 — Gradle Task & Verify Mode
- `generateMigrationDraft` 태스크: draft 파일 출력.
- `verifyMigrationDraft` (check 훅): expected vs actual diff가 비어있지 않으면 **build FAIL** + 무엇이 빠졌는지 리포트. → drift를 `livePostgresTest`보다 앞단(CI, DB 불필요)에서 조기 검출.

## Implementation Status (2026-07-18)

Phase 1–4 코드 구현 완료. 위치: `build-logic/src/main/kotlin/cc/midolog/buildlogic/jpadsl/`.

| Phase | 산출물 | 상태 |
|-------|--------|------|
| 1 | `MigrationSchemaModel.kt`, `SqlTypeMapper.kt`, `ExpectedSchemaBuilder.kt` | 구현 |
| 2 | `FlywaySchemaParser.kt` | 구현 |
| 3 | `SchemaDiffer.kt`, `MigrationDraftRenderer.kt`, `MigrationDraftEngine.kt` | 구현 |
| 4 | `GenerateMigrationDraftTask.kt`, `VerifyMigrationDraftTask.kt` + `JpaDslPlugin` 등록 | 구현 |
| test | `build-logic/src/test/.../MigrationDraftTest.kt` (7 케이스, 핵심: 현재 DSL+V1 = drift 0) | 작성 |

**실측 검증 완료** (JDK 17 = Homebrew `openjdk@17`로 확보, Gradle 9.5.1):

| 검증 | 명령 | 결과 |
|------|------|------|
| 단위 테스트 | `./gradlew -p build-logic test` | **7/7 PASS** (skipped=0, failures=0), 핵심 "zero drift" 포함 |
| verify happy-path | `:storage:jpa:verifyMigrationDraft` | PASS — no schema drift detected |
| generate happy-path | `:storage:jpa:generateMigrationDraft` | `V2__generated_draft.sql` = no-drift 주석만 |
| verify 음성-path | V1 임시 제거 후 verify | **BUILD FAILED** — 5개 missing table 정확 보고 |
| generate 음성-path | 빈 스키마에서 generate | 실 도메인 파싱 기반 전체 CREATE draft 생성 (V1과 동일 스키마 재구성) |
| 원복 재검증 | V1 복원 후 verify | PASS |

생성 draft와 hand-written V1의 차이는 (a) 컬럼 순서, (b) FK 제약명 규칙 `fk_<table>_<column>`, (c) index/seed 미재생성(가산 수동 항목이므로 올바른 동작)뿐이다.

검증 통과 확인. `JPA_DSL_RISK_REGISTER.md` #3 status를 `manual process accepted` → `draft automation (verify-only)`로 갱신하고 Evidence에 `verifyMigrationDraft`를 기록할 것(후속).

## Verification Baseline

```bash
./gradlew :build-logic:test          # Phase 1~3 단위 테스트
./gradlew :storage:jpa:generateMigrationDraft   # draft 생성 확인
./gradlew :storage:jpa:verifyMigrationDraft     # drift=0 확인 (V1이 최신이면 PASS)
./gradlew test
git diff --check
```

기존 baseline(`test` + `livePostgresTest`)은 회귀 방지용으로 계속 통과해야 한다.

## Open Questions — 해소됨

1. **타입 매핑 정책** → **고정 명시 매핑** 채택. `SqlTypeMapper`에 하드코딩(id/FK=`VARCHAR(64)`, String/enum=`VARCHAR(255)`, 숫자·시간 타입 매핑). 미지원 타입은 fail-fast로 명시적 매핑/`storageType` override를 강제.
2. **Phase 2 방식** → **정적 SQL 파싱(A)** 채택. `FlywaySchemaParser`가 `CREATE TABLE`/`ALTER TABLE ADD`/table-level FK를 파싱. DB 불필요·결정적.
3. **verify 태스크 위치** → **opt-in 태스크로 유지**(`check`에 자동 연결하지 않음). 이유: 빌드를 자동 실패시키는 것은 신중한 결정이며, 첫 단계는 "draft 생성+검증 한정" 원칙에 맞춰 명시 실행. `check` 연결은 후속 결정으로 남김.
4. **draft 출력 위치** → **`build/generated/migration-draft/`에만 기록**. migration 폴더 자동 복사·자동 적용 없음. 사람이 review 후 승격.

## Escalation / 이후

이 plan 완료 후 리스크 레지스터 #3 status를 `manual process accepted` → `draft automation (verify-only)`로 갱신하고 Evidence에 `verifyMigrationDraft` 태스크를 기록한다.
