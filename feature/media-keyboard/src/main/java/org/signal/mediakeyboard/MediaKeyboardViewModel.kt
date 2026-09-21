/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log
import org.signal.mediakeyboard.data.MediaKeyboardRepository

/**
 * @param isEnteringText Whether the search surface is up, from the scaffold that owns it.
 * @param initialTab The tab to open on, or null to open on the first one offered.
 */
class MediaKeyboardViewModel(
  private val repository: MediaKeyboardRepository,
  isEnteringText: StateFlow<Boolean>,
  initialTab: MediaKeyboardTab? = null
) : EventDrivenViewModel<MediaKeyboardScreenEvents>(TAG, shouldLogEvents = false) {

  companion object {
    private val TAG = Log.tag(MediaKeyboardViewModel::class)
  }

  private val _state = MutableStateFlow(
    initialTab?.let { MediaKeyboardState(preferredTab = it) } ?: MediaKeyboardState()
  )
  val state: StateFlow<MediaKeyboardState> = _state.asStateFlow()

  init {
    onEvent(MediaKeyboardScreenEvents.Initialize)

    isEnteringText
      .onEach { entering -> _state.update { it.copy(searchActive = entering) } }
      .launchIn(viewModelScope)
  }

  override suspend fun processEvent(event: MediaKeyboardScreenEvents) {
    applyEvent(_state.value, event) { next ->
      _state.update { current -> next.copy(searchActive = current.searchActive, restrictedTo = current.restrictedTo) }
    }
  }

  @VisibleForTesting
  suspend fun applyEvent(
    state: MediaKeyboardState,
    event: MediaKeyboardScreenEvents,
    stateEmitter: (MediaKeyboardState) -> Unit
  ) {
    when (event) {
      is MediaKeyboardScreenEvents.Initialize -> {
        val offered = repository.getAvailableTabs()
        stateEmitter(state.copy(offeredTabs = MediaKeyboardTab.entries.filter { it in offered }, initialized = true))
      }
      is MediaKeyboardScreenEvents.TabSelected -> {
        stateEmitter(state.copy(preferredTab = event.tab, searchQuery = ""))
      }
      is MediaKeyboardScreenEvents.SearchQueryChanged -> {
        stateEmitter(state.copy(searchQuery = event.query))
      }
    }
  }

  /**
   * Narrows the keyboard to [tabs], or null to offer everything again. Not an event: it is the
   * host's standing configuration rather than something the user did, and applying it needs nothing
   * the repository has to fetch.
   */
  fun restrictTabs(tabs: Set<MediaKeyboardTab>?) {
    _state.update { it.copy(restrictedTo = tabs) }
  }

  class Factory(
    private val repository: MediaKeyboardRepository,
    private val isEnteringText: StateFlow<Boolean>,
    private val initialTab: MediaKeyboardTab?
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      @Suppress("UNCHECKED_CAST")
      return MediaKeyboardViewModel(repository, isEnteringText, initialTab) as T
    }
  }
}
