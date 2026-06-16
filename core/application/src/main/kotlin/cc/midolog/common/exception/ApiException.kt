package cc.midolog.common.exception

/** 비즈니스/API 예외. ErrorCode로 HTTP 상태와 메시지를 결정한다. */
class ApiException(
    val errorCode: ErrorCode,
    val detail: String? = null,
) : RuntimeException(detail ?: errorCode.defaultMessage) {
    companion object {
        /** NOT_FOUND 에러 코드로 예외를 생성한다. */
        fun notFound(detail: String? = null) = ApiException(ErrorCode.NOT_FOUND, detail)

        /** INVALID_INPUT 에러 코드로 예외를 생성한다. */
        fun invalidInput(detail: String? = null) = ApiException(ErrorCode.INVALID_INPUT, detail)
    }
}
