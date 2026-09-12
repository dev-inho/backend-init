package cc.midolog.util

/**
 * 공통 기본값 및 포맷 상수들을 정의하는 유틸리티 객체.
 *
 * 페이징 모델([cc.midolog.util.paging.PageRequest])에서 기본 페이지 크기([DEFAULT_PAGE_SIZE] = 20)와
 * 최대 크기([MAX_PAGE_SIZE] = 100)를 제한하기 위해 참조한다.
 * 날짜 포맷 상수는 현재 참조하는 코드는 없다(docs/DEAD_CODE_CANDIDATES.md #8).
 */
object UtilDefaults {

    const val DEFAULT_PAGE_SIZE: Int = 20

    const val MAX_PAGE_SIZE: Int = 100

    const val DEFAULT_DATE_FORMAT: String = "yyyy-MM-dd"

    const val DEFAULT_DATETIME_FORMAT: String = "yyyy-MM-dd'T'HH:mm:ss"
}
