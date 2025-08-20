package org.thoughtcrime.securesms.messagerequests

import org.thoughtcrime.securesms.recipients.Recipient

/**
 * Group info needed to show message request state UX.
 */
class GroupInfo(
  val fullMemberCount: Int = 0,
  val pendingMemberCount: Int = 0,
  val description: String = "",
  val hasExistingContacts: Boolean = false,
  val membersPreview: List<Recipient> = emptyList()
) {
  companion object {
    @JvmField
    val ZERO = GroupInfo()
  }
}
