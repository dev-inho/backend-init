# 파일 저장 설계 및 구현 로드맵 (FILE_STORAGE_PLAN)

## 1. 개요 및 권고안

현재 프로젝트는 `client/storage-file` 모듈에 동기식(로컬 파일 시스템 의존) 덮어쓰기 기능만 제한적으로 구현되어 있으며, 실제 소비자나 업로드 웹 컨트롤러가 전무한 상태입니다(`docs/DEAD_CODE_CANDIDATES.md:14` 참고). 게이트웨이(`gateway/core/src/main/kotlin/cc/midolog/gateway/proxy/ProxyHandler.kt:58`)는 다운로드 요청에 대해 `bodyToMono(ByteArray::class.java)`로 전체 바이트를 메모리에 버퍼링하므로 대용량 파일 다운로드 시 Out-of-Memory(OOM)가 발생할 위험이 큽니다.

이러한 제약과 관련하여 작은 팀의 단계별 확장 전략을 다음과 같이 권고합니다:
1. **Local provider의 한계 및 1차 스트리밍**: Local 파일 시스템은 브라우저에 임시 서명 URL을 발급할 수 있는 엔드포인트나 권한 체계가 없으며, 서버의 로컬 파일 시스템 경로를 클라이언트에 노출하는 에뮬레이션은 성립하지 않고 보안상으로도 금지됩니다. 따라서 1차 단계인 Local-first 구현 시에는 **서버 경유 WebFlux 논블로킹 스트리밍(업로드/다운로드)**으로 동작합니다. 단, 게이트웨이(`ProxyHandler`)의 `ByteArray` 응답 버퍼링 제약으로 인해 대용량 다운로드 시 OOM 위험이 일시적으로 수반됨을 인정하고 감수합니다.
2. **S3 도입 및 최종 구조**: Phase 4에서 S3 프로바이더가 추가되면서 비로소 **Presigned URL 직접 업로드/다운로드 기반 서버리스 I/O**가 활성화됩니다. 클라이언트는 스토리지를 직통하고 서버는 콜백(Finalize)을 통한 메타데이터 검증만 수행하므로 게이트웨이 OOM과 트래픽 병목이 근본적으로 해소됩니다.
3. **의존성 격리 및 Fail-Fast**: 인프라 계층을 신규 모듈 트리로 분리하고 Spring Boot 4 표준 자동 설정 체계를 도입하여, 잘못된 provider 설정 시 애플리케이션 기동 시점에 즉각 실패(`fail-fast`)하도록 구성합니다.

---

## 2. 포트 계약 구체화 및 도메인 모델 설계

### 2.1 스트림 추상화 대안 비교 및 추천

`core:domain` 모듈은 외부 프레임워크 비의존 원칙을 가지며 `build.gradle`의 `dependencies` 블록이 비어 있어야 합니다. 파일 I/O 스트리밍을 지원하기 위해 다음 대안을 검토합니다:

- **A. `kotlinx.coroutines.flow.Flow` 도입**: `org.jetbrains.kotlinx:kotlinx-coroutines-core` 의존성을 `core:domain`에 추가합니다. 코루틴 생태계의 표준 백프레셔(Backpressure)와 취소(Cancellation)를 손쉽게 활용할 수 있습니다. 단, 프레임워크 비의존 원칙이 훼손될 수 있습니다.
- **B. 순수 Kotlin 청크 추상화**: 외부 의존성 없이 `suspend fun readChunk(buffer: ByteArray): Int` 형태의 `ChunkReader` / `ChunkWriter` 인터페이스를 직접 선언하여 구현합니다. 설계자는 Close, Error, Backpressure 전파 책임을 직접 캡슐화해야 하는 부담을 안게 됩니다.

**추천안: B (순수 Kotlin 청크 추상화)**. 
작은 팀 초기 프로젝트에서 도메인 오염을 막기 위해 의존성 0을 유지하는 것이 중요합니다. 향후 S3 전환 시 대부분의 트래픽이 Presigned URL로 우회될 것이므로, 도메인 모듈에 외부 코루틴 라이브러리를 강제할 이유가 없습니다.

