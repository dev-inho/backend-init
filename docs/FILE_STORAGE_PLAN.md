# 파일 저장 설계 및 구현 로드맵 (FILE_STORAGE_PLAN)

## 1. 개요 및 권고안

현재 프로젝트는 `client/storage-file` 모듈에 동기식(로컬 파일 시스템 의존) 덮어쓰기 기능만 제한적으로 구현되어 있으며, 실제 소비자나 업로드 웹 컨트롤러가 전무한 상태입니다(`docs/DEAD_CODE_CANDIDATES.md:14` 참고). 게이트웨이(`core/gateway/src/main/kotlin/cc/midolog/gateway/proxy/ProxyHandler.kt:59`)는 다운로드 요청에 대해 `bodyToMono(ByteArray::class.java)`로 전체 바이트를 메모리에 버퍼링하므로 대용량 파일 다운로드 시 Out-of-Memory(OOM)가 발생할 위험이 큽니다.

이러한 제약을 회피하고 작은 팀의 유지보수 비용을 줄이기 위해, **Presigned URL 직접 업로드/다운로드 기반 서버리스 I/O를 주력으로 하고 서버는 콜백(Finalize)을 통한 메타데이터만 처리하는 하이브리드 스트림 구조**를 권고합니다. 서버 스트리밍이 불가피한 경우(Fallback) WebFlux의 논블로킹 파일 스트림을 제한적으로 지원하되, 의존성 격리를 위해 인프라 계층을 신규 모듈로 분리하고 어플리케이션 기동 시 실패가 빠른(`fail-fast`) 설정을 적용합니다.

---

## 2. 포트 계약 구체화 및 도메인 모델 설계

### 2.1 스트림 추상화 대안 비교 및 추천

`core:domain` 모듈은 외부 프레임워크 비의존 원칙을 가지며 `build.gradle`의 `dependencies` 블록이 비어 있어야 합니다. 파일 I/O 스트리밍을 지원하기 위해 다음 대안을 검토합니다:

- **A. `kotlinx.coroutines.flow.Flow` 도입**: `org.jetbrains.kotlinx:kotlinx-coroutines-core` 의존성을 `core:domain`에 추가합니다. 코루틴 생태계의 표준 백프레셔(Backpressure)와 취소(Cancellation)를 손쉽게 활용할 수 있습니다. 단, 프레임워크 비의존 원칙이 훼손될 수 있습니다.
- **B. 순수 Kotlin 청크 추상화**: 외부 의존성 없이 `suspend fun readChunk(buffer: ByteArray): Int` 형태의 `ChunkReader` / `ChunkWriter` 인터페이스를 직접 선언하여 구현합니다. 설계자는 Close, Error, Backpressure 전파 책임을 직접 캡슐화해야 하는 부담을 안게 됩니다.

**추천안: B (순수 Kotlin 청크 추상화)**. 
작은 팀 초기 프로젝트에서 도메인 오염을 막기 위해 의존성 0을 유지하는 것이 중요합니다. 실제 대용량 트래픽의 99%는 Presigned URL 우회로 처리될 것이므로, Fallback 스트리밍을 위해 `Flow` 의존성을 전역 도메인에 허용하는 비용은 합리적이지 않습니다.

### 2.2 도메인 모델 및 포트 계약 구체화

업로드 대상과 권한을 명확히 분리하기 위해 `FilePresignPort`는 단순 URL 문자열이 아닌 HTTP Method, Expiry, 서명된 헤더(Required Headers) 제약이 모두 담긴 `PresignedRequest` 객체를 반환해야 합니다. `ChunkReader`는 자원 해제(`Closeable`)와 에러 전파(`cancel`) 소유권을 정의합니다. Checksum 계산 주체는 스트림을 마지막으로 처리하는 Infra 어댑터(S3 SDK 또는 Local FS)가 담당하여 최종 무결성을 보장합니다.

```kotlin
package cc.midolog.file.model

/**
 * 스토리지에 저장되는 파일의 메타데이터 불변 도메인 모델.
 */
data class StoredFile(
    val id: String, // 비즈니스 도메인 식별자 (ULID/UUID)
    val ownerId: String, // 파일 소유자 식별자 (인가 검증용)
    val storageKey: String, // 경로 순회(Path Traversal) 공격 방지를 위한 무작위 UUID 객체 키
    val sizeBytes: Long, // 확정된 파일 크기 (바이트)
    val contentType: String,
    val checksum: String?, // Infra 어댑터에서 계산 및 검증된 무결성 해시 (SHA-256 등)
    val status: FileStatus
)

/**
 * 클라이언트에게 발급할 제한된 권한의 통신 제약 명세.
 */
data class PresignedRequest(
    val url: String, // 서명된 스토리지 엔드포인트
    val method: String, // 허용된 HTTP 메서드 (예: PUT, GET)
    val expirationSeconds: Long, // 만료 시간 제약 (기본 15분)
    val requiredHeaders: Map<String, String> // 업로드 시 클라이언트가 강제 주입해야 할 서명 헤더 목록
)

enum class FileStatus { PENDING, READY, FAILED, DELETED }
```

