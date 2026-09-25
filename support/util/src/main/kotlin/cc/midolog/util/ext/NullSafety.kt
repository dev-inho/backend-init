package cc.midolog.util.ext

/**
 * null 안전성 확장 함수 모음.
 *
 * 순수 Kotlin stdlib 기반, 외부 의존성 없음.
 */

/**
 * null이 아니면 non-null 값을 반환하고, null이면 필드명을 포함한 [IllegalArgumentException]을 던진다.
 *
 * `Validation.requireNotNull`, `orThrow`와 함께 null 검증 후 예외를 던지는 3중복 구현이다(docs/DEAD_CODE_CANDIDATES.md #10).
 * `Validation.requireNotNull`과 달리 수신 객체(`T?`)에 대한 확장 함수 형태로 동작하여, 원본의 nullable 타입을 non-null 타입(`T`)으로 좁힌다.
 * null인 경우 `"$name must not be null"` 메시지로 예외를 던진다.
 */
fun <T> T?.requireField(name: String): T = this ?: throw IllegalArgumentException("$name must not be null")
