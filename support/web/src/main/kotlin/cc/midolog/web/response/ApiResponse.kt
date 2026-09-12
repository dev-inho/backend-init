package cc.midolog.web.response

/**
 * 모든 HTTP API 응답의 통일된 규약을 정의하는 불변 봉투(Envelope) 데이터 클래스.
 *
 * 성공 여부([success]), 비즈니스 응답 코드([code]), 사용자 안내 메시지([message]), 실제 결과 페이로드([data])로 구성되며,
 * 클라이언트가 일관된 구조로 응답을 역직렬화하고 예외 상황을 처리할 수 있도록 돕는다.
 */
data class ApiResponse<T>(
    val success: Boolean,
    val code: String,
    val message: String,
    val data: T? = null,
) {
    companion object {
        /**
         * 성공 응답 봉투를 생성한다.
         *
         * [success]는 `true`, [code]는 `"OK"`로 고정되며 본문 데이터([data])를 담는다. 성공 응답이므로 에러 정보는 포함되지 않는다.
         */
        fun <T> ok(data: T? = null, message: String = "OK"): ApiResponse<T> =
            ApiResponse(success = true, code = "OK", message = message, data = data)

        /**
         * 실패 응답 봉투를 생성한다.
         *
         * [success]는 `false`로 설정되고 실패 사유를 나타내는 에러 코드([code])와 안내 메시지([message])를 담는다.
         * 실패 응답 규약에 따라 본문 데이터([data])는 항상 null로 고정된다.
         */
        fun <T> error(code: String, message: String): ApiResponse<T> =
            ApiResponse(success = false, code = code, message = message, data = null)
    }
}