```kotlin
package cc.midolog.file.port.storage

import cc.midolog.file.model.StoredFile
import cc.midolog.file.model.PresignedRequest
import java.io.Closeable

/**
 * 프레임워크 비의존 파일 청크 스트리밍 리더. 자원 해제 및 취소 소유권을 캡슐화.
 */

/**
 * 프레임워크 비의존 파일 청크 스트리밍 라이터. 파일 다운로드(서버 경유) 시 활용.
 */
interface ChunkWriter : Closeable {
    /**
     * 버퍼를 스토리지 또는 네트워크 응답 스트림으로 출력합니다.
     */
    suspend fun writeChunk(buffer: ByteArray, length: Int)
    
    suspend fun cancel(cause: Throwable?)
}
interface ChunkReader : Closeable {
    /**
     * 버퍼 크기만큼 스트림에서 데이터를 읽습니다.
     * @return 읽은 바이트 수, EOF 도달 시 -1 반환.
     */
    suspend fun readChunk(buffer: ByteArray): Int
    
    /**
     * 스트리밍 중 클라이언트 타임아웃이나 백프레셔 한계 도달 시 조기 자원 반환을 지시합니다.
     * @param cause 취소 원인 예외 객체
     */
    suspend fun cancel(cause: Throwable?)
}

interface FileStoragePort {
    /**
     * ChunkReader를 통해 스트림을 읽어 스토리지에 씁니다.
     * Checksum 계산 및 검증의 책임은 이 구현을 담당하는 Infra 어댑터에게 있습니다.
     * @param knownSize 크기를 미리 알고 있는 경우. null이면 동적 스트리밍(Chunked) 처리.
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
     * 이미 파일이 삭제되어 존재하지 않더라도 성공(true)을 반환해야 합니다.
     */
    suspend fun delete(key: String): Boolean
    
    suspend fun exists(key: String): Boolean
}

interface FilePresignPort {
    /**
     * 클라이언트가 파일을 업로드할 수 있도록 Presigned PUT URL을 발급합니다.
     * 보안을 위해 Content-Type을 고정하고 Expiry(기본 15분)를 부여합니다.
     */
    suspend fun presignUpload(
        key: String, 
        expirationSeconds: Long, 
        expectedSize: Long?, 
        contentType: String
    ): PresignedRequest
    
    /**
     * 클라이언트가 파일을 다운로드할 수 있도록 Presigned GET URL을 발급합니다.
     */
    suspend fun presignDownload(
        key: String, 
        expirationSeconds: Long
    ): PresignedRequest
}
```

---

## 3. 모듈 트리 구조 및 의존 방향 확정

기존 프로젝트의 `core:domain`, `core:application` 경계를 유지하여 헥사고날 아키텍처의 비즈니스 응집도를 보호합니다. 모든 도메인, 포트, 비즈니스 서비스, 웹 컨트롤러를 단일 `file-core` 모듈에 몰아넣는 방식은 헥사고날 경계를 파편화시킬 우려가 있으므로, **인프라 어댑터만 별도의 Bounded Context 모듈 트리로 캡슐화**합니다.

### 3.1 권고 모듈 트리 의존성 그래프

```text
core/
 ├── domain (file.model, file.port 순수 인터페이스 추가)
 └── application (file 비즈니스 서비스 및 file.web 컨트롤러, core:domain 의존)

storage/
 ├── file-local           # Local FS 어댑터 구현체 (core:domain 의존)
 ├── file-s3              # S3 SDK 어댑터 구현체 (core:domain 의존)
 ├── file-autoconfigure   # 자동 설정 팩토리 (compileOnly 로 local/s3 참조)
 ├── file-starter-local   # Local 전용 starter (autoconfigure + file-local api 의존)
 └── file-starter-s3      # S3 전용 starter (autoconfigure + file-s3 api 의존)
```

