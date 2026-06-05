/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.rest

import org.signal.libsignal.net.BadRequestError

/**
 * Maps a non-2xx HTTP response into a typed [BadRequestError]. Pass an instance to
 * [SignalRestClient.request] when a request has known business-logic errors.
 */
fun interface ErrorMapper<out E : BadRequestError> {
  fun map(statusCode: Int, headers: Map<String, String>, body: ByteArray?): E
}
