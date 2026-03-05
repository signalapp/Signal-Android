/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.donations

/**
 * Endpoint generation class that assists in ensuring test code utilizes the same
 * paths for data access as production code.
 */
object StripePaths {

  /**
   * Endpoint to retrieve data on the given payment intent
   */
  fun getPaymentIntentPath(paymentIntentId: String, clientSecret: String): String {
    return "payment_intents/$paymentIntentId?client_secret=$clientSecret"
  }

  /**
   * Endpoint to confirm the given payment intent
   */
  fun getPaymentIntentConfirmationPath(paymentIntentId: String): String {
    return "payment_intents/$paymentIntentId/confirm"
  }

  /**
   * Endpoint to retrieve data on the given setup intent
   */
  fun getSetupIntentPath(setupIntentId: String, clientSecret: String): String {
    return "setup_intents/$setupIntentId?client_secret=$clientSecret&expand[0]=latest_attempt"
  }

  /**
   * Endpoint to confirm the given setup intent
   */
  fun getSetupIntentConfirmationPath(setupIntentId: String): String {
    return "setup_intents/$setupIntentId/confirm"
  }

  /**
   * Endpoint to interact with payment methods
   */
  fun getPaymentMethodsPath() = "payment_methods"

  /**
   * Endpoint to interact with tokens
   */
  fun getTokensPath() = "tokens"
}
