package org.thoughtcrime.securesms.billing

import android.content.Context
import com.android.billingclient.api.ProductDetailsResult
import org.signal.billing.BillingApi
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.util.RemoteConfig

/**
 * Play billing factory. Returns empty implementation if message backups are not enabled.
 */
object GooglePlayBillingFactory {
  @JvmStatic
  fun create(context: Context): GooglePlayBillingApi {
    return if (RemoteConfig.messageBackups) {
      GooglePlayBillingApiImpl(context)
    } else {
      GooglePlayBillingApi.Empty
    }
  }
}

/**
 * Play Store implementation
 */
private class GooglePlayBillingApiImpl(context: Context) : GooglePlayBillingApi {

  private companion object {
    val TAG = Log.tag(GooglePlayBillingApiImpl::class)
  }

  private val billingApi: BillingApi = BillingApi.getOrCreate(context)

  override fun isApiAvailable(): Boolean = billingApi.areSubscriptionsSupported()

  override suspend fun queryProducts() {
    val products: ProductDetailsResult = billingApi.queryProducts()

    Log.d(TAG, "queryProducts: ${products.billingResult.responseCode}, ${products.billingResult.debugMessage}")
  }
}
