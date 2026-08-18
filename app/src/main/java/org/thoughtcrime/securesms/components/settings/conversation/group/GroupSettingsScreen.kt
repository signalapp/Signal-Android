/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.conversation.group

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Texts
import org.signal.emoji.EmojiText
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.avatar.AvatarImage
import org.thoughtcrime.securesms.components.settings.conversation.group.GroupSettingsState.Dialog
import org.thoughtcrime.securesms.components.settings.conversation.shared.ArchiveChatRow
import org.thoughtcrime.securesms.components.settings.conversation.shared.BlockRow
import org.thoughtcrime.securesms.components.settings.conversation.shared.CallBar
import org.thoughtcrime.securesms.components.settings.conversation.shared.CallBarState
import org.thoughtcrime.securesms.components.settings.conversation.shared.ChatColorAndWallpaperRow
import org.thoughtcrime.securesms.components.settings.conversation.shared.ConversationHeader
import org.thoughtcrime.securesms.components.settings.conversation.shared.ConversationSettingsScaffold
import org.thoughtcrime.securesms.components.settings.conversation.shared.DeleteChatRow
import org.thoughtcrime.securesms.components.settings.conversation.shared.DisappearingMessagesRow
import org.thoughtcrime.securesms.components.settings.conversation.shared.GroupMember
import org.thoughtcrime.securesms.components.settings.conversation.shared.InternalDetailsButton
import org.thoughtcrime.securesms.components.settings.conversation.shared.LargeIconRow
import org.thoughtcrime.securesms.components.settings.conversation.shared.LegacyGroupState
import org.thoughtcrime.securesms.components.settings.conversation.shared.PREVIEW_GROUP_ID
import org.thoughtcrime.securesms.components.settings.conversation.shared.ROW_AVATAR_SIZE
import org.thoughtcrime.securesms.components.settings.conversation.shared.ReportSpamRow
import org.thoughtcrime.securesms.components.settings.conversation.shared.SoundsAndNotificationsRow
import org.thoughtcrime.securesms.components.settings.conversation.shared.StarredMessagesRow
import org.thoughtcrime.securesms.components.settings.conversation.shared.UnmuteDialog
import org.thoughtcrime.securesms.components.settings.conversation.shared.callLogSection
import org.thoughtcrime.securesms.components.settings.conversation.shared.previewRecipient
import org.thoughtcrime.securesms.components.settings.conversation.shared.sharedMediaSection
import org.thoughtcrime.securesms.groups.memberlabel.MemberLabelPill
import org.thoughtcrime.securesms.groups.memberlabel.StyledMemberLabel
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.recipients.Recipient
import org.signal.core.ui.R as CoreUiR

/**
 * Settings for a group conversation.
 */
