/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.listdetail.demo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.ui.compose.split.ListDetailEvents
import org.signal.core.ui.compose.split.ListDetailNavigator
import org.signal.core.ui.compose.split.PaneAnchor
import org.signal.core.util.logging.Log

/**
 * Owns the navigation. Every event here is answered by a one-liner into [ListDetailNavigator], which is
 * the point: you keep your screens' rules, and it keeps the stacks, the pane anchor, and the saved state.
 */
class DemoViewModel(savedStateHandle: SavedStateHandle) : EventDrivenViewModel<DemoEvents>(TAG, shouldLogEvents = true) {

  companion object {
    private val TAG = Log.tag(DemoViewModel::class)
  }

  val navigator = ListDetailNavigator<DemoListRoute, DemoDetailRoute>(
    savedStateHandle = savedStateHandle,
    scope = viewModelScope,
    stackKeys = mapOf(
      DemoListRoute.INBOX to "inbox_stack",
      DemoListRoute.CONTACTS to "contacts_stack"
    ),
    initialRoot = DemoListRoute.INBOX
  )

  /** The selected tab, which is the stack being displayed. */
  val currentTab: StateFlow<DemoListRoute> = navigator.currentRoot

  /** The list on top of that stack — the archive, whenever it has been pushed. */
  val displayedList: StateFlow<DemoListRoute> = navigator.displayedList

  /** The detail content above the displayed list, or null when the list is showing on its own. */
  val detail: StateFlow<DemoDetailRoute?> = navigator.detail

  /** Where the divider sits in a split-pane window. */
  val paneAnchor: StateFlow<PaneAnchor> = navigator.paneAnchor

  override suspend fun processEvent(event: DemoEvents) {
    when (event) {
      DemoEvents.ArchiveSelected -> navigator.processEvent(ListDetailEvents.GoToList(DemoListRoute.ARCHIVE, root = DemoListRoute.INBOX, push = true))
      is DemoEvents.ListDetailEvent -> navigator.processEvent(event.event)
    }
  }
}
