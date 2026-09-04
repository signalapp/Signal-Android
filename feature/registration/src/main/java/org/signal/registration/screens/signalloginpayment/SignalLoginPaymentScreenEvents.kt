/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.signalloginpayment

import org.signal.core.util.billing.OneTimePurchaseResult

sealed class SignalLoginPaymentScreenEvents {
  /** Emitted once when the screen is created to load initial data (namely the purchase price) into the state. */
  data object Initialize : SignalLoginPaymentScreenEvents()

  /** The user tapped the back arrow. */
  data object BackClicked : SignalLoginPaymentScreenEvents()

  /** The user tapped the "learn more" link in the description. */
  data object LearnMoreClicked : SignalLoginPaymentScreenEvents()

  /** The user selected one of the two options. */
  data class OptionSelected(val option: SignalLoginPaymentState.Option) : SignalLoginPaymentScreenEvents()

  /** The user tapped retry on the purchase card after the price lookup failed. */
  data object PriceRetryClicked : SignalLoginPaymentScreenEvents()

  /** The user edited the manually-pasted receipt credential that skips the purchase flow. */
  data class ManualReceiptCredentialChanged(val value: ManualReceiptCredential) : SignalLoginPaymentScreenEvents()

  /** The user tapped the primary action button, committing to the currently selected option. */
  data object ContinueClicked : SignalLoginPaymentScreenEvents()

  /**
   * The Google Play purchase sheet the UI layer launched has resolved. Carries only the outcome -- the activity that
   * hosted the sheet stays in the UI layer.
   */
  data class PurchaseFlowCompleted(val result: OneTimePurchaseResult) : SignalLoginPaymentScreenEvents()

  /** The user dismissed the network error dialog. */
  data object NetworkErrorDialogDismissed : SignalLoginPaymentScreenEvents()

  /** The user dismissed the unknown error dialog. */
  data object UnknownErrorDialogDismissed : SignalLoginPaymentScreenEvents()

  /** The user dismissed the failed-purchase dialog. */
  data object PurchaseFailedDialogDismissed : SignalLoginPaymentScreenEvents()

  /** The user dismissed the dialog saying Signal Login can't be bought here. */
  data object PurchaseUnavailableDialogDismissed : SignalLoginPaymentScreenEvents()

  /** The user dismissed the dialog saying their payment hasn't settled yet. */
  data object PurchasePendingDialogDismissed : SignalLoginPaymentScreenEvents()

  /** The user dismissed the invalid-receipt-credential dialog. */
  data object InvalidReceiptCredentialDialogDismissed : SignalLoginPaymentScreenEvents()
}
