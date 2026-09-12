package cc.midolog.logging

import org.reactivestreams.Subscription
import org.slf4j.MDC
import reactor.core.CoreSubscriber
import reactor.core.publisher.Hooks
import reactor.core.publisher.Operators
import reactor.util.context.Context

/**
 * Reactor [Context]의 요청 식별자(`X-Request-Id`)를 각 리액티브 신호 처리 시점에 SLF4J MDC로 자동 복사하는 브리지 객체.
 *
 * ThreadLocal 기반의 MDC는 Reactor 연산자 체인에서 스레드가 전환될 때 유실되므로, 리액터 연산자 리프팅을 통해
 * 신호 실행 직전에 Context 값을 MDC에 동기화한다. 현재 프로젝트에서는 게이트웨이(`core:gateway`의 `GatewayApplication`)
 * 부트스트랩 시점에만 [enable]을 호출해 전역 훅으로 활성화한다.
 */
object ReactorMdc {
    /** MDC 및 Reactor Context에서 요청 식별자를 공유하기 위한 표준 키([LoggingMdc.REQUEST_ID]). */
    const val KEY = LoggingMdc.REQUEST_ID
    private const val HOOK = "cc.midolog.logging.ReactorMdc"

    /**
     * Reactor 전역 훅(`Hooks.onEachOperator`)에 [MdcLifter]를 등록하여 모든 리액티브 신호에서 MDC 동기화가 이루어지도록 활성화한다.
     *
     * 애플리케이션 부트스트랩 시점에 단 한 번 호출해야 한다.
     */
    fun enable() {
        Hooks.onEachOperator(HOOK, Operators.lift { _, sub -> MdcLifter(sub) })
    }
}

/**
 * Reactor 연산자 신호 실행 직전에 Context의 요청 식별자를 MDC에 임시 설정하고 신호 완료 후 원복하는 [CoreSubscriber] 데코레이터.
 *
 * `onNext`, `onError`, `onComplete` 신호마다 현재 Context의 [ReactorMdc.KEY]를 읽어 MDC에 put하고,
 * 실행이 끝나면 `finally` 블록에서 이전 MDC 상태로 복원하여 스레드 풀 오염 및 MDC 누수를 방지한다.
 */
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
