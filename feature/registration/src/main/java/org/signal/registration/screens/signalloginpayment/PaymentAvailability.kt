/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.signalloginpayment

import com.google.android.gms.common.ConnectionResult
import org.signal.core.util.logging.Log

/**
 * Whether a Signal Login can be paid for on this device right now, and if not, what is wrong.
 */
enum class PaymentAvailability {
  /** Google Play services and the Play Store are both ready to take a payment. */
  Available,

  /** Google Play services is installed but too old to be used, and can be updated in place. */
  ServiceUpdateRequired,

  /** Google Play services is installed but turned off. */
  ServiceDisabled,

  /** Google Play services is not installed, but this device could install it. */
  ServiceMissing,

  /** Google Play services is midway through updating itself and should work again shortly. */
  ServiceUpdating,

  /** This device cannot run Google Play services at all. There is nothing the user can do about it. */
  ServiceInvalid,

  /** Google Play services works, but there is no Play Store account to pay with. */
  NotSignedIn,

  /** Google Play services works, but this build has no Google Play billing to pay with. */
  PurchasesUnavailable;

  val isAvailable: Boolean
    get() = this == Available

  /** Whether there is nothing the user could do about this, so the purchase option is not worth offering at all. */
  val isTerminal: Boolean
    get() = this == ServiceInvalid || this == PurchasesUnavailable

  companion object {
    private val TAG = Log.tag(PaymentAvailability::class)

    /** Maps a [ConnectionResult] code, as reported by `GoogleApiAvailability`, onto the state it describes. */
    fun fromConnectionResult(code: Int): PaymentAvailability {
      return when (code) {
        ConnectionResult.SUCCESS -> Available
        ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED -> ServiceUpdateRequired
        ConnectionResult.SERVICE_DISABLED -> ServiceDisabled
        ConnectionResult.SERVICE_MISSING -> ServiceMissing
        ConnectionResult.SERVICE_UPDATING -> ServiceUpdating
        ConnectionResult.SERVICE_INVALID -> ServiceInvalid
        else -> {
          Log.w(TAG, "Unrecognized Google Play services connection result: $code. Treating it as missing.")
          ServiceMissing
        }
      }
    }
  }
}
