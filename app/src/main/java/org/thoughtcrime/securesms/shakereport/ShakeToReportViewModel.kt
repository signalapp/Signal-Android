/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.shakereport

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.shakereport.ShakeToReportRepository.SubmitResult
import org.thoughtcrime.securesms.shakereport.ShakeToReportState.Step

/**
 * Drives [ShakeToReportDialog].
 */
class ShakeToReportViewModel(
  private val repository: ShakeToReportRepository = ShakeToReportRepository()
) : EventDrivenViewModel<ShakeToReportEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(ShakeToReportViewModel::class)
  }

  private val _state = MutableStateFlow(ShakeToReportState())
  private val _actions = Channel<ShakeToReportAction>(Channel.BUFFERED)

  val state: StateFlow<ShakeToReportState> = _state.asStateFlow()
  val actions: Flow<ShakeToReportAction> = _actions.receiveAsFlow()

  override suspend fun processEvent(event: ShakeToReportEvent) {
    when (event) {
      ShakeToReportEvent.SubmitClicked -> {
        if (_state.value.step == Step.Prompt) {
          submit()
        }
      }
      ShakeToReportEvent.RetryClicked -> {
        if (_state.value.step == Step.Failed) {
          submit()
        }
      }
      ShakeToReportEvent.CopyClicked -> {
        val step = _state.value.step as? Step.Submitted ?: return
        _actions.send(ShakeToReportAction.CopyUrl(step.url))
      }
      ShakeToReportEvent.ShareClicked -> {
        val step = _state.value.step as? Step.Submitted ?: return
        _actions.send(ShakeToReportAction.ShareUrl(step.url))
      }
      ShakeToReportEvent.DismissRequested -> {
        if (_state.value.step != Step.Submitting) {
          _actions.send(ShakeToReportAction.Dismiss)
        }
      }
    }
  }

  private suspend fun submit() {
    Log.i(TAG, "Submitting log...")
    _state.update { it.copy(step = Step.Submitting) }

    val step = when (val result = repository.submitLog()) {
      is SubmitResult.Success -> {
        Log.i(TAG, "Log uploaded.")
        Step.Submitted(result.url)
      }
      SubmitResult.Failure -> {
        Log.w(TAG, "Failed to upload log.")
        Step.Failed
      }
    }

    _state.update { it.copy(step = step) }
  }
}
