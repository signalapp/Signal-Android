/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.mediaviewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log
import org.signal.mediaviewer.MediaViewerAction
import org.signal.mediaviewer.MediaViewerEvent
import org.signal.mediaviewer.MediaViewerScreen
import org.signal.mediaviewer.MediaViewerState
import org.thoughtcrime.securesms.mediapreview.MediaIntentFactory

/**
 * Drives [MediaViewerScreen]: pages the thread's gallery around the opened
 * attachment, re-anchors when the underlying attachments change (advancing to the next available
 * item when the current one is deleted), and turns user events into host actions.
 */
class MediaViewerViewModel(
  private val args: MediaIntentFactory.MediaPreviewArgs
) : EventDrivenViewModel<MediaViewerEvent>(TAG) {

  private val _state = MutableStateFlow(
    MediaViewerState(
      showThread = args.showThread,
      allMediaInRail = args.allMediaInRail
    )
  )
  val state: StateFlow<MediaViewerState> = _state.asStateFlow()

  private val _actions = Channel<MediaViewerAction>(Channel.BUFFERED)
  val actions: Flow<MediaViewerAction> = _actions.receiveAsFlow()

  /** Whether the shared element exit transition should run: the user is still on the media they entered on. */
  fun shouldFinishAfterTransition(): Boolean {
    return false
  }

  override suspend fun processEvent(event: MediaViewerEvent) {
    when (event) {
      is MediaViewerEvent.PageScrolled -> Unit
      is MediaViewerEvent.PageSettled -> Unit
      is MediaViewerEvent.PagerScrollStateChanged -> Unit
      is MediaViewerEvent.RailVisibleRange -> Unit
      is MediaViewerEvent.MediaReady -> Unit
      is MediaViewerEvent.MediaTapped -> Unit
      is MediaViewerEvent.SharedAnimationChanged -> Unit
      is MediaViewerEvent.HdrDetected -> Unit
      is MediaViewerEvent.VideoPlaybackStarted -> Unit
      is MediaViewerEvent.VideoPlaybackStopped -> Unit
      is MediaViewerEvent.VideoPlaybackError -> Unit
      is MediaViewerEvent.ChromeAutoHideElapsed -> Unit
      is MediaViewerEvent.BackClicked -> Unit
      is MediaViewerEvent.ToolbarTitleClicked -> Unit
      is MediaViewerEvent.ShareClicked -> Unit
      is MediaViewerEvent.ForwardClicked -> Unit
      is MediaViewerEvent.SaveClicked -> Unit
      is MediaViewerEvent.EditClicked -> Unit
      is MediaViewerEvent.DeleteClicked -> Unit
      is MediaViewerEvent.DeleteForEveryoneValidityResolved -> Unit
      is MediaViewerEvent.DeleteConfirmed -> Unit
      is MediaViewerEvent.ViewStorageSettingsClicked -> Unit
      is MediaViewerEvent.DialogDismissed -> Unit
      is MediaViewerEvent.DataInvalidated -> Unit
      is MediaViewerEvent.PagedDataUpdated -> Unit
      is MediaViewerEvent.InitialItemLoaded -> Unit
      is MediaViewerEvent.InitialWindowLoaded -> Unit
      is MediaViewerEvent.GalleryAnchorMissing -> Unit
      is MediaViewerEvent.MessageBodiesResolved -> Unit
      is MediaViewerEvent.ScrollRequestStarted -> Unit
      is MediaViewerEvent.RailItemClicked -> Unit
    }
  }

  class Factory(private val args: MediaIntentFactory.MediaPreviewArgs) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      @Suppress("UNCHECKED_CAST")
      return MediaViewerViewModel(args) as T
    }
  }

  companion object {
    private val TAG = Log.tag(MediaViewerViewModel::class)
  }
}
