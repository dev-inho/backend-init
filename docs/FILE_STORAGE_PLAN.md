# 파일 저장 설계 및 구현 로드맵 (FILE_STORAGE_PLAN)

## 1. 개요 및 권고안

현재 사용되지 않는 `FileStoragePort`와 `LocalFileStorageAdapter`를 확장 가능한 스트림 및 메타데이터 지원 스토리지 아키텍처로 개편합니다. 
분석 결과, OOM 위험을 유발하는 동기식 `ByteArray` 전체 버퍼링을 제거하고, 대용량 파일 처리에 적합한 **Presigned URL 직접 업로드/다운로드 방식과 서버 측 스트리밍(`Flux<DataBuffer>`)을 병행 지원하는 하이브리드 S3 호환 모델**을 권고합니다.

## 2. 포트 계약 구체화 및 도메인 모델

`core:domain`의 프레임워크 비의존 순수성 및 스프링/클라우드 종속성 배제 원칙(`DomainPurityTest` 만족)을 준수하기 위해, 도메인에는 어노테이션이 없는 순수 Kotlin 인터페이스와 데이터 클래스만 위치합니다.

### 2.1 도메인 모델 설계
```kotlin
package cc.midolog.file.model

data class StoredFile(
    val id: String, // 식별자
    val ownerId: String, // 업로더 식별자
    val storageKey: String, // 스토리지 내 물리적 경로/키
    val sizeBytes: Long,
    val contentType: String,
    val checksum: String?, 
    val status: FileStatus // PENDING, READY, FAILED, DELETED
)

enum class FileStatus { PENDING, READY, FAILED, DELETED }
```

### 2.2 포트 책임 경계 분리
스트림 기반 파일 조작(I/O)과 Presigned URL 발급(계산/서명)은 결합도가 다르므로 포트를 분리하는 것을 권장합니다. I/O 포트는 외부 인프라에 의존하며, Presigner는 암호화 연산에 가깝습니다.

```kotlin
package cc.midolog.file.port.storage

import java.io.InputStream
// Flux<DataBuffer> 등은 프레임워크 종속이므로 domain에서는 InputStream, OutputStream 또는 순수 비동기 콜백을 사용하거나, 
// 코루틴의 Flow<ByteArray>를 사용하여 프레임워크 종속을 피합니다.
import kotlinx.coroutines.flow.Flow

interface FileStoragePort {
    suspend fun store(key: String, content: Flow<ByteArray>, size: Long): Boolean
    suspend fun load(key: String): Flow<ByteArray>?
    suspend fun delete(key: String): Boolean
    suspend fun exists(key: String): Boolean
}

interface FilePresignPort {
    suspend fun presignUpload(key: String, expirationSeconds: Long): String
    suspend fun presignDownload(key: String, expirationSeconds: Long): String
}
```

## 3. 모듈 배치 권고안

**비교 후보**:
1. `client/storage-file` 유지: 파일 I/O는 외부 연동(client)에 해당하지만, 메타데이터 DB는 영속성(storage)에 속하므로 경계가 모호해짐.
2. `storage/file-{local,s3}`: DB와 동일한 영속성 모듈에 배치.
3. `file/{core,autoconfigure,starter}`: 게이트웨이와 유사한 탑재식(starter) 패턴.

**권고안: `file/{core,autoconfigure,starter}` 패턴 도입**
- **근거**: 현재 `gateway-starter` 패턴을 도입 중인 아키텍처 방향성에 부합합니다. 파일 스토리지는 단순 DB 저장이 아니라 로컬/S3 프로바이더 전략, Presigned URL 발급, 스트리밍 유틸리티 등 복합적인 설정이 필요하므로 독립된 컨텍스트로 묶는 것이 가장 적합합니다.

## 4. Provider 전환 및 조건부 빈 (AutoConfiguration)

- **설계**: `storage.file.provider=local|s3` 프로퍼티 기반 조건부 빈 생성.
- **Fail-fast 전략 채택**: 프로퍼티가 누락되었거나 `local`, `s3` 외의 잘못된 값이 입력된 경우, 조용히 `local`로 Fallback하지 않고 애플리케이션 기동 시점(`@ConditionalOnProperty` 또는 `EnvironmentPostProcessor` 검증)에 예외를 발생시키는 **fail-fast** 전략을 명시합니다. 의도치 않은 로컬 저장으로 인한 운영 데이터 유실 방지가 목적입니다.
- **Starter 패턴**: `file-autoconfigure`의 `AutoConfiguration.imports` 방식을 채택하여 어플리케이션이 `file-starter`에만 의존하면 필요한 빈들이 조건에 맞게 자동 주입되도록 합니다.

