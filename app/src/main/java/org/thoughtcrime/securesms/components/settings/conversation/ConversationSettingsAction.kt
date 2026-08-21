/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.conversation

import androidx.compose.ui.unit.IntRect
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.database.MediaTable
import org.thoughtcrime.securesms.database.model.IdentityRecord
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.groups.SelectionLimits
import org.thoughtcrime.securesms.groups.ui.GroupChangeFailureReason
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * One-shot side effects that need an Activity, FragmentManager, or the legacy nav graph, and therefore have to be
 * carried out by [ConversationSettingsFragment] rather than the screen itself.
 *
 * Actions are logged, so be sure `toString()` contains nothing sensitive.
 */
sealed interface ConversationSettingsAction {

  /** Open the full-screen preview of the recipient's avatar. */
  data class ShowAvatarPreview(val recipientId: RecipientId) : ConversationSettingsAction

  /** Ask whether to view the recipient's story or their avatar, since they have both. */
  data class ShowStoryOrAvatarDialog(val recipientId: RecipientId, val isInHiddenStoryMode: Boolean) : ConversationSettingsAction

  /** Open the sheet describing the badge the user tapped. */
  data class ShowBadgeSheet(val recipientId: RecipientId, val badge: Badge) : ConversationSettingsAction {
    override fun toString(): String = "ShowBadgeSheet($recipientId)"
  }

  /** Open the sheet describing the recipient. */
  data class ShowAboutSheet(val recipient: Recipient) : ConversationSettingsAction {
    override fun toString(): String = "ShowAboutSheet(${recipient.id})"
  }

  /** Open the flow for editing the group's name, avatar, and description. */
  data class EditGroupProfile(val groupId: GroupId) : ConversationSettingsAction

  /** Open the flow for editing just the group's description. */
  data class EditGroupDescription(val groupId: GroupId) : ConversationSettingsAction

  /** Show the group's full description in a dialog, since it was too long to fit in the header. */
  data class ShowGroupDescriptionDialog(val groupId: GroupId, val shouldLinkify: Boolean) : ConversationSettingsAction

  /** Open the support page explaining Signal groups. */
  data object ShowGroupsLearnMore : ConversationSettingsAction

  /** Open the share sheet for inviting friends to Signal. */
  data object ShowInviteFriends : ConversationSettingsAction

  /** Open the internal details screen, which only internal users ever see. */
  data class NavigateToInternalDetails(val recipientId: RecipientId) : ConversationSettingsAction

  /** Open the conversation, optionally with search already running. */
  data class OpenConversation(val recipientId: RecipientId, val threadId: Long, val withSearchOpen: Boolean = false) : ConversationSettingsAction

  /** Open the flow for adding to this group's story. */
  data class AddToGroupStory(val recipientId: RecipientId) : ConversationSettingsAction

  /** Start a video call with the recipient. */
  data class StartVideoCall(val recipient: Recipient) : ConversationSettingsAction {
    override fun toString(): String = "StartVideoCall(${recipient.id})"
  }

  /** Start an audio call with the recipient. */
  data class StartAudioCall(val recipient: Recipient) : ConversationSettingsAction {
    override fun toString(): String = "StartAudioCall(${recipient.id})"
  }

  /** Prompt for the custom time the user wants to stay muted until. */
  data object ShowMuteUntilTimePicker : ConversationSettingsAction

  /** Open the disappearing messages screen. */
  data class NavigateToDisappearingMessages(val recipientId: RecipientId, val initialValue: Int) : ConversationSettingsAction

  /** Open the flow for editing the recipient's nickname and note. */
  data class EditNickname(val recipientId: RecipientId) : ConversationSettingsAction

  /** Open the chat color and wallpaper screen. */
  data class OpenChatWallpaper(val recipientId: RecipientId) : ConversationSettingsAction

  /** Open the sounds and notifications screen. */
  data class NavigateToSoundsAndNotifications(val recipientId: RecipientId) : ConversationSettingsAction

  /** Open the list of starred messages in this chat. */
  data class OpenStarredMessages(val threadId: Long) : ConversationSettingsAction

  /** Open the recipient's entry in the system contacts. */
  data class ViewContact(val recipient: Recipient) : ConversationSettingsAction {
    override fun toString(): String = "ViewContact(${recipient.id})"
  }

  /** Open the system flow for adding the recipient to the contacts. */
  data class AddContact(val recipient: Recipient) : ConversationSettingsAction {
    override fun toString(): String = "AddContact(${recipient.id})"
  }

  /** Open the safety number screen for the recipient. */
  data class ShowSafetyNumber(val identityRecord: IdentityRecord?) : ConversationSettingsAction {
    override fun toString(): String = "ShowSafetyNumber(hasIdentityRecord=${identityRecord != null})"
  }

  /** Open the media viewer on the item the user tapped in the shared media rail, scaling up out of [bounds]. */
  data class ShowMediaPreview(val mediaRecord: MediaTable.MediaRecord, val isLtr: Boolean, val bounds: IntRect) : ConversationSettingsAction {
    override fun toString(): String = "ShowMediaPreview(messageId=${mediaRecord.messageId}, isLtr=$isLtr)"
  }

