/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.data

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import org.signal.appsettings.dataandstorage.AutoDownloadNetwork
import org.signal.appsettings.dataandstorage.DataAndStorageSettingsAction
import org.signal.appsettings.dataandstorage.DataAndStorageSettingsEvent
import org.signal.appsettings.dataandstorage.DataAndStorageSettingsState
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log

/**
 * Drives [org.signal.appsettings.dataandstorage.DataAndStorageSettingsScreen].
 */
class DataAndStorageSettingsViewModel : EventDrivenViewModel<DataAndStorageSettingsEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(DataAndStorageSettingsViewModel::class)
  }

  private val _state = MutableStateFlow(DataAndStorageSettingsState())
  private val _actions = Channel<DataAndStorageSettingsAction>(Channel.BUFFERED)

  val state: StateFlow<DataAndStorageSettingsState> = _state.asStateFlow()
  val actions: Flow<DataAndStorageSettingsAction> = _actions.receiveAsFlow()

  init {
    refresh()

    DataAndStorageSettingsRepository.totalStorageUseBytes()
      .onEach { total -> _state.update { it.copy(totalStorageUseBytes = total) } }
      .launchIn(viewModelScope)
  }

  override suspend fun processEvent(event: DataAndStorageSettingsEvent) {
    when (event) {
      DataAndStorageSettingsEvent.ScreenResumed -> {
        refresh()
        DataAndStorageSettingsRepository.refreshStorageUsage()
      }

      DataAndStorageSettingsEvent.NavigateBackClicked -> _actions.send(DataAndStorageSettingsAction.NavigateBack)

      DataAndStorageSettingsEvent.ManageStorageClicked -> _actions.send(DataAndStorageSettingsAction.NavigateToManageStorage)

      is DataAndStorageSettingsEvent.AutoDownloadChanged -> {
        DataAndStorageSettingsRepository.setAutoDownload(event.network, event.mediaTypes)
        refresh()
      }

      is DataAndStorageSettingsEvent.SentMediaQualitySelected -> {
        DataAndStorageSettingsRepository.setSentMediaQuality(event.quality)
        refresh()
      }

      is DataAndStorageSettingsEvent.CallDataModeSelected -> {
        DataAndStorageSettingsRepository.setCallDataMode(event.mode)
        refresh()
      }

      is DataAndStorageSettingsEvent.StayConnectedInBackgroundToggled -> {
        if (event.enabled) {
          _state.update { it.copy(dialog = DataAndStorageSettingsState.Dialog.ConfirmStayConnectedInBackground) }
        } else {
          DataAndStorageSettingsRepository.setForceWebsocketModeEnabled(false)
          refresh()
        }
      }

      DataAndStorageSettingsEvent.StayConnectedInBackgroundConfirmed -> {
        if (_state.value.dialog != DataAndStorageSettingsState.Dialog.ConfirmStayConnectedInBackground) return
        _state.update { it.copy(dialog = DataAndStorageSettingsState.Dialog.None) }
        DataAndStorageSettingsRepository.setForceWebsocketModeEnabled(true)
        refresh()
      }

      DataAndStorageSettingsEvent.UseProxyClicked -> _actions.send(DataAndStorageSettingsAction.NavigateToProxy)

      DataAndStorageSettingsEvent.DialogDismissed -> _state.update { it.copy(dialog = DataAndStorageSettingsState.Dialog.None) }
    }
  }

  private fun refresh() {
    _state.update {
      it.copy(
        autoDownload = AutoDownloadNetwork.entries.associateWith { network -> DataAndStorageSettingsRepository.getAutoDownload(network) },
        alwaysAutoDownloadedBytes = DataAndStorageSettingsRepository.getAlwaysAutoDownloadedBytes(),
        sentMediaQuality = DataAndStorageSettingsRepository.getSentMediaQuality(),
        callDataMode = DataAndStorageSettingsRepository.getCallDataMode(),
        forceWebsocketMode = DataAndStorageSettingsRepository.isForceWebsocketModeEnabled(),
        playServicesAvailable = DataAndStorageSettingsRepository.isPlayServicesAvailable(),
        isProxyEnabled = DataAndStorageSettingsRepository.isProxyEnabled()
      )
    }
  }
}
