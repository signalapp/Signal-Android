/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.recipients.ui.sharablegrouplink

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import org.signal.chatsettings.screens.sharablegrouplink.ShareableGroupLinkEvents
import org.signal.chatsettings.screens.sharablegrouplink.ShareableGroupLinkState
import org.signal.chatsettings.screens.sharablegrouplink.ShareableGroupLinkState.Dialog
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.groups.ui.GroupChangeResult
import org.thoughtcrime.securesms.groups.ui.GroupErrors

/**
 * View model behind [ShareableGroupLinkScreen].
 */
class ShareableGroupLinkViewModel(
  private val groupId: GroupId.V2,
  private val repository: ShareableGroupLinkRepository = ShareableGroupLinkRepository()
) : EventDrivenViewModel<ShareableGroupLinkEvents>(TAG, false) {

  companion object {
    private val TAG = Log.tag(ShareableGroupLinkViewModel::class)
  }

  private val _state = MutableStateFlow(ShareableGroupLinkState())

  val state: StateFlow<ShareableGroupLinkState> = _state.asStateFlow()

  init {
    repository
      .observeGroupLink(groupId)
      .onEach { onEvent(ShareableGroupLinkEvents.GroupLinkChanged(it)) }
      .launchIn(viewModelScope)
  }

  override suspend fun processEvent(event: ShareableGroupLinkEvents) {
    when (event) {
      is ShareableGroupLinkEvents.GroupLinkChanged -> {
        _state.update { it.copy(groupLink = event.groupLink) }
      }

      ShareableGroupLinkEvents.GroupLinkToggled -> {
        val current = _state.value.groupLink

        applyChange {
          repository.setGroupLinkState(
            groupId = groupId,
            enabled = !current.enabled,
            requiresAdminApproval = current.requiresAdminApproval
          )
        }
      }

      ShareableGroupLinkEvents.AdminApprovalToggled -> {
        val current = _state.value.groupLink

        applyChange {
          repository.setGroupLinkState(
            groupId = groupId,
            enabled = current.enabled,
            requiresAdminApproval = !current.requiresAdminApproval
          )
        }
      }

      ShareableGroupLinkEvents.ResetLinkClicked -> {
        _state.update { it.copy(dialog = Dialog.ConfirmResetLink) }
      }

      ShareableGroupLinkEvents.ResetLinkConfirmed -> {
        _state.update { it.copy(dialog = Dialog.None) }
        applyChange { repository.cycleGroupLinkPassword(groupId) }
      }

      ShareableGroupLinkEvents.DialogDismissed -> {
        _state.update { it.copy(dialog = Dialog.None) }
      }

      ShareableGroupLinkEvents.SnackbarDismissed -> {
        _state.update { it.copy(errorMessage = null) }
      }
    }
  }

  /**
   * Runs a group change while the screen shows a spinner over itself. Changes have to be applied one at a time, since
   * each toggle is relative to the link's current state.
   */
  private suspend fun applyChange(change: suspend () -> GroupChangeResult) {
    _state.update { it.copy(busy = true) }

    val result = change()

    _state.update { it.copy(busy = false) }

    if (!result.isSuccess) {
      _state.update { it.copy(errorMessage = GroupErrors.getUserDisplayMessage(result.failureReason)) }
    }
  }
}