## 5. 메타데이터 DB 테이블 및 상태 전이 설계

### 5.1 스키마 및 마이그레이션
`core/application/src/main/resources/db/migration/V2__create_file_meta_table.sql`
```sql
CREATE TABLE IF NOT EXISTS file_meta (
    id VARCHAR(64) PRIMARY KEY,
    owner_id VARCHAR(64) NOT NULL,
    storage_key VARCHAR(255) NOT NULL,
    size_bytes BIGINT NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    checksum VARCHAR(255),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_file_meta_owner ON file_meta(owner_id);
```

### 5.2 JPA DSL 생성기 연동
`storage/jpa/build.gradle`의 DSL에 `FileMeta` 추가:
```groovy
    entity('cc.midolog.file.model.FileMeta') {
        table = 'file_meta'
        id = 'id'
        field('status') { enumStrategy = 'STRING' }
        // ... (필드 매핑)
    }
```

### 5.3 상태 전이 (State Transition)
1. 클라이언트 업로드 요청 -> DB에 `PENDING` 기록 -> Presigned URL 반환
2. 클라이언트 직접 업로드 완료 후 웹훅/콜백 호출 -> 스토리지 실제 객체 확인(크기/체크섬 대조) -> DB 상태 `READY`로 업데이트
3. 파일 업로드 시간 초과 / 유효성 실패 -> `FAILED`
4. 삭제 요청 -> 스토리지 객체 삭제 (Soft-delete 또는 즉시) -> DB 상태 `DELETED`

## 6. 웹 계층 스트리밍 및 보안 설계

- **ProxyHandler 제약 우회**: 현재 `docs/GATEWAY.md:205`의 `ProxyHandler`는 응답을 `ByteArray`로 전체 버퍼링하므로 대용량 파일 다운로드 시 OOM이 발생합니다. 따라서 직접 다운로드는 금지하고, 서버는 **Presigned URL 리다이렉트(302)**를 반환하여 클라이언트가 S3/Cloudflare R2에서 직접 파일을 내려받도록 하여 게이트웨이 대역폭을 우회합니다.
- **스트리밍 업로드 (서버 경유 시)**: 부득이한 서버 경유 시 `spring.servlet.multipart.max-file-size` 및 WebFlux의 `maxInMemorySize`를 설정하여 스트림을 유지합니다. 
- **경로 순회 방지**: 저장 키(Storage Key)는 사용자가 입력한 파일명을 쓰지 않고 내부적으로 `UUID` 또는 해시 조합 문자열을 생성하여 방지합니다.
- **Content-Type 및 바이러스 스캔**: 클라이언트가 보낸 Content-Type은 신뢰하지 않고 백엔드에서 Tika 라이브러리(매직 넘버 대조)로 신뢰 경계를 확립합니다. S3 업로드 시 EventBridge + Lambda 훅으로 비동기 바이러스 스캔을 설계합니다.

## 7. 운영 측면 및 이관(Migration) 전략

- **고아 파일 정리 (Orphan Cleanup)**: DB 상태가 일정 기간(예: 24시간) `PENDING`인 파일과 스토리지의 실제 객체를 대조하는 스프링 배치 Job을 도입합니다.
- **멱등성 및 재시도**: 업로드 콜백은 재시도 가능하도록 멱등적으로 설계(`READY` 상태면 스킵)합니다.
- **버저닝**: 덮어쓰기로 인한 데이터 유실을 막기 위해 S3 버저닝을 활성화합니다.
- **Local -> S3 이관**: 초기 로컬 파일 개발 후 S3 전환 시, `local` 프로바이더에서 S3 프로바이더로 비동기 복제 스크립트를 작성하여 스토리지 키 매핑 정합성을 검증합니다.

## 8. 테스트 전략

- **Local Provider**: 파일 I/O 테스트는 JUnit의 `@TempDir` 어노테이션을 활용하여 임시 디렉토리에서 빠르고 안전하게 실행합니다.
- **S3 Provider (MinIO)**: 통합 테스트를 위해 `Testcontainers` 기반의 MinIO 이미지를 띄워 S3 API 호환성 통합 검증을 수행합니다. AWS SDK의 Mock(예: S3Mock)보다 실제 네트워크 환경을 묘사하는 MinIO 컨테이너가 엣지 케이스 방지에 유리합니다.
- **테스트 격리**: `docs/MODULE_GUIDE.md`의 규칙에 따라 Docker 기반 테스트는 기본 `test` 테스크에서 돌지 않고 `livePostgresTest`처럼 `liveS3Test`와 같은 별도 태그 및 Gradle 테스크로 분리하여 빌드 속도를 보장합니다.

