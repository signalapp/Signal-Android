package org.thoughtcrime.securesms.messagerequests

/**
 * Group info needed to show message request state UX.
 */
class GroupInfo(
  val fullMemberCount: Int = 0,
  val pendingMemberCount: Int = 0,
  val description: String = "",
  val hasExistingContacts: Boolean = false
) {
  companion object {
    @JvmField
    val ZERO = GroupInfo()
  }
}
