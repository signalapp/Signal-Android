package org.thoughtcrime.securesms.safety

import org.thoughtcrime.securesms.recipients.RecipientId

/** User-driven events emitted by the safety number bottom sheet UI. */
sealed interface SafetyNumberBottomSheetEvent {
  /** The user confirmed they want to send despite safety number changes. */
  data object SendAnyway : SafetyNumberBottomSheetEvent

  /** The user opened the full review-connections screen. */
  data object ReviewConnections : SafetyNumberBottomSheetEvent

  /** The user requested to verify the safety number for [recipientId]. */
  data class VerifySafetyNumber(val recipientId: RecipientId) : SafetyNumberBottomSheetEvent

  /** The user removed [recipientId] from all selected distribution lists. */
  data class RemoveFromStory(val recipientId: RecipientId) : SafetyNumberBottomSheetEvent

  /** The user removed [recipientId] from the send destinations. */
  data class RemoveDestination(val recipientId: RecipientId) : SafetyNumberBottomSheetEvent

  /** The user removed all recipients from the given distribution list [bucket]. */
  data class RemoveAll(val bucket: SafetyNumberBucket.DistributionListBucket) : SafetyNumberBottomSheetEvent
}