## 9. 🔶 사용자 결정 필요 (Decision Records)

| 결정 항목 | 선택지 | 각 선택지의 영향 | 권고 기본값 |
| --- | --- | --- | --- |
| **모듈 위치** | 1) `client/storage-file`<br>2) `storage/file-*`<br>3) `file-starter` 패러다임 | 3)은 구조 변경이 크지만 응집도가 높음. 1)은 기존 파일 유지. | **`file-starter` 패턴** (Gateway와 방향 일치) |
| **Provider 지원 범위 (1차)** | 1) Local만 먼저<br>2) Local + S3(MinIO) 동시 | 2)는 S3 비동기 설정과 Testcontainers 추가 작업으로 공수가 늘어남. | **Local + S3(MinIO)** (클라우드 네이티브 조기 확보) |
| **메타데이터 분리 컨텍스트** | 1) 기존 `core:domain` 내 파일 패키지<br>2) 독립된 `file` 컨텍스트 | 2)는 도메인 결합도를 낮추지만 MyBatis/JPA 어댑터를 별도로 작성해야 함. | **독립된 `file` 컨텍스트** |
| **Presigned URL 직접 업로드** | 1) 서버 경유 스트리밍 (OOM 주의)<br>2) Presigned 직접 업로드 | 2)는 프론트엔드 작업 방식이 달라지며, Gateway 버퍼링을 우회하여 백엔드 부하를 줄임. | **Presigned 직접 업로드 (서버는 메타데이터 콜백만 처리)** |

## 10. 로드맵 (Phase별 분할)

각 Phase는 한 명의 워커가 처리할 수 있는 브리프(일감) 크기입니다.

### Phase 0: 파일 스토리지 모듈 뼈대 및 사전 준비 (난이도: Flash)
- **목표**: 삭제 후보 #1, #2(`FileStoragePort.kt`, `LocalFileStorageAdapter.kt`)를 삭제하지 않고, 이 설계 문서에서 정의한 새로운 모듈 구조(`file-starter`, `file-autoconfigure`, `file-core`)로 코드를 이동 및 재설계합니다.
- **파일 경계**: `client/storage-file` 삭제 (설정 제거), `file/` 멀티 모듈 생성, 빈 포트 인터페이스 및 `StoredFile` 모델 정의.
- **선행 조건**: 없음.
- **가드 테스트**: `DomainPurityTest`가 새 파일 모델을 검증하여 통과해야 함.

### Phase 1: Local / S3 프로바이더 구현 (난이도: Pro)
- **목표**: `storage.file.provider`에 따라 동작하는 `LocalFileStorageAdapter` 및 `S3FileStorageAdapter` 구현 및 `file-autoconfigure` 연동.
- **파일 경계**: `file-core` 어댑터 구현체, `file-autoconfigure/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
- **선행 조건**: Phase 0 완료.
- **가드 테스트**: `@TempDir`을 활용한 로컬 테스트, `fail-fast` 프로퍼티 부재 시 애플리케이션 시작 실패 통합 테스트.

### Phase 2: 메타데이터 DB 영속성 및 Flyway/JPA 적용 (난이도: Pro)
- **목표**: `FileMeta` 도메인 영속화, MyBatis 및 JPA(DSL) 어댑터 구현, Flyway V2 마이그레이션 스크립트 작성.
- **파일 경계**: `core/domain`, `storage/jpa/build.gradle`, `storage/mybatis`, `db/migration/`.
- **선행 조건**: Phase 1 완료.
- **가드 테스트**: `livePostgresTest`에서 `FileMeta` 삽입/상태 변경 테스트가 성공해야 함.

### Phase 3: 웹 계층 및 Presigned URL 연동 (난이도: Pro)
- **목표**: 파일 업로드 요청 시 Presigned URL 발급, 업로드 완료 콜백 수신 및 메타데이터 `READY` 전환 컨트롤러 추가. 다운로드 리다이렉트 엔드포인트 구현.
- **파일 경계**: `file-core` 웹 컨트롤러, 예외 처리 핸들러, `ProxyHandler` 우회 설정(라우팅 배제).
- **선행 조건**: Phase 2 완료.
- **가드 테스트**: Presigned URL 발급 API 모의 테스트, 다운로드 API가 `302 Found` 및 S3 URL을 정확히 반환하는지 검증.
