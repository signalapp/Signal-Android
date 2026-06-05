/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.messagesync

import org.signal.core.util.ByteSize
import org.signal.core.util.bytes
import org.signal.registration.util.DebugLoggableModel

data class MessageSyncScreenState(
  val downloadedBytes: ByteSize = 0.bytes,
  val totalBytes: ByteSize = 0.bytes
) : DebugLoggableModel()
