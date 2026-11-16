/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration

import org.signal.core.util.billing.PurchaseLauncher

/**
 * Where [RegistrationRepository.startOrCompleteSignalLoginPurchase] left off.
 *
 * The purchase sheet needs an activity, which only the UI layer has, so the repository stops at the point of launch and
 * hands the launcher back rather than taking an activity of its own.
 */
sealed interface SignalLoginPurchaseStep {

  /**
   * The UI layer must launch [launcher], then pass the outcome to
   * [RegistrationRepository.completeSignalLoginPurchase].
   */
  data class LaunchRequired(val launcher: PurchaseLauncher) : SignalLoginPurchaseStep

  /** Nothing to launch. Either the purchase was already paid for and has now been redeemed, or it failed outright. */
  data class Finished(val result: SignalLoginPurchaseResult) : SignalLoginPurchaseStep
}
