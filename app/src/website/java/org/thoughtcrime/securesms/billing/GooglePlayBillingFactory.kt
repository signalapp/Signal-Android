package org.thoughtcrime.securesms.billing

import android.content.Context

/**
 * Website builds do not support google play billing.
 */
object GooglePlayBillingFactory {
  @JvmStatic
  fun create(context: Context): GooglePlayBillingApi {
    return GooglePlayBillingApi.Empty
  }
}
