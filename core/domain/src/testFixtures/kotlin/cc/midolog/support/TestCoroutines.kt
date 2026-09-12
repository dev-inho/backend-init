package cc.midolog.support

import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

fun <T> runTestBlocking(block: suspend () -> T): T {
    val latch = CountDownLatch(1)
    val result = AtomicReference<Result<T>>()
    block.startCoroutine(object : Continuation<T> {
        override val context: CoroutineContext = EmptyCoroutineContext
        override fun resumeWith(res: Result<T>) {
            result.set(res)
            latch.countDown()
        }
    })
    latch.await()
    return result.get().getOrThrow()
}
