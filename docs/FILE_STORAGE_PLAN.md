# 파일 저장 설계 및 구현 로드맵 (FILE_STORAGE_PLAN)

## 1. 개요 및 권고안

현재 사용되지 않는 동기식 파일 저장 코드를 확장 가능한 파일 스토리지 아키텍처로 개편합니다.
게이트웨이(`ProxyHandler`)의 `ByteArray` 응답 버퍼링 제약(OOM 위험)을 우회하기 위해, 작은 팀의 1차 구축 범위를 **Presigned URL 직접 업로드/다운로드 기반의 서버리스 I/O 통신망**으로 한정하고 서버는 메타데이터 콜백(Finalize)만 처리하는 하이브리드 S3 호환 모델을 권고합니다.

## 2. 포트 계약 구체화 및 도메인 모델

### 2.1 스트림 추상화 대안 비교 및 추천
`core:domain`은 프레임워크 비의존 원칙을 가지며 `build.gradle` 의존성이 0입니다. 스트리밍을 지원하기 위해 다음 대안을 비교합니다.
- **A. `Flow` 도입**: `org.jetbrains.kotlinx:kotlinx-coroutines-core` 의존성을 추가합니다. 코루틴 생태계의 표준 백프레셔와 취소를 활용할 수 있으나 순수성 원칙이 완화됩니다.
- **B. 순수 Kotlin 청크 추상화**: `suspend fun read(buffer: ByteArray): Int` 형태의 `ChunkReader`/`ChunkWriter` 인터페이스를 정의합니다. 외부 의존성은 0이지만 close/오류 처리/백프레셔를 직접 구현해야 하는 부담이 있습니다.

**추천**: **B (순수 Kotlin 청크 추상화)**. 도메인 오염을 막기 위해 의존성 0을 유지하며, 데이터 크기가 큰 파일 스트리밍은 Presigned URL 우회로 최소화되므로 A의 비용을 들일 필요가 적습니다.

### 2.2 도메인 모델 및 포트 인터페이스 설계
```kotlin
package cc.midolog.file.model

data class StoredFile(
    val id: String, // 도메인 식별자
    val ownerId: String,
    val storageKey: String, // 스토리지 경로(UUID 등)
    val sizeBytes: Long,
    val contentType: String,
    val checksum: String?, 
    val status: FileStatus
)

enum class FileStatus { PENDING, READY, FAILED, DELETED }
```

```kotlin
package cc.midolog.file.port.storage

import cc.midolog.file.model.StoredFile

interface FileStoragePort {
    // known/unknown size 대응, contentType/checksum 검증을 포함해 메타데이터까지 반환
    suspend fun store(key: String, reader: ChunkReader, knownSize: Long?, contentType: String, expectedChecksum: String?): StoredFile
    
    // not-found 시 null 반환, 인프라 에러 시 예외 발생
    suspend fun load(key: String): ChunkReader?
    
    // 멱등성 보장 (이미 삭제된 경우에도 true)
    suspend fun delete(key: String): Boolean
    
    suspend fun exists(key: String): Boolean
}

interface FilePresignPort {
    suspend fun presignUpload(key: String, expirationSeconds: Long, expectedSize: Long?, contentType: String): String
    suspend fun presignDownload(key: String, expirationSeconds: Long): String
}
```

## 3. 모듈 트리 구조 및 의존 방향 확정

게이트웨이의 `starter` 패턴을 차용하되, 책임과 의존 방향을 명확히 분리한 모듈 트리입니다.

```text
client:storage-file (기존 모듈은 Phase 0에서 삭제 또는 deprecate)

file/
 ├── file-core           # 도메인(StoredFile), 포트 인터페이스, 비즈니스 서비스, 웹 컨트롤러 (타 인프라 의존성 없음)
 ├── file-local          # Local FS 전용 어댑터 구현체 (file-core 의존)
 ├── file-s3             # AWS SDK 기반 S3/MinIO 어댑터 구현체 (file-core 의존)
 ├── file-autoconfigure  # 조건부 빈 설정, ConfigurationProperties 검증 (core, local, s3 의존)
 └── file-starter        # 프로젝트 의존성 묶음 (autoconfigure 의존)
```

