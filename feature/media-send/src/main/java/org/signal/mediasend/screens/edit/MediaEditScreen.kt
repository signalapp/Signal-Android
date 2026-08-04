/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.edit

import android.net.Uri
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.fragment.compose.AndroidFragment
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.signal.core.ui.WindowBreakpoint
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.LocalChatColorProvider
import org.signal.core.ui.compose.LocalDisplayNameProvider
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.rememberWindowBreakpoint
import org.signal.glide.compose.GlideImage
import org.signal.glide.compose.GlideImageScaleType
import org.signal.glide.decryptableuri.DecryptableUri
import org.signal.imageeditor.core.model.EditorModel
import org.signal.mediasend.EditorState
import org.signal.mediasend.MediaSendDependencies
import org.signal.mediasend.MediaSendFlowState
import org.signal.mediasend.rememberPreviewState
import org.signal.mediasend.screens.MediaSendMetrics
import org.signal.mediasend.screens.edit.document.DocumentPage
import org.signal.mediasend.screens.edit.image.BlurFacesBar
import org.signal.mediasend.screens.edit.image.BrushWidthBar
import org.signal.mediasend.screens.edit.image.BrushWidthPreview
import org.signal.mediasend.screens.edit.image.DrawModeColorBar
import org.signal.mediasend.screens.edit.image.ImageEditor
import org.signal.mediasend.screens.edit.image.ImageEditorClearAllButton
import org.signal.mediasend.screens.edit.image.ImageEditorToolbar
import org.signal.mediasend.screens.edit.image.ImageEditorUndoRedoButtons
import org.signal.mediasend.screens.edit.image.RotationDial
import org.signal.mediasend.screens.edit.video.VideoEditorFragment
import org.signal.mediasend.screens.edit.video.VideoEditorViewModel
import org.signal.mediasend.screens.edit.video.VideoTrimBar

