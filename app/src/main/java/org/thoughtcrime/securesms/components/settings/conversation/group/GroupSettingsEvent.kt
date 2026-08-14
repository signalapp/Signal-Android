/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.conversation.group

import org.signal.uicomponents.recentmediarail.RecentMediaRailAction
import org.signal.uicomponents.recentmediarail.RecentMediaRailEvents
import org.signal.uicomponents.recentmediarail.RecentMediaRailState
import org.thoughtcrime.securesms.components.settings.conversation.ConversationSettingsRepository.GroupDetails
import org.thoughtcrime.securesms.components.settings.conversation.shared.CallEntry
import org.thoughtcrime.securesms.database.model.StoryViewState
import org.thoughtcrime.securesms.groups.memberlabel.StyledMemberLabel
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * Reminder that these events are logged, so don't include anything sensitive in the toString.
 */
sealed interface GroupSettingsEvent {

  /** The user tapped the header avatar, which opens the avatar preview or the group's story. */
  data object AvatarClicked : GroupSettingsEvent

  /** The user tapped edit, which opens the group name and avatar editor. */
  data object EditGroupClicked : GroupSettingsEvent

  /** The user tapped a group description they're allowed to edit. */
  data object EditGroupDescriptionClicked : GroupSettingsEvent

  /** The user tapped a group description they can't edit, which shows it in full instead. */
  data object ViewGroupDescriptionClicked : GroupSettingsEvent

  /** The user tapped "learn more" on the legacy group notice. */
  data object LegacyGroupLearnMoreClicked : GroupSettingsEvent

  /** The user tapped the MMS group notice, which nudges them to invite the members to Signal. */
  data object LegacyGroupMmsWarningClicked : GroupSettingsEvent

  /** The user tapped the internal details button, which only internal users ever see. */
  data object InternalDetailsClicked : GroupSettingsEvent

  /** The user tapped the message button, which opens the conversation. */
  data object MessageClicked : GroupSettingsEvent

  /** The user tapped the video call button, which only admins may do in an announcement group. */
  data object VideoCallClicked : GroupSettingsEvent

  /** The user tapped add to story, which only admins may do in an announcement group. */
  data object AddToStoryClicked : GroupSettingsEvent

  /** The user tapped the mute button, which opens the mute menu or asks to confirm unmuting. */
  data object MuteClicked : GroupSettingsEvent

  /** The user picked one of the preset durations out of the mute menu. */
  data class MuteDurationSelected(val muteUntil: Long) : GroupSettingsEvent

  /** The user asked to mute until a time of their own choosing, which the fragment prompts for. */
  data object MuteUntilCustomTimeClicked : GroupSettingsEvent

  /** The user confirmed unmuting the chat. */
  data object UnmuteConfirmed : GroupSettingsEvent

  /** The user tapped the search button, which opens the conversation with search already running. */
  data object SearchClicked : GroupSettingsEvent

  /** The user tapped the disappearing messages row. */
  data object DisappearingMessagesClicked : GroupSettingsEvent

  /** The user tapped the chat color and wallpaper row. */
  data object ChatColorAndWallpaperClicked : GroupSettingsEvent

  /** The user tapped the sounds and notifications row. */
  data object SoundsAndNotificationsClicked : GroupSettingsEvent

  /** The user tapped the starred messages row. */
  data object StarredMessagesClicked : GroupSettingsEvent

  /** The user tapped the search button in the member list header. */
  data object MemberSearchClicked : GroupSettingsEvent

  /** The user tapped the row that adds members to the group. */
  data object AddMembersClicked : GroupSettingsEvent

  /** The user tapped a member row, which opens their sheet or, for themselves, the member label editor. */
  data class MemberClicked(val recipientId: RecipientId) : GroupSettingsEvent

  /** The user tapped a member's avatar, which always opens their sheet. */
  data class MemberAvatarClicked(val recipientId: RecipientId) : GroupSettingsEvent

  /** The user tapped "see all" under the collapsed member list. */
  data object RevealAllMembersClicked : GroupSettingsEvent

  /** The user tapped the group link row. */
  data object GroupLinkClicked : GroupSettingsEvent

