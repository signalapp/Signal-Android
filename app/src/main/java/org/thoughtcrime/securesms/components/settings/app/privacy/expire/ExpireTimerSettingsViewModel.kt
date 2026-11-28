/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.privacy.expire

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
import org.signal.appsettings.expiretimer.ExpireTimerSettingsAction
import org.signal.appsettings.expiretimer.ExpireTimerSettingsEvent
import org.signal.appsettings.expiretimer.ExpireTimerSettingsState
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.navigateBack
import org.signal.core.models.recipients.RecipientId
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log
import kotlin.time.Duration.Companion.seconds

/**
 * Drives [org.signal.appsettings.expiretimer.ExpireTimerSettingsScreen].
 */
class ExpireTimerSettingsViewModel(
  private val config: Config,
  private val parentEventEmitter: (AppSettingsNavigationEvent) -> Unit
) : EventDrivenViewModel<ExpireTimerSettingsEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(ExpireTimerSettingsViewModel::class)
  }

  private val _state = MutableStateFlow(
    ExpireTimerSettingsState(
      mode = config.mode,
      initialTimer = if (config.recipientId == null) (config.initialValue ?: ExpireTimerSettingsRepository.getUniversalExpireTimer()).seconds else 0.seconds
    )
  )
  private val _actions = Channel<ExpireTimerSettingsAction>(Channel.BUFFERED)

  val state: StateFlow<ExpireTimerSettingsState> = _state.asStateFlow()
  val actions: Flow<ExpireTimerSettingsAction> = _actions.receiveAsFlow()

  init {
    if (config.recipientId != null) {
      ExpireTimerSettingsRepository.observeExpiration(config.recipientId)
        .onEach { seconds -> _state.update { it.copy(initialTimer = seconds.seconds) } }
        .launchIn(viewModelScope)
    }
  }

  override suspend fun processEvent(event: ExpireTimerSettingsEvent) {
    when (event) {
      ExpireTimerSettingsEvent.NavigateBackClicked -> parentEventEmitter.navigateBack()
      is ExpireTimerSettingsEvent.TimerSelected -> _state.update { it.copy(selectedTimer = event.timer) }
      ExpireTimerSettingsEvent.CustomTimeClicked -> _state.update { it.copy(dialog = ExpireTimerSettingsState.Dialog.CustomTimer) }
      is ExpireTimerSettingsEvent.CustomTimerSelected -> _state.update { it.copy(selectedTimer = event.timer, dialog = ExpireTimerSettingsState.Dialog.None) }
      ExpireTimerSettingsEvent.DialogDismissed -> _state.update { it.copy(dialog = ExpireTimerSettingsState.Dialog.None) }
      ExpireTimerSettingsEvent.SaveClicked -> save()
      ExpireTimerSettingsEvent.SnackbarDismissed -> _state.update { it.copy(snackbar = ExpireTimerSettingsState.Snackbar.None) }
    }
  }

  private suspend fun save() {
    val state = _state.value
    val timer = state.currentTimer

    if (config.mode == ExpireTimerSettingsState.Mode.GROUP_CREATE) {
      _actions.send(ExpireTimerSettingsAction.ReturnResult(timer))
      return
    }

    if (timer == state.initialTimer) {
      parentEventEmitter.navigateBack()
      return
    }

    _state.update { it.copy(isSaving = true) }
    val seconds = timer.inWholeSeconds.toInt()

    if (config.recipientId != null) {
      when (val result = ExpireTimerSettingsRepository.setExpiration(config.recipientId, seconds)) {
        ExpireTimerSettingsRepository.SetExpirationResult.Success -> parentEventEmitter.navigateBack()
        is ExpireTimerSettingsRepository.SetExpirationResult.Failure -> {
          _state.update { it.copy(isSaving = false, snackbar = ExpireTimerSettingsState.Snackbar.SaveFailed(result.message)) }
        }
      }
    } else {
      ExpireTimerSettingsRepository.setUniversalExpireTimer(seconds)
      parentEventEmitter.navigateBack()
    }
  }

  /**
   * What the timer is for: the default for new chats when [recipientId] is null and [forResultMode] is false, one chat
   * when [recipientId] is set, or a group being created when [forResultMode] is true, starting from [initialValue].
   */
  data class Config(
    val recipientId: RecipientId? = null,
    val forResultMode: Boolean = false,
    val initialValue: Int? = null
  ) {
    val mode: ExpireTimerSettingsState.Mode
      get() = when {
        recipientId != null -> ExpireTimerSettingsState.Mode.RECIPIENT
        forResultMode -> ExpireTimerSettingsState.Mode.GROUP_CREATE
        else -> ExpireTimerSettingsState.Mode.UNIVERSAL
      }
  }
}
