/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.edit

import android.Manifest
import androidx.annotation.StringRes
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.DialogController
import org.signal.core.ui.compose.DialogResult
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.ui.compose.PermissionController
import org.signal.core.ui.util.StorageUtil
import org.signal.core.util.logging.Log
import org.signal.mediasend.EditorState
import org.signal.mediasend.MediaSendDependencies
import org.signal.mediasend.MediaSendFlowEvent
import org.signal.mediasend.MediaSendFlowState
import org.signal.mediasend.MediaSendRepository
import org.signal.mediasend.R
import org.signal.mediasend.SaveToStorageResult
import org.signal.mediasend.SnackbarEvent
import org.signal.mediasend.screens.edit.video.VideoEditorViewModel
import org.signal.mediasend.screens.edit.video.VideoPlayerCommand
import org.signal.mediasend.screens.edit.video.trim.VideoTrimBarAction
import org.signal.mediasend.screens.edit.video.trim.VideoTrimBarEvents
import org.signal.mediasend.screens.edit.video.trim.VideoTrimBarPresenter

/**
 * Drives the edit screen.
 *
 * What the user edits is not this screen's to keep -- an edit has to survive the editor being swiped away -- so every
 * change leaves as a [MediaSendFlowEvent] and comes back as [MediaEditScreenEvents.ParentStateChanged]. Writing the
 * focused image out to shared storage is the exception: nothing else in the flow offers it, so it is done here.
 */
