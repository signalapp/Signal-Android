/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingFlowParams.ProductDetailsParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.consumePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.signal.core.util.billing.BillingError
import org.signal.core.util.billing.BillingPurchaseState
import org.signal.core.util.billing.OneTimeProduct
import org.signal.core.util.billing.OneTimeProductId
import org.signal.core.util.billing.OneTimeProductResult
import org.signal.core.util.billing.OneTimePurchase
import org.signal.core.util.billing.OneTimePurchaseApi
import org.signal.core.util.billing.OneTimePurchasePreparation
import org.signal.core.util.billing.OneTimePurchaseResult
import org.signal.core.util.billing.PurchaseLauncher
import org.signal.core.util.logging.Log
import java.util.concurrent.atomic.AtomicReference

/**
 * Google Play Billing implementation of [OneTimePurchaseApi] for consumable products.
 *
 * The purchase sheet delivers its outcome on a [PurchasesUpdatedListener] rather than as a return value, so the
 * launcher handed back by [preparePurchase] gives the listener a [CompletableDeferred] to complete. That means a purchase that lands after
 * this object dies -- process death while the sheet is open, most notably -- is not reported to anyone, which is why
 * [queryUnconsumedPurchase] exists.
 */
internal class OneTimePurchaseApiImpl(
  context: Context
) : OneTimePurchaseApi {

  companion object {
    private val TAG = Log.tag(OneTimePurchaseApiImpl::class)
  }

  private val pendingFlow = AtomicReference<PendingFlow?>(null)

  private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
    pendingFlow.getAndSet(null)?.let { it.deferred.complete(toPurchaseResult(it.product, billingResult, purchases)) }
  }

  /** Built on first use, so simply constructing this api does not stand up a Play client or a thread. */
  private val connectionLazy = lazy { BillingClientConnection(context, purchasesUpdatedListener) }
  private val connection: BillingClientConnection get() = connectionLazy.value

  override suspend fun queryProduct(product: OneTimeProductId): OneTimeProductResult = withContext(Dispatchers.IO) {
    val offer = when (val result = queryOfferDetails(product)) {
      is QueryResult.Success -> result.value
      is QueryResult.Failure -> {
        Log.w(TAG, "[queryProduct] Failed to query product. Error code: ${result.error.billingResponseCode}", true)
        return@withContext result.error.toProductResult()
      }
    }

    if (offer == null) {
      Log.w(TAG, "[queryProduct] No matching purchase option for the product.", true)
      OneTimeProductResult.Unavailable
    } else {
      OneTimeProductResult.Success(OneTimeProduct(formattedPrice = offer.formattedPrice))
    }
  }

  override suspend fun queryUnconsumedPurchase(product: OneTimeProductId): OneTimePurchase? = withContext(Dispatchers.IO) {
    when (val result = queryUnconsumedPurchaseInternal(product)) {
      is QueryResult.Success -> result.value
      is QueryResult.Failure -> {
        Log.w(TAG, "[queryUnconsumedPurchase] Failed to check for an existing purchase. Error code: ${result.error.billingResponseCode}", true)
        null
      }
    }
  }

  override suspend fun preparePurchase(product: OneTimeProductId): OneTimePurchasePreparation = withContext(Dispatchers.IO) {
    val existing = when (val result = queryUnconsumedPurchaseInternal(product)) {
      is QueryResult.Success -> result.value
      is QueryResult.Failure -> {
        Log.w(TAG, "[preparePurchase] Failed to check for an existing purchase. Error code: ${result.error.billingResponseCode}", true)
        return@withContext result.error.toPreparation()
      }
    }

    if (existing != null) {
      Log.i(TAG, "[preparePurchase] The user already has an unconsumed purchase. Reusing it instead of charging again.", true)
      return@withContext OneTimePurchasePreparation.AlreadyOwned(existing)
    }

    val offer = when (val result = queryOfferDetails(product)) {
      is QueryResult.Success -> result.value
      is QueryResult.Failure -> {
        Log.w(TAG, "[preparePurchase] Failed to query product. Error code: ${result.error.billingResponseCode}", true)
        return@withContext result.error.toPreparation()
      }
    }

    if (offer == null) {
      Log.w(TAG, "[preparePurchase] No matching purchase option for the product.", true)
      return@withContext OneTimePurchasePreparation.Unavailable
    }

    val billingFlowParams = BillingFlowParams.newBuilder()
      .setProductDetailsParamsList(
        listOf(
          ProductDetailsParams.newBuilder()
            .setProductDetails(offer.productDetails)
            .setOfferToken(offer.offerToken)
            .build()
        )
      )
      .build()

    OneTimePurchasePreparation.Ready(PurchaseLauncher { activity -> launch(activity, product, billingFlowParams) })
  }

  private suspend fun launch(activity: Activity, product: OneTimeProductId, billingFlowParams: BillingFlowParams): OneTimePurchaseResult {
    val deferred = CompletableDeferred<OneTimePurchaseResult>()
    if (!pendingFlow.compareAndSet(null, PendingFlow(product, deferred))) {
      Log.w(TAG, "[launch] A purchase flow is already in progress.", true)
      return OneTimePurchaseResult.GenericError
    }

    val launchResult = try {
      connection.withConnection("launch") {
        withContext(Dispatchers.Main) {
          connection.client.launchBillingFlow(activity, billingFlowParams)
        }
      }
    } catch (e: BillingError) {
      Log.w(TAG, "[launch] Failed to launch. Error code: ${e.billingResponseCode}", e, true)
      clearPendingFlow(deferred)
      return e.toPurchaseResult()
    }

    // Google Play only calls the listener when the sheet actually opened, so anything else has to resolve here or we
    // would await a result that is never delivered.
    if (launchResult.responseCode != BillingResponseCode.OK) {
      Log.w(TAG, "[launch] The purchase sheet did not open. Response code: ${launchResult.responseCode}", true)
      clearPendingFlow(deferred)
      return BillingError(launchResult.responseCode).toPurchaseResult()
    }

    return try {
      deferred.await()
    } finally {
      clearPendingFlow(deferred)
    }
  }

  /** Clears [pendingFlow] only if it still holds [deferred], so we never drop a newer flow's listener. */
  private fun clearPendingFlow(deferred: CompletableDeferred<OneTimePurchaseResult>) {
    pendingFlow.updateAndGet { if (it?.deferred === deferred) null else it }
  }

  override fun close() {
    if (connectionLazy.isInitialized()) {
      Log.i(TAG, "[close] Releasing the billing connection.", true)
      connection.close()
    }
  }

  override suspend fun consumePurchase(purchaseToken: String): Boolean = withContext(Dispatchers.IO) {
    val params = ConsumeParams.newBuilder()
      .setPurchaseToken(purchaseToken)
      .build()

    val result = try {
      connection.withConnection("consumePurchase") {
        connection.client.consumePurchase(params)
      }
    } catch (e: BillingError) {
      Log.w(TAG, "[consumePurchase] Failed to consume. Error code: ${e.billingResponseCode}", e, true)
      return@withContext false
    }

    val success = result.billingResult.responseCode == BillingResponseCode.OK
    if (!success) {
      Log.w(TAG, "[consumePurchase] Google Play rejected the consumption. Response code: ${result.billingResult.responseCode}", true)
    }

    success
  }

  /**
   * The most recent unconsumed purchase of [product], or a success holding null if there isn't one.
   */
  private suspend fun queryUnconsumedPurchaseInternal(product: OneTimeProductId): QueryResult<OneTimePurchase?> {
    val params = QueryPurchasesParams.newBuilder()
      .setProductType(ProductType.INAPP)
      .build()

    val result = try {
      connection.withConnection("queryUnconsumedPurchase") {
        connection.client.queryPurchasesAsync(params)
      }
    } catch (e: BillingError) {
      return QueryResult.Failure(e)
    }

    if (result.billingResult.responseCode != BillingResponseCode.OK) {
      return QueryResult.Failure(BillingError(result.billingResult.responseCode))
    }

    return QueryResult.Success(
      result.purchasesList
        .filter { product.productId in it.products }
        .maxByOrNull { it.purchaseTime }
        ?.toOneTimePurchase()
    )
  }

  /**
   * Details for the specific purchase option we sell, or a success holding null if Google Play doesn't offer it.
   */
  private suspend fun queryOfferDetails(product: OneTimeProductId): QueryResult<Offer?> {
    val params = QueryProductDetailsParams.newBuilder()
      .setProductList(
        listOf(
          QueryProductDetailsParams.Product.newBuilder()
            .setProductId(product.productId)
            .setProductType(ProductType.INAPP)
            .build()
        )
      )
      .build()

    val result = try {
      connection.withConnection("queryOfferDetails") {
        connection.client.queryProductDetails(params)
      }
    } catch (e: BillingError) {
      return QueryResult.Failure(e)
    }

    if (result.billingResult.responseCode != BillingResponseCode.OK) {
      return QueryResult.Failure(BillingError(result.billingResult.responseCode))
    }

    val details = result.productDetailsList?.firstOrNull { it.productId == product.productId } ?: return QueryResult.Success(null)
    val offer = details.oneTimePurchaseOfferDetailsList?.firstOrNull { it.purchaseOptionId == product.purchaseOptionId } ?: return QueryResult.Success(null)
    val offerToken = offer.offerToken ?: return QueryResult.Success(null)
    val formattedPrice = offer.formattedPrice ?: return QueryResult.Success(null)

    return QueryResult.Success(Offer(productDetails = details, offerToken = offerToken, formattedPrice = formattedPrice))
  }

  private fun toPurchaseResult(product: OneTimeProductId, billingResult: BillingResult, purchases: List<Purchase>?): OneTimePurchaseResult {
    return when (billingResult.responseCode) {
      BillingResponseCode.OK -> {
        val purchase = purchases
          ?.filter { product.productId in it.products }
          ?.maxByOrNull { it.purchaseTime }

        if (purchase == null) {
          Log.w(TAG, "[toPurchaseResult] Reported success, but there was no purchase of our product.", true)
          OneTimePurchaseResult.GenericError
        } else {
          Log.i(TAG, "[toPurchaseResult] Purchase completed. State: ${purchase.purchaseState}", true)
          OneTimePurchaseResult.Success(purchase.toOneTimePurchase())
        }
      }

      BillingResponseCode.USER_CANCELED -> OneTimePurchaseResult.UserCancelled
      BillingResponseCode.NETWORK_ERROR -> OneTimePurchaseResult.NetworkError

      BillingResponseCode.BILLING_UNAVAILABLE,
      BillingResponseCode.FEATURE_NOT_SUPPORTED,
      BillingResponseCode.ITEM_UNAVAILABLE -> OneTimePurchaseResult.Unavailable

      BillingResponseCode.SERVICE_DISCONNECTED,
      BillingResponseCode.SERVICE_UNAVAILABLE -> OneTimePurchaseResult.NetworkError

      else -> {
        Log.w(TAG, "[toPurchaseResult] Unhandled response code: ${billingResult.responseCode}", true)
        OneTimePurchaseResult.GenericError
      }
    }
  }

  private fun BillingError.toPurchaseResult(): OneTimePurchaseResult {
    return when (billingResponseCode) {
      BillingResponseCode.NETWORK_ERROR,
      BillingResponseCode.SERVICE_DISCONNECTED,
      BillingResponseCode.SERVICE_UNAVAILABLE -> OneTimePurchaseResult.NetworkError

      BillingResponseCode.BILLING_UNAVAILABLE,
      BillingResponseCode.FEATURE_NOT_SUPPORTED,
      BillingResponseCode.ITEM_UNAVAILABLE -> OneTimePurchaseResult.Unavailable

      else -> OneTimePurchaseResult.GenericError
    }
  }

  private fun BillingError.toProductResult(): OneTimeProductResult {
    return when (billingResponseCode) {
      BillingResponseCode.BILLING_UNAVAILABLE,
      BillingResponseCode.FEATURE_NOT_SUPPORTED,
      BillingResponseCode.ITEM_UNAVAILABLE -> OneTimeProductResult.Unavailable

      // Anything else -- network trouble, a disconnected service, an unrecognized code -- is worth another attempt.
      else -> OneTimeProductResult.TransientError
    }
  }

  private fun BillingError.toPreparation(): OneTimePurchasePreparation {
    return when (billingResponseCode) {
      BillingResponseCode.NETWORK_ERROR,
      BillingResponseCode.SERVICE_DISCONNECTED,
      BillingResponseCode.SERVICE_UNAVAILABLE -> OneTimePurchasePreparation.NetworkError

      BillingResponseCode.BILLING_UNAVAILABLE,
      BillingResponseCode.FEATURE_NOT_SUPPORTED,
      BillingResponseCode.ITEM_UNAVAILABLE -> OneTimePurchasePreparation.Unavailable

      else -> OneTimePurchasePreparation.GenericError
    }
  }

  private fun Purchase.toOneTimePurchase(): OneTimePurchase {
    return OneTimePurchase(
      purchaseToken = purchaseToken,
      state = when (purchaseState) {
        Purchase.PurchaseState.PURCHASED -> BillingPurchaseState.PURCHASED
        Purchase.PurchaseState.PENDING -> BillingPurchaseState.PENDING
        else -> BillingPurchaseState.UNSPECIFIED
      }
    )
  }

  /** Outcome of a Play query that either produced a value or failed with a [BillingError]. */
  private sealed interface QueryResult<out T> {
    data class Success<out T>(val value: T) : QueryResult<T>
    data class Failure(val error: BillingError) : QueryResult<Nothing>
  }

  private class PendingFlow(
    val product: OneTimeProductId,
    val deferred: CompletableDeferred<OneTimePurchaseResult>
  )

  private class Offer(
    val productDetails: ProductDetails,
    val offerToken: String,
    val formattedPrice: String
  )
}
