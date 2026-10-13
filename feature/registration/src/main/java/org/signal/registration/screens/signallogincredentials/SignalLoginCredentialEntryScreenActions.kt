/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.signallogincredentials

sealed interface SignalLoginCredentialEntryScreenActions {
  /** Open the article explaining where to find your Signal Login. */
  data object OpenNeedHelpArticle : SignalLoginCredentialEntryScreenActions
}
