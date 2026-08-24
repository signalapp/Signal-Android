/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.edit

import android.Manifest
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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
) : EventDrivenViewModel<MediaEditScreenEvents>(TAG) {

  companion object {
    private val TAG = Log.tag(MediaEditViewModel::class)
  }

  private val _state: MutableStateFlow<MediaEditState> = MutableStateFlow(MediaEditState().withParentState(parentState.value))
  val state: StateFlow<MediaEditState> = _state.asStateFlow()

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
  }

  override suspend fun processEvent(event: MediaEditScreenEvents) {
    when (event) {
      is MediaEditScreenEvents.ParentStateChanged -> _state.update { it.withParentState(event.parentState) }
      is MediaEditScreenEvents.FocusedMediaChanged -> parentEventEmitter(MediaSendFlowEvent.SetFocusedMedia(event.media))
      is MediaEditScreenEvents.ReorderSelectedMedia -> parentEventEmitter(MediaSendFlowEvent.ReorderSelectedMedia(event.fromIndex, event.toIndex))
      is MediaEditScreenEvents.RemoveMedia -> parentEventEmitter(MediaSendFlowEvent.RemoveMedia(setOf(event.media)))
      is MediaEditScreenEvents.SetMediaQuality -> parentEventEmitter(MediaSendFlowEvent.SetMediaQuality(event.quality))
      is MediaEditScreenEvents.BrushWidthChanged -> parentEventEmitter(MediaSendFlowEvent.SetBrushWidth(event.tool, event.fraction))
      is MediaEditScreenEvents.ToggleBlurFaces -> parentEventEmitter(MediaSendFlowEvent.SetBlurFacesEnabled(event.enabled))
      is MediaEditScreenEvents.VideoTrimChanged -> parentEventEmitter(MediaSendFlowEvent.VideoTrimChanged(event.videoTrimData, event.editingComplete))
      MediaEditScreenEvents.ToggleViewOnce -> parentEventEmitter(MediaSendFlowEvent.ToggleViewOnce)
      MediaEditScreenEvents.ToggleVideoMuted -> parentEventEmitter(MediaSendFlowEvent.ToggleVideoMuted)
      is MediaEditScreenEvents.AddMessageClick -> parentEventEmitter(MediaSendFlowEvent.AddMessageRequested(event.startWithEmojiKeyboard))
      is MediaEditScreenEvents.ScheduleSendClick -> parentEventEmitter(MediaSendFlowEvent.ScheduleSendRequested(event.option))
      MediaEditScreenEvents.StickerClick -> parentEventEmitter(MediaSendFlowEvent.StickerRequested)
      MediaEditScreenEvents.NextClick -> parentEventEmitter(MediaSendFlowEvent.NextRequested)
      MediaEditScreenEvents.NavigateToGallery -> parentEventEmitter(MediaSendFlowEvent.NavigateToFolders)
      MediaEditScreenEvents.NavigateBack -> parentEventEmitter(MediaSendFlowEvent.NavigateBackFromEdit)
      MediaEditScreenEvents.SaveMedia -> saveFocusedMediaToStorage()
      is MediaEditScreenEvents.VideoSeek -> error("VideoSeek is routed to the video player bus by MediaEditScreen and must not reach the view-model.")
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

  class Factory(
    private val parentState: StateFlow<MediaSendFlowState>,
    private val parentEventEmitter: (MediaSendFlowEvent) -> Unit
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return MediaEditViewModel(parentState, parentEventEmitter) as T
    }
  }
}
