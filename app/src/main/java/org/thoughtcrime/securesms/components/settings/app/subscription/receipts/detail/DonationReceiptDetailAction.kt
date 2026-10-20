/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.receipts.detail

import android.net.Uri

/**
 * One-shot side effects that need an Activity or the nav graph, and therefore have to be carried out
 * by the host rather than the screen itself.
 *
 * Actions are logged, so be sure `toString()` contains nothing sensitive.
 */
sealed interface DonationReceiptDetailAction {

  /** Leave the screen. */
  data object NavigateBack : DonationReceiptDetailAction

  /** Open the share sheet for the rendered receipt image at [uri]. */
  data class ShareReceipt(val uri: Uri) : DonationReceiptDetailAction
}
