/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose.split

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

/**
 * Owns which [PaneAnchor] a split-pane window sits at, and the rules for moving between them. Intended
 * to be held by the view-model that owns the window's navigation state.
 */
internal class PaneAnchorController(private val savedStateHandle: SavedStateHandle) {

  companion object {
    private const val KEY = "pane_anchor"
  }

  val anchor: StateFlow<PaneAnchor> = savedStateHandle.getStateFlow(KEY, PaneAnchor.SPLIT)

  /**
   * Whether one pane currently occupies the whole window.
   */
  val isFullScreenPane: Flow<Boolean> = anchor.map { it != PaneAnchor.SPLIT }

  /**
   * The user dragged the pane divider to [anchor].
   */
  fun onAnchorSelected(anchor: PaneAnchor) {
    set(anchor)
  }

  /**
   * Opening detail content while the list fills the window has to reveal the detail. A window already
   * showing both panes is left as the user arranged it.
   */
  fun revealDetailPane() {
    if (anchor.value == PaneAnchor.LIST_ONLY) {
      set(PaneAnchor.DETAIL_ONLY)
    }
  }

  /**
   * Losing detail content, or explicitly asking for the list, has to reveal the list if the detail
   * filled the window.
   */
  fun revealListPane() {
    if (anchor.value == PaneAnchor.DETAIL_ONLY) {
      set(PaneAnchor.LIST_ONLY)
    }
  }

  private fun set(value: PaneAnchor) {
    savedStateHandle[KEY] = value
  }
}
