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
  /** Where the one-time purchase price lookup has got to. */
  val price: Price = Price.Loading,
  /**
   * Whether the user has already paid for a Signal Login that was never redeemed, because registration failed after
   * payment last time. Continuing picks that purchase back up rather than charging again.
   */
  val hasUnredeemedPurchase: Boolean = false,
  /**
   * Whether a Signal Login can be bought on this build at all, which requires Google Play billing. When false the
   * purchase option is disabled, but the flow stays open so someone who already has a Signal Login can still log in.
   */
  val isPurchaseSupported: Boolean = true,
  /**
   * A manually-pasted, base64-encoded receipt credential. Lets a debug build skip payment entirely and register with a
   * credential issued out-of-band. When non-blank, the continue button redeems it directly.
   */
  val manualReceiptCredential: ManualReceiptCredential = ManualReceiptCredential.EMPTY,
  val showManualReceiptCredentialEntry: Boolean = false,
  val showSpinner: Boolean = false,
  val dialogs: Dialogs = Dialogs()
) {
  /** Whether the purchase option can be picked. A purchase that was already paid for can still be continued. */
  val isPurchaseOptionEnabled: Boolean
    get() = isPurchaseSupported || hasUnredeemedPurchase

  /** Whether we know enough to let the user act on the selected option. */
  val isActionEnabled: Boolean
    get() = !showSpinner && (manualReceiptCredential.isNotBlank || selectedOption == Option.ExistingLogin || hasUnredeemedPurchase || price is Price.Available)

  /**
   * Result of asking Google Play what a Signal Login costs.
   */
  sealed interface Price {
    data object Loading : Price

    data class Available(val formattedPrice: String) : Price

    /** The lookup failed but may succeed on another attempt. The card offers a retry. */
    data object TransientError : Price

    /** A Signal Login can't be bought here at all. There is nothing to retry. */
    data object Unavailable : Price
  }

  enum class Option {
    /** Buy a new Signal Login. */
    Purchase,

    /** Register with an account key the user already owns. */
    ExistingLogin
  }

  data class Dialogs(
    val networkError: Boolean = false,
    val unknownError: Boolean = false,
    val purchaseFailed: Boolean = false,
    val purchaseUnavailable: Boolean = false,
    val purchasePending: Boolean = false,
    val invalidReceiptCredential: Boolean = false
  )
}
