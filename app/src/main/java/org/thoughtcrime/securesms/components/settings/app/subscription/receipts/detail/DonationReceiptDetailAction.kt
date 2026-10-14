/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.receipts.detail

import android.net.Uri

sealed interface DonationReceiptDetailAction {
  data class ShareReceipt(val uri: Uri) : DonationReceiptDetailAction
}
