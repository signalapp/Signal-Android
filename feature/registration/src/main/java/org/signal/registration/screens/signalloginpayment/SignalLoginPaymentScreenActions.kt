/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.signalloginpayment

import org.signal.core.util.billing.PurchaseLauncher

sealed interface SignalLoginPaymentScreenActions {
  /** Open the article explaining Signal Login. */
  data object OpenLearnMoreArticle : SignalLoginPaymentScreenActions

  /**
   * Launch the Google Play purchase sheet. The UI layer owns the activity, launches [launcher], and reports the
   * outcome back as [SignalLoginPaymentScreenEvents.PurchaseFlowCompleted].
   */
  data class LaunchPurchaseFlow(val launcher: PurchaseLauncher) : SignalLoginPaymentScreenActions
}
