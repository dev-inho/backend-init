package cc.midolog.util.retry

import cc.midolog.util.Validation

/**
 * 재시도 정책을 정의하는 불변 데이터 클래스.
 *
 * 재시도 횟수, 초기 지연 시간, 지수 백오프 승수, 최대 지연 시간,
 * 그리고 재시도 대상 예외를 필터링하는 조건을 포함한다.
 *
 * @param maxAttempts 최대 시도 횟수 (최소 1, 기본값으로 1은 재시도 없이 1회만 실행)
 * @param initialDelayMillis 첫 재시도의 지연 시간(밀리초, 최소 0)
 * @param multiplier 지연 시간의 지수 백오프 승수 (기본 2.0 = 각 시도마다 2배씩 증가)
 * @param maxDelayMillis 최대 지연 시간의 상한(밀리초, 기본 Long.MAX_VALUE = 제한 없음)
 * @param retryOn 주어진 예외에 대해 재시도할지 결정하는 술어 (기본값: 모든 예외 재시도)
 */
data class RetryPolicy(
    val maxAttempts: Int,
    val initialDelayMillis: Long,
    val multiplier: Double = 2.0,
    val maxDelayMillis: Long = Long.MAX_VALUE,
    val retryOn: (Throwable) -> Boolean = { true }
) {
    init {
        Validation.requirePositive(maxAttempts.toLong(), "maxAttempts")
        require(initialDelayMillis >= 0) { "initialDelayMillis must not be negative" }
    }
}

/**
 * 주어진 정책에 따라 지연 시간을 계산한다.
 *
 * 지연 시간 = min(initialDelayMillis * multiplier^attempt, maxDelayMillis)
 *
 * @param attempt 0부터 시작하는 시도 번호 (0번째 시도는 첫 재시도의 지연)
 * @return 밀리초 단위의 지연 시간
 */
fun RetryPolicy.delayForAttempt(attempt: Int): Long {
    if (initialDelayMillis == 0L) return 0L
    if (attempt < 0) return 0L

    // 지수 백오프 계산
    val exponentialDelay = (initialDelayMillis * Math.pow(multiplier, attempt.toDouble())).toLong()
    return minOf(exponentialDelay, maxDelayMillis)
}

/**
 * 주어진 정책에 따라 블로킹 재시도를 수행한다.
 *
 * block()을 실행하고, 예외가 발생한 경우:
 * - retryOn이 false이면 즉시 예외를 던진다.
 * - retryOn이 true이고 시도가 남으면 지수 백오프 지연 후 재시도한다.
 * - 모든 시도가 실패하면 마지막 예외를 던진다.
 *
 * sleeper를 주입 가능하게 하여 테스트에서 실제 지연 대신 호출 추적이 가능하다.
 *
 * @param policy 재시도 정책
 * @param sleeper 지연을 수행하는 함수 (기본값: Thread.sleep)
 * @param block 실행할 블로킹 작업
 * @return block의 반환값
 * @throws Throwable block이 던진 마지막 예외
 */
fun <T> retry(
    policy: RetryPolicy,
    sleeper: (Long) -> Unit = { Thread.sleep(it) },
    block: () -> T
): T {
    var lastException: Throwable? = null

    for (attempt in 0 until policy.maxAttempts) {
        try {
            return block()
        } catch (e: Throwable) {
            lastException = e

            // 마지막 시도이거나 retryOn이 false이면 즉시 던진다
            if (attempt == policy.maxAttempts - 1 || !policy.retryOn(e)) {
                throw e
            }

            // 지수 백오프 지연
            val delayMillis = policy.delayForAttempt(attempt)
            if (delayMillis > 0) {
                sleeper(delayMillis)
            }
        }
    }

    // 도달할 수 없음 (loop는 항상 반환하거나 던짐)
    throw lastException ?: RuntimeException("No attempts made")
}
