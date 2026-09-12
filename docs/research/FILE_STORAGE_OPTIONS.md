# 확장 가능한 파일 저장 설계 조사 (FILE_STORAGE_OPTIONS)

## 1. 저장소 백엔드 비교

| 저장소 | 공식 사실 (호환성 및 기능) | 작은 팀 기준 평가 (비용 및 운영) | 공식 문서 URL |
| --- | --- | --- | --- |
| **Local FS** | Java NIO `Files`는 운영 체제의 로컬 파일 시스템을 제어하는 표준 API를 제공합니다. | 인프라 락인과 추가 비용이 없으나 스케일 아웃 시 파일 동기화 부담이 매우 큽니다. | [문서](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html) |
| **AWS S3** | 정적 웹 호스팅, 버저닝 및 높은 내구성을 제공하는 객체 스토리지 서비스입니다. | 관리 부담이 적고 사실상 표준 API로 락인 우려가 낮으나, 아웃바운드 트래픽 비용이 발생합니다. | [문서](https://docs.aws.amazon.com/AmazonS3/latest/userguide/Welcome.html) |
| **MinIO** | AWS S3 API와 호환되는 고성능 객체 스토리지 서버입니다. | 자체 호스팅으로 트래픽 비용을 아낄 수 있으나, 서버 운영 및 장애 복구 부담이 존재합니다. | [문서](https://min.io/docs/minio/linux/index.html) |
| **Cloudflare R2** | 기존 S3 호환 API를 지원하며 이그레스(Egress) 데이터 전송 수수료를 부과하지 않습니다. | AWS S3 대비 트래픽 비용이 크게 절감되어 대용량 다운로드 서비스에 유리합니다. | [문서](https://developers.cloudflare.com/r2/) |
| **GCS** | 멀티 리전 스토리지와 S3 호환 API(XML) 및 자체 API(JSON)를 제공합니다. | 엣지 네트워크 캐싱이 뛰어나지만 S3 완벽 호환은 아니며 마이그레이션 테스트가 필요합니다. | [문서](https://cloud.google.com/storage/docs/introduction) |
| **Azure Blob** | Microsoft 클라우드의 방대한 비정형 데이터용 객체 스토리지입니다. | Entra ID 등 MS 생태계 통합이 유리하나, S3 비호환으로 전용 SDK 락인이 발생합니다. | [문서](https://learn.microsoft.com/en-us/azure/storage/blobs/storage-blobs-introduction) |

## 2. 접근 방식 및 스트리밍 비교

| 항목 | 공식 사실 및 설명 | 공식 문서 URL |
| --- | --- | --- |
| **서버 경유 스트리밍** | Spring WebFlux는 논블로킹 방식으로 HTTP 요청/응답을 `Flux<DataBuffer>` 형태의 스트림으로 처리합니다. | [문서](https://docs.spring.io/spring-framework/reference/web/webflux/reactive-spring.html) |
| **Presigned URL 직접 업로드** | 스토리지 소유자가 발급한 임시 서명 URL을 통해 클라이언트가 스토리지에 객체를 직접 업로드할 수 있습니다. | [문서](https://docs.aws.amazon.com/AmazonS3/latest/userguide/PresignedUrlUploadObject.html) |
| **대용량 Multipart 업로드** | 큰 객체를 여러 파트로 나누어 독립적이고 병렬로 업로드하여 처리량을 향상시킬 수 있습니다. | [문서](https://docs.aws.amazon.com/AmazonS3/latest/userguide/mpuoverview.html) |
| **DataBuffer vs ByteArray** | `DataBuffer`는 바이트 버퍼의 추상화로, Netty 메모리 버퍼 풀을 사용하여 메모리 할당을 최적화합니다. | [문서](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/core/io/buffer/DataBuffer.html) |

## 3. 라이브러리 및 SDK 비교 (WebFlux 호환성 및 기능)

| 라이브러리 | 공식 기능 및 제약 | 작은 팀 기준 선택 및 평가 | 공식 문서 URL |
| --- | --- | --- | --- |
| **AWS SDK Java v2 (Netty Async)** | Netty 기반 비동기 논블로킹 I/O를 기본 지원하며 Spring WebFlux의 이벤트 루프와 호환성이 높습니다. | **(최종 추천)** WebFlux 환경에서 별도 JNI 의존성 없이 안정적으로 동작하며 커뮤니티 레퍼런스가 많습니다. | [문서](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/asynchronous.html) |
| **AWS SDK Java v2 (CRT)** | C 기반 AWS Common Runtime을 사용하여 시작 시간과 메모리를 최적화한 대안 HTTP 클라이언트입니다. | JNI 네이티브 라이브러리가 포함되어 알파/작은 팀 컨테이너 환경에서 셋업 비용(호환성)이 발생할 수 있습니다. | [문서](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/http-configuration-crt.html) |
| **Spring Cloud AWS** | Spring Boot 환경을 위한 S3 연동 및 자동 설정 인프라 추상화를 제공합니다. | 설정이 편하나 지원되는 SDK 버전이 결합되고, 추상화 계층으로 인해 S3 특정 세밀한 제어가 어려울 수 있습니다. | [문서](https://docs.awspring.io/spring-cloud-aws/docs/3.1.1/reference/html/index.html) |
| **MinIO Java SDK** | S3 API와 호환되는 객체 스토리지를 제어하기 위한 단순한 클라이언트입니다. | S3 전용 최적화나 Async 스트리밍(Netty) 통합이 상대적으로 부족하여 WebFlux 직접 결합 시 불리합니다. | [문서](https://github.com/minio/minio-java) |

## 4. 보안, 메타데이터 및 운영

| 항목 | 공식 사실 및 설명 | 공식 문서 URL |
| --- | --- | --- |
| **메타데이터 속성** | S3 객체는 시스템 정의 메타데이터(Content-Type 등) 및 사용자 정의 메타데이터(x-amz-meta-)를 가질 수 있습니다. | [문서](https://docs.aws.amazon.com/AmazonS3/latest/userguide/UsingMetadata.html) |
| **보안 및 바이러스 스캔** | Amazon GuardDuty는 S3 버킷에 업로드된 객체에 대해 악성코드 검사를 자동화할 수 있습니다. | [문서](https://docs.aws.amazon.com/guardduty/latest/ug/malware-protection-s3.html) |
| **운영 및 버저닝** | 버저닝 활성화 시 객체 변형이나 삭제로부터 복구할 수 있도록 동일 키에 여러 버전을 유지합니다. | [문서](https://docs.aws.amazon.com/AmazonS3/latest/userguide/Versioning.html) |
| **객체 키 명명 규칙** | 객체 키 명명 시 영숫자 및 일부 안전한 특수 문자(하이픈 등)를 사용하도록 가이드합니다. (경로 순회 방지는 자체 로직 필요) | [문서](https://docs.aws.amazon.com/AmazonS3/latest/userguide/object-keys.html) |
| **Presigned POST (크기 상한)** | Presigned PUT과 달리 POST Policy는 `content-length-range`를 통해 허용되는 파일 업로드 크기를 강제할 수 있습니다. | [문서](https://docs.aws.amazon.com/AmazonS3/latest/API/sigv4-HTTPPOSTConstructPolicy.html) |
| **Checksum (무결성)** | S3는 데이터 전송 중 손상을 감지하기 위해 추가적인 Checksum(CRC32, SHA256 등) 해시 검증 기능을 제공합니다. | [문서](https://docs.aws.amazon.com/AmazonS3/latest/userguide/checking-object-integrity.html) |
| **Presigned 만료 (Expiration)** | Presigned URL 생성 시 만료 시간(기본값 15분)을 지정하여 제한된 시간 동안만 접근을 허용합니다. | [문서](https://docs.aws.amazon.com/AmazonS3/latest/userguide/ShareObjectPreSignedURL.html) |
| **고아 객체 정리 (Lifecycle)** | Lifecycle Configuration을 통해 일정 기간이 지난 특정 접두사의 불완전 업로드 및 객체를 자동 삭제할 수 있습니다. | [문서](https://docs.aws.amazon.com/AmazonS3/latest/userguide/object-lifecycle-mgmt.html) |
| **DataSync (이관)** | AWS DataSync는 온프레미스 스토리지 시스템과 AWS 스토리지 서비스 간의 데이터 이동을 자동화합니다. | [문서](https://docs.aws.amazon.com/datasync/latest/userguide/how-datasync-works.html) |

## 5. 테스트 전략 비교

| 후보 | 공식 사실 및 설명 | 작은 팀 기준 평가 | 공식 문서 URL |
| --- | --- | --- | --- |
| **MinIO Testcontainers** | Java 통합 테스트 환경에서 MinIO 도커 컨테이너 라이프사이클을 제어하는 모듈입니다. | 실제 인프라와 가장 유사한 네트워크 환경(Testcontainers)을 제공하여 엣지 케이스 재현에 좋습니다. | [문서](https://java.testcontainers.org/modules/minio/) |
| **S3Mock** | S3 API를 모방하는 인메모리 기반 경량 웹 서버입니다. | 컨테이너 없이 빠르고 가볍게 띄울 수 있으나 고급 S3 기능 호환성 한계가 있을 수 있습니다. | [문서](https://github.com/adobe/S3Mock) |

## 6. 현재 코드 대비 갭 표 (Gap Analysis)

| 기능 갭 | 현재 구현 상태 (명령어 및 파일:행 근거) | 제안 사항 | 우선순위 |
| --- | --- | --- | --- |
| **실제 소비자 부재** | `docs/DEAD_CODE_CANDIDATES.md:14` (실제 FileStoragePort 호출 0건 기록) | 현재 데드 코드를 범용 포트 인터페이스로 교체. | 상 |
| **업로드 컨트롤러 부재** | `rg -n "multipart|FilePart|DataBuffer" core/application/src/main` 결과 0건 | 외부 요청을 받을 `file` 도메인 REST 컨트롤러 구현. | 중 |
| **동기식 버퍼링** | `core/domain/src/main/kotlin/cc/midolog/sample/port/file/FileStoragePort.kt:13`의 `suspend fun store(path: String, bytes: ByteArray)` | 대용량 OOM을 막기 위한 순수 추상화 스트림(Chunk) 모델로 변경. | 상 |
| **메타데이터 DB 부재** | `core/application/src/main/resources/db/migration/V1__create_storage_tables.sql` 내부 파일 관리 테이블 목록 0건 확인 | 식별자와 상태를 관리할 `file_meta` 테이블 도입. | 상 |
| **GW 응답 버퍼링 한계** | `core/gateway/src/main/kotlin/cc/midolog/gateway/proxy/ProxyHandler.kt:59` (`bodyToMono(ByteArray::class.java)`) | 트래픽이 GW를 거치지 않는 Presigned 우회 다운로드 도입. | 상 |
