package org.thoughtcrime.securesms.components.settings.app.subscription.receipts.list

import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.database.model.InAppPaymentReceiptRecord

data class DonationReceiptBadge(
  val type: InAppPaymentReceiptRecord.Type,
  val level: Int,
  val badge: Badge
)