### 2.2 도메인 모델 및 포트 계약 구체화

`PresignedRequest`는 S3 등 서명 capability를 가진 원격 스토리지 전용으로 설계하며 HTTP Method, Expiry, 서명 헤더 목록을 캡슐화합니다. Local provider는 presign capability를 지원하지 않으며(호출 시 예외 또는 미지원 플래그), 서버 경유 WebFlux 스트리밍(`ChunkReader` / `ChunkWriter`)으로 I/O를 처리합니다.

```kotlin
package cc.midolog.file.model

data class StoredFile(
    val id: String, // 비즈니스 도메인 식별자 (ULID)
    val ownerId: String, // 소유자 식별자 (인가 검증용)
    val storageKey: String, // 경로 순회 방지용 무작위 UUID 키
    val sizeBytes: Long, // 확정된 파일 크기 (바이트)
    val contentType: String,
    val checksum: String?, // Infra 어댑터에서 계산/검증된 해시
    val status: FileStatus
)

data class PresignedRequest(
    val url: String, // 서명된 스토리지 엔드포인트
    val method: String, // 허용된 HTTP 메서드 (PUT, GET)
    val expirationSeconds: Long, // 서명 유효 만료 시간
    val requiredHeaders: Map<String, String> // 업로드 시 강제 주입할 서명 헤더 목록
)

enum class FileStatus { PENDING, READY, FAILED, DELETED }
```

```kotlin
package cc.midolog.file.port.storage

import cc.midolog.file.model.StoredFile
import cc.midolog.file.model.PresignedRequest
import java.io.Closeable

/**
 * 프레임워크 비의존 파일 청크 스트리밍 라이터.
 * 파일 다운로드 또는 서버 경유 스트리밍 시 네트워크 출력에 바이트를 씁니다.
 */
interface ChunkWriter : Closeable {
    /**
     * 버퍼를 스토리지 또는 네트워크 응답 스트림으로 출력합니다.
     */
    suspend fun writeChunk(buffer: ByteArray, length: Int)
    
    /**
     * 스트리밍 중 예외 발생 시 하위 자원을 정리하고 조기 중단을 전파합니다.
     */
    suspend fun cancel(cause: Throwable?)
}

/**
 * 프레임워크 비의존 파일 청크 스트리밍 리더.
 * 파일 업로드 또는 서버 경유 스토리지 로드 시 청크 단위로 데이터를 읽습니다.
 */
interface ChunkReader : Closeable {
    /**
     * 버퍼 크기만큼 스트림에서 데이터를 읽습니다.
     * @return 읽은 바이트 수, EOF 도달 시 -1 반환.
     */
    suspend fun readChunk(buffer: ByteArray): Int
    
    /**
     * 클라이언트 타임아웃이나 백프레셔 한계 도달 시 조기 자원 반환을 지시합니다.
     */
    suspend fun cancel(cause: Throwable?)
}

interface FileStoragePort {
    /**
     * ChunkReader를 통해 스트림을 읽어 스토리지에 영속화합니다.
     * Checksum 계산 및 검증의 책임은 이 구현을 담당하는 Infra 어댑터에게 있습니다.
     * @param knownSize 크기를 미리 알고 있는 경우. null이면 동적 청크 스트리밍 처리.
     */
    suspend fun store(
        key: String, 
        reader: ChunkReader, 
        knownSize: Long?, 
        contentType: String, 
        expectedChecksum: String?
    ): StoredFile
    
    /**
     * 지정된 key의 객체를 읽기 위한 ChunkReader를 반환합니다.
     * 객체가 없으면 null을 반환하고, 권한이나 네트워크 에러 시 예외를 던집니다.
     */
    suspend fun load(key: String): ChunkReader?
    
    /**
     * 객체 삭제를 수행하며 멱등성을 보장합니다.
     * 대상 파일이 이미 존재하지 않더라도 성공(true)을 반환해야 합니다.
     */
    suspend fun delete(key: String): Boolean
    
    /**
     * 스토리지 내 객체 존재 여부를 확인합니다.
     */
    suspend fun exists(key: String): Boolean
}

/**
 * 객체 스토리지(S3/R2) 전용 Capability 포트.
 * Local provider 환경에서는 지원되지 않으며 호출 시 UnsupportedOperationException을 발생시킵니다.
 */
interface FilePresignPort {
    /** 현재 스토리지 프로바이더의 Presign 지원 여부 */
    val isSupported: Boolean

    /** 클라이언트가 파일을 직접 업로드할 수 있도록 Presigned PUT 명세를 발급합니다. */
    suspend fun presignUpload(
        key: String, 
        expirationSeconds: Long, 
        expectedSize: Long?, 
        contentType: String
    ): PresignedRequest
    
    /** 클라이언트가 파일을 직접 다운로드할 수 있도록 Presigned GET 명세를 발급합니다. */
    suspend fun presignDownload(
        key: String, 
        expirationSeconds: Long
    ): PresignedRequest
}
```

