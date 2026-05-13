package org.thoughtcrime.securesms.payments.razorpay

import android.app.Activity
import android.content.Context
import android.content.Intent
import org.signal.core.util.billing.BillingPurchaseResult
import org.signal.core.util.billing.BillingPurchaseState
import org.signal.core.util.logging.Log

/**
 * Handles Razorpay payment flow and result processing.
 * Manages payment initiation, result handling, and error scenarios.
 */
class RazorpayPaymentHandler(private val context: Context) {

  companion object {
    private val TAG = Log.tag(RazorpayPaymentHandler::class.java)
    const val REQUEST_CODE_RAZORPAY = 1001
  }

  /**
   * Initiates Razorpay payment for the specified plan
   */
  fun initiatePayment(
    activity: Activity,
    planId: String,
    customerId: String,
    onResult: (BillingPurchaseResult) -> Unit
  ) {
    try {
      val plan = RazorpayConfig.getPlanById(planId) ?: run {
        Log.e(TAG, "Plan not found: $planId")
        onResult(BillingPurchaseResult.GenericError)
        return
      }

      Log.d(TAG, "Initiating Razorpay payment for plan: ${plan.name}")

      // Create intent for Razorpay payment activity
      val intent = Intent(context, RazorpayPaymentActivity::class.java).apply {
        putExtra(RazorpayPaymentActivity.EXTRA_PLAN_ID, planId)
        putExtra(RazorpayPaymentActivity.EXTRA_CUSTOMER_ID, customerId)
        putExtra(RazorpayPaymentActivity.EXTRA_AMOUNT, plan.amountInPaisa)
        putExtra(RazorpayPaymentActivity.EXTRA_PLAN_NAME, plan.name)
      }

      activity.startActivityForResult(intent, REQUEST_CODE_RAZORPAY)
    } catch (e: Exception) {
      Log.e(TAG, "Error initiating Razorpay payment", e)
      onResult(BillingPurchaseResult.GenericError)
    }
  }

  /**
   * Processes activity result from Razorpay payment activity
   */
  fun handleActivityResult(
    requestCode: Int,
    resultCode: Int,
    data: Intent?
  ): BillingPurchaseResult {
    if (requestCode != REQUEST_CODE_RAZORPAY) {
      return BillingPurchaseResult.None
    }

    return when (resultCode) {
      Activity.RESULT_OK -> {
        Log.d(TAG, "Payment successful")
        data?.let { intent ->
          val paymentId = intent.getStringExtra(RazorpayPaymentActivity.EXTRA_PAYMENT_ID)
          val orderId = intent.getStringExtra(RazorpayPaymentActivity.EXTRA_ORDER_ID)
          val signature = intent.getStringExtra(RazorpayPaymentActivity.EXTRA_SIGNATURE)

          if (!paymentId.isNullOrEmpty() && !orderId.isNullOrEmpty()) {
            BillingPurchaseResult.Success(
              purchaseState = BillingPurchaseState.Purchased,
              purchaseToken = paymentId,
              isAcknowledged = true,
              purchaseTime = System.currentTimeMillis(),
              isAutoRenewing = true // Assuming subscription is auto-renewing
            )
          } else {
            Log.w(TAG, "Missing payment details in result")
            BillingPurchaseResult.GenericError
          }
        } ?: run {
          Log.w(TAG, "No data returned from payment activity")
          BillingPurchaseResult.GenericError
        }
      }

      Activity.RESULT_CANCELED -> {
        Log.d(TAG, "Payment cancelled by user")
        BillingPurchaseResult.UserCancelled
      }

      else -> {
        Log.w(TAG, "Unknown result code: $resultCode")
        BillingPurchaseResult.GenericError
      }
    }
  }

  /**
   * Retries a failed payment
   */
  fun retryPayment(
    activity: Activity,
    planId: String,
    customerId: String,
    onResult: (BillingPurchaseResult) -> Unit
  ) {
    Log.d(TAG, "Retrying payment for plan: $planId")
    initiatePayment(activity, planId, customerId, onResult)
  }

  /**
   * Cancels ongoing payment
   */
  fun cancelPayment() {
    Log.d(TAG, "Payment cancelled")
  }
}
