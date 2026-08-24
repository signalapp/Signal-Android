/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.clockskew

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log
import java.text.DateFormat
import java.util.Date
import java.util.TimeZone

class ClockSkewViewModel(
  private val clock: () -> Long = { System.currentTimeMillis() },
  detected: StateFlow<Boolean> = ClockSkewDetector.detected
) : EventDrivenViewModel<ClockSkewScreenEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(ClockSkewViewModel::class)
  }

  private val _state = MutableStateFlow(ClockSkewState(deviceDateTime = formatDeviceDateTime()))
  val state: StateFlow<ClockSkewState> = _state.asStateFlow()

  private val _actions = Channel<ClockSkewScreenAction>(Channel.UNLIMITED)
  val actions: Flow<ClockSkewScreenAction> = _actions.receiveAsFlow()

  init {
    detected
      .onEach { onEvent(ClockSkewScreenEvent.SkewStateChanged(it)) }
      .launchIn(viewModelScope)
  }

  override suspend fun processEvent(event: ClockSkewScreenEvent) {
    applyEvent(_state.value, event, { _actions.trySend(it) }) { _state.value = it }
  }

  @VisibleForTesting
  fun applyEvent(
    state: ClockSkewState,
    event: ClockSkewScreenEvent,
    actionEmitter: (ClockSkewScreenAction) -> Unit,
    stateEmitter: (ClockSkewState) -> Unit
  ) {
    when (event) {
      ClockSkewScreenEvent.ScreenResumed -> stateEmitter(state.copy(deviceDateTime = formatDeviceDateTime()))
      ClockSkewScreenEvent.AdjustDateSelected -> actionEmitter(ClockSkewScreenAction.OpenDateSettings)
      is ClockSkewScreenEvent.SkewStateChanged -> if (!event.detected) actionEmitter(ClockSkewScreenAction.Finish)
    }
  }

  private fun formatDeviceDateTime(): String {
    val dateTime = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(clock()))
    val timeZone = TimeZone.getDefault().getDisplayName(false, TimeZone.LONG)
    return "$dateTime\n($timeZone)"
  }
}
