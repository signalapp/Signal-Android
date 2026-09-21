/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.screens.emoji

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
import org.signal.mediakeyboard.MediaKeyboardState
import org.signal.mediakeyboard.MediaKeyboardTab
import org.signal.mediakeyboard.data.EmojiCategoryPage
import org.signal.mediakeyboard.data.EmojiKeyboardCategory
import org.signal.mediakeyboard.data.EmojiKeyboardRepository

class EmojiPageViewModel(
  private val repository: EmojiKeyboardRepository,
  private val parentState: StateFlow<MediaKeyboardState>,
  private val onAction: (MediaKeyboardAction) -> Unit
) : EventDrivenViewModel<EmojiPageScreenEvents>(TAG, shouldLogEvents = false) {

  companion object {
    private val TAG = Log.tag(EmojiPageViewModel::class)
  }

  private val _state = MutableStateFlow(EmojiPageState())
  val state: StateFlow<EmojiPageState> = _state.asStateFlow()

  init {
    onEvent(EmojiPageScreenEvents.Initialize)
    parentState
      .onEach { onEvent(EmojiPageScreenEvents.ParentStateChanged(it)) }
      .launchIn(viewModelScope)
  }

  override suspend fun processEvent(event: EmojiPageScreenEvents) {
    applyEvent(_state.value, event) { _state.value = it }
  }

  @VisibleForTesting
  suspend fun applyEvent(
    state: EmojiPageState,
    event: EmojiPageScreenEvents,
    stateEmitter: (EmojiPageState) -> Unit
  ) {
    when (event) {
      is EmojiPageScreenEvents.Initialize -> {
        val pages = buildPages()
        stateEmitter(
          state.copy(
            pages = pages,
            preferredVariations = repository.getPreferredVariations(),
            selectedCategory = state.selectedCategory ?: pages.firstOrNull()?.category
          )
        )
      }

      is EmojiPageScreenEvents.ParentStateChanged -> {
        stateEmitter(applyParentState(state, event.parentState))
      }

      is EmojiPageScreenEvents.CategorySelected -> {
        stateEmitter(state.copy(selectedCategory = event.category, scrollTarget = event.category))
      }

      is EmojiPageScreenEvents.VisibleCategoryChanged -> {
        if (state.scrollTarget == null && state.selectedCategory != event.category) {
          stateEmitter(state.copy(selectedCategory = event.category))
        }
      }

      is EmojiPageScreenEvents.ScrollTargetConsumed -> {
        stateEmitter(state.copy(scrollTarget = null))
      }

      is EmojiPageScreenEvents.EmojiClicked -> {
        val display = state.displayEmoji(event.emoji)
        repository.onEmojiUsed(display)
        onAction(MediaKeyboardAction.EmojiSelected(display))
      }

      is EmojiPageScreenEvents.EmojiLongPressed -> {
        if (event.emoji.hasVariations) {
          stateEmitter(state.copy(variationSelector = EmojiPageState.VariationSelectorTarget(event.cellKey, event.emoji)))
        }
      }

      is EmojiPageScreenEvents.VariationSelected -> {
        repository.setPreferredVariation(event.emoji.canonical, event.variation)
        repository.onEmojiUsed(event.variation)
        onAction(MediaKeyboardAction.EmojiSelected(event.variation))
        stateEmitter(
          state.copy(
            preferredVariations = repository.getPreferredVariations(),
            variationSelector = null
          )
        )
      }

      is EmojiPageScreenEvents.VariationSelectorDismissed -> {
        stateEmitter(state.copy(variationSelector = null))
      }
    }
  }

  private suspend fun applyParentState(state: EmojiPageState, parent: MediaKeyboardState): EmojiPageState {
    val searching = parent.searchActive && parent.selectedTab == MediaKeyboardTab.EMOJI

    return if (!searching) {
      if (state.searchResults != null) {
        state.copy(searchResults = null, searchQuery = "", pages = buildPages())
      } else {
        state
      }
    } else if (parent.searchQuery != state.searchQuery || state.searchResults == null) {
      // Bail if a newer query has already arrived.
      if (parent.searchQuery != parentState.value.searchQuery) {
        state
      } else {
        val results = if (parent.searchQuery.isBlank()) {
          repository.getRecentEmoji()
        } else {
          repository.search(parent.searchQuery)
        }
        state.copy(searchResults = results, searchQuery = parent.searchQuery)
      }
    } else {
      state
    }
  }

  private suspend fun buildPages(): List<EmojiCategoryPage> {
    val recents = repository.getRecentEmoji()
    return buildList {
      if (recents.isNotEmpty()) {
        add(EmojiCategoryPage(EmojiKeyboardCategory.RECENTS, recents))
      }
      addAll(repository.getEmojiPages())
    }
  }

  class Factory(
    private val repository: EmojiKeyboardRepository,
    private val parentState: StateFlow<MediaKeyboardState>,
    private val onAction: (MediaKeyboardAction) -> Unit
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      @Suppress("UNCHECKED_CAST")
      return EmojiPageViewModel(repository, parentState, onAction) as T
    }
  }
}
