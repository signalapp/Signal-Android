/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.internal.websocket

import org.signal.libsignal.net.ChatConnection.Response

fun Response.toWebsocketResponse(isUnidentified: Boolean): WebsocketResponse {
  return WebsocketResponse(
    this.status,
    this.body.decodeToString(),
    this.headers,
    isUnidentified
  )
}
