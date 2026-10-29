/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.screens.gif

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.signal.core.util.Result
import org.signal.core.util.logging.Log
import org.signal.mediakeyboard.MediaKeyboardAction
import org.signal.mediakeyboard.data.GifKeyboardRepository
import org.signal.mediakeyboard.screens.MediaKeyboardPageViewModel

@OptIn(FlowPreview::class)
class GifPageViewModel(
  private val repository: GifKeyboardRepository
) : MediaKeyboardPageViewModel<GifPageScreenEvents>(TAG) {

  companion object {
    private val TAG = Log.tag(GifPageViewModel::class)
    private const val PAGE_SIZE = 20
    private const val SEARCH_DEBOUNCE_MS = 300L
  }

  /**
   * The search query, for a host that offers search; the keyboard leaves it empty. The field edits this directly rather
   * than through events, which arrive a dispatch later and would let fast typing race the field's own state.
   */
  val searchFieldState = TextFieldState()

  private val _state = MutableStateFlow(GifPageState())
  val state: StateFlow<GifPageState> = _state.asStateFlow()

  private var loadJob: Job? = null

  init {
    onEvent(GifPageScreenEvents.Initialize)
    snapshotFlow { searchFieldState.text.toString().trim() }
      .distinctUntilChanged()
      .debounce { query -> if (query.isEmpty()) 0L else SEARCH_DEBOUNCE_MS }
      .onEach { onEvent(GifPageScreenEvents.SearchQueryChanged(it)) }
      .launchIn(viewModelScope)
  }

  override suspend fun processEvent(event: GifPageScreenEvents) {
    applyEvent(_state.value, event) { _state.value = it }
  }

  @VisibleForTesting
  suspend fun applyEvent(
    state: GifPageState,
    event: GifPageScreenEvents,
    stateEmitter: (GifPageState) -> Unit
  ) {
    when (event) {
      is GifPageScreenEvents.Initialize -> {
        loadFirstPage(state, stateEmitter)
      }

      is GifPageScreenEvents.QuickSearchSelected -> {
        if (state.selectedQuickSearch != event.option || state.loadFailed) {
          loadFirstPage(state.copy(selectedQuickSearch = event.option), stateEmitter)
        }
      }

      is GifPageScreenEvents.SearchQueryChanged -> {
        if (state.searchQuery != event.query) {
          loadFirstPage(state.copy(searchQuery = event.query), stateEmitter)
        }
      }

      is GifPageScreenEvents.LoadMoreRequested -> {
        loadNextPage(state, stateEmitter)
      }

      is GifPageScreenEvents.RetryClicked -> {
        loadFirstPage(state, stateEmitter)
      }

      is GifPageScreenEvents.GifClicked -> {
        emitAction(MediaKeyboardAction.GifSelected(event.gif))
      }

      is GifPageScreenEvents.SearchClicked -> {
        emitAction(MediaKeyboardAction.GifSearchClicked)
      }
    }
  }

  /**
   * Fetches run off the event loop, each replacing whatever fetch came before it, so a query the user has typed past is
   * dropped rather than finished and queued ahead of the one they want.
   */
  private fun launchLoad(block: suspend () -> Unit) {
    loadJob?.cancel()
    loadJob = viewModelScope.launch { block() }
  }

  private fun loadFirstPage(state: GifPageState, stateEmitter: (GifPageState) -> Unit) {
    val loading = state.copy(gifs = emptyList(), isLoading = true, isLoadingMore = false, hasMore = true, loadFailed = false)
    stateEmitter(loading)

    launchLoad {
      when (val result = repository.getGifs(loading.query, offset = 0, limit = PAGE_SIZE)) {
        is Result.Success -> {
          stateEmitter(loading.copy(gifs = result.success.gifs, hasMore = result.success.hasMore, isLoading = false))
        }
        is Result.Failure -> {
          Log.w(TAG, "Failed to load gifs: ${result.failure}")
          stateEmitter(loading.copy(isLoading = false, loadFailed = true, hasMore = false))
        }
      }
    }
  }

  private fun loadNextPage(state: GifPageState, stateEmitter: (GifPageState) -> Unit) {
    if (state.isLoading || state.isLoadingMore || !state.hasMore || state.loadFailed) {
      return
    }

    stateEmitter(state.copy(isLoadingMore = true))

    launchLoad { appendNextPage(state, stateEmitter) }
  }

  private suspend fun appendNextPage(state: GifPageState, stateEmitter: (GifPageState) -> Unit) {
    when (val result = repository.getGifs(state.query, offset = state.gifs.size, limit = PAGE_SIZE)) {
      is Result.Success -> {
        stateEmitter(
          state.copy(
            gifs = state.gifs + result.success.gifs,
            hasMore = result.success.hasMore,
            isLoadingMore = false
          )
        )
      }
      is Result.Failure -> {
        Log.w(TAG, "Failed to load more gifs: ${result.failure}")
        stateEmitter(state.copy(isLoadingMore = false, hasMore = false))
      }
    }
  }

  class Factory(
    private val repository: GifKeyboardRepository
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      @Suppress("UNCHECKED_CAST")
      return GifPageViewModel(repository) as T
    }
  }
}
