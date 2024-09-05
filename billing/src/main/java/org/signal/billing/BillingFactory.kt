/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.billing

import org.signal.core.util.billing.BillingApi
import org.signal.core.util.billing.BillingDependencies

/**
 * Play billing factory. Returns empty implementation if message backups are not enabled.
 */
object BillingFactory {
  @JvmStatic
  fun create(billingDependencies: BillingDependencies, isBackupsAvailable: Boolean): BillingApi {
    return if (isBackupsAvailable) {
      BillingApiImpl(billingDependencies)
    } else {
      BillingApi.Empty
    }
  }
}