---

## 3. 모듈 트리 구조 및 의존 방향 확정

기존 프로젝트의 `core:domain`, `core:application` 경계를 유지하여 헥사고날 아키텍처의 비즈니스 응집도를 보호합니다. 모든 도메인, 포트, 비즈니스 서비스, 웹 컨트롤러를 단일 모듈에 몰아넣는 방식은 계층 경계를 무너뜨리므로, **인프라 어댑터만 별도의 스토리지 모듈 트리로 캡슐화**합니다.

> **코디네이터 결정: 1차는 `storage/file-local` 단일 모듈, 5모듈은 S3 도입 때**

### 3.1 권고 모듈 트리 의존성 그래프

```text
core/
 ├── domain (file.model, file.port 순수 도메인 계약 추가, 의존성 0)
 └── application (file 비즈니스 서비스 및 WebFlux 컨트롤러, core:domain 의존)

storage/
 ├── file-local           # Local FS 어댑터 구현체 (core:domain 의존)
 ├── file-s3              # S3 SDK 비동기 어댑터 구현체 (core:domain 의존)
 ├── file-autoconfigure   # 자동 설정 팩토리 (compileOnly 로 local/s3 참조)
 ├── file-starter-local   # Local 전용 starter (autoconfigure + file-local 의존)
 └── file-starter-s3      # S3 전용 starter (autoconfigure + file-s3 의존)
```

### 3.2 Spring Boot 4 표준 자동 설정 및 Fail-Fast

