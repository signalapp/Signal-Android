package org.thoughtcrime.securesms.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.ProductDetailsResult
import com.android.billingclient.api.PurchasesUpdatedListener
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

  private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
    when {
      billingResult.responseCode == BillingResponseCode.OK && purchases != null -> {
        Log.d(TAG, "purchasesUpdatedListener: ${purchases.size} purchases.")
        purchases.forEach {
          // Handle purchases.
        }
      }
      billingResult.responseCode == BillingResponseCode.USER_CANCELED -> {
        // Handle user cancelled
        Log.d(TAG, "purchasesUpdatedListener: User cancelled.")
      }
      else -> {
        Log.d(TAG, "purchasesUpdatedListener: No purchases.")
      }
    }
  }

  private val billingApi: BillingApi = BillingApi.getOrCreate(context, purchasesUpdatedListener)

  override fun isApiAvailable(): Boolean = billingApi.areSubscriptionsSupported()

  override suspend fun queryProducts() {
    val products: ProductDetailsResult = billingApi.queryProducts()

    Log.d(TAG, "queryProducts: ${products.billingResult.responseCode}, ${products.billingResult.debugMessage}")
  }

  override suspend fun queryPurchases() {
    Log.d(TAG, "queryPurchases")

    val purchaseResult = billingApi.queryPurchases()
    purchasesUpdatedListener.onPurchasesUpdated(purchaseResult.billingResult, purchaseResult.purchasesList)
  }

  override suspend fun launchBillingFlow(activity: Activity) {
    billingApi.launchBillingFlow(activity)
  }
}
