/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.receipts.detail

/**
 * Reminder that these events are logged, so don't include anything sensitive in the toString.
 */
sealed interface DonationReceiptDetailEvent {

  /** The user tapped the navigation (back) icon. */
  data object NavigateBackClicked : DonationReceiptDetailEvent

  /** The user tapped the share receipt button. */
  data object ShareClicked : DonationReceiptDetailEvent
}
