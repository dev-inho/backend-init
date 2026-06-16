# Decision Log — backend-init

| ID | 결정 | 근거 | 날짜 |
|----|------|------|------|
| D001 | 헥사고날(Ports & Adapters) Gradle 멀티모듈 채택 | 참고 happ-api 컨벤션 일치, 도메인-인프라 분리로 테스트/확장 용이 | 2026-06-15 |
| D002 | WebFlux(reactive) + 코루틴 스택 | 게이트웨이 프록시/논블로킹 I/O에 적합, 참고와 동일 | 2026-06-15 |
| D003 | 게이트웨이 path-prefix 라우팅 + X-Request-Id 트랜잭션 ID 전파 | 단일 진입점에서 분기·추적 일원화 | 2026-06-15 |
| D004 | 요청 확인 화면/config 서버/다중 인스턴스는 이번 마일스톤 문서 설계만 | 우선 구조·골격 확정 후 후속 plan으로 구현 | 2026-06-15 |
| D005 | base package = `cc.midolog` (group 동일) | 사용자 지정 | 2026-06-15 |
| D006 | 게이트웨이 프록시는 hop-by-hop/Host/Content-Length 헤더를 제거 후 전달, X-Request-Id는 `[A-Za-z0-9_-]{1,128}` 검증 후 유지(위반 시 재생성) | request smuggling·로그 인젝션 방지 | 2026-06-15 |