- **Fail-fast 검증**: `@ConditionalOnProperty`의 fallback 우려를 막기 위해 `file-autoconfigure` 내부에 `@ConfigurationProperties`와 커스텀 Validator 빈을 두어 기동 시점에 `storage.file.provider`가 `local` 또는 `s3`인지 명시적으로 검증(실패 시 기동 중단)합니다.

## 4. 메타데이터 상태 전이와 인프라 정합성

### 4.1 상태 전이 및 콜백(Finalize) 위조 방지
단순 콜백을 맹신하면 클라이언트가 파일 크기나 체크섬을 위조할 수 있습니다. 

| 현재 상태 | 액션 | 다음 상태 | 설명 및 멱등성/보상 동작 |
| --- | --- | --- | --- |
| (없음) | Presign 요청 | `PENDING` | DB에 `PENDING` 레코드 생성 후 서명된 URL 발급. |
| `PENDING` | Finalize API 호출 | `READY` | **백엔드가 스토리지 객체 메타데이터(HEAD)를 조회**하여 크기 및 체크섬 일치를 확인한 후 `READY`로 전환. 실패 시 `FAILED`. 재시도 멱등성 보장. |
| `PENDING` | TTL 만료 (Orphan Sweep) | `FAILED` | 스케줄러가 오래된 `PENDING`을 `FAILED`로 일괄 변경 및 스토리지 고아 객체 삭제 시도. |
| `READY` | 파일 삭제 요청 | `DELETED` | 스토리지 물리 삭제 선행 -> 성공 시 DB `DELETED` 마킹. 실패 시 재시도 큐 적재. |

### 4.2 영속성 마이그레이션
Flyway 마이그레이션 스크립트명은 충돌 방지를 위해 **"구현 시 origin/main fetch 후 다음 사용 가능한 번호"**를 사용합니다. (예: `V{next}__create_file_meta_table.sql`).

MyBatis를 위한 `FileMetaMapper` 인터페이스와 `file-core`에 `FileMetaRepositoryPort`를 둡니다.

## 5. 웹 계층 설계 제약 및 보안

- **스트리밍 제약 책임**: WebFlux 환경이므로 서블릿 스펙인 `spring.servlet.multipart...`가 아닌 `spring.webflux.multipart.max-memory-size`로 메모리 상한을 통제합니다. `DataBuffer` 누수를 막기 위해 `DataBufferUtils.release`와 백프레셔 전파를 코드 내에 보장해야 합니다.
- **Presigned URL 보안**: 단순 업로드가 아닌 보안 제약을 부여합니다. Method(PUT), 15분 만료(Expiration), 허용 Content-Length 한도, Content-Type 일치 조건, Private Bucket 설정 및 객체 소유권 검증을 서명 시 포함합니다.
- **경로 순회 방지**: 요청된 원본 파일명은 DB에만 보관하고 실제 스토리지 Key는 `UUID.randomUUID().toString()`을 활용합니다.
- **작은 팀 1차 범위 구분**: 바이러스 스캔(EventBridge+Lambda), 매직 넘버 대조(Tika)는 1차 범위에서 제외하고 향후 도입 가능성만 열어둡니다.

## 6. 테스트 전략

- **Local**: 기본 `test` 테스크에서 Docker 없이 JUnit `@TempDir` 및 H2/fake 매핑 생성 검증을 수행합니다.
- **DB/JPA**: Phase 2의 JPA DSL/Flyway 매핑 검증은 Docker를 띄우고 `livePostgresTest` 태그를 활용하는 방식으로 분리하여 기본 CI 속도를 유지합니다.
- **S3 호환성**: 인메모리 S3Mock보다 실제 엣지 케이스 재현율이 높은 **MinIO Testcontainers**를 도입하며, 이 역시 컨테이너가 필요하므로 `liveS3Test`와 같은 별도 태스크로 분리하여 실행합니다.