@Composable
fun GroupSettingsScreen(
  state: GroupSettingsState,
  onEvent: (GroupSettingsEvent) -> Unit,
  onNavigationClick: () -> Unit,
  onAvatarViewCreated: (View) -> Unit,
  modifier: Modifier = Modifier
) {
  ConversationSettingsScaffold(
    title = state.title,
    recipient = state.recipient,
    onNavigationClick = onNavigationClick,
    actions = {
      if (state.canEditGroupAttributes) {
        IconButton(onClick = { onEvent(GroupSettingsEvent.EditGroupClicked) }) {
          Icon(
            painter = painterResource(CoreUiR.drawable.symbol_edit_24),
            contentDescription = stringResource(R.string.ManageGroupActivity_edit_name_and_picture)
          )
        }
      }
    },
    modifier = modifier
  ) {
    if (state.recipient == Recipient.UNKNOWN) {
      return@ConversationSettingsScaffold
    }

    item {
      ConversationHeader(
        recipient = state.recipient,
        name = state.title,
        storyViewState = state.storyViewState,
        subhead = if (state.showMembershipCountAsSubhead) membershipSubhead(state) else null,
        onAvatarClick = { onEvent(GroupSettingsEvent.AvatarClicked) },
        onAvatarViewCreated = onAvatarViewCreated
      ) {
        if (state.isTerminated) {
          Text(
            text = stringResource(R.string.ConversationSettingsFragment__this_group_was_ended),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
              .padding(top = 8.dp)
              .background(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(percent = 50))
              .padding(horizontal = 12.dp, vertical = 6.dp)
          )
        }
      }
    }

    if (state.groupId.isV2 && !state.isTerminated) {
      item {
        GroupDescription(
          description = state.description,
          canEdit = state.canEditGroupAttributes,
          onEditClick = { onEvent(GroupSettingsEvent.EditGroupDescriptionClicked) },
          onViewClick = { onEvent(GroupSettingsEvent.ViewGroupDescriptionClicked) }
        )
      }
    } else if (state.legacyGroupState != LegacyGroupState.NONE) {
      item {
        LegacyGroupNotice(
          legacyGroupState = state.legacyGroupState,
          onLearnMoreClick = { onEvent(GroupSettingsEvent.LegacyGroupLearnMoreClicked) },
          onMmsWarningClick = { onEvent(GroupSettingsEvent.LegacyGroupMmsWarningClicked) }
        )
      }
    }

    if (state.displayInternalRecipientDetails) {
      item {
        InternalDetailsButton(onClick = { onEvent(GroupSettingsEvent.InternalDetailsClicked) })
      }
    }

    item {
      CallBar(
        state = state.callBar,
        enabled = !state.isDeprecatedOrUnregistered,
        isMuteMenuShown = state.dialog == Dialog.MuteMenu,
        onAddToStoryClick = { onEvent(GroupSettingsEvent.AddToStoryClicked) },
        onMessageClick = { onEvent(GroupSettingsEvent.MessageClicked) },
        onVideoCallClick = { onEvent(GroupSettingsEvent.VideoCallClicked) },
        onAudioCallClick = {},
        onMuteClick = { onEvent(GroupSettingsEvent.MuteClicked) },
        onMuteDurationSelected = { onEvent(GroupSettingsEvent.MuteDurationSelected(it)) },
        onMuteUntilCustomTimeClick = { onEvent(GroupSettingsEvent.MuteUntilCustomTimeClicked) },
        onMuteMenuDismissed = { onEvent(GroupSettingsEvent.DialogDismissed) },
        onSearchClick = { onEvent(GroupSettingsEvent.SearchClicked) }
      )
    }

    item { Dividers.Default() }

    callLogSection(state.calls)

    if (!state.recipient.isBlocked) {
      item {
        DisappearingMessagesRow(
          lifespanSeconds = state.disappearingMessagesLifespan,
          enabled = state.canEditDisappearingMessages && !state.isDeprecatedOrUnregistered,
          onClick = { onEvent(GroupSettingsEvent.DisappearingMessagesClicked) }
        )
      }
    }

    item {
      ChatColorAndWallpaperRow(onClick = { onEvent(GroupSettingsEvent.ChatColorAndWallpaperClicked) })
    }

    item {
      SoundsAndNotificationsRow(
        enabled = !state.isDeprecatedOrUnregistered,
        onClick = { onEvent(GroupSettingsEvent.SoundsAndNotificationsClicked) }
      )
    }

    if (state.starredMessagesEnabled) {
      item {
        StarredMessagesRow(onClick = { onEvent(GroupSettingsEvent.StarredMessagesClicked) })
      }
    }

    sharedMediaSection(
      state = state.mediaRail,
      onEvent = { onEvent(GroupSettingsEvent.MediaRailEvent(it)) }
    )

    membershipSection(state, onEvent)
    managementSection(state, onEvent)
    terminatedGroupSection(state, onEvent)
    blockAndSpamSection(state, onEvent)
    endGroupSection(state, onEvent)
  }

  GroupSettingsDialogs(
    state = state,
    onEvent = onEvent
  )
}

