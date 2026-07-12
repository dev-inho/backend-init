package cc.midolog.util.ext

/**
 * 문자열을 trim하고, 결과가 비어있으면 null을 반환한다.
 * null 문자열에 대해 호출하면 null을 반환한다.
 *
 * @return trim된 문자열 또는 blank인 경우 null
 */
fun String?.trimToNull(): String? {
    return this?.trim()?.takeIf { it.isNotBlank() }
}

/**
 * 문자열을 지정된 최대 길이로 약칭한다.
 * 문자열 길이가 maxLength 이하면 그대로 반환한다.
 * maxLength를 초과하면 ellipsis를 붙여 총 길이가 maxLength가 되도록 자른다.
 *
 * @param maxLength 최대 길이. ellipsis 길이보다 작으면 예외 발생
 * @param ellipsis 생략 기호 (기본값: "...")
 * @return 약칭된 또는 원본 문자열
 * @throws IllegalArgumentException maxLength가 ellipsis 길이보다 작은 경우
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