## 7. 🔶 사용자 결정 필요 (Decision Records)

| 결정 항목 | 선택지 | 각 선택지의 영향 | 권고 기본값 |
| --- | --- | --- | --- |
| **모듈 위치** | 1) `file/{core,autoconfigure,starter}` 구조 도입<br>2) 기존 `client/storage-file` 재사용 | 1)은 Gateway와 일관된 인프라 캡슐화 제공. 2)는 빠른 구현. | **1) `file-starter` 구조 도입** |
| **Provider 지원 범위 (1차)** | 1) Local 먼저 구축<br>2) Local + S3 동시 | 2)는 S3 비동기 설정과 컨테이너 테스트 등 공수 증가. | **1) 작은 팀이므로 Local 먼저 구축 후 S3 확장** |
| **Presigned URL 처리** | 1) 직접 업로드(서버는 메타데이터만 확인)<br>2) 서버 스트리밍 경유 | 1)은 Gateway 버퍼링 OOM을 원천 차단하고 서버 대역폭을 아낌. | **1) Presigned 직접 업로드** |
| **메타데이터 분리 컨텍스트** | 1) 독립된 `file` 컨텍스트 테이블<br>2) `sample`/`user`에 직접 결합 | 1)은 도메인 횡단 파일 관리 시 결합도를 낮춰 마이크로서비스 확장에 유리. | **1) 독립된 `file` 컨텍스트 테이블** |

## 8. 로드맵 (Phase별 분할)

### Phase 0: 모듈 뼈대 및 의존성 격리 (난이도: Flash)
- **목표**: `client/storage-file`를 향후 마이그레이션을 위해 Deprecate(Shim 유지)하고 `file/{core,local,s3,autoconfigure,starter}` 뼈대 생성.
- **파일 경계**: `settings.gradle`, 각 모듈 `build.gradle`, `StoredFile`, 순수 Kotlin 청크 추상화 인터페이스.
- **선행 조건**: 없음.
- **가드 테스트**: `DomainPurityTest`가 새 `file-core` 도메인 패키지의 순수성 유지 검증.

### Phase 1: 파일 I/O 프로바이더 구현 (난이도: Pro)
- **목표**: `file-local`, `file-s3` 어댑터 구현 및 `file-autoconfigure`의 Provider Fail-fast 설정.
- **파일 경계**: `LocalFileStorageAdapter`, `S3FileStorageAdapter` (AWS SDK Async API), `FileStorageProperties` validator.
- **선행 조건**: Phase 0 완료.
- **가드 테스트**: `@TempDir` 로컬 I/O 검증, `storage.file.provider` 누락/오류 시 기동 실패 검증.

### Phase 2: 메타데이터 영속성 및 Flyway 적용 (난이도: Pro)
- **목표**: `FileMeta` 도메인 영속화, MyBatis/JPA 어댑터 구현, 마이그레이션 적용.
- **파일 경계**: `storage/jpa/build.gradle`, `storage/mybatis`, `db/migration/V{next}__create_file_meta_table.sql`.
- **선행 조건**: Phase 1 완료.
- **가드 테스트**: 기본 `test` (H2/매핑 생성) 통과 및 `livePostgresTest`에서 실제 메타데이터 상태 전이 검증.

### Phase 3: Presigned 기반 Web Layer 및 콜백 연동 (난이도: Pro)
- **목표**: Presign 발급 및 Finalize API(HEAD 크기/체크섬 검증 로직 포함) 컨트롤러 구현, DataBuffer 누수 방지.
- **파일 경계**: `file-core/web` 컨트롤러, Presigner 서비스, 고아 객체 정리 스케줄러.
- **선행 조건**: Phase 2 완료 및 사용자 결정 3 완료.
- **가드 테스트**: `liveS3Test` (MinIO Testcontainers) 태스크를 통해 Presigned URL 발급 및 HTTP 클라이언트 직접 업로드 후 Finalize 성공 통합 시나리오 확인.
