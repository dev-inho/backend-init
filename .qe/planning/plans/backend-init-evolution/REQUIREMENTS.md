# Requirements — backend-init-evolution

## Scope

backend-init을 재사용 가능한 운영형 백엔드 템플릿으로 발전시킨다. 진행 순서는 A(운영형 템플릿화) → B(도메인 예제 확장) → C(플랫폼화)로 고정한다.

## P0 — Must Have

| ID | Requirement | Verification |
|----|-------------|--------------|
| R-P0-01 | Gateway proxy가 query string, request/response header, streaming response를 손실 없이 전달한다. | Gateway proxy 테스트와 `./gradlew test` 통과 |
| R-P0-02 | Gateway upstream timeout, 오류 응답, fallback 정책이 명시되고 테스트된다. | timeout/error path 테스트 통과 |
| R-P0-03 | `local/dev/prod` profile 정책을 분리하고, 기본 실행이 운영 profile을 암묵 활성화하지 않는다. | 설정 파일 검토와 context load 테스트 통과 |
| R-P0-04 | 문서가 실제 패키지/모듈 위치와 일치한다. | README/docs 경로 검토 |
| R-P0-05 | 운영 기본 관측성(request id, structured log, metrics, health)을 템플릿 기본값으로 제공한다. | actuator/logging 테스트 또는 smoke check |
| R-P0-06 | Phase 1 완료 후 전체 테스트가 통과한다. | `./gradlew test` 통과 |

## P1 — Should Have

| ID | Requirement | Verification |
|----|-------------|--------------|
| R-P1-01 | 샘플 도메인을 User/Auth/File 중심의 실전 예제로 대체하거나 병행 제공한다. | API/service/repository contract 테스트 통과 |
| R-P1-02 | MyBatis/JPA 선택 구조가 신규 도메인에서도 동일 계약으로 검증된다. | persistence profile별 contract 테스트 통과 |
| R-P1-03 | Docker Compose 기반 PostgreSQL/Redis local smoke 경로를 문서화한다. | local smoke command 문서와 실행 확인 |
| R-P1-04 | CI quality gate 계획이 정리된다. | CI 문서 또는 workflow 초안 |
| R-P1-05 | 템플릿 사용자가 새 도메인을 추가하는 절차가 문서화된다. | module guide 업데이트 |

## P2 — Could Have

| ID | Requirement | Verification |
|----|-------------|--------------|
| R-P2-01 | 다중 application instance 라우팅 전략을 구현하거나 선택 가능한 설정으로 제공한다. | Gateway route 설정 테스트 |
| R-P2-02 | Config server 도입 여부와 대안을 결정하고 최소 구현 또는 보류 문서를 남긴다. | decision log 업데이트 |
| R-P2-03 | Gateway request dashboard를 최소 기능으로 제공한다. | dashboard endpoint/UI smoke check |
| R-P2-04 | OpenTelemetry tracing 연동을 선택 기능으로 제공한다. | trace id propagation smoke check |

## Non-Goals

- 즉시 대규모 마이크로서비스 플랫폼으로 확장하지 않는다.
- Sample 코드를 무작정 늘리지 않는다.
- 운영 secret 값을 저장소에 커밋하지 않는다.
