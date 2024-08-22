package org.signal.billing

import android.content.Context
import org.signal.core.util.billing.BillingApi

/**
 * Website builds do not support google play billing.
 */
object BillingFactory {
  @JvmStatic
  fun create(context: Context): BillingApi {
    return BillingApi.Empty
  }
}
