/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.messagesync

sealed class MessageSyncScreenEvent {
  data object LearnMoreClick : MessageSyncScreenEvent()
  data object CancelClick : MessageSyncScreenEvent()
  data object RetryClick : MessageSyncScreenEvent()
  data object ContinueWithoutMessagesClick : MessageSyncScreenEvent()
}
