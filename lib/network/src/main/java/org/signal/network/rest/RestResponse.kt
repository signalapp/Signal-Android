/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.rest

/**
 * The result of a successful (2xx) [SignalRestClient] request when no response class was provided.
 * Holds the raw status, headers, and body bytes for the caller to consume.
 */
data class RestResponse(
  val statusCode: Int,
  val headers: Map<String, String>,
  val body: ByteArray
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is RestResponse) return false
    return statusCode == other.statusCode && headers == other.headers && body.contentEquals(other.body)
  }

  override fun hashCode(): Int {
    var result = statusCode
    result = 31 * result + headers.hashCode()
    result = 31 * result + body.contentHashCode()
    return result
  }
}
