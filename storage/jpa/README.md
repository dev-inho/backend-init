# storage:jpa

`storage:jpa`는 `core:domain`의 repository port를 Spring Data JPA로 구현하는 blocking persistence adapter다.

## Boundary
- JPA entity, repository, mapper, adapter는 이 모듈 내부에 둔다.
- `core:domain` 모델에는 `@Entity`, `@Table`, Spring Data import를 추가하지 않는다.
- `core:application`은 `SampleRepositoryPort`만 의존하고 JPA 구현 클래스를 직접 import하지 않는다.
- QueryDSL(`querydsl-jpa`)은 `storage/jpa`의 내부 `implementation`이며, 생성된 Q 타입(`Q*JpaEntity`)은 모듈 밖으로 절대 노출하지 않는다 (`PersistenceBoundaryTest`가 타 모듈의 `com.querydsl` import를 원천 차단).

## Generated JPA Sources & QueryDSL
- `core:domain` 모델은 어노테이션 없이 plain data class로 유지한다.
- `storage:jpa`는 `build-logic`의 `cc.midolog.jpa-dsl` 내부 Gradle plugin을 적용한다.
- `storage:jpa/build.gradle`의 typed `jpaDsl { ... }` 선언이 JPA table/id/field/relation 매핑의 source다.
- `build-logic` plugin production code가 domain data class의 primary constructor를 읽어 `*JpaEntity`, `*JpaRepository`, `*JpaMapper`를 생성한다.
- JPA DSL 자체의 generated source directory, sourceSets, 기본 compile task wiring은 plugin이 소유한다. 단, QueryDSL kapt 파이프라인과의 연동(`kaptGenerateStubsKotlin.dependsOn('generateJpaDslSources')`)은 `storage/jpa/build.gradle`의 `afterEvaluate`에서 모듈 수준으로 와이어링한다.
- 빌드/컴파일 파이프라인의 태스크 체인은 다음과 같이 순차적으로 동작한다:
  `domain → generateJpaDslSources(Kotlin entity) → kaptGenerateStubsKotlin → kaptKotlin(Q Java) → compileKotlin`
- 생성 소스는 두 개의 서로 다른 generated root로 격리되어 관리된다:
  1. JPA DSL 생성 Kotlin 소스: `build/generated/sources/jpaDsl/main/kotlin` (`*JpaEntity`, `*JpaRepository`, `*JpaMapper`)
  2. QueryDSL kapt 생성 Java 소스: `build/generated/source/kapt/main` (`Q*JpaEntity.java`)
- 생성 파일은 Gradle build directory 아래에만 위치하며, `src/main/kotlin`에는 adapter/config 같은 hand-written persistence code만 둔다.
- `validateJpaDslGeneratorNegativeCases` task는 unsupported DSL 선언이 invalid Kotlin compile error로 넘어가기 전에 `GradleException`으로 실패하는지 검증한다.
- `SelfContainedQueryDslGuardTest`는 문자열 JPQL(`createQuery(`) 0건 유지와 6개 Q 클래스의 정확한 파일 경로 존재를 검증한다.

### Pluginization Status
- Phase 1은 plugin shell/parity harness 단계로 완료됐다.
- Phase 2는 typed DSL과 production validator/renderer extraction 단계로 완료됐다.
- Phase 3는 integration hardening과 documentation handoff 단계로 완료 대상이다.
- `build-logic` included build가 `cc.midolog.jpa-dsl` plugin id와 TestKit baseline을 제공한다.
- 현재 plugin은 `jpaDsl` extension, typed entity/field/relation model, parser, validator, renderer, `generateJpaDslSources`, `validateJpaDslGeneratorNegativeCases`를 제공한다.
- plugin은 repository-private included build다. public plugin publishing, binary compatibility, and release automation are intentionally out of scope.

