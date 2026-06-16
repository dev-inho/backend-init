package cc.midolog.util

import java.time.Clock
import java.time.Instant

/** 시간 추상화 — 테스트에서 고정 시계를 주입할 수 있도록 한다. */
interface TimeProvider {
    fun now(): Instant
}

/** 시스템 시계 기반 기본 구현. */
class SystemTimeProvider(private val clock: Clock = Clock.systemUTC()) : TimeProvider {
    override fun now(): Instant = clock.instant()
}
