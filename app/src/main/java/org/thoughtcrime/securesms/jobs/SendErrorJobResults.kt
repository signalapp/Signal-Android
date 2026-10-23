package org.thoughtcrime.securesms.jobs

import org.signal.core.util.logging.Log
import org.signal.libsignal.net.ChallengeOption
import org.signal.libsignal.protocol.InvalidSessionException
import org.signal.libsignal.protocol.NoSessionException
import org.signal.network.service.MessageService
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.ratelimit.ProofRequiredExceptionHandler

fun MessageService.SendError.SessionAttemptsExhausted.toJobResult(tag: String, defaultBackoff: Long): Job.Result {
  Log.w(tag, "Unable to establish a session, retrying.", this)
  return Job.Result.retry(defaultBackoff)
}

fun MessageService.SendError.PreKeyUnavailable.toJobResult(tag: String, defaultBackoff: Long): Job.Result {
  Log.w(tag, "Unable to establish a session, retrying.", this)
  return Job.Result.retry(defaultBackoff)
}

/**
 * Retries immediately if a push challenge is solved. Otherwise retries with backoff, or fails when [defaultBackoff] is null.
 */
fun MessageService.SendError.ChallengeRequired.tryPushChallengeAndToJobResult(tag: String, defaultBackoff: Long?): Job.Result {
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

fun MessageService.SendError.RateLimited.toJobResult(tag: String, defaultBackoff: Long): Job.Result {
  val backoff = maxOf(defaultBackoff, retryAfter?.inWholeMilliseconds ?: 0L)
  Log.w(tag, "Rate limited, retryAfter=$retryAfter, using backoff=${backoff}ms", this)
  return Job.Result.retry(backoff)
}

fun MessageService.SendError.NetworkError.toJobResult(tag: String, defaultBackoff: Long): Job.Result {
  Log.w(tag, "Network error, retrying.", exception)
  return Job.Result.retry(defaultBackoff)
}

fun MessageService.SendError.ApplicationError.toJobResult(tag: String, description: String, defaultBackoff: Long): Job.Result {
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
fun MessageService.SendError.toSyncJobResult(tag: String, description: String, defaultBackoff: Long): Job.Result {
  return when (this) {
    is MessageService.SendError.IdentityMismatch,
    is MessageService.SendError.NotRegistered,
    is MessageService.SendError.Unauthorized,
    is MessageService.SendError.ContentTooLarge -> {
      Log.w(tag, "Failed to send $description.", this)
      Job.Result.failure()
    }

    is MessageService.SendError.ChallengeRequired -> tryPushChallengeAndToJobResult(tag, defaultBackoff)
    is MessageService.SendError.PreKeyUnavailable -> toJobResult(tag, defaultBackoff)
    is MessageService.SendError.SessionAttemptsExhausted -> toJobResult(tag, defaultBackoff)
    is MessageService.SendError.RateLimited -> toJobResult(tag, defaultBackoff)
    is MessageService.SendError.NetworkError -> toJobResult(tag, defaultBackoff)
    is MessageService.SendError.ApplicationError -> toJobResult(tag, description, defaultBackoff)
  }
}
