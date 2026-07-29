/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.signalloginpayment

sealed class SignalLoginPaymentScreenEvents {
  /** Emitted once when the screen is created to load initial data (namely the purchase price) into the state. */
  data object Initialize : SignalLoginPaymentScreenEvents()

  /** The user tapped the back arrow. */
  data object BackClicked : SignalLoginPaymentScreenEvents()

  /** The user tapped the "learn more" link in the description. */
  data object LearnMoreClicked : SignalLoginPaymentScreenEvents()

  /** The user selected one of the two options. */
  data class OptionSelected(val option: SignalLoginPaymentState.Option) : SignalLoginPaymentScreenEvents()

  /** The user tapped the primary action button, committing to the currently selected option. */
  data object ContinueClicked : SignalLoginPaymentScreenEvents()

  /** The user dismissed the network error dialog. */
  data object NetworkErrorDialogDismissed : SignalLoginPaymentScreenEvents()

  /** The user dismissed the unknown error dialog. */
  data object UnknownErrorDialogDismissed : SignalLoginPaymentScreenEvents()

  /** The user dismissed the failed-purchase dialog. */
  data object PurchaseFailedDialogDismissed : SignalLoginPaymentScreenEvents()
}
