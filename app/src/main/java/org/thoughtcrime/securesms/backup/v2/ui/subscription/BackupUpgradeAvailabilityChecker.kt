/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui.subscription

import android.content.Context
import com.google.android.gms.common.GoogleApiAvailability
import org.thoughtcrime.securesms.dependencies.AppDependencies

/**
 * Delegate object for checking whether backup upgrade prompts should be shown to the user.
 */
object BackupUpgradeAvailabilityChecker {

  /**
   * Best effort check for upgrade access. We check availability and show proper dialogs in the checkout
   * flow, so this is fine as "best effort"
   */
  suspend fun isUpgradeAvailable(
    context: Context
  ): Boolean {
    val googlePlayServicesCode = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
    val googlePlayServicesAvailability = GooglePlayServicesAvailability.fromCode(googlePlayServicesCode)

    return when (googlePlayServicesAvailability) {
      GooglePlayServicesAvailability.UNKNOWN, GooglePlayServicesAvailability.SERVICE_MISSING, GooglePlayServicesAvailability.SERVICE_DISABLED, GooglePlayServicesAvailability.SERVICE_INVALID -> false
      GooglePlayServicesAvailability.SERVICE_VERSION_UPDATE_REQUIRED, GooglePlayServicesAvailability.SERVICE_UPDATING -> true
      GooglePlayServicesAvailability.SUCCESS -> {
        val billingResponseCode = AppDependencies.billingApi.getApiAvailability()
        billingResponseCode.isSuccess
      }
    }
  }
}
