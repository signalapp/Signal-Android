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
fun WebSocketRequestMessage.Companion.get(path: String, headers: Map<String, String> = emptyMap()): WebSocketRequestMessage {
  return WebSocketRequestMessage(
    verb = "GET",
    path = path,
    headers = headers.toHeaderList(),
    id = SecureRandom().nextLong()
  )
}

/**
 * Create a basic POST web socket request
 */
fun WebSocketRequestMessage.Companion.post(path: String, body: Any?, headers: Map<String, String> = emptyMap()): WebSocketRequestMessage {
  return WebSocketRequestMessage(
    verb = "POST",
    path = path,
    body = body?.let { JsonUtil.toJsonByteString(body) },
    headers = (if (body != null) listOf("content-type:application/json") else emptyList()) + headers.toHeaderList(),
    id = SecureRandom().nextLong()
  )
}

/**
 * Create a basic DELETE web socket request
 */
fun WebSocketRequestMessage.Companion.delete(path: String, headers: Map<String, String> = emptyMap()): WebSocketRequestMessage {
  return WebSocketRequestMessage(
    verb = "DELETE",
    path = path,
    headers = headers.toHeaderList(),
    id = SecureRandom().nextLong()
  )
}

/**
 * Create a basic PUT web socket request, where body is JSON-ified.
 */
fun WebSocketRequestMessage.Companion.put(path: String, body: Any, headers: Map<String, String> = emptyMap()): WebSocketRequestMessage {
  return WebSocketRequestMessage(
    verb = "PUT",
    path = path,
    headers = listOf("content-type:application/json") + headers.toHeaderList(),
    body = JsonUtil.toJsonByteString(body),
    id = SecureRandom().nextLong()
  )
}

private fun Map<String, String>.toHeaderList(): List<String> {
  return map { (key, value) -> "$key:$value" }
}
