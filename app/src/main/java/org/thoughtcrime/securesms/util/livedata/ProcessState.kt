package org.thoughtcrime.securesms.util.livedata

/**
 * Provide a general representation of a discrete process. States are idle,
 * working, success, and failure.
 */
sealed class ProcessState<T> {
  class Idle<T> : ProcessState<T>()
  class Working<T> : ProcessState<T>()
  data class Success<T>(val result: T) : ProcessState<T>()
  data class Failure<T>(val throwable: Throwable?) : ProcessState<T>()

  companion object {
    fun <T> fromResult(result: Result<T>): ProcessState<T> {
      return if (result.isSuccess) {
        Success(result.getOrThrow())
      } else {
        Failure(result.exceptionOrNull())
      }
    }
  }
}
