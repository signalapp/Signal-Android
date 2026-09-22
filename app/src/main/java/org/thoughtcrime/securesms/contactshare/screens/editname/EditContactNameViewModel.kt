/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contactshare.screens.editname

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
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log

class EditContactNameViewModel(
  private val savedState: SavedStateHandle
) : EventDrivenViewModel<EditContactNameEvent>(TAG, shouldLogEvents = true) {

  companion object {
    private val TAG = Log.tag(EditContactNameViewModel::class)

    private const val KEY_PARTS = "parts"
    private const val KEY_ORIGINAL = "original"
  }

  private val _state = MutableStateFlow(
    EditContactNameState(
      parts = savedState[KEY_PARTS] ?: ContactNameParts(),
      original = savedState[KEY_ORIGINAL] ?: ContactNameParts()
    )
  )
  val state: StateFlow<EditContactNameState> = _state.asStateFlow()

  private val _results = Channel<EditContactNameResult>(Channel.BUFFERED)
  val results: Flow<EditContactNameResult> = _results.receiveAsFlow()

  init {
    viewModelScope.launch {
      _state.collect {
        savedState[KEY_PARTS] = it.parts
        savedState[KEY_ORIGINAL] = it.original
      }
    }
  }

  override suspend fun processEvent(event: EditContactNameEvent) {
    when (event) {
      is EditContactNameEvent.Initialize -> {
        _state.value = EditContactNameState(parts = event.parts, original = event.parts)
      }

      is EditContactNameEvent.PrefixChanged -> updateParts { it.copy(prefix = event.value) }
      is EditContactNameEvent.GivenNameChanged -> updateParts { it.copy(givenName = event.value) }
      is EditContactNameEvent.MiddleNameChanged -> updateParts { it.copy(middleName = event.value) }
      is EditContactNameEvent.FamilyNameChanged -> updateParts { it.copy(familyName = event.value) }
      is EditContactNameEvent.SuffixChanged -> updateParts { it.copy(suffix = event.value) }

      EditContactNameEvent.SaveClicked -> {
        val current = _state.value
        if (current.canSave) {
          _results.send(EditContactNameResult.Saved(current.parts))
        }
      }

      EditContactNameEvent.BackClicked -> _results.send(EditContactNameResult.Cancelled)
    }
  }

  private fun updateParts(transform: (ContactNameParts) -> ContactNameParts) {
    _state.update { it.copy(parts = transform(it.parts)) }
  }
}
