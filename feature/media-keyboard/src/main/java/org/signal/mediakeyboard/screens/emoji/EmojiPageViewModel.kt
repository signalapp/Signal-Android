/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.screens.emoji

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.signal.core.util.logging.Log
import org.signal.mediakeyboard.MediaKeyboardAction
import org.signal.mediakeyboard.data.EmojiCategoryPage
import org.signal.mediakeyboard.data.EmojiKeyboardCategory
import org.signal.mediakeyboard.data.EmojiKeyboardRepository
import org.signal.mediakeyboard.data.KeyboardEmoji
import org.signal.mediakeyboard.screens.MediaKeyboardPageViewModel

/**
 * @param searchQuery Queries from a host's own search field: null when not searching, blank to show recents. Defaults
 *   to [searchFieldState], where blank means not searching.
 */
class EmojiPageViewModel(
  private val repository: EmojiKeyboardRepository,
  searchQuery: Flow<String?>? = null
) : MediaKeyboardPageViewModel<EmojiPageScreenEvents>(TAG, shouldLogEvents = false) {

  companion object {
    private val TAG = Log.tag(EmojiPageViewModel::class)
  }

  /** The in-page search field's query, edited directly since events arrive too late to keep up with typing. */
  val searchFieldState = TextFieldState()

  private val _state = MutableStateFlow(EmojiPageState())
  val state: StateFlow<EmojiPageState> = _state.asStateFlow()

  private var latestSearchQuery: String? = null

  init {
    onEvent(EmojiPageScreenEvents.Initialize)

    val queries = searchQuery ?: snapshotFlow { searchFieldState.text.toString().trim().ifEmpty { null } }
    queries
      .distinctUntilChanged()
      .onEach { query ->
        latestSearchQuery = query
        onEvent(EmojiPageScreenEvents.SearchQueryChanged(query))
      }
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

      is EmojiPageScreenEvents.RefreshRecents -> {
        val pages = withRecents(state.pages, repository.getRecentEmoji())
        if (pages != state.pages) {
          stateEmitter(state.copy(pages = pages))
        }
      }

      is EmojiPageScreenEvents.SearchQueryChanged -> {
        applySearchQuery(state, event.query)?.let(stateEmitter)
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
        emitAction(MediaKeyboardAction.EmojiSelected(display))
      }

      is EmojiPageScreenEvents.EmojiLongPressed -> {
        if (event.emoji.hasVariations) {
          stateEmitter(state.copy(variationSelector = EmojiPageState.VariationSelectorTarget(event.cellKey, event.emoji)))
        }
      }

      is EmojiPageScreenEvents.VariationSelected -> {
        repository.setPreferredVariation(event.emoji.canonical, event.variation)
        repository.onEmojiUsed(event.variation)
        emitAction(MediaKeyboardAction.EmojiSelected(event.variation))
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

  /** The state for [query], or null if a newer query has arrived. */
  private suspend fun applySearchQuery(state: EmojiPageState, query: String?): EmojiPageState? {
    if (query != latestSearchQuery) {
      return null
    }

    return when {
      query == null && state.searchResults == null -> state
      query == null -> state.copy(searchResults = null, searchQuery = "", pages = buildPages())
      query.isBlank() -> state.copy(searchResults = repository.getRecentEmoji(), searchQuery = query)
      else -> state.copy(searchResults = repository.search(query), searchQuery = query)
    }
  }

  private suspend fun buildPages(): List<EmojiCategoryPage> {
    return withRecents(repository.getEmojiPages(), repository.getRecentEmoji())
  }

  /** Replaces the recents page in [pages] with [recents], placed after [EmojiKeyboardCategory.THIS_MESSAGE]. */
  private fun withRecents(pages: List<EmojiCategoryPage>, recents: List<KeyboardEmoji>): List<EmojiCategoryPage> {
    val (leading, rest) = pages
      .filter { it.category != EmojiKeyboardCategory.RECENTS }
      .partition { it.category == EmojiKeyboardCategory.THIS_MESSAGE }

    return buildList {
      addAll(leading)
      if (recents.isNotEmpty()) {
        add(EmojiCategoryPage(EmojiKeyboardCategory.RECENTS, recents))
      }
      addAll(rest)
    }
  }

  class Factory(
    private val repository: EmojiKeyboardRepository,
    private val searchQuery: Flow<String?>? = null
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      @Suppress("UNCHECKED_CAST")
      return EmojiPageViewModel(repository, searchQuery) as T
    }
  }
}
