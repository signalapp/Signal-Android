/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.select

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.core.models.media.Media
import org.signal.core.models.media.MediaFolder
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.ui.compose.Snackbars
import org.signal.core.util.logging.Log
import org.signal.mediasend.MediaSendDependencies
import org.signal.mediasend.MediaSendFlowEvent
import org.signal.mediasend.MediaSendFlowState
import org.signal.mediasend.MediaSendRepository
import org.signal.mediasend.R
import org.signal.mediasend.SnackbarEvent

/**
 * Drives the select screen, for one appearance of it: either the list of folders, or the contents of a single folder.
 *
 * The gallery and how much of it we are allowed to read are only of interest to this screen, so they live here. The
 * selection is not -- the rest of the flow sends it -- so it arrives as [MediaSelectScreenEvents.ParentStateChanged] and
 * is rendered from a copy in this screen's own state. Anything this screen wants done to it goes back out as a
 * [MediaSendFlowEvent].
 *
 * @param mediaFolder The folder whose contents to show, or null for the list of folders.
 */
internal class MediaSelectViewModel(
  private val parentState: StateFlow<MediaSendFlowState>,
  private val parentEventEmitter: (MediaSendFlowEvent) -> Unit,
  mediaFolder: MediaFolder?,
  /** Passed straight through: the rail follows it, and nothing about it is this screen's to decide. */
  val selectionAdditions: Flow<Media>,
  private val repository: MediaSendRepository = MediaSendDependencies.mediaSendRepository
) : EventDrivenViewModel<MediaSelectScreenEvents>(TAG) {

  companion object {
    private val TAG = Log.tag(MediaSelectViewModel::class)
  }

  private val _state: MutableStateFlow<MediaSelectState> = MutableStateFlow(
    if (mediaFolder != null) {
      MediaSelectState.Files(
        selectedMediaFolder = mediaFolder,
        selectedMediaFolderItems = emptyList(),
        selectedMedia = emptyList(),
        recipientId = parentState.value.recipientId
      )
    } else {
      MediaSelectState.Folders(
        mediaFolders = emptyList(),
        selectedMedia = emptyList(),
        recipientId = parentState.value.recipientId
      )
    }
  )

  val state: StateFlow<MediaSelectState> = _state.asStateFlow()

  /** Hosted here, since this is the only screen that asks for the gallery's permissions. */
  val readMediaPermission = MediaPermissionController()

  init {
    parentState
      .distinctUntilChangedBy { it.selectedMedia to it.isSelectionRejected }
      .onEach { onEvent(MediaSelectScreenEvents.ParentStateChanged(it)) }
      .launchIn(viewModelScope)

    refresh()
  }

  override suspend fun processEvent(event: MediaSelectScreenEvents) {
    when (event) {
      is MediaSelectScreenEvents.ParentStateChanged -> _state.update { it.withParentState(event.parentState.selectedMedia, event.parentState.isSelectionRejected) }
      MediaSelectScreenEvents.SelectionRejectionShown -> parentEventEmitter(MediaSendFlowEvent.SelectionRejectionShown)
      is MediaSelectScreenEvents.FolderClick -> event.mediaFolder?.let { parentEventEmitter(MediaSendFlowEvent.NavigateToFiles(it)) }
      is MediaSelectScreenEvents.MediaClick -> applyMediaClickEvent(event.media)
      is MediaSelectScreenEvents.MediaSelected -> parentEventEmitter(MediaSendFlowEvent.AddMedia(event.media))
      is MediaSelectScreenEvents.MediaUnselected -> parentEventEmitter(MediaSendFlowEvent.RemoveMedia(event.media))
      is MediaSelectScreenEvents.SetFocusedMedia -> parentEventEmitter(MediaSendFlowEvent.SetFocusedMedia(event.media))
      is MediaSelectScreenEvents.ReorderSelectedMedia -> parentEventEmitter(MediaSendFlowEvent.ReorderSelectedMedia(event.fromIndex, event.toIndex))
      MediaSelectScreenEvents.NavigateToEdit -> parentEventEmitter(MediaSendFlowEvent.NavigateToEdit)
      MediaSelectScreenEvents.NavigateToCamera -> parentEventEmitter(MediaSendFlowEvent.NavigateToCamera)
      MediaSelectScreenEvents.NavigateBack -> parentEventEmitter(MediaSendFlowEvent.NavigateBackFromSelect)
      MediaSelectScreenEvents.Refresh -> refresh()
      MediaSelectScreenEvents.RequestMediaPermissions -> requestReadMediaPermissions(reportDenial = true)
      MediaSelectScreenEvents.SelectMorePhotos -> requestReadMediaPermissions(reportDenial = false)
    }
  }

  private fun applyMediaClickEvent(media: Media) {
    if (_state.value.selectedMedia.any { it.uri == media.uri }) {
      parentEventEmitter(MediaSendFlowEvent.RemoveMedia(setOf(media)))
    } else {
      parentEventEmitter(MediaSendFlowEvent.AddMedia(setOf(media)))
    }
  }

  /**
   * Re-reads what this screen shows from the media store, along with the level of access we currently have. Widening
   * selected-photos access adds items to a folder without changing which folder it is, so the contents have to be
   * re-read even when it looks like nothing about the folder has changed.
   */
  private fun refresh() {
    viewModelScope.launch {
      val mediaPermissions = MediaPermissions.current()

      val reloaded: MediaSelectState = when (val snapshot = _state.value) {
        is MediaSelectState.Folders -> snapshot.copy(mediaFolders = repository.getFolders())
        is MediaSelectState.Files -> snapshot.copy(selectedMediaFolderItems = repository.getMedia(snapshot.selectedMediaFolder.bucketId))
      }

      // Only what the parent reports can have changed while we were reading, and that is not ours to overwrite.
      _state.update { current ->
        reloaded
          .withMediaPermissions(mediaPermissions)
          .withParentState(current.selectedMedia, current.isSelectionRejected)
      }
    }
  }

  /**
   * Prompts for the gallery's read permissions and refreshes on any result, granted or not: selected-photos access
   * comes back as a denial of the broad permissions but still changes what we can see.
   */
  private fun requestReadMediaPermissions(reportDenial: Boolean) {
    viewModelScope.launch {
      val denied = readMediaPermission.request(permanentDenialSheet = reportDenial)

      if (reportDenial && denied) {
        parentEventEmitter(
          MediaSendFlowEvent.ShowSnackbar(
            SnackbarEvent(
              message = R.string.MediaSelectScreen__signal_needs_access_to_show_your_photos_and_videos,
              duration = Snackbars.Duration.LONG
            )
          )
        )
      }

      refresh()
    }
  }

  class Factory(
    private val parentState: StateFlow<MediaSendFlowState>,
    private val parentEventEmitter: (MediaSendFlowEvent) -> Unit,
    private val mediaFolder: MediaFolder?,
    private val selectionAdditions: Flow<Media>
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return MediaSelectViewModel(parentState, parentEventEmitter, mediaFolder, selectionAdditions) as T
    }
  }
}
