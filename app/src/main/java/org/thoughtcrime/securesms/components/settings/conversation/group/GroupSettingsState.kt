/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.conversation.group

import org.signal.uicomponents.recentmediarail.RecentMediaRailState
import org.thoughtcrime.securesms.components.settings.conversation.shared.CallBarState
import org.thoughtcrime.securesms.components.settings.conversation.shared.CallEntry
import org.thoughtcrime.securesms.components.settings.conversation.shared.CollapsibleList
import org.thoughtcrime.securesms.components.settings.conversation.shared.GroupMember
import org.thoughtcrime.securesms.components.settings.conversation.shared.LegacyGroupState
import org.thoughtcrime.securesms.database.model.StoryViewState
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.groups.memberlabel.StyledMemberLabel
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId

data class GroupSettingsState(
  val groupId: GroupId,
  val recipient: Recipient = Recipient.UNKNOWN,
  /** Changed when recipient content changes, since Recipient.equals only compares IDs. */
  val recipientContentVersion: Int = 0,
  val threadId: Long = -1L,
  val storyViewState: StoryViewState = StoryViewState.NONE,
  val isDeprecatedOrUnregistered: Boolean = false,
  val displayInternalRecipientDetails: Boolean = false,
  val starredMessagesEnabled: Boolean = false,
  val disappearingMessagesLifespan: Int = 0,
  val canModifyBlockedState: Boolean = false,
  val isArchived: Boolean = false,
  val mediaRail: RecentMediaRailState = RecentMediaRailState(),
  val calls: List<CallEntry> = emptyList(),
  val callBar: CallBarState = CallBarState(),
  val title: String = "",
  val description: String? = null,
  val descriptionShouldLinkify: Boolean = false,
  val membershipCountDescription: String = "",
  val allMembers: List<GroupMember> = emptyList(),
  val membersExpanded: Boolean = false,
  val memberLabels: Map<RecipientId, StyledMemberLabel> = emptyMap(),
  val canSetOwnMemberLabel: Boolean = false,
  val isSelfAdmin: Boolean = false,
  val canAddToGroup: Boolean = false,
  val canEditGroupAttributes: Boolean = false,
  val isActive: Boolean = false,
  val isTerminated: Boolean = false,
  val isAnnouncementGroup: Boolean = false,
  val groupLinkEnabled: Boolean = false,
  val legacyGroupState: LegacyGroupState = LegacyGroupState.NONE,
  val detailsLoaded: Boolean = false,
  val dialog: Dialog = Dialog.None
) {

  /**
   * True once we've loaded enough to render the screen without it visibly shuffling around. Shared media is
   * deliberately not part of this: the rail reserves its space while loading, so there's no reason to hold the whole
   * screen behind it.
   */
  val isLoaded: Boolean = recipient != Recipient.UNKNOWN && detailsLoaded

  val members: List<GroupMember> = CollapsibleList.collapse(allMembers, membersExpanded)

  val canShowMoreMembers: Boolean = CollapsibleList.canExpand(allMembers, membersExpanded)

  /** Disappearing messages can only be changed by those who can edit group attributes, and never on a blocked chat. */
  val canEditDisappearingMessages: Boolean = canEditGroupAttributes && !recipient.isBlocked

  val canLeave: Boolean = isActive && groupId.isPush

  val canEndGroup: Boolean = isActive && groupId.isV2 && isSelfAdmin

  val canAddMembers: Boolean = canAddToGroup && !isTerminated && !isDeprecatedOrUnregistered

  /** Only group admins can add to an announcement group's story or start a call in one. */
  val isAnnouncementGroupRestricted: Boolean = isAnnouncementGroup && !isSelfAdmin

  /**
   * Whether the line under the group name should be the member count. V2 groups normally put their description there
   * instead.
   */
  val showMembershipCountAsSubhead: Boolean = groupId.isV1 || (!canEditGroupAttributes && description.isNullOrEmpty())

  sealed interface Dialog {
    data object None : Dialog
    data object MuteMenu : Dialog
    data object Unmute : Dialog
    data object CannotAddToGroupStory : Dialog
    data object CannotStartGroupCall : Dialog
    data object DeletingChat : Dialog
    data object AddingMembers : Dialog
  }
}
