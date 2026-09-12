# 확장 가능한 파일 저장 설계 조사 (FILE_STORAGE_OPTIONS)

## 1. 저장소 백엔드 비교

| 저장소 | 공식 사실 (호환성 및 기능) | 작은 팀 기준 평가 (프로젝트 추론) | 공식 문서 URL |
| --- | --- | --- | --- |
| **Local FS** | Java NIO `Files`는 운영 체제의 로컬 파일 시스템을 조작(생성, 읽기, 쓰기, 삭제 등)하는 표준 API를 제공합니다. | [프로젝트 추론] 외부 인프라 비용과 초기 셋업 공수가 없으나, 다중 노드 확장 시 로컬 파일 동기화가 불가능하여 단일 인스턴스 개발/테스트 또는 임시 파일 처리로 용도가 제한됩니다. | [문서](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html) |
| **AWS S3** | 정적 웹 호스팅, 버저닝 및 99.999999999% 내구성을 제공하는 관리형 객체 스토리지 서비스입니다. | [프로젝트 추론] 스토리지 서버 운영 관리 부담이 최소화되고 사실상 업계 표준 API로 락인 우려가 낮으나, 데이터 아웃바운드(Egress) 및 API 호출에 따른 지속적인 비용 모니터링이 필요합니다. | [문서](https://docs.aws.amazon.com/AmazonS3/latest/userguide/Welcome.html) |
| **MinIO** | AWS S3 API와 호환되는 고성능 오픈소스 객체 스토리지 서버로 자체 호스팅이 가능합니다. | [프로젝트 추론] 온프레미스/자체 호스팅 구축을 통해 클라우드 트래픽 비용을 절감할 수 있으나, 작은 팀이 고가용성 인프라 운영, 디스크 장애 복구 및 패치 책임을 직접 부담해야 합니다. | [문서](https://min.io/docs/minio/linux/index.html) |
| **Cloudflare R2** | S3 호환 API를 제공하며 데이터 송신(Egress) 수수료를 부과하지 않는 분산 객체 스토리지입니다. | [프로젝트 추론] 다운로드 트래픽이 많은 환경에서 비용 예측성이 매우 우수하나, EventBridge 등 AWS 고유 관리형 생태계 서비스와의 직접 연계에는 제약이 따릅니다. | [문서](https://developers.cloudflare.com/r2/) |
| **GCS** | 멀티 리전 스토리지와 XML API를 통한 S3 호환성 및 자체 JSON API를 동시 지원합니다. | [프로젝트 추론] 구글 클라우드 인프라 활용에 유리하나, S3 API와의 완전한 1:1 호환이 아니므로 XML API 호환 서브셋에 대한 별도 사전 정합성 검증이 요구됩니다. | [문서](https://cloud.google.com/storage/docs/introduction) |
| **Azure Blob** | 대규모 비정형 데이터를 저장하기 위한 Microsoft Azure의 관리형 객체 스토리지입니다. | [프로젝트 추론] Entra ID 등 Microsoft 생태계 통합에 유리하나, S3 API와 직접 호환되지 않아 Azure 전용 SDK 도입에 따른 벤더 종속(Lock-in)이 불가피합니다. | [문서](https://learn.microsoft.com/en-us/azure/storage/blobs/storage-blobs-introduction) |

## 2. 접근 방식 및 스트리밍 비교

| 항목 | 공식 사실 및 설명 | 공식 문서 URL |
| --- | --- | --- |
| **서버 경유 스트리밍** | Spring WebFlux는 논블로킹 방식으로 HTTP 요청/응답을 `Flux<DataBuffer>` 형태의 스트림으로 처리합니다. | [문서](https://docs.spring.io/spring-framework/reference/web/webflux/reactive-spring.html) |
| **Presigned URL 직접 업로드** | 스토리지 소유자가 발급한 임시 서명 URL을 통해 클라이언트가 스토리지에 객체를 직접 업로드할 수 있습니다. | [문서](https://docs.aws.amazon.com/AmazonS3/latest/userguide/PresignedUrlUploadObject.html) |
| **대용량 Multipart 업로드** | 큰 객체를 여러 파트로 나누어 독립적이고 병렬로 업로드하여 처리량을 향상시킬 수 있습니다. | [문서](https://docs.aws.amazon.com/AmazonS3/latest/userguide/mpuoverview.html) |
| **DataBuffer vs ByteArray** | `DataBuffer`는 다양한 바이트 버퍼 조작을 위한 데이터 추상화 인터페이스이며, 런타임 구현체(`NettyDataBuffer`는 Netty ByteBuf 풀 기반, `DefaultDataBuffer`는 힙 ByteBuffer 기반)에 따라 메모리 관리 방식이 다릅니다. | [문서](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/core/io/buffer/DataBuffer.html) |

## 3. 라이브러리 및 SDK 비교 (WebFlux 호환성 및 기능)

| 라이브러리 | 공식 기능 및 제약 | 작은 팀 기준 선택 및 평가 (프로젝트 추론) | 공식 문서 URL |
| --- | --- | --- | --- |
| **AWS SDK Java v2 (Netty Async)** | Netty 기반 비동기 논블로킹 HTTP 클라이언트를 기본 탑재하여 논블로킹 I/O를 지원합니다. | **(최종 추천)** [프로젝트 추론] WebFlux 이벤트 루프와 호환성이 높고 순수 JVM 환경에서 별도 JNI 의존성 없이 안정적으로 구동되어 작은 팀의 컨테이너 배포 및 셋업 복잡도가 가장 낮습니다. | [문서](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/asynchronous.html) |
| **AWS SDK Java v2 (CRT)** | C 기반 AWS Common Runtime(CRT)을 사용하여 시작 시간 단축과 메모리 사용량 최적화를 제공합니다. | [프로젝트 추론] 높은 처리량에 유리하나 JNI 기반 네이티브 바이너리가 포함되어 경량 컨테이너(Alpine 등) 환경에서 플랫폼별 바이너리 호환성 검증 및 셋업 공수가 추가됩니다. | [문서](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/http-configuration-crt.html) |
| **Spring Cloud AWS** | Spring Boot 환경을 위한 S3 클라이언트 빈 자동 구성 및 `S3Template` 등의 고수준 편의 추상화를 제공합니다. | [프로젝트 추론] 설정 편의성이 높으나 Spring Cloud 릴리스 주기에 종속되어 최신 AWS SDK 기능 반영이 지연될 수 있고, 미세한 비동기 백프레셔 제어가 제한적일 수 있습니다. | [문서](https://docs.awspring.io/spring-cloud-aws/docs/3.1.1/reference/html/index.html) |
| **MinIO Java SDK** | MinIO 및 S3 호환 객체 스토리지를 제어하기 위한 독립적인 Java 클라이언트 라이브러리입니다. | [프로젝트 추론] 단순 동기 작업에는 직관적이나 WebFlux 논블로킹 리액티브 스트리밍(Netty)과의 결합 지원이 부족하여 본 프로젝트의 비동기 헥사고날 구조에는 부적합합니다. | [문서](https://github.com/minio/minio-java) |

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

| 후보 | 공식 사실 (실행 모드 및 특성) | 작은 팀 기준 평가 (프로젝트 추론) | 공식 문서 URL |
| --- | --- | --- | --- |
| **MinIO Testcontainers** | Docker 컨테이너 환경에서 실제 MinIO 서버 인스턴스를 프로그래밍 방식으로 기동하고 관리하는 Testcontainers 공식 모듈입니다. | [프로젝트 추론] 실제 네트워크 통신과 S3 호환 REST API를 충실하게 검증할 수 있으나 도커 데몬 실행 환경이 필수적이므로 일반 단위 테스트와 분리된 별도 통합 태스크(`liveS3Test`)로 격리해야 합니다. | [문서](https://java.testcontainers.org/modules/minio/) |
| **S3Mock** | Docker 컨테이너, Testcontainers, JUnit 4 Rule, JUnit 5 Extension 및 standalone Spring Boot JAR 애플리케이션 등 다양한 실행 모드를 지원하는 Adobe의 S3 모킹 웹 서버입니다. | [프로젝트 추론] JVM 프로세스 내 임베디드(In-memory/임시 디렉터리) 모드로 구동 시 도커 없이 빠른 피드백을 얻을 수 있으나, S3 고급 기능(정밀한 서명 검증, 멀티파트 완성도 등)의 모킹 한계가 있어 완벽한 엣지 케이스 검증에는 한계가 있습니다. | [문서](https://github.com/adobe/S3Mock) |

## 6. 현재 코드 대비 갭 표 (Gap Analysis)

| 기능 갭 | 현재 구현 상태 (명령어 및 파일:행 근거) | 제안 사항 | 우선순위 |
| --- | --- | --- | --- |
| **실제 소비자 부재** | `docs/DEAD_CODE_CANDIDATES.md:14` (실제 FileStoragePort 호출 0건 기록) | 현재 데드 코드를 범용 포트 인터페이스로 교체. | 상 |
| **업로드 컨트롤러 부재** | `rg -n "multipart|FilePart|DataBuffer" core/application/src/main` 결과 0건 | 외부 요청을 받을 `file` 도메인 REST 컨트롤러 구현. | 중 |
| **동기식 버퍼링** | `core/domain/src/main/kotlin/cc/midolog/sample/port/file/FileStoragePort.kt:13`의 `suspend fun store(path: String, bytes: ByteArray)` | 대용량 OOM을 막기 위한 순수 추상화 스트림(Chunk) 모델로 변경. | 상 |
| **메타데이터 DB 부재** | `core/application/src/main/resources/db/migration/V1__create_storage_tables.sql` 내부 파일 관리 테이블 목록 0건 확인 | 식별자와 상태를 관리할 `file_meta` 테이블 도입. | 상 |
| **GW 응답 버퍼링 한계** | `gateway/core/src/main/kotlin/cc/midolog/gateway/proxy/ProxyHandler.kt:58` (`bodyToMono(ByteArray::class.java)`) | 트래픽이 GW를 거치지 않는 Presigned 우회 다운로드 도입. | 상 |
