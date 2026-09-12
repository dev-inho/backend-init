package cc.midolog.web.exception

/**
 * 비즈니스 및 API 계층에서 발생하는 표준 런타임 예외.
 *
 * 지정된 [ErrorCode]를 기반으로 [cc.midolog.web.handler.GlobalExceptionHandler]가
 * HTTP 응답 상태 코드와 표준 에러 응답([cc.midolog.web.response.ApiResponse])을 결정한다.
 */
class ApiException(
    val errorCode: ErrorCode,
    val detail: String? = null,
) : RuntimeException(detail ?: errorCode.defaultMessage) {
    companion object {
        /**
         * [ErrorCode.NOT_FOUND](HTTP 404)를 가진 [ApiException]을 생성한다.
         *
         * [cc.midolog.web.handler.GlobalExceptionHandler]에 의해 404 상태 코드와
         * `"NOT_FOUND"` 에러 코드를 담은 [cc.midolog.web.response.ApiResponse.error]로 변환된다.
         */
        fun notFound(detail: String? = null) = ApiException(ErrorCode.NOT_FOUND, detail)

        /**
         * [ErrorCode.INVALID_INPUT](HTTP 400)을 가진 [ApiException]을 생성한다.
         *
         * [cc.midolog.web.handler.GlobalExceptionHandler]에 의해 400 상태 코드와
         * `"INVALID_INPUT"` 에러 코드를 담은 [cc.midolog.web.response.ApiResponse.error]로 변환된다.
         * 현재 비즈니스 코드 내 소비자는 없으나(docs/DEAD_CODE_CANDIDATES.md #6), 파라미터 유효성 검증용 팩토리로 유효하다.
         */
        fun invalidInput(detail: String? = null) = ApiException(ErrorCode.INVALID_INPUT, detail)
    }
}