@Composable
internal fun MediaEditScreen(
  state: MediaSendFlowState,
  onEvent: (MediaEditScreenEvents) -> Unit,
  imageControllers: ImageController.Container
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
    onEvent(MediaEditScreenEvents.NavigateBack)
  }

  Box(modifier = Modifier.fillMaxSize()) {
    val isSmallWindowBreakpoint = rememberWindowBreakpoint() is WindowBreakpoint.Small
    val videoEditorViewModel = rememberVideoEditorViewModel()

    val focusedUri = state.focusedMedia?.uri
    val focusedEditorState = focusedUri?.let { state.editorStateMap[it] }
    val imageController = if (focusedUri != null && focusedEditorState is EditorState.Image) {
      imageControllers.getOrCreate(focusedUri, focusedEditorState.model)
    } else {
      null
    }

    // Composed after the camera-first handler so it wins while an editor mode is open: back should step out of that mode
    // rather than leave the screen.
    BackHandler(enabled = imageController?.canHandleBack == true) {
      imageController?.onBackPressed()
    }

    var isVideoInteracting by remember(focusedUri) { mutableStateOf(false) }
    var isAdjustingBrushWidth by remember(focusedUri) { mutableStateOf(false) }
    val isImageEditing = imageController?.isUserInEdit == true
    val isInteracting = isImageEditing || isVideoInteracting

    // Drags of the media itself, which every piece of chrome gets out of the way for. Sliders are excluded -- they are
    // chrome themselves, and clearing the screen would hide what they adjust.
    val isDragging = imageController?.imageEditorState?.isGestureActive == true || isVideoInteracting

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

        is EditorState.Document -> {
          DocumentPage(
            document = editorState,
            modifier = Modifier.fillMaxSize()
          )
        }

        EditorState.Gif -> {
          if (!LocalInspectionMode.current) {
            GlideImage(
              model = DecryptableUri(uri),
              scaleType = GlideImageScaleType.FIT_CENTER,
              contentScale = ContentScale.Fit,
              modifier = Modifier.fillMaxSize()
            )
          }
        }

        is EditorState.VideoTrim, EditorState.VideoGif -> {
          val media = state.selectedMedia[index]
          var videoEditorFragment by remember(media.uri) { mutableStateOf<VideoEditorFragment?>(null) }

          AndroidFragment<VideoEditorFragment>(
            modifier = Modifier.fillMaxSize(),
            arguments = VideoEditorFragment.arguments(media.uri, maxAttachmentSize = 0L, isVideoGif = media.isVideoGif)
          ) { fragment ->
            videoEditorFragment = fragment
          }

          // AndroidFragment's onUpdate fires only when the fragment is first added, so trim/focus changes have to be
          // pushed in from a keyed effect or the preview player never re-clips.
          val videoTrimData = (state.editorStateMap[media.uri] as? EditorState.VideoTrim)?.videoTrimData
          LaunchedEffect(videoEditorFragment, state.focusedMedia?.uri, state.isTouchEnabled, videoTrimData) {
            videoEditorFragment?.onStateUpdate(
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
            Box(
              modifier = Modifier
                .fillMaxSize()
                .background(color = Previews.rememberRandomColor())
            )
          }
        }
      }
    }

    if (imageController != null && (imageController.isUserDrawing || imageController.isUserBlurring)) {
      // A multi-touch commit/discard can tear down the bar mid-drag, so the terminal gesture callback is not guaranteed.
      DisposableEffect(Unit) {
        onDispose { isAdjustingBrushWidth = false }
      }

      BrushWidthPreview(
        visible = isAdjustingBrushWidth,
        thickness = imageController.brushThickness,
        viewMatrix = imageController.imageEditorState.viewMatrix,
        color = Color(imageController.drawColorBarState.color),
        isBlur = imageController.isUserBlurring,
        modifier = Modifier.fillMaxSize()
      )

      MediaEditControl(
        faded = isDragging,
        modifier = Modifier.align(Alignment.CenterStart)
      ) {
        BrushWidthBar(
          fraction = imageController.brushWidthFraction,
          onFractionChanged = { fraction, gestureComplete ->
            isAdjustingBrushWidth = !gestureComplete
            val tool = imageController.brushTool
            imageController.setBrushWidthFraction(fraction)

            if (gestureComplete && tool != null) {
              onEvent(MediaEditScreenEvents.BrushWidthChanged(tool, fraction))
            }
          }
        )
      }
    }

    val isTextEditing = imageController?.textEditingElement != null

    // Null whenever the destination is still to be chosen, which is what turns the trailing button back into a
    // "next" arrow.
    val recipientChatColor: Color? = state.recipientId?.let { LocalChatColorProvider.current(it.id).value }

    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .padding(bottom = 10.dp)
        .navigationBarsPadding()
        .then(if (isTextEditing) Modifier.imePadding() else Modifier)
    ) {
      Column(
        verticalArrangement = spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        if (focusedEditorState is EditorState.VideoTrim) {
          VideoTrimTimeline(
            videoUri = focusedUri,
            editorState = focusedEditorState,
            videoEditorViewModel = videoEditorViewModel,
            onInteractingChange = { isVideoInteracting = it },
            onEvent = onEvent
          )
        }

        if (state.selectedMedia.size > 1 && isAddMediaVisible(state, focusedEditorState)) {
          MediaEditControl(visible = !isImageEditing, faded = isDragging) {
            ThumbnailRow(
              selectedMedia = state.selectedMedia,
              pagerState = pagerState,
              enabled = !isInteracting,
              onFocusedMediaChange = {
                onEvent(MediaEditScreenEvents.FocusedMediaChanged(it))
              },
              onThumbnailClick = { index ->
                if (pagerState.currentPage == index) {
                  onEvent(MediaEditScreenEvents.RemoveMedia(state.selectedMedia[index]))
                } else {
                  scope.launch {
                    pagerState.animateScrollToPage(index)
                  }
                }
              },
              onReorder = { fromIndex, toIndex ->
                onEvent(MediaEditScreenEvents.ReorderSelectedMedia(fromIndex, toIndex))
              }
            )
          }
        }

        imageController?.let { controller ->
          if (controller.mode == ImageController.Mode.CROP) {
            RotationDial(
              imageEditorController = controller,
              modifier = Modifier
                .widthIn(max = 380.dp)
                .padding(horizontal = 16.dp)
            )
          }

          if (controller.isUserDrawing) {
            MediaEditControl(faded = isDragging) {
              DrawModeColorBar(imageEditorController = controller)
            }
          }

          if (controller.isUserBlurring) {
            MediaEditControl(faded = isDragging) {
              BlurFacesBar(
                checked = controller.isBlurringFaces,
                onCheckedChange = { onEvent(MediaEditScreenEvents.ToggleBlurFaces(it)) }
              )
            }
          }
        }

        if (isSmallWindowBreakpoint) {
          MediaToolbar(
            focusedUri = focusedUri,
            focusedEditorState = focusedEditorState,
            state = state,
            onEvent = onEvent,
            imageController = imageController,
            isTextEditing = isTextEditing,
            isDragging = isDragging
          )
        }
      }

      MediaEditControl(
        visible = !isImageEditing,
        faded = isDragging,
        enter = MediaSendMetrics.SlidingControlEnterTransition,
        exit = MediaSendMetrics.SlidingControlExitTransition
      ) {
        AddAMessageRow(
          enabled = !isInteracting && !state.isSending,
          canScheduleSend = !state.isStory,
          viewOnceAvailable = state.isViewOnceAvailable,
          viewOnce = state.isViewOnceEnabled,
          message = state.message,
          recipientChatColor = recipientChatColor,
          onEvent = onEvent,
          onNextClick = { onEvent(MediaEditScreenEvents.NextClick) },
          modifier = Modifier
            .widthIn(max = 624.dp)
            .padding(horizontal = 16.dp)
            // Own padding rather than the stack's arrangement so the gap collapses along with the slide.
            .padding(top = 20.dp, bottom = 16.dp)
        )
      }
    }

    if (!isSmallWindowBreakpoint) {
      MediaToolbar(
        focusedUri = focusedUri,
        focusedEditorState = focusedEditorState,
        state = state,
        onEvent = onEvent,
        imageController = imageController,
        isTextEditing = isTextEditing,
        isDragging = isDragging,
        modifier = Modifier
          .align(Alignment.CenterEnd)
      )
    }

    val displayNameState = state.recipientId?.let { LocalDisplayNameProvider.current(it.id) } ?: remember { mutableStateOf(null) }
    val displayName: String? by displayNameState

    MediaEditControl(
      faded = isDragging,
      modifier = Modifier
        .align(Alignment.TopCenter)
        .padding(top = 10.dp)
        .systemBarsPadding()
    ) {
      MediaEditSummaryPill(
        displayName = displayName,
        selectedMedia = state.selectedMedia,
        selectedPage = pagerState.currentPage
      )
    }

    ImageEditorUndoRedoButtons(
      imageEditorController = imageController,
      isDragging = isDragging,
      modifier = Modifier
        .align(Alignment.TopStart)
        .padding(top = 12.dp, start = 16.dp)
        .systemBarsPadding()
    )

    ImageEditorClearAllButton(
      imageEditorController = imageController,
      isDragging = isDragging,
      modifier = Modifier
        .align(Alignment.TopEnd)
        .padding(top = 12.dp, end = 16.dp)
        .systemBarsPadding()
    )

    if (state.isSavingMedia) {
      MediaEditScreenDialogs.SavingToStorageProgressDialog()
    }

    MediaEditScreenDialogs.DetectingFacesProgressDialog(visible = imageController?.isDetectingFaces == true)
  }
}

