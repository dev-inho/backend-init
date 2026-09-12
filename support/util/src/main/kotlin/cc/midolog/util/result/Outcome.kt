package cc.midolog.util.result

/**
 * 성공([Outcome.Success]) 또는 실패([Outcome.Failure]) 결과를 표현하는 공변(covariant) 봉투 타입.
 *
 * 예외를 던지는 대신 반환값으로 작업의 성공/실패를 명시적으로 다루기 위한 Result 패턴 구현체다.
 * 성공 값 [S]와 실패 원인 [F]에 대한 공변성을 제공해 유연한 하위 타입 대입이 가능하다.
 * 현재 프로젝트 내 외부 소비자는 없으나(docs/DEAD_CODE_CANDIDATES.md #11), ExistingUtilCompatibilityTest의
 * 하위 호환성 검증 대상이다.
 */
sealed class Outcome<out S, out F> {

    /** 성공 값을 담는 불변 데이터 클래스. */
    data class Success<S>(val value: S) : Outcome<S, Nothing>()

    /** 실패 원인을 담는 불변 데이터 클래스. */
    data class Failure<F>(val error: F) : Outcome<Nothing, F>()
}

/** 성공 값을 [Outcome.Success]로 감싸 반환한다. */
fun <S> success(value: S): Outcome<S, Nothing> = Outcome.Success(value)

/** 실패 원인을 [Outcome.Failure]로 감싸 반환한다. */
fun <F> failure(error: F): Outcome<Nothing, F> = Outcome.Failure(error)

/**
 * 성공 결과인 경우 변환 함수 [transform]을 적용한 새 성공 결과를 반환하고, 실패인 경우 원래 실패를 그대로 전달한다.
 */
fun <S, T, F> Outcome<S, F>.map(transform: (S) -> T): Outcome<T, F> = when (this) {
    is Outcome.Success -> success(transform(this.value))
    is Outcome.Failure -> failure(this.error)
}

/**
 * 실패 결과인 경우 변환 함수 [transform]을 적용한 새 실패 결과를 반환하고, 성공인 경우 원래 성공을 그대로 전달한다.
 */
fun <S, F, G> Outcome<S, F>.mapFailure(transform: (F) -> G): Outcome<S, G> = when (this) {
    is Outcome.Success -> success(this.value)
    is Outcome.Failure -> failure(transform(this.error))
}

/**
 * 성공 또는 실패 결과에 따라 각각 [onSuccess] 또는 [onFailure] 함수를 적용해 단일 최종 값을 산출한다.
 */
fun <S, F, R> Outcome<S, F>.fold(onSuccess: (S) -> R, onFailure: (F) -> R): R = when (this) {
    is Outcome.Success -> onSuccess(this.value)
    is Outcome.Failure -> onFailure(this.error)
}

/** 성공 결과이면 성공 값을 반환하고, 실패 결과이면 null을 반환한다. */
fun <S, F> Outcome<S, F>.getOrNull(): S? = when (this) {
    is Outcome.Success -> this.value
    is Outcome.Failure -> null
}

/** 실패 결과이면 실패 원인을 반환하고, 성공 결과이면 null을 반환한다. */
fun <S, F> Outcome<S, F>.failureOrNull(): F? = when (this) {
    is Outcome.Success -> null
    is Outcome.Failure -> this.error
}

fun <S, F> Outcome<S, F>.isSuccess(): Boolean = this is Outcome.Success

fun <S, F> Outcome<S, F>.isFailure(): Boolean = this is Outcome.Failure
