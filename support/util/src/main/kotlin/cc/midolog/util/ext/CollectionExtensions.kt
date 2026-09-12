package cc.midolog.util.ext

/**
 * 컬렉션 null 안전성 및 연속 요소 청킹을 위한 확장 함수 모음.
 *
 * 순수 Kotlin stdlib 기반으로 작성되었다.
 */

/**
 * nullable [Iterable]이 null이면 빈 리스트(`emptyList()`)를 반환하고, null이 아니면 자기 자신을 반환한다.
 *
 * Kotlin 표준 라이브러리(`kotlin.collections.orEmpty`)와 동일한 시그니처 및 동작의 중복 구현이나(docs/DEAD_CODE_CANDIDATES.md #9),
 * 하위 호환성을 위해 유지된다.
 */
fun <T> Iterable<T>?.orEmpty(): Iterable<T> = this ?: emptyList()

/**
 * nullable [Collection]이 null이면 빈 리스트(`emptyList()`)를 반환하고, null이 아니면 자기 자신을 반환한다.
 *
 * Kotlin 표준 라이브러리(`kotlin.collections.orEmpty`)와 동일한 시그니처 및 동작의 중복 구현이다(docs/DEAD_CODE_CANDIDATES.md #9).
 */
fun <T> Collection<T>?.orEmpty(): Collection<T> = this ?: emptyList()

/**
 * nullable [List]가 null이면 빈 리스트(`emptyList()`)를 반환하고, null이 아니면 자기 자신을 반환한다.
 *
 * Kotlin 표준 라이브러리(`kotlin.collections.orEmpty`)와 동일한 시그니처 및 동작의 중복 구현이다(docs/DEAD_CODE_CANDIDATES.md #9).
 */
fun <T> List<T>?.orEmpty(): List<T> = this ?: emptyList()

/**
 * 키 추출 함수([keySelector])의 반환값이 동일한 인접 요소들을 묶어 청크 리스트로 분할한다.
 *
 * 같은 키를 가진 연속된 요소는 하나의 하위 리스트로 묶이며, 키가 달라지는 지점에서 새로운 청크가 시작된다.
 * 리스트 전체의 정렬 여부와 무관하게 인접성만을 기준으로 그룹화하며, 빈 컬렉션인 경우 빈 리스트를 반환한다.
 * 현재 프로젝트 내 실제 소비자는 없다(docs/DEAD_CODE_CANDIDATES.md #17).
 */
fun <T, K> Iterable<T>.chunkedBy(keySelector: (T) -> K): List<List<T>> {
    val result = mutableListOf<List<T>>()
    var currentChunk = mutableListOf<T>()
    var currentKey: K? = null
    var isFirstElement = true

    for (element in this) {
        val key = keySelector(element)
        if (isFirstElement) {
            currentKey = key
            currentChunk.add(element)
            isFirstElement = false
        } else if (key == currentKey) {
            currentChunk.add(element)
        } else {
            // Key가 변했으므로 현재 청크를 저장하고 새로운 청크 시작
            result.add(currentChunk.toList())
            currentChunk = mutableListOf(element)
            currentKey = key
        }
    }

    // 마지막 청크 저장
    if (currentChunk.isNotEmpty()) {
        result.add(currentChunk.toList())
    }

    return result
}
