/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util.billing

/**
 * Outcome of [OneTimePurchaseApi.queryProduct].
 */
sealed interface OneTimeProductResult {
  data class Success(val product: OneTimeProduct) : OneTimeProductResult

  /** Google Play cannot sell the product here. Either not a Play build, or the product isn't published. Retrying won't help. */
  data object Unavailable : OneTimeProductResult

  /** A transient failure, e.g. no network or the billing service was unreachable. Retrying may work. */
  data object TransientError : OneTimeProductResult
}
