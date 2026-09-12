# Storage Audit Report

## 1. 포트 계약과 어댑터 동작 동치성

### 1.1 계약 테스트 H2 어댑터 실행 여부
* **과거 감사 시점 파일:행 (현재 삭제됨):** `core/application/src/test/kotlin/cc/midolog/persistence/SampleRepositoryPortContractTest.kt:48`
* **원문:** `        val repository = Proxy.newProxyInstance(`
* **과거 감사 시점 파일:행 (현재 삭제됨):** `core/application/src/test/kotlin/cc/midolog/persistence/UserRepositoryPortContractTest.kt:56`
* **원문:** `        val repository = Proxy.newProxyInstance(`
* **과거 감사 시점 파일:행 (현재 삭제됨):** `core/application/src/test/kotlin/cc/midolog/persistence/FileMetaRepositoryPortContractTest.kt:146`
* **원문:** `        val repository = Proxy.newProxyInstance(`
* **판정:** 결함 (과거 감사 시점) -> **[복원됨 (PR #39, PR #43)]**
* **근거:** (과거) 실제 H2 어댑터를 주입하여 쿼리를 검증하지 않고, `Map`과 `Proxy` 기반 Fake 구현체로 동작하여 영속성 계층의 동작을 실증하지 못했음.
* **복원 근거 및 현재 경로:**
  - `core/application`의 레거시 Fake/Proxy 계약 테스트 파일은 전면 삭제되었습니다.
  - 공통 계약 키트는 `core/domain/src/testFixtures/kotlin/cc/midolog/{sample,user,file}/port/repository/*RepositoryPortContract.kt` 3종으로 복원되었습니다.
  - 실제 H2 DB 기반 계약 테스트는 각 저장소 모듈로 이관되어 총 6종의 어댑터 테스트가 실제 H2 환경에서 계약 동치성을 검증합니다:
    - `storage/jpa/src/test/kotlin/cc/midolog/storage/jpa/{sample,user,file}/*RepositoryPortContractTest.kt`
    - `storage/mybatis/src/test/kotlin/cc/midolog/storage/mybatis/{sample,user,file}/*RepositoryPortContractTest.kt`

### 1.2 어댑터 6종 계약(null 반환, upsert, 트랜잭션, IO 격리, 예외 변환) 전수 비교 표

| 구분 | JPA 어댑터 (Sample/User/FileMeta) | MyBatis 어댑터 (Sample/User/FileMeta) |
|---|---|---|
| **Null 반환** | `storage/jpa/src/main/kotlin/cc/midolog/storage/jpa/sample/JpaSampleRepositoryAdapter.kt:32`<br>`                .orElse(null)` | `storage/mybatis/src/main/kotlin/cc/midolog/storage/mybatis/sample/MyBatisSampleRepositoryAdapter.kt:24`<br>`        sampleMapper.selectById(id)?.let {` |
| **Upsert** | `storage/jpa/src/main/kotlin/cc/midolog/storage/jpa/file/JpaFileMetaRepositoryAdapter.kt:32`<br>`                fileMetaJpaRepository.save(FileMetaJpaMapper.toEntity(file)),`<br>*(단순 upsert가 아님, Spring Data JPA의 new 판정에 따른 persist/merge 수행)* | `storage/mybatis/src/main/kotlin/cc/midolog/storage/mybatis/user/MyBatisUserRepositoryAdapter.kt:34`<br>`        val affectedRows = userMapper.upsert(user.id, user.email, user.displayName)`<br>*(DB 네이티브 ON CONFLICT 등 사용)* |
| **트랜잭션** | `storage/jpa/src/main/kotlin/cc/midolog/storage/jpa/user/JpaUserRepositoryAdapter.kt:37`<br>`        transactionOperations.execute {`<br>*(WebFlux 스레드 로컬 문제 방지)* | `storage/mybatis/src/main/kotlin/cc/midolog/storage/mybatis/sample/MyBatisSampleRepositoryAdapter.kt:29`<br>`    override suspend fun save(sample: Sample): Sample = withContext(Dispatchers.IO) {`<br>*(어댑터 내 `@Transactional` 및 `TransactionOperations` 모두 부재)* |
| **IO 격리** | `storage/jpa/src/main/kotlin/cc/midolog/storage/jpa/user/JpaUserRepositoryAdapter.kt:36`<br>`    override suspend fun save(user: User): User = withContext(Dispatchers.IO) {` | `storage/mybatis/src/main/kotlin/cc/midolog/storage/mybatis/user/MyBatisUserRepositoryAdapter.kt:33`<br>`    override suspend fun save(user: User): User = withContext(Dispatchers.IO) {` |
| **예외 변환** | `storage/jpa/src/main/kotlin/cc/midolog/storage/jpa/sample/JpaSampleRepositoryAdapter.kt:41`<br>`        } ?: error("JPA sample save transaction returned no result")` | `storage/mybatis/src/main/kotlin/cc/midolog/storage/mybatis/user/MyBatisUserRepositoryAdapter.kt:35`<br>`        check(affectedRows == 1) {` |

- **트랜잭션 부재 문제:** MyBatis 어댑터는 조회/저장에 모두 `@Transactional`이나 `TransactionOperations`가 부재하여 트랜잭션 경계가 성립하지 않는 결함이 있습니다.
- **예외 변환 한계:** JPA 어댑터는 트랜잭션 콜백 null 발생 시 하드코딩된 예외를 던지며, MyBatis는 `affectedRows == 1`을 검사하여 예외를 발생시키나, 이는 동시성 제어가 아니며 단순 기대 행 수 검증에 불과합니다.

## 2. 문자열 JPQL 및 MyBatis 쿼리 비교

### 2.1 SELECT 및 UPDATE 상태/조건 쿼리
* **파일:행:** `storage/jpa/src/main/kotlin/cc/midolog/storage/jpa/file/JpaFileMetaRepositoryAdapter.kt:53`
* **원문:** `                "SELECT e FROM FileMetaJpaEntity e WHERE e.status = :status AND e.updatedAt < :cutoff ORDER BY e.updatedAt ASC",`
* **파일:행:** `storage/jpa/src/main/kotlin/cc/midolog/storage/jpa/file/JpaFileMetaRepositoryAdapter.kt:40`
* **원문:** `                "UPDATE FileMetaJpaEntity e SET e.status = :status, e.updatedAt = :now WHERE e.id = :id"`
* **판정:** 결함
* **근거:** JPA 어댑터는 SELECT/UPDATE 모두 컴파일 타임 검증이 불가능한 문자열 JPQL을 사용하며, LIMIT은 코드 레벨(`query.maxResults`)로 제어합니다.

### 2.2 MyBatis 결과 매핑 위험 (DEAD_CODE #12)
* **파일:행:** `storage/mybatis/src/main/resources/mapper/file/FileMetaMapper.xml:6`
* **원문:** `    <select id="selectById" resultType="map">`
* **판정:** 결함
* **근거:** MyBatis는 `resultType="map"`과 수동 `toDomain` 별칭(`AS "ownerId"` 등)에 의존하여 매핑이 깨지기 쉽습니다. `DEAD_CODE_CANDIDATES.md` #12에서 지적된 바와 같이, 설정 중복 및 Map 자동 변환 한계를 내포합니다.

## 3. Instant.now() 호출 일관성
* **파일:행:** `storage/jpa/src/main/kotlin/cc/midolog/storage/jpa/file/JpaFileMetaRepositoryAdapter.kt:43`
* **원문:** `            query.setParameter("now", java.time.Instant.now())`
* **파일:행:** `storage/mybatis/src/main/kotlin/cc/midolog/storage/mybatis/file/MyBatisFileMetaRepositoryAdapter.kt:41`
* **원문:** `        mapper.updateStatus(id, status.name, Instant.now()) > 0`
* **파일:행:** `core/application/src/main/kotlin/cc/midolog/business/config/ClockConfig.kt:19`
* **원문:** `        return Clock.systemUTC()`
* **판정:** 결함 (과거 감사 시점) -> **[복원됨 / 진행 중]**
* **근거:** 실제 `ClockConfig`에 의해 `clock.instant()` 주입이 가능함에도 `Instant.now()` 하드코딩을 사용하여 테스트 시간 의존성 문제가 있습니다.
* **복원 근거 및 현재 상태:**
  - `application`은 PR #42(`refactor/fileservice-clock`)에서 `FileService`에 `Clock`을 주입하여 해결되었으며, 고아 정리 서비스(`FileOrphanCleanupService`)도 주입된 `Clock`을 사용하여 제어됩니다.
  - storage 어댑터는 PR #43 포트 계약 테스트(`JpaFileMetaRepositoryPortContractTest`, `MyBatisFileMetaRepositoryPortContractTest`)에서 `clock()` 검증을 거치도록 어댑터 생성 시 `Clock` 주입 구조가 정비되었습니다.

## 4. JPA DSL 생성기 품질 확인
* **파일:행:** `storage/jpa/build/generated/sources/jpaDsl/main/kotlin/cc/midolog/storage/jpa/file/FileMetaJpaRepository.kt:5`
* **원문:** `interface FileMetaJpaRepository : JpaRepository<FileMetaJpaEntity, String>`
* **파일:행:** `storage/jpa/build/generated/sources/jpaDsl/main/kotlin/cc/midolog/storage/jpa/file/FileMetaJpaMapper.kt:7`
* **원문:** `        FileMetaJpaEntity(`
* **파일:행:** `storage/jpa/build/generated/sources/jpaDsl/main/kotlin/cc/midolog/storage/jpa/file/FileMetaJpaEntity.kt:14`
* **원문:** `class FileMetaJpaEntity(`
* **판정:** 개선
* **근거:** 엔티티 필드는 nullable 여부에 맞게 잘 생성되나 Mapper가 수동 매핑하여 스키마 변경 시 누락 위험이 있습니다. 관계 미존재로 현재 N+1 이슈는 없으나 추후 연관관계 설정 시 페치 전략 자동 생성이 필요합니다.

## 5. 포트 추가/이중화 유지보수성 평가
PR #24 기준, 포트 1건 추가 시 20개 파일이 변경되며 이는 스토리지 경로 13개(JPA 5개, MyBatis 8개)와 그 외 7개로 분류됩니다.

### 5.1 이중화 유지 및 통폐합 후보 추천 비교안
* **유지:** 공통 7개 파일을 포함해 JPA/MyBatis 총 20개 파일을 모두 유지하므로 테스트 및 구현 비용이 매우 큽니다.
* **JPA 단일 (추천):** MyBatis 관련 8개 파일을 제거할 수 있습니다. 객체지향적 도메인 매핑과 타입 안전성 확보가 용이합니다.
* **MyBatis 단일:** JPA 관련 5개 파일을 제거할 수 있습니다. 단, XML 및 Map 기반 매핑 의존과 SQL 수동 유지보수로 인해 생산성이 떨어집니다.
* **다른 조합:** Spring Data JDBC 등은 단순 매핑이 강점이나 추가 학습 비용 및 생태계 차이가 존재합니다.

## 6. 경계 유출 (Boundary Leak) 전수 점검

**상위 원칙:** “JPA·MyBatis 관심사는 `storage/jpa`, `storage/mybatis` 밖으로 나오지 않는다.”

### 6.1 테스트/설정 코드 유출 전수 목록 (과거 감사 시점 결함)
* **직접 구현체 Import (테스트) -> [복원됨 (PR #39, PR #43)]:**
  * 과거 감사 대상 파일 (현재 삭제됨):
    * `core/application/src/test/kotlin/cc/midolog/persistence/FileMetaRepositoryPortContractTest.kt:6`<br>`import cc.midolog.storage.jpa.file.FileMetaJpaEntity`
    * `core/application/src/test/kotlin/cc/midolog/persistence/PersistenceProfileContextTest.kt:5`<br>`import cc.midolog.storage.jpa.sample.JpaSampleRepositoryAdapter`
    * `core/application/src/test/kotlin/cc/midolog/persistence/UserRepositoryPortContractTest.kt:3`<br>`import cc.midolog.storage.jpa.user.JpaUserRepositoryAdapter`
    * `core/application/src/test/kotlin/cc/midolog/persistence/SampleRepositoryPortContractTest.kt:5`<br>`import cc.midolog.storage.jpa.sample.JpaSampleRepositoryAdapter`
    * `core/application/src/test/kotlin/cc/midolog/persistence/MyBatisProfileContextTest.kt:5`<br>`import cc.midolog.storage.mybatis.sample.SampleMapper`
  * **복원 근거:** 위 5개 테스트 파일은 전면 삭제되었으며, 현재 `core/application` 내에는 concrete storage 어댑터 import가 0건입니다. 신규 `PersistenceBoundaryTest` 가드가 재유출을 원천 차단합니다.
* **프레임워크 설정 문자열 유출 -> [복원됨 (PR #39)]:**
  * `core/application/src/test/kotlin/cc/midolog/storage/FileStorageIntegrationTest.kt:28`<br>`            "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration," +`
    * *판정:* 허용 예외 (import가 아닌 자동 설정 제외용 프로퍼티 값 문자열 리터럴).
  * 실제 `application.yml` 설정 키 유출: `spring.data.jpa.repositories.enabled: false`, `mybatis.mapper-locations` 등 인프라 설정이 전역 앱 설정에 노출되었던 결함.
    * *복원 근거:* 전역 `application.yml`에서 JPA/MyBatis 설정 키가 전면 제거되었으며, 각 storage 모듈의 `resources/application-jpa.yml` 및 `resources/application-mybatis.yml`로 격리 이전되어 설정 소유권이 복원되었습니다.
* **모듈 의존성 유출 -> [복원됨 (PR #39, PR #43)]:**
  * `core/application/build.gradle:37-38` (과거 `testImplementation` -> 현행 `testRuntimeOnly`로 복원):
    * 과거: `testImplementation project(':storage:mybatis')`, `testImplementation project(':storage:jpa')`
    * 현행: `testRuntimeOnly project(':storage:mybatis')`, `testRuntimeOnly project(':storage:jpa')`, `testRuntimeOnly "com.h2database:h2"`
    * *복원 근거:* 테스트 컴파일 클래스패스에서 storage 모듈이 제거되어 컴파일 타임 import가 원천 불가능해졌습니다.
  * `core/application/build.gradle:15-16` (`runtimeOnly project(':storage:mybatis')`, `runtimeOnly project(':storage:jpa')`):
    * *판정:* 허용 예외 (애플리케이션 실행 조합 루트(composition root)로서 런타임 classpath에 어댑터를 싣는 유일한 정당한 자리).
* **KDoc 유출:**
  * `core/application/src/test/kotlin/cc/midolog/infra/security/SecurityConfigTest.kt:18`<br>` * DataSource/MyBatis/Redis 등 실제 인프라 의존 없이 인증 동작을 검증한다.` (문서 목적이므로 예외 허용)
* **빌드 로직 허용 예외 논의:**
  * `build-logic/src/main/kotlin/cc/midolog/buildlogic/jpadsl/JpaDslRenderer.kt:137`<br>`import org.springframework.data.jpa.repository.JpaRepository` (공통 빌드 도구이므로 소스 스캔 대상 밖 명시적 예외)

### 6.2 경계 복원 및 가드 추천 (구현 완료 현황)
* **계약 테스트 복원 [구현 완료 (PR #39, PR #43)]:** `core:domain`의 `src/testFixtures`에 3개 포트 계약 키트(`*RepositoryPortContract`)를 배치하고, 각 저장소 모듈(`storage:jpa`, `storage:mybatis`)에서 이를 상속받아 H2 환경에서 실제 어댑터를 실행하는 계약 테스트 6종으로 완전 이관되었습니다.
* **설정 복원 [구현 완료 (PR #39)]:** 전역 `application.yml`의 JPA/MyBatis 프로퍼티들을 각 스토리지 모듈 내부 `resources/application-{profile}.yml`로 이전하여 모듈별 설정 소유권을 확립했습니다.
* **가드 도입 [구현 완료]:** `core/application/src/test/kotlin/cc/midolog/PersistenceBoundaryTest.kt`를 도입하여 `core/**`, `gateway/**`, `support/**`, `client/**` 모듈의 실제 소스 트리 전체를 스캔해 `jakarta.persistence`, `org.springframework.data.jpa`, `org.hibernate`, `org.mybatis`, `org.apache.ibatis`, `cc.midolog.storage.jpa`, `cc.midolog.storage.mybatis` import 유입을 영구 차단합니다.