### Adding a Domain to JPA
1. `core:domain`에 annotation 없는 primary-constructor `data class`를 추가한다.
2. repository port는 `core:domain`에 두고 concrete JPA type은 import하지 않는다.
3. `storage:jpa/build.gradle`의 `jpaDsl { ... }`에 `entity('<fqcn>')`를 추가하고 `table`, `id`, field/relation mapping을 선언한다.
4. `@Profile("jpa")` adapter를 추가하고 domain port contract test를 재사용한다.
5. `./gradlew :storage:jpa:clean :storage:jpa:test :core:domain:test`로 generated source와 domain purity를 확인한다.
6. PostgreSQL-specific mapping confidence가 필요하면 live database를 띄우고 `:storage:jpa:livePostgresTest`를 실행한다.

### Live PostgreSQL Smoke

기본 `:storage:jpa:test`는 H2 기반 slice test만 실행한다. 실제 PostgreSQL dialect/driver와 generated JPA mapping을 함께 확인할 때만 tagged smoke를 별도로 실행한다.

```bash
POSTGRES_PORT=55432 docker compose up -d postgres
DB_URL=jdbc:postgresql://localhost:55432/backend \
DB_USERNAME=backend \
DB_PASSWORD=backend \
./gradlew :storage:jpa:livePostgresTest
```

- `livePostgresTest`는 `live-postgres` JUnit tag만 실행한다.
- 일반 `test` task는 `live-postgres` tag를 제외하므로 Docker 없이도 동작해야 한다.
- smoke는 Flyway migration을 먼저 적용한 뒤 Hibernate `ddl-auto=validate`와 sample/user adapter, scalar column/enum/converter mapping, parent-child join column persistence를 실제 PostgreSQL에 대해 검증한다.

### Failure Modes
- domain source file이 없으면 `Domain source not found for <domainClass>`로 실패한다.
- primary-constructor `data class`가 아니면 `Only primary-constructor data classes are supported`로 실패한다.
- `id` property가 없거나 unknown field/relation을 선언하면 generation 단계에서 실패한다.
- converter는 `storageType`과 함께 선언해야 한다.
- enum strategy는 현재 `STRING`만 지원한다.
- unsupported relation type, missing relation metadata, self-target relation은 generated Kotlin compile 전에 실패한다.

### Scalar DSL
```groovy
jpaDsl {
    entity('cc.midolog.sample.model.ScalarSample') {
        table = 'scalar_sample'
        id = 'id'

        field('displayName') {
            column = 'display_name'
        }
        field('nickname') {
            nullable = true
        }
        field('status') {
            enumStrategy = 'STRING'
        }
        field('code') {
            column = 'code_value'
            storageType = 'String'
            converter = 'cc.midolog.storage.jpa.sample.ScalarSampleCodeJpaConverter'
        }
    }
}
```

- `column`: Kotlin property name과 다른 DB column name을 생성한다.
- `nullable`: generated `@Column(nullable = ...)` 값을 명시한다. 생략하면 Kotlin nullable type 여부를 따른다.
- `enumStrategy = 'STRING'`: generated field에 `@Enumerated(EnumType.STRING)`을 추가한다.
- `storageType` + `converter`: value object를 JPA scalar field로 저장한다.
- converter는 `toStorage(domainType): storageType`, `toDomain(storageType): domainType` 함수를 제공해야 한다.
- 현재 generator는 primary-constructor `data class`를 대상으로 한다.
- unsupported constructor form, missing id, unknown field, unsupported enum strategy, invalid converter declaration은 generation 단계에서 실패한다.

### Relation DSL
```groovy
jpaDsl {
    entity('cc.midolog.sample.model.RelationParent') {
        table = 'relation_parent'
        id = 'id'

        relation('children') {
            type = 'oneToMany'
            target = 'cc.midolog.sample.model.RelationChild'
            mappedBy = 'parent'
            toDomain = 'emptyList()'
        }
    }

    entity('cc.midolog.sample.model.RelationChild') {
        table = 'relation_child'
        id = 'id'

        field('parentId') {
            relation = 'parent'
        }
        relation('parent') {
            type = 'manyToOne'
            target = 'cc.midolog.sample.model.RelationParent'
            sourceField = 'parentId'
            joinColumn = 'parent_id'
            referencedColumn = 'id'
        }
    }
}
```

