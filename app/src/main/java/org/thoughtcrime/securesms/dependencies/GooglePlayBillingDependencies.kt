/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.dependencies

import android.content.Context
import org.signal.core.util.billing.BillingDependencies

/**
 * Dependency object for Google Play Billing.
 */
object GooglePlayBillingDependencies : BillingDependencies {

  override val context: Context get() = AppDependencies.application

  override suspend fun getProductId(): String {
    return "backup" // TODO [message-backups] This really shouldn't be hardcoded into the app.
  }

  override suspend fun getBasePlanId(): String {
    return "monthly" // TODO [message-backups] This really shouldn't be hardcoded into the app.
  }
}