/**
 * Toolbar allowing for common actions
 */
@Composable
private fun MediaToolbar(
  state: MediaSendFlowState,
  onEvent: (MediaEditScreenEvents) -> Unit,
  focusedUri: Uri?,
  focusedEditorState: EditorState?,
  imageController: ImageController?,
  isTextEditing: Boolean,
  isDragging: Boolean,
  modifier: Modifier = Modifier
) {
  if (focusedUri == null || focusedEditorState == null) {
    return
  }

  // An empty toolbar would still claim its slot in the surrounding stack, so bail before the control is composed.
  if (focusedEditorState !is EditorState.Image && !hasSharedToolbarButtons(state, focusedEditorState)) {
    return
  }

  MediaEditControl(faded = isDragging, modifier = modifier) {
    when (focusedEditorState) {
      is EditorState.Image -> {
        imageController?.let {
          ImageEditorToolbar(
            imageEditorController = it,
            state = state,
            editorState = focusedEditorState,
            onEvent = onEvent,
            modifier = Modifier
              .navigationBarsPadding()
              .padding(end = 24.dp)
              .then(if (isTextEditing) Modifier.imePadding() else Modifier)
          )
        }
      }

      else -> MediaEditorToolbar {
        MediaEditorToolbarSharedButtons(
          state = state,
          editorState = focusedEditorState,
          onEvent = onEvent
        )
      }
    }
  }
}

/**
 * Trim/scrub timeline for the focused video. Drag state is reported through [onInteractingChange] so the rest of the
 * stack can get out of the way, and seeks are translated into player commands rather than screen events.
 */
@Composable
private fun VideoTrimTimeline(
  videoUri: Uri,
  editorState: EditorState.VideoTrim,
  videoEditorViewModel: VideoEditorViewModel,
  onInteractingChange: (Boolean) -> Unit,
  onEvent: (MediaEditScreenEvents) -> Unit
) {
  val playbackPositionUs by produceState(editorState.videoTrimData.startTimeUs, videoUri) {
    videoEditorViewModel.events(videoUri).collect { event ->
      if (event is VideoEditorViewModel.Event.ActualPositionChanged) {
        value = event.positionUs
      }
    }
  }

  VideoTrimBar(
    videoUri = videoUri,
    mediaInputFactory = MediaSendDependencies.mediaInputFactory,
    videoTrimData = editorState.videoTrimData,
    maxSelectableDurationUs = editorState.maxDurationUs,
    playbackPositionUs = playbackPositionUs,
    onEvent = { event ->
      when (event) {
        is MediaEditScreenEvents.VideoTrimChanged -> {
          onInteractingChange(!event.editingComplete)
          onEvent(event)
        }

        is MediaEditScreenEvents.VideoSeek -> {
          onInteractingChange(!event.editingComplete)
          videoEditorViewModel.sendCommand(
            videoUri,
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
      state = rememberPreviewState().copy(
        selectedMedia = selectedMedia,
        focusedMedia = selectedMedia.first(),
        editorStateMap = mutableMapOf(
          selectedMedia.first().uri to EditorState.Image(EditorModel.create(0))
        )
      ),
      onEvent = {},
      imageControllers = remember { ImageController.Container() }
    )
  }
}
