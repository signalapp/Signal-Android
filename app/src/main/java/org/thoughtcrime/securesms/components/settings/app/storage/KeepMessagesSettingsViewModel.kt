/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.storage

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.signal.appsettings.managestorage.KeepMessagesSettingsEvent
import org.signal.appsettings.managestorage.KeepMessagesSettingsState
import org.signal.appsettings.managestorage.KeepMessagesSettingsState.Dialog
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.navigateBack
import org.signal.core.models.storage.KeepMessagesDuration
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.keyvalue.SignalStore

/**
 * Drives [org.signal.appsettings.managestorage.KeepMessagesSettingsScreen].
 */
class KeepMessagesSettingsViewModel(
  private val parentEventEmitter: (AppSettingsNavigationEvent) -> Unit,
  private val repository: ManageStorageSettingsRepository = ManageStorageSettingsRepository()
) : EventDrivenViewModel<KeepMessagesSettingsEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(KeepMessagesSettingsViewModel::class)
  }

  private val _state = MutableStateFlow(KeepMessagesSettingsState(selection = SignalStore.settings.keepMessagesDuration))

  val state: StateFlow<KeepMessagesSettingsState> = _state.asStateFlow()

  override suspend fun processEvent(event: KeepMessagesSettingsEvent) {
    when (event) {
      KeepMessagesSettingsEvent.NavigateBackClicked -> {
        parentEventEmitter.navigateBack()
      }
      is KeepMessagesSettingsEvent.DurationSelected -> {
        val deletesMoreMessages = event.duration.ordinal > _state.value.selection.ordinal
        if (deletesMoreMessages) {
          _state.update { it.copy(dialog = Dialog.ConfirmDuration(event.duration)) }
        } else {
          setDuration(event.duration)
        }
      }
      KeepMessagesSettingsEvent.DurationConfirmed -> {
        val dialog = _state.value.dialog as? Dialog.ConfirmDuration ?: return
        _state.update { it.copy(dialog = Dialog.None) }
        setDuration(dialog.duration)
      }
      KeepMessagesSettingsEvent.DialogDismissed -> {
        _state.update { it.copy(dialog = Dialog.None) }
      }
    }
  }

  private fun setDuration(duration: KeepMessagesDuration) {
    repository.setKeepMessagesDuration(duration)
    _state.update { it.copy(selection = duration) }
  }
}
