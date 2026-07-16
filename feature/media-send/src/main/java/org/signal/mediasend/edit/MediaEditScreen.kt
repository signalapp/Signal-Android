/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.edit

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.fragment.compose.AndroidFragment
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.signal.core.ui.WindowBreakpoint
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.rememberWindowBreakpoint
import org.signal.imageeditor.core.model.EditorModel
import org.signal.mediasend.EditorState
import org.signal.mediasend.MediaSendState
import org.signal.mediasend.edit.image.ImageEditor
import org.signal.mediasend.edit.image.ImageEditorToolbar
import org.signal.mediasend.edit.image.RotationDial
import org.signal.mediasend.edit.video.VideoEditorFragment
import org.signal.mediasend.edit.video.VideoEditorToolbar
import org.signal.mediasend.edit.video.VideoEditorViewModel

@Composable
fun MediaEditScreen(
  state: MediaSendState,
  onEvent: (MediaEditScreenEvent) -> Unit
) {
  val scope = rememberCoroutineScope()

  val pagerState = rememberPagerState(
    initialPage = state.focusedMedia?.let { state.selectedMedia.indexOf(it).coerceAtLeast(0) } ?: 0,
    pageCount = { state.selectedMedia.size }
  )

  // Media captured from the camera is added to the selection asynchronously, so the Edit screen can compose before the
  // new item lands in selectedMedia. Keep the pager aligned with focusedMedia once it does.
  LaunchedEffect(state.focusedMedia, state.selectedMedia) {
    val targetPage = state.focusedMedia?.let { state.selectedMedia.indexOf(it) } ?: -1
    if (targetPage >= 0 && targetPage != pagerState.currentPage) {
      pagerState.scrollToPage(targetPage)
    }
  }

  // During a camera-first flow, backing out of edit when the only selection is the capture itself should discard the
  // capture and return to the camera rather than leaving the empty editor on the back stack.
  val isOnlyCameraFirstCapture = state.cameraFirstCapture != null &&
    state.selectedMedia.size == 1 &&
    state.selectedMedia.firstOrNull() == state.cameraFirstCapture
  BackHandler(enabled = isOnlyCameraFirstCapture) {
    onEvent(MediaEditScreenEvent.NavigateBack)
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .navigationBarsPadding()
  ) {
    val isSmallWindowBreakpoint = rememberWindowBreakpoint() is WindowBreakpoint.Small
    val imageControllers = remember { ImageController.Container() }

    val videoEditorViewModel = rememberVideoEditorViewModel()

    val focusedUri = state.focusedMedia?.uri
    val focusedEditorState = focusedUri?.let { state.editorStateMap[it] }
    val imageController = if (focusedUri != null && focusedEditorState is EditorState.Image) {
      imageControllers.getOrCreate(focusedUri, focusedEditorState.model)
    } else {
      null
    }

    var isVideoInteracting by remember(focusedUri) { mutableStateOf(false) }
    val isInteracting = imageController?.isUserInEdit == true || isVideoInteracting

    HorizontalPager(
      state = pagerState,
      modifier = Modifier.fillMaxSize(),
      snapPosition = SnapPosition.Center,
      userScrollEnabled = !isInteracting
    ) { index ->
      val uri = state.selectedMedia[index].uri
      when (val editorState = state.editorStateMap[uri]) {
        is EditorState.Image -> {
          ImageEditor(
            controller = imageControllers.getOrCreate(uri, editorState.model),
            modifier = Modifier.fillMaxSize()
          )
        }

        is EditorState.VideoTrim -> {
          val media = state.selectedMedia[index]
          AndroidFragment<VideoEditorFragment>(
            modifier = Modifier.fillMaxSize(),
            arguments = VideoEditorFragment.arguments(media.uri, maxAttachmentSize = 0L, isVideoGif = media.isVideoGif)
          ) { fragment ->
            fragment.onStateUpdate(
              state.focusedMedia?.uri,
              state.isTouchEnabled,
              state::getOrCreateVideoTrimData
            )
          }
        }

        null -> {
          if (!LocalInspectionMode.current) {
            error("Invalid editor state.")
          } else {
            Box(modifier = Modifier.fillMaxSize().background(color = Previews.rememberRandomColor()))
          }
        }
      }
    }

    val isTextEditing = imageController?.textEditingElement != null

    Column(
      verticalArrangement = spacedBy(20.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .then(if (isTextEditing) Modifier.imePadding() else Modifier)
    ) {
      if (state.selectedMedia.isNotEmpty() && !isInteracting) {
        ThumbnailRow(
          selectedMedia = state.selectedMedia,
          pagerState = pagerState,
          onFocusedMediaChange = {
            onEvent(MediaEditScreenEvent.FocusedMediaChanged(it))
          },
          onThumbnailClick = { index ->
            scope.launch {
              pagerState.animateScrollToPage(index)
            }
          }
        )
      }

      when (focusedEditorState) {
        is EditorState.Image -> {
          imageController?.let { controller ->
            if (controller.mode == ImageController.Mode.CROP) {
              RotationDial(
                imageEditorController = controller,
                modifier = Modifier
                  .widthIn(max = 380.dp)
                  .padding(horizontal = 16.dp)
              )
            }
            if (isSmallWindowBreakpoint) {
              ImageEditorToolbar(imageEditorController = controller)
            }
          }
        }
        is EditorState.VideoTrim -> {
          val playbackPositionUs by produceState(focusedEditorState.videoTrimData.startTimeUs, focusedUri) {
            videoEditorViewModel.events(focusedUri).collect { event ->
              if (event is VideoEditorViewModel.Event.ActualPositionChanged) {
                value = event.positionUs
              }
            }
          }

          VideoEditorToolbar(
            videoTrimData = focusedEditorState.videoTrimData,
            playbackPositionUs = playbackPositionUs,
            onEvent = { event ->
              when (event) {
                is MediaEditScreenEvent.VideoTrimChanged -> {
                  isVideoInteracting = !event.editingComplete
                  onEvent(event)
                }
                is MediaEditScreenEvent.VideoSeek -> {
                  isVideoInteracting = !event.editingComplete
                  videoEditorViewModel.sendCommand(
                    focusedUri,
                    if (event.editingComplete) {
                      VideoEditorViewModel.Command.EndPositionDrag(event.positionUs)
                    } else {
                      VideoEditorViewModel.Command.PositionDrag(event.positionUs)
                    }
                  )
                }
                else -> onEvent(event)
              }
            }
          )
        }
        null -> Unit
      }

      if (!isInteracting) {
        AddAMessageRow(
          message = state.message,
          onEvent = onEvent,
          onNextClick = { onEvent(MediaEditScreenEvent.NavigateToSend) },
          modifier = Modifier
            .widthIn(max = 624.dp)
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp)
        )
      }
    }

    if (!isSmallWindowBreakpoint && imageController != null) {
      ImageEditorToolbar(
        imageEditorController = imageController,
        modifier = Modifier
          .align(Alignment.CenterEnd)
          .padding(end = 24.dp)
          .then(if (isTextEditing) Modifier.imePadding() else Modifier)
      )
    }
  }
}

@Composable
private fun rememberVideoEditorViewModel(): VideoEditorViewModel {
  return if (LocalInspectionMode.current) {
    remember { VideoEditorViewModel() }
  } else {
    viewModel<VideoEditorViewModel>(viewModelStoreOwner = LocalActivity.current as ViewModelStoreOwner)
  }
}

@AllDevicePreviews
@Composable
private fun MediaEditScreenPreview() {
  val selectedMedia = rememberPreviewMedia(10)

  Previews.Preview {
    MediaEditScreen(
      state = MediaSendState(
        selectedMedia = selectedMedia,
        focusedMedia = selectedMedia.first(),
        editorStateMap = mutableMapOf(
          selectedMedia.first().uri to EditorState.Image(EditorModel.create(0))
        )
      ),
      onEvent = {}
    )
  }
}
