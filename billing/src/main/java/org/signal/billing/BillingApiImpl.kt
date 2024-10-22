/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.billing

import android.app.Activity
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingFlowParams.ProductDetailsParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.ProductDetailsResult
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.util.billing.BillingApi
import org.signal.core.util.billing.BillingDependencies
import org.signal.core.util.billing.BillingProduct
import org.signal.core.util.billing.BillingPurchaseResult
import org.signal.core.util.logging.Log
import org.signal.core.util.money.FiatMoney
import java.math.BigDecimal
import java.util.Currency

/**
 * BillingApi serves as the core location for interacting with the Google Billing API. Use of this API is required
 * for remote backups paid tier, and will only be available in play store builds.
 *
 * Care should be taken here to ensure only one instance of this exists at a time.
 */
internal class BillingApiImpl(
  private val billingDependencies: BillingDependencies
) : BillingApi {

  companion object {
    private val TAG = Log.tag(BillingApi::class)
  }

  private val connectionState = MutableStateFlow<State>(State.Init)
  private val coroutineScope = CoroutineScope(Dispatchers.Default)

  private val internalResults = MutableSharedFlow<BillingPurchaseResult>()

  private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
    val result = when (billingResult.responseCode) {
      BillingResponseCode.OK -> {
        if (purchases == null) {
          Log.d(TAG, "purchasesUpdatedListener: No purchases.")
          BillingPurchaseResult.None
        } else {
          Log.d(TAG, "purchasesUpdatedListener: ${purchases.size} purchases.")
          val newestPurchase = purchases.maxByOrNull { it.purchaseTime }
          if (newestPurchase == null) {
            Log.d(TAG, "purchasesUpdatedListener: no purchase.")
            BillingPurchaseResult.None
          } else {
            Log.d(TAG, "purchasesUpdatedListener: successful purchase at ${newestPurchase.purchaseTime}")
            BillingPurchaseResult.Success(
              purchaseToken = newestPurchase.purchaseToken,
              isAcknowledged = newestPurchase.isAcknowledged,
              purchaseTime = newestPurchase.purchaseTime,
              isAutoRenewing = newestPurchase.isAutoRenewing
            )
          }
        }
      }
      BillingResponseCode.BILLING_UNAVAILABLE -> {
        Log.d(TAG, "purchasesUpdatedListener: Billing unavailable.")
        BillingPurchaseResult.BillingUnavailable
      }
      BillingResponseCode.USER_CANCELED -> {
        Log.d(TAG, "purchasesUpdatedListener: User cancelled.")
        BillingPurchaseResult.UserCancelled
      }
      BillingResponseCode.ERROR -> {
        Log.d(TAG, "purchasesUpdatedListener: error.")
        BillingPurchaseResult.GenericError
      }
      BillingResponseCode.NETWORK_ERROR -> {
        Log.d(TAG, "purchasesUpdatedListener: Network error.")
        BillingPurchaseResult.NetworkError
      }
      BillingResponseCode.DEVELOPER_ERROR -> {
        Log.d(TAG, "purchasesUpdatedListener: Developer error.")
        BillingPurchaseResult.GenericError
      }
      BillingResponseCode.FEATURE_NOT_SUPPORTED -> {
        Log.d(TAG, "purchasesUpdatedListener: Feature not supported.")
        BillingPurchaseResult.FeatureNotSupported
      }
      BillingResponseCode.ITEM_ALREADY_OWNED -> {
        Log.d(TAG, "purchasesUpdatedListener: Already owned.")
        BillingPurchaseResult.AlreadySubscribed
      }
      BillingResponseCode.ITEM_NOT_OWNED -> {
        error("This shouldn't happen during the purchase process")
      }
      BillingResponseCode.ITEM_UNAVAILABLE -> {
        Log.d(TAG, "purchasesUpdatedListener: Item is unavailable")
        BillingPurchaseResult.TryAgainLater
      }
      BillingResponseCode.SERVICE_UNAVAILABLE -> {
        Log.d(TAG, "purchasesUpdatedListener: Service is unavailable.")
        BillingPurchaseResult.TryAgainLater
      }
      BillingResponseCode.SERVICE_DISCONNECTED -> {
        Log.d(TAG, "purchasesUpdatedListener: Service is disconnected.")
        BillingPurchaseResult.TryAgainLater
      }
      else -> {
        Log.d(TAG, "purchasesUpdatedListener: No purchases.")
        BillingPurchaseResult.None
      }
    }

    coroutineScope.launch { internalResults.emit(result) }
  }

  private val billingClient: BillingClient = BillingClient.newBuilder(billingDependencies.context)
    .setListener(purchasesUpdatedListener)
    .enablePendingPurchases(
      PendingPurchasesParams.newBuilder()
        .enableOneTimeProducts()
        .build()
    )
    .build()

  init {
    coroutineScope.launch {
      createConnectionFlow()
        .retry { it is RetryException }
        .collect { newState ->
          Log.d(TAG, "Updating Google Play Billing connection state: $newState")
          connectionState.update {
            newState
          }
        }
    }
  }

  override fun getBillingPurchaseResults(): Flow<BillingPurchaseResult> {
    return internalResults
  }

  override suspend fun queryProduct(): BillingProduct? {
    try {
      val products = queryProductsInternal()

      val details: ProductDetails? = products.productDetailsList?.firstOrNull { it.productId == billingDependencies.getProductId() }
      val pricing: ProductDetails.PricingPhase? = details?.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()

      if (pricing == null) {
        Log.d(TAG, "No pricing available.")
        return null
      }

      return BillingProduct(
        price = FiatMoney(BigDecimal.valueOf(pricing.priceAmountMicros, 6), Currency.getInstance(pricing.priceCurrencyCode))
      )
    } catch (e: BillingError) {
      Log.w(TAG, "Failed to query product. Returning null. Error code: ${e.billingResponseCode}", e)
      return null
    }
  }

  override suspend fun queryPurchases(): BillingPurchaseResult {
    val param = QueryPurchasesParams.newBuilder()
      .setProductType(ProductType.SUBS)
      .build()

    val result = doOnConnectionReady {
      Log.d(TAG, "Querying purchases.")
      billingClient.queryPurchasesAsync(param)
    }

    purchasesUpdatedListener.onPurchasesUpdated(result.billingResult, result.purchasesList)

    val purchase = result.purchasesList.maxByOrNull { it.purchaseTime } ?: return BillingPurchaseResult.None

    return BillingPurchaseResult.Success(
      purchaseTime = purchase.purchaseTime,
      purchaseToken = purchase.purchaseToken,
      isAcknowledged = purchase.isAcknowledged,
      isAutoRenewing = purchase.isAutoRenewing
    )
  }

  /**
   * Launches the Google Play billing flow.
   * Returns a billing result if we launched the flow, null otherwise.
   */
  override suspend fun launchBillingFlow(activity: Activity) {
    val productDetails = queryProductsInternal().productDetailsList
    if (productDetails.isNullOrEmpty()) {
      Log.w(TAG, "No products are available! Cancelling billing flow launch.")
      return
    }

    val subscriptionDetails: ProductDetails = productDetails[0]
    val offerToken = subscriptionDetails.subscriptionOfferDetails?.firstOrNull()
    if (offerToken == null) {
      Log.w(TAG, "No offer tokens available on subscription product! Cancelling billing flow launch.")
      return
    }

    val productDetailParamsList = listOf(
      ProductDetailsParams.newBuilder()
        .setProductDetails(subscriptionDetails)
        .setOfferToken(offerToken.offerToken)
        .build()
    )

    val billingFlowParams = BillingFlowParams.newBuilder()
      .setProductDetailsParamsList(productDetailParamsList)
      .build()

    doOnConnectionReady {
      withContext(Dispatchers.Main) {
        Log.d(TAG, "Launching billing flow.")
        billingClient.launchBillingFlow(activity, billingFlowParams)
      }
    }
  }

  /**
   * Returns whether or not subscriptions are supported by a user's device. Lack of subscription support is generally due
   * to out-of-date Google Play API
   */
  override fun isApiAvailable(): Boolean {
    return billingClient.isFeatureSupported(BillingClient.FeatureType.SUBSCRIPTIONS).responseCode == BillingResponseCode.OK
  }

  private suspend fun queryProductsInternal(): ProductDetailsResult {
    val productList = listOf(
      QueryProductDetailsParams.Product.newBuilder()
        .setProductId(billingDependencies.getProductId())
        .setProductType(ProductType.SUBS)
        .build()
    )

    val params = QueryProductDetailsParams.newBuilder()
      .setProductList(productList)
      .build()

    return withContext(Dispatchers.IO) {
      doOnConnectionReady {
        Log.d(TAG, "Querying product details.")
        billingClient.queryProductDetails(params)
      }
    }
  }

  private suspend fun <T> doOnConnectionReady(block: suspend () -> T): T {
    val state = connectionState
      .filter { it == State.Connected || it is State.Failure }
      .first()

    return when (state) {
      State.Connected -> block()
      is State.Failure -> throw state.billingError
      else -> error("Unexpected state: $state")
    }
  }

  private fun createConnectionFlow(): Flow<State> {
    return callbackFlow {
      Log.d(TAG, "Starting Google Play Billing connection...", true)
      trySend(State.Connecting)

      billingClient.startConnection(object : BillingClientStateListener {
        override fun onBillingServiceDisconnected() {
          Log.d(TAG, "Google Play Billing became disconnected.", true)
          trySend(State.Disconnected)
          cancel(CancellationException("Google Play Billing became disconnected.", RetryException()))
        }

        override fun onBillingSetupFinished(billingResult: BillingResult) {
          Log.d(TAG, "onBillingSetupFinished: ${billingResult.responseCode}")
          if (billingResult.responseCode == BillingResponseCode.OK) {
            Log.d(TAG, "Google Play Billing is ready.", true)
            trySend(State.Connected)
          } else {
            Log.d(TAG, "Google Play Billing failed to connect.", true)
            val billingError = BillingError(
              billingResponseCode = billingResult.responseCode
            )
            trySend(State.Failure(billingError))
            channel.close()
          }
        }
      })

      awaitClose {
        billingClient.endConnection()
      }
    }
  }

  private sealed interface State {
    data object Init : State
    data object Connecting : State
    data object Connected : State
    data object Disconnected : State
    data class Failure(val billingError: BillingError) : State
  }

  private class RetryException : Exception()
}
