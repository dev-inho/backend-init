# Query DSL Options Research

## 1. 후보 비교 표

| 기술 후보 | DSL 산출물 캡슐화 여부 | 코드 생성 / 플러그인 | 타입 안전 범위 (컬럼/조인/프로젝션) | 코루틴/WebFlux 호환성 | Boot 4 / Hibernate 7 호환성 | 최신 릴리스 및 날짜 | 학습 곡선 |
|---|---|---|---|---|---|---|---|
| **QueryDSL** | 내부 캡슐화 가능 (Q클래스 외부 노출 안함) ([문서](https://querydsl.com/static/querydsl/latest/reference/html/ch02.html)) | kapt/KSP Q클래스 생성 필요 ([문서](https://querydsl.com/static/querydsl/latest/reference/html/ch02.html)) | 컬럼, 조인, 프로젝션 모두 지원 ([문서](https://querydsl.com/static/querydsl/latest/reference/html/ch03.html)) | 기본 블로킹, 코루틴 래핑 필요 ([문서](https://docs.spring.io/spring-data/jpa/reference/repositories/coroutines.html)) | `io.github.openfeign.querydsl` 7.x로 지원 ([릴리스](https://github.com/OpenFeign/querydsl/releases/tag/7.6)) | 7.6 (2026-08-19, [릴리스](https://github.com/OpenFeign/querydsl/releases/tag/7.6)) | 낮음 ([출처](https://querydsl.com/)) |
| **Kotlin JDSL 3.x** | 노출 없음 (DSL 산출물 미존재) ([문서](https://github.com/line/kotlin-jdsl/tree/main/docs)) | 생성 불필요 ([문서](https://github.com/line/kotlin-jdsl/tree/main/docs)) | 컬럼, 조인 프로젝션 컴파일 타임 검증 ([문서](https://github.com/line/kotlin-jdsl/tree/main/docs/basic-usage)) | 기본 블로킹, 코루틴 래핑 필요 ([문서](https://github.com/line/kotlin-jdsl/tree/main/docs)) | Hibernate 7 지원 ([릴리스](https://github.com/line/kotlin-jdsl/releases/tag/3.9.0)) | 3.9.0 (2026-05-11, [릴리스](https://github.com/line/kotlin-jdsl/releases/tag/3.9.0)) | 중간 ([출처](https://github.com/line/kotlin-jdsl)) |
| **JPA Criteria** | 내부 캡슐화 가능 ([문서](https://docs.spring.io/spring-data/jpa/reference/jpa/specifications.html)) | Metamodel 생성(선택적) ([문서](https://docs.jboss.org/hibernate/orm/current/topical/html_single/metamodelgen/MetamodelGenerator.html)) | 문자열 사용 시 컬럼 안전하지 않음 ([문서](https://docs.spring.io/spring-data/jpa/reference/)) | 기본 블로킹, 코루틴 래핑 필요 ([문서](https://docs.spring.io/spring-data/jpa/reference/repositories/coroutines.html)) | Boot 4 기본 포함 ([문서](https://github.com/spring-projects/spring-boot/releases)) | Boot 버전에 종속적 ([문서](https://spring.io/projects/spring-data-jpa)) | 높음 ([출처](https://spring.io/projects/spring-data-jpa)) |
| **jOOQ** | 수동 캡슐화 필요 (인터페이스 노출 주의) ([문서](https://www.jooq.org/doc/latest/manual/sql-building/column-expressions/)) | Flyway 스키마 기반 생성 필요 ([문서](https://www.jooq.org/doc/latest/manual/code-generation/)) | 컬럼, 조인, 프로젝션 완벽 ([문서](https://www.jooq.org/)) | R2DBC 코루틴 통합 지원 ([문서](https://www.jooq.org/doc/latest/manual/sql-execution/coroutines/)) | 완벽 지원 ([릴리스](https://github.com/jOOQ/jOOQ/releases/tag/version-3.21.8)) | 3.21.8 (2026-09-04, [릴리스](https://github.com/jOOQ/jOOQ/releases/tag/version-3.21.8)) | 높음 ([출처](https://www.jooq.org/)) |
| **MyBatis Dynamic SQL** | 노출 없음 (DTO 캡슐화) ([문서](https://mybatis.org/mybatis-dynamic-sql/docs/introduction.html)) | 불필요 ([문서](https://mybatis.org/mybatis-dynamic-sql/docs/introduction.html)) | 단일 쿼리 수준 컴파일 안전 ([문서](https://mybatis.org/mybatis-dynamic-sql/docs/introduction.html)) | MyBatis Spring Coroutines 활용 ([문서](https://mybatis.org/spring/)) | Jakarta 호환 지원 ([릴리스](https://github.com/mybatis/mybatis-dynamic-sql/releases/tag/mybatis-dynamic-sql-2.0.0)) | 2.0.0 (2026-03-12, [릴리스](https://github.com/mybatis/mybatis-dynamic-sql/releases/tag/mybatis-dynamic-sql-2.0.0)) | 낮음 ([출처](https://mybatis.org/mybatis-dynamic-sql/)) |
| **Spring Data JDBC** | 노출 없음 ([문서](https://docs.spring.io/spring-data/jdbc/reference/jdbc/domain-driven-design.html)) | 불필요 ([문서](https://docs.spring.io/spring-data/jdbc/reference/)) | 리포지토리 메서드 수준 안전 ([문서](https://docs.spring.io/spring-data/jdbc/reference/)) | JDBC 블로킹, R2DBC 대체 가능 ([문서](https://docs.spring.io/spring-data/r2dbc/reference/)) | 완벽 지원 ([문서](https://github.com/spring-projects/spring-data-jdbc/releases)) | Boot 버전에 종속적 ([문서](https://spring.io/projects/spring-data-jdbc)) | 낮음 ([출처](https://spring.io/projects/spring-data-jdbc)) |
| **Exposed** | 내부 캡슐화 필요 ([문서](https://jetbrains.github.io/Exposed/getting-started.html)) | 테이블/엔티티 객체 수동 작성 ([문서](https://jetbrains.github.io/Exposed/getting-started.html)) | 컬럼, 조인, 프로젝션 완전 지원 ([문서](https://jetbrains.github.io/Exposed/getting-started.html)) | 자체 서스펜드 트랜잭션 제공 ([문서](https://jetbrains.github.io/Exposed/transactions.html)) | 독자 생태계 (Hibernate 미사용) ([릴리스](https://github.com/JetBrains/Exposed/releases/tag/1.5.0)) | 1.5.0 (2026-08-26, [릴리스](https://github.com/JetBrains/Exposed/releases/tag/1.5.0)) | 중간 ([출처](https://jetbrains.github.io/Exposed/)) |

## 2. 구체적 코드 예시 및 분석 (FileMeta)

### 2.1 QueryDSL
```kotlin
// findById
queryFactory.selectFrom(qFileMeta).where(qFileMeta.id.eq(id)).fetchOne()

// save
// 순수 JPA persist 활용. QueryDSL은 삽입 쿼리 인터페이스가 빈약함
entityManager.persist(entity)

// updateStatus
queryFactory.update(qFileMeta)
    .set(qFileMeta.status, status)
    .set(qFileMeta.updatedAt, clock.instant())
    .where(qFileMeta.id.eq(id)).execute()

// findExpiredPending
queryFactory.selectFrom(qFileMeta)
    .where(qFileMeta.status.eq(FileStatus.PENDING).and(qFileMeta.updatedAt.lt(cutoff)))
    .orderBy(qFileMeta.updatedAt.asc()).limit(limit.toLong()).fetch()
```

### 2.2 Kotlin JDSL 3.x (+ Criteria 혼합안)
```kotlin
// findById
queryFactory.singleQuery<FileMetaJpaEntity> {
    select(entity(FileMetaJpaEntity::class))
    from(entity(FileMetaJpaEntity::class))
    where(path(FileMetaJpaEntity::id).eq(id))
}

// save
fileMetaJpaRepository.save(entity)

// updateStatus
// Kotlin JDSL 3.x는 Select 집중형이라 Criteria 혼합이 권장됨.
// 단, "status", "id" 등 문자열 프로퍼티 사용 시 컬럼 타입 완전 검증이 불가한 한계 존재.
val update = cb.createCriteriaUpdate(FileMetaJpaEntity::class.java)
val root = update.from(FileMetaJpaEntity::class.java)
update.set("status", status).set("updatedAt", clock.instant())
update.where(cb.equal(root.get<String>("id"), id))
entityManager.createQuery(update).executeUpdate()

// findExpiredPending
queryFactory.listQuery<FileMetaJpaEntity> {
    select(entity(FileMetaJpaEntity::class))
    from(entity(FileMetaJpaEntity::class))
    where(path(FileMetaJpaEntity::status).eq(FileStatus.PENDING).and(path(FileMetaJpaEntity::updatedAt).lessThan(cutoff)))
    orderBy(path(FileMetaJpaEntity::updatedAt).asc()).limit(limit)
}
```

### 2.3 JPA Criteria
```kotlin
// findById
val root = query.from(FileMetaJpaEntity::class.java)
query.select(root).where(cb.equal(root.get<String>("id"), id))
entityManager.createQuery(query).singleResult

// save
entityManager.persist(entity)

// updateStatus
// Kotlin JDSL 혼합안과 동일하게 문자열 기반 속성 참조의 한계가 존재함.
val update = cb.createCriteriaUpdate(FileMetaJpaEntity::class.java)
val root = update.from(FileMetaJpaEntity::class.java)
update.set("status", status).set("updatedAt", clock.instant())
update.where(cb.equal(root.get<String>("id"), id))
entityManager.createQuery(update).executeUpdate()

// findExpiredPending
val root = query.from(FileMetaJpaEntity::class.java)
query.select(root)
    .where(cb.and(cb.equal(root.get<FileStatus>("status"), FileStatus.PENDING), cb.lessThan(root.get<java.time.Instant>("updatedAt"), cutoff)))
    .orderBy(cb.asc(root.get<java.time.Instant>("updatedAt")))
entityManager.createQuery(query).setMaxResults(limit).resultList
```

### 2.4 jOOQ
```kotlin
// findById
dsl.selectFrom(FILE_META).where(FILE_META.ID.eq(id)).fetchOneInto(FileMeta::class.java)

// save (upsert)
// jOOQ는 네이티브에 가까운 upsert 지원
dsl.insertInto(FILE_META)
    .set(FILE_META.ID, entity.id)
    .set(FILE_META.STATUS, entity.status) // 등등 모든 컬럼 set
    .onConflict(FILE_META.ID)
    .doUpdate()
    .set(FILE_META.STATUS, entity.status)
    .execute()

// updateStatus
dsl.update(FILE_META)
    .set(FILE_META.STATUS, status.name)
    .set(FILE_META.UPDATED_AT, clock.instant())
    .where(FILE_META.ID.eq(id))
    .execute()

// findExpiredPending
dsl.selectFrom(FILE_META)
    .where(FILE_META.STATUS.eq(FileStatus.PENDING.name))
    .and(FILE_META.UPDATED_AT.lt(cutoff))
    .orderBy(FILE_META.UPDATED_AT.asc())
    .limit(limit)
    .fetchInto(FileMeta::class.java)
```

### 2.5 MyBatis Dynamic SQL
```kotlin
// findById
val selectStatement = select(FileMetaDynamicSqlSupport.id, FileMetaDynamicSqlSupport.status)
    .from(FileMetaDynamicSqlSupport.fileMeta)
    .where(FileMetaDynamicSqlSupport.id, isEqualTo(id))
    .build()
    .render(RenderingStrategies.MYBATIS3)
mapper.selectMany(selectStatement).firstOrNull()

// save
// upsert 쿼리를 Dynamic SQL Provider로 구성해야 하거나 별도 XML 유지 필요
mapper.insert(entity)

// updateStatus
val updateStatement = update(FileMetaDynamicSqlSupport.fileMeta)
    .set(FileMetaDynamicSqlSupport.status).equalTo(status)
    .set(FileMetaDynamicSqlSupport.updatedAt).equalTo(clock.instant())
    .where(FileMetaDynamicSqlSupport.id, isEqualTo(id))
    .build()
    .render(RenderingStrategies.MYBATIS3)
mapper.update(updateStatement)

// findExpiredPending
val selectStatement = select(FileMetaDynamicSqlSupport.id, FileMetaDynamicSqlSupport.status)
    .from(FileMetaDynamicSqlSupport.fileMeta)
    .where(FileMetaDynamicSqlSupport.status, isEqualTo(FileStatus.PENDING))
    .and(FileMetaDynamicSqlSupport.updatedAt, isLessThan(cutoff))
    .orderBy(FileMetaDynamicSqlSupport.updatedAt)
    .limit(limit)
    .build()
    .render(RenderingStrategies.MYBATIS3)
mapper.selectMany(selectStatement)
```

### 2.6 Spring Data JDBC
```kotlin
// findById
repository.findById(id)

// save
// 엔티티가 새로운지 아닌지(IsNewStrategy) 판단에 따라 Insert/Update가 갈림
repository.save(entity)

// updateStatus
// 애노테이션 기반 @Query 작성
@Modifying
@Query("UPDATE file_meta SET status = :status, updated_at = :updatedAt WHERE id = :id")
fun updateStatus(id: String, status: FileStatus, updatedAt: Instant)

// findExpiredPending
// 애노테이션 기반 @Query 작성
@Query("SELECT * FROM file_meta WHERE status = :status AND updated_at < :cutoff ORDER BY updated_at ASC LIMIT :limit")
fun findExpiredPending(status: FileStatus, cutoff: Instant, limit: Int): List<FileMetaEntity>
```

### 2.7 Exposed
```kotlin
// findById
FileMetaTable.selectAll().where { FileMetaTable.id eq id }.singleOrNull()

// save (upsert 지원)
FileMetaTable.upsert {
    it[id] = entity.id
    it[status] = entity.status
}

// updateStatus
FileMetaTable.update({ FileMetaTable.id eq id }) {
    it[status] = status
    it[updatedAt] = clock.instant()
}

// findExpiredPending
FileMetaTable.selectAll()
    .where { (FileMetaTable.status eq FileStatus.PENDING) and (FileMetaTable.updatedAt less cutoff) }
    .orderBy(FileMetaTable.updatedAt to SortOrder.ASC)
    .limit(limit)
    .toList()
```

## 3. 추천 후보: Kotlin JDSL 3.x (또는 혼합안)
**추천 사유:** DSL 산출물을 별도 클래스로 노출하지 않아 "DSL 산출물이 storage 모듈 밖으로 보이지 않아야 한다"는 경계 원칙을 만족합니다. 반면 QueryDSL은 Q클래스를 생성하여 의존성이 유출될 가능성이 높습니다. Bulk Update 한계는 Criteria로 보완 가능합니다(단, 속성을 문자열로 기입해야 하므로 Update 컬럼 검증은 일부 수동이 필요합니다).

## 4. 🔶 채택 로드맵 및 사용자 결정

**도입 로드맵 (순서 엄수):**
1. **경계 유출 제거:** `core/application` 내 JPA/MyBatis 설정 정리 및 KDoc을 제외한 프레임워크 import 제거.
2. **경계 가드:** `build.gradle`에 인프라 import 스캔 검증 태스크 추가.
3. **경계 복원:** `java-test-fixtures` 이관을 통한 계약 테스트 인프라 주입 독립.
4. **DSL 채택:** 어댑터 내 JPQL 쿼리를 Kotlin JDSL로 교체 (문자열 제거).

**🔶 사용자 결정 사항:**
1. **이중화 유지 여부:** JPA/MyBatis 중 JPA 단일로 통폐합할지 여부.
2. **JPA DSL 채택 여부:** Kotlin JDSL 3.x 도입 및 Criteria 병행 전략 승인 여부.
3. **MyBatis 대안 전환 여부:** 만약 MyBatis를 남길 경우 Dynamic SQL이나 jOOQ로 전환할지 여부.
