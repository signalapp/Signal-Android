/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.api

import org.signal.network.websocket.WebsocketResponse
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Parses the `Retry-After` header as a whole number of seconds. Returns null if the header is
 * absent or can't be parsed (e.g. HTTP-date form, which the server does not currently use).
 */
internal fun WebsocketResponse.retryAfter(): Duration? {
  val raw = getHeader("retry-after") ?: return null
  return raw.toLongOrNull()?.seconds
}
