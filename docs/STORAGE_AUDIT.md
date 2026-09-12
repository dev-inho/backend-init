# Storage Audit Report

## 1. 포트 계약과 어댑터 동작 동치성

### 1.1 계약 테스트 H2 어댑터 실행 여부
* **파일:행:** `core/application/src/test/kotlin/cc/midolog/persistence/FileMetaRepositoryPortContractTest.kt:144`
* **원문:** `    private fun jpaAdapter(): FileMetaRepositoryPort {`
* **판정:** 결함
* **근거:** 실제 H2 어댑터를 주입하여 쿼리를 검증하지 않고, `Map`과 `Proxy` 기반 Fake 구현체로 동작하여 영속성 계층의 동작을 실증하지 못한다.

### 1.2 트랜잭션 정책 및 I/O 격리, 예외 변환, null 반환, upsert
**JPA 어댑터 (Sample/User/FileMeta)**
* **파일:행:** `storage/jpa/src/main/kotlin/cc/midolog/storage/jpa/sample/JpaSampleRepositoryAdapter.kt:29`
* **원문:** `        transactionOperations.execute {`
* **파일:행:** `storage/jpa/src/main/kotlin/cc/midolog/storage/jpa/sample/JpaSampleRepositoryAdapter.kt:36`
* **원문:** `        } ?: error("JPA sample save transaction returned no result")`
* **판정:** 양호/결함 혼재
* **근거:** 블로킹 I/O를 `withContext(Dispatchers.IO)`로 격리하고 코루틴 환경에서 `TransactionOperations.execute`를 통해 트랜잭션을 관리하는 점은 양호하다(모든 JPA 어댑터 공통). 하지만 save 결과가 null일 때 런타임 예외를 던지며, upsert 대신 기본 persist/merge를 수행하므로 MyBatis의 upsert와 동치성이 맞지 않는다.

**MyBatis 어댑터 (Sample/User/FileMeta)**
* **파일:행:** `storage/mybatis/src/main/kotlin/cc/midolog/storage/mybatis/sample/MyBatisSampleRepositoryAdapter.kt:26`
* **원문:** `        check(affectedRows == 1) {`
* **판정:** 양호
* **근거:** I/O 격리가 되어 있으며, upsert 쿼리를 호출하고 반환된 영향 행 수가 1이 아닐 경우 예외를 던져 동시성 제어 및 무결성을 보장한다.

## 2. 문자열 JPQL 및 MyBatis 쿼리 비교

### 2.1 SELECT 및 UPDATE 상태/조건 쿼리
* **파일:행:** `storage/jpa/src/main/kotlin/cc/midolog/storage/jpa/file/JpaFileMetaRepositoryAdapter.kt:53`
* **원문:** `                "SELECT e FROM FileMetaJpaEntity e WHERE e.status = :status AND e.updatedAt < :cutoff ORDER BY e.updatedAt ASC",`
* **파일:행:** `storage/jpa/src/main/kotlin/cc/midolog/storage/jpa/file/JpaFileMetaRepositoryAdapter.kt:41`
* **원문:** `                "UPDATE FileMetaJpaEntity e SET e.status = :status, e.updatedAt = :now WHERE e.id = :id"`
* **판정:** 결함
* **근거:** JPA 어댑터는 SELECT/UPDATE 모두 컴파일 타임 검증이 불가능한 문자열 JPQL을 사용하며, LIMIT은 코드 레벨(`query.maxResults`)로 제어한다. 반면 MyBatis는 XML (`FileMetaMapper.xml:61` 등)에서 LIMIT과 정렬을 명시적으로 처리한다. 양쪽 모두 비관적/낙관적 잠금(Lock) 처리는 누락되어 있어 만료 처리 시 동시성 이슈 가능성이 있다.

## 3. Instant.now() 호출 일관성

* **파일:행:** `storage/jpa/src/main/kotlin/cc/midolog/storage/jpa/file/JpaFileMetaRepositoryAdapter.kt:43`
* **원문:** `            query.setParameter("now", java.time.Instant.now())`
* **판정:** 결함
* **근거:** `ClockConfig`에 의한 시간 주입(`clock.instant()`) 대신 `Instant.now()` 하드코딩이 존재해 테스트 시간에 의존성이 발생한다.

## 4. JPA DSL 생성기 품질 확인

* **파일:행:** `storage/jpa/build/generated/sources/jpaDsl/main/kotlin/cc/midolog/storage/jpa/file/FileMetaJpaMapper.kt:6`
* **원문:** `        FileMetaJpaEntity(`
* **파일:행:** `storage/jpa/build/generated/sources/jpaDsl/main/kotlin/cc/midolog/storage/jpa/file/FileMetaJpaEntity.kt:14`
* **원문:** `class FileMetaJpaEntity(`
* **판정:** 개선
* **근거:** 엔티티 필드는 nullable 여부에 맞게 잘 생성되나 Mapper가 모든 필드를 수동 매핑하여 스키마 변경 시 누락 위험이 있다. 현재 `FileMeta` 등 엔티티에는 관계(`@OneToMany` 등)가 없어 N+1 문제는 당장 발생하지 않으나, 향후 관계 추가 시 연관관계 페치 전략 자동 생성을 고려해야 한다.

