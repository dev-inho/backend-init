# Wave 1-3: 게이트웨이 설계 문서 (docs/GATEWAY.md)

## 작업 완료
- **파일**: `/Users/jinsungkim/Desktop/Workspace/mido/backend-init/docs/GATEWAY.md` 작성 완료

## 핵심 설계 내용

**트랜잭션 ID 관리**: `RequestIdFilter` (WebFilter, @Order(0))가 요청 헤더 `X-Request-Id`를 수신하거나 UUID 생성하여 전체 생명주기에서 추적. MDC 연동으로 로깅 일관성 확보.

**라우팅·프록시**: 경로 기반 분기 — `/api/**` → application(8081), `/batch/**` → batch(8082), `/actuator/**` → application(8081). `ProxyHandler`(WebClient)로 X-Request-Id 헤더 자동 전파.

**향후 계획**: 모니터링 대시보드에서 트랜잭션 ID 기반 요청 목록 조회·필터링 기능 (Phase 2). 게이트웨이 단일 진입점으로 비즈니스 서버 수평 확장 지원.
