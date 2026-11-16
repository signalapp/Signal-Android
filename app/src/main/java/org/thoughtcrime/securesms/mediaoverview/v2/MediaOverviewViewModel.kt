/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.mediaoverview.v2

import androidx.paging.PagingData
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log
import org.signal.mediaoverview.MediaOverviewAction
import org.signal.mediaoverview.MediaOverviewEvent
import org.signal.mediaoverview.MediaOverviewRow
import org.signal.mediaoverview.MediaOverviewState
import org.signal.mediaoverview.MediaOverviewState.Tab
import org.thoughtcrime.securesms.database.MediaTable

/**
 * Drives [org.signal.mediaoverview.MediaOverviewScreen] for one conversation, or for every
 * conversation when [threadId] is [MediaTable.ALL_THREADS].
 */
class MediaOverviewViewModel(
  threadId: Long
) : EventDrivenViewModel<MediaOverviewEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(MediaOverviewViewModel::class)
  }

  private val isAllThreads = threadId == MediaTable.ALL_THREADS.toLong()

  private val _state = MutableStateFlow(
    MediaOverviewState(
      isAllThreads = isAllThreads,
      selectedTab = if (isAllThreads) Tab.ALL else Tab.MEDIA,
      sorting = if (isAllThreads) MediaOverviewState.Sorting.LARGEST else MediaOverviewState.Sorting.NEWEST,
      detailLayout = isAllThreads
    )
  )
  private val _actions = Channel<MediaOverviewAction>(Channel.BUFFERED)

  val state: StateFlow<MediaOverviewState> = _state.asStateFlow()
  val actions: Flow<MediaOverviewAction> = _actions.receiveAsFlow()

  /** Each tab's paged media. */
  val media: Map<Tab, Flow<PagingData<MediaOverviewRow>>> = emptyMap()

  override suspend fun processEvent(event: MediaOverviewEvent) {
    when (event) {
      MediaOverviewEvent.BackClicked -> {}
      is MediaOverviewEvent.TabSelected -> {}
      MediaOverviewEvent.SortClicked -> {}
      is MediaOverviewEvent.SortSelected -> {}
      MediaOverviewEvent.LayoutToggled -> {}
      is MediaOverviewEvent.ItemClicked -> {}
      is MediaOverviewEvent.ItemLongClicked -> {}
      is MediaOverviewEvent.AudioButtonClicked -> {}
      MediaOverviewEvent.ContextMenuDismissed -> {}
      MediaOverviewEvent.ContextMenuForwardClicked -> {}
      MediaOverviewEvent.ContextMenuShareClicked -> {}
      MediaOverviewEvent.ContextMenuSaveClicked -> {}
      MediaOverviewEvent.ContextMenuDeleteClicked -> {}
      MediaOverviewEvent.ContextMenuSelectClicked -> {}
      MediaOverviewEvent.ContextMenuJumpToMessageClicked -> {}
      MediaOverviewEvent.SaveSelectedClicked -> {}
      MediaOverviewEvent.SelectAllClicked -> {}
      MediaOverviewEvent.DeleteSelectedClicked -> {}
      MediaOverviewEvent.SelectionCleared -> {}
      MediaOverviewEvent.DeleteConfirmed -> {}
      MediaOverviewEvent.SaveAvailableConfirmed -> {}
      MediaOverviewEvent.ViewStorageSettingsClicked -> {}
      MediaOverviewEvent.DialogDismissed -> {}
      MediaOverviewEvent.SaveFinished -> {}
      is MediaOverviewEvent.AudioPlaybackChanged -> {}
    }
  }
}
