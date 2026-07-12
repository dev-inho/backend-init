package cc.midolog.util.ext

/**
 * 컬렉션 확장 함수 모음 (null-safety + 청킹).
 *
 * 순수 Kotlin stdlib 기반, 외부 의존성 없음.
 */

/**
 * nullable Iterable을 빈 반복 가능 객체로 변환한다.
 * null이면 emptyList()를 반환하므로 null-coalescing 루프가 안전하다.
 *
 * 예제:
 * val items: Iterable<String>? = null
 * items.orEmpty().forEach { ... } // NPE 없음
 */
fun <T> Iterable<T>?.orEmpty(): Iterable<T> = this ?: emptyList()

/**
 * nullable Collection을 빈 컬렉션으로 변환한다.
 * null이면 emptyList()를 반환한다.
 */
fun <T> Collection<T>?.orEmpty(): Collection<T> = this ?: emptyList()

/**
 * nullable List를 빈 리스트로 변환한다.
 * null이면 emptyList()를 반환한다.
 */
fun <T> List<T>?.orEmpty(): List<T> = this ?: emptyList()

/**
 * 인접한 요소들의 key가 변할 때마다 새로운 청크로 분리한다.
 *
 * 동작:
 * - 같은 key를 가진 연속 요소는 하나의 청크로 묶인다.
 * - key가 변하면 새로운 청크가 시작된다.
 * - 빈 리스트를 입력하면 빈 리스트를 반환한다.
 *
 * 예제:
 * val data = listOf(1, 1, 2, 2, 2, 1, 3)
 * data.chunkedBy { it } == listOf(listOf(1, 1), listOf(2, 2, 2), listOf(1), listOf(3))
 *
 * 매개변수:
 * - keySelector: 각 요소에서 비교용 key를 추출하는 람다
 *
 * 반환값:
 * 청크의 리스트. 각 청크는 같은 key를 가진 인접 요소들.
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
