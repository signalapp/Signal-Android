/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util.billing

import org.signal.core.util.logging.Log

enum class BillingResponseCode(val code: Int) {
  UNKNOWN(code = Int.MIN_VALUE),
  SERVICE_TIMEOUT(code = -3),
  FEATURE_NOT_SUPPORTED(code = -2),
  SERVICE_DISCONNECTED(code = -1),
  OK(code = 0),
  USER_CANCELED(code = 1),
  SERVICE_UNAVAILABLE(code = 2),
  BILLING_UNAVAILABLE(code = 3),
  ITEM_UNAVAILABLE(code = 4),
  DEVELOPER_ERROR(code = 5),
  ERROR(code = 6),
  ITEM_ALREADY_OWNED(code = 7),
  ITEM_NOT_OWNED(code = 8),
  NETWORK_ERROR(code = 12);

  val isSuccess: Boolean get() = this == OK

  companion object {

    private val TAG = Log.tag(BillingResponseCode::class)

    fun fromBillingLibraryResponseCode(responseCode: Int): BillingResponseCode {
      val code = BillingResponseCode.entries.firstOrNull { responseCode == it.code } ?: UNKNOWN

      if (code == UNKNOWN) {
        Log.w(TAG, "Unknown response code: $code")
      }

      return code
    }
  }
}
