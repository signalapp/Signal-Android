/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.schedule

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfileSchedule
import java.time.LocalTime

/**
 * Drives [EditNotificationProfileScheduleScreen].
 */
class EditNotificationProfileScheduleViewModel(
  private val profileId: Long,
  createMode: Boolean
) : EventDrivenViewModel<EditNotificationProfileScheduleEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(EditNotificationProfileScheduleViewModel::class)
  }

  private val _state = MutableStateFlow(EditNotificationProfileScheduleState(createMode = createMode))
  private val _actions = Channel<EditNotificationProfileScheduleAction>(Channel.BUFFERED)

  val state: StateFlow<EditNotificationProfileScheduleState> = _state.asStateFlow()
  val actions: Flow<EditNotificationProfileScheduleAction> = _actions.receiveAsFlow()

  init {
    viewModelScope.launch {
      val schedule = EditNotificationProfileScheduleRepository.getSchedule(profileId)
      _state.update { it.copy(schedule = schedule) }
    }
  }

  override suspend fun processEvent(event: EditNotificationProfileScheduleEvent) {
    when (event) {
      EditNotificationProfileScheduleEvent.NavigateBackClicked -> _actions.send(EditNotificationProfileScheduleAction.NavigateBack)
      is EditNotificationProfileScheduleEvent.ScheduleToggled -> updateSchedule { it.copy(enabled = event.enabled) }
      is EditNotificationProfileScheduleEvent.DayToggled -> {
        updateSchedule { schedule ->
          val days = schedule.daysEnabled
          schedule.copy(daysEnabled = if (event.day in days) days - event.day else days + event.day)
        }
      }
      EditNotificationProfileScheduleEvent.StartTimeClicked -> showTimePicker(isStart = true)
      EditNotificationProfileScheduleEvent.EndTimeClicked -> showTimePicker(isStart = false)
      is EditNotificationProfileScheduleEvent.TimeSelected -> applySelectedTime(event.time)
      EditNotificationProfileScheduleEvent.DialogDismissed -> _state.update { it.copy(dialog = EditNotificationProfileScheduleState.Dialog.None) }
      EditNotificationProfileScheduleEvent.NextClicked -> save()
      EditNotificationProfileScheduleEvent.SnackbarDismissed -> _state.update { it.copy(snackbar = EditNotificationProfileScheduleState.Snackbar.None) }
    }
  }

  private fun updateSchedule(transform: (NotificationProfileSchedule) -> NotificationProfileSchedule) {
    _state.update { state -> state.schedule?.let { state.copy(schedule = transform(it)) } ?: state }
  }

  private fun showTimePicker(isStart: Boolean) {
    if (_state.value.schedule?.enabled == true) {
      _state.update { it.copy(dialog = EditNotificationProfileScheduleState.Dialog.TimePicker(isStart)) }
    }
  }

  private fun applySelectedTime(time: LocalTime) {
    val dialog = _state.value.dialog as? EditNotificationProfileScheduleState.Dialog.TimePicker ?: return
    val value = time.hour * 100 + time.minute

    updateSchedule { if (dialog.isStart) it.copy(start = value) else it.copy(end = value) }
    _state.update { it.copy(dialog = EditNotificationProfileScheduleState.Dialog.None) }
  }

  private suspend fun save() {
    val state = _state.value
    val schedule = state.schedule ?: return

    when {
      schedule.enabled && schedule.daysEnabled.isEmpty() -> {
        _state.update { it.copy(snackbar = EditNotificationProfileScheduleState.Snackbar.NoDaysSelected) }
      }
      state.createMode && !schedule.enabled -> {
        _actions.send(EditNotificationProfileScheduleAction.NavigateToProfileCreated(profileId))
      }
      else -> {
        _state.update { it.copy(isSaving = true) }
        EditNotificationProfileScheduleRepository.saveSchedule(profileId, schedule)
        _state.update { it.copy(isSaving = false) }

        _actions.send(if (state.createMode) EditNotificationProfileScheduleAction.NavigateToProfileCreated(profileId) else EditNotificationProfileScheduleAction.NavigateBack)
      }
    }
  }
}
