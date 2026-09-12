package cc.midolog.util.paging

import cc.midolog.util.UtilDefaults
import kotlin.math.ceil

/**
 * 정렬 방향(오름차순 ASC, 내림차순 DESC)을 정의하는 열거형.
 */
enum class Direction {
    ASC, DESC
}

/**
 * 단일 속성에 대한 정렬 조건을 표현하는 데이터 클래스.
 *
 * 정렬할 대상 속성명([property])과 정렬 방향([direction], 기본 ASC)을 지정한다.
 * [property]가 공백이거나 빈 문자열인 경우 예외를 던진다.
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
 * 페이지 번호, 크기, 정렬 조건을 캡슐화한 오프셋 기반 페이징 요청 데이터 클래스.
 *
 * [page]는 0부터 시작하는 페이지 번호이며 음수일 수 없다.
 * [size]는 페이지당 항목 수로 1부터 [UtilDefaults.MAX_PAGE_SIZE] 사이여야 하며, 위반 시 예외를 던진다.
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
     * 페이지 번호와 페이지 크기를 곱해 0 기반의 조회 시작 오프셋(offset)을 계산한다.
     */
    val offset: Long
        get() = page.toLong() * size
}

/**
 * 페이징된 응답 데이터와 메타데이터(전체 건수, 페이지 크기, 현재 페이지)를 표현하는 불변 데이터 클래스.
 *
 * 현재 프로젝트 내 외부 모듈 소비자는 없으나(docs/DEAD_CODE_CANDIDATES.md #11), 하위 호환성 검증 대상이다.
 */
data class Page<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long
) {
    /**
     * 전체 데이터 건수([totalElements])와 페이지 크기([size])를 바탕으로 올림 계산한 전체 페이지 수를 산출한다.
     */
    val totalPages: Int
        get() = if (size > 0) ceil(totalElements.toDouble() / size).toInt() else 0

    val hasNext: Boolean
        get() = page + 1 < totalPages

    val hasPrevious: Boolean
        get() = page > 0

    val isFirst: Boolean
        get() = page == 0

    val isLast: Boolean
        get() = page + 1 >= totalPages
}
