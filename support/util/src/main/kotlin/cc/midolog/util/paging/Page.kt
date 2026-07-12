package cc.midolog.util.paging

import cc.midolog.util.UtilDefaults
import kotlin.math.ceil

/**
 * 정렬 방향을 나타내는 열거형.
 *
 * - ASC: 오름차순
 * - DESC: 내림차순
 */
enum class Direction {
    ASC, DESC
}

/**
 * 정렬 조건을 나타내는 데이터 클래스.
 *
 * @property property 정렬할 속성명 (공백 문자열 불허)
 * @property direction 정렬 방향 (기본값: ASC)
 * @throws IllegalArgumentException property가 공백이거나 빈 문자열인 경우
 */
data class Sort(
    val property: String,
    val direction: Direction = Direction.ASC
) {
    init {
        require(property.isNotBlank()) {
            "Sort property must not be blank"
        }
    }
}

/**
 * 페이지 요청 정보를 나타내는 데이터 클래스.
 *
 * @property page 페이지 번호 (0부터 시작, 음수 불허)
 * @property size 페이지 크기 (기본값: DEFAULT_PAGE_SIZE, 범위: 1..MAX_PAGE_SIZE)
 * @property sorts 정렬 조건들 (기본값: 빈 리스트)
 * @throws IllegalArgumentException page가 음수이거나 size가 범위를 벗어난 경우
 */
data class PageRequest(
    val page: Int,
    val size: Int = UtilDefaults.DEFAULT_PAGE_SIZE,
    val sorts: List<Sort> = emptyList()
) {
    init {
        require(page >= 0) {
            "Page must not be negative, got: $page"
        }
        require(size in 1..UtilDefaults.MAX_PAGE_SIZE) {
            "Page size must be between 1 and ${UtilDefaults.MAX_PAGE_SIZE}, got: $size"
        }
    }

    /**
     * 조회 시작 위치(offset)를 계산한다.
     *
     * offset = page * size
     */
    val offset: Long
        get() = page.toLong() * size
}

/**
 * 페이징된 응답 결과를 나타내는 데이터 클래스.
 *
 * @property content 현재 페이지의 콘텐츠 리스트
 * @property page 현재 페이지 번호 (0부터 시작)
 * @property size 페이지 크기
 * @property totalElements 전체 데이터 개수
 */
data class Page<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long
) {
    /**
     * 전체 페이지 수를 계산한다.
     *
     * size가 0보다 크면 ceil(totalElements / size), 아니면 0을 반환한다.
     */
    val totalPages: Int
        get() = if (size > 0) ceil(totalElements.toDouble() / size).toInt() else 0

    /**
     * 다음 페이지 존재 여부를 반환한다.
     *
     * 현재 페이지가 마지막 페이지가 아닌 경우 true
     */
    val hasNext: Boolean
        get() = page + 1 < totalPages

    /**
     * 이전 페이지 존재 여부를 반환한다.
     *
     * 현재 페이지가 0(첫 페이지)이 아닌 경우 true
     */
    val hasPrevious: Boolean
        get() = page > 0

    /**
     * 현재 페이지가 첫 페이지인지 반환한다.
     */
    val isFirst: Boolean
        get() = page == 0

    /**
     * 현재 페이지가 마지막 페이지인지 반환한다.
     */
    val isLast: Boolean
        get() = page + 1 >= totalPages
}
