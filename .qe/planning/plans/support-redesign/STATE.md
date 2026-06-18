# State — support-redesign

- **Active Phase**: Phase 1 — support:util 재설계
- **Plan Slug**: support-redesign
- **Updated**: 2026-06-16

## Phase Progress

> 자동 생성 (ledger.mjs render-state) — 직접 수정 금지

### Phase 1 — support:util 재설계 (순수 Kotlin 기반기) (ACTIVE)
- [ ] G001 [Wave 1 — 확장/상수 기반] Kotlin 확장 함수(컬렉션: orEmpty/chunkedBy, 문자열: trimToNull/abbreviate, null-safety: ifNull/requireField)
- [ ] G002 [Wave 1 — 확장/상수 기반] 공통 상수/열거(공용 포맷, 기본 페이지 크기 등) 정의
- [ ] G003 [Wave 1 — 확장/상수 기반] 기존 Validation/TimeProvider/IdGenerator 정리·일관화
- [ ] G004 [Wave 2 — 결과/페이징 모델] 결과 타입: Result/Either 계열(Outcome<S, F>) + 매핑 함수
- [ ] G005 [Wave 2 — 결과/페이징 모델] 페이징 모델: PageRequest(page/size/sort), Page<T>(content/total/page meta), Sort/Direction
- [ ] G006 [Wave 3 — 재시도/마스킹] 재시도/백오프 유틸(시도 횟수·지연·예외 필터) — 순수/코루틴 분리 검토
- [ ] G007 [Wave 3 — 재시도/마스킹] PII 마스킹 유틸(email/phone/card 부분 마스킹)

### Phase 2 — support:logging 재설계 (구조적 로깅 + 리액티브 인지)
- [ ] G008 [Wave 1 — 프로파일별 구조적 로깅] logback-spring.xml 재설계: springProfile(local=콘솔 패턴 / prod=JSON)
- [ ] G009 [Wave 1 — 프로파일별 구조적 로깅] JSON 인코더 선택(logstash-logback-encoder 또는 Logback 네이티브 JSON) — spec에서 확정
- [ ] G010 [Wave 1 — 프로파일별 구조적 로깅] MDC 키 표준 상수(X-Request-Id 등) 정의
- [ ] G011 [Wave 2 — 마스킹 + MDC 브리지 이전] 민감정보 마스킹 로그 컨버터/패턴 레이아웃
- [ ] G012 [Wave 2 — 마스킹 + MDC 브리지 이전] ReactorMdc를 core:gateway → support:logging 으로 이전·일반화(reactor 의존 추가), enable() 표준 진입점 제공

### Phase 3 — support:web 신설 (웹 공통 횡단 통합)
- [ ] G013 [Wave 1 — 모듈 생성 + 응답/예외 이전] support:web 모듈 생성(settings.gradle include, build.gradle: webflux 최소 의존)
- [ ] G014 [Wave 1 — 모듈 생성 + 응답/예외 이전] ApiResponse/ApiException/ErrorCode를 core:application → support:web 으로 이전
- [ ] G015 [Wave 2 — 필터/핸들러 이전] GlobalExceptionHandler 공통화(support:web), 검증 예외 처리 포함
- [ ] G016 [Wave 2 — 필터/핸들러 이전] RequestIdFilter 일반화 이전 + 요청/응답 로깅 필터 추가
- [ ] G017 [Wave 3 — core 리팩터 + 회귀 검증] core:application/core:gateway가 support:web/support:logging 컴포넌트 사용하도록 임포트 교체
- [ ] G018 [Wave 3 — core 리팩터 + 회귀 검증] ./gradlew build + 런타임 회귀 검증(응답 봉투·404·트랜잭션 ID)
