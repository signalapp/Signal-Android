/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.select

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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
import org.signal.mediasend.MediaSendEvent
import org.signal.mediasend.MediaSendFlowState
import org.signal.mediasend.MediaSendRepository
import org.signal.mediasend.R
import org.signal.mediasend.SnackbarEvent

/**
 * Drives the select screen, for one appearance of it: either the list of folders, or the contents of a single folder.
 *
 * The gallery and how much of it we are allowed to read are only of interest to this screen, so they live here. The
 * selection is not -- the rest of the flow sends it -- so it arrives as [MediaSelectScreenEvent.ParentStateChanged] and
 * is rendered from a copy in this screen's own state. Anything this screen wants done to it goes back out as a
 * [MediaSendEvent].
 *
 * @param mediaFolder The folder whose contents to show, or null for the list of folders.
 */
internal class MediaSelectViewModel(
  private val parentState: StateFlow<MediaSendFlowState>,
  private val parentEventEmitter: (MediaSendEvent) -> Unit,
  mediaFolder: MediaFolder?,
  private val repository: MediaSendRepository = MediaSendDependencies.mediaSendRepository
) : EventDrivenViewModel<MediaSelectScreenEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(MediaSelectViewModel::class)
  }

  private val _state: MutableStateFlow<MediaSelectScreenState> = MutableStateFlow(
    if (mediaFolder != null) {
      MediaSelectScreenState.Files(
        selectedMediaFolder = mediaFolder,
        selectedMediaFolderItems = emptyList(),
        selectedMedia = emptyList()
      )
    } else {
      MediaSelectScreenState.Folders(
        mediaFolders = emptyList(),
        selectedMedia = emptyList()
      )
    }
  )

  val state: StateFlow<MediaSelectScreenState> = _state.asStateFlow()

  /** Hosted here, since this is the only screen that asks for the gallery's permissions. */
  val readMediaPermission = MediaPermissionController()

  init {
    parentState
      .distinctUntilChangedBy { it.selectedMedia to it.isSelectionRejected }
      .onEach { onEvent(MediaSelectScreenEvent.ParentStateChanged(it)) }
      .launchIn(viewModelScope)

    refresh()
  }

  override suspend fun processEvent(event: MediaSelectScreenEvent) {
    when (event) {
      is MediaSelectScreenEvent.ParentStateChanged -> _state.update { it.withParentState(event.parentState.selectedMedia, event.parentState.isSelectionRejected) }
      MediaSelectScreenEvent.SelectionRejectionShown -> parentEventEmitter(MediaSendEvent.SelectionRejectionShown)
      is MediaSelectScreenEvent.FolderClick -> event.mediaFolder?.let { parentEventEmitter(MediaSendEvent.NavigateToFiles(it)) }
      is MediaSelectScreenEvent.MediaClick -> applyMediaClickEvent(event.media)
      is MediaSelectScreenEvent.MediaSelected -> parentEventEmitter(MediaSendEvent.AddMedia(event.media))
      is MediaSelectScreenEvent.MediaUnselected -> parentEventEmitter(MediaSendEvent.RemoveMedia(event.media))
      is MediaSelectScreenEvent.SetFocusedMedia -> parentEventEmitter(MediaSendEvent.SetFocusedMedia(event.media))
      is MediaSelectScreenEvent.ReorderSelectedMedia -> parentEventEmitter(MediaSendEvent.ReorderSelectedMedia(event.fromIndex, event.toIndex))
      MediaSelectScreenEvent.NavigateToEdit -> parentEventEmitter(MediaSendEvent.NavigateToEdit)
      MediaSelectScreenEvent.NavigateToCamera -> parentEventEmitter(MediaSendEvent.NavigateToCamera)
      MediaSelectScreenEvent.Refresh -> refresh()
      MediaSelectScreenEvent.RequestMediaPermissions -> requestReadMediaPermissions(reportDenial = true)
      MediaSelectScreenEvent.SelectMorePhotos -> requestReadMediaPermissions(reportDenial = false)
    }
  }

  private fun applyMediaClickEvent(media: Media) {
    if (_state.value.selectedMedia.any { it.uri == media.uri }) {
      parentEventEmitter(MediaSendEvent.RemoveMedia(setOf(media)))
    } else {
      parentEventEmitter(MediaSendEvent.AddMedia(setOf(media)))
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

      val reloaded: MediaSelectScreenState = when (val snapshot = _state.value) {
        is MediaSelectScreenState.Folders -> snapshot.copy(mediaFolders = repository.getFolders())
        is MediaSelectScreenState.Files -> snapshot.copy(selectedMediaFolderItems = repository.getMedia(snapshot.selectedMediaFolder.bucketId))
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
          MediaSendEvent.ShowSnackbar(
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
    private val parentEventEmitter: (MediaSendEvent) -> Unit,
    private val mediaFolder: MediaFolder?
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return MediaSelectViewModel(parentState, parentEventEmitter, mediaFolder) as T
    }
  }
}
