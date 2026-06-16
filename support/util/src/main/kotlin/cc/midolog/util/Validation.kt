package cc.midolog.util

/** 공통 검증 헬퍼. 위반 시 IllegalArgumentException을 던진다. */
object Validation {
    fun requireNotBlank(value: String?, name: String): String {
        require(!value.isNullOrBlank()) { "$name must not be blank" }
        return value
    }

    fun <T> requireNotNull(value: T?, name: String): T {
        require(value != null) { "$name must not be null" }
        return value
    }

    fun requirePositive(value: Long, name: String): Long {
        require(value > 0) { "$name must be positive" }
        return value
    }
}

/** null이면 메시지와 함께 예외를 던지고, 아니면 값을 반환한다. */
fun <T> T?.orThrow(message: () -> String): T = this ?: throw IllegalArgumentException(message())