- `manyToOne`: domain의 scalar id field를 generated JPA relation field로 변환한다.
- `oneToMany`: generated JPA entity에 lazy collection relation을 추가한다.
- relation fetch는 기본적으로 `FetchType.LAZY`로 생성한다.
- mapper는 deep graph를 자동 복원하지 않는다. `oneToMany`의 `toDomain: 'emptyList'`는 lazy proxy 접근과 무한 재귀를 피하기 위한 정책이다.
- 현재 relation DSL은 simple parent-child relation만 지원한다. many-to-many, cascade remove, orphan removal, arbitrary deep graph persistence는 범위 밖이다.
- unsupported relation type, missing target/sourceField/joinColumn/referencedColumn/mappedBy, self-target relation은 generation 단계에서 실패한다.

### Generator Hardening
- `build-logic`의 TestKit은 plugin apply, marker task, malformed marker DSL failure를 검증한다.
- `build-logic`의 TestKit은 typed Groovy DSL positive generation, custom generated source directory, empty DSL failure, check task dependency, and malformed DSL negative failures를 검증한다.
- `generateJpaDslSources`는 항상 output directory를 삭제한 뒤 다시 생성해 stale generated source를 남기지 않는다.
- 빌드/컴파일 체인은 `domain → generateJpaDslSources(Kotlin entity) → kaptGenerateStubsKotlin → kaptKotlin(Q Java) → compileKotlin` 순서로 의존성이 연결된다.
- `check`는 `validateJpaDslGeneratorNegativeCases`에 의존한다.
- `SelfContainedQueryDslGuardTest`는 Gradle `queryDsl.generatedSourceDir` system property(`build/generated/source/kapt/main`)를 통해 6개 Q Java 파일(`QSampleJpaEntity.java`, `QUserJpaEntity.java`, `QFileMetaJpaEntity.java`, `QScalarSampleJpaEntity.java`, `QRelationParentJpaEntity.java`, `QRelationChildJpaEntity.java`)의 존재를 검증하고, `src/main`의 문자열 JPQL `createQuery(` 호출이 0건임을 보장한다.
- 새 domain field를 추가할 때는 `core:domain` data class와 `jpaDsl { ... }` 선언만 수정한다. JPA annotation은 domain에 추가하지 않는다.

### Escalation Criteria
- DSL 사용자가 둘 이상의 repository/project로 늘어나거나, plugin API compatibility가 필요해지면 public/plugin publishing plan을 별도로 만든다.
- many-to-many, cascade remove, orphan removal, deep graph persistence가 필요하면 relation DSL expansion plan으로 분리한다.
- domain source parsing이 Kotlin syntax edge cases를 더 많이 다뤄야 하면 compiler/Kotlin parser adoption을 별도 검토한다.
- 해소된 리스크와 잔여 non-blocking 리스크는 `docs/JPA_DSL_RISK_REGISTER.md`에 기록한다.

## Runtime Profile
- `JpaSampleRepositoryAdapter`와 JPA repository scan은 `jpa` profile에서만 활성화된다.
- `mybatis` profile과 동시에 켜면 `SampleRepositoryPort` 구현이 둘 이상 생길 수 있으므로, 운영 실행에서는 persistence profile을 하나만 선택한다.

## Blocking I/O
- JPA는 blocking persistence다.
- Adapter는 repository 호출을 `Dispatchers.IO` 안에서 실행해 WebFlux event loop를 직접 막지 않는다.
- Transaction 범위는 단일 repository read/write method로 제한한다.

## Schema
- Runtime schema는 `core:application/src/main/resources/db/migration`의 Flyway migration이 소유한다.
- `docker/postgres/init.sql`은 로컬 PostgreSQL bootstrap만 담당하고 table DDL을 소유하지 않는다.
- 운영용 main 설정에 파괴적 `ddl-auto=create`, `create-drop`, `drop` 옵션을 두지 않는다.
- 테스트에서는 in-memory H2 slice test에서 `create-drop`을 사용할 수 있다. Tagged live PostgreSQL smoke는 Flyway migration 후 `ddl-auto=validate`를 사용한다.
- generated JPA mapping을 바꾸는 domain/DSL 변경은 migration SQL 변경과 함께 리뷰한다.
