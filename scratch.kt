import org.springframework.http.HttpStatus
import java.util.concurrent.TimeoutException

fun gatewayError(error: Throwable, method: String): HttpStatus {
    val unwrapped = error
    return if (unwrapped is TimeoutException) {
        HttpStatus.GATEWAY_TIMEOUT
    } else if (unwrapped is RetryableStatusCodeException && (method == "GET" || method == "HEAD" || method == "OPTIONS")) {
        HttpStatus.valueOf(unwrapped.statusCode)
    } else {
        HttpStatus.BAD_GATEWAY
    }
}
class RetryableStatusCodeException(val statusCode: Int) : RuntimeException()
