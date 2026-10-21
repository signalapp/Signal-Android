/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.allowedmembers

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log

/**
 * Drives [AddAllowedMembersScreen].
 */
class AddAllowedMembersViewModel(
  private val profileId: Long
) : EventDrivenViewModel<AddAllowedMembersEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(AddAllowedMembersViewModel::class)
  }

  private val _state = MutableStateFlow(AddAllowedMembersState())
  private val _actions = Channel<AddAllowedMembersAction>(Channel.BUFFERED)

  val state: StateFlow<AddAllowedMembersState> = _state.asStateFlow()
  val actions: Flow<AddAllowedMembersAction> = _actions.receiveAsFlow()

  init {
    viewModelScope.launch {
      AddAllowedMembersRepository.observeProfile(profileId)
        .catch { Log.w(TAG, "Unable to read profile.", it) }
        .collect { profileAndMembers ->
          _state.update { it.copy(profile = profileAndMembers.profile, members = profileAndMembers.members) }
        }
    }
  }

  override suspend fun processEvent(event: AddAllowedMembersEvent) {
    when (event) {
      AddAllowedMembersEvent.NavigateBackClicked -> _actions.send(AddAllowedMembersAction.NavigateBack)
      AddAllowedMembersEvent.AddMembersClicked -> {
        val profile = _state.value.profile ?: return
        _actions.send(AddAllowedMembersAction.NavigateToSelectRecipients(profile.id, profile.allowedMembers))
      }
      is AddAllowedMembersEvent.RemoveMemberClicked -> {
        val removed = AddAllowedMembersRepository.removeMember(profileId, event.recipientId)
        _state.update { it.copy(snackbar = AddAllowedMembersState.Snackbar.MemberRemoved(removed)) }
      }
      is AddAllowedMembersEvent.UndoRemoveMemberClicked -> AddAllowedMembersRepository.addMember(profileId, event.recipientId)
      is AddAllowedMembersEvent.AllowAllCallsToggled -> AddAllowedMembersRepository.setAllowAllCalls(profileId, event.enabled)
      is AddAllowedMembersEvent.NotifyForAllMentionsToggled -> AddAllowedMembersRepository.setAllowAllMentions(profileId, event.enabled)
      AddAllowedMembersEvent.NextClicked -> _actions.send(AddAllowedMembersAction.NavigateToSchedule(profileId))
      AddAllowedMembersEvent.SnackbarDismissed -> _state.update { it.copy(snackbar = AddAllowedMembersState.Snackbar.None) }
    }
  }
}
