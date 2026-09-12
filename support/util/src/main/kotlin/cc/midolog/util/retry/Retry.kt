package cc.midolog.util.retry

import cc.midolog.util.Validation

/**
 * 일시적 장애 대응을 위한 불변 재시도 정책 데이터 클래스.
 *
 * 최대 시도 횟수([maxAttempts]), 첫 재시도 지연 시간([initialDelayMillis]), 지수 백오프 승수([multiplier]),
 * 최대 지연 상한([maxDelayMillis]), 재시도 대상 예외 필터 조건([retryOn])을 캡슐화한다.
 * [maxAttempts]는 1 이상, [initialDelayMillis]는 0 이상이어야 하며 위반 시 예외를 던진다.
 * 현재 프로젝트 내 외부 모듈 소비자는 없으나(docs/DEAD_CODE_CANDIDATES.md #11), 하위 호환성 검증 대상이다.
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
 * 지수 백오프 공식 min(initialDelayMillis * multiplier^attempt, maxDelayMillis)에 따라 회차별 지연 시간을 밀리초 단위로 계산한다.
 *
 * 0부터 시작하는 [attempt] 번호(0은 첫 재시도)를 전달받으며, [attempt]가 음수이거나 [RetryPolicy.initialDelayMillis]가 0이면 0을 반환한다.
 */
fun RetryPolicy.delayForAttempt(attempt: Int): Long {
    if (initialDelayMillis == 0L) return 0L
    if (attempt < 0) return 0L

    // 지수 백오프 계산
    val exponentialDelay = (initialDelayMillis * Math.pow(multiplier, attempt.toDouble())).toLong()
    return minOf(exponentialDelay, maxDelayMillis)
}

/**
 * 주어진 정책에 따라 블로킹 작업을 실행하고, 실패 시 지수 백오프 대기 후 재시도한다.
 *
 * 작업 실행 중 예외가 발생하면 [RetryPolicy.retryOn] 술어가 참이고 남은 시도가 있을 때만 지연 후 다시 시도하며,
 * [RetryPolicy.retryOn]이 거짓이거나 모든 시도를 소진하면 마지막 예외를 그대로 다시 던진다.
 * 테스트 격리를 위해 대기 함수([sleeper], 기본값 Thread.sleep)를 주입받을 수 있다.
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
