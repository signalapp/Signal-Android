/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.messagesync

import org.signal.registration.util.DebugLoggableModel

sealed class MessageSyncScreenEvent : DebugLoggableModel() {
  data object LearnMoreClick : MessageSyncScreenEvent()
  data object CancelClick : MessageSyncScreenEvent()
}