- **AutoConfiguration.imports 표준 채택**: Spring Boot 4.0.6 규격 및 L1 게이트웨이 starter 패턴과 통일하여 `META-INF/spring.factories`를 배제하고 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`에 자동 구성 FQCN(`cc.midolog.storage.file.autoconfigure.FileStorageAutoConfiguration`)을 1줄로 선언합니다.
- **`@AutoConfiguration` 및 `@ConditionalOnClass`**: 자동 구성 진입점을 선언하고 클래스패스에 `LocalFileStorageAdapter` 또는 AWS SDK `S3AsyncClient`가 존재할 때만 해당 어댑터 빈을 조건부로 등록합니다.
- **`@ConfigurationProperties` 유효성 검증 (Fail-Fast)**: `storage.file.provider` (`local` | `s3`) 값을 바인딩하고 기동 시점에 허용되지 않은 값이거나 필수 속성이 누락되었을 때 즉시 `IllegalStateException`을 발생시켜 애플리케이션 기동을 즉각 차단합니다.

---

## 4. 메타데이터 설계 및 상태 전이 (Finalize 정합성)

### 4.1 영속성 스키마 및 이중 어댑터(JPA/MyBatis) 배선

```sql
CREATE TABLE file_meta (
    id VARCHAR(26) PRIMARY KEY, -- ULID
    owner_id VARCHAR(100) NOT NULL,
    storage_key VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    size_bytes BIGINT,
    content_type VARCHAR(100),
    checksum VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uk_file_meta_storage_key ON file_meta (storage_key);
CREATE INDEX idx_file_meta_status_updated ON file_meta (status, updated_at);
```

**JPA DSL 생성기 배선 (`storage/jpa/build.gradle`)**:
`storage/jpa/build.gradle`의 `jpaDsl` 블록에 `FileMeta` 엔티티 설정을 추가하여 컴파일 시점에 JPA 메타 모델을 자동 생성합니다:

```groovy
jpaDsl {
    entity('cc.midolog.file.model.FileMeta') {
        table = 'file_meta'
        id = 'id'
        field('ownerId') { column = 'owner_id' }
        field('storageKey') { column = 'storage_key' }
        field('status') { enumStrategy = 'STRING' }
        field('sizeBytes') { column = 'size_bytes'; nullable = true }
        field('contentType') { column = 'content_type'; nullable = true }
        field('checksum') { nullable = true }
        field('createdAt') { column = 'created_at' }
        field('updatedAt') { column = 'updated_at' }
    }
}
```

**MyBatis 및 JPA 이중 어댑터 파일 경계**:
- **도메인 포트**: `core/domain/src/main/kotlin/cc/midolog/file/port/repository/FileMetaRepositoryPort.kt`
- **JPA 어댑터 계층**:
  - `storage/jpa/src/main/kotlin/cc/midolog/storage/jpa/file/FileMetaJpaRepository.kt` (Spring Data JPA)
  - `storage/jpa/src/main/kotlin/cc/midolog/storage/jpa/file/JpaFileMetaRepositoryAdapter.kt` (포트 구현체)
- **MyBatis 어댑터 계층**:
  - `storage/mybatis/src/main/resources/mapper/file/FileMetaMapper.xml`
  - `storage/mybatis/src/main/kotlin/cc/midolog/storage/mybatis/file/FileMetaMapper.kt`
  - `storage/mybatis/src/main/kotlin/cc/midolog/storage/mybatis/file/MyBatisFileMetaRepositoryAdapter.kt` (포트 구현체)
- **테스트 및 로드 격리**: 기본 `test` 태스크에서는 H2 In-Memory DB로 DSL 매핑을 검증하고, 별도 `livePostgresTest` 태스크에서 실제 PostgreSQL 트랜잭션 전이 및 롤백을 검증합니다. 컨텍스트 기동 시 프로파일에 따라 단 하나의 어댑터만 활성화하여 Dual-read 문제를 원천 차단합니다.

### 4.2 상태 전이 및 정합성 보장 (위조 방어)

클라이언트의 콜백 API(Finalize) 호출을 맹신하면 파일 크기나 해시값이 위조될 수 있습니다. 이를 방어하기 위해 서버는 Finalize 과정에서 스토리지 백엔드(HEAD API 또는 Local File 메타데이터)를 직접 재조회하여 검증해야 합니다.

| 현재 상태 | 액션 주체 및 동작 | 서버 동작 (다음 상태 및 멱등성 보장) |
| --- | --- | --- |
| (없음) | **업로드 요청 (Presign 또는 스트리밍)** | 고유 `storageKey` 생성 및 DB에 `PENDING` 레코드 생성. 서명된 `PresignedRequest` 또는 업로드 스트림 수신. |
| `PENDING` | **Finalize API 호출 (성공 콜백)** | **[HEAD/메타데이터 검증]** 스토리지 백엔드에 객체 사이즈와 메타데이터 재조회. 일치 시 `READY` 전환. 불일치/실패 시 `FAILED` 전환 후 400 반환. 재시도 시 이미 `READY` 상태면 200 반환(멱등성). |
| `PENDING` | **TTL 만료 (고아 객체 정리)** | **[Orphan Sweep]** 스케줄러가 `updated_at` + TTL 지난 `PENDING` 객체를 페이징 기반 체크포인트로 스캔. DB `FAILED` 마킹 후 스토리지 물리 삭제. |
| `READY` | **파일 삭제 요청** | **[순서 멱등성]** 스토리지 물리 객체 삭제 선행 → 성공 시 DB `DELETED` 마킹. 실패 시 재시도 큐 발행. |

---

## 5. 웹 계층 제약 및 WebFlux 스트리밍 대응

- **메모리 버퍼링 통제**: 서버 스트리밍 Fallback 및 Local-first 환경에서 Spring Boot 4.0.6 공식 WebFlux 프로퍼티인 `spring.webflux.multipart.max-in-memory-size`를 설정하여 힙 메모리 고갈을 차단합니다.
- **DataBuffer 해제 (Ownership)**: WebFlux의 `Flux<DataBuffer>` 처리 시 NettyByteBuf 등 풀 기반 버퍼는 GC에 의해 자동으로 해제되지 않습니다. 따라서 `ChunkReader`를 구현하는 Web 어댑터 계층은 읽기 완료, 에러 발생, 또는 취소 시그널 시 반드시 `DataBufferUtils.release(buffer)`를 호출하여 메모리 누수를 방지합니다.
- **Local 다운로드 응답 방식**: Local provider 환경에서 다운로드 요청 시 `ChunkReader`를 통해 스트림 단위로 청크를 읽어 WebFlux `ServerResponse.ok().contentType(...).body(BodyInserters.fromDataBuffers(...))` 형태로 스트리밍 응답을 전송합니다. 단, 게이트웨이 `ProxyHandler`의 다운로드 버퍼링 제약으로 인한 OOM 한계는 존재합니다.
- **크기 상한 강제 (Size Limit)**: Presigned PUT 및 Local 스트리밍 환경에서 클라이언트가 보고한 크기를 맹신하지 않고, 스트림 수신 중 한계 초과 시 즉각 중단하거나 Finalize 사후 검증(HEAD 조회)을 통해 즉시 `FAILED` 마킹 후 객체를 삭제합니다.

---

## 6. ✅ 확정 결정 기록 (Decision Records)

| 결정 항목 | 확정 내용 및 근거 | 확정 상태 |
| --- | --- | --- |
| **Provider 지원 범위 (1차)** | **Local 단독 우선 구축 확정**<br>외부 인프라 비용/공수 없이 로컬 환경 및 개발 사이클을 우선 확보하며, S3는 추후 Phase로 연기. | **확정 (구현 완료)** |
| **모듈 경계 및 의존 구조** | **기존 `core:domain/application` 유지 + `storage/file-local` 단일 모듈 확정**<br>도메인 분리로 인한 보일러플레이트를 줄이고 헥사고날 경계를 유지. 5모듈 세분화는 S3 도입 시점에 진행. | **확정 (구현 완료)** |
| **업로드 접근 방식** | **하이브리드 접근 방식 확정**<br>1차 Local 환경은 서버 경유 논블로킹 스트리밍으로 구현 완료되었으며, 2차 S3 확장 시 Presigned 업로드로 전환. | **확정 (1차 완료)** |
| **S3 업로드 방식 (Phase 4)** | **Presigned PUT (Finalize 사후 검증) 확정**<br>향후 S3 도입 시 단순 URL 발급 및 클라이언트 호환이 용이한 Presigned PUT 방식 채택 예정. | **확정 (S3 도입 시 적용)** |

---

## 7. 로드맵 (Phase 별 워커 브리프 크기 분할)

이 로드맵은 점진적 검증과 통합을 목표로 총 5단계(Phase 0 ~ 4)로 구성되며, 각 Phase는 독립된 하나의 워커 브리프 단위입니다.

### Phase 0: 심볼 보존 및 헥사고날 뼈대 분리 (✅ 완료)
- **내용**: 기존 데드 코드를 무작정 삭제하지 않고 호환 Shim으로 유지하여 빌드 무결성을 보호합니다. `core:domain`에 새 포트 인터페이스를 정의하고, `storage/file-autoconfigure` 및 `storage/file-starter-local` 모듈의 뼈대를 신규 생성합니다.
- **파일 경계**: `settings.gradle`, `core/domain/build.gradle`, `ChunkReader.kt`, `StoredFile.kt`, `FileStoragePort.kt`, `FilePresignPort.kt`, 기존 `client/storage-file`의 `@Deprecated` 어댑터.
- **선행 조건**: 없음.
- **가드 테스트**: `DomainPurityTest`가 새 `cc.midolog.file.port` 패키지 내 클래스들의 외부 라이브러리 비의존성을 검증하여 100% 통과해야 합니다.

### Phase 1: Local-first 인프라 및 서버 경유 스트리밍 구현 (✅ 완료)
- **내용**: (사용자 결정 1에 따라 S3를 배제하고) `storage/file-local` 모듈에 Local FS 전용 파일 I/O 구현체(`LocalFileStorageAdapter.kt`)를 작성합니다. 로컬 환경은 presign capability를 지원하지 않으므로, `core:application`에 서버 경유 WebFlux 논블로킹 스트리밍(업로드 `Multipart`/`DataBuffer` 소비, 다운로드 `DataBuffer` 응답) 컨트롤러를 구현합니다. 게이트웨이 `ProxyHandler` 버퍼링 제약이 존재함을 명시합니다. `file-autoconfigure`에 `AutoConfiguration.imports` 기반의 자동 구성을 배치하고, 기동 시 provider 설정 유효성을 검사하여 잘못된 값이면 즉각 실패(`fail-fast`)합니다.
- **파일 경계**: `LocalFileStorageAdapter.kt`, `FileStoragePropertiesValidator.kt`, `FileStorageAutoConfiguration.kt`, `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`, `FileStreamingController.kt`.
- **선행 조건**: Phase 0 완료.
- **가드 테스트**: 설정 누락/오타(`storage.file.provider=invalid`) 시 Fail-fast 기동 실패 검증. JUnit `@TempDir`을 활용한 대용량 파일 읽기/쓰기 모의 통합 테스트 통과.

### Phase 2: 메타데이터 영속성 트랜잭션 전이 (✅ 완료)
- **내용**: 메타데이터 영속성 관리를 위해 `storage/jpa/build.gradle`의 `jpaDsl` 블록에 `FileMeta` 엔티티를 배선하고, MyBatis 매퍼 XML 및 리포지토리 어댑터를 작성합니다. Flyway 마이그레이션 스크립트를 생성합니다.
- **파일 경계**: `V{next}__create_file_meta_table.sql`, `storage/jpa/build.gradle`, `FileMetaJpaRepository.kt`, `JpaFileMetaRepositoryAdapter.kt`, `FileMetaMapper.xml`, `FileMetaMapper.kt`, `MyBatisFileMetaRepositoryAdapter.kt`.
- **선행 조건**: Phase 1 완료.
- **가드 테스트**: 기본 `test` 태스크에서 H2 In-Memory DB로 자동 생성된 JPA DSL 매핑 스모크 테스트 수행. 분리된 `livePostgresTest` 태스크에서 PENDING -> READY 트랜잭션 상태 전이 및 롤백 검증.

### Phase 3: Finalize 검증 컨트롤러 및 고아 정리 훅 (난이도: pro)
- **내용**: `core:application` 모듈에 Finalize 콜백 API를 열어 스토리지 계층을 조회하고 크기/무결성을 이중 검증합니다. 스케줄러를 통해 만료된 PENDING 객체를 페이징 기반 체크포인트 방식으로 스캔하여 일괄 정리합니다.
- **파일 경계**: `FileFinalizeController.kt`, `FileFinalizeService.kt`, `OrphanCleanupScheduler.kt`.
- **선행 조건**: Phase 2 완료.
- **가드 테스트**: Web 어댑터에서 스트림 처리 완료/취소 시 `DataBufferUtils.release()` 호출을 단위 테스트로 검증. 스케줄러 페이징 쿼리 격리 테스트.

### Phase 4: S3 프로바이더 통합 및 Testcontainers 연동 (난이도: pro)
- **내용**: `storage/file-s3` 모듈 및 `file-starter-s3`를 신설하고, AWS SDK Java v2 (Netty Async) 기반의 S3 어댑터 및 `S3FilePresignAdapter.kt`를 구현합니다. Presigned 직접 업로드/다운로드를 전면 활성화하여 게이트웨이 OOM을 근본적으로 해소합니다.
- **파일 경계**: `storage/file-s3/build.gradle`, `S3FileStorageAdapter.kt`, `S3FilePresignAdapter.kt`, `MinIOTestcontainersConfig.kt`.
- **선행 조건**: Phase 3 안정화 완료.
- **가드 테스트**: **MinIO Testcontainers**를 도입하여 별도의 `liveS3Test` 환경에서 Presigned URL 발급, 파일 업로드, Finalize 통합 시나리오 100% 검증.
