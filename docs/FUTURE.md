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
  - 현재 최소 구현은 `gateway.routes.application-urls` 기반 라운드로빈
  - 가중치 기반, health-check 제외, service discovery는 후속 확장
  - 또는 외부 로드밸런서(AWS ALB, Nginx 등) 활용

### 고려사항
- **로드밸런싱**: 게이트웨이 내장 라우팅 vs. 외부 LB
- **헬스체크**: `/actuator/health` 엔드포인트를 통한 인스턴스 상태 모니터링
- **무중단 배포(Blue-Green Deployment)**: 서버 업데이트 중 서비스 지속성 확보

### 상태
✅ **최소 구현 완료** — Gateway 내부 라운드로빈은 optional 설정으로 제공. 외부 LB/service discovery와 health-check 기반 제외는 후속.

---

## 2. Config 서버 (Spring Cloud Config)

### 목적
여러 모듈 및 인스턴스의 설정을 중앙에서 일괄 관리하여 배포/변경 효율성 향상

### 설계 개요
- **별도 설정 서버**: config 전담 모듈 또는 독립 서버 운영
  - Git 저장소(또는 파일 기반)에서 설정 파일 관리
  - 환경별 분리: `application-local.yml`, `application-dev.yml`, `application-prod.yml`
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
- **저장소**: 현재 최소 구현은 bounded in-memory store
- **조회 UI**: 
  - `/internal/gateway/requests` JSON endpoint 제공
  - HTML 대시보드와 Redis-backed 분산 store는 후속 확장

### 고려사항
- **저장소 선택**: 인메모리(간편, 서버 재시작 시 소실) vs. Redis(분산, 지속성)
- **보안**: 모니터링 화면 접근 제어(Basic Auth, IP whitelist 등)
- **실시간성**: 요청 데이터 업데이트 주기 및 화면 갱신 방식
- **성능**: 요청 로깅이 게이트웨이 성능에 미치는 영향 최소화

### 상태
✅ **최소 구현 완료** — 기본 비활성화. `GATEWAY_REQUEST_VISIBILITY_ENABLED=true`일 때만 최근 요청 event를 조회한다. 상세 설정은 `./GATEWAY.md` 참조.

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
- **외부 라이브러리**: Spring Cloud Sleuth 등 분산 추적 라이브러리 고려
- **OpenTelemetry**: exporter/collector 운영 전제가 생기므로 현재는 보류하고 request id 기반 추적을 baseline으로 둔다.
- **로그 저장/분석**: 중앙 집중식 로깅(ELK Stack 등)과 연동 시 트랜잭션 ID 기반 검색

### 상태
📌 **초기 구현 완료 + OpenTelemetry 보류** — `support:web`의 요청 ID 필터와 `support:logging`의 MDC/마스킹 로깅은 구현되어 있으며, OpenTelemetry exporter/collector 연동은 별도 운영 가치가 확인될 때 optional module로 추가한다.

---

## 5. CI 품질 게이트

### 목적
템플릿 사용자가 새 프로젝트를 시작할 때 최소 품질 기준을 자동 검증한다.

### 현재 기준
- 필수: `./gradlew test`
- 권장: Gateway proxy regression test, profile config regression test 유지

### 향후 추가 후보
- dependency vulnerability scan
- secret scan
- architecture rule test
- Docker Compose 기반 PostgreSQL/Redis smoke test

### 상태
✅ **초기 CI workflow 추가** — 현재 템플릿은 `./gradlew test`를 필수 품질 게이트로 사용하고, GitHub Actions에서는 dependency/security scan을 별도 workflow로 실행한다. organization별 정책, threshold, SARIF 업로드, PR required check 지정은 후속 운영 정책으로 확정한다.

---

## 관련 문서

- `./ARCHITECTURE.md` — 전체 아키텍처 및 모듈 구성
- `./MODULE_GUIDE.md` — 각 모듈의 책임과 인터페이스
- `./GATEWAY.md` — 게이트웨이 상세 설계 (라우팅, 필터 등)

---

**마지막 갱신**: 2026-07-17
