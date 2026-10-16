/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.screens.sticker

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import org.signal.core.util.logging.Log
import org.signal.mediakeyboard.MediaKeyboardAction
import org.signal.mediakeyboard.data.StickerKeyboardRepository
import org.signal.mediakeyboard.screens.MediaKeyboardPageViewModel

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class StickerPageViewModel(
  private val repository: StickerKeyboardRepository
) : MediaKeyboardPageViewModel<StickerPageScreenEvents>(TAG, shouldLogEvents = false) {

  companion object {
    private val TAG = Log.tag(StickerPageViewModel::class)
    private const val SEARCH_DEBOUNCE_MS = 150L
  }

  /**
   * The search query, for a host that offers search; the keyboard leaves it empty. The field edits this directly rather
   * than through events, which arrive a dispatch later and would let fast typing race the field's own state.
   */
  val searchFieldState = TextFieldState()

  private val _state = MutableStateFlow(StickerPageState(allowAnimation = repository.allowStickerAnimation, favoritesEnabled = repository.favoritesEnabled))
  val state: StateFlow<StickerPageState> = _state.asStateFlow()

  init {
    onEvent(StickerPageScreenEvents.Initialize)
    snapshotFlow { searchFieldState.text.toString().trim() }
      .distinctUntilChanged()
      .debounce { query -> if (query.isEmpty()) 0L else SEARCH_DEBOUNCE_MS }
      .flatMapLatest { query ->
        if (query.isEmpty()) {
          repository.observeStickerPacks().map { StickerPageScreenEvents.PacksUpdated(it) }
        } else {
          flow { emit(StickerPageScreenEvents.PacksUpdated(repository.searchStickerPacks(query), query)) }
        }
      }
      .onEach { onEvent(it) }
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
        val selectable = event.packs.filterNot { it.isEmptyFavorites }
        val selected = state.selectedPackId?.takeIf { id -> selectable.any { it.id == id } }
          ?: selectable.firstOrNull()?.id
        stateEmitter(state.copy(packs = event.packs, selectedPackId = selected, searchQuery = event.searchQuery))
      }

      is StickerPageScreenEvents.PackSelected -> {
        if (state.packs.any { it.id == event.packId && it.isEmptyFavorites }) {
          emitAction(MediaKeyboardAction.EmptyFavoritesClicked)
        } else {
          stateEmitter(state.copy(selectedPackId = event.packId, scrollTargetPackId = event.packId))
        }
      }

      is StickerPageScreenEvents.VisiblePackChanged -> {
        if (state.scrollTargetPackId == null && state.selectedPackId != event.packId) {
          stateEmitter(state.copy(selectedPackId = event.packId))
        }
      }

      is StickerPageScreenEvents.ScrollTargetConsumed -> {
        stateEmitter(state.copy(scrollTargetPackId = null))
      }

      is StickerPageScreenEvents.StickerLongPressed -> {
        emitAction(MediaKeyboardAction.StickerSelected(event.sticker))
      }

      is StickerPageScreenEvents.StickerSendClicked -> {
        repository.onStickerUsed(event.sticker)
        emitAction(MediaKeyboardAction.StickerSendClicked(event.sticker))
      }

      is StickerPageScreenEvents.SearchClicked -> {
        emitAction(MediaKeyboardAction.StickerSearchClicked)
      }

      is StickerPageScreenEvents.ViewStickerPackClicked -> {
        emitAction(MediaKeyboardAction.ViewStickerPackClicked(event.packId, event.packKey))
      }

      is StickerPageScreenEvents.SendStickerPackClicked -> {
        emitAction(MediaKeyboardAction.SendStickerPackClicked(event.packId, event.packKey))
      }

      is StickerPageScreenEvents.RemoveStickerPackClicked -> {
        stateEmitter(state.copy(confirmRemovePack = ConfirmRemovePack(event.packId, event.packKey)))
      }

      is StickerPageScreenEvents.RemoveStickerPackConfirmed -> {
        val pack = state.confirmRemovePack
        stateEmitter(state.copy(confirmRemovePack = null))

        if (pack != null) {
          emitAction(MediaKeyboardAction.RemoveStickerPackConfirmed(pack.packId, pack.packKey))
        }
      }

      is StickerPageScreenEvents.RemoveStickerPackCanceled -> {
        stateEmitter(state.copy(confirmRemovePack = null))
      }

      is StickerPageScreenEvents.ClearRecentStickersClicked -> {
        repository.clearRecentStickers()
      }

      is StickerPageScreenEvents.AddStickerToFavoritesClicked -> {
        emitAction(MediaKeyboardAction.AddStickerToFavoritesClicked(event.sticker))
      }

      is StickerPageScreenEvents.RemoveStickerFromFavoritesClicked -> {
        stateEmitter(state.copy(confirmRemoveFavorite = event.sticker))
      }

      is StickerPageScreenEvents.RemoveStickerFromFavoritesConfirmed -> {
        val sticker = state.confirmRemoveFavorite
        stateEmitter(state.copy(confirmRemoveFavorite = null))

        if (sticker != null) {
          emitAction(MediaKeyboardAction.RemoveStickerFromFavoritesConfirmed(sticker))
        }
      }

      is StickerPageScreenEvents.RemoveStickerFromFavoritesCanceled -> {
        stateEmitter(state.copy(confirmRemoveFavorite = null))
      }

      is StickerPageScreenEvents.MoveFavoriteToTopClicked -> {
        emitAction(MediaKeyboardAction.MoveFavoriteToTopClicked(event.sticker))
      }
    }
  }

  class Factory(
    private val repository: StickerKeyboardRepository
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      @Suppress("UNCHECKED_CAST")
      return StickerPageViewModel(repository) as T
    }
  }
}
