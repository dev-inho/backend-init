# Storage Audit Report

## 1. 포트 계약과 어댑터 동작 동치성

### 1.1 계약 테스트 H2 어댑터 실행 여부
* **파일:행:** `core/application/src/test/kotlin/cc/midolog/persistence/FileMetaRepositoryPortContractTest.kt:144`
* **원문:** `    private fun jpaAdapter(): FileMetaRepositoryPort {`
* **판정:** 결함
* **근거:** 실제 H2 어댑터를 주입하여 쿼리를 검증하지 않고, `Map`과 `Proxy` 기반 Fake 구현체로 동작하여 영속성 계층의 동작(쿼리, 제약조건 등)을 실증하지 못한다.

### 1.2 트랜잭션 정책 및 I/O 격리
* **파일:행:** `storage/jpa/src/main/kotlin/cc/midolog/storage/jpa/file/JpaFileMetaRepositoryAdapter.kt:29`
* **원문:** `    override suspend fun save(file: FileMeta): FileMeta = withContext(Dispatchers.IO) {`
* **판정:** 양호
* **근거:** WebFlux의 이벤트 루프를 막지 않기 위해 블로킹 I/O를 `withContext(Dispatchers.IO)`로 격리하고 있으며, 코루틴에서 ThreadLocal을 사용하는 `@Transactional` 대신 `TransactionOperations.execute`를 명시적으로 호출해 트랜잭션 경계를 안전하게 제어하고 있다. (모든 어댑터에 공통 적용됨)

## 2. 문자열 JPQL 및 MyBatis 쿼리 비교

### 2.1 상태 조건 및 LIMIT 처리
* **파일:행:** `storage/jpa/src/main/kotlin/cc/midolog/storage/jpa/file/JpaFileMetaRepositoryAdapter.kt:53`
* **원문:** `                "SELECT e FROM FileMetaJpaEntity e WHERE e.status = :status AND e.updatedAt < :cutoff ORDER BY e.updatedAt ASC",`
* **판정:** 개선
* **근거:** MyBatis는 XML을 통해 `LIMIT` 등 쿼리를 작성하나, JPA는 위와 같이 문자열 JPQL을 사용 후 코드 레벨(`query.maxResults`)로 제어한다. 컴파일 타임 검증 불가 결함이 존재한다.

## 3. Instant.now() 호출 일관성

### 3.1 ClockConfig vs 하드코딩
* **파일:행:** `storage/jpa/src/main/kotlin/cc/midolog/storage/jpa/file/JpaFileMetaRepositoryAdapter.kt:43`
* **원문:** `            query.setParameter("now", java.time.Instant.now())`
* **판정:** 결함
* **근거:** `ClockConfig` 빈(주입받은 `clock.instant()`)을 사용하지 않고 `Instant.now()`를 하드코딩하여 시간에 의존적인 테스트 작성을 방해한다.

## 4. JPA DSL 생성기 품질 확인

### 4.1 생성된 리포지토리 및 엔티티
* **파일:행:** `storage/jpa/build/generated/sources/jpaDsl/main/kotlin/cc/midolog/storage/jpa/file/FileMetaJpaRepository.kt:5`
* **원문:** `interface FileMetaJpaRepository : JpaRepository<FileMetaJpaEntity, String>`
* **판정:** 개선
* **근거:** 엔티티, 리포지토리는 생성되나 `@EntityGraph`나 `FetchType.LAZY`와 같은 연관관계 N+1 방지 전략이 명시되지 않았다. `FileMetaJpaMapper.kt`는 모든 필드를 수동 매핑하여 스키마 변경 시 필드 누락 위험이 높다.

## 5. 포트 추가/이중화 유지보수성 평가

PR #24에서 포트 1건 추가 시 총 20개의 파일이 변경되었다.
- **공통/빌드/계약 (13개):** 도메인 모델, 포트 인터페이스, 계약 테스트, DB 마이그레이션, 빌드 파일 등.
- **어댑터 구현/유지 비용 (7개):** `JpaFileMetaRepositoryAdapter.kt`, `JpaFileMetaRepositoryAdapterTest.kt`, `FileMetaMapper.kt`, `MyBatisFileMetaRepositoryAdapter.kt`, `FileMetaMapper.xml`, `MyBatisFileMetaMapperH2Test.kt`, `MyBatisFileMetaRepositoryAdapterTest.kt`.
이 7개(설정 테스트 포함 8개) 파일은 순수하게 "이중화 유지"를 위해 중복 작성해야 하는 비용이다.

