/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.details

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.components.settings.app.notifications.profiles.NotificationProfileEdits

/**
 * Drives [NotificationProfileDetailsScreen].
 */
class NotificationProfileDetailsViewModel(
  private val profileId: Long
) : EventDrivenViewModel<NotificationProfileDetailsEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(NotificationProfileDetailsViewModel::class)
  }

  private val _state = MutableStateFlow(NotificationProfileDetailsState())
  private val _actions = Channel<NotificationProfileDetailsAction>(Channel.BUFFERED)

  val state: StateFlow<NotificationProfileDetailsState> = _state.asStateFlow()
  val actions: Flow<NotificationProfileDetailsAction> = _actions.receiveAsFlow()

  init {
    viewModelScope.launch {
      NotificationProfileDetailsRepository.observeProfile(profileId)
        .transformWhile { details ->
          emit(details)
          details != null
        }
        .catch { Log.w(TAG, "Unable to read profile.", it) }
        .collect { details ->
          if (details == null) {
            _actions.send(NotificationProfileDetailsAction.NavigateBack)
          } else {
            _state.update { it.copy(profile = details.profile, members = details.members, activeStatus = details.activeStatus) }
          }
        }
    }
  }

  override suspend fun processEvent(event: NotificationProfileDetailsEvent) {
    when (event) {
      NotificationProfileDetailsEvent.NavigateBackClicked -> _actions.send(NotificationProfileDetailsAction.NavigateBack)
      NotificationProfileDetailsEvent.EditClicked -> _actions.send(NotificationProfileDetailsAction.NavigateToEdit(profileId))
      NotificationProfileDetailsEvent.ProfileToggled -> {
        val profile = _state.value.profile ?: return
        NotificationProfileDetailsRepository.toggleProfile(profile)
      }
      NotificationProfileDetailsEvent.AddMembersClicked -> {
        val profile = _state.value.profile ?: return
        _actions.send(NotificationProfileDetailsAction.NavigateToSelectRecipients(profile.id, profile.allowedMembers))
      }
      is NotificationProfileDetailsEvent.RemoveMemberClicked -> {
        val removed = NotificationProfileEdits.removeMember(profileId, event.recipientId)
        _state.update { it.copy(snackbar = NotificationProfileDetailsState.Snackbar.MemberRemoved(removed)) }
      }
      is NotificationProfileDetailsEvent.UndoRemoveMemberClicked -> NotificationProfileEdits.addMember(profileId, event.recipientId)
      NotificationProfileDetailsEvent.SeeAllMembersClicked -> _state.update { it.copy(expanded = true) }
      NotificationProfileDetailsEvent.ScheduleClicked -> _actions.send(NotificationProfileDetailsAction.NavigateToSchedule(profileId))
      is NotificationProfileDetailsEvent.AllowAllCallsToggled -> NotificationProfileEdits.setAllowAllCalls(profileId, event.enabled)
      is NotificationProfileDetailsEvent.NotifyForAllMentionsToggled -> NotificationProfileEdits.setAllowAllMentions(profileId, event.enabled)
      NotificationProfileDetailsEvent.DeleteClicked -> _state.update { it.copy(dialog = NotificationProfileDetailsState.Dialog.ConfirmDelete) }
      NotificationProfileDetailsEvent.DeleteConfirmed -> {
        _state.update { it.copy(dialog = NotificationProfileDetailsState.Dialog.None) }
        NotificationProfileDetailsRepository.deleteProfile(profileId)
      }
      NotificationProfileDetailsEvent.DialogDismissed -> _state.update { it.copy(dialog = NotificationProfileDetailsState.Dialog.None) }
      NotificationProfileDetailsEvent.SnackbarDismissed -> _state.update { it.copy(snackbar = NotificationProfileDetailsState.Snackbar.None) }
    }
  }
}
