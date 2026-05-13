package org.thoughtcrime.securesms.payments.razorpay

import org.json.JSONObject

/**
 * Configuration for Razorpay payment system.
 * All amounts are in Indian Rupees (INR).
 */
object RazorpayConfig {

  /**
   * Payment plan tier definitions.
   * All amounts stored in rupees (not paisa for display).
   * Razorpay API expects amounts in paisa (multiply by 100).
   */
  data class PaymentPlan(
    val id: String,
    val name: String,
    val description: String,
    val amountInRupees: Int,
    val amountInPaisa: Int = amountInRupees * 100,
    val features: List<String> = emptyList()
  )

  // Plan definitions in INR
  val PLAN_FREE = PaymentPlan(
    id = "free",
    name = "Free",
    description = "Basic messaging",
    amountInRupees = 0,
    features = listOf(
      "Unlimited messages",
      "End-to-end encryption",
      "Group chats"
    )
  )

  val PLAN_BASIC = PaymentPlan(
    id = "basic",
    name = "Basic",
    description = "Enhanced features",
    amountInRupees = 99,
    features = listOf(
      "All Free features",
      "Encrypted backups",
      "500MB backup storage"
    )
  )

  val PLAN_PRO = PaymentPlan(
    id = "pro",
    name = "Pro",
    description = "Professional tier",
    amountInRupees = 299,
    features = listOf(
      "All Basic features",
      "2GB backup storage",
      "Priority support"
    )
  )

  val PLAN_PREMIUM = PaymentPlan(
    id = "premium",
    name = "Premium",
    description = "All features included",
    amountInRupees = 599,
    features = listOf(
      "All Pro features",
      "10GB backup storage",
      "Advanced privacy settings",
      "Premium support"
    )
  )

  val ALL_PLANS = listOf(PLAN_FREE, PLAN_BASIC, PLAN_PRO, PLAN_PREMIUM)

  /**
   * Currency configuration
   */
  object Currency {
    const val CODE = "INR"
    const val SYMBOL = "₹"
    const val DISPLAY_NAME = "Indian Rupee"
  }

  /**
   * Razorpay payment gateway constants
   */
  object Gateway {
    const val TIMEOUT_MS = 30000 // 30 seconds
    const val MAX_RETRIES = 3
    const val API_VERSION = "2024-01-01"
    
    // Payment methods supported by Razorpay in India
    val SUPPORTED_METHODS = listOf(
      "card",           // Credit/Debit cards
      "netbanking",     // Net banking
      "wallet",         // Wallets (Paytm, etc.)
      "upi"             // UPI payments
    )
  }

  /**
   * Validation constants
   */
  object Validation {
    const val MIN_API_KEY_LENGTH = 32
    const val MAX_API_KEY_LENGTH = 64
    const val API_KEY_PATTERN = "^[a-zA-Z0-9_-]{32,64}$"
  }

  /**
   * API Key format validation
   */
  fun isValidApiKey(key: String): Boolean {
    if (key.length < Validation.MIN_API_KEY_LENGTH || 
        key.length > Validation.MAX_API_KEY_LENGTH) {
      return false
    }
    return key.matches(Regex(Validation.API_KEY_PATTERN))
  }

  /**
   * Get plan by ID
   */
  fun getPlanById(planId: String): PaymentPlan? {
    return ALL_PLANS.find { it.id == planId }
  }

  /**
   * Format amount for display (INR with symbol)
   */
  fun formatAmountForDisplay(amountInRupees: Int): String {
    return "${Currency.SYMBOL}$amountInRupees"
  }

  /**
   * Create order payload for Razorpay API
   */
  fun createOrderPayload(
    planId: String,
    customerId: String,
    description: String = "Payment for Signal subscription"
  ): JSONObject {
    val plan = getPlanById(planId) ?: return JSONObject()
    
    return JSONObject().apply {
      put("amount", plan.amountInPaisa)
      put("currency", Currency.CODE)
      put("receipt", "order_$customerId")
      put("description", description)
      put("customer_id", customerId)
    }
  }
}
