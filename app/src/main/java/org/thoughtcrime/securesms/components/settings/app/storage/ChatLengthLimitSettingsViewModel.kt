/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.storage

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.signal.appsettings.managestorage.ChatLengthLimitSettingsEvent
import org.signal.appsettings.managestorage.ChatLengthLimitSettingsState
import org.signal.appsettings.managestorage.ChatLengthLimitSettingsState.Dialog
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.navigateBack
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log

/**
 * Drives [org.signal.appsettings.managestorage.ChatLengthLimitSettingsScreen].
 */
class ChatLengthLimitSettingsViewModel(
  private val parentEventEmitter: (AppSettingsNavigationEvent) -> Unit,
  private val repository: ManageStorageSettingsRepository = ManageStorageSettingsRepository()
) : EventDrivenViewModel<ChatLengthLimitSettingsEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(ChatLengthLimitSettingsViewModel::class)
  }

  private val _state = MutableStateFlow(ChatLengthLimitSettingsState(lengthLimit = repository.getLengthLimit()))

  val state: StateFlow<ChatLengthLimitSettingsState> = _state.asStateFlow()

  override suspend fun processEvent(event: ChatLengthLimitSettingsEvent) {
    when (event) {
      ChatLengthLimitSettingsEvent.NavigateBackClicked -> {
        parentEventEmitter.navigateBack()
      }
      is ChatLengthLimitSettingsEvent.LimitSelected -> {
        applyLimitSelected(event.limit)
      }
      ChatLengthLimitSettingsEvent.CustomClicked -> {
        val currentLimit = _state.value.lengthLimit.takeIf { it != ChatLengthLimitSettingsState.NO_LIMIT }
        _state.update { it.copy(dialog = Dialog.CustomLimit(initialLimit = currentLimit)) }
      }
      is ChatLengthLimitSettingsEvent.CustomLimitEntered -> {
        if (_state.value.dialog !is Dialog.CustomLimit) return
        _state.update { it.copy(dialog = Dialog.None) }
        applyLimitSelected(event.limit)
      }
      ChatLengthLimitSettingsEvent.LimitConfirmed -> {
        val dialog = _state.value.dialog as? Dialog.ConfirmLimit ?: return
        _state.update { it.copy(dialog = Dialog.None) }
        setLimit(dialog.limit)
      }
      ChatLengthLimitSettingsEvent.DialogDismissed -> {
        _state.update { it.copy(dialog = Dialog.None) }
      }
    }
  }

  private fun applyLimitSelected(limit: Int) {
    if (trimsMoreMessages(limit)) {
      _state.update { it.copy(dialog = Dialog.ConfirmLimit(limit)) }
    } else {
      setLimit(limit)
    }
  }

  private fun trimsMoreMessages(limit: Int): Boolean {
    val current = _state.value.lengthLimit
    return limit != ChatLengthLimitSettingsState.NO_LIMIT && (current == ChatLengthLimitSettingsState.NO_LIMIT || limit < current)
  }

  private fun setLimit(limit: Int) {
    val trim = trimsMoreMessages(limit)

    repository.setLengthLimit(limit)
    _state.update { it.copy(lengthLimit = limit) }

    if (trim) {
      repository.trimAllThreads(limit)
    }
  }
}
