/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.internal.websocket

/**
 * An interface to support app<->signal-service interop for the purposes of web socket shadowing.
 */
interface WebSocketShadowingBridge {
  /**
   * Persist shadowing stats snapshot.
   */
  fun writeStatsSnapshot(bytes: ByteArray)

  /**
   * Restore shadowing stats snapshot.
   */
  fun readStatsSnapshot(): ByteArray?

  /**
   * Display a notification the user to submit debug logs, with a custom message.
   */
  fun triggerFailureNotification(message: String)
}