/** The group's member list, including the add-member and see-all affordances. */
private fun LazyListScope.membershipSection(
  state: GroupSettingsState,
  onEvent: (GroupSettingsEvent) -> Unit
) {
  val memberCount = state.allMembers.size

  if (state.canAddToGroup || memberCount > 0) {
    item { Dividers.Default() }

    item {
      MemberSectionHeader(
        memberCount = memberCount,
        isTerminated = state.isTerminated,
        onSearchClick = { onEvent(GroupSettingsEvent.MemberSearchClicked) }
      )
    }
  }

  if (state.canAddMembers) {
    item {
      LargeIconRow(
        text = stringResource(R.string.ConversationSettingsFragment__add_members),
        icon = R.drawable.ic_plus_24,
        onClick = { onEvent(GroupSettingsEvent.AddMembersClicked) }
      )
    }
  }

  items(
    items = state.members,
    key = { it.recipient.id.toLong() }
  ) { member ->
    val memberLabel = state.memberLabels[member.recipient.id]
    val canSetMemberLabel = member.recipient.isSelf && state.canSetOwnMemberLabel

    MemberRow(
      recipient = member.recipient,
      isAdmin = member.isAdmin,
      memberLabel = memberLabel,
      showAddMemberLabel = canSetMemberLabel && memberLabel == null,
      onClick = { onEvent(GroupSettingsEvent.MemberClicked(member.recipient.id)) },
      onAvatarClick = { onEvent(GroupSettingsEvent.MemberAvatarClicked(member.recipient.id)) }
    )
  }

  if (state.canShowMoreMembers) {
    item {
      LargeIconRow(
        text = stringResource(R.string.ConversationSettingsFragment__see_all),
        icon = R.drawable.ic_chevron_down_icon_20,
        onClick = { onEvent(GroupSettingsEvent.RevealAllMembersClicked) }
      )
    }
  }
}

/** The group link, member labels, invite requests, permissions, and the two ways out of a group. */
private fun LazyListScope.managementSection(
  state: GroupSettingsState,
  onEvent: (GroupSettingsEvent) -> Unit
) {
  if (state.recipient.isPushV2Group && !state.isTerminated) {
    item { Dividers.Default() }

    item {
      Rows.TextRow(
        text = stringResource(R.string.ConversationSettingsFragment__group_link),
        label = stringResource(if (state.groupLinkEnabled) R.string.preferences_on else R.string.preferences_off),
        icon = painterResource(R.drawable.ic_link_24),
        enabled = state.isActive && !state.isDeprecatedOrUnregistered,
        onClick = { onEvent(GroupSettingsEvent.GroupLinkClicked) }
      )
    }

    item {
      Rows.TextRow(
        text = stringResource(R.string.ConversationSettingsFragment__group_member_label),
        icon = painterResource(R.drawable.symbol_tag_24),
        enabled = state.canSetOwnMemberLabel && !state.isDeprecatedOrUnregistered,
        onClick = { onEvent(GroupSettingsEvent.GroupMemberLabelClicked) },
        onDisabledClick = { onEvent(GroupSettingsEvent.GroupMemberLabelDisabledClicked) }
      )
    }

    item {
      Rows.TextRow(
        text = stringResource(R.string.ConversationSettingsFragment__requests_and_invites),
        icon = painterResource(R.drawable.ic_update_group_add_16),
        iconModifier = Modifier.size(24.dp),
        enabled = state.isActive && !state.isDeprecatedOrUnregistered,
        onClick = { onEvent(GroupSettingsEvent.RequestsAndInvitesClicked) }
      )
    }

    if (state.isSelfAdmin) {
      item {
        Rows.TextRow(
          text = stringResource(R.string.ConversationSettingsFragment__permissions),
          icon = painterResource(R.drawable.ic_lock_24),
          enabled = state.isActive && !state.isDeprecatedOrUnregistered,
          onClick = { onEvent(GroupSettingsEvent.PermissionsClicked) }
        )
      }
    }
  }

  if (state.canLeave) {
    item { Dividers.Default() }

    item {
      Rows.TextRow(
        text = stringResource(R.string.conversation__menu_leave_group),
        icon = painterResource(R.drawable.symbol_leave_24),
        foregroundTint = MaterialTheme.colorScheme.error,
        enabled = !state.isDeprecatedOrUnregistered,
        onClick = { onEvent(GroupSettingsEvent.LeaveGroupClicked) }
      )
    }
  }
}

