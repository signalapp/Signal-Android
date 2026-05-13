package org.thoughtcrime.securesms.payments.razorpay

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.util.billing.BillingPurchaseResult
import org.signal.core.util.logging.Log

/**
 * ViewModel for managing Razorpay payment flow state and operations.
 * Handles payment initiation, result processing, and state management.
 */
class RazorpayPaymentFlowViewModel(
  private val context: Context,
  private val paymentHandler: RazorpayPaymentHandler,
  private val apiKeyManager: ApiKeyManager
) : ViewModel() {

  companion object {
    private val TAG = Log.tag(RazorpayPaymentFlowViewModel::class.java)
  }

  // Payment state flow
  private val _paymentState = MutableStateFlow<PaymentFlowState>(PaymentFlowState.Idle)
  val paymentState: StateFlow<PaymentFlowState> = _paymentState

  // API configuration state
  private val _apiConfigState = MutableStateFlow<ApiConfigState>(ApiConfigState.NotConfigured)
  val apiConfigState: StateFlow<ApiConfigState> = _apiConfigState

  // Selected plan
  private val _selectedPlan = MutableStateFlow<RazorpayConfig.PaymentPlan?>(null)
  val selectedPlan: StateFlow<RazorpayConfig.PaymentPlan?> = _selectedPlan

  // Payment result
  private val _paymentResult = MutableStateFlow<PaymentResult?>(null)
  val paymentResult: StateFlow<PaymentResult?> = _paymentResult

  init {
    checkApiConfiguration()
  }

  /**
   * Check if API keys are configured
   */
  private fun checkApiConfiguration() {
    viewModelScope.launch(Dispatchers.IO) {
      val configured = apiKeyManager.hasApiKeys()
      withContext(Dispatchers.Main) {
        _apiConfigState.value = if (configured) {
          ApiConfigState.Configured
        } else {
          ApiConfigState.NotConfigured
        }
      }
    }
  }

  /**
   * Select a payment plan
   */
  fun selectPlan(planId: String) {
    val plan = RazorpayConfig.getPlanById(planId)
    if (plan != null) {
      _selectedPlan.value = plan
      Log.d(TAG, "Plan selected: ${plan.name} (₹${plan.amountInRupees})")
    } else {
      Log.w(TAG, "Invalid plan ID: $planId")
    }
  }

  /**
   * Initiate payment for the selected plan
   */
  fun initiatePayment(
    activity: Activity,
    customerId: String
  ) {
    val plan = _selectedPlan.value
    if (plan == null) {
      Log.e(TAG, "No plan selected")
      _paymentState.value = PaymentFlowState.Error("No plan selected")
      return
    }

    if (_apiConfigState.value != ApiConfigState.Configured) {
      Log.e(TAG, "API not configured")
      _paymentState.value = PaymentFlowState.ApiKeyMissing
      return
    }

    _paymentState.value = PaymentFlowState.Processing

    paymentHandler.initiatePayment(
      activity = activity,
      planId = plan.id,
      customerId = customerId
    ) { result ->
      handlePaymentResult(result)
    }
  }

  /**
   * Handle payment result from RazorpayPaymentHandler
   */
  fun handleActivityResult(
    requestCode: Int,
    resultCode: Int,
    data: Intent?
  ) {
    val result = paymentHandler.handleActivityResult(requestCode, resultCode, data)
    handlePaymentResult(result)
  }

  /**
   * Process payment result and update state
   */
  private fun handlePaymentResult(result: BillingPurchaseResult) {
    viewModelScope.launch(Dispatchers.Main) {
      when (result) {
        is BillingPurchaseResult.Success -> {
          Log.d(TAG, "Payment successful: ${result.purchaseToken}")
          _paymentState.value = PaymentFlowState.Success
          _paymentResult.value = PaymentResult.Success(
            paymentId = result.purchaseToken,
            timestamp = result.purchaseTime
          )
        }

        is BillingPurchaseResult.UserCancelled -> {
          Log.d(TAG, "Payment cancelled by user")
          _paymentState.value = PaymentFlowState.Cancelled
        }

        is BillingPurchaseResult.NetworkError -> {
          Log.e(TAG, "Network error during payment")
          _paymentState.value = PaymentFlowState.Error("Network error. Please try again.")
        }

        is BillingPurchaseResult.BillingUnavailable -> {
          Log.e(TAG, "Billing unavailable")
          _paymentState.value = PaymentFlowState.Error("Payment service unavailable")
        }

        is BillingPurchaseResult.GenericError -> {
          Log.e(TAG, "Generic error during payment")
          _paymentState.value = PaymentFlowState.Error("Payment failed. Please try again.")
        }

        else -> {
          Log.w(TAG, "Unhandled payment result: $result")
          _paymentState.value = PaymentFlowState.Error("Unexpected error occurred")
        }
      }
    }
  }

  /**
   * Retry failed payment
   */
  fun retryPayment(
    activity: Activity,
    customerId: String
  ) {
    val plan = _selectedPlan.value
    if (plan == null) {
      Log.e(TAG, "No plan selected for retry")
      return
    }

    Log.d(TAG, "Retrying payment")
    _paymentState.value = PaymentFlowState.Processing

    paymentHandler.retryPayment(
      activity = activity,
      planId = plan.id,
      customerId = customerId
    ) { result ->
      handlePaymentResult(result)
    }
  }

  /**
   * Reset payment flow state
   */
  fun reset() {
    _paymentState.value = PaymentFlowState.Idle
    _selectedPlan.value = null
    _paymentResult.value = null
    Log.d(TAG, "Payment flow reset")
  }

  /**
   * Configure API keys
   */
  fun configureApiKeys(activity: Activity) {
    Log.d(TAG, "Opening API key configuration")
    // This would typically open the RazorpayKeySetupFragment
    // Implementation depends on the hosting Activity/Fragment
  }

  /**
   * Get all available plans
   */
  fun getAvailablePlans(): List<RazorpayConfig.PaymentPlan> {
    return RazorpayConfig.ALL_PLANS
  }

  /**
   * Get plan by ID
   */
  fun getPlanById(planId: String): RazorpayConfig.PaymentPlan? {
    return RazorpayConfig.getPlanById(planId)
  }

  /**
   * Format amount for display
   */
  fun formatAmount(amountInRupees: Int): String {
    return RazorpayConfig.formatAmountForDisplay(amountInRupees)
  }
}

/**
 * Factory for creating RazorpayPaymentFlowViewModel
 */
class RazorpayPaymentFlowViewModelFactory(
  private val context: Context
) : ViewModelProvider.Factory {
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    val paymentHandler = RazorpayPaymentHandler(context)
    val apiKeyManager = ApiKeyManager(context)
    return RazorpayPaymentFlowViewModel(context, paymentHandler, apiKeyManager) as T
  }
}

/**
 * UI State for payment flow
 */
sealed class PaymentFlowState {
  object Idle : PaymentFlowState()
  object Processing : PaymentFlowState()
  object Success : PaymentFlowState()
  object Cancelled : PaymentFlowState()
  object ApiKeyMissing : PaymentFlowState()
  data class Error(val message: String) : PaymentFlowState()
}

/**
 * API Configuration state
 */
sealed class ApiConfigState {
  object NotConfigured : ApiConfigState()
  object Configured : ApiConfigState()
}

/**
 * Payment result data
 */
sealed class PaymentResult {
  data class Success(
    val paymentId: String,
    val timestamp: Long
  ) : PaymentResult()

  data class Error(
    val message: String
  ) : PaymentResult()
}
