/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.messagesync

sealed interface MessageSyncScreenAction {
  /** Open the article explaining message sync between devices. */
  data object OpenLearnMoreArticle : MessageSyncScreenAction
}
