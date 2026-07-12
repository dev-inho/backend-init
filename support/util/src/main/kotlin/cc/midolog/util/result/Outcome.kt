package cc.midolog.util.result

/**
 * 성공 또는 실패 결과를 표현하는 타입.
 *
 * 성공 시 Success<S>로, 실패 시 Failure<F>로 표현된다.
 * 제네릭 공변성(covariance)을 활용하여 안전한 타입 계층을 제공한다.
 *
 * @param S 성공 값의 타입 (공변)
 * @param F 실패 값의 타입 (공변)
 */
sealed class Outcome<out S, out F> {

    /**
     * 성공 결과를 나타내는 데이터 클래스.
     *
     * @param value 성공한 값
     */
    data class Success<S>(val value: S) : Outcome<S, Nothing>()

    /**
     * 실패 결과를 나타내는 데이터 클래스.
     *
     * @param error 실패 원인
     */
    data class Failure<F>(val error: F) : Outcome<Nothing, F>()
}

/**
 * 성공 결과를 생성하는 팩토리 함수.
 *
 * @param value 성공한 값
 * @return Success로 래핑된 Outcome
 */
fun <S> success(value: S): Outcome<S, Nothing> = Outcome.Success(value)

/**
 * 실패 결과를 생성하는 팩토리 함수.
 *
 * @param error 실패 원인
 * @return Failure로 래핑된 Outcome
 */
fun <F> failure(error: F): Outcome<Nothing, F> = Outcome.Failure(error)

/**
 * 성공 값을 변환한다. 실패인 경우 그대로 전달된다.
 *
 * @param transform 성공 값을 변환하는 함수
 * @return 변환된 성공 값 또는 원래의 실패
 */
fun <S, T, F> Outcome<S, F>.map(transform: (S) -> T): Outcome<T, F> = when (this) {
    is Outcome.Success -> success(transform(this.value))
    is Outcome.Failure -> failure(this.error)
}

/**
 * 실패 값을 변환한다. 성공인 경우 그대로 전달된다.
 *
 * @param transform 실패 값을 변환하는 함수
 * @return 원래의 성공 또는 변환된 실패
 */
fun <S, F, G> Outcome<S, F>.mapFailure(transform: (F) -> G): Outcome<S, G> = when (this) {
    is Outcome.Success -> success(this.value)
    is Outcome.Failure -> failure(transform(this.error))
}

/**
 * 성공 또는 실패에 따라 다른 함수를 적용하여 최종 값을 생성한다.
 *
 * @param onSuccess 성공한 경우 적용할 함수
 * @param onFailure 실패한 경우 적용할 함수
 * @return 함수 적용 결과
 */
fun <S, F, R> Outcome<S, F>.fold(onSuccess: (S) -> R, onFailure: (F) -> R): R = when (this) {
    is Outcome.Success -> onSuccess(this.value)
    is Outcome.Failure -> onFailure(this.error)
}

/**
 * 성공 값을 반환하거나, 실패인 경우 null을 반환한다.
 *
 * @return 성공 값 또는 null
 */
fun <S, F> Outcome<S, F>.getOrNull(): S? = when (this) {
    is Outcome.Success -> this.value
    is Outcome.Failure -> null
}

/**
 * 실패 값을 반환하거나, 성공인 경우 null을 반환한다.
 *
 * @return 실패 값 또는 null
 */
fun <S, F> Outcome<S, F>.failureOrNull(): F? = when (this) {
    is Outcome.Success -> null
    is Outcome.Failure -> this.error
}

/**
 * 이 결과가 성공인지 여부를 반환한다.
 *
 * @return 성공이면 true, 실패이면 false
 */
fun <S, F> Outcome<S, F>.isSuccess(): Boolean = this is Outcome.Success

/**
 * 이 결과가 실패인지 여부를 반환한다.
 *
 * @return 실패이면 true, 성공이면 false
 */
fun <S, F> Outcome<S, F>.isFailure(): Boolean = this is Outcome.Failure
