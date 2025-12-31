package org.signal.core.util

/**
 * A Result that allows for generic definitions of success/failure values.
 */
sealed class Result<out S, out F> {

  data class Failure<out F>(val failure: F) : Result<Nothing, F>()
  data class Success<out S>(val success: S) : Result<S, Nothing>()

  companion object {
    @JvmStatic
    fun <S> success(value: S) = Success(value)

    @JvmStatic
    fun <F> failure(value: F) = Failure(value)
  }

  /**
   * Maps an Result<S, F> to an Result<T, F>. Failure values will pass through, while
   * right values will be operated on by the parameter.
   */
  fun <T> map(onSuccess: (S) -> T): Result<T, F> {
    return when (this) {
      is Failure -> this
      is Success -> success(onSuccess(success))
    }
  }

  /**
   * Allows the caller to operate on the Result such that the correct function is applied
   * to the value it contains.
   */
  fun <T> either(
    onSuccess: (S) -> T,
    onFailure: (F) -> T
  ): T {
    return when (this) {
      is Success -> onSuccess(success)
      is Failure -> onFailure(failure)
    }
  }
}

/**
 * Maps an Result<L, R> to an Result<L, T>. Failure values will pass through, while
 * right values will be operated on by the parameter.
 *
 * Note this is an extension method in order to make the generics happy.
 */
fun <T, S, F> Result<S, F>.flatMap(onSuccess: (S) -> Result<T, F>): Result<T, F> {
  return when (this) {
    is Result.Success -> onSuccess(success)
    is Result.Failure -> this
  }
}

/**
 * Try is a specialization of Result where the Failure is fixed to Throwable.
 */
typealias Try<S> = Result<S, Throwable>
