# Query DSL Options Research

## 1. 후보 비교 표

| 기술 후보 | DSL 산출물 캡슐화 여부 | 코드 생성 / 지원 플러그인 | 타입 안전 범위 (컬럼/조인/프로젝션) | 코루틴/WebFlux 호환성 | Boot 4 / Hibernate 7 지원 여부 | 최신 릴리스 및 날짜 | 학습 곡선 |
|---|---|---|---|---|---|---|---|
| **QueryDSL** | 내부 캡슐화 가능 (어댑터 내에서만 Q클래스 사용 시, [문서](https://github.com/OpenFeign/querydsl)) | kapt/KSP 코드 생성 필요 ([저장소](https://github.com/OpenFeign/querydsl)) | 컬럼, 조인, 프로젝션 모두 지원 ([문서](https://github.com/OpenFeign/querydsl)) | 기본적으로 블로킹, 코루틴 래핑 필요 ([문서](https://docs.spring.io/spring-data/jpa/reference/)) | `io.github.openfeign.querydsl` 6.10.1+로 Jakarta/Hib 7/Boot 4 지원 ([릴리스](https://github.com/OpenFeign/querydsl/releases)) | 6.10.1 (2024-03, [릴리스](https://github.com/OpenFeign/querydsl/releases)) | 낮음 |
| **Kotlin JDSL 3.x** | 노출 없음 (Kotlin 리플렉션/람다 활용, [문서](https://github.com/line/kotlin-jdsl)) | 생성 불필요 ([저장소](https://github.com/line/kotlin-jdsl)) | 컬럼, 조인 프로젝션 런타임/컴파일 타임 검증 ([문서](https://github.com/line/kotlin-jdsl)) | 기본적으로 블로킹, 코루틴 래핑 필요 ([문서](https://github.com/line/kotlin-jdsl)) | Hibernate 7 지원 진행 중 ([이슈/릴리스](https://github.com/line/kotlin-jdsl/releases)) | 3.5.3 (2025-01, [릴리스](https://github.com/line/kotlin-jdsl/releases)) | 중간 |
| **JPA Criteria** | 내부 캡슐화 가능 ([문서](https://docs.spring.io/spring-data/jpa/reference/jpa/specifications.html)) | 선택적 (Metamodel, [문서](https://hibernate.org/orm/)) | 스펙 상 완벽 호환 ([문서](https://docs.spring.io/spring-data/jpa/reference/)) | 기본적으로 블로킹, 코루틴 래핑 필요 ([문서](https://spring.io/projects/spring-data-jpa)) | Boot 4 기본 포함 ([문서](https://spring.io/projects/spring-boot)) | Boot 버전에 종속적 ([문서](https://spring.io/projects/spring-data-jpa)) | 높음 |
| **jOOQ** | 수동 캡슐화 필요 (인터페이스 노출 금지, [문서](https://www.jooq.org/doc/latest/manual/sql-building/column-expressions/)) | Flyway 스키마 기반 생성 필요 (SQL 우선 대체제, [문서](https://www.jooq.org/doc/latest/manual/)) | 컬럼, 조인, 프로젝션 완벽 ([문서](https://www.jooq.org/)) | R2DBC 코루틴 통합 지원 ([문서](https://www.jooq.org/doc/latest/manual/)) | 완벽 지원 ([다운로드](https://www.jooq.org/download/versions)) | 3.20.0 (2025-02, [저장소](https://github.com/jOOQ/jOOQ)) | 높음 |
| **MyBatis Dynamic SQL** | 노출 없음 (DTO 내 캡슐화, [문서](https://mybatis.org/mybatis-dynamic-sql/docs/introduction.html)) | 불필요 ([문서](https://mybatis.org/mybatis-dynamic-sql/)) | 컬럼, 단일 쿼리 수준 ([문서](https://mybatis.org/mybatis-dynamic-sql/)) | MyBatis Spring Coroutines 의존 ([문서](https://mybatis.org/mybatis-dynamic-sql/)) | Jakarta 호환 지원 ([릴리스](https://github.com/mybatis/mybatis-dynamic-sql/releases)) | 1.5.1 (2024-02, [저장소](https://github.com/mybatis/mybatis-dynamic-sql)) | 낮음 |
| **Spring Data JDBC** | 노출 없음 ([문서](https://spring.io/projects/spring-data-jdbc)) | 불필요 ([문서](https://spring.io/projects/spring-data-jdbc)) | 기본 리포지토리 메서드 수준 ([문서](https://spring.io/projects/spring-data-jdbc)) | R2DBC 분리, JDBC 자체는 블로킹 ([문서](https://spring.io/projects/spring-data-r2dbc)) | 완벽 지원 ([문서](https://spring.io/projects/spring-data-jdbc)) | 3.5.0 (2025-01, [문서](https://spring.io/projects/spring-data-jdbc)) | 낮음 |
| **Exposed** | 내부 캡슐화 필요 ([문서](https://jetbrains.github.io/Exposed/)) | 부분 필요 ([문서](https://github.com/JetBrains/Exposed)) | 컬럼, 조인, 프로젝션 지원 ([문서](https://jetbrains.github.io/Exposed/)) | JDBC 블로킹, 자체 서스펜드 트랜잭션 ([문서](https://jetbrains.github.io/Exposed/transactions.html)) | 독자 생태계 ([릴리스](https://github.com/JetBrains/Exposed/releases)) | 0.58.0 (2025-02, [저장소](https://github.com/JetBrains/Exposed)) | 중간 |

## 2. 구체적 코드 예시 및 분석 (FileMeta)

### 2.1 QueryDSL
```kotlin
// findById
queryFactory.selectFrom(qFileMeta).where(qFileMeta.id.eq(id)).fetchOne()

// save: 기존 JPA persist 사용. (포트 계약의 upsert와 달리 순수 JPA는 기존 엔티티 갱신 방식이 다르므로 주의)
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

### 2.2 Kotlin JDSL 3.x
```kotlin
// findById
queryFactory.singleQuery<FileMetaJpaEntity> {
    select(entity(FileMetaJpaEntity::class))
    from(entity(FileMetaJpaEntity::class))
    where(path(FileMetaJpaEntity::id).eq(id))
}

// save: JpaRepository 의존 (upsert 지원 한계 동일)
fileMetaJpaRepository.save(entity)

// updateStatus: Kotlin JDSL 3.x는 Select 쿼리에 집중되어 Bulk Update를 네이티브 지원하지 않음. 혼합안으로 CriteriaUpdate 사용 권장.
val cb = entityManager.criteriaBuilder
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

## 3. 추천 후보: Kotlin JDSL 3.x (또는 혼합안)
**추천 사유:** DSL 산출물을 별도 클래스로 노출하지 않아 경계 유출 원칙을 엄격하게 지킬 수 있습니다. Bulk Update 미지원 한계가 있으나 이는 JPA Criteria로 보완 가능하며, 전반적인 조회 쿼리의 타입 안전성을 플러그인 없이 확보합니다.

## 4. 🔶 채택 로드맵 및 사용자 결정
도입은 다음 조각 단위로 이루어져야 합니다.
1. **경계 복원:** `java-test-fixtures` 이관 및 `AutoConfiguration`을 통한 설정 독립.
2. **경계 가드:** `build.gradle`에 인프라 import 스캔 검증 태스크 추가.
3. **DSL 도입:** 어댑터 내 JPQL 쿼리를 Kotlin JDSL로 교체.

**🔶 사용자 결정 사항:**
1. **이중화 유지 여부:** JPA/MyBatis 중 JPA 단일로 통폐합할지 여부.
2. **JPA DSL 채택 여부:** Kotlin JDSL 3.x 도입 및 Criteria 병행 전략 승인 여부.
3. **MyBatis 대안 전환 여부:** 만약 MyBatis를 남길 경우 Dynamic SQL이나 jOOQ로 전환할지 여부.
