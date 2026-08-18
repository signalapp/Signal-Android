/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.conversation.individual

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
import org.thoughtcrime.securesms.components.settings.conversation.ConversationSettingsKind
import org.thoughtcrime.securesms.components.settings.conversation.ConversationSettingsRepository
import org.thoughtcrime.securesms.components.settings.conversation.individual.IndividualSettingsState.Dialog
import org.thoughtcrime.securesms.components.settings.conversation.shared.BlockAndSpamHandler
import org.thoughtcrime.securesms.components.settings.conversation.shared.CallBarState
import org.thoughtcrime.securesms.components.settings.conversation.shared.SharedMediaLoader
import org.thoughtcrime.securesms.components.settings.conversation.shared.toConversationSettingsAction
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * View model behind all three 1:1 settings screens: [IndividualSettingsScreen], [NoteToSelfSettingsScreen], and
 * [ReleaseNotesSettingsScreen].
 */
class IndividualSettingsViewModel(
  private val recipientId: RecipientId,
  private val kind: ConversationSettingsKind,
  private val callMessageIds: LongArray,
  private val repository: ConversationSettingsRepository
) : EventDrivenViewModel<IndividualSettingsEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(IndividualSettingsViewModel::class)
  }

  /** Whether we're the call-info variant of this screen, which shows a message button in place of search. */
  private val isCallInfoVariant: Boolean = callMessageIds.isNotEmpty()

  /** The release notes chat doesn't show a media rail, so there's no reason to go load one. */
  private val showsSharedMedia: Boolean = kind != ConversationSettingsKind.RELEASE_NOTES

  private val _state = MutableStateFlow(
    IndividualSettingsState(
      isDeprecatedOrUnregistered = repository.isDeprecatedOrUnregistered(),
      starredMessagesEnabled = repository.isStarredMessagesEnabled(),
      displayInternalRecipientDetails = repository.isInternalRecipientDetailsEnabled()
    )
  )

  private val _actions = Channel<ConversationSettingsAction>(Channel.BUFFERED)

  val state: StateFlow<IndividualSettingsState> = _state.asStateFlow()
  val actions: Flow<ConversationSettingsAction> = _actions.receiveAsFlow()

  private val sharedMediaLoader = SharedMediaLoader(repository)
  private val mediaRailPresenter = RecentMediaRailPresenter(viewModelScope, sharedMediaLoader)

  init {
    require(kind != ConversationSettingsKind.GROUP) { "Groups belong to GroupSettingsViewModel" }

    repository
      .observeRecipient(recipientId)
      .onEach { onEvent(IndividualSettingsEvent.RecipientChanged(it)) }
      .launchIn(viewModelScope)

    repository
      .observeStoryViewState(recipientId)
      .onEach { onEvent(IndividualSettingsEvent.StoryViewStateChanged(it)) }
      .launchIn(viewModelScope)

    mediaRailPresenter
      .state
      .onEach { onEvent(IndividualSettingsEvent.MediaRailStateChanged(it)) }
      .launchIn(viewModelScope)

    mediaRailPresenter
      .actions
      .onEach { onEvent(IndividualSettingsEvent.MediaRailAction(it)) }
      .launchIn(viewModelScope)

    if (callMessageIds.isNotEmpty()) {
      repository
        .observeCalls(_state.map { it.threadId }, callMessageIds)
        .onEach { onEvent(IndividualSettingsEvent.CallsChanged(it)) }
        .launchIn(viewModelScope)
    }

    viewModelScope.launch {
      onEvent(IndividualSettingsEvent.ThreadIdLoaded(repository.getThreadId(recipientId)))
    }

    // Neither note to self nor the release notes chat shows groups in common or a safety number, so don't go looking.
    if (kind == ConversationSettingsKind.INDIVIDUAL) {
      repository
        .observeGroupsInCommon(recipientId)
        .onEach { onEvent(IndividualSettingsEvent.GroupsInCommonChanged(it)) }
        .launchIn(viewModelScope)

      viewModelScope.launch {
        onEvent(IndividualSettingsEvent.SelfHasGroupsLoaded(repository.hasGroups()))
      }

      viewModelScope.launch {
        onEvent(IndividualSettingsEvent.IdentityRecordLoaded(repository.getIdentity(recipientId)))
      }
    }
  }

  override suspend fun processEvent(event: IndividualSettingsEvent) {
    val state = _state.value

    when (event) {
      IndividualSettingsEvent.AvatarClicked -> {
        BlockAndSpamHandler.avatarClickAction(state.recipient, state.storyViewState, repository.isStoriesFeatureEnabled())?.let { _actions.send(it) }
      }
      is IndividualSettingsEvent.BadgeClicked -> {
        _actions.send(ConversationSettingsAction.ShowBadgeSheet(recipientId, event.badge))
      }
      IndividualSettingsEvent.HeadlineClicked -> {
        _actions.send(ConversationSettingsAction.ShowAboutSheet(state.recipient))
      }
      IndividualSettingsEvent.InternalDetailsClicked -> {
        _actions.send(ConversationSettingsAction.NavigateToInternalDetails(recipientId))
      }
      IndividualSettingsEvent.MessageClicked -> {
        _actions.send(ConversationSettingsAction.OpenConversation(recipientId, state.threadId))
      }
      IndividualSettingsEvent.VideoCallClicked -> {
        _actions.send(ConversationSettingsAction.StartVideoCall(state.recipient))
      }
      IndividualSettingsEvent.AudioCallClicked -> {
        _actions.send(ConversationSettingsAction.StartAudioCall(state.recipient))
      }
      IndividualSettingsEvent.MuteClicked -> {
        _state.update { it.copy(dialog = if (state.callBar.isMuted) Dialog.Unmute else Dialog.MuteMenu) }
      }
      is IndividualSettingsEvent.MuteDurationSelected -> {
        _state.update { it.copy(dialog = Dialog.None) }
        repository.setMuteUntil(recipientId, event.muteUntil)
      }
      IndividualSettingsEvent.MuteUntilCustomTimeClicked -> {
        _state.update { it.copy(dialog = Dialog.None) }
        _actions.send(ConversationSettingsAction.ShowMuteUntilTimePicker)
      }
      IndividualSettingsEvent.UnmuteConfirmed -> {
        _state.update { it.copy(dialog = Dialog.None) }
        repository.setMuteUntil(recipientId, 0)
      }
      IndividualSettingsEvent.SearchClicked -> {
        _actions.send(ConversationSettingsAction.OpenConversation(recipientId, state.threadId, withSearchOpen = true))
      }
      IndividualSettingsEvent.DisappearingMessagesClicked -> {
        _actions.send(ConversationSettingsAction.NavigateToDisappearingMessages(recipientId, state.disappearingMessagesLifespan))
      }
      IndividualSettingsEvent.NicknameClicked -> {
        _actions.send(ConversationSettingsAction.EditNickname(recipientId))
      }
      IndividualSettingsEvent.ChatColorAndWallpaperClicked -> {
        _actions.send(ConversationSettingsAction.OpenChatWallpaper(recipientId))
      }
      IndividualSettingsEvent.SoundsAndNotificationsClicked -> {
        _actions.send(ConversationSettingsAction.NavigateToSoundsAndNotifications(recipientId))
      }
      IndividualSettingsEvent.StarredMessagesClicked -> {
        _actions.send(ConversationSettingsAction.OpenStarredMessages(state.threadId))
      }
      IndividualSettingsEvent.ContactDetailsClicked -> {
        _actions.send(ConversationSettingsAction.ViewContact(state.recipient))
      }
      IndividualSettingsEvent.AddAsContactClicked -> {
        _actions.send(ConversationSettingsAction.AddContact(state.recipient))
      }
      IndividualSettingsEvent.ViewSafetyNumberClicked -> {
        val identityRecord = repository.getIdentity(recipientId)
        _state.update { it.copy(identityRecord = identityRecord) }
        _actions.send(ConversationSettingsAction.ShowSafetyNumber(identityRecord))
      }
      IndividualSettingsEvent.SupportCenterClicked -> {
        _actions.send(ConversationSettingsAction.OpenSupportCenter)
      }
      IndividualSettingsEvent.ContactUsClicked -> {
        _actions.send(ConversationSettingsAction.OpenContactUs)
      }
      IndividualSettingsEvent.DonateClicked -> {
        _actions.send(ConversationSettingsAction.OpenDonate)
      }
      IndividualSettingsEvent.AddToAGroupClicked -> {
        val groupMembership = repository.getGroupMembership(recipientId)
        _actions.send(ConversationSettingsAction.AddToAGroup(recipientId, groupMembership))
      }
      is IndividualSettingsEvent.GroupInCommonClicked -> {
        val group = state.allGroupsInCommon.firstOrNull { it.id == event.recipientId }
        if (group != null) {
          _actions.send(ConversationSettingsAction.OpenGroupConversation(group))
        }
      }
      IndividualSettingsEvent.RevealAllGroupsInCommonClicked -> {
        _state.update { it.copy(groupsInCommonExpanded = true) }
      }
      IndividualSettingsEvent.BlockClicked -> {
        _actions.send(BlockAndSpamHandler.blockAction(state.recipient))
      }
      IndividualSettingsEvent.BlockConfirmed -> {
        val result = repository.block(recipientId)
        if (!result.isSuccess) {
          _actions.send(ConversationSettingsAction.ShowBlockError(result.getFailureReason()))
        }
      }
      IndividualSettingsEvent.UnblockConfirmed -> {
        repository.unblock(recipientId)
      }
      IndividualSettingsEvent.ReportSpamClicked -> {
        _actions.send(BlockAndSpamHandler.reportSpamAction(state.recipient))
      }
      IndividualSettingsEvent.ReportSpamConfirmed -> {
        BlockAndSpamHandler.reportSpam(state.recipient, state.threadId, repository) { _actions.send(it) }
      }
      IndividualSettingsEvent.BlockAndReportSpamConfirmed -> {
        BlockAndSpamHandler.blockAndReportSpam(state.recipient, state.threadId, repository) { _actions.send(it) }
      }
      IndividualSettingsEvent.DialogDismissed -> {
        _state.update { it.copy(dialog = Dialog.None) }
      }
      IndividualSettingsEvent.RecipientRefreshRequested -> {
        repository.refreshRecipient(recipientId)
      }
      is IndividualSettingsEvent.RecipientChanged -> {
        _state.update { it.applyRecipient(event.recipient) }
      }
      is IndividualSettingsEvent.StoryViewStateChanged -> {
        _state.update { it.copy(storyViewState = event.storyViewState) }
      }
      is IndividualSettingsEvent.CallsChanged -> {
        _state.update { it.copy(calls = event.calls) }
      }
      is IndividualSettingsEvent.ThreadIdLoaded -> {
        _state.update { it.copy(threadId = event.threadId) }
        if (showsSharedMedia) {
          mediaRailPresenter.onEvent(RecentMediaRailEvents.SourceChanged(event.threadId))
        }
      }
      is IndividualSettingsEvent.GroupsInCommonChanged -> {
        _state.update { it.copy(allGroupsInCommon = event.groupsInCommon) }
      }
      is IndividualSettingsEvent.SelfHasGroupsLoaded -> {
        _state.update { it.copy(selfHasGroups = event.selfHasGroups) }
      }
      is IndividualSettingsEvent.IdentityRecordLoaded -> {
        _state.update { it.copy(identityRecord = event.identityRecord) }
      }
      is IndividualSettingsEvent.MediaRailEvent -> {
        mediaRailPresenter.onEvent(event.event)
      }
      is IndividualSettingsEvent.MediaRailStateChanged -> {
        _state.update { it.copy(mediaRail = event.railState) }
      }
      is IndividualSettingsEvent.MediaRailAction -> {
        event.action.toConversationSettingsAction(sharedMediaLoader, state.threadId)?.let { _actions.send(it) }
      }
    }
  }

  private fun IndividualSettingsState.applyRecipient(recipient: Recipient): IndividualSettingsState {
    val isReachable = !recipient.isBlocked && !recipient.isSelf && !recipient.isReleaseNotes

    return copy(
      recipient = recipient,
      recipientContentVersion = if (this.recipient.hasSameContent(recipient)) recipientContentVersion else recipientContentVersion + 1,
      callBar = CallBarState(
        isMessageAvailable = isCallInfoVariant,
        isVideoAvailable = recipient.isRegistered && isReachable,
        isAudioAvailable = recipient.isRegistered && isReachable,
        isAudioSecure = recipient.isRegistered,
        isMuteAvailable = !recipient.isSelf,
        isMuted = recipient.isMuted,
        isSearchAvailable = !isCallInfoVariant
      ),
      disappearingMessagesLifespan = recipient.expiresInSeconds,
      canModifyBlockedState = !recipient.isSelf && repository.isBlockable(recipient),
      contactLinkState = when {
        recipient.isSelf || recipient.isReleaseNotes || recipient.isBlocked -> ContactLinkState.NONE
        recipient.isSystemContact -> ContactLinkState.OPEN
        recipient.hasE164 && recipient.shouldShowE164 -> ContactLinkState.ADD
        else -> ContactLinkState.NONE
      }
    )
  }

  class Factory(
    private val recipientId: RecipientId,
    private val kind: ConversationSettingsKind,
    private val callMessageIds: LongArray,
    private val repository: ConversationSettingsRepository
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return requireNotNull(modelClass.cast(IndividualSettingsViewModel(recipientId, kind, callMessageIds, repository)))
    }
  }
}
