/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.signallogincredentials

/** What [SignalLoginVerifyViewModel] asks its host to do, since the check runs outside the registration flow. */
sealed interface SignalLoginVerifyActions {
  /** The user typed in the login that matches their account. */
  data object Verified : SignalLoginVerifyActions

  /** The user backed out without verifying. */
  data object Close : SignalLoginVerifyActions

  /** Open the article explaining where to find your Signal Login. */
  data object OpenNeedHelpArticle : SignalLoginVerifyActions
}
