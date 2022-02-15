package org.thoughtcrime.securesms.components.settings.conversation

import org.thoughtcrime.securesms.groups.ui.GroupChangeFailureReason
import org.thoughtcrime.securesms.recipients.Recipient

sealed class GroupAddMembersResult {
  class Success(
    val numberOfMembersAdded: Int,
    val newMembersInvited: List<Recipient>
  ) : GroupAddMembersResult()

  class Failure(
    val reason: GroupChangeFailureReason
  ) : GroupAddMembersResult()
}