  /** The user tapped the member label row. */
  data object GroupMemberLabelClicked : GroupSettingsEvent

  /** The user tapped the member label row while the group doesn't allow them to set one. */
  data object GroupMemberLabelDisabledClicked : GroupSettingsEvent

  /** The user tapped the requests and invites row. */
  data object RequestsAndInvitesClicked : GroupSettingsEvent

  /** The user tapped the group permissions row. */
  data object PermissionsClicked : GroupSettingsEvent

  /** The user tapped leave group, which asks the fragment to confirm first. */
  data object LeaveGroupClicked : GroupSettingsEvent

  /** The user tapped end group, which asks the fragment to confirm first. */
  data object EndGroupClicked : GroupSettingsEvent

  /** The user tapped the archive row on a group that has ended, which toggles whether the chat is archived. */
  data object ArchiveChatClicked : GroupSettingsEvent

  /** The user tapped delete chat on a group that has ended. */
  data object DeleteChatClicked : GroupSettingsEvent

  /** The user tapped block or unblock, which asks the fragment to confirm first. */
  data object BlockClicked : GroupSettingsEvent

  /** The user confirmed the block in the fragment's dialog. */
  data object BlockConfirmed : GroupSettingsEvent

  /** The user confirmed the unblock in the fragment's dialog. */
  data object UnblockConfirmed : GroupSettingsEvent

  /** The user tapped report spam, which asks the fragment to confirm first. */
  data object ReportSpamClicked : GroupSettingsEvent

  /** The user confirmed reporting spam without also blocking. */
  data object ReportSpamConfirmed : GroupSettingsEvent

  /** The user confirmed reporting spam and blocking in the same step. */
  data object BlockAndReportSpamConfirmed : GroupSettingsEvent

  /** Dismisses whatever is in [GroupSettingsState.dialog]. */
  data object DialogDismissed : GroupSettingsEvent

  /** The user picked who to add from the contact selection activity the fragment launched. */
  data class AddMembersSelected(val recipientIds: List<RecipientId>) : GroupSettingsEvent {
    override fun toString(): String = "AddMembersSelected(count=${recipientIds.size})"
  }

  /** The group's record or recipient changed, alongside whether its chat is currently archived. */
  data class GroupDetailsChanged(val details: GroupDetails, val isArchived: Boolean) : GroupSettingsEvent {
    override fun toString(): String = "GroupDetailsChanged(memberCount=${details.members.size}, isArchived=$isArchived)"
  }

  /** The labels for the current membership came back, alongside whether the user may set their own. */
  data class MemberLabelsLoaded(val memberLabels: Map<RecipientId, StyledMemberLabel>, val canSetOwnMemberLabel: Boolean) : GroupSettingsEvent {
    override fun toString(): String = "MemberLabelsLoaded(count=${memberLabels.size}, canSetOwnMemberLabel=$canSetOwnMemberLabel)"
  }

  /** The group's story became unviewed, viewed, or went away entirely. */
  data class StoryViewStateChanged(val storyViewState: StoryViewState) : GroupSettingsEvent

  /** The calls behind the call info variant of this screen finished loading. */
  data class CallsChanged(val calls: List<CallEntry>) : GroupSettingsEvent {
    override fun toString(): String = "CallsChanged(count=${calls.size})"
  }

  /** The group's thread id came back, or -1 if it doesn't have a thread yet. */
  data class ThreadIdLoaded(val threadId: Long) : GroupSettingsEvent

  /** Received an event from the media rail that we want to forward */
  data class MediaRailEvent(val event: RecentMediaRailEvents) : GroupSettingsEvent

  /** The media rail's presenter emitted new state for us to mirror. */
  data class MediaRailStateChanged(val railState: RecentMediaRailState) : GroupSettingsEvent {
    override fun toString(): String = "MediaRailStateChanged(count=${railState.media.size}, loaded=${railState.loaded})"
  }

  /** The media rail's presenter decided something needs doing that only this screen can do. */
  data class MediaRailAction(val action: RecentMediaRailAction) : GroupSettingsEvent
}
