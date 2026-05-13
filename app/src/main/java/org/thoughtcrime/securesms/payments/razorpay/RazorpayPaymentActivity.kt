package org.thoughtcrime.securesms.payments.razorpay

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R

/**
 * Activity that handles Razorpay payment checkout.
 * Communicates with Razorpay's payment gateway and returns results to the caller.
 */
class RazorpayPaymentActivity : AppCompatActivity() {

  companion object {
    private val TAG = Log.tag(RazorpayPaymentActivity::class.java)

    const val EXTRA_PLAN_ID = "plan_id"
    const val EXTRA_CUSTOMER_ID = "customer_id"
    const val EXTRA_AMOUNT = "amount"
    const val EXTRA_PLAN_NAME = "plan_name"
    const val EXTRA_PAYMENT_ID = "payment_id"
    const val EXTRA_ORDER_ID = "order_id"
    const val EXTRA_SIGNATURE = "signature"
  }

  private lateinit var apiKeyManager: ApiKeyManager
  private var planId: String = ""
  private var customerId: String = ""
  private var amountInPaisa: Int = 0
  private var planName: String = ""
  private var orderId: String = ""

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    apiKeyManager = ApiKeyManager(this)

    // Extract payment parameters from intent
    planId = intent.getStringExtra(EXTRA_PLAN_ID) ?: ""
    customerId = intent.getStringExtra(EXTRA_CUSTOMER_ID) ?: ""
    amountInPaisa = intent.getIntExtra(EXTRA_AMOUNT, 0)
    planName = intent.getStringExtra(EXTRA_PLAN_NAME) ?: ""

    Log.d(TAG, "Razorpay payment activity created - Plan: $planName, Amount: $amountInPaisa paisa")

    if (planId.isEmpty() || customerId.isEmpty() || amountInPaisa == 0) {
      Log.e(TAG, "Invalid payment parameters")
      setResult(RESULT_CANCELED)
      finish()
      return
    }

    // Initialize payment flow
    lifecycleScope.launch {
      initializePayment()
    }
  }

  /**
   * Initialize and launch Razorpay payment checkout
   */
  private suspend fun initializePayment() {
    withContext(Dispatchers.IO) {
      try {
        // Verify API keys are configured
        if (!apiKeyManager.hasApiKeys()) {
          Log.e(TAG, "Razorpay API keys not configured")
          showError("Payment gateway not configured")
          return@withContext
        }

        // Create Razorpay order
        val plan = RazorpayConfig.getPlanById(planId)
        if (plan == null) {
          Log.e(TAG, "Invalid plan: $planId")
          showError("Invalid payment plan")
          return@withContext
        }

        orderId = "order_${System.currentTimeMillis()}_$customerId"
        Log.d(TAG, "Created order: $orderId")

        // Launch Razorpay checkout
        withContext(Dispatchers.Main) {
          launchRazorpayCheckout()
        }
      } catch (e: Exception) {
        Log.e(TAG, "Error initializing payment", e)
        withContext(Dispatchers.Main) {
          showError("Failed to initialize payment")
        }
      }
    }
  }

  /**
   * Launch Razorpay checkout interface
   * This would integrate with Razorpay SDK (com.razorpay:checkout)
   */
  private fun launchRazorpayCheckout() {
    try {
      Log.d(TAG, "Launching Razorpay checkout for order: $orderId")

      // In production, this would use:
      // val checkout = Checkout()
      // checkout.setKeyID(apiKeyManager.getApiKey())
      // val options = JSONObject()
      // options.put("image", R.drawable.ic_launcher_foreground)
      // options.put("title", "Signal - $planName")
      // options.put("description", "Subscription to $planName plan")
      // options.put("order_id", orderId)
      // options.put("amount", amountInPaisa)
      // options.put("currency", RazorpayConfig.Currency.CODE)
      // options.put("prefill.email", customerId)
      // checkout.open(this, options)

      // For now, simulate successful payment (for testing)
      simulatePaymentSuccess()

    } catch (e: Exception) {
      Log.e(TAG, "Error launching Razorpay checkout", e)
      showError("Failed to launch payment")
    }
  }

  /**
   * Simulate successful payment (for development/testing)
   * This should be replaced with actual Razorpay callback
   */
  private fun simulatePaymentSuccess() {
    Log.d(TAG, "Simulating successful payment")
    val paymentId = "pay_${System.currentTimeMillis()}"
    val signature = "signature_${System.currentTimeMillis()}"

    returnPaymentResult(
      paymentId = paymentId,
      orderId = orderId,
      signature = signature
    )
  }

  /**
   * Return payment result to caller
   */
  private fun returnPaymentResult(
    paymentId: String,
    orderId: String,
    signature: String
  ) {
    Log.d(TAG, "Payment successful - Payment ID: $paymentId, Order ID: $orderId")
    
    val resultIntent = Intent().apply {
      putExtra(EXTRA_PAYMENT_ID, paymentId)
      putExtra(EXTRA_ORDER_ID, orderId)
      putExtra(EXTRA_SIGNATURE, signature)
    }

    setResult(RESULT_OK, resultIntent)
    finish()
  }

  /**
   * Handle payment errors
   */
  private fun showError(message: String) {
    Log.e(TAG, "Payment error: $message")
    Toast.makeText(this, "Payment Error: $message", Toast.LENGTH_LONG).show()
    
    setResult(RESULT_CANCELED)
    finish()
  }

  /**
   * Handle back button press
   */
  override fun onBackPressed() {
    Log.d(TAG, "User pressed back - cancelling payment")
    setResult(RESULT_CANCELED)
    finish()
  }
}
