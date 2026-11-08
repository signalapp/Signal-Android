/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.storage

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.appsettings.managestorage.ManageStorageSettingsAction
import org.signal.appsettings.managestorage.ManageStorageSettingsEvent
import org.signal.appsettings.managestorage.ManageStorageSettingsState
import org.signal.appsettings.managestorage.ManageStorageSettingsState.Dialog
import org.signal.appsettings.managestorage.ManageStorageSettingsState.OnDeviceStorageOptimizationState
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.appsettings.routes.navigateBack
import org.signal.appsettings.routes.navigateTo
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.keyvalue.SignalStore

/**
 * Drives [org.signal.appsettings.managestorage.ManageStorageSettingsScreen].
 */
class ManageStorageSettingsViewModel(
  private val parentEventEmitter: (AppSettingsNavigationEvent) -> Unit,
  private val repository: ManageStorageSettingsRepository = ManageStorageSettingsRepository(),
  private val storageUsageRepository: StorageUsageRepository = StorageUsageRepository.instance
) : EventDrivenViewModel<ManageStorageSettingsEvent>(TAG, shouldLogEvents = true) {

  companion object {
    private val TAG = Log.tag(ManageStorageSettingsViewModel::class)
  }

  private val _state = MutableStateFlow(
    ManageStorageSettingsState(
      keepMessagesDuration = SignalStore.settings.keepMessagesDuration,
      lengthLimit = repository.getLengthLimit(),
      syncTrimDeletes = SignalStore.settings.syncThreadTrimDeletes,
      isPrimaryDevice = SignalStore.account.isPrimaryDevice,
      isMultiDevice = SignalStore.account.isMultiDevice
    )
  )
  private val _actions = Channel<ManageStorageSettingsAction>(Channel.BUFFERED)

  val state: StateFlow<ManageStorageSettingsState> = _state.asStateFlow()
  val actions: Flow<ManageStorageSettingsAction> = _actions.receiveAsFlow()

  /** What optimized storage was when the screen opened, so we only kick off an offload or restore if it changed. */
  private val initialOptimizeStorage = SignalStore.backup.optimizeStorage

  /** One-time check on Play Billing to determine whether Optimize Storage can be offered. */
  private var optimizeStorageAvailable = false

  private val isPaidTierPending: StateFlow<Boolean> = repository.observeIsPaidTierPending()
    .stateIn(viewModelScope, SharingStarted.Eagerly, false)

  init {
    storageUsageRepository.usage
      .onEach { usage -> _state.update { it.copy(storageUsage = usage) } }
      .launchIn(viewModelScope)

    viewModelScope.launch {
      optimizeStorageAvailable = repository.isOptimizeStorageAvailable()
      _state.update { it.copy(onDeviceStorageOptimizationState = getOnDeviceStorageOptimizationState()) }
    }
  }

  override suspend fun processEvent(event: ManageStorageSettingsEvent) {
    when (event) {
      ManageStorageSettingsEvent.ScreenResumed -> refresh()
      ManageStorageSettingsEvent.NavigateBackClicked -> parentEventEmitter.navigateBack()
      ManageStorageSettingsEvent.ReviewStorageClicked -> _actions.send(ManageStorageSettingsAction.NavigateToReviewStorage)
      is ManageStorageSettingsEvent.OptimizeStorageToggled -> applyOptimizeStorageToggled(event.enabled)
      ManageStorageSettingsEvent.OptimizeStorageWithLocalBackupConfirmed -> applyOptimizeStorageWithLocalBackupConfirmed()
      ManageStorageSettingsEvent.PaidTierUpgradeCompleted -> setOptimizeStorage(true)
      ManageStorageSettingsEvent.KeepMessagesClicked -> parentEventEmitter.navigateTo(AppSettingsRoute.DataAndStorageRoute.KeepMessages)
      ManageStorageSettingsEvent.ChatLengthLimitClicked -> parentEventEmitter.navigateTo(AppSettingsRoute.DataAndStorageRoute.ChatLengthLimit)
      is ManageStorageSettingsEvent.SyncTrimDeletesToggled -> setSyncTrimDeletes(event.enabled)
      ManageStorageSettingsEvent.DeleteChatHistoryClicked -> _state.update { it.copy(dialog = Dialog.ConfirmDeleteChatHistory) }
      ManageStorageSettingsEvent.DeleteChatHistoryConfirmed -> applyDeleteChatHistoryConfirmed()
      ManageStorageSettingsEvent.DialogDismissed -> _state.update { it.copy(dialog = Dialog.None) }
    }
  }

  override fun onCleared() {
    when (_state.value.onDeviceStorageOptimizationState) {
      OnDeviceStorageOptimizationState.ENABLED -> if (!initialOptimizeStorage) repository.offloadOptimizedMedia()
      OnDeviceStorageOptimizationState.DISABLED -> if (initialOptimizeStorage) repository.restoreOptimizedMedia()
      OnDeviceStorageOptimizationState.FEATURE_NOT_AVAILABLE, OnDeviceStorageOptimizationState.REQUIRES_PAID_TIER -> Unit
    }
  }

  /** Re-reads what the chat limit screens may have changed while we were in the background, and recomputes usage. */
  private fun refresh() {
    storageUsageRepository.refresh()
    _state.update {
      it.copy(
        keepMessagesDuration = SignalStore.settings.keepMessagesDuration,
        lengthLimit = repository.getLengthLimit(),
        isMultiDevice = SignalStore.account.isMultiDevice,
        onDeviceStorageOptimizationState = getOnDeviceStorageOptimizationState()
      )
    }
  }

  private fun getOnDeviceStorageOptimizationState(): OnDeviceStorageOptimizationState {
    return when {
      !optimizeStorageAvailable -> OnDeviceStorageOptimizationState.FEATURE_NOT_AVAILABLE
      SignalStore.backup.backupTier != MessageBackupTier.PAID -> OnDeviceStorageOptimizationState.REQUIRES_PAID_TIER
      SignalStore.backup.optimizeStorage -> OnDeviceStorageOptimizationState.ENABLED
      else -> OnDeviceStorageOptimizationState.DISABLED
    }
  }

  private suspend fun applyOptimizeStorageToggled(enabled: Boolean) {
    when {
      isPaidTierPending.value -> _state.update { it.copy(dialog = Dialog.PaidTierPending) }
      _state.value.onDeviceStorageOptimizationState == OnDeviceStorageOptimizationState.REQUIRES_PAID_TIER -> _actions.send(ManageStorageSettingsAction.ShowUpgradeToEnableOptimizedStorage)
      enabled && SignalStore.backup.newLocalBackupsEnabled -> _state.update { it.copy(dialog = Dialog.ConfirmOptimizeStorageWithLocalBackup) }
      else -> setOptimizeStorage(enabled)
    }
  }

  private fun applyOptimizeStorageWithLocalBackupConfirmed() {
    if (_state.value.dialog != Dialog.ConfirmOptimizeStorageWithLocalBackup) return
    _state.update { it.copy(dialog = Dialog.None) }
    setOptimizeStorage(true)
  }

  private fun setOptimizeStorage(enabled: Boolean) {
    val current = getOnDeviceStorageOptimizationState()
    if (current != OnDeviceStorageOptimizationState.DISABLED && current != OnDeviceStorageOptimizationState.ENABLED) {
      return
    }

    SignalStore.backup.optimizeStorage = enabled
    _state.update { it.copy(onDeviceStorageOptimizationState = if (enabled) OnDeviceStorageOptimizationState.ENABLED else OnDeviceStorageOptimizationState.DISABLED) }
  }

  private fun setSyncTrimDeletes(enabled: Boolean) {
    SignalStore.settings.syncThreadTrimDeletes = enabled
    _state.update { it.copy(syncTrimDeletes = enabled) }
  }

  private fun applyDeleteChatHistoryConfirmed() {
    when (_state.value.dialog) {
      Dialog.ConfirmDeleteChatHistory -> {
        _state.update { it.copy(dialog = Dialog.ReconfirmDeleteChatHistory) }
      }
      Dialog.ReconfirmDeleteChatHistory -> {
        _state.update { it.copy(dialog = Dialog.None) }
        repository.deleteAllConversations()
      }
      else -> Unit
    }
  }
}