### 5.1 이중화 유지 및 통폐합 후보 추천
- **유지:** JPA와 MyBatis의 장점을 모두 취할 수 있으나 포트 추가 시마다 어댑터 2벌, 테스트 2벌, XML을 유지해야 하므로 생산성이 낮다.
- **JPA 단일 (추천):** 객체지향적 도메인 매핑과 타입 안전성(K-JDSL 도입 시) 확보가 용이하며 컴파일 타임 검증이 가능하므로 유지보수 비용을 크게 낮출 수 있다.
- **MyBatis 단일:** 복잡한 SQL 작성에 유리하나 CRUD 매핑 오버헤드가 높다.
- **다른 조합:** Spring Data JDBC 등은 WebFlux와 궁합이 좋으나 생태계가 작다.

## 6. 경계 유출 (Boundary Leak) 전수 점검

**상위 원칙:** “JPA·MyBatis 관심사는 `storage/jpa`, `storage/mybatis` 밖으로 나오지 않는다.”
메인 코드 스캔(`grep -rn cc.midolog.storage.jpa`) 결과, 0건 유출(KDoc 예외 제외)을 확인했다. 하지만 테스트와 설정 영역에서 다수 유출되었다.

### 6.1 테스트 코드 및 빌드 설정의 유출 전수 목록 (결함)
- `core/application/src/test/kotlin/cc/midolog/persistence/FileMetaRepositoryPortContractTest.kt:6` - `import cc.midolog.storage.jpa.file.FileMetaJpaEntity`
- `core/application/src/test/kotlin/cc/midolog/persistence/SampleRepositoryPortContractTest.kt:5` - `import cc.midolog.storage.jpa.sample.JpaSampleRepositoryAdapter`
- `core/application/src/test/kotlin/cc/midolog/persistence/UserRepositoryPortContractTest.kt:3` - `import cc.midolog.storage.jpa.user.JpaUserRepositoryAdapter`
- `core/application/src/test/kotlin/cc/midolog/persistence/PersistenceProfileContextTest.kt:5` - `import cc.midolog.storage.jpa.sample.JpaSampleRepositoryAdapter`
- `core/application/src/test/kotlin/cc/midolog/persistence/MyBatisProfileContextTest.kt:5` - `import cc.midolog.storage.mybatis.sample.SampleMapper`
- `core/application/src/test/kotlin/cc/midolog/storage/FileStorageIntegrationTest.kt:28` - `            "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration," +`
- `core/application/src/test/kotlin/cc/midolog/common/security/SecurityConfigTest.kt:18` - ` * DataSource/MyBatis/Redis 등 실제 인프라 의존 없이 인증 동작을 검증한다.` (KDoc 예외)
- `core/application/build.gradle:37` - `    testImplementation project(':storage:mybatis')`
- `core/application/build.gradle:15` - `    runtimeOnly project(':storage:mybatis')` (조합 예외)
- `core/application/src/main/resources/application.yml:4` - `spring:` (아래 data.jpa 설정)

### 6.2 경계 복원 및 가드 추천
- **계약 테스트 복원 (추천):** `domain`의 `java-test-fixtures`에 포트 계약 테스트 키트(추상 클래스 및 hook)를 두고, 각 storage 모듈이 이를 상속받아 자기 인프라 어댑터로 실행하도록 이관한다.
- **설정 복원 (추천):** 각 storage 모듈이 `AutoConfiguration.imports` 기반으로 자기 모듈의 자동 구성 및 `@PropertySource` 기본값을 소유하고, `application.yml`에는 `spring.profiles.active=jpa`만 남긴다.
- **가드 도입 (추천):** 루트 `build.gradle`의 `verification` 태스크에 스캔 스크립트나 ArchUnit을 두어, `storage` 외부 모듈이 `cc.midolog.storage.(jpa|mybatis)`나 인프라 패키지를 import할 때 실패(`"인프라 계층 캡슐화 위반"`)하도록 강제한다.
