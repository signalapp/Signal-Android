/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util.billing

import android.app.Activity

/**
 * Everything needed to launch a one-time purchase, worked out ahead of the launch itself.
 *
 * Splitting preparation from launching keeps [Activity] out of [OneTimePurchaseApi]'s contract: only the UI layer,
 * which is the one place an activity legitimately lives, ever touches [PurchaseLauncher].
 */
sealed interface OneTimePurchasePreparation {

  /**
   * The user already owns an unconsumed purchase, so there is nothing to launch and nothing to charge. Redeem this
   * instead.
   */
  data class AlreadyOwned(val purchase: OneTimePurchase) : OneTimePurchasePreparation

  /** Ready to go. The UI layer hands [launcher] an activity to host the purchase sheet. */
  data class Ready(val launcher: PurchaseLauncher) : OneTimePurchasePreparation

  /** Google Play cannot sell the product here. Retrying won't help. */
  data object Unavailable : OneTimePurchasePreparation

  data object NetworkError : OneTimePurchasePreparation

  data object GenericError : OneTimePurchasePreparation
}

/**
 * Launches a prepared purchase sheet and suspends until it resolves.
 *
 * The activity is supplied here, at the moment of launch, rather than being held by the billing api or passed through
 * a screen event.
 */
fun interface PurchaseLauncher {
  suspend fun launch(activity: Activity): OneTimePurchaseResult
}
