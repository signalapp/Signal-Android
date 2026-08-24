/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.conversation.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log
import org.signal.uicomponents.recentmediarail.RecentMediaRailEvents
import org.signal.uicomponents.recentmediarail.RecentMediaRailPresenter
import org.thoughtcrime.securesms.components.settings.conversation.ConversationSettingsAction
import org.thoughtcrime.securesms.components.settings.conversation.ConversationSettingsRepository
import org.thoughtcrime.securesms.components.settings.conversation.ConversationSettingsRepository.GroupDetails
import org.thoughtcrime.securesms.components.settings.conversation.group.GroupSettingsState.Dialog
import org.thoughtcrime.securesms.components.settings.conversation.shared.BlockAndSpamHandler
import org.thoughtcrime.securesms.components.settings.conversation.shared.CallBarState
import org.thoughtcrime.securesms.components.settings.conversation.shared.SharedMediaLoader
import org.thoughtcrime.securesms.components.settings.conversation.shared.toConversationSettingsAction
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.groups.SelectionLimits
import org.thoughtcrime.securesms.groups.v2.GroupAddMembersResult
import org.thoughtcrime.securesms.recipients.Recipient

/**
 * View model behind [GroupSettingsScreen].
 */
class GroupSettingsViewModel(
  private val groupId: GroupId,
  private val callMessageIds: LongArray,
  private val repository: ConversationSettingsRepository
) : EventDrivenViewModel<GroupSettingsEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(GroupSettingsViewModel::class)
  }

  /** Whether we're the call-info variant of this screen, which shows a message button in place of search. */
  private val isCallInfoVariant: Boolean = callMessageIds.isNotEmpty()

  private val _state = MutableStateFlow(
    GroupSettingsState(
      groupId = groupId,
      isDeprecatedOrUnregistered = repository.isDeprecatedOrUnregistered(),
      starredMessagesEnabled = repository.isStarredMessagesEnabled(),
      displayInternalRecipientDetails = repository.isInternalRecipientDetailsEnabled()
    )
  )

  private val _actions = Channel<ConversationSettingsAction>(Channel.BUFFERED)

  val state: StateFlow<GroupSettingsState> = _state.asStateFlow()
  val actions: Flow<ConversationSettingsAction> = _actions.receiveAsFlow()

  private val sharedMediaLoader = SharedMediaLoader(repository)
  private val mediaRailPresenter = RecentMediaRailPresenter(viewModelScope, sharedMediaLoader)

  init {
    repository
      .observeGroupDetails(groupId)
      .onEach { details ->
        onEvent(GroupSettingsEvent.GroupDetailsChanged(details, repository.isArchived(details.recipient.id)))
        loadMemberLabels(details.members.map { member -> member.recipient })
      }
      .launchIn(viewModelScope)

    repository
      .observeStoryViewState(groupId)
      .onEach { onEvent(GroupSettingsEvent.StoryViewStateChanged(it)) }
      .launchIn(viewModelScope)

    mediaRailPresenter
      .state
      .onEach { onEvent(GroupSettingsEvent.MediaRailStateChanged(it)) }
      .launchIn(viewModelScope)

    mediaRailPresenter
      .actions
      .onEach { onEvent(GroupSettingsEvent.MediaRailAction(it)) }
      .launchIn(viewModelScope)

    if (callMessageIds.isNotEmpty()) {
      repository
        .observeCalls(_state.map { it.threadId }, callMessageIds)
        .onEach { onEvent(GroupSettingsEvent.CallsChanged(it)) }
        .launchIn(viewModelScope)
    }

    viewModelScope.launch {
      onEvent(GroupSettingsEvent.ThreadIdLoaded(repository.getThreadId(groupId)))
    }
  }

  override suspend fun processEvent(event: GroupSettingsEvent) {
    val state = _state.value

    when (event) {
      GroupSettingsEvent.AvatarClicked -> {
        BlockAndSpamHandler.avatarClickAction(state.recipient, state.storyViewState, repository.isStoriesFeatureEnabled())?.let { _actions.send(it) }
      }

      GroupSettingsEvent.EditGroupClicked -> {
        _actions.send(ConversationSettingsAction.EditGroupProfile(groupId))
      }

      GroupSettingsEvent.EditGroupDescriptionClicked -> {
        _actions.send(ConversationSettingsAction.EditGroupDescription(groupId))
      }

      GroupSettingsEvent.ViewGroupDescriptionClicked -> {
        _actions.send(ConversationSettingsAction.ShowGroupDescriptionDialog(groupId, state.descriptionShouldLinkify))
      }

      GroupSettingsEvent.LegacyGroupLearnMoreClicked -> {
        _actions.send(ConversationSettingsAction.ShowGroupsLearnMore)
      }

      GroupSettingsEvent.LegacyGroupMmsWarningClicked -> {
        _actions.send(ConversationSettingsAction.ShowInviteFriends)
      }

      GroupSettingsEvent.InternalDetailsClicked -> {
        _actions.send(ConversationSettingsAction.NavigateToInternalDetails(state.recipient.id))
      }

      GroupSettingsEvent.MessageClicked -> {
        _actions.send(ConversationSettingsAction.OpenConversation(state.recipient.id, state.threadId))
      }

      GroupSettingsEvent.VideoCallClicked -> {
        if (state.isAnnouncementGroupRestricted) {
          _state.update { it.copy(dialog = Dialog.CannotStartGroupCall) }
        } else {
          _actions.send(ConversationSettingsAction.StartVideoCall(state.recipient))
        }
      }

      GroupSettingsEvent.AddToStoryClicked -> {
        if (state.isAnnouncementGroupRestricted) {
          _state.update { it.copy(dialog = Dialog.CannotAddToGroupStory) }
        } else {
          _actions.send(ConversationSettingsAction.AddToGroupStory(state.recipient.id))
        }
      }

      GroupSettingsEvent.MuteClicked -> {
        _state.update { it.copy(dialog = if (state.callBar.isMuted) Dialog.Unmute else Dialog.MuteMenu) }
      }

      is GroupSettingsEvent.MuteDurationSelected -> {
        _state.update { it.copy(dialog = Dialog.None) }
        repository.setMuteUntil(groupId, event.muteUntil)
      }

      GroupSettingsEvent.MuteUntilCustomTimeClicked -> {
        _state.update { it.copy(dialog = Dialog.None) }
        _actions.send(ConversationSettingsAction.ShowMuteUntilTimePicker)
      }

      GroupSettingsEvent.UnmuteConfirmed -> {
        _state.update { it.copy(dialog = Dialog.None) }
        repository.setMuteUntil(groupId, 0)
      }

      GroupSettingsEvent.SearchClicked -> {
        _actions.send(ConversationSettingsAction.OpenConversation(state.recipient.id, state.threadId, withSearchOpen = true))
      }

      GroupSettingsEvent.DisappearingMessagesClicked -> {
        _actions.send(ConversationSettingsAction.NavigateToDisappearingMessages(state.recipient.id, state.disappearingMessagesLifespan))
      }

      GroupSettingsEvent.ChatColorAndWallpaperClicked -> _actions.send(ConversationSettingsAction.OpenChatWallpaper(state.recipient.id))

      GroupSettingsEvent.SoundsAndNotificationsClicked -> {
        _actions.send(ConversationSettingsAction.NavigateToSoundsAndNotifications(state.recipient.id))
      }

      GroupSettingsEvent.StarredMessagesClicked -> _actions.send(ConversationSettingsAction.OpenStarredMessages(state.threadId))

      GroupSettingsEvent.MemberSearchClicked -> {
        _actions.send(ConversationSettingsAction.NavigateToMemberSearch(groupId, state.canAddMembers, state.groupLinkEnabled))
      }

      GroupSettingsEvent.AddMembersClicked -> applyAddMembersClicked()

      is GroupSettingsEvent.AddMembersSelected -> applyAddMembersSelected(event)

      is GroupSettingsEvent.MemberClicked -> {
        val member = state.allMembers.firstOrNull { it.recipient.id == event.recipientId }
        val canSetMemberLabel = member?.recipient?.isSelf == true && state.canSetOwnMemberLabel

        if (canSetMemberLabel && state.memberLabels[event.recipientId] == null) {
          _actions.send(ConversationSettingsAction.NavigateToMemberLabel(groupId))
        } else {
          _actions.send(ConversationSettingsAction.ShowRecipientBottomSheet(event.recipientId, groupId))
        }
      }

      is GroupSettingsEvent.MemberAvatarClicked -> {
        _actions.send(ConversationSettingsAction.ShowRecipientBottomSheet(event.recipientId, groupId))
      }

      GroupSettingsEvent.RevealAllMembersClicked -> {
        _state.update { it.copy(membersExpanded = true) }
      }

      GroupSettingsEvent.GroupLinkClicked -> {
        _actions.send(ConversationSettingsAction.NavigateToShareableGroupLink(groupId))
      }

      GroupSettingsEvent.GroupMemberLabelClicked -> {
        _actions.send(ConversationSettingsAction.NavigateToMemberLabel(groupId))
      }

      GroupSettingsEvent.GroupMemberLabelDisabledClicked -> {
        _actions.send(ConversationSettingsAction.ShowMemberLabelPermissionError)
      }

      GroupSettingsEvent.RequestsAndInvitesClicked -> {
        _actions.send(ConversationSettingsAction.OpenRequestsAndInvites(groupId.requireV2()))
      }

      GroupSettingsEvent.PermissionsClicked -> {
        _actions.send(ConversationSettingsAction.NavigateToPermissions(groupId))
      }

      GroupSettingsEvent.LeaveGroupClicked -> {
        _actions.send(ConversationSettingsAction.ShowLeaveGroupDialog(groupId))
      }

      GroupSettingsEvent.EndGroupClicked -> {
        _actions.send(ConversationSettingsAction.ShowEndGroupDialog(groupId.requireV2(), state.title))
      }

      GroupSettingsEvent.ArchiveChatClicked -> {
        applyArchiveToggle(state)
      }

      GroupSettingsEvent.DeleteChatClicked -> {
        applyDeleteChat(state)
      }

      GroupSettingsEvent.BlockClicked -> {
        _actions.send(BlockAndSpamHandler.blockAction(state.recipient))
      }

      GroupSettingsEvent.BlockConfirmed -> {
        val result = repository.block(groupId)
        if (!result.isSuccess) {
          _actions.send(ConversationSettingsAction.ShowBlockError(result.getFailureReason()))
        }
      }

      GroupSettingsEvent.UnblockConfirmed -> {
        repository.unblock(groupId)
      }

      GroupSettingsEvent.ReportSpamClicked -> {
        _actions.send(BlockAndSpamHandler.reportSpamAction(state.recipient))
      }

      GroupSettingsEvent.ReportSpamConfirmed -> {
        BlockAndSpamHandler.reportSpam(state.recipient, state.threadId, repository) { _actions.send(it) }
      }

      GroupSettingsEvent.BlockAndReportSpamConfirmed -> {
        BlockAndSpamHandler.blockAndReportSpam(state.recipient, state.threadId, repository) { _actions.send(it) }
      }

      GroupSettingsEvent.DialogDismissed -> {
        _state.update { it.copy(dialog = Dialog.None) }
      }

      is GroupSettingsEvent.GroupDetailsChanged -> {
        _state.update { it.applyGroupDetails(event.details, event.isArchived) }
      }

      is GroupSettingsEvent.MemberLabelsLoaded -> {
        _state.update {
          it.copy(
            memberLabels = event.memberLabels,
            canSetOwnMemberLabel = event.canSetOwnMemberLabel
          )
        }
      }

      is GroupSettingsEvent.StoryViewStateChanged -> {
        _state.update { it.copy(storyViewState = event.storyViewState) }
      }

      is GroupSettingsEvent.CallsChanged -> {
        _state.update { it.copy(calls = event.calls) }
      }

      is GroupSettingsEvent.ThreadIdLoaded -> {
        _state.update { it.copy(threadId = event.threadId) }
        mediaRailPresenter.onEvent(RecentMediaRailEvents.SourceChanged(event.threadId))
      }

      is GroupSettingsEvent.MediaRailEvent -> {
        mediaRailPresenter.onEvent(event.event)
      }

      is GroupSettingsEvent.MediaRailStateChanged -> {
        _state.update { it.copy(mediaRail = event.railState) }
      }

      is GroupSettingsEvent.MediaRailAction -> {
        event.action.toConversationSettingsAction(sharedMediaLoader, state.threadId)?.let { _actions.send(it) }
      }
    }
  }

  private suspend fun applyAddMembersClicked() {
    val capacity = repository.getGroupCapacity(groupId)
    if (capacity == null) {
      Log.w(TAG, "No group record to read capacity from, ignoring.")
      return
    }

    if (capacity.getRemainingCapacity() > 0) {
      _actions.send(
        ConversationSettingsAction.AddMembersToGroup(
          groupId = groupId,
          selectionLimits = SelectionLimits(capacity.getSelectionWarning(), capacity.getSelectionLimit()),
          groupMembersWithoutSelf = capacity.getMembersWithoutSelf()
        )
      )
    } else {
      _actions.send(ConversationSettingsAction.ShowGroupHardLimitDialog)
    }
  }

  private suspend fun applyAddMembersSelected(event: GroupSettingsEvent.AddMembersSelected) {
    _state.update { it.copy(dialog = Dialog.AddingMembers) }
    val result = repository.addMembers(groupId, event.recipientIds)
    _state.update { it.copy(dialog = Dialog.None) }

    when (result) {
      is GroupAddMembersResult.Success -> {
        if (result.newMembersInvited.isNotEmpty()) {
          _actions.send(ConversationSettingsAction.ShowGroupInvitesSentDialog(result.newMembersInvited))
        }

        if (result.numberOfMembersAdded > 0) {
          _actions.send(ConversationSettingsAction.ShowMembersAdded(result.numberOfMembersAdded))
        }
      }

      is GroupAddMembersResult.Failure -> _actions.send(ConversationSettingsAction.ShowAddMembersError(result.reason))
    }
  }

  private suspend fun applyArchiveToggle(state: GroupSettingsState) {
    if (state.threadId <= 0) {
      return
    }

    val archived = !state.isArchived
    _state.update { it.copy(isArchived = archived) }
    repository.setArchived(state.threadId, archived)

    if (archived) {
      _actions.send(ConversationSettingsAction.GoToConversationList)
    }
  }

  private suspend fun applyDeleteChat(state: GroupSettingsState) {
    if (state.threadId <= 0) {
      return
    }

    _state.update { it.copy(dialog = Dialog.DeletingChat) }
    repository.deleteChat(state.threadId)
    _state.update { it.copy(dialog = Dialog.None) }
    _actions.send(ConversationSettingsAction.GoToConversationList)
  }

  private suspend fun loadMemberLabels(members: List<Recipient>) {
    val v2GroupId = groupId.v2OrNull() ?: return

    val memberLabels = repository.getMemberLabels(v2GroupId, members)
    val canSetOwnMemberLabel = repository.canSetOwnMemberLabel(v2GroupId)

    onEvent(GroupSettingsEvent.MemberLabelsLoaded(memberLabels, canSetOwnMemberLabel))
  }

  private fun GroupSettingsState.applyGroupDetails(
    details: GroupDetails,
    isArchived: Boolean
  ): GroupSettingsState {
    val recipient = details.recipient

    return copy(
      recipient = recipient,
      recipientContentVersion = if (this.recipient.hasSameContent(recipient)) recipientContentVersion else recipientContentVersion + 1,
      callBar = CallBarState(
        isMessageAvailable = isCallInfoVariant,
        isVideoAvailable = recipient.isPushV2Group && !recipient.isBlocked && recipient.isActiveGroup,
        isAudioAvailable = false,
        isAudioSecure = recipient.isPushV2Group,
        isMuteAvailable = true,
        isMuted = recipient.isMuted,
        isSearchAvailable = !isCallInfoVariant,
        isAddToStoryAvailable = recipient.isPushV2Group && !recipient.isBlocked && recipient.isActiveGroup && repository.isAddToStoryAvailable()
      ),
      disappearingMessagesLifespan = recipient.expiresInSeconds,
      canModifyBlockedState = repository.isBlockable(recipient),
      isArchived = isArchived,
      allMembers = details.members,
      isSelfAdmin = details.isSelfAdmin,
      canAddToGroup = details.canAddMembers,
      canEditGroupAttributes = details.canEditGroupAttributes,
      isActive = details.isActive,
      isTerminated = details.isTerminated,
      title = details.title,
      description = details.description,
      descriptionShouldLinkify = details.descriptionShouldLinkify,
      groupLinkEnabled = details.groupLinkEnabled,
      membershipCountDescription = details.membershipCountDescription,
      legacyGroupState = details.legacyGroupState,
      isAnnouncementGroup = details.isAnnouncementGroup,
      detailsLoaded = true
    )
  }

  class Factory(
    private val groupId: GroupId,
    private val callMessageIds: LongArray,
    private val repository: ConversationSettingsRepository
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return requireNotNull(modelClass.cast(GroupSettingsViewModel(groupId, callMessageIds, repository)))
    }
  }
}
