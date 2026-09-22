/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.screens.sticker

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log
import org.signal.mediakeyboard.MediaKeyboardAction
import org.signal.mediakeyboard.data.StickerKeyboardRepository

class StickerPageViewModel(
  private val repository: StickerKeyboardRepository,
  private val onAction: (MediaKeyboardAction) -> Unit
) : EventDrivenViewModel<StickerPageScreenEvents>(TAG, shouldLogEvents = false) {

  companion object {
    private val TAG = Log.tag(StickerPageViewModel::class)
  }

  private val _state = MutableStateFlow(StickerPageState(allowAnimation = repository.allowStickerAnimation))
  val state: StateFlow<StickerPageState> = _state.asStateFlow()

  init {
    onEvent(StickerPageScreenEvents.Initialize)
    repository.observeStickerPacks()
      .onEach { onEvent(StickerPageScreenEvents.PacksUpdated(it)) }
      .launchIn(viewModelScope)
  }

  override suspend fun processEvent(event: StickerPageScreenEvents) {
    applyEvent(_state.value, event) { _state.value = it }
  }

  @VisibleForTesting
  suspend fun applyEvent(
    state: StickerPageState,
    event: StickerPageScreenEvents,
    stateEmitter: (StickerPageState) -> Unit
  ) {
    when (event) {
      is StickerPageScreenEvents.Initialize -> Unit

      is StickerPageScreenEvents.PacksUpdated -> {
        val selected = state.selectedPackId?.takeIf { id -> event.packs.any { it.id == id } }
          ?: event.packs.firstOrNull()?.id
        stateEmitter(state.copy(packs = event.packs, selectedPackId = selected))
      }

      is StickerPageScreenEvents.PackSelected -> {
        stateEmitter(state.copy(selectedPackId = event.packId, scrollTargetPackId = event.packId))
      }

      is StickerPageScreenEvents.VisiblePackChanged -> {
        if (state.scrollTargetPackId == null && state.selectedPackId != event.packId) {
          stateEmitter(state.copy(selectedPackId = event.packId))
        }
      }

      is StickerPageScreenEvents.ScrollTargetConsumed -> {
        stateEmitter(state.copy(scrollTargetPackId = null))
      }

      is StickerPageScreenEvents.StickerClicked -> {
        repository.onStickerUsed(event.sticker)
        onAction(MediaKeyboardAction.StickerSelected(event.sticker))
      }

      is StickerPageScreenEvents.SearchClicked -> {
        onAction(MediaKeyboardAction.StickerSearchClicked)
      }

      is StickerPageScreenEvents.ViewStickerPackClicked -> {
        onAction(MediaKeyboardAction.ViewStickerPackClicked(event.packId, event.packKey))
      }

      is StickerPageScreenEvents.SendStickerPackClicked -> {
        onAction(MediaKeyboardAction.SendStickerPackClicked(event.packId, event.packKey))
      }

      is StickerPageScreenEvents.RemoveStickerPackClicked -> {
        onAction(MediaKeyboardAction.RemoveStickerPackClicked(event.packId, event.packKey))
      }

      is StickerPageScreenEvents.ClearRecentStickersClicked -> {
        repository.clearRecentStickers()
      }
    }
  }

  class Factory(
    private val repository: StickerKeyboardRepository,
    private val onAction: (MediaKeyboardAction) -> Unit
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      @Suppress("UNCHECKED_CAST")
      return StickerPageViewModel(repository, onAction) as T
    }
  }
}
