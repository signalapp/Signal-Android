/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.internal

import org.whispersystems.signalservice.internal.util.JsonUtil
import org.whispersystems.signalservice.internal.websocket.WebSocketRequestMessage
import java.security.SecureRandom

/**
 * Create a basic GET web socket request
 */
fun WebSocketRequestMessage.Companion.get(path: String): WebSocketRequestMessage {
  return WebSocketRequestMessage(
    verb = "GET",
    path = path,
    id = SecureRandom().nextLong()
  )
}

/**
 * Create a basic DELETE web socket request
 */
fun WebSocketRequestMessage.Companion.delete(path: String): WebSocketRequestMessage {
  return WebSocketRequestMessage(
    verb = "DELETE",
    path = path,
    id = SecureRandom().nextLong()
  )
}

/**
 * Create a basic PUT web socket request, where body is JSON-ified.
 */
fun WebSocketRequestMessage.Companion.put(path: String, body: Any): WebSocketRequestMessage {
  return WebSocketRequestMessage(
    verb = "PUT",
    path = path,
    headers = listOf("content-type:application/json"),
    body = JsonUtil.toJsonByteString(body),
    id = SecureRandom().nextLong()
  )
}
