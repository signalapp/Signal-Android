package org.thoughtcrime.securesms.safety

import org.thoughtcrime.securesms.database.IdentityTable

/**
 * Screen state for SafetyNumberBottomSheetFragment and SafetyNumberReviewConnectionsFragment
 */
data class SafetyNumberBottomSheetState(
  val untrustedRecipientCount: Int,
  val hasLargeNumberOfUntrustedRecipients: Boolean,
  val destinationToRecipientMap: Map<SafetyNumberBucket, List<SafetyNumberRecipient>> = emptyMap(),
  val loadState: LoadState = LoadState.INIT
) {

  fun isEmpty(): Boolean {
    return !hasLargeNumberOfUntrustedRecipients && destinationToRecipientMap.values.flatten().isEmpty() && loadState == LoadState.READY
  }

  fun isCheckupComplete(): Boolean {
    return loadState == LoadState.DONE ||
      isEmpty() ||
      destinationToRecipientMap.values.flatten().all { it.identityRecord.verifiedStatus == IdentityTable.VerifiedStatus.VERIFIED }
  }

  enum class LoadState {
    INIT,
    READY,
    DONE
  }
}
