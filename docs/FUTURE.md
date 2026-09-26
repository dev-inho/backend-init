# 향후 설계 (Future Work)

> **중요 안내**: 아래 항목은 선택 기능입니다. 기본 실행에서는 비활성화하거나 문서화된 보류 상태로 둡니다.

---

## 1. 비즈니스 서버 다중 인스턴스 (수평 확장)

### 목적
요청 증가 시 동일 application 서버를 2개 이상 병렬 운용하여 처리 용량 확대

### 설계 개요
- **무상태(Stateless) 설계**: 비즈니스 서버는 상태를 가지지 않음
  - 세션, 상태 데이터는 Redis 등 외부 캐시/저장소로 위임
  - 요청 처리 간 독립성 확보
- **분산 라우팅**: 게이트웨이가 들어온 요청을 다중 인스턴스로 분산
  - 현재 구현은 `gateway.routes.application-urls` 기반 라운드로빈 및 능동 헬스체크 연동 완료
  - 가중치 기반 라우팅 및 동적 서비스 디스커버리(Service Discovery)는 후속 확장
  - 또는 외부 로드밸런서(AWS ALB, Nginx 등) 활용

### 고려사항
- **로드밸런싱**: 게이트웨이 내장 라우팅 vs. 외부 LB
- **헬스체크**: `/actuator/health` 엔드포인트를 통한 주기적 능동 헬스체크 및 장애 타겟 자동 제외/복구 연동 완료 (`GatewayRouteSelector.kt`, PR #35)
- **무중단 배포(Blue-Green Deployment)**: 서버 업데이트 중 서비스 지속성 확보

### 상태
✅ **라운드로빈 및 헬스체크 연동 완료** — Gateway 내부 라운드로빈 및 능동 헬스체크 기반 장애 격리/fail-open 구현 완료 (`GatewayRouteSelector.kt`, PR #35). 가중치 기반 분산 및 외부 LB/동적 service discovery 연동은 후속 과제로 유지.

---

## 2. Config 서버 (중앙 집중식 설정 관리)

### 목적
여러 모듈 및 인스턴스의 설정을 중앙에서 일괄 관리하여 배포/변경 효율성 향상

### 설계 개요
- **별도 설정 서버**: config 전담 모듈 또는 독립 서버 운영
  - Git 저장소(또는 파일 기반)에서 설정 파일 관리
  - 환경별 분리: 현재 프로젝트는 `application.yml` 및 `application-local.yml`만 존재하며 `application-dev.yml`, `application-prod.yml`은 아직 없음(`support:logging`의 `logback-spring.xml`에만 `prod` 프로파일 블록 존재). 향후 다중 환경 확장에 따라 dev/prod 프로파일 분리 검토.
- **부팅 시 조회**: 각 서비스가 기동 시 설정 서버에서 환경별 설정 일괄 조회
- **이미지 빌드 최소화**: 설정 변경 시 재빌드 불필요

### 고려사항
- **도입 여부 검토**: 프로젝트 규모/복잡도에 따라 필요성 판단 ("둘까 함")
- **설정 변경 전파**: 런타임 시 설정 갱신 여부 및 메커니즘
- **민감정보 분리**: API Key, DB 비밀번호는 `.env` 또는 secret 매니저로 별도 관리
- **보안**: 설정 서버 접근 제어(SSL/TLS, 인증)

### 상태
📌 **보류 결정** — 현재 baseline은 환경변수와 secret manager 주입이다. Config Server는 여러 배포 환경/인스턴스에서 설정 변경 전파가 운영 병목이 될 때 별도 task로 도입한다.

---

## 3. 게이트웨이 요청 확인 화면 (모니터링 대시보드)

### 목적
게이트웨이로 들어온 요청과 트랜잭션 ID(`X-Request-Id`)를 시각화된 화면에서 실시간 확인

### 설계 개요
- **요청 로그 수집**: 게이트웨이 필터/미들웨어에서 요청 정보 기록
  - 메서드, URL, 응답 코드, 타임스탬프, 트랜잭션 ID 등
- **저장소**: 현재 최소 구현은 bounded in-memory store (`RequestEventStore`)
- **조회 UI**: 
  - `/internal/gateway/requests` JSON endpoint 제공
  - HTML 대시보드와 Redis-backed 분산 store는 후속 확장

### 고려사항
- **저장소 선택**: 인메모리(간편, 서버 재시작 시 소실) vs. Redis(분산, 지속성)
- **보안**: 모니터링/가시성 엔드포인트 접근 제어는 Basic Auth/IP whitelist 대신 JWT Bearer 토큰 인증으로 결정 및 구현 완료(`gateway/core/src/main/kotlin/cc/midolog/gateway/filter/JwtAuthFilter.kt`의 `/internal/gateway/` 경로 분기)
- **실시간성**: 요청 데이터 업데이트 주기 및 화면 갱신 방식
- **성능**: 요청 로깅이 게이트웨이 성능에 미치는 영향 최소화

### 상태
✅ **최소 구현 완료** — 기본 비활성화. `GATEWAY_REQUEST_VISIBILITY_ENABLED=true`일 때만 최근 요청 event를 조회하며, `/internal/gateway/**` 엔드포인트는 `JwtAuthFilter`를 통한 JWT Bearer 토큰 인증으로 보호된다. 상세 설정은 `./GATEWAY.md` 참조.

---

## 4. 트랜잭션 ID ↔ 로깅(MDC) 고도화

### 목적
게이트웨이 진입점부터 최종 응답까지 전체 요청 흐름을 트랜잭션 ID(`X-Request-Id`)로 추적하고 모든 로그에 일관되게 출력

### 설계 개요
- **트랜잭션 ID 생성**: 게이트웨이에서 각 요청마다 `X-Request-Id` 생성 또는 클라이언트에서 전달받은 값 사용
- **MDC(Mapped Diagnostic Context) 바인딩**: 
  - 필터/인터셉터에서 `X-Request-Id`를 MDC에 바인딩
  - 로그 패턴에 `%X{X-Request-Id}` 표기
- **다운스트림 전파**: 
  - 비즈니스 서버 → 타 서비스 호출 시 `X-Request-Id` 헤더 전달
  - 각 서버에서 동일 ID로 MDC 설정
- **로그 일관성**: 모든 계층(게이트웨이, 비즈니스 서버, 외부 서비스)에서 동일 ID로 로그 출력

### 고려사항
- **비동기 작업**: ThreadLocal 기반 MDC는 비동기 작업에서 소실 위험 → 명시적 전달 필요
- **성능**: MDC 바인딩/언바인딩 오버헤드 최소화
- **외부 라이브러리**: Micrometer Tracing / Sleuth 등 분산 추적 라이브러리 고려
- **OpenTelemetry**: exporter/collector 운영 전제가 생기므로 현재는 보류하고 request id 기반 추적을 baseline으로 둔다.
- **로그 저장/분석**: 중앙 집중식 로깅(ELK Stack 등)과 연동 시 트랜잭션 ID 기반 검색

### 상태
📌 **초기 구현 완료 + OpenTelemetry 보류** — `support:web`의 요청 ID 필터와 `support:logging`의 MDC/마스킹 로깅은 구현되어 있으며, OpenTelemetry exporter/collector 연동은 별도 운영 가치가 확인될 때 optional module로 추가한다.

---

## 5. CI 품질 게이트

### 목적
템플릿 사용자가 새 프로젝트를 시작할 때 최소 품질 기준을 자동 검증한다.

### 구현 현황
- **보안 스캔 워크플로우 (`.github/workflows/security.yml`)**: PR 및 main push 시 자동 실행
  - OWASP Dependency-Check (의존성 보안 취약점 점검)
  - Gitleaks (시크릿 유출 탐지)
- **빌드·테스트 워크플로우 (`.github/workflows/ci.yml`)**: PR 및 main push 시 자동 실행
  - `./gradlew build` (전체 모듈 컴파일과 테스트, `DomainPurityTest` 등 아키텍처 가드 포함)
  - `./gradlew -p build-logic test`
  - PR에서는 `git diff --check`로 공백 오류 검사
  - 실패 시 테스트 리포트를 아티팩트로 업로드

### 향후 추가 후보
- architecture rule test (계층 규칙 검증)
- Docker Compose 기반 PostgreSQL/Redis live smoke test

### 상태
✅ **보안 스캔 + 빌드·테스트 게이트 구축 완료** — `security.yml`과 `ci.yml`이 PR마다 실행된다. MinIO가 필요한 `liveS3Test`와 Docker 기반 live smoke test는 CI에 포함하지 않는다.

---

## 6. ProxyHandler 응답 스트리밍 전환

### 목적
게이트웨이 프록시 중계 시 대용량 응답에 대한 메모리 점유 최적화 및 첫 바이트 전송 지연(TTFB) 개선

### 현재 동작 및 트레이드오프
현재 `gateway/core`의 `ProxyHandler.kt`는 클라이언트 요청 본문은 `DataBuffer` 스트림으로 다운스트림에 전달하지만, 다운스트림 응답은 `response.bodyToMono(ByteArray::class.java)`를 통해 메모리에 바이트 배열로 전체 버퍼링한 뒤 반환한다.
- **장점**: 응답 헤더 정제, 상태 코드 조작, 다운스트림 장애 시 502(Bad Gateway) 및 504(Gateway Timeout) 폴백 처리가 단순하고 직관적이다.
- **단점/트레이드오프**: 대용량 파일 다운로드나 거대 JSON 응답 수신 시 게이트웨이 JVM 힙 메모리 사용량이 급증하여 메모리 압박이 발생할 수 있는 트레이드오프가 있다.

### 향후 방향
다운스트림 응답 본문 또한 `DataBuffer` 기반 리액티브 스트리밍(`body(BodyInserters.fromDataBuffers(...))`)으로 전환하여 메모리 복사를 최소화하고 대용량 응답 중계 성능을 확보한다.

---

## 7. 게이트웨이 탑재식(Starter) 지원 로드맵

### 목적
현재의 독립 프로세스 게이트웨이 외에도, 단일 서버 환경에서 비즈니스 애플리케이션 JVM 내에 게이트웨이 라우팅/필터 체인을 임베디드로 탑재하여 운영 복잡도와 네트워크 홉을 최소화하는 구조 제공

### 로드맵 개요
자체 구현 필터 체인(가시성, JWT, Rate Limit 등)을 보존하면서 starter 형태로 모듈화하는 단계별 로드맵을 수립함:
- **Phase 0**: 사전 준비 (패키지 재배치 및 JWT 공유 모듈화)
- **Phase 1**: `gateway:core` 및 `gateway:autoconfigure` 분할
- **Phase 2**: 단독 실행 애플리케이션 껍데기 `gateway/app` 및 `gateway:starter` 신설
- **Phase 3**: 동일 프로세스 내 In-process 디스패치 또는 루프백 라우팅 및 보안 체인 분리
- **Phase 4**: 원격 모드 대상 능동 헬스체크 및 멱등 재시도·메트릭 연동 완료 (PR #33·#35). 타깃별 서킷 브레이커도 standalone/remote 프록시에 도입 완료 (PR #63, 상세는 [`docs/GATEWAY.md`](./GATEWAY.md) 3.3절).

상세 설계, 기능 매트릭스 갭 분석 및 사용자 결정 항목은 [`docs/GATEWAY_STARTER_PLAN.md`](./GATEWAY_STARTER_PLAN.md) 참조.

---

## 8. 코드 정리 및 중복 제거 후보

### 목적
소비자가 없거나 다른 모듈/표준 라이브러리와 중복되는 코드 및 샘플 컴포넌트를 식별하여 유지보수 부채를 줄임

### 주요 정리 대상군
- **미사용 샘플 컴포넌트**: `FileStoragePort`, `LocalFileStorageAdapter`(`FileStorageIntegrationTest` 가드 대상이라 `@Deprecated`로 유지) 등. `SampleService.findPair`, `SampleController.echo`, `SampleStreamController`는 2026-09-25에 삭제됨
- **중복 유틸리티**: null 검증 헬퍼 3중복(`requireField`, `Validation.requireNotNull`, `orThrow`) 등. `CollectionExtensions.orEmpty`와 `NullSafety.ifNull`은 2026-09-25에 삭제됨
- **중복 로직 통합**: JWT 코덱 통합(`JwtCodec`) 및 모듈 이전으로 인한 중복 테스트 정리

상세 실측 건수, 제안(삭제/유지/합치기) 및 영향도 가이드는 [`docs/DEAD_CODE_CANDIDATES.md`](./DEAD_CODE_CANDIDATES.md) 참조.

---

## 관련 문서

- `./ARCHITECTURE.md` — 전체 아키텍처 및 모듈 구성
- `./MODULE_GUIDE.md` — 각 모듈의 책임과 인터페이스
- `./GATEWAY.md` — 게이트웨이 상세 설계 (라우팅, 필터 등)
- `./GATEWAY_STARTER_PLAN.md` — 게이트웨이 탑재식(Starter) 지원 및 단계별 로드맵
- `./DEAD_CODE_CANDIDATES.md` — 죽은 코드 및 중복 코드 후보 목록
- `./JPA_DSL_RISK_REGISTER.md` — JPA DSL 잔여 리스크 레지스터

---

**마지막 갱신**: 2026-09-12
