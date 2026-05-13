package org.thoughtcrime.securesms.payments.razorpay

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R

/**
 * Helper class to integrate Razorpay payments into existing Signal payment flows.
 * Provides convenience methods for payment initiation and handling.
 */
object RazorpayPaymentIntegration {

  private val TAG = Log.tag(RazorpayPaymentIntegration::class.java)

  /**
   * Initialize Razorpay payment system
   * Call this during app startup to initialize components
   */
  fun initialize(context: Context) {
    try {
      Log.d(TAG, "Initializing Razorpay payment system")
      ApiKeyManager(context) // Initialize and validate
    } catch (e: Exception) {
      Log.e(TAG, "Error initializing Razorpay system", e)
    }
  }

  /**
   * Check if Razorpay API keys are configured
   */
  fun isConfigured(context: Context): Boolean {
    return try {
      ApiKeyManager(context).hasApiKeys()
    } catch (e: Exception) {
      Log.e(TAG, "Error checking configuration", e)
      false
    }
  }

  /**
   * Show API key configuration dialog
   */
  fun showApiKeySetup(fragmentManager: FragmentManager) {
    Log.d(TAG, "Showing API key setup dialog")
    try {
      RazorpayKeySetupFragment.newInstance().show(
        fragmentManager,
        "razorpay_key_setup"
      )
    } catch (e: Exception) {
      Log.e(TAG, "Error showing API key setup", e)
    }
  }

  /**
   * Initiate payment for a specific plan
   * Returns true if payment was initiated, false if API keys not configured
   */
  fun initiatePayment(
    activity: Activity,
    planId: String,
    customerId: String,
    onResult: (success: Boolean, message: String) -> Unit
  ): Boolean {
    return try {
      val apiKeyManager = ApiKeyManager(activity)
      
      if (!apiKeyManager.hasApiKeys()) {
        Log.w(TAG, "API keys not configured")
        onResult(false, "Payment system not configured. Please configure API keys.")
        return false
      }

      val plan = RazorpayConfig.getPlanById(planId)
      if (plan == null) {
        Log.e(TAG, "Invalid plan: $planId")
        onResult(false, "Invalid payment plan")
        return false
      }

      Log.d(TAG, "Initiating payment for plan: ${plan.name}")
      
      val paymentHandler = RazorpayPaymentHandler(activity)
      paymentHandler.initiatePayment(
        activity = activity,
        planId = planId,
        customerId = customerId
      ) { result ->
        val (success, message) = when (result) {
          is org.signal.core.util.billing.BillingPurchaseResult.Success -> {
            true to "Payment successful"
          }
          is org.signal.core.util.billing.BillingPurchaseResult.UserCancelled -> {
            false to "Payment cancelled"
          }
          is org.signal.core.util.billing.BillingPurchaseResult.GenericError -> {
            false to "Payment failed"
          }
          else -> false to "Unknown error"
        }
        onResult(success, message)
      }
      true
    } catch (e: Exception) {
      Log.e(TAG, "Error initiating payment", e)
      onResult(false, "Error: ${e.message}")
      false
    }
  }

  /**
   * Get all available payment plans
   */
  fun getAvailablePlans(): List<RazorpayConfig.PaymentPlan> {
    return RazorpayConfig.ALL_PLANS
  }

  /**
   * Get a specific plan by ID
   */
  fun getPlan(planId: String): RazorpayConfig.PaymentPlan? {
    return RazorpayConfig.getPlanById(planId)
  }

  /**
   * Format amount for display (₹99, ₹299, etc.)
   */
  fun formatAmount(amountInRupees: Int): String {
    return RazorpayConfig.formatAmountForDisplay(amountInRupees)
  }

  /**
   * Get the INR currency code
   */
  fun getCurrencyCode(): String {
    return RazorpayConfig.Currency.CODE
  }

  /**
   * Get the INR currency symbol
   */
  fun getCurrencySymbol(): String {
    return RazorpayConfig.Currency.SYMBOL
  }

  /**
   * Check if a specific plan is free
   */
  fun isPlanFree(plan: RazorpayConfig.PaymentPlan): Boolean {
    return plan.amountInRupees == 0
  }

  /**
   * Get plan features as formatted string
   */
  fun getPlanFeaturesAsText(plan: RazorpayConfig.PaymentPlan): String {
    return plan.features.joinToString("\n") { "• $it" }
  }

  /**
   * Validate Razorpay API key format
   */
  fun validateApiKeyFormat(key: String): Boolean {
    return RazorpayConfig.isValidApiKey(key)
  }

  /**
   * Log payment event for analytics
   */
  fun logPaymentEvent(
    planId: String,
    eventName: String,
    properties: Map<String, Any> = emptyMap()
  ) {
    Log.d(TAG, "Payment event: $eventName - Plan: $planId, Properties: $properties")
    // This could be extended to send to analytics service
  }

  /**
   * Handle payment activity result
   */
  fun handleActivityResult(
    activity: Activity,
    requestCode: Int,
    resultCode: Int,
    data: Intent?,
    onResult: (success: Boolean, paymentId: String?) -> Unit
  ) {
    try {
      val paymentHandler = RazorpayPaymentHandler(activity)
      val result = paymentHandler.handleActivityResult(requestCode, resultCode, data)
      
      when (result) {
        is org.signal.core.util.billing.BillingPurchaseResult.Success -> {
          Log.d(TAG, "Payment successful: ${result.purchaseToken}")
          onResult(true, result.purchaseToken)
        }
        else -> {
          Log.w(TAG, "Payment not successful: $result")
          onResult(false, null)
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error handling activity result", e)
      onResult(false, null)
    }
  }

  /**
   * Clear stored API keys (use with caution)
   */
  fun clearApiKeys(context: Context) {
    Log.w(TAG, "Clearing API keys")
    try {
      ApiKeyManager(context).clearApiKeys()
    } catch (e: Exception) {
      Log.e(TAG, "Error clearing API keys", e)
    }
  }
}
