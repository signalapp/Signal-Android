/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

@file:JvmName("SendJobUtil")

package org.thoughtcrime.securesms.jobs

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JobLogger
import org.thoughtcrime.securesms.jobmanager.impl.BackoffUtil
import org.thoughtcrime.securesms.transport.RetryLaterException
import org.thoughtcrime.securesms.util.RemoteConfig.serverErrorMaxBackoff
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException
import org.whispersystems.signalservice.api.push.exceptions.ProofRequiredException
import org.whispersystems.signalservice.api.push.exceptions.RateLimitException
import org.whispersystems.signalservice.api.push.exceptions.RetryNetworkException
import java.util.concurrent.TimeUnit
import org.signal.libsignal.net.RetryLaterException as LibSignalRetryLaterException

fun Job.getBackoffMillisFromException(tag: String, pastAttemptCount: Int, exception: Exception, default: () -> Long): Long {
  when (exception) {
    is ProofRequiredException -> {
      val backoff = exception.retryAfterSeconds
      Log.w(tag, JobLogger.format(this, "[Proof Required] Retry-After is $backoff seconds."))
      if (backoff >= 0) {
        return TimeUnit.SECONDS.toMillis(backoff)
      }
    }

    is RateLimitException -> {
      val backoff = exception.retryAfterMilliseconds.orElse(-1L)
      if (backoff >= 0) {
        return backoff
      }
    }

    is NonSuccessfulResponseCodeException -> {
      if (exception.is5xx()) {
        return BackoffUtil.exponentialBackoff(pastAttemptCount, serverErrorMaxBackoff)
      }
    }

    is LibSignalRetryLaterException -> {
      return exception.duration.toMillis()
    }

    is RetryNetworkException -> {
      return exception.retryAfterMs
    }

    is RetryLaterException -> {
      val backoff = exception.backoff
      if (backoff >= 0) {
        return backoff
      }
    }
  }

  return default()
}
