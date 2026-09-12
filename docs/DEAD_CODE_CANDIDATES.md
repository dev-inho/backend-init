# 죽은 코드 및 중복 코드 후보 목록 (Dead Code & Duplicate Candidates)

기준 커밋: `6e1a15d8e3006966bab9d45a345c5f6a7915fb50`

소비자가 없는 죽은 코드와 중복 구현 후보를 실측하여 기록한 목록입니다.
코드는 직접 수정하거나 삭제하지 않았으며, 각 후보별 실제 소비자 건수(선언 자체·자체 테스트·문서 제외)와 제안을 정리했습니다.

---

## 1. 죽은 코드 및 중복 후보 목록

| # | 심볼 | 파일:행 | 종류(미사용/중복/샘플/문서없음) | grep 명령 | 소비자 건수 | 제안(삭제/유지/이동/합치기) | 이유 | 상태 |
|---|------|---------|--------------------------------|-----------|-------------|----------------------------|------|------|
| 1 | `FileStoragePort` | `core/domain/src/main/kotlin/cc/midolog/sample/port/file/FileStoragePort.kt:4` | 미사용, 샘플 | `grep -rn "FileStoragePort" --include='*.kt' --include='*.xml' --include='*.gradle' . \| grep -v '/build/'` | 0 | 삭제 | 선언 및 `LocalFileStorageAdapter` 구현 외 실제 비즈니스 로직 및 테스트에서 호출 0건. | 미해결 |
| 2 | `LocalFileStorageAdapter` | `client/storage-file/src/main/kotlin/cc/midolog/client/storage/LocalFileStorageAdapter.kt:12` | 미사용, 샘플 | `grep -rn "LocalFileStorageAdapter" --include='*.kt' --include='*.xml' --include='*.gradle' . \| grep -v '/build/'` | 0 | 삭제 | `core:application`에 runtimeOnly로 등록되어 빈은 뜨지만 주입받아 사용하는 곳 0건, 테스트 0건. client 모듈 잔재. | 미해결 |
| 3 | `SampleService.findPair` | `core/application/src/main/kotlin/cc/midolog/business/service/SampleService.kt:32` | 미사용, 샘플 | `grep -rn "findPair" --include='*.kt' --include='*.xml' --include='*.gradle' . \| grep -v '/build/'` | 0 | 삭제 | `coroutineScope` + `async` 동시성 예시로 작성되었으나 main/test 어디에서도 호출 0건. | 미해결 |
| 4 | `SampleController.echo` | `core/application/src/main/kotlin/cc/midolog/web/sample/SampleController.kt:38` | 미사용, 샘플 | `grep -rn "/api/sample/echo" --include='*.kt' --include='*.xml' --include='*.gradle' . \| grep -v '/build/'` | 0 | 삭제 | `SupportWebTest.kt:225`의 문자열 출현은 로깅 마스킹 테스트용 mock URL일 뿐, 실제 핸들러 호출 0건. | 미해결 |
| 5 | `SampleStreamController.stream` | `core/application/src/main/kotlin/cc/midolog/web/sample/SampleStreamController.kt:18` | 미사용, 샘플 | `grep -rn "/api/sample/stream" --include='*.kt' --include='*.xml' --include='*.gradle' . \| grep -v '/build/'` | 0 | 삭제 | WebFlux Flow 스트리밍 예시이나 컨트롤러를 호출하는 클라이언트나 테스트가 전혀 없음. | 미해결 |
| 6 | `ApiException.invalidInput` | `support/web/src/main/kotlin/cc/midolog/web/exception/ApiException.kt:13` | 미사용 | `grep -rn "invalidInput" --include='*.kt' --include='*.xml' --include='*.gradle' . \| grep -v '/build/'` | 0 | 유지 | main 비즈니스 소비자 0건(자체 테스트 `SupportWebTest.kt:81`만 호출). 향후 파라미터 유효성 검증용 팩토리로 유효함. | 미해결 |
| 7 | `ErrorCode.FORBIDDEN` | `support/web/src/main/kotlin/cc/midolog/web/exception/ErrorCode.kt:10` | 미사용 | `grep -rn "ErrorCode.FORBIDDEN" --include='*.kt' --include='*.xml' --include='*.gradle' . \| grep -v '/build/'` | 0 | 유지 | main 코드 소비자 0건(자체 테스트 `SupportWebTest.kt:64,94`만 사용). HTTP 403 표준 에러 코드로 향후 인가 처리 시 필수. | 미해결 |
| 8 | `UtilDefaults.DEFAULT_DATE_FORMAT`, `DEFAULT_DATETIME_FORMAT` | `support/util/src/main/kotlin/cc/midolog/util/UtilDefaults.kt:18,21` | 미사용, 문서없음 | `grep -rn "DEFAULT_DATE.*FORMAT" --include='*.kt' --include='*.xml' --include='*.gradle' . \| grep -v '/build/'` | 0 | 유지 | 주석의 "날짜 처리 로직에서 참조된다"는 불일치(소비자 0건, 자체 테스트 2건). 날짜 포맷 표준 상수로 라이브러리 차원 보존 가능. | 미해결 |
| 9 | `Collection<T>?.orEmpty()`, `List<T>?.orEmpty()`, `Iterable<T>?.orEmpty()` | `support/util/src/main/kotlin/cc/midolog/util/ext/CollectionExtensions.kt:17,23,29` | 중복, 미사용 | `grep -rn "cc.midolog.util.ext" --include='*.kt' . \| grep -v '/build/'` | 0 | 삭제 | Kotlin stdlib(`kotlin.collections.orEmpty()`)과 동일 시그니처 및 동작. 타 모듈 import 0건. | 미해결 |
| 10 | `requireField`, `Validation.requireNotNull`, `orThrow` | `support/util/src/main/kotlin/cc/midolog/util/ext/NullSafety.kt:51`, `Validation.kt:10,22` | 중복, 미사용 | `grep -rn "requireField" --include='*.kt' . \| grep -v '/build/'` | 0 | 합치기 | null 검증 후 `IllegalArgumentException`을 던지는 기능으로 3개 함수가 동일 목적 중복(`NullSafety.kt:33` 주석도 중복 자인). | 미해결 |
| 11 | `support:util` 미사용 컴포넌트군 (`Outcome`, `Page`, `Retry`, `TimeProvider`, `IdGenerator`) | `support/util/src/main/kotlin/cc/midolog/util/{result/Outcome.kt:12, paging/Page.kt:73, retry/Retry.kt:63, TimeProvider.kt:7, IdGenerator.kt:5}` | 미사용 | `grep -rn "Outcome" --include='*.kt' . \| grep -v '/build/' \| grep -v TaskOutcome` | 0 | 유지 | 실제 외부 모듈 소비자는 `JwtSecretValidator`와 `Masking` 둘뿐. 단 `ExistingUtilCompatibilityTest`의 호환성 약속 대상이므로 임의 삭제 지양. | 미해결 |
| 12 | SQL 컬럼 별칭 ↔ 설정 중복 | `storage/mybatis/src/main/resources/mapper/user/UserMapper.xml:6` & `core/application/.../application.yml:15` | 중복 | `grep -rn "map-underscore-to-camel-case" . \| grep -v '/build/'` | N/A | 유지 | `resultType="map"` 사용으로 인해 Map key 자동 변환이 되지 않아 현재는 별칭 필수. 향후 DTO/Entity 반환으로 리팩토링 시 정리 가능. | 미해결 |
| 13 | JPA DSL 스캐폴딩 태스크 잔재 (`jpaDslPluginInfo`, `validateJpaDslPluginScaffold`) | `build-logic/src/main/kotlin/cc/midolog/buildlogic/jpadsl/JpaDslPlugin.kt:12,61,62` | 문서없음, 미사용 | `grep -rn "jpaDslPluginInfo" . \| grep -v '/build/'` | 0 | 삭제 | 문서 및 빌드 라이프사이클에 미연결. 플러그인 초기 스캐폴딩 잔재이며 실질적 검증은 `validateJpaDslGeneratorNegativeCases` 등이 수행. | 미해결 |
| 14 | JWT 키 생성 및 검증 로직 중복 (`JwtProvider` ↔ `JwtAuthFilter`) | `core/application/.../JwtProvider.kt:20,33` ↔ `core/gateway/.../JwtAuthFilter.kt:30,45` | 중복 | `grep -rn "Keys.hmacShaKeyFor" --include='*.kt' . \| grep -v '/build/'` | 2 | 합치기 | `core:application`과 `core:gateway`가 SecretKey 생성 및 토큰 검증 로직을 각각 중복 구현. S3 워커가 `JwtCodec`으로 통합 작업 진행 중. | 진행 중(S3) |
| 15 | `RequestIdFilterTest` 중복 | `core/gateway/src/test/.../RequestIdFilterTest.kt` ↔ `support/web/src/test/.../SupportWebTest.kt:154-207` | 중복 | `grep -rn "class RequestIdFilterTest" --include='*.kt' . \| grep -v '/build/'` | 2 | 삭제 | `RequestIdFilter`가 `support:web`으로 이전되면서 동일한 테스트가 두 모듈에 중복 존재. S1 워커가 gateway 측 테스트 삭제 진행 중. | 진행 중(S1) |
| 16 | `NullSafety.ifNull` (신규) | `support/util/src/main/kotlin/cc/midolog/util/ext/NullSafety.kt:27` | 중복, 미사용 | `grep -rn "\.ifNull(" --include='*.kt' . \| grep -v '/build/'` | 0 | 삭제 | Kotlin 기본 엘비스 연산자(`?:`)와 기능이 100% 동일한 불필요한 래퍼 함수. 자체 테스트(`NullSafetyTest.kt`) 외 참조 0건. | 미해결 |
| 17 | `CollectionExtensions.chunkedBy` (신규) | `support/util/src/main/kotlin/cc/midolog/util/ext/CollectionExtensions.kt:49` | 미사용 | `grep -rn "chunkedBy" --include='*.kt' . \| grep -v '/build/'` | 0 | 유지 | 연속된 동일 키 요소를 묶는 유틸리티이나 현재 프로젝트 전반에서 실제 소비자가 전무함. | 미해결 |
| 18 | `StringExtensions.trimToNull`, `abbreviate` (신규) | `support/util/src/main/kotlin/cc/midolog/util/ext/StringExtensions.kt:9,23` | 미사용 | `grep -rn "trimToNull" --include='*.kt' . \| grep -v '/build/'` | 0 | 유지 | 문자열 가공 확장 함수이나 프로젝트 내 실제 소비자 0건(자체 테스트 외 참조 없음). | 미해결 |
| 19 | `SampleJobConfig` (신규) | `core/batch/src/main/kotlin/cc/midolog/batch/job/SampleJobConfig.kt:19,26,42` | 미사용, 샘플 | `grep -rn "SampleJobConfig" --include='*.kt' --include='*.xml' --include='*.gradle' . \| grep -v '/build/'` | 0 | 유지 | sample 테이블 집계 로깅 Tasklet Job이나, 실행 트리거 엔드포인트도 없고 테스트 코드도 전무함. | 미해결 |
| 20 | `CreateSampleRequest` (신규) | `core/application/src/main/kotlin/cc/midolog/web/sample/dto/CreateSampleRequest.kt:7` | 미사용, 샘플 | `grep -rn "CreateSampleRequest" --include='*.kt' --include='*.xml' --include='*.gradle' . \| grep -v '/build/'` | 0 | 삭제 | `SampleController.create`의 파라미터로만 쓰이며 컨트롤러 자체 테스트 및 외부 호출 0건. 컨트롤러 정리 시 함께 삭제. | 미해결 |