internal class MediaEditViewModel(
  parentState: StateFlow<MediaSendFlowState>,
  private val parentEventEmitter: (MediaSendFlowEvent) -> Unit,
  private val repository: MediaSendRepository = MediaSendDependencies.mediaSendRepository
) : EventDrivenViewModel<MediaEditScreenEvents>(TAG, shouldLogEvents = false) {

  companion object {
    private val TAG = Log.tag(MediaEditViewModel::class)
  }

  private val _state: MutableStateFlow<MediaEditState> = MutableStateFlow(MediaEditState().withParentState(parentState.value))
  val state: StateFlow<MediaEditState> = _state.asStateFlow()

  private val trimBarPresenter = VideoTrimBarPresenter(viewModelScope)

  /** Seeks for the video player, which lives behind [VideoEditorViewModel] rather than in this screen. */
  private val _videoPlayerCommands = Channel<VideoPlayerCommand>(Channel.BUFFERED)
  val videoPlayerCommands: Flow<VideoPlayerCommand> = _videoPlayerCommands.receiveAsFlow()

  /** Hosted here, since this is the only screen that saves media or asks for what saving it needs. */
  val saveToStorageDialog = DialogController<Unit>()
  val writeStoragePermission = PermissionController(
    permission = Manifest.permission.WRITE_EXTERNAL_STORAGE,
    permanentDenialMessage = R.string.MediaSendViewModel__signal_needs_the_storage_permission
  )

  init {
    parentState
      .onEach { onEvent(MediaEditScreenEvents.ParentStateChanged(it)) }
      .launchIn(viewModelScope)

    trimBarPresenter
      .state
      .onEach { onEvent(MediaEditScreenEvents.TrimBarStateChanged(it)) }
      .launchIn(viewModelScope)

    trimBarPresenter
      .actions
      .onEach { onEvent(MediaEditScreenEvents.TrimBarAction(it)) }
      .launchIn(viewModelScope)
  }

  override suspend fun processEvent(event: MediaEditScreenEvents) {
    when (event) {
      is MediaEditScreenEvents.ParentStateChanged -> {
        _state.update { it.withParentState(event.parentState) }
        updateTrimBarSource()
      }
      is MediaEditScreenEvents.FocusedMediaChanged -> parentEventEmitter(MediaSendFlowEvent.SetFocusedMedia(event.media))
      is MediaEditScreenEvents.ReorderSelectedMedia -> parentEventEmitter(MediaSendFlowEvent.ReorderSelectedMedia(event.fromIndex, event.toIndex))
      is MediaEditScreenEvents.RemoveMedia -> parentEventEmitter(MediaSendFlowEvent.RemoveMedia(setOf(event.media)))
      is MediaEditScreenEvents.SetMediaQuality -> parentEventEmitter(MediaSendFlowEvent.SetMediaQuality(event.quality))
      is MediaEditScreenEvents.BrushWidthChanged -> parentEventEmitter(MediaSendFlowEvent.SetBrushWidth(event.tool, event.fraction))
      is MediaEditScreenEvents.ToggleBlurFaces -> parentEventEmitter(MediaSendFlowEvent.SetBlurFacesEnabled(event.enabled))
      MediaEditScreenEvents.ToggleViewOnce -> parentEventEmitter(MediaSendFlowEvent.ToggleViewOnce)
      MediaEditScreenEvents.ToggleVideoMuted -> parentEventEmitter(MediaSendFlowEvent.ToggleVideoMuted)
      is MediaEditScreenEvents.AddMessageClick -> parentEventEmitter(MediaSendFlowEvent.AddMessageRequested(event.startWithEmojiKeyboard))
      is MediaEditScreenEvents.ScheduleSendClick -> parentEventEmitter(MediaSendFlowEvent.ScheduleSendRequested(event.option))
      MediaEditScreenEvents.StickerClick -> parentEventEmitter(MediaSendFlowEvent.StickerRequested)
      MediaEditScreenEvents.NextClick -> parentEventEmitter(MediaSendFlowEvent.NextRequested)
      MediaEditScreenEvents.NavigateToGallery -> parentEventEmitter(MediaSendFlowEvent.NavigateToFolders)
      MediaEditScreenEvents.NavigateBack -> parentEventEmitter(MediaSendFlowEvent.NavigateBackFromEdit)
      MediaEditScreenEvents.SaveMedia -> saveFocusedMediaToStorage()
      is MediaEditScreenEvents.TrimBarEvent -> trimBarPresenter.onEvent(event.event)
      is MediaEditScreenEvents.TrimBarStateChanged -> _state.update { it.copy(videoTrimBar = event.state) }
      is MediaEditScreenEvents.TrimBarAction -> onTrimBarAction(event.action)
    }
  }

  /**
   * Points the trim bar at the focused video and passes along the flow's trim for it. Sent on every flow update, not
   * just when the trim changes: the bar ignores trims that arrive mid-drag, so the update that ends a drag has to carry
   * the trim again even if it is unchanged.
   */
  private fun updateTrimBarSource() {
    val state = _state.value
    val uri = state.focusedMedia?.uri ?: return
    val editorState = state.focusedEditorState as? EditorState.VideoTrim ?: return

    trimBarPresenter.onEvent(VideoTrimBarEvents.SourceChanged(uri))
    trimBarPresenter.onEvent(VideoTrimBarEvents.TrimDataChanged(uri, editorState.videoTrimData, editorState.maxDurationUs.takeIf { it > 0 }))
  }

  private suspend fun onTrimBarAction(action: VideoTrimBarAction) {
    when (action) {
      is VideoTrimBarAction.TrimChanged -> parentEventEmitter(MediaSendFlowEvent.VideoTrimChanged(action.uri, action.videoTrimData, action.editingComplete))
      is VideoTrimBarAction.Seek -> {
        val command = if (action.editingComplete) {
          VideoEditorViewModel.Command.EndPositionDrag(action.positionUs)
        } else {
          VideoEditorViewModel.Command.PositionDrag(action.positionUs)
        }
        _videoPlayerCommands.send(VideoPlayerCommand(action.uri, command))
      }
    }
  }

  /**
   * Writes the focused image, edits included, out to the device's shared storage. Launched rather than awaited so that
   * the confirmation and the permission prompt do not hold up the events behind them.
   */
  private fun saveFocusedMediaToStorage() {
    val editorState = _state.value.focusedEditorState as? EditorState.Image ?: return

    viewModelScope.launch {
      if (!repository.hasDismissedSaveToStorageWarning && saveToStorageDialog.show(Unit) != DialogResult.POSITIVE) {
        return@launch
      }

      if (!StorageUtil.canWriteToMediaStore() && !writeStoragePermission.request()) {
        showSnackbar(R.string.MediaSendViewModel__unable_to_save_without_storage_permission)
        return@launch
      }

      if (_state.value.isSavingMedia) {
        return@launch
      }

      _state.update { it.copy(isSavingMedia = true) }
      val result = try {
        repository.saveImageToStorage(editorState.model)
      } finally {
        _state.update { it.copy(isSavingMedia = false) }
      }

      showSnackbar(
        when (result) {
          SaveToStorageResult.SUCCESS -> R.string.MediaSendViewModel__media_saved
          SaveToStorageResult.FAILURE -> R.string.MediaSendViewModel__error_saving_media
          SaveToStorageResult.NO_WRITE_ACCESS -> R.string.MediaSendViewModel__unable_to_save_without_storage_permission
        }
      )
    }
  }

  fun markSaveToStorageWarningDismissed() {
    repository.markSaveToStorageWarningDismissed()
  }

  private fun showSnackbar(@StringRes message: Int) {
    parentEventEmitter(MediaSendFlowEvent.ShowSnackbar(SnackbarEvent(message = message)))
  }
}