### 3.2 런타임 의존성 최소화 (Optional Classpath) 및 Fail-Fast
- `file-autoconfigure`가 `file-local`과 `file-s3`에 모두 `implementation`으로 강결합되면, 개발자가 선택하지 않은 SDK가 런타임에 포함되어 불필요한 빈 검사나 클래스 로딩 비용이 발생합니다.
- 이를 방지하기 위해 `file-autoconfigure`는 어댑터들을 `compileOnly`로만 참조하고, `@ConditionalOnClass`로 런타임 존재 여부를 파악하여 조건부 빈을 등록합니다.
- **Fail-fast 검증**: `file-autoconfigure` 내부의 `@ConfigurationProperties` Validator를 통해 `storage.file.provider` 환경 속성값이 정확히 `local` 또는 `s3`인지 어플리케이션 기동 시점에 검사합니다. 누락이나 오타 발견 시 즉각 예외를 던져 서비스 기동을 중단시킵니다.
- **빈 주입 안전성**: WebFlux 어플리케이션 컨텍스트에서 파일 의존성이 요구될 때, `file-starter-*` 중 하나만 주입되도록 보장하여 충돌을 막습니다.

---

## 4. 메타데이터 설계 및 상태 전이 (Finalize 정합성)

### 4.1 영속성 스키마 및 이중 어댑터 경계

메타데이터는 스토리지에 물리적으로 저장된 파일의 상태 추적과 클라이언트 쿼리 처리에 활용됩니다.


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
**테이블 설계 제안 (`file_meta`)**:
- `id` (PK, varchar): 비즈니스 식별자 (ULID 선호)
- `owner_id` (varchar): 파일을 업로드하고 소유하는 사용자 ID
- `storage_key` (varchar): 스토리지 버킷 내 객체 고유 경로 키 (UUID 기반)
- `status` (varchar): 현재 파일의 가용 상태 (PENDING, READY, FAILED, DELETED)
- `size_bytes` (bigint): 파일 크기. 초기엔 NULL일 수 있으나 READY 전환 시 갱신.
- `content_type` (varchar): 파일 MIME 타입
- `checksum` (varchar): 스토리지 무결성 해시값 (옵션)
- `created_at` (timestamp), `updated_at` (timestamp)

**마이그레이션 스크립트 작성 원칙**:
병렬 작업 시 스크립트 번호 충돌을 피하기 위해 마이그레이션 스크립트 파일명은 `V{next}__create_file_meta_table.sql` 형식을 따르며, 구현을 담당하는 워커가 커밋 직전 `origin/main` 분기를 fetch한 뒤 다음 사용 가능한 정수 번호를 부여합니다.

**MyBatis/JPA 이중화 경계**:
프로젝트 컨벤션에 따라 `storage:mybatis` 에는 `FileMetaMapper.xml`을, `storage:jpa`에는 Spring Data Repository 및 DSL 엔티티를 별도의 패키지 경계를 두고 구현합니다. 컨텍스트 기동 시 프로파일에 따라 둘 중 하나만 활성화되도록 제어하여 Dual-read(중복 로드) 문제를 원천 차단합니다.

### 4.2 상태 전이 및 정합성 보장 (위조 방어)

클라이언트의 콜백 API(Finalize) 호출을 맹신하면 파일 크기나 해시값이 위조되어 악의적인 대용량 파일이 무제한 방치될 수 있습니다. 이를 방어하기 위해 서버는 Finalize 과정에서 스토리지 백엔드(HEAD/Metadata API)를 직접 재조회하여 검증해야 합니다.

| 현재 상태 | 클라이언트 / 스케줄러 액션 | 서버 동작 (다음 상태 및 멱등성 보장) |
| --- | --- | --- |
| (없음) | **업로드 Presign 요청** | 고유 `storageKey` 생성 및 DB에 `PENDING` 레코드 생성. 서명된 `PresignedRequest` 반환. |
| `PENDING` | **Finalize API 호출 (성공 콜백)** | **[HEAD 검증]** 스토리지 백엔드에 객체 사이즈와 메타데이터 재조회. 일치 시 `READY` 전환. 불일치/실패 시 `FAILED` 전환 후 클라이언트 400 반환. 재시도 시 이미 `READY` 상태면 200 반환(멱등성). |
| `PENDING` | **TTL 만료 (고아 객체 정리)** | **[Orphan Sweep]** 스케줄러가 `updated_at` + TTL 지난 `PENDING` 객체 스캔. 재시작 가능한 체크포인트(페이징) 방식으로 읽어 DB `FAILED` 마킹 후 스토리지 물리 삭제 시도. |
| `READY` | **파일 삭제 요청** | **[순서 멱등성]** 스토리지 물리 객체 삭제 선행 → 실패 시 재시도 큐(또는 PENDING_DELETE), 성공 시 DB `DELETED` 마킹. |