/** Ending the group, which sits below block and report spam so it reads as the most drastic option. */
private fun LazyListScope.endGroupSection(
  state: GroupSettingsState,
  onEvent: (GroupSettingsEvent) -> Unit
) {
  if (!state.canEndGroup) {
    return
  }

  item { Dividers.Default() }

  item {
    Rows.TextRow(
      text = stringResource(R.string.ConversationSettingsFragment__end_group),
      icon = painterResource(R.drawable.symbol_x_circle_24),
      foregroundTint = MaterialTheme.colorScheme.error,
      enabled = !state.isDeprecatedOrUnregistered,
      onClick = { onEvent(GroupSettingsEvent.EndGroupClicked) }
    )
  }
}

/** Archiving and deleting, which we only offer once a group has been ended. */
private fun LazyListScope.terminatedGroupSection(
  state: GroupSettingsState,
  onEvent: (GroupSettingsEvent) -> Unit
) {
  if (!state.isTerminated) {
    return
  }

  item { Dividers.Default() }

  item {
    ArchiveChatRow(
      isArchived = state.isArchived,
      onClick = { onEvent(GroupSettingsEvent.ArchiveChatClicked) }
    )
  }

  item {
    DeleteChatRow(onClick = { onEvent(GroupSettingsEvent.DeleteChatClicked) })
  }
}

private fun LazyListScope.blockAndSpamSection(
  state: GroupSettingsState,
  onEvent: (GroupSettingsEvent) -> Unit
) {
  if (state.isTerminated) {
    item { Dividers.Default() }

    item {
      ReportSpamRow(onClick = { onEvent(GroupSettingsEvent.ReportSpamClicked) })
    }

    return
  }

  if (!state.canModifyBlockedState) {
    return
  }

  // The leave-group section already ends in a divider, so adding another here would double it up.
  if (!state.canLeave) {
    item { Dividers.Default() }
  }

  item {
    BlockRow(
      isBlocked = state.recipient.isBlocked,
      isGroup = true,
      enabled = !state.isDeprecatedOrUnregistered,
      onClick = { onEvent(GroupSettingsEvent.BlockClicked) }
    )
  }

  item {
    ReportSpamRow(
      enabled = !state.isDeprecatedOrUnregistered,
      onClick = { onEvent(GroupSettingsEvent.ReportSpamClicked) }
    )
  }
}

@Composable
private fun membershipSubhead(state: GroupSettingsState): String {
  return if (state.groupId.isV1) {
    stringResource(R.string.ConversationSettingsFragment__s_dot_s, state.membershipCountDescription, stringResource(R.string.ManageGroupActivity_legacy_group))
  } else {
    state.membershipCountDescription
  }
}

@Composable
private fun GroupDescription(
  description: String?,
  canEdit: Boolean,
  onEditClick: () -> Unit,
  onViewClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  if (description.isNullOrEmpty()) {
    if (canEdit) {
      Text(
        text = stringResource(R.string.ManageGroupActivity_add_group_description),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier
          .fillMaxWidth()
          .clickable(onClick = onEditClick)
          .padding(horizontal = 32.dp, vertical = 8.dp)
      )
    }
  } else {
    EmojiText(
      text = description,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
      maxLines = 2,
      modifier = modifier
        .fillMaxWidth()
        .clickable(onClick = onViewClick)
        .padding(horizontal = 32.dp, vertical = 8.dp)
    )
  }
}

