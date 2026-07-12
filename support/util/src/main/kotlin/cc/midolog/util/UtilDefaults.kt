package cc.midolog.util

/**
 * 공통 기본값 및 포맷 상수들을 정의하는 유틸리티 객체.
 *
 * 페이징·날짜 포매팅 등 여러 모듈에서 공유하는 기본값을 중앙 집중식으로 관리한다.
 * 페이징 모델 및 날짜 처리 로직에서 참조된다.
 */
object UtilDefaults {

    /** 페이징 기본 페이지 크기 */
    const val DEFAULT_PAGE_SIZE: Int = 20

    /** 페이징 최대 페이지 크기 */
    const val MAX_PAGE_SIZE: Int = 100

    /** 날짜 포맷 패턴 (YYYY-MM-DD) */
    const val DEFAULT_DATE_FORMAT: String = "yyyy-MM-dd"

    /** 날짜시간 포맷 패턴 (YYYY-MM-DD'T'HH:mm:ss) */
    const val DEFAULT_DATETIME_FORMAT: String = "yyyy-MM-dd'T'HH:mm:ss"
}
