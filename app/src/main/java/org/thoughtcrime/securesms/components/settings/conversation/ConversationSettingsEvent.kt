package org.thoughtcrime.securesms.components.settings.conversation

import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.groups.ui.GroupChangeFailureReason
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId

sealed class ConversationSettingsEvent {
  class AddToAGroup(
    val recipientId: RecipientId,
    val groupMembership: List<RecipientId>
  ) : ConversationSettingsEvent()

  class AddMembersToGroup(
    val groupId: GroupId,
    val selectionWarning: Int,
    val selectionLimit: Int,
    val isAnnouncementGroup: Boolean,
    val groupMembersWithoutSelf: List<RecipientId>
  ) : ConversationSettingsEvent()

  object ShowGroupHardLimitDialog : ConversationSettingsEvent()

  class ShowAddMembersToGroupError(
    val failureReason: GroupChangeFailureReason
  ) : ConversationSettingsEvent()

  class ShowBlockGroupError(
    val failureReason: GroupChangeFailureReason
  ) : ConversationSettingsEvent()

  class ShowGroupInvitesSentDialog(
    val invitesSentTo: List<Recipient>
  ) : ConversationSettingsEvent()

  class ShowMembersAdded(
    val membersAddedCount: Int
  ) : ConversationSettingsEvent()
}
