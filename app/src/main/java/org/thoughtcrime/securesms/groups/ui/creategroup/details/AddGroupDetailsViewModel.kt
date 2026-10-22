/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups.ui.creategroup.details

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.core.models.media.Media
import org.signal.core.models.recipients.RecipientId
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.BreakIteratorCompat
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.RemoteConfig

/**
 * Drives [AddGroupDetailsScreen].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AddGroupDetailsViewModel(
  recipientIds: List<RecipientId>,
  private val repository: AddGroupDetailsRepository = AddGroupDetailsRepository()
) : EventDrivenViewModel<AddGroupDetailsEvent>(TAG, shouldLogEvents = true) {

  companion object {
    private val TAG = Log.tag(AddGroupDetailsViewModel::class)
  }

  private val _state = MutableStateFlow(AddGroupDetailsState(disappearingMessagesTimer = SignalStore.settings.universalExpireTimer))
  private val _actions = Channel<AddGroupDetailsAction>(Channel.BUFFERED)

  val state: StateFlow<AddGroupDetailsState> = _state.asStateFlow()
  val actions: Flow<AddGroupDetailsAction> = _actions.receiveAsFlow()

  /** The photo the user picked, kept so the avatar picker can reopen on it. */
  private var avatarMedia: Media? = null

  /** The photo the user most recently picked or cleared. Picking again cancels a load that's still running. */
  private val selectedAvatar = MutableStateFlow<Media?>(null)

  init {
    viewModelScope.launch {
      val members = repository.resolveMembers(recipientIds)
      _state.update { it.copy(members = members) }
    }

    _state
      .map { state -> state.members.map { it.id }.toSet() }
      .distinctUntilChanged()
      .mapLatest { memberIds -> if (memberIds.isEmpty()) emptyList() else repository.getGroupsWithSameMembers(memberIds) }
      .onEach { sameGroups -> _state.update { it.copy(sameGroups = sameGroups) } }
      .launchIn(viewModelScope)

    selectedAvatar
      .mapLatest { media -> media to media?.let { repository.loadAvatar(it) } }
      .onEach { (media, avatar) ->
        if (media == null || avatar != null) {
          avatarMedia = media
          _state.update { it.copy(avatar = avatar) }
        }
      }
      .launchIn(viewModelScope)
  }

  override suspend fun processEvent(event: AddGroupDetailsEvent) {
    when (event) {
      AddGroupDetailsEvent.NavigateBackClicked -> {
        _actions.send(AddGroupDetailsAction.NavigateBack)
      }

      is AddGroupDetailsEvent.NameChanged -> {
        _state.update { it.copy(name = truncateGroupName(event.name)) }
      }

      AddGroupDetailsEvent.AvatarClicked -> {
        _actions.send(AddGroupDetailsAction.NavigateToAvatarPicker(avatarMedia))
      }

      is AddGroupDetailsEvent.AvatarSelected -> {
        selectedAvatar.value = event.media
      }

      AddGroupDetailsEvent.AvatarCleared -> {
        selectedAvatar.value = null
      }

      AddGroupDetailsEvent.DisappearingMessagesClicked -> {
        _actions.send(AddGroupDetailsAction.LaunchDisappearingMessagesPicker(_state.value.disappearingMessagesTimer))
      }

      is AddGroupDetailsEvent.DisappearingMessagesTimerSelected -> {
        _state.update { it.copy(disappearingMessagesTimer = event.timer) }
      }

      is AddGroupDetailsEvent.MemberClicked -> {
        val member = _state.value.members.firstOrNull { it.id == event.recipientId } ?: return
        _state.update { it.copy(dialog = AddGroupDetailsState.Dialog.ConfirmRemoveMember(member)) }
      }

      AddGroupDetailsEvent.RemoveMemberConfirmed -> {
        val dialog = _state.value.dialog as? AddGroupDetailsState.Dialog.ConfirmRemoveMember ?: return
        _state.update { state ->
          state.copy(
            members = state.members.filterNot { it.id == dialog.member.id },
            dialog = AddGroupDetailsState.Dialog.None
          )
        }
      }

      is AddGroupDetailsEvent.SameGroupClicked -> {
        val group = _state.value.sameGroups.firstOrNull { it.id == event.groupId } ?: return
        _state.update { it.copy(dialog = AddGroupDetailsState.Dialog.ConfirmDiscard(group)) }
      }

      AddGroupDetailsEvent.DiscardConfirmed -> {
        val dialog = _state.value.dialog as? AddGroupDetailsState.Dialog.ConfirmDiscard ?: return
        _state.update { it.copy(dialog = AddGroupDetailsState.Dialog.None) }
        val threadId = repository.getOrCreateThreadId(dialog.group)
        _actions.send(AddGroupDetailsAction.OpenConversation(dialog.group.id, threadId))
      }

      AddGroupDetailsEvent.DialogDismissed -> {
        _state.update { it.copy(dialog = AddGroupDetailsState.Dialog.None) }
      }

      AddGroupDetailsEvent.CreateClicked -> {
        if (!_state.value.canCreate) {
          return
        }

        _state.update { it.copy(isCreating = true) }
        viewModelScope.launch { createGroup() }
      }
    }
  }

  private fun truncateGroupName(name: String): String {
    val breakIterator = BreakIteratorCompat.getInstance()
    breakIterator.setText(name)
    return breakIterator.take(RemoteConfig.maxGroupNameGraphemeLength).toString()
  }

  private suspend fun createGroup() {
    val state = _state.value

    val result = repository.createGroup(
      members = state.members.map { it.id }.toSet(),
      avatar = state.avatar,
      name = state.name,
      disappearingMessagesTimer = state.disappearingMessagesTimer
    )

    when (result) {
      is GroupCreateResult.Success -> {
        _actions.send(AddGroupDetailsAction.GroupCreated(result.groupId, result.threadId, result.invitedMembers))
      }

      GroupCreateResult.RetryableFailure -> {
        _state.update { it.copy(isCreating = false) }
        _actions.send(AddGroupDetailsAction.ShowTryAgainLater)
      }

      GroupCreateResult.Failure -> {
        _state.update { it.copy(isCreating = false) }
        _actions.send(AddGroupDetailsAction.ShowGroupCreationFailed)
      }
    }
  }
}
