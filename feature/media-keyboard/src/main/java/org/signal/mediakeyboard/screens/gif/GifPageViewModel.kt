/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.screens.gif

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.Result
import org.signal.core.util.logging.Log
import org.signal.mediakeyboard.MediaKeyboardAction
import org.signal.mediakeyboard.data.GifKeyboardRepository

class GifPageViewModel(
  private val repository: GifKeyboardRepository,
  private val onAction: (MediaKeyboardAction) -> Unit
) : EventDrivenViewModel<GifPageScreenEvents>(TAG) {

  companion object {
    private val TAG = Log.tag(GifPageViewModel::class)
    private const val PAGE_SIZE = 20
  }

  private val _state = MutableStateFlow(GifPageState())
  val state: StateFlow<GifPageState> = _state.asStateFlow()

  init {
    onEvent(GifPageScreenEvents.Initialize)
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

      is GifPageScreenEvents.LoadMoreRequested -> {
        loadNextPage(state, stateEmitter)
      }

      is GifPageScreenEvents.RetryClicked -> {
        loadFirstPage(state, stateEmitter)
      }

      is GifPageScreenEvents.GifClicked -> {
        onAction(MediaKeyboardAction.GifSelected(event.gif))
      }

      is GifPageScreenEvents.SearchClicked -> {
        onAction(MediaKeyboardAction.GifSearchClicked)
      }
    }
  }

  private suspend fun loadFirstPage(state: GifPageState, stateEmitter: (GifPageState) -> Unit) {
    var current = state.copy(gifs = emptyList(), isLoading = true, isLoadingMore = false, hasMore = true, loadFailed = false)
    stateEmitter(current)

    when (val result = repository.getGifs(current.selectedQuickSearch.query, offset = 0, limit = PAGE_SIZE)) {
      is Result.Success -> {
        current = current.copy(gifs = result.success.gifs, hasMore = result.success.hasMore, isLoading = false)
      }
      is Result.Failure -> {
        Log.w(TAG, "Failed to load gifs: ${result.failure}")
        current = current.copy(isLoading = false, loadFailed = true, hasMore = false)
      }
    }

    stateEmitter(current)
  }

  private suspend fun loadNextPage(state: GifPageState, stateEmitter: (GifPageState) -> Unit) {
    if (state.isLoading || state.isLoadingMore || !state.hasMore || state.loadFailed) {
      return
    }

    stateEmitter(state.copy(isLoadingMore = true))

    when (val result = repository.getGifs(state.selectedQuickSearch.query, offset = state.gifs.size, limit = PAGE_SIZE)) {
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
    private val repository: GifKeyboardRepository,
    private val onAction: (MediaKeyboardAction) -> Unit
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      @Suppress("UNCHECKED_CAST")
      return GifPageViewModel(repository, onAction) as T
    }
  }
}