---

## 5. 웹 계층 제약 및 WebFlux 스트리밍 대응

- **메모리 버퍼링 통제**: 서버 스트리밍 Fallback이 동작할 경우, Spring Boot 4.0.6 (WebFlux 환경) 공식 프로퍼티인 `spring.webflux.multipart.max-in-memory-size`를 설정하여 힙 메모리 고갈을 막아야 합니다. (기존 Servlet MVC의 `spring.servlet.multipart` 관련 프로퍼티는 Reactive 스택에서 동작하지 않으므로 무시됩니다.)
- **DataBuffer 해제 (Ownership)**: WebFlux의 `Flux<DataBuffer>` 처리 시 프레임워크 밖으로 넘어간 Netty ByteBuf는 GC에 의해 자동으로 해제되지 않습니다. 따라서 `ChunkReader`를 구현하는 Web 어댑터 계층은 읽기 완료, 에러 발생, 또는 백프레셔(Backpressure) 종료 시그널 시 반드시 `DataBufferUtils.release(buffer)`를 호출하여 메모리 누수를 100% 방지해야 합니다.
- **크기 상한 강제 (Size Limit)**: Presigned POST는 AWS Policy를 통해 `content-length-range`로 업로드 크기를 강제할 수 있습니다. 반면 본 설계가 추천하는 Presigned PUT 방식은 서명 시점 자체 크기 상한 강제가 어렵습니다. 따라서 클라이언트가 업로드를 완료하더라도 서버의 Finalize 단계(HEAD 재조회)에서 크기를 엄격히 사후 재검증하고, 초과 시 즉각 `FAILED` 처리 및 스토리지 객체를 삭제하는 방어 로직을 채택합니다.
- **향후 확장 고려 (Out of Scope)**: 서버 부하를 유발하는 매직 넘버 대조(Apache Tika) 및 비동기 이벤트 파이프라인이 필요한 악성코드 스캔(EventBridge + Lambda 연동)은 초기 복잡도를 높이므로 1차 구현 범위에서 완전히 배제하며, Phase 4 이후의 독립 워커 일감으로 연기합니다.

---

## 6. 🔶 사용자 결정 필요 항목 (Decision Records)

| 결정 항목 | 선택지 | 각 선택지의 영향 | 권고 기본값 |
| --- | --- | --- | --- |
| **Provider 지원 범위 (1차)** | 1) **Local 단독 우선 구축**<br>2) Local + S3 동시 구현 | S3 Testcontainers 설정, 비동기 SDK 튜닝 등 초기 구현 공수가 높음. 점진적 확장이 유리함. | **1) Local 기반 우선 구축 (S3는 Phase 4로 연기)** |
| **모듈 경계 및 의존 구조** | 1) **기존 `core:domain/application` 유지 + `file-starter`**<br>2) 신규 Bounded Context 분리 | 1)은 헥사고날 경계를 지키면서 인프라만 캡슐화함. 2)는 도메인 파편화 및 보일러플레이트 급증 우려. | **1) 기존 `core:domain/application` 유지** |
| **Presigned 직접 업로드** | 1) **직접 업로드(Finalize 백엔드 검증)**<br>2) 서버 스트리밍 경유 전용 | 1)은 게이트웨이 OOM을 영구히 차단하고 대역폭을 아끼나, 클라이언트-서버 콜백 구현이 강제됨. | **1) Presigned 직접 업로드 도입** |
| **업로드 방식 (PUT vs POST)**| 1) **Presigned PUT (Finalize 시 사후 검증)**<br>2) Presigned POST (사전 Policy 제한) | 1)은 서명 발급 및 SDK 호환이 단순함. 2)는 사전 차단이 강하나 클라이언트 폼 구현 제약이 존재함. | **1) Presigned PUT** |

---

## 7. 로드맵 (Phase 별 워커 브리프 크기 분할)

이 로드맵은 작은 팀 초기 프로젝트 환경에 맞춰 점진적 검증과 통합을 목표로 총 5단계(Phase 0 ~ 4)로 구성됩니다. 각 Phase는 완전히 독립된 하나의 워커 일감(Task) 단위로 실행될 수 있습니다.

