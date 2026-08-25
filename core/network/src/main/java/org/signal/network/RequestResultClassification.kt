/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network

import org.signal.libsignal.net.BadRequestError
import org.signal.libsignal.net.RequestResult
import java.io.IOException
import org.signal.libsignal.net.toRequestResult as libsignalToRequestResult

/**
 * Classifies [this] as a [RequestResult]. Prefer this over libsignal's `Throwable.toRequestResult()`.
 *
 * Libsignal's classifier misses handling things like IOException and forwards them as application errors.
 */
inline fun <reified E : BadRequestError> Throwable.toRequestResult(): RequestResult<Nothing, E> {
  if (this is E) {
    return RequestResult.NonSuccess(this)
  }

  val result = libsignalToRequestResult()
  val unclassified = (result as? RequestResult.ApplicationError)?.cause

  return if (unclassified is IOException) {
    RequestResult.RetryableNetworkError(unclassified)
  } else {
    result
  }
}
