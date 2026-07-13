package cc.midolog.logging

import org.reactivestreams.Subscription
import org.slf4j.MDC
import reactor.core.CoreSubscriber
import reactor.core.publisher.Hooks
import reactor.core.publisher.Operators
import reactor.util.context.Context

/**
 * reactor Context의 X-Request-Id를 각 신호 처리 시점에 MDC로 복사한다.
 * thread-local MDC와 reactor의 스레드 전환 불일치를 보완해 로그에 트랜잭션 ID가 일관되게 찍히도록 한다.
 */
object ReactorMdc {
    const val KEY = LoggingMdc.REQUEST_ID
    private const val HOOK = "cc.midolog.logging.ReactorMdc"

    /** reactor Hooks에 MDC 동기화 lifter를 등록한다. 애플리케이션 부트스트랩에서 한 번 호출한다. */
    fun enable() {
        Hooks.onEachOperator(HOOK, Operators.lift { _, sub -> MdcLifter(sub) })
    }
}

private class MdcLifter<T : Any>(private val delegate: CoreSubscriber<T>) : CoreSubscriber<T> {
    override fun onSubscribe(s: Subscription) = delegate.onSubscribe(s)
    override fun onNext(t: T) = withMdc { delegate.onNext(t) }
    override fun onError(t: Throwable) = withMdc { delegate.onError(t) }
    override fun onComplete() = withMdc { delegate.onComplete() }
    override fun currentContext(): Context = delegate.currentContext()

    private inline fun withMdc(block: () -> Unit) {
        val id = delegate.currentContext().getOrDefault(ReactorMdc.KEY, null as String?)
        val previous = MDC.get(ReactorMdc.KEY)
        if (id != null) MDC.put(ReactorMdc.KEY, id) else MDC.remove(ReactorMdc.KEY)
        try {
            block()
        } finally {
            if (previous != null) MDC.put(ReactorMdc.KEY, previous) else MDC.remove(ReactorMdc.KEY)
        }
    }
}