@Composable
private fun LegacyGroupNotice(
  legacyGroupState: LegacyGroupState,
  onLearnMoreClick: () -> Unit,
  onMmsWarningClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val body: String
  val linkLabel: String
  val onClick: () -> Unit

  when (legacyGroupState) {
    LegacyGroupState.LEARN_MORE -> {
      body = stringResource(R.string.ManageGroupActivity_legacy_group_learn_more)
      linkLabel = stringResource(R.string.LearnMoreTextView_learn_more)
      onClick = onLearnMoreClick
    }

    LegacyGroupState.MMS_WARNING -> {
      body = stringResource(R.string.ManageGroupActivity_this_is_an_insecure_mms_group)
      linkLabel = stringResource(R.string.ManageGroupActivity_invite_now)
      onClick = onMmsWarningClick
    }

    LegacyGroupState.NONE -> return
  }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(horizontal = 32.dp, vertical = 12.dp)
  ) {
    Text(
      text = body,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Text(
      text = linkLabel,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.primary,
      modifier = Modifier.padding(top = 4.dp)
    )
  }
}

@Composable
private fun MemberSectionHeader(
  memberCount: Int,
  isTerminated: Boolean,
  onSearchClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
      .fillMaxWidth()
      .clickable(onClick = onSearchClick)
  ) {
    Texts.SectionHeader(
      text = if (isTerminated) {
        pluralStringResource(R.plurals.ConversationSettingsFragment__d_former_members, memberCount, memberCount)
      } else {
        pluralStringResource(R.plurals.ContactSelectionListFragment_d_members, memberCount, memberCount)
      },
      modifier = Modifier.weight(1f)
    )

    Icon(
      painter = painterResource(CoreUiR.drawable.symbol_search_24),
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(end = 24.dp)
    )
  }
}