---

## 2. 🔶 사용자 결정 필요

삭제를 실행할 경우 발생할 수 있는 영향과 선행/후속 조치 사항입니다:

- **#1 FileStoragePort & #2 LocalFileStorageAdapter**: `client:storage-file` 모듈 전체 제거 시 `settings.gradle`(`include 'client:storage-file'`), `core/application/build.gradle`(`runtimeOnly project(':client:storage-file')`), `docs/MODULE_GUIDE.md`의 모듈 가이드 동시 갱신 필요.
- **#3 SampleService.findPair**: `SampleService.kt` 내부 메서드만 제거되므로 외부 모듈이나 설정 파일 변경 영향 없음.
- **#4 SampleController.echo & #5 SampleStreamController.stream & #20 CreateSampleRequest**: 샘플 엔드포인트 제거 시 `SupportWebTest.kt:225`의 마스킹 테스트용 mock URL을 다른 유효 경로(`/api/sample/ping` 등)로 대체 필요. `SampleStreamController.kt` 파일 통째 제거 가능.
- **#6 ApiException.invalidInput & #7 ErrorCode.FORBIDDEN**: 삭제 시 `SupportWebTest.kt`의 해당 단위 검증 테스트 동시 제거 필요. 도메인/컨트롤러 확장 시 재작성 비용을 고려하면 보존 권장.
- **#8 UtilDefaults.DEFAULT_DATE_FORMAT, DEFAULT_DATETIME_FORMAT**: 삭제 시 `UtilDefaultsTest.kt`의 정합성 테스트 동시 제거 필요. 외부 참조가 없어 런타임 사이드이펙트는 없음.
- **#9 CollectionExtensions.orEmpty**: 삭제 시 `CollectionExtensionsTest.kt` 제거 필요. Kotlin 표준 라이브러리(`kotlin.collections.orEmpty`)가 투명하게 대체하므로 호출부 컴파일 영향 없음.
- **#10 null 헬퍼 3중복 (requireField/requireNotNull/orThrow)**: `ExistingUtilCompatibilityTest.kt`가 `Validation.requireNotNull`과 `orThrow`의 하위 호환성을 검증 중이므로, 임의 삭제 시 해당 테스트 실패. 호환성 약속 재정의 후 단일 시그니처로 통일 필요.
- **#11 support:util 미사용 컴포넌트군 (Outcome/Page/Retry/TimeProvider/IdGenerator)**: `ExistingUtilCompatibilityTest.kt`가 `TimeProvider`, `IdGenerator`의 시그니처를 잠그고 있으므로, 삭제 시 호환성 테스트 계약 수정 필요. 범용 공통 라이브러리 정책에 따라 보존 여부 결정 필요.
- **#12 UserMapper.xml SQL 별칭**: 현재 `resultType="map"` 구조에서는 별칭 제거 시 `displayName` 프로퍼티 바인딩 깨짐 발생. `UserEntity` 또는 DTO 매핑으로 전환하는 리팩토링 시에만 안전하게 제거 가능.
- **#13 build-logic jpaDslPluginInfo & validateJpaDslPluginScaffold**: 삭제 시 `JpaDslPluginTest.kt`의 해당 태스크 테스트 제거 필요. 플러그인의 소스코드 생성 기능(`generateJpaDslSources`)에는 영향 없음.
- **#14 JWT 키 생성 및 검증 중복**: S3 워커(`refactor/app-layering-jwt`)가 `JwtCodec`으로 통합 진행 중. 머지 후 `JwtProvider`와 `JwtAuthFilter`가 공통 코덱을 참조하도록 전환됨.
- **#15 RequestIdFilterTest 중복**: S1 워커(`refactor/gw-layering`)가 `core:gateway` 측 테스트 삭제 진행 중. 삭제되더라도 `support:web`의 `SupportWebTest`에서 동일 검증이 100% 수행되므로 테스트 누락 없음.
- **#16 NullSafety.ifNull**: 삭제 시 `NullSafetyTest.kt` 제거 필요. Kotlin 기본 `?:` 연산자로 100% 대체 가능하므로 런타임 영향 없음.
- **#17 CollectionExtensions.chunkedBy & #18 StringExtensions**: 삭제 시 각 단위 테스트 파일 제거 필요. 외부 소비자가 없어 런타임 사이드이펙트 없음.
- **#19 core:batch SampleJobConfig**: 제거 시 `BatchApplication` 기동 시 빈 등록이 없어지며, `core:batch` 모듈에 실제 배치 Job이 하나도 남지 않게 됨. 향후 배치 구조 가이드 역할 감안 시 유지 권장.
