/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.signalloginpayment

/**
 * State for the Signal Login purchase screen, where the user either buys a Signal Login or indicates they already have
 * one.
 */
data class SignalLoginPaymentState(
  val selectedOption: Option = Option.Purchase,
  /**
   * Localized, currency-formatted price of the one-time purchase, as reported by the billing library. Null until the
   * price has been loaded.
   */
  val formattedPrice: String? = null,
  val showSpinner: Boolean = false,
  val dialogs: Dialogs = Dialogs()
) {
  /** Whether we know enough to let the user act on the selected option. */
  val isActionEnabled: Boolean
    get() = !showSpinner && (selectedOption == Option.ExistingLogin || formattedPrice != null)

  enum class Option {
    /** Buy a new Signal Login. */
    Purchase,

    /** Register with an account key the user already owns. */
    ExistingLogin
  }

  data class Dialogs(
    val networkError: Boolean = false,
    val unknownError: Boolean = false,
    val purchaseFailed: Boolean = false
  )
}