### Phase 0: 심볼 보존 및 헥사고날 뼈대 분리 (난이도: flash)
- **내용**: 기존 데드 코드를 무작정 삭제하지 않고 **호환 Shim(어댑터)으로 유지**하여 기존 컴파일 의존성을 보호합니다. `core:domain`에 새 포트 인터페이스를 정의하고, `storage/file-autoconfigure` 및 `storage/file-starter-local` 모듈의 뼈대를 신규 생성합니다.
- **파일 경계**: `settings.gradle`, `core/domain/build.gradle`, `ChunkReader.kt`, `StoredFile.kt`, `FileStoragePort.kt`, 기존 `client/storage-file` 패키지의 `@Deprecated` 애노테이션 적용.
- **선행 조건**: 없음.
- **가드 테스트**: `DomainPurityTest`가 새 `cc.midolog.file.port.storage` 패키지 내 클래스들의 외부 라이브러리(Spring, Kotlinx Coroutines 등) 비의존성을 철저히 검증하여 통과해야 합니다.

### Phase 1: Local-first 인프라 및 자동 설정 구현 (난이도: pro)
- **내용**: (사용자 결정 1에 따라 S3를 배제하고) `storage/file-local` 모듈에 Local FS 전용 파일 I/O 구현체(`ChunkReader` 연동)를 작성합니다. `file-autoconfigure`에 프로바이더 설정 유효성을 기동 시 명시적으로 검증하는 커스텀 빈을 포함합니다.
- **파일 경계**: `LocalFileStorageAdapter.kt`, `LocalFilePresignAdapter.kt`(단순 로컬 디렉터리 경로 반환 에뮬레이션), `FileStoragePropertiesValidator.kt`, `spring.factories`.
- **선행 조건**: Phase 0 완료.
- **가드 테스트**: 설정 누락(`storage.file.provider=invalid`) 시 Fail-fast 어플리케이션 기동 실패 동작 확인. JUnit `@TempDir`을 활용한 대용량 파일 읽기/쓰기 모의 통합 테스트 100% 통과.

### Phase 2: 메타데이터 영속성 트랜잭션 전이 (난이도: pro)
- **내용**: 메타데이터의 영속성 관리를 위해 MyBatis 및 Spring Data JPA의 이중 어댑터를 작성하고 Flyway 마이그레이션 스크립트를 생성합니다. 영속성 계층은 직접적인 스토리지 I/O와 무관하게 도메인 상태 변경만 추적합니다.
- **파일 경계**: `core/application/src/main/resources/db/migration/V{next}__create_file_meta_table.sql`, `storage/mybatis/` 매퍼 XML, `storage/jpa/` 엔티티 및 리포지토리 인터페이스.
- **선행 조건**: Phase 1 완료.
- **가드 테스트**: 컨테이너가 불필요한 기본 `test` 테스크에서 H2 In-Memory DB로 매핑 DDL 자동 생성 검증. 분리된 `livePostgresTest` 태그 환경에서 PENDING -> READY 트랜잭션 상태 전이 및 롤백 시나리오를 통합 검증.

### Phase 3: Presign 발급 컨트롤러 및 고아 정리 훅 (난이도: pro)
- **내용**: `core:application` 모듈에 사용자 요청을 처리하는 웹 계층 컨트롤러를 열고, Finalize API에서 스토리지 포트를 조회해 크기/무결성을 이중 검증(Dual-read 방지)합니다. 스케줄러를 통해 만료된 PENDING 객체를 체크포인트 기반으로 페이징 스캔하여 일괄 삭제합니다.
- **파일 경계**: `cc.midolog.web.file.FileController.kt`, `FileService.kt`, `OrphanCleanupScheduler.kt`.
- **선행 조건**: Phase 2 완료 및 사용자 결정 3/4 확정.
- **가드 테스트**: 서버 스트리밍 Fallback 발생 시 `DataBufferUtils.release()` 호출을 Mock 객체의 Verification 기능으로 검증하여 메모리 누수가 없음을 증명. 스케줄러 Quartz/Cron 트리거 격리 테스트.

### Phase 4: S3 프로바이더 통합 및 Testcontainers 연동 (난이도: pro)
- **내용**: `storage/file-s3` 모듈 및 `file-starter-s3`를 신설하고, AWS SDK Java v2 (Netty Async) 기반의 완전 비동기 S3 어댑터를 구현합니다. `storage.file.provider=s3` 분기 자동 설정을 완성합니다.
- **파일 경계**: `storage/file-s3/build.gradle`, `S3FileStorageAdapter.kt`, `S3FilePresignAdapter.kt`, `MinIOTestcontainersConfig.kt`.
- **선행 조건**: Phase 3 안정화 및 로컬 운영 환경 배포 완료.
- **가드 테스트**: Mocking(S3Mock)을 지양하고 실제 엣지 케이스 재현율이 높은 **MinIO Testcontainers**를 도입하여 `liveS3Test` 환경에서 Presigned URL 발급, 파일 업로드, Finalize 엣지 통합 시나리오 100% 검증.
