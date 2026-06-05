/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.rest

import org.signal.libsignal.net.BadRequestError

/**
 * Default error returned when a request produces a non-2xx response and the caller didn't supply
 * a custom [ErrorMapper]. Carries the raw response info so callers can inspect after the fact.
 */
data class RestStatusCodeError(
  val statusCode: Int,
  val headers: Map<String, String>,
  val body: ByteArray?
) : BadRequestError {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is RestStatusCodeError) return false
    return statusCode == other.statusCode &&
      headers == other.headers &&
      (body?.contentEquals(other.body) ?: (other.body == null))
  }

  override fun hashCode(): Int {
    var result = statusCode
    result = 31 * result + headers.hashCode()
    result = 31 * result + (body?.contentHashCode() ?: 0)
    return result
  }
}
