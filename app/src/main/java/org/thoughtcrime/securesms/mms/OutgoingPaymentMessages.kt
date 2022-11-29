package org.thoughtcrime.securesms.mms

import org.thoughtcrime.securesms.database.ThreadTable
import org.thoughtcrime.securesms.database.model.StoryType
import org.thoughtcrime.securesms.recipients.Recipient
import java.util.LinkedList

/**
 * Specialized message sent to request someone activate payments.
 */
class OutgoingRequestToActivatePaymentMessages(
  recipient: Recipient,
  sentTimeMillis: Long,
  expiresIn: Long
) : OutgoingSecureMediaMessage(
  recipient,
  "",
  LinkedList(),
  sentTimeMillis,
  ThreadTable.DistributionTypes.CONVERSATION,
  expiresIn,
  false,
  StoryType.NONE,
  null,
  false,
  null,
  emptyList(),
  emptyList(),
  emptyList(),
  null
) {
  override fun isRequestToActivatePayments(): Boolean = true
  override fun isUrgent(): Boolean = false
}

/**
 * Specialized message sent to indicate you activated payments. Intended to only
 * be sent to those that sent requests prior to activation.
 */
class OutgoingPaymentsActivatedMessages(
  recipient: Recipient,
  sentTimeMillis: Long,
  expiresIn: Long
) : OutgoingSecureMediaMessage(
  recipient,
  "",
  LinkedList(),
  sentTimeMillis,
  ThreadTable.DistributionTypes.CONVERSATION,
  expiresIn,
  false,
  StoryType.NONE,
  null,
  false,
  null,
  emptyList(),
  emptyList(),
  emptyList(),
  null
) {
  override fun isPaymentsActivated(): Boolean = true
  override fun isUrgent(): Boolean = false
}

class OutgoingPaymentsNotificationMessage(
  recipient: Recipient,
  paymentUuid: String,
  sentTimeMillis: Long,
  expiresIn: Long
) : OutgoingSecureMediaMessage(
  recipient,
  paymentUuid,
  LinkedList(),
  sentTimeMillis,
  ThreadTable.DistributionTypes.CONVERSATION,
  expiresIn,
  false,
  StoryType.NONE,
  null,
  false,
  null,
  emptyList(),
  emptyList(),
  emptyList(),
  null
) {
  override fun isPaymentsNotification(): Boolean = true
}
