/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration

import org.signal.network.api.RegistrationApiV2.CreateLoginReceiptCredentialError
import org.signal.network.api.RegistrationApiV2.RegisterAccountError

/**
 * Outcome of buying a Signal Login and redeeming it for an account that has no phone number.
 */
sealed interface SignalLoginPurchaseResult {

  /** The purchase was redeemed and a brand new account now exists. */
  data class Registered(val account: RegisteredAccountData) : SignalLoginPurchaseResult

  /**
   * The payment has not settled yet, either with Google Play or with the service. The purchase is persisted, so the
   * user can come back and finish later.
   */
  data object PurchasePending : SignalLoginPurchaseResult

  /** The user backed out of the Google Play purchase sheet. Nothing was charged. */
  data object Cancelled : SignalLoginPurchaseResult

  /** Google Play could not sell the product here, e.g. this is not a Play build or the product is not published. */
  data object PurchaseUnavailable : SignalLoginPurchaseResult

  /** Google Play failed to take the payment. */
  data object PurchaseFailed : SignalLoginPurchaseResult

  /** The purchase went through, but the service would not issue a receipt credential for it. */
  data class RedemptionFailed(val error: CreateLoginReceiptCredentialError) : SignalLoginPurchaseResult

  /** The purchase was redeemed for a valid credential, but registering with it failed. */
  data class RegistrationFailed(val error: RegisterAccountError) : SignalLoginPurchaseResult

  data object NetworkError : SignalLoginPurchaseResult

  data object UnknownError : SignalLoginPurchaseResult
}
