# 확장 가능한 파일 저장 설계 조사 (FILE_STORAGE_OPTIONS)

## 1. 저장소 백엔드 비교

| 저장소 | 특징 및 사실 | 공식 문서 URL (출처) |
| --- | --- | --- |
| **Local FS** | Java `java.nio.file.Files`를 사용하여 호스트 OS의 파일 시스템에 직접 바이트를 덮어씁니다. | [문서](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html) |
| **AWS S3** | 높은 내구성과 가용성을 제공하는 관리형 객체 스토리지 서비스입니다. | [문서](https://docs.aws.amazon.com/AmazonS3/latest/userguide/Welcome.html) |
| **MinIO** | AWS S3 API와 호환되는 고성능 객체 스토리지 서버로 자체 호스팅이 가능합니다. | [문서](https://min.io/docs/minio/linux/index.html) |
| **Cloudflare R2** | 이그레스(Egress) 데이터 전송 수수료가 없는 S3 호환 객체 스토리지입니다. | [문서](https://developers.cloudflare.com/r2/) |
| **Google Cloud Storage (GCS)** | 전 세계적으로 엣지 캐싱 및 확장성을 제공하는 Google의 객체 스토리지입니다. | [문서](https://cloud.google.com/storage/docs/introduction) |
| **Azure Blob Storage** | 대규모 비정형 데이터를 저장하기 위해 설계된 Microsoft의 클라우드 객체 스토리지입니다. | [문서](https://learn.microsoft.com/en-us/azure/storage/blobs/storage-blobs-introduction) |

## 2. 접근 방식 및 스트리밍 비교

| 항목 | 사실 및 설명 | 공식 문서 URL (출처) |
| --- | --- | --- |
| **서버 경유 업로드** | 파일이 백엔드 서버를 거쳐 저장소로 전달되며, WebFlux에서는 `Flux<DataBuffer>` 형태의 스트림으로 처리합니다. | [문서](https://docs.spring.io/spring-framework/reference/web/webflux/reactive-spring.html) |
| **Presigned URL 직접 업로드** | 클라이언트가 서명된 URL을 발급받아 스토리지에 직접 업로드하므로 서버의 부하를 줄입니다. | [문서](https://docs.aws.amazon.com/AmazonS3/latest/userguide/PresignedUrlUploadObject.html) |
| **대용량 Multipart 업로드** | 큰 파일을 여러 파트로 나누어 병렬 업로드하여 네트워크 문제를 극복하고 처리량을 향상시킵니다. | [문서](https://docs.aws.amazon.com/AmazonS3/latest/userguide/mpuoverview.html) |
| **DataBuffer vs ByteArray** | `DataBuffer`는 메모리 버퍼 관리를 최적화하여 대용량 파일을 청크 단위로 스트리밍 처리할 수 있게 합니다. 전체 바이트 버퍼링 시 OOM 위험이 있습니다. | [문서](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/core/io/buffer/DataBuffer.html) |

## 3. 라이브러리 및 SDK 비교

| 라이브러리 | 특징 및 사실 | 공식 문서 URL (출처) |
| --- | --- | --- |
| **AWS SDK for Java v2 (Async)** | 비동기 논블로킹 I/O를 지원하며 `CompletableFuture` 기반으로 높은 동시성을 제공합니다. | [문서](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/asynchronous.html) |
| **Spring Cloud AWS** | Spring 환경에 AWS 인프라(S3 포함) 통합을 위한 추상화 및 자동 설정을 제공합니다. | [문서](https://docs.awspring.io/spring-cloud-aws/docs/3.1.1/reference/html/index.html) |
| **MinIO Java SDK** | MinIO 서버와 통신하기 위한 Java 클라이언트 SDK로 S3 호환 스토리지를 제어합니다. | [문서](https://github.com/minio/minio-java) |

## 4. 메타데이터, 보안, 운영

| 항목 | 사실 및 설명 | 공식 문서 URL (출처) |
| --- | --- | --- |
| **메타데이터 (DB)** | 객체 스토리지 자체 메타데이터 외에 RDBMS 테이블로 소유자, 크기, 체크섬 등을 관리하여 검색과 무결성을 확보합니다. | [문서](https://docs.aws.amazon.com/AmazonS3/latest/userguide/UsingMetadata.html) |
| **보안 및 바이러스 스캔** | S3에 업로드된 객체에 대해 Lambda 등을 연동하여 악성코드를 스캔할 수 있습니다. | [문서](https://docs.aws.amazon.com/guardduty/latest/ug/malware-protection-s3.html) |
| **운영 및 버저닝** | 스토리지 레벨에서 버저닝을 활성화하여 실수로 덮어쓰거나 삭제한 객체를 복원할 수 있습니다. | [문서](https://docs.aws.amazon.com/AmazonS3/latest/userguide/Versioning.html) |

## 5. 현재 코드 대비 갭 표 (Gap Analysis)

| 기능 갭 | 현재 구현 상태 (파일:행 근거) | 제안 사항 | 우선순위 |
| --- | --- | --- | --- |
| **실제 소비자 부재** | `core/domain/src/main/kotlin/cc/midolog/sample/port/file/FileStoragePort.kt:12` (소비자 0건), `client/storage-file/src/main/kotlin/cc/midolog/client/storage/LocalFileStorageAdapter.kt:16` (0건 사용). | 현재 설계를 버리고 범용 파일 스토리지 포트로 대체. | 상 |
| **동기식 전체 바이트 버퍼링** | `FileStoragePort.kt:13`의 `suspend fun store(path: String, bytes: ByteArray): String` | `Flux<DataBuffer>` 혹은 외부 스토리지 스트리밍 포트로 변경. | 상 |
| **메타데이터 테이블 부재** | `core/application/src/main/resources/db/migration/V1__create_storage_tables.sql:1` (파일 관련 메타데이터 테이블 0건) | `FileMeta` 도메인과 DB 테이블(Flyway & JPA DSL) 도입. | 상 |
| **업로드 컨트롤러 부재** | `docs/DEAD_CODE_CANDIDATES.md:14` (업로드 컨트롤러 0건) | `file` 컨텍스트의 컨트롤러 분리 혹은 Presigned URL 발급 엔드포인트 구축. | 중 |
| **게이트웨이 응답 버퍼링 제약** | `docs/GATEWAY.md:205`의 `ProxyHandler` 다운스트림 응답 `ByteArray` 전체 버퍼링. 대용량 파일 다운로드 시 OOM 위험. | 파일 다운로드는 Presigned URL 리다이렉트나 Gateway 외부망 우회(직접 접근) 방안 적용. | 상 |
