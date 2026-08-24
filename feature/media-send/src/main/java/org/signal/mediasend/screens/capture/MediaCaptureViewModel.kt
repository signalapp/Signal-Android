/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.capture

import androidx.annotation.StringRes
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
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log
import org.signal.mediasend.MediaSendFlowEvent
import org.signal.mediasend.MediaSendFlowState
import org.signal.mediasend.MediaSendRoute
import org.signal.mediasend.R
import org.signal.mediasend.SnackbarEvent
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Drives the capture screen, which makes media rather than picking media that already exists.
 *
 * Writing a capture out is this screen's, since only it knows what the camera handed back. What becomes of the media
 * afterwards is not: it leaves as [MediaSendFlowEvent.MediaCaptured], and the selection comes back as
 * [MediaCaptureScreenEvents.ParentStateChanged].
 *
 * @param selectedCaptureScreen Which of the two capture screens navigation is currently showing.
 */
internal class MediaCaptureViewModel(
  parentState: StateFlow<MediaSendFlowState>,
  private val parentEventEmitter: (MediaSendFlowEvent) -> Unit,
  selectedCaptureScreen: MediaSendRoute.Capture,
  private val repository: MediaCaptureRepository = MediaCaptureRepository()
) : EventDrivenViewModel<MediaCaptureScreenEvents>(TAG) {

  companion object {
    private val TAG = Log.tag(MediaCaptureViewModel::class)
  }

  private val _state: MutableStateFlow<MediaCaptureState> = MutableStateFlow(
    with(parentState.value) {
      MediaCaptureState(
        selectedCaptureScreen = selectedCaptureScreen,
        selectedMedia = selectedMedia,
        isCameraFirst = isCameraFirst,
        isStory = isStory,
        storiesEnabled = storiesEnabled,
        mode = mode,
        mediaConstraints = mediaConstraints,
        storyMaxVideoDuration = storyMaxVideoDuration
      )
    }
  )

  val state: StateFlow<MediaCaptureState> = _state.asStateFlow()

  init {
    parentState
      .distinctUntilChangedBy { it.selectedMedia }
      .onEach { onEvent(MediaCaptureScreenEvents.ParentStateChanged(it)) }
      .launchIn(viewModelScope)
  }

  override suspend fun processEvent(event: MediaCaptureScreenEvents) {
    when (event) {
      is MediaCaptureScreenEvents.ParentStateChanged -> _state.update { it.copy(selectedMedia = event.parentState.selectedMedia) }
      is MediaCaptureScreenEvents.SelectedCaptureScreenChanged -> _state.update { it.copy(selectedCaptureScreen = event.selectedCaptureScreen) }
      MediaCaptureScreenEvents.ShowCamera -> parentEventEmitter(MediaSendFlowEvent.NavigateToCamera)
      MediaCaptureScreenEvents.ShowTextStory -> parentEventEmitter(MediaSendFlowEvent.NavigateToTextStory)
      MediaCaptureScreenEvents.NextClicked -> parentEventEmitter(MediaSendFlowEvent.NavigateToEdit)
      is MediaCaptureScreenEvents.Camera -> processCameraEvent(event.event)
    }
  }

  private fun processCameraEvent(event: CameraXScreenEvents) {
    when (event) {
      is CameraXScreenEvents.ImageCaptured -> captureMedia(R.string.MediaSendViewModel__error_taking_photo) {
        repository.writeCapturedImage(event.data, event.width, event.height)
      }

      is CameraXScreenEvents.VideoCaptured -> captureMedia(R.string.MediaSendViewModel__error_recording_video, event.durationMs.milliseconds) {
        repository.writeCapturedVideo(event.fd)
      }

      CameraXScreenEvents.VideoCaptureError -> showSnackbar(R.string.MediaSendViewModel__error_recording_video)
      is CameraXScreenEvents.QrCodeFound -> parentEventEmitter(MediaSendFlowEvent.QrCodeScanned(event.data))
      CameraXScreenEvents.GalleryClicked -> parentEventEmitter(MediaSendFlowEvent.NavigateToFolders)
      CameraXScreenEvents.CameraCloseClicked -> parentEventEmitter(MediaSendFlowEvent.CloseRequested)
    }
  }

  /** Hands the capture [write] produces to the flow, or says why nothing arrived if it could not be written out. */
  private fun captureMedia(@StringRes errorMessage: Int, recordingDuration: Duration? = null, write: suspend () -> Media?) {
    viewModelScope.launch {
      val media = write()

      if (media != null) {
        parentEventEmitter(MediaSendFlowEvent.MediaCaptured(media, recordingDuration))
      } else {
        showSnackbar(errorMessage)
      }
    }
  }

  private fun showSnackbar(@StringRes message: Int) {
    parentEventEmitter(MediaSendFlowEvent.ShowSnackbar(SnackbarEvent(message = message)))
  }

  class Factory(
    private val parentState: StateFlow<MediaSendFlowState>,
    private val parentEventEmitter: (MediaSendFlowEvent) -> Unit,
    private val selectedCaptureScreen: MediaSendRoute.Capture
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return MediaCaptureViewModel(parentState, parentEventEmitter, selectedCaptureScreen) as T
    }
  }
}
