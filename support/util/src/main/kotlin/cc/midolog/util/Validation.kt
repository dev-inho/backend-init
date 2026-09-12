package cc.midolog.util

/**
 * 파라미터 및 상태값의 유효성을 검증하는 헬퍼 객체.
 *
 * 검증 실패 시 지정된 필드명([name])을 포함해 [IllegalArgumentException]을 던져 조기에 오류를 감지(fail-fast)하도록 돕는다.
 */
object Validation {

    /**
     * 문자열이 null이거나 공백인지 검증하고, 유효한 경우 원본 문자열을 반환한다.
     *
     * 빈 문자열이거나 공백만으로 구성된 경우 `"$name must not be blank"` 메시지와 함께 예외를 던진다.
     */
    fun requireNotBlank(value: String?, name: String): String {
        require(!value.isNullOrBlank()) { "$name must not be blank" }
        return value
    }

    /**
     * 값이 null이 아닌지 검증하고, 유효한 경우 non-null 값을 반환한다.
     *
     * null인 경우 `"$name must not be null"` 메시지와 함께 예외를 던진다.
     * `cc.midolog.util.ext.requireField`, [orThrow]와 함께 null 검증 목적의 3중복 구현이다(docs/DEAD_CODE_CANDIDATES.md #10).
     */
    fun <T> requireNotNull(value: T?, name: String): T {
        require(value != null) { "$name must not be null" }
        return value
    }

    /**
     * 정수 값이 0보다 큰 양수인지 검증하고, 유효한 경우 값을 반환한다.
     *
     * 0 이하이면 `"$name must be positive"` 메시지와 함께 예외를 던진다.
     */
    fun requirePositive(value: Long, name: String): Long {
        require(value > 0) { "$name must be positive" }
        return value
    }
}

/**
 * 수신 객체가 null이면 [message] 람다가 생성한 메시지와 함께 [IllegalArgumentException]을 던지고, null이 아니면 non-null 값을 반환한다.
 *
 * `Validation.requireNotNull`, `cc.midolog.util.ext.requireField`와 함께 null 검증 목적의 3중복 구현이다(docs/DEAD_CODE_CANDIDATES.md #10).
 */
fun <T> T?.orThrow(message: () -> String): T = this ?: throw IllegalArgumentException(message())
