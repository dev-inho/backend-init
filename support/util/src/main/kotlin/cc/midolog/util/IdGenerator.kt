package cc.midolog.util

import java.util.UUID

/**
 * 지정한 접두사와 12자리 UUID 난수를 결합하여 고유 식별자 문자열을 생성하는 유틸리티.
 *
 * 현재 프로젝트 내 외부 모듈 소비자는 없으나(docs/DEAD_CODE_CANDIDATES.md #11),
 * ExistingUtilCompatibilityTest가 출력 형식({prefix}_{12자리})을 검증하는 하위 호환성 대상이다.
 */
object IdGenerator {
    /**
     * 지정한 접두사([prefix]) 뒤에 하이픈을 제거한 UUID 앞 12자리를 붙여 `{prefix}_{12자리}` 형태의 고유 식별자를 생성한다.
     */
    fun generate(prefix: String): String =
        "${prefix}_${UUID.randomUUID().toString().replace("-", "").take(12)}"
}
