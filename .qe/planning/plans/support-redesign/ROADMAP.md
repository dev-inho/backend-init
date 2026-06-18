# Roadmap — support-redesign

> backend-init `support` 레이어 실무 표준 재설계
> 구성: `support:util`(순수 Kotlin) · `support:logging`(리액티브-인지 로깅) · `support:web`(신설, 웹 횡단)
> base package `cc.midolog` · Groovy gradle · 의존 방향: support = 최하위, core/도메인 역의존 금지

## 현황 요약
- support:util: IdGenerator·TimeProvider·Validation (3개)
- support:logging: logback-spring.xml (콘솔+MDC 패턴) 1개
- 웹 공통(ApiResponse/ApiException/ErrorCode/GlobalExceptionHandler/RequestIdFilter)이 core에 분산, ReactorMdc는 gateway에 임시 배치

## Phase 1 — support:util 재설계 (순수 Kotlin 기반기) (ACTIVE)
**Goal**: 프레임워크 비의존 재사용 유틸 라이브러리를 정립한다. (검증: 빌드 성공 + 확장/결과/페이징/마스킹 단위 테스트 통과)
**Requirements**: FR-01, FR-02, FR-03, NFR-01

### Wave 1 — 확장/상수 기반
- Kotlin 확장 함수(컬렉션: orEmpty/chunkedBy, 문자열: trimToNull/abbreviate, null-safety: ifNull/requireField)
- 공통 상수/열거(공용 포맷, 기본 페이지 크기 등) 정의
- 기존 Validation/TimeProvider/IdGenerator 정리·일관화

### Wave 2 — 결과/페이징 모델
- 결과 타입: Result/Either 계열(Outcome<S, F>) + 매핑 함수
- 페이징 모델: PageRequest(page/size/sort), Page<T>(content/total/page meta), Sort/Direction

### Wave 3 — 재시도/마스킹
- 재시도/백오프 유틸(시도 횟수·지연·예외 필터) — 순수/코루틴 분리 검토
- PII 마스킹 유틸(email/phone/card 부분 마스킹)

## Phase 2 — support:logging 재설계 (구조적 로깅 + 리액티브 인지)
**Goal**: 운영급 로깅 인프라(프로파일별 + JSON + 마스킹 + MDC 표준)를 구축한다. (검증: local 콘솔/prod JSON 출력 + 트랜잭션 ID·마스킹 동작 확인)
**Requirements**: FR-04, FR-05, FR-06

### Wave 1 — 프로파일별 구조적 로깅
- logback-spring.xml 재설계: springProfile(local=콘솔 패턴 / prod=JSON)
- JSON 인코더 선택(logstash-logback-encoder 또는 Logback 네이티브 JSON) — spec에서 확정
- MDC 키 표준 상수(X-Request-Id 등) 정의

### Wave 2 — 마스킹 + MDC 브리지 이전
- 민감정보 마스킹 로그 컨버터/패턴 레이아웃
- ReactorMdc를 core:gateway → support:logging 으로 이전·일반화(reactor 의존 추가), enable() 표준 진입점 제공

## Phase 3 — support:web 신설 (웹 공통 횡단 통합)
**Goal**: 흩어진 웹 공통 요소를 support:web으로 통합하고 core가 재사용하도록 리팩터한다. (검증: 빌드 성공 + 응답/예외/트랜잭션 ID 런타임 회귀 없음)
**Requirements**: FR-07, FR-08, FR-09, NFR-02, NFR-03, NFR-04

### Wave 1 — 모듈 생성 + 응답/예외 이전
- support:web 모듈 생성(settings.gradle include, build.gradle: webflux 최소 의존)
- ApiResponse/ApiException/ErrorCode를 core:application → support:web 으로 이전

### Wave 2 — 필터/핸들러 이전
- GlobalExceptionHandler 공통화(support:web), 검증 예외 처리 포함
- RequestIdFilter 일반화 이전 + 요청/응답 로깅 필터 추가

### Wave 3 — core 리팩터 + 회귀 검증
- core:application/core:gateway가 support:web/support:logging 컴포넌트 사용하도록 임포트 교체
- ./gradlew build + 런타임 회귀 검증(응답 봉투·404·트랜잭션 ID)

## Wave Model 요약
| Phase | Waves | 핵심 산출물 |
|-------|-------|------------|
| Phase 1 | 3 | util 확장·상수, Outcome/Page 모델, 재시도/마스킹 |
| Phase 2 | 2 | 프로파일별 JSON 로깅, 마스킹 컨버터, ReactorMdc 이전 |
| Phase 3 | 3 | support:web 신설, 웹 공통 이전, core 리팩터·회귀검증 |