@Composable
private fun MemberRow(
  recipient: Recipient,
  isAdmin: Boolean,
  memberLabel: StyledMemberLabel?,
  showAddMemberLabel: Boolean,
  onClick: () -> Unit,
  onAvatarClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val about = recipient.combinedAboutAndEmoji

  Rows.TextRow(
    text = {
      Column(modifier = Modifier.weight(1f)) {
        EmojiText(
          text = if (recipient.isSelf) stringResource(R.string.Recipient_you) else recipient.getDisplayName(context),
          style = MaterialTheme.typography.bodyLarge
        )

        when {
          memberLabel != null -> MemberLabelPill(
            emoji = memberLabel.label.emoji,
            text = memberLabel.label.displayText,
            tintColor = Color(memberLabel.tintColor),
            contentPadding = MemberLabelPill.contentPaddingCompact,
            textStyle = MemberLabelPill.textStyleCompact
          )

          showAddMemberLabel -> Text(
            text = stringResource(R.string.GroupRecipientListItem__add_member_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )

          !about.isNullOrBlank() -> EmojiText(
            text = about,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
          )
        }
      }

      if (isAdmin) {
        Text(
          text = stringResource(R.string.GroupRecipientListItem_admin),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(start = 16.dp)
        )
      }
    },
    icon = {
      AvatarImage(
        recipient = recipient,
        modifier = Modifier
          .size(ROW_AVATAR_SIZE)
          .clickable(onClick = onAvatarClick)
      )
    },
    onClick = onClick,
    modifier = modifier
  )
}

@Composable
private fun GroupSettingsDialogs(
  state: GroupSettingsState,
  onEvent: (GroupSettingsEvent) -> Unit
) {
  when (state.dialog) {
    Dialog.Unmute -> UnmuteDialog(
      recipient = state.recipient,
      onConfirm = { onEvent(GroupSettingsEvent.UnmuteConfirmed) },
      onDismiss = { onEvent(GroupSettingsEvent.DialogDismissed) }
    )

    Dialog.CannotAddToGroupStory -> Dialogs.SimpleMessageDialog(
      title = stringResource(R.string.ConversationSettingsFragment__cant_add_to_group_story),
      message = stringResource(R.string.ConversationSettingsFragment__only_admins_of_this_group_can_add_to_its_story),
      dismiss = stringResource(android.R.string.ok),
      onDismiss = { onEvent(GroupSettingsEvent.DialogDismissed) }
    )

    Dialog.CannotStartGroupCall -> Dialogs.SimpleMessageDialog(
      title = stringResource(R.string.ConversationActivity_cant_start_group_call),
      message = stringResource(R.string.ConversationActivity_only_admins_of_this_group_can_start_a_call),
      dismiss = stringResource(android.R.string.ok),
      onDismiss = { onEvent(GroupSettingsEvent.DialogDismissed) }
    )

    Dialog.DeletingChat -> Dialogs.IndeterminateProgressDialog(message = stringResource(R.string.ConversationFragment_deleting_messages))

    Dialog.AddingMembers -> Dialogs.IndeterminateProgressDialog()

    // The mute menu is a dropdown anchored to the call bar, so CallBar renders it rather than us.
    Dialog.MuteMenu, Dialog.None -> Unit
  }
}

private val PREVIEW_MEMBERS = listOf(
  GroupMember(previewRecipient(2L, profileName = ProfileName.fromParts("Benjamin", "Sisko")), isAdmin = true),
  GroupMember(previewRecipient(3L, profileName = ProfileName.fromParts("Jadzia", "Dax")), isAdmin = false)
)

private fun previewState(
  recipient: Recipient = previewRecipient(1L, groupName = "Deep Space Nine", groupId = PREVIEW_GROUP_ID),
  isActive: Boolean = true,
  isTerminated: Boolean = false,
  legacyGroupState: LegacyGroupState = LegacyGroupState.NONE,
  membersExpanded: Boolean = false,
  allMembers: List<GroupMember> = PREVIEW_MEMBERS
): GroupSettingsState {
  return GroupSettingsState(
    groupId = PREVIEW_GROUP_ID,
    recipient = recipient,
    threadId = 1L,
    canModifyBlockedState = true,
    allMembers = allMembers,
    membersExpanded = membersExpanded,
    title = "Deep Space Nine",
    description = "Bajoran space station",
    membershipCountDescription = "${allMembers.size} members",
    canEditGroupAttributes = true,
    canAddToGroup = true,
    isActive = isActive,
    isTerminated = isTerminated,
    legacyGroupState = legacyGroupState,
    isSelfAdmin = true,
    detailsLoaded = true,
    callBar = CallBarState(
      isVideoAvailable = true,
      isMuteAvailable = true,
      isSearchAvailable = true,
      isAddToStoryAvailable = true
    )
  )
}

@Composable
private fun GroupSettingsScreenPreview(state: GroupSettingsState) {
  Previews.Preview {
    GroupSettingsScreen(
      state = state,
      onEvent = {},
      onNavigationClick = {},
      onAvatarViewCreated = {}
    )
  }
}

@AllDevicePreviews
@Composable
private fun GroupSettingsScreenActivePreview() {
  GroupSettingsScreenPreview(previewState())
}

@DayNightPreviews
@Composable
private fun GroupSettingsScreenTerminatedPreview() {
  GroupSettingsScreenPreview(previewState(isActive = false, isTerminated = true))
}

@DayNightPreviews
@Composable
private fun GroupSettingsScreenLegacyPreview() {
  GroupSettingsScreenPreview(previewState(legacyGroupState = LegacyGroupState.LEARN_MORE))
}

@DayNightPreviews
@Composable
private fun GroupSettingsScreenMmsPreview() {
  GroupSettingsScreenPreview(previewState(legacyGroupState = LegacyGroupState.MMS_WARNING))
}

@DayNightPreviews
@Composable
private fun GroupSettingsScreenBlockedPreview() {
  GroupSettingsScreenPreview(previewState(recipient = previewRecipient(1L, groupName = "Deep Space Nine", groupId = PREVIEW_GROUP_ID, isBlocked = true)))
}

/** Seven members is one past the collapse threshold, so this shows the "see all" affordance. */
@DayNightPreviews
@Composable
private fun GroupSettingsScreenCollapsedMembersPreview() {
  val members = (2L..8L).map { GroupMember(previewRecipient(it, profileName = ProfileName.fromParts("Member", "$it")), isAdmin = false) }
  GroupSettingsScreenPreview(previewState(allMembers = members))
}
