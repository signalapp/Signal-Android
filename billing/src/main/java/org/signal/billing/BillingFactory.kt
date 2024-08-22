/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.billing

import android.content.Context
import org.signal.core.util.billing.BillingApi

/**
 * Play billing factory. Returns empty implementation if message backups are not enabled.
 */
object BillingFactory {
  @JvmStatic
  fun create(context: Context, isBackupsAvailable: Boolean): BillingApi {
    return if (isBackupsAvailable) {
      BillingApiImpl(context)
    } else {
      BillingApi.Empty
    }
  }
}
