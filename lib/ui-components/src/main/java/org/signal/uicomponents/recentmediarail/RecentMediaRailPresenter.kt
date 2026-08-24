/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.uicomponents.recentmediarail

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import org.signal.core.ui.compose.EventDrivenPresenter
import org.signal.core.util.logging.Log
import org.signal.uicomponents.recentmediarail.RecentMedia.Availability

/**
 * All of the logic behind a [RecentMediaRail]: loading it, keeping it up to date, and deciding what a tap on it should
 * do.
 *
 * Meant to be held by the view model of whichever screen shows the rail, which feeds it events, mirrors [state] into
 * its own state, and carries out [actions].
 */
class RecentMediaRailPresenter(
  coroutineScope: CoroutineScope,
  private val loader: Loader
) : EventDrivenPresenter<RecentMediaRailEvents>(TAG, coroutineScope) {

  companion object {
    private val TAG = Log.tag(RecentMediaRailPresenter::class)
  }

  private val _state = MutableStateFlow(RecentMediaRailState())
  private val _actions = Channel<RecentMediaRailAction>(Channel.BUFFERED)

  val state: StateFlow<RecentMediaRailState> = _state.asStateFlow()
  val actions: Flow<RecentMediaRailAction> = _actions.receiveAsFlow()

  private var sourceId: Long? = null

  override suspend fun processEvent(event: RecentMediaRailEvents) {
    when (event) {
      is RecentMediaRailEvents.SourceChanged -> {
        if (sourceId != event.sourceId) {
          sourceId = event.sourceId
          load()
        }
      }
      RecentMediaRailEvents.RefreshRequested -> {
        load()
      }
      is RecentMediaRailEvents.ItemClicked -> {
        val media = _state.value.media.getOrNull(event.index)

        val action = when (media?.availability) {
          Availability.AVAILABLE -> RecentMediaRailAction.OpenMedia(event.index, event.bounds, event.leftToRight)
          Availability.RESTORABLE -> RecentMediaRailAction.DownloadMedia(event.index)
          Availability.UNAVAILABLE -> RecentMediaRailAction.ShowMediaUnavailable
          null -> null
        }

        if (action != null) {
          _actions.send(action)
        }
      }
      RecentMediaRailEvents.SeeAllClicked -> {
        _actions.send(RecentMediaRailAction.OpenAllMedia)
      }
    }
  }

  private suspend fun load() {
    val id = sourceId ?: return
    _state.value = RecentMediaRailState(media = loader.load(id), loaded = true)
  }

  /** Where the rail's contents come from. */
  fun interface Loader {
    suspend fun load(sourceId: Long): List<RecentMedia>
  }
}