  /** Download the media the user tapped, since it isn't on disk yet. */
  data class DownloadMedia(val mediaRecord: MediaTable.MediaRecord) : ConversationSettingsAction {
    override fun toString(): String = "DownloadMedia(messageId=${mediaRecord.messageId})"
  }

  /** Tell the user the media they tapped hasn't finished sending. */
  data object ShowMediaNotSentYet : ConversationSettingsAction

  /** Open the media overview for this chat. */
  data class ShowMediaOverview(val threadId: Long) : ConversationSettingsAction

  /** Open the Signal support center. */
  data object OpenSupportCenter : ConversationSettingsAction

  /** Open the flow for contacting Signal support. */
  data object OpenContactUs : ConversationSettingsAction

  /** Open the donation flow. */
  data object OpenDonate : ConversationSettingsAction

  /** Open the picker for choosing which of the user's groups to add the recipient to. */
  data class AddToAGroup(val recipientId: RecipientId, val groupMembership: List<RecipientId>) : ConversationSettingsAction {
    override fun toString(): String = "AddToAGroup($recipientId, groupMembershipCount=${groupMembership.size})"
  }

  /** Open the conversation for one of the groups in common. */
  data class OpenGroupConversation(val recipient: Recipient) : ConversationSettingsAction {
    override fun toString(): String = "OpenGroupConversation(${recipient.id})"
  }

  /** Open the searchable list of group members. */
  data class NavigateToMemberSearch(val groupId: GroupId, val canAdd: Boolean, val hasGroupLink: Boolean) : ConversationSettingsAction

  /** Open the picker for choosing new members to add to the group. */
  data class AddMembersToGroup(val groupId: GroupId, val selectionLimits: SelectionLimits, val groupMembersWithoutSelf: List<RecipientId>) : ConversationSettingsAction {
    override fun toString(): String = "AddMembersToGroup($groupId, memberCount=${groupMembersWithoutSelf.size})"
  }

  /** Tell the user the group is already at the maximum number of members. */
  data object ShowGroupHardLimitDialog : ConversationSettingsAction

  /** Open the sheet of actions for the group member the user tapped. */
  data class ShowRecipientBottomSheet(val recipientId: RecipientId, val groupId: GroupId) : ConversationSettingsAction

  /** Open the screen for editing the user's label in this group. */
  data class NavigateToMemberLabel(val groupId: GroupId) : ConversationSettingsAction

  /** Tell the user they aren't allowed to set a member label in this group. */
  data object ShowMemberLabelPermissionError : ConversationSettingsAction

  /** Open the group link screen. */
  data class NavigateToShareableGroupLink(val groupId: GroupId) : ConversationSettingsAction

  /** Open the list of pending requests and invites. */
  data class OpenRequestsAndInvites(val groupId: GroupId.V2) : ConversationSettingsAction

  /** Open the group permissions screen. */
  data class NavigateToPermissions(val groupId: GroupId) : ConversationSettingsAction

  /** Ask the user to confirm leaving the group. */
  data class ShowLeaveGroupDialog(val groupId: GroupId) : ConversationSettingsAction

  /** Ask the user to confirm ending the group for everyone. */
  data class ShowEndGroupDialog(val groupId: GroupId.V2, val groupTitle: String) : ConversationSettingsAction {
    override fun toString(): String = "ShowEndGroupDialog($groupId)"
  }

  /** Ask the user to confirm blocking the recipient. */
  data class ShowBlockDialog(val recipient: Recipient) : ConversationSettingsAction {
    override fun toString(): String = "ShowBlockDialog(${recipient.id})"
  }

  /** Ask the user to confirm unblocking the recipient. */
  data class ShowUnblockDialog(val recipient: Recipient) : ConversationSettingsAction {
    override fun toString(): String = "ShowUnblockDialog(${recipient.id})"
  }

  /** Ask the user to confirm reporting spam, optionally offering to block at the same time. */
  data class ShowReportSpamDialog(val recipient: Recipient, val canBlock: Boolean) : ConversationSettingsAction {
    override fun toString(): String = "ShowReportSpamDialog(${recipient.id}, canBlock=$canBlock)"
  }

  /** Tell the user why the block failed. */
  data class ShowBlockError(val failureReason: GroupChangeFailureReason) : ConversationSettingsAction

  /** Confirm to the user that the spam was reported. */
  data object ShowSpamReported : ConversationSettingsAction

  /** Confirm to the user that the spam was reported and the recipient blocked. */
  data object ShowSpamReportedAndBlocked : ConversationSettingsAction

  /** Tell the user why adding members failed. */
  data class ShowAddMembersError(val failureReason: GroupChangeFailureReason) : ConversationSettingsAction

  /** Tell the user which recipients were invited rather than added outright. */
  data class ShowGroupInvitesSentDialog(val invitesSentTo: List<Recipient>) : ConversationSettingsAction {
    override fun toString(): String = "ShowGroupInvitesSentDialog(inviteCount=${invitesSentTo.size})"
  }

  /** Confirm to the user how many members were added. */
  data class ShowMembersAdded(val membersAddedCount: Int) : ConversationSettingsAction

  /** Pop back to the conversation list, since this chat no longer exists. */
  data object GoToConversationList : ConversationSettingsAction
}
