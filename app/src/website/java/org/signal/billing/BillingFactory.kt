package org.signal.billing

import org.signal.core.util.billing.BillingApi
import org.signal.core.util.billing.BillingDependencies

/**
 * Website builds do not support google play billing.
 */
object BillingFactory {
  @JvmStatic
  fun create(billingDependencies: BillingDependencies, isBackupsAvailable: Boolean): BillingApi {
    return BillingApi.Empty
  }
}