## 5. 포트 추가/이중화 유지보수성 평가

PR #24에서 포트 1건 추가 시 총 20개의 파일이 변경되었다.
- **공통/빌드/계약 (13개):** 도메인, 인터페이스, 계약 테스트 등
- **이중화로 인한 추가 비용 (7개):**
  1. `JpaFileMetaRepositoryAdapter.kt`
  2. `JpaFileMetaRepositoryAdapterTest.kt`
  3. `FileMetaMapper.kt`
  4. `MyBatisFileMetaRepositoryAdapter.kt`
  5. `FileMetaMapper.xml`
  6. `MyBatisFileMetaMapperH2Test.kt`
  7. `MyBatisFileMetaRepositoryAdapterTest.kt`

어댑터 1세트를 위해 최소 7개의 파일이 추가로 생성(JPA 2개, MyBatis 5개)된다.

### 5.1 이중화 유지 및 통폐합 후보 추천
- **추천안 (JPA 단일):** 객체지향적 도메인 매핑과 타입 안전성(Kotlin JDSL 도입 시) 확보가 용이하며 컴파일 타임 검증을 통해 20개 중 5개의 MyBatis 파일 유지보수 비용을 아낄 수 있다.

## 6. 경계 유출 (Boundary Leak) 전수 점검

**상위 원칙:** “JPA·MyBatis 관심사는 `storage/jpa`, `storage/mybatis` 밖으로 나오지 않는다.”

### 6.1 테스트 코드 및 빌드 설정의 유출 전수 목록 (결함)
직접 스캔 결과 다음과 같은 유출이 발견되었다:
- **직접 구현체 import (테스트):**
  - `core/application/src/test/kotlin/cc/midolog/persistence/FileMetaRepositoryPortContractTest.kt:6` 원문: `import cc.midolog.storage.jpa.file.FileMetaJpaEntity`
  - `core/application/src/test/kotlin/cc/midolog/persistence/PersistenceProfileContextTest.kt:5` 원문: `import cc.midolog.storage.jpa.sample.JpaSampleRepositoryAdapter`
  - `core/application/src/test/kotlin/cc/midolog/persistence/UserRepositoryPortContractTest.kt:3` 원문: `import cc.midolog.storage.jpa.user.JpaUserRepositoryAdapter`
  - `core/application/src/test/kotlin/cc/midolog/persistence/SampleRepositoryPortContractTest.kt:5` 원문: `import cc.midolog.storage.jpa.sample.JpaSampleRepositoryAdapter`
  - `core/application/src/test/kotlin/cc/midolog/persistence/MyBatisProfileContextTest.kt:5` 원문: `import cc.midolog.storage.mybatis.sample.SampleMapper`
- **프레임워크 설정 문자열/의존성 유출:**
  - `core/application/src/test/kotlin/cc/midolog/storage/FileStorageIntegrationTest.kt:28` 원문: `            "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration," +`
  - `core/application/build.gradle:37` 원문: `    testImplementation project(':storage:mybatis')`
- **KDoc 예외:**
  - `core/application/src/test/kotlin/cc/midolog/common/security/SecurityConfigTest.kt:18` 원문: ` * DataSource/MyBatis/Redis 등 실제 인프라 의존 없이 인증 동작을 검증한다.` (KDoc이므로 허용 가능)
- **빌드 로직 허용 예외 논의:**
  - `build-logic/src/main/kotlin/cc/midolog/buildlogic/jpadsl/JpaDslRenderer.kt:137` 원문: `import org.springframework.data.jpa.repository.JpaRepository`
  - `build-logic`은 공통 도구이므로 `storage/jpa` 안으로 옮기거나 전역 예외로 명시적 허용이 필요하다.
- **런타임 조합 허용 예외:**
  - `core/application/build.gradle:15` 원문: `    runtimeOnly project(':storage:mybatis')` (애플리케이션 진입점에서의 조합이므로 예외 허용)

### 6.2 경계 복원 및 가드 추천
- **계약 테스트 복원 (추천):** `domain`의 `java-test-fixtures`에 포트 계약 테스트 키트를 두고 각 `storage` 모듈이 의존성을 역전시켜 자기 어댑터로 상속 실행하도록 한다. 별도 `storage/contract-tests` 모듈을 만드는 것보다 도메인 응집도가 높다.
- **설정 복원 (추천):** 각 storage 모듈이 `AutoConfiguration.imports`로 자기 모듈의 `@PropertySource` 기본값 및 설정을 소유하도록 구성하고, `application.yml`에는 프로파일 활성화(`spring.profiles.active`)만 남긴다.
- **가드 도입 (추천):** 루트 `build.gradle`의 `verification` 태스크에서 스캔을 수행하여 `storage/jpa`, `storage/mybatis` 외부의 모듈(main+test)이 `jakarta.persistence`, `org.mybatis`, `cc.midolog.storage.(jpa|mybatis)` 패키지를 import 시 빌드 실패를 유도한다. 이는 core/application 테스트 단계보다 더 빠르고 원칙적으로 캡슐화를 강제한다.
