# Roadmap — backend-init-evolution

## Overview

backend-init 발전은 A → B → C 순서로 진행한다.

- Phase 1: A — 운영형 템플릿화
- Phase 2: B — 도메인 예제 확장
- Phase 3: C — 플랫폼화

## Phase 1 — A: 운영형 템플릿화

**Goal:** 현재 골격을 실서비스 시작점으로 신뢰할 수 있게 Gateway, 환경 설정, 관측성, 문서 정합성을 강화한다.

**Requirements:** R-P0-01, R-P0-02, R-P0-03, R-P0-04, R-P0-05, R-P0-06, R-P1-03, R-P1-04

### Waves

#### Wave 1 — Gateway correctness

- Preserve query strings and encoded URI details in proxy forwarding.
- Stream downstream responses without forcing all bodies into memory.
- Define and test upstream timeout/error response behavior.
- Re-check hop-by-hop header sanitation and request id propagation.

#### Wave 2 — Runtime configuration

- Split local defaults from base `application.yml`.
- Make `local/dev/prod` profile activation explicit.
- Document required environment variables for application, gateway, and batch.
- Add context-load coverage for representative profiles where practical.

#### Wave 3 — Observability and docs

- Align Gateway docs with actual `support:web` filter ownership.
- Define baseline health/info/metrics exposure.
- Decide structured log format and request latency metric scope.
- Update README, module guide, and gateway guide to match implemented behavior.

**Success Criteria**

- `./gradlew test` passes.
- Gateway proxy tests cover query forwarding, header sanitation, streaming behavior, and timeout/error behavior.
- No production-sensitive default profile is auto-enabled from base config.
- Docs point to real package/module ownership.

## Phase 2 — B: 도메인 예제 확장

**Goal:** Sample 중심 골격을 실제 프로젝트 시작에 가까운 User/Auth/File 예제로 확장하되, 템플릿 복잡도는 통제한다.

**Requirements:** R-P1-01, R-P1-02, R-P1-05

### Waves

#### Wave 1 — Domain replacement strategy

- Decide whether `sample` is replaced or retained as a minimal teaching example.
- Define User/Auth/File bounded contexts and module placement.
- Define use cases that prove ports/adapters without becoming a full product.

#### Wave 2 — Persistence and contract tests

- Add repository ports and adapters for selected domain examples.
- Keep MyBatis/JPA implementation selectable by profile.
- Extend contract tests across persistence implementations.

#### Wave 3 — Template authoring docs

- Document how to add a new domain from model → port → service → adapter → controller.
- Document when to choose MyBatis vs JPA.
- Add a minimal checklist for project bootstrap from this template.

**Success Criteria**

- New domain example compiles and tests pass.
- Persistence implementation switching remains profile-based.
- Documentation lets a new service start without reverse-engineering sample code.

## Phase 3 — C: 플랫폼화

**Goal:** 다중 인스턴스, 중앙 설정, 요청 관측 화면 같은 플랫폼 기능을 선택적으로 도입한다.

**Requirements:** R-P2-01, R-P2-02, R-P2-03, R-P2-04

### Waves

#### Wave 1 — Scaling and routing

- Design application instance discovery/routing mode.
- Choose Gateway-only routing vs external load balancer assumption.
- Add health-check based route verification where practical.

#### Wave 2 — Config and secrets

- Decide Config Server vs environment/secret-manager baseline.
- Document secure configuration flow.
- Implement only if the operational value outweighs template complexity.

#### Wave 3 — Request visibility

- Define request dashboard minimum data model.
- Choose in-memory vs Redis-backed request event storage.
- Protect any dashboard endpoint by default.

**Success Criteria**

- Platform features are optional, documented, and disabled unless explicitly enabled.
- Any implemented dashboard/config/scaling behavior has tests or smoke checks.
- Decision log records what was implemented vs intentionally deferred.
