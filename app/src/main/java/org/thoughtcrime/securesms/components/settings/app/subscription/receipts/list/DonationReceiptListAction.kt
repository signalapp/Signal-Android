/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.receipts.list

/**
 * One-shot side effects that need an Activity or the nav graph, and therefore have to be carried out
 * by the host rather than the screen itself.
 *
 * Actions are logged, so be sure `toString()` contains nothing sensitive.
 */
sealed interface DonationReceiptListAction {

  /** Leave the screen. */
  data object NavigateBack : DonationReceiptListAction

  /** Show the receipt with id [receiptId]. */
  data class NavigateToReceipt(val receiptId: Long) : DonationReceiptListAction
}
