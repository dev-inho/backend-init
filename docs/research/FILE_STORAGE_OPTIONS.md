# 확장 가능한 파일 저장 설계 조사 (FILE_STORAGE_OPTIONS)

## 1. 저장소 백엔드 비교 (작은 팀 기준 비용/운영/호환성/락인 고려)

| 저장소 | 비용 및 운영 부담 | 호환성 및 락인 | 공식 문서 URL (출처) |
| --- | --- | --- | --- |
| **Local FS** | 운영 인프라 비용이 없으나 스케일 아웃 시 파일 동기화 부담이 매우 큽니다. | 인프라 락인이 없으며 Java 표준 API를 사용합니다. | [문서](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html) |
| **AWS S3** | 저장 용량 및 아웃바운드 트래픽 비용이 발생하지만 관리 부담이 거의 없습니다. | 객체 스토리지의 사실상 표준(S3 API)이므로 락인 우려가 낮습니다. | [문서](https://docs.aws.amazon.com/AmazonS3/latest/userguide/Welcome.html) |
| **MinIO** | 자체 호스팅 시 트래픽 비용을 절감할 수 있으나 서버 운영 및 장애 복구 부담이 있습니다. | AWS S3 API 완벽 호환으로 락인 없는 마이그레이션이 가능합니다. | [문서](https://min.io/docs/minio/linux/index.html) |
| **Cloudflare R2** | 이그레스(Egress) 데이터 전송 수수료가 없어 트래픽 비용이 크게 절감됩니다. | S3 호환 API를 제공하여 기존 S3 클라이언트 생태계를 그대로 활용합니다. | [문서](https://developers.cloudflare.com/r2/) |
| **GCS** | 멀티 리전 엣지 캐싱 기능이 뛰어나지만 트래픽 비용 구조는 S3와 유사합니다. | 자체 API(XML/JSON)와 S3 호환 API를 제공하나 완전 호환은 제한적입니다. | [문서](https://cloud.google.com/storage/docs/introduction) |
| **Azure Blob** | Microsoft 생태계(Entra ID 등) 통합 시 유지보수가 편리합니다. | S3 호환성이 부족하여 코드 레벨의 Azure 전용 SDK 종속성이 발생합니다. | [문서](https://learn.microsoft.com/en-us/azure/storage/blobs/storage-blobs-introduction) |

## 2. 접근 방식 및 스트리밍 비교

| 항목 | 사실 및 설명 | 공식 문서 URL (출처) |
| --- | --- | --- |
| **서버 경유 업로드** | 파일이 백엔드 서버를 거쳐 전달되며, WebFlux에서는 `Flux<DataBuffer>` 형태의 스트림으로 처리합니다. | [문서](https://docs.spring.io/spring-framework/reference/web/webflux/reactive-spring.html) |
| **Presigned URL 직접 업로드** | 클라이언트가 서명된 URL로 스토리지에 직접 업로드하므로 서버의 OOM 및 대역폭 부하를 줄입니다. | [문서](https://docs.aws.amazon.com/AmazonS3/latest/userguide/PresignedUrlUploadObject.html) |
| **대용량 Multipart 업로드** | 큰 파일을 여러 파트로 나누어 병렬 업로드하여 네트워크 문제를 극복하고 처리량을 향상시킵니다. | [문서](https://docs.aws.amazon.com/AmazonS3/latest/userguide/mpuoverview.html) |
| **DataBuffer vs ByteArray** | `DataBuffer`는 메모리 버퍼 풀을 사용하여 대용량 파일의 메모리 할당/해제를 최적화합니다. | [문서](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/core/io/buffer/DataBuffer.html) |

## 3. 라이브러리 및 SDK 비교

| 라이브러리 | 특징 및 사실 | 공식 문서 URL (출처) |
| --- | --- | --- |
| **AWS SDK Java v2 (Async)** | Netty 기반의 비동기 논블로킹 I/O를 지원하며 `CompletableFuture`로 높은 동시성을 제공합니다. | [문서](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/asynchronous.html) |
| **AWS SDK Java v2 (CRT)** | AWS Common Runtime(C 언어 기반)을 사용하여 시작 시간과 메모리 공간을 최적화한 HTTP 클라이언트입니다. | [문서](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/http-configuration-crt.html) |
| **Spring Cloud AWS** | Spring Boot 환경에 S3 리소스 바인딩과 자동 설정을 추상화하여 제공합니다. | [문서](https://docs.awspring.io/spring-cloud-aws/docs/3.1.1/reference/html/index.html) |
| **MinIO Java SDK** | S3 호환 스토리지를 제어하기 위한 단순화된 클라이언트 SDK입니다. | [문서](https://github.com/minio/minio-java) |

## 4. 보안, 메타데이터 및 운영

| 항목 | 사실 및 설명 | 공식 문서 URL (출처) |
| --- | --- | --- |
| **메타데이터 (DB)** | 객체 스토리지 메타데이터 외에 관계형 DB로 소유자, 크기, 체크섬 등을 인덱싱해야 검색이 빠릅니다. | [문서](https://docs.aws.amazon.com/AmazonS3/latest/userguide/UsingMetadata.html) |
| **보안 및 바이러스 스캔** | S3 업로드 시 Lambda를 연동하여 악성코드를 스캔할 수 있습니다 (GuardDuty). | [문서](https://docs.aws.amazon.com/guardduty/latest/ug/malware-protection-s3.html) |
| **운영 및 버저닝** | 덮어쓰기나 삭제를 대비해 버저닝을 활성화하여 복원 지점을 확보합니다. | [문서](https://docs.aws.amazon.com/AmazonS3/latest/userguide/Versioning.html) |
| **경로 순회 방지 (Path Traversal)** | 사용자 입력 파일명을 그대로 Key로 쓰지 않고 고유 식별자(UUID)를 활용합니다. | [문서](https://docs.aws.amazon.com/AmazonS3/latest/userguide/object-keys.html) |
| **Content-Type 검증** | 클라이언트 헤더를 맹신하지 않고 매직 넘버 대조(Tika) 등으로 파일 형식을 판별해야 합니다. | [문서](https://docs.aws.amazon.com/AmazonS3/latest/userguide/UsingMetadata.html) |
| **크기 상한 (Size Limit)** | Presigned URL 발급 시 Content-Length-Range 조건을 부여하여 스토리지 상의 업로드 크기를 제한합니다. | [문서](https://docs.aws.amazon.com/AmazonS3/latest/API/sigv4-HTTPPOSTConstructPolicy.html) |
| **Checksum (무결성)** | 업로드 시 해시 값을 검증하여 전송 중 데이터가 손상되지 않았음을 보장합니다. | [문서](https://docs.aws.amazon.com/AmazonS3/latest/userguide/checking-object-integrity.html) |
| **Presigned 만료 (Expiration)** | 서명된 URL은 유출 피해 최소화를 위해 짧은 유효 기간(예: 15분)을 가집니다. | [문서](https://docs.aws.amazon.com/AmazonS3/latest/userguide/ShareObjectPreSignedURL.html) |
| **고아 객체 정리 (Orphan Cleanup)** | 업로드 실패로 버려진 객체를 S3 Lifecycle 규칙을 통해 자동 정리합니다. | [문서](https://docs.aws.amazon.com/AmazonS3/latest/userguide/object-lifecycle-mgmt.html) |
| **Local -> S3 이관 (Migration)** | 로컬 디렉터리와 S3를 양방향 동기화하여 서비스 중단 없이 데이터를 이관합니다. | [문서](https://docs.aws.amazon.com/datasync/latest/userguide/how-datasync-works.html) |

## 5. 테스트 전략 비교

| 후보 | 특징 및 사실 | 공식 문서 URL (출처) |
| --- | --- | --- |
| **MinIO Testcontainers** | 실제 MinIO 컨테이너를 띄워 S3 API 통합 테스트를 수행하므로 네트워크 엣지 케이스 재현이 탁월합니다. | [문서](https://java.testcontainers.org/modules/minio/) |
| **S3Mock** | 경량 인메모리 S3 모의 서버로 CI 리소스를 아낄 수 있으나, 호환성 한계가 있을 수 있습니다. | [문서](https://github.com/adobe/S3Mock) |

## 6. 현재 코드 대비 갭 표 (Gap Analysis)

| 기능 갭 | 현재 구현 상태 (명령어 및 파일:행 근거) | 제안 사항 | 우선순위 |
| --- | --- | --- | --- |
| **실제 소비자 부재** | `docs/DEAD_CODE_CANDIDATES.md:14` (실제 호출 0건 기록) | 데드 코드를 대체할 범용 파일 포트로 교체. | 상 |
| **업로드 컨트롤러 부재** | `rg -n "multipart|FilePart|DataBuffer" core/application/src/main` 결과 0건 | 파일 관리를 위한 `file` 도메인 REST 컨트롤러 구현. | 중 |
| **동기식 버퍼링** | `FileStoragePort.kt:13`의 `suspend fun store(path: String, bytes: ByteArray)` | 순수 추상화 스트림 및 비동기 스트리밍 포트로 변경. | 상 |
| **메타데이터 DB 부재** | `core/application/src/main/resources/db/migration/V1__create_storage_tables.sql:1-44` 테이블 5개 중 파일 관련 테이블 없음 | `file_meta` 물리 테이블과 Flyway V2 마이그레이션 도입. | 상 |
| **GW 응답 버퍼링 한계** | `core/gateway/src/main/kotlin/cc/midolog/gateway/proxy/ProxyHandler.kt:59` (`bodyToMono(ByteArray::class.java)`) | 파일 다운로드 트래픽이 GW를 거치지 않게 Presigned 직접 우회. | 상 |
