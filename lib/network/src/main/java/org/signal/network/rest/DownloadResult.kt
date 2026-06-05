/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.rest

/**
 * Success outcome of [SignalRestClient.download]. The actual payload was streamed into the
 * caller-supplied destination; this carries metadata about the response.
 */
data class DownloadResult(
  val statusCode: Int,
  val headers: Map<String, String>,
  val totalBytes: Long
)
