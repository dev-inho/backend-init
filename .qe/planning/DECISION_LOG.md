# Decision Log — backend-init

| ID | 결정 | 근거 | 날짜 |
|----|------|------|------|
| D001 | 헥사고날(Ports & Adapters) Gradle 멀티모듈 채택 | 참고 happ-api 컨벤션 일치, 도메인-인프라 분리로 테스트/확장 용이 | 2026-06-15 |
| D002 | WebFlux(reactive) + 코루틴 스택 | 게이트웨이 프록시/논블로킹 I/O에 적합, 참고와 동일 | 2026-06-15 |
| D003 | 게이트웨이 path-prefix 라우팅 + X-Request-Id 트랜잭션 ID 전파 | 단일 진입점에서 분기·추적 일원화 | 2026-06-15 |
| D004 | 초기 마일스톤에서는 요청 확인 화면/config 서버/다중 인스턴스를 문서 설계로 제한 | 우선 구조·골격 확정 후 후속 plan에서 선택 구현하기로 함. Phase 3에서 일부 선택 기능은 D012로 갱신 | 2026-06-15 |
| D005 | base package = `cc.midolog` (group 동일) | 사용자 지정 | 2026-06-15 |
| D006 | 게이트웨이 프록시는 hop-by-hop/Host/Content-Length 헤더를 제거 후 전달, X-Request-Id는 `[A-Za-z0-9_-]{1,128}` 검증 후 유지(위반 시 재생성) | request smuggling·로그 인젝션 방지 | 2026-06-15 |
| D007 | support 레이어를 util(순수 Kotlin)·logging(리액티브-인지)·web(신설) 3모듈로 재구성 | 웹 공통(ApiResponse/예외/필터) 분산을 해소하고 전 모듈 재사용 | 2026-06-16 |
| D008 | ReactorMdc(트랜잭션 ID↔MDC 브리지)를 gateway → support:logging 으로 이전 | 로깅 인프라의 올바른 소속, reactor 의존을 logging에 한정 | 2026-06-16 |
| D009 | Micrometer 메트릭·OpenTelemetry 트레이싱은 이번 support 재설계 범위 제외(후속) | "실무 표준" 범위 집중, 과도 확장 지양 | 2026-06-16 |
| D010 | JPA/MyBatis는 브랜치가 아니라 `storage:*` adapter 모듈로 공존 | 공통 domain/application 코드를 main에 유지하고 persistence 구현만 교체 가능하게 하기 위함 | 2026-07-13 |
| D011 | `core:domain`에는 ORM annotation을 두지 않는다 | 도메인 순수성 유지, JPA entity와 domain model 생명주기 분리 | 2026-07-13 |
| D012 | Phase 3 플랫폼 기능은 Gateway 내부 라운드로빈과 request visibility 최소 기능까지만 기본 제공하고, Config Server/OpenTelemetry는 문서화된 선택 기능으로 보류 | 템플릿 초기 복잡도와 운영 의존성을 낮추면서 다중 인스턴스/요청 관측의 최소 검증 가치를 제공 | 2026-07-17 |
| D013 | backend-init-evolution은 A/B/C 완료 후 문서·상태 정합성 마무리로 닫는다 | 기능 추가보다 완료 ledger, 사용자-facing 문서, 잔여 리스크 명시가 commit 전 실제 마무리 조건 | 2026-07-17 |
| D014 | JPA는 domain annotation이 아니라 `storage:jpa` DSL에서 generated entity/repository/mapper를 만든다 | domain plain 원칙을 유지하면서 실제 JPA 테이블 연동을 가능하게 하고, generator validation으로 unsupported DSL을 조기에 실패시키기 위함 | 2026-07-17 |
