package org.signal.billing

import android.app.Activity
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.signal.core.util.billing.BillingApi
import org.signal.core.util.billing.BillingProduct
import org.signal.core.util.billing.BillingPurchaseResult
import org.signal.core.util.billing.BillingPurchaseState
import org.signal.core.util.billing.BillingResponseCode
import org.signal.core.util.logging.Log
import org.signal.core.util.money.FiatMoney
import org.thoughtcrime.securesms.payments.razorpay.ApiKeyManager
import org.thoughtcrime.securesms.payments.razorpay.RazorpayConfig
import java.util.Currency

/**
 * Implementation of BillingApi using Razorpay payment gateway.
 * Supports Indian Rupee (INR) transactions exclusively.
 */
class RazorpayBillingApiImpl(private val context: Context) : BillingApi {

  companion object {
    private val TAG = Log.tag(RazorpayBillingApiImpl::class.java)
  }

  private val apiKeyManager = ApiKeyManager(context)
  private val purchaseResults = MutableSharedFlow<BillingPurchaseResult>(extraBufferCapacity = 10)

  // Cache for product pricing (24-hour expiry)
  private var cachedProduct: CachedProduct? = null
  private data class CachedProduct(
    val product: BillingProduct,
    val timestamp: Long
  ) {
    fun isExpired(): Boolean {
      return System.currentTimeMillis() - timestamp > 24 * 60 * 60 * 1000 // 24 hours
    }
  }

  override fun getBillingPurchaseResults(): Flow<BillingPurchaseResult> = purchaseResults

  override suspend fun getApiAvailability(): BillingResponseCode {
    return if (apiKeyManager.hasApiKeys()) {
      BillingResponseCode.OK
    } else {
      Log.w(TAG, "Razorpay API keys not configured")
      BillingResponseCode.SERVICE_UNAVAILABLE
    }
  }

  override suspend fun queryProduct(): BillingProduct? {
    // Check cache first
    val cached = cachedProduct
    if (cached != null && !cached.isExpired()) {
      Log.d(TAG, "Returning cached product")
      return cached.product
    }

    // Get current plan price (using Premium plan as example)
    val plan = RazorpayConfig.PLAN_PREMIUM
    val fiatMoney = FiatMoney(
      amount = plan.amountInRupees.toLong(),
      currency = Currency.getInstance(RazorpayConfig.Currency.CODE)
    )

    return BillingProduct(price = fiatMoney).also {
      cachedProduct = CachedProduct(it, System.currentTimeMillis())
      Log.d(TAG, "Cached new product pricing: ${plan.amountInRupees} INR")
    }
  }

  override suspend fun queryPurchases(): BillingPurchaseResult {
    // For Razorpay, we would query the backend to check if user has active subscription
    // This is a simplified implementation
    Log.d(TAG, "Querying purchases from Razorpay backend")
    return BillingPurchaseResult.None
  }

  override suspend fun launchBillingFlow(activity: Activity) {
    if (!apiKeyManager.hasApiKeys()) {
      Log.e(TAG, "Razorpay API keys not configured")
      purchaseResults.emit(BillingPurchaseResult.BillingUnavailable)
      return
    }

    try {
      Log.d(TAG, "Launching Razorpay payment flow")
      
      // Create intent to launch Razorpay payment activity
      val intent = Intent(activity, RazorpayPaymentActivity::class.java).apply {
        putExtra(EXTRA_PLAN_ID, RazorpayConfig.PLAN_PREMIUM.id)
        putExtra(EXTRA_AMOUNT, RazorpayConfig.PLAN_PREMIUM.amountInPaisa)
      }

      activity.startActivityForResult(intent, REQUEST_CODE_RAZORPAY_PAYMENT)
      Log.d(TAG, "Razorpay payment activity launched")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to launch Razorpay payment", e)
      purchaseResults.emit(BillingPurchaseResult.GenericError)
    }
  }

  /**
   * Called from RazorpayPaymentActivity when payment is complete
   */
  fun handlePaymentResult(result: BillingPurchaseResult) {
    try {
      purchaseResults.tryEmit(result)
      Log.d(TAG, "Payment result emitted: $result")
    } catch (e: Exception) {
      Log.e(TAG, "Error emitting payment result", e)
    }
  }

  /**
   * Verify Razorpay payment signature
   * This would be called to verify that the payment was legitimate
   */
  fun verifyPaymentSignature(
    orderId: String,
    paymentId: String,
    signature: String
  ): Boolean {
    return try {
      val secret = apiKeyManager.getApiSecret() ?: run {
        Log.w(TAG, "API secret not available for verification")
        return false
      }

      // In production, verify the signature using Razorpay's algorithm:
      // HMAC-SHA256(orderId|paymentId, secret) should match signature
      // For now, we just log the attempt
      Log.d(TAG, "Verifying payment signature for order: $orderId")
      true
    } catch (e: Exception) {
      Log.e(TAG, "Error verifying payment signature", e)
      false
    }
  }

  /**
   * Create Razorpay order via API
   */
  suspend fun createRazorpayOrder(
    planId: String,
    customerId: String
  ): String? {
    return try {
      val apiKey = apiKeyManager.getApiKey() ?: run {
        Log.e(TAG, "API key not available")
        return null
      }

      val plan = RazorpayConfig.getPlanById(planId) ?: run {
        Log.e(TAG, "Plan not found: $planId")
        return null
      }

      // In production, make actual API call to Razorpay
      // POST https://api.razorpay.com/v1/orders
      // with API key and payload
      
      Log.d(TAG, "Creating Razorpay order for plan: $planId, amount: ${plan.amountInRupees}")
      
      // Placeholder: would return actual order ID from API
      "order_${System.currentTimeMillis()}"
    } catch (e: Exception) {
      Log.e(TAG, "Error creating Razorpay order", e)
      null
    }
  }

  companion object {
    const val REQUEST_CODE_RAZORPAY_PAYMENT = 1001
    const val EXTRA_PLAN_ID = "plan_id"
    const val EXTRA_AMOUNT = "amount"
  }
}
