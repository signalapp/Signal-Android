/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.util

import org.signal.donations.StripePaths

/**
 * Stripe paths should be prefixed with 'stripe/' in order to access the proper namespacing in
 * the mock server. This object serves as a convenience delegate to StripePaths.
 */
object TestStripePaths {
  /**
   * @see StripePaths.getPaymentIntentPath
   */
  fun getPaymentIntentPath(paymentIntentId: String, clientSecret: String): String {
    return withNamespace(StripePaths.getPaymentIntentPath(paymentIntentId, clientSecret))
  }

  /**
   * @see StripePaths.getPaymentIntentConfirmationPath
   */
  fun getPaymentIntentConfirmationPath(paymentIntentId: String): String {
    return withNamespace(StripePaths.getPaymentIntentConfirmationPath(paymentIntentId))
  }

  /**
   * @see StripePaths.getSetupIntentPath
   */
  fun getSetupIntentPath(setupIntentId: String, clientSecret: String): String {
    return withNamespace(StripePaths.getSetupIntentPath(setupIntentId, clientSecret))
  }

  /**
   * @see StripePaths.getSetupIntentConfirmationPath
   */
  fun getSetupIntentConfirmationPath(setupIntentId: String): String {
    return withNamespace(StripePaths.getSetupIntentConfirmationPath(setupIntentId))
  }

  /**
   * @see StripePaths.getPaymentIntentPath
   */
  fun getPaymentMethodsPath(): String {
    return withNamespace(StripePaths.getPaymentMethodsPath())
  }

  /**
   * @see StripePaths.getTokensPath
   */
  fun getTokensPath(): String {
    return withNamespace(StripePaths.getTokensPath())
  }

  private fun withNamespace(path: String) = "stripe/$path"
}
