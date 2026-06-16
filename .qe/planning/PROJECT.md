# PROJECT — backend-init

## Vision
Spring Boot 4 + Kotlin 기반 헥사고날 멀티모듈 백엔드. 게이트웨이가 단일 진입점에서 요청을 받아 비즈니스 서버로 분기하고, 트랜잭션 ID로 요청을 추적·관리한다. 참고 프로젝트(`mido/happ-project/happ-api`)의 구조 컨벤션을 따른다.

## Core Pillars
1. **멀티모듈 헥사고날** — domain(순수) ↔ application/gateway/batch ↔ client/storage/support
2. **게이트웨이 분기** — path-prefix 라우팅 + 트랜잭션 ID(X-Request-Id) 전파
3. **수평 확장** — 무상태 비즈니스 서버, 동일 인스턴스 다중화 가능
4. **관측성** — 트랜잭션 ID ↔ 로깅(MDC), 게이트웨이 요청 확인 화면(향후)

## Naming
- rootProject: `backend-init`
- group: `cc.midolog`
- base package: `cc.midolog`

## Milestones
| Milestone | Plan Slug | 상태 |
|-----------|-----------|------|
| 멀티모듈 구조 셋업 | multimodule-setup | 진행 중 (Phase 1) |
