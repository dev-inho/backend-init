package cc.midolog.util

import java.time.Clock
import java.time.Instant

/**
 * 현재 시각 조회를 추상화하는 인터페이스.
 *
 * 비즈니스 로직이 시스템 시계에 직접 결합되지 않도록 격리하여, 단위 테스트 및 통합 테스트 시
 * 고정 시계(`Clock.fixed`)를 주입해 시간 의존 로직을 결정론적으로 검증할 수 있도록 한다.
 * 현재 프로젝트 내 외부 모듈 소비자는 없으나(docs/DEAD_CODE_CANDIDATES.md #11),
 * ExistingUtilCompatibilityTest의 하위 호환성 검증 대상이다.
 */
interface TimeProvider {
    /** 현재 시각을 [Instant]로 반환한다. */
    fun now(): Instant
}

/**
 * [Clock.systemUTC]를 기본으로 사용하는 [TimeProvider]의 프로덕션 구현체.
 *
 * 테스트 등 필요에 따라 생성자를 통해 임의의 [Clock]을 주입받아 동작할 수 있다.
 */
class SystemTimeProvider(private val clock: Clock = Clock.systemUTC()) : TimeProvider {
    override fun now(): Instant = clock.instant()
}
