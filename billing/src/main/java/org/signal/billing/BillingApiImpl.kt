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
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.util.billing.BillingApi
import org.signal.core.util.billing.BillingDependencies
import org.signal.core.util.billing.BillingError
import org.signal.core.util.billing.BillingProduct
import org.signal.core.util.billing.BillingPurchaseResult
import org.signal.core.util.billing.BillingPurchaseState
import org.signal.core.util.logging.Log
import org.signal.core.util.money.FiatMoney
import java.math.BigDecimal
import java.util.Currency
import java.util.concurrent.Executors
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

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
    private val CACHE_LIFESPAN = 1.days
  }

  private val productDetailsDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
  private var productDetailsExpiration: Duration = 0.days
  private var productDetailsResult: ProductDetailsResult? = null

  private val connectionState = MutableStateFlow<State>(State.Init)
  private val coroutineScope = CoroutineScope(Dispatchers.Default)
  private val connectionStateDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

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
              purchaseState = newestPurchase.purchaseState.toBillingPurchaseState(),
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

  override fun getBillingPurchaseResults(): Flow<BillingPurchaseResult> {
    return internalResults
  }

  override suspend fun queryProduct(): BillingProduct? {
    return withContext(Dispatchers.IO) {
      try {
        val products = queryProductsInternal()

        val details: ProductDetails? = products.productDetailsList?.firstOrNull { it.productId == billingDependencies.getProductId() }
        val pricing: ProductDetails.PricingPhase? = details?.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()

        if (pricing == null) {
          Log.d(TAG, "No pricing available.")
          null
        } else {
          val price = FiatMoney(BigDecimal.valueOf(pricing.priceAmountMicros, 6), Currency.getInstance(pricing.priceCurrencyCode))
          Log.d(TAG, "Found product pricing: $price")
          BillingProduct(
            price = price
          )
        }
      } catch (e: BillingError) {
        Log.w(TAG, "Failed to query product. Returning null. Error code: ${e.billingResponseCode}", e)
        null
      }
    }
  }

  override suspend fun queryPurchases(): BillingPurchaseResult {
    val param = QueryPurchasesParams.newBuilder()
      .setProductType(ProductType.SUBS)
      .build()

    val result = doOnConnectionReady("queryPurchases") {
      billingClient.queryPurchasesAsync(param)
    }

    val purchase = result.purchasesList.maxByOrNull { it.purchaseTime } ?: return BillingPurchaseResult.None

    return BillingPurchaseResult.Success(
      purchaseState = purchase.purchaseState.toBillingPurchaseState(),
      purchaseTime = purchase.purchaseTime,
      purchaseToken = purchase.purchaseToken,
      isAcknowledged = purchase.isAcknowledged,
      isAutoRenewing = purchase.isAutoRenewing
    )
  }

  /**
   * Launches the Google Play billing flow.
   *
   * If the user already has an active purchase (purchase exists and autoRenew == true) then we will not
   * launch and instead immediately post the purchase.
   */
  override suspend fun launchBillingFlow(activity: Activity) {
    val latestPurchase = queryPurchases()
    if (latestPurchase is BillingPurchaseResult.Success && latestPurchase.isAutoRenewing) {
      Log.w(TAG, "Already purchased.")
      internalResults.emit(latestPurchase)
      return
    }

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

    doOnConnectionReady("launchBillingFlow") {
      withContext(Dispatchers.Main) {
        billingClient.launchBillingFlow(activity, billingFlowParams)
      }
    }
  }

  /**
   * Returns whether or not subscriptions are supported by a user's device. Lack of subscription support is generally due
   * to out-of-date Google Play API
   */
  override suspend fun isApiAvailable(): Boolean {
    return try {
      doOnConnectionReady("isApiAvailable") {
        billingClient.isFeatureSupported(BillingClient.FeatureType.SUBSCRIPTIONS).responseCode == BillingResponseCode.OK
      }
    } catch (e: BillingError) {
      Log.e(TAG, "Failed to connect to Google Play Billing", e)
      false
    }
  }

  private fun Int.toBillingPurchaseState(): BillingPurchaseState {
    return when (this) {
      Purchase.PurchaseState.PURCHASED -> BillingPurchaseState.PURCHASED
      Purchase.PurchaseState.PENDING -> BillingPurchaseState.PENDING
      else -> BillingPurchaseState.UNSPECIFIED
    }
  }

  private suspend fun queryProductsInternal(): ProductDetailsResult {
    return withContext(productDetailsDispatcher) {
      val now = System.currentTimeMillis().milliseconds
      val cachedResult = productDetailsResult
      if (now < productDetailsExpiration && cachedResult != null) {
        Log.d(TAG, "Returning cached product details.")
        return@withContext cachedResult
      }

      val productList = listOf(
        QueryProductDetailsParams.Product.newBuilder()
          .setProductId(billingDependencies.getProductId())
          .setProductType(ProductType.SUBS)
          .build()
      )

      val params = QueryProductDetailsParams.newBuilder()
        .setProductList(productList)
        .build()

      val result = doOnConnectionReady("queryProductsInternal") {
        billingClient.queryProductDetails(params)
      }

      Log.d(TAG, "Caching product details.")
      productDetailsResult = result
      productDetailsExpiration = now + CACHE_LIFESPAN

      return@withContext result
    }
  }

  private suspend fun <T> doOnConnectionReady(caller: String, block: suspend () -> T): T {
    Log.d(TAG, "Awaiting connection from $caller... (current state: ${connectionState.value})", true)
    startBillingClientConnectionIfNecessary()

    val state = connectionState
      .filter { it == State.Connected || it is State.Failure }
      .first()

    Log.d(TAG, "Handling block from $caller.. (current state: ${connectionState.value})", true)
    return when (state) {
      State.Connected -> block()
      is State.Failure -> throw state.billingError
      else -> error("Unexpected state: $state")
    }
  }

  private suspend fun startBillingClientConnectionIfNecessary() {
    withContext(connectionStateDispatcher) {
      val billingConnectionState = billingClient.connectionState
      when (billingConnectionState) {
        BillingClient.ConnectionState.DISCONNECTED -> {
          Log.d(TAG, "BillingClient is disconnected. Starting connection attempt.", true)
          connectionState.update { State.Connecting }
          billingClient.startConnection(
            BillingListener(
              onStateUpdate = { new ->
                connectionState.update { old ->
                  Log.d(TAG, "Moving from state $old -> $new", true)
                  new
                }
              }
            )
          )
        }

        BillingClient.ConnectionState.CONNECTING -> {
          Log.d(TAG, "BillingClient is already connecting. Nothing to do.", true)
        }

        BillingClient.ConnectionState.CONNECTED -> {
          Log.d(TAG, "BillingClient is already connected. Nothing to do.", true)
        }

        BillingClient.ConnectionState.CLOSED -> {
          Log.w(TAG, "BillingClient was permanently closed. Cannot proceed.", true)
        }
      }
    }
  }

  private class BillingListener(
    private val onStateUpdate: (State) -> Unit
  ) : BillingClientStateListener {
    override fun onBillingServiceDisconnected() {
      Log.d(TAG, "BillingListener#onBillingServiceDisconnected", true)
      onStateUpdate(State.Disconnected)
    }

    override fun onBillingSetupFinished(billingResult: BillingResult) {
      Log.d(TAG, "BillingListener#onBillingSetupFinished: ${billingResult.responseCode}", true)
      if (billingResult.responseCode == BillingResponseCode.OK) {
        Log.d(TAG, "BillingListener#onBillingSetupFinished: ready", true)
        onStateUpdate(State.Connected)
      } else {
        Log.d(TAG, "BillingListener#onBillingSetupFinished: failure", true)
        val billingError = BillingError(
          billingResponseCode = billingResult.responseCode
        )
        onStateUpdate(State.Failure(billingError))
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
