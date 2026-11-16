/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.backups

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.appsettings.routes.navigateBack
import org.signal.appsettings.routes.navigateTo
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.concurrent.SignalDispatchers
import org.signal.core.util.logging.Log
import kotlin.time.Duration.Companion.milliseconds

/**
 * Drives the top-level backups settings screen.
 *
 * @param launchCheckoutFlow True when the screen was opened to start backups checkout straight away, which only a
 *   primary device can do. [savedStateHandle] remembers that it was started, so restoring the screen doesn't start it
 *   again.
 */
class BackupsSettingsViewModel(
  private val parentEventEmitter: (AppSettingsNavigationEvent) -> Unit,
  launchCheckoutFlow: Boolean = false,
  savedStateHandle: SavedStateHandle = SavedStateHandle(),
  private val repository: BackupsSettingsRepository = BackupsSettingsRepository
) : EventDrivenViewModel<BackupsSettingsEvent>(TAG, shouldLogEvents = true) {

  companion object {
    private val TAG = Log.tag(BackupsSettingsViewModel::class)

    private const val KEY_CHECKOUT_LAUNCHED = "checkout_launched"
    private const val LEARN_MORE_URL = "https://support.signal.org/hc/articles/360007059752"
  }

  private val backupState = repository.observeBackupState(viewModelScope)

  private val _state = MutableStateFlow(
    BackupsSettingsState(
      backupState = backupState.value,
      lastBackupAt = repository.lastBackupTime().milliseconds,
      isLinkedDevice = repository.isLinkedDevice()
    )
  )
  private val _actions = Channel<BackupsSettingsAction>(Channel.BUFFERED)

  val state: StateFlow<BackupsSettingsState> = _state.asStateFlow()
  val actions: Flow<BackupsSettingsAction> = _actions.receiveAsFlow()

  init {
    viewModelScope.launch {
      backupState.collect { backupState ->
        Log.d(TAG, "Found enabled state $backupState. Updating UI state.")
        _state.update {
          it.copy(
            backupState = backupState,
            lastBackupAt = repository.lastBackupTime().milliseconds,
            showBackupTierInternalOverride = repository.canOverrideBackupTier(),
            backupTierInternalOverride = repository.backupTierInternalOverride()
          )
        }
      }
    }

    viewModelScope.launch(SignalDispatchers.Default) {
      repository.lastBackupTimeFlow().collect { lastBackupTime ->
        _state.update { it.copy(lastBackupAt = lastBackupTime.milliseconds) }
      }
    }

    if (repository.isLinkedDevice()) {
      viewModelScope.launch { repository.refreshBackupFileTimestamp() }
    }

    if (launchCheckoutFlow && repository.isPrimaryDevice() && savedStateHandle.get<Boolean>(KEY_CHECKOUT_LAUNCHED) != true) {
      savedStateHandle[KEY_CHECKOUT_LAUNCHED] = true
      _actions.trySend(BackupsSettingsAction.LaunchCheckout)
    }
  }

  override suspend fun processEvent(event: BackupsSettingsEvent) {
    when (event) {
      BackupsSettingsEvent.NavigateBackClicked -> {
        parentEventEmitter.navigateBack()
      }
      BackupsSettingsEvent.BackupsRowClicked -> {
        val state = _state.value
        when (state.backupState) {
          is BackupState.Error -> Unit
          BackupState.None -> {
            if (!state.isLinkedDevice) {
              _actions.send(BackupsSettingsAction.LaunchCheckout)
            }
          }
          else -> parentEventEmitter.navigateTo(AppSettingsRoute.BackupsRoute.Remote())
        }
      }
      BackupsSettingsEvent.OnDeviceBackupsRowClicked -> {
        parentEventEmitter.navigateTo(AppSettingsRoute.BackupsRoute.Local())
      }
      is BackupsSettingsEvent.BackupTierInternalOverrideChanged -> {
        repository.setBackupTierInternalOverride(event.tier, viewModelScope)
      }
      BackupsSettingsEvent.LearnMoreClicked -> {
        _actions.send(BackupsSettingsAction.OpenSupportArticle(LEARN_MORE_URL))
      }
      BackupsSettingsEvent.CheckoutCompleted -> {
        parentEventEmitter.navigateTo(AppSettingsRoute.BackupsRoute.Remote())
      }
    }
  }
}
