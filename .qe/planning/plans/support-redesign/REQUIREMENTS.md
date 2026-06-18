# Requirements — support-redesign

> backend-init의 `support` 레이어를 실무 표준 수준으로 재설계
> 야망: 실무 표준 · 모듈 구성: 모듈 추가(util / logging / web) · base package `cc.midolog`

## 배경 (현황)
- `support:util`(순수 Kotlin): `IdGenerator`·`TimeProvider`·`Validation` 3개뿐
- `support:logging`: `logback-spring.xml` 하나뿐 (콘솔 + MDC 패턴)
- 8개 모듈이 support에 의존하나 실제 사용 심볼 거의 없음
- 트랜잭션 ID용 `ReactorMdc`가 reactor 의존 때문에 support:logging이 아닌 `core:gateway`에 임시 배치됨
- 웹 공통 요소(`ApiResponse`/`ApiException`/`ErrorCode`/`GlobalExceptionHandler`/`RequestIdFilter`)가 `core:application`·`core:gateway`에 분산

## Functional Requirements

| ID | 우선순위 | 요구사항 |
|----|----------|----------|
| FR-01 | P0 | `support:util`에 재사용 Kotlin 확장 세트(컬렉션/문자열/null-safety)와 공통 상수를 추가한다. |
| FR-02 | P0 | 공통 결과 타입(Result/Either 계열)과 페이징 모델(Page/Sort/PageRequest)을 `support:util`에 정의한다. |
| FR-03 | P1 | 재시도/백오프 유틸과 PII 마스킹 유틸을 제공한다. |
| FR-04 | P0 | `support:logging`을 프로파일별(local 콘솔 / prod JSON) 구조적 로깅으로 재설계한다. |
| FR-05 | P1 | 민감정보 마스킹 로그 컨버터/어펜더를 제공한다. |
| FR-06 | P0 | `ReactorMdc`(트랜잭션 ID ↔ MDC 브리지)를 support로 이전·일반화하고 MDC 키를 표준 상수로 정의한다. |
| FR-07 | P0 | `support:web` 모듈을 신설하고 `ApiResponse`/`ApiException`/`ErrorCode`/`GlobalExceptionHandler`를 이전해 전 모듈이 재사용하게 한다. |
| FR-08 | P1 | `RequestIdFilter`를 일반화해 support로 이전하고, 요청/응답 로깅 필터를 제공한다. |
| FR-09 | P0 | `core:application`·`core:gateway`가 이전된 support 컴포넌트를 사용하도록 리팩터(임포트 교체)한다. |
| FR-10 | P2 | (범위 외 — 후속) Micrometer 메트릭/분산 트레이싱(OpenTelemetry)은 이번 마일스톤 제외. |

## Non-Functional Requirements

| ID | 우선순위 | 요구사항 |
|----|----------|----------|
| NFR-01 | P0 | `support:util`은 순수 Kotlin(프레임워크 비의존)을 유지한다. coroutine 의존이 필요한 유틸은 신중히 도입하거나 분리한다. |
| NFR-02 | P0 | 의존 방향: support는 최하위 레이어로 core/도메인에 역의존하지 않는다. `support:web`은 webflux에만 의존(도메인 무관). |
| NFR-03 | P0 | 재설계 후 `./gradlew build` 성공 + 기존 런타임 동작(트랜잭션 ID·예외·응답) 회귀 없음. |
| NFR-04 | P1 | 이전(migration)은 동작 동일성을 유지(behavior-preserving). 외부 API 응답 형태 불변. |

## Scope 경계
- **포함**: util 확장·결과/페이징·재시도/마스킹, 구조적 로깅·마스킹, ReactorMdc 이전, support:web 신설 + 웹 공통 이전 + core 리팩터.
- **제외(후속)**: Micrometer 메트릭, OpenTelemetry 분산 트레이싱, 캐시/세션 추상화.
