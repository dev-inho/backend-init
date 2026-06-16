package cc.midolog.common.response

/** 표준 API 응답 봉투. */
data class ApiResponse<T>(
    val success: Boolean,
    val code: String,
    val message: String,
    val data: T? = null,
) {
    companion object {
        fun <T> ok(data: T? = null, message: String = "OK"): ApiResponse<T> =
            ApiResponse(success = true, code = "OK", message = message, data = data)

        fun <T> error(code: String, message: String): ApiResponse<T> =
            ApiResponse(success = false, code = code, message = message, data = null)
    }
}
