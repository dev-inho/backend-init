package cc.midolog.util.ext

/**
 * 문자열 정제 및 길이 축약을 위한 확장 함수 모음.
 *
 * 현재 프로젝트 내 외부 소비자는 없으나(docs/DEAD_CODE_CANDIDATES.md #18), 유틸리티 계층으로 보존된다.
 */

/**
 * 문자열의 앞뒤 공백을 제거하고, 공백 제거 후 빈 문자열이거나 원본이 null이면 null을 반환한다.
 */
fun String?.trimToNull(): String? {
    return this?.trim()?.takeIf { it.isNotBlank() }
}

/**
 * 문자열 길이가 최대 허용 길이([maxLength])를 초과할 경우 말줄임표([ellipsis], 기본값 `...`)를 붙여 전체 길이가 정확히 [maxLength]가 되도록 잘라낸다.
 *
 * [maxLength]가 [ellipsis] 길이보다 작으면 [IllegalArgumentException]을 던지며, 원본 길이가 [maxLength] 이하면 원본을 그대로 반환한다.
 */
fun String.abbreviate(maxLength: Int, ellipsis: String = "..."): String {
    if (maxLength < ellipsis.length) {
        throw IllegalArgumentException("maxLength must be at least as long as ellipsis")
    }

    if (this.length <= maxLength) {
        return this
    }

    val cutLength = maxLength - ellipsis.length
    return this.substring(0, cutLength) + ellipsis
}
