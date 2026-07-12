package cc.midolog.util.ext

/**
 * null 안전성 확장 함수 모음.
 *
 * 순수 Kotlin stdlib 기반, 외부 의존성 없음.
 */

/**
 * null이면 fallback() 지연 평가 결과를 반환하고, 아니면 자기 자신을 반환한다.
 *
 * 지연 평가(lazy evaluation)이므로, 값이 null이 아니면 fallback 람다는 호출되지 않는다.
 *
 * 예제:
 * val name: String? = null
 * name.ifNull { "default-name" } == "default-name"
 *
 * val name: String? = "actual"
 * name.ifNull { "default-name" } == "actual"  // fallback 호출되지 않음
 *
 * 매개변수:
 * - fallback: null일 때만 호출되는 지연 평가 람다
 *
 * 반환값:
 * 원본 값이 null이 아니면 원본, null이면 fallback() 결과
 */
fun <T> T?.ifNull(fallback: () -> T): T = this ?: fallback()

/**
 * null이 아니면 non-null 값을 반환하고, null이면 필드명을 포함한 IllegalArgumentException을 던진다.
 *
 * 검증(validation) 목적으로 필드명과 함께 명확한 에러 메시지를 제공한다.
 * Validation.requireNotNull과 달리, 확장 함수로서 null-safe 타입(T?)에서 직접 호출 가능하다.
 *
 * 예제:
 * val userId: Long? = 42
 * userId.requireField("userId") == 42L
 *
 * val userId: Long? = null
 * userId.requireField("userId")  // IllegalArgumentException: "userId must not be null" 던짐
 *
 * 매개변수:
 * - name: 필드명 (예외 메시지에 포함됨)
 *
 * 반환값:
 * non-null 값 (원본의 T? 에서 T로 타입 좁혀짐)
 *
 * 예외:
 * - IllegalArgumentException: name must not be null (예: "userId must not be null")
 */
fun <T> T?.requireField(name: String): T = this ?: throw IllegalArgumentException("$name must not be null")
