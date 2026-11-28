/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.receipts.list

/**
 * Reminder that these events are logged, so don't include anything sensitive in the toString.
 */
sealed interface DonationReceiptListEvent {

  /** The user tapped the navigation (back) icon. */
  data object NavigateBackClicked : DonationReceiptListEvent

  /** The user tapped the receipt with id [receiptId]. */
  data class ReceiptClicked(val receiptId: Long) : DonationReceiptListEvent
}
