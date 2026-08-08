/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.signalloginpayment

sealed interface SignalLoginPaymentScreenActions {
  /** Open the article explaining Signal Login. */
  data object OpenLearnMoreArticle : SignalLoginPaymentScreenActions
}
