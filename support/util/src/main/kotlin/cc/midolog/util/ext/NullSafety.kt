package cc.midolog.util.ext

/**
 * null 안전성 확장 함수 모음.
 *
 * 순수 Kotlin stdlib 기반, 외부 의존성 없음.
 */

/**
 * null이면 [fallback] 지연 평가 결과를 반환하고, 아니면 자기 자신을 반환한다.
 *
 * 값이 null이 아닌 경우 [fallback] 람다는 평가되지 않는다. Kotlin 기본 엘비스 연산자(`?:`)와 기능이
 * 완전히 동일한 불필요한 래퍼 함수이나(docs/DEAD_CODE_CANDIDATES.md #16), 함수 체이닝 방식으로 기본값을 지연 주입할 때 사용된다.
 */
fun <T> T?.ifNull(fallback: () -> T): T = this ?: fallback()

/**
 * null이 아니면 non-null 값을 반환하고, null이면 필드명을 포함한 [IllegalArgumentException]을 던진다.
 *
 * `Validation.requireNotNull`, `orThrow`와 함께 null 검증 후 예외를 던지는 3중복 구현이다(docs/DEAD_CODE_CANDIDATES.md #10).
 * `Validation.requireNotNull`과 달리 수신 객체(`T?`)에 대한 확장 함수 형태로 동작하여, 원본의 nullable 타입을 non-null 타입(`T`)으로 좁힌다.
 * null인 경우 `"$name must not be null"` 메시지로 예외를 던진다.
 */
fun <T> T?.requireField(name: String): T = this ?: throw IllegalArgumentException("$name must not be null")
