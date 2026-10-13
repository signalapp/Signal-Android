/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account.twofactor

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import org.signal.appsettings.account.TwoFactorMethod
import org.signal.appsettings.twofactornameentry.TwoFactorNameEntryAction
import org.signal.appsettings.twofactornameentry.TwoFactorNameEntryEvent
import org.signal.appsettings.twofactornameentry.TwoFactorNameEntryState
import org.signal.core.models.MasterKey
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.BreakIteratorCompat
import org.signal.core.util.StringUtil
import org.signal.core.util.logging.Log
import org.signal.libsignal.net.RequestResult
import org.signal.network.service.TwoFactorMethodService
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.net.SignalNetwork
import java.time.Instant

/**
 * Handles both naming a newly added second factor and renaming one that already exists. A method whose metadata we
 * could not read has no name to start from, so renaming it starts from an empty field.
 */
class TwoFactorNameEntryViewModel(
  private val methodId: Long?,
  private val kind: TwoFactorMethod.Kind,
  private val createdAt: Long,
  private val renamedMethod: TwoFactorMethod? = null,
  private val service: TwoFactorMethodService = SignalNetwork.twoFactorMethodService,
  private val masterKeyProvider: () -> MasterKey = { SignalStore.svr.masterKey },
  private val clock: () -> Long = System::currentTimeMillis
) : EventDrivenViewModel<TwoFactorNameEntryEvent>(TAG, shouldLogEvents = true) {

  companion object {
    private val TAG = Log.tag(TwoFactorNameEntryViewModel::class)

    /** A tighter limit than the service's, so a name stays something a row can show rather than something it has to cut off. */
    const val MAX_NAME_LENGTH_GRAPHEMES = 30
  }

  private val breakIterator = BreakIteratorCompat.getInstance()

  private val _state = MutableStateFlow(
    TwoFactorNameEntryState(
      name = renamedMethod?.name?.trimNameToLengthLimits() ?: "",
      kind = kind,
      renaming = renamedMethod != null
    )
  )
  private val _actions = Channel<TwoFactorNameEntryAction>(Channel.BUFFERED)

  val state: StateFlow<TwoFactorNameEntryState> = _state.asStateFlow()
  val actions: Flow<TwoFactorNameEntryAction> = _actions.receiveAsFlow()

  override suspend fun processEvent(event: TwoFactorNameEntryEvent) {
    when (event) {
      TwoFactorNameEntryEvent.NavigateBackClicked -> {
        _actions.send(TwoFactorNameEntryAction.NavigateBack)
      }
      is TwoFactorNameEntryEvent.NameChanged -> {
        _state.update { it.copy(name = event.name.trimNameToLengthLimits()) }
      }
      TwoFactorNameEntryEvent.NextClicked -> {
        applyNextClicked(
          inputState = _state.value,
          stateEmitter = { _state.value = it },
          actionEmitter = { _actions.send(it) }
        )
      }
    }
  }

  private suspend fun applyNextClicked(
    inputState: TwoFactorNameEntryState,
    stateEmitter: (TwoFactorNameEntryState) -> Unit,
    actionEmitter: suspend (TwoFactorNameEntryAction) -> Unit
  ) {
    var state = inputState.copy()

    if (!state.canSubmit) {
      return
    }

    val name = inputState.name.trim()
    state = state.copy(submitting = true)
    stateEmitter(state)

    val id = renamedMethod?.id ?: methodId
    if (id == null) {
      Log.w(TAG, "Asked to name a method without being told which one. Going back to account settings rather than stranding the user here.")
      actionEmitter(TwoFactorNameEntryAction.NavigateToAccountSettings)
      return
    }

    val stamp = if (renamedMethod != null) renamedMethod.createdAt ?: clock() else createdAt

    when (service.setName(id = id, name = name, createdAt = Instant.ofEpochMilli(stamp), masterKey = masterKeyProvider())) {
      is RequestResult.Success -> {
        actionEmitter(if (renamedMethod != null) TwoFactorNameEntryAction.ShowMethodRenamed(kind) else TwoFactorNameEntryAction.ShowMethodSetUp(kind))
        actionEmitter(TwoFactorNameEntryAction.NavigateToAccountSettings)
      }
      is RequestResult.NonSuccess -> {
        Log.w(TAG, "Asked to name a method the service doesn't have. Going back to account settings rather than stranding the user here.")
        actionEmitter(TwoFactorNameEntryAction.NavigateToAccountSettings)
      }
      is RequestResult.RetryableNetworkError, is RequestResult.ApplicationError -> {
        state = state.copy(submitting = false)
        stateEmitter(state)
        actionEmitter(TwoFactorNameEntryAction.ShowNameNotSaved)
      }
    }
  }

  /** Keeps the name within the length limits the service puts on metadata. */
  private fun String.trimNameToLengthLimits(): String {
    val input = this
    val graphemeTruncated = breakIterator
      .apply { setText(input) }
      .take(MAX_NAME_LENGTH_GRAPHEMES)
      .toString()

    return StringUtil.trimToFit(graphemeTruncated, TwoFactorMethodService.MAX_NAME_LENGTH_BYTES)
  }
}
