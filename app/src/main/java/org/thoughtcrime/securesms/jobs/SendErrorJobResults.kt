package org.thoughtcrime.securesms.jobs

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.signal.core.util.logging.Log
import org.signal.libsignal.net.ChallengeOption
import org.signal.libsignal.net.RequestResult
import org.signal.libsignal.protocol.InvalidSessionException
import org.signal.libsignal.protocol.NoSessionException
import org.signal.network.api.CdnApi
import org.signal.network.service.IndividualMessageService
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.ratelimit.ProofRequiredExceptionHandler

fun IndividualMessageService.SendError.SessionAttemptsExhausted.toJobResult(tag: String, defaultBackoff: Long): Job.Result {
  Log.w(tag, "Unable to establish a session, retrying.", this)
  return Job.Result.retry(defaultBackoff)
}

fun IndividualMessageService.SendError.PreKeyUnavailable.toJobResult(tag: String, defaultBackoff: Long): Job.Result {
  Log.w(tag, "Unable to establish a session, retrying.", this)
  return Job.Result.retry(defaultBackoff)
}

/**
 * Retries immediately if a push challenge is solved. Otherwise retries with backoff, or fails when [defaultBackoff] is null.
 */
fun IndividualMessageService.SendError.ChallengeRequired.tryPushChallengeAndToJobResult(tag: String, defaultBackoff: Long?): Job.Result {
  if (ChallengeOption.PUSH_CHALLENGE in options && ProofRequiredExceptionHandler.tryThrottledPushChallenge() == ProofRequiredExceptionHandler.PushChallengeResult.SUCCESS) {
    Log.i(tag, "Solved a push challenge, retrying now.")
    return Job.Result.retry(0L)
  }

  if (defaultBackoff == null) {
    Log.w(tag, "Challenge required, options=$options, not retrying.")
    return Job.Result.failure()
  }

  val backoff = maxOf(defaultBackoff, retryAfter?.inWholeMilliseconds ?: 0L)
  Log.w(tag, "Challenge required, options=$options, retryAfter=$retryAfter, using backoff=${backoff}ms")
  return Job.Result.retry(backoff)
}

fun IndividualMessageService.SendError.RateLimited.toJobResult(tag: String, defaultBackoff: Long): Job.Result {
  val backoff = maxOf(defaultBackoff, retryAfter?.inWholeMilliseconds ?: 0L)
  Log.w(tag, "Rate limited, retryAfter=$retryAfter, using backoff=${backoff}ms", this)
  return Job.Result.retry(backoff)
}

fun IndividualMessageService.SendError.NetworkError.toJobResult(tag: String, defaultBackoff: Long): Job.Result {
  Log.w(tag, "Network error, retrying.", exception)
  return Job.Result.retry(defaultBackoff)
}

fun IndividualMessageService.SendError.ApplicationError.toJobResult(tag: String, description: String, defaultBackoff: Long): Job.Result {
  return when (val cause = exception) {
    is NoSessionException,
    is InvalidSessionException -> {
      Log.w(tag, "Bad session, retrying.", cause)
      Job.Result.retry(defaultBackoff)
    }

    is RuntimeException -> {
      Log.e(tag, "Unexpected error sending $description.", cause)
      Job.Result.fatalFailure(cause)
    }

    else -> {
      Log.w(tag, "Application error.", cause)
      Job.Result.failure()
    }
  }
}

/**
 * Retries challenges and rate limits, so only use it from jobs that don't share a queue with user sends.
 */
fun IndividualMessageService.SendError.toSyncJobResult(tag: String, description: String, defaultBackoff: Long): Job.Result {
  return when (this) {
    is IndividualMessageService.SendError.IdentityMismatch,
    is IndividualMessageService.SendError.NotRegistered,
    is IndividualMessageService.SendError.Unauthorized,
    is IndividualMessageService.SendError.ContentTooLarge -> {
      Log.w(tag, "Failed to send $description.", this)
      Job.Result.failure()
    }

    is IndividualMessageService.SendError.ChallengeRequired -> tryPushChallengeAndToJobResult(tag, defaultBackoff)
    is IndividualMessageService.SendError.PreKeyUnavailable -> toJobResult(tag, defaultBackoff)
    is IndividualMessageService.SendError.SessionAttemptsExhausted -> toJobResult(tag, defaultBackoff)
    is IndividualMessageService.SendError.RateLimited -> toJobResult(tag, defaultBackoff)
    is IndividualMessageService.SendError.NetworkError -> toJobResult(tag, defaultBackoff)
    is IndividualMessageService.SendError.ApplicationError -> toJobResult(tag, description, defaultBackoff)
  }
}

/**
 * Unwraps an upload result. On failure, Left is the job result to return.
 */
fun <T> RequestResult<T, CdnApi.UploadError>.toEither(tag: String, defaultBackoff: Long): Either<Job.Result, T> {
  return when (this) {
    is RequestResult.Success -> result.right()
    is RequestResult.NonSuccess -> error.toJobResult(tag, defaultBackoff).left()
    is RequestResult.RetryableNetworkError -> {
      val backoff = maxOf(defaultBackoff, retryAfter?.toMillis() ?: 0L)
      Log.w(tag, "Network error uploading, retryAfter=$retryAfter, using backoff=${backoff}ms", networkError)
      Job.Result.retry(backoff).left()
    }
    is RequestResult.ApplicationError -> when (val cause = cause) {
      is RuntimeException -> {
        Log.e(tag, "Unexpected error uploading.", cause)
        Job.Result.fatalFailure(cause).left()
      }

      else -> {
        Log.w(tag, "Application error uploading.", cause)
        Job.Result.failure().left()
      }
    }
  }
}

private fun CdnApi.UploadError.toJobResult(tag: String, defaultBackoff: Long): Job.Result {
  return when (this) {
    is CdnApi.UploadError.TooLarge,
    is CdnApi.UploadError.ChecksumMismatch -> {
      Log.w(tag, "Upload rejected: $this")
      Job.Result.failure()
    }

    is CdnApi.UploadError.InvalidRequest,
    is CdnApi.UploadError.ResumeLocationInvalid -> {
      Log.w(tag, "Upload needs to start over: $this")
      Job.Result.retry(defaultBackoff)
    }

    is CdnApi.UploadError.RateLimited -> {
      val backoff = maxOf(defaultBackoff, retryAfter?.inWholeMilliseconds ?: 0L)
      Log.w(tag, "Upload rate limited, retryAfter=$retryAfter, using backoff=${backoff}ms")
      Job.Result.retry(backoff)
    }
  }
}
