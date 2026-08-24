/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.edit

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.fragment.compose.AndroidFragment
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.signal.core.ui.WindowBreakpoint
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.LocalChatColorProvider
import org.signal.core.ui.compose.LocalDisplayNameProvider
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.horizontalGutters
import org.signal.core.ui.rememberWindowBreakpoint
import org.signal.core.util.ContentTypeUtil
import org.signal.glide.compose.GlideImage
import org.signal.glide.compose.GlideImageScaleType
import org.signal.glide.decryptableuri.DecryptableUri
import org.signal.imageeditor.core.model.EditorModel
import org.signal.mediasend.EditorState
import org.signal.mediasend.MediaConstraints
import org.signal.mediasend.PreviewMediaInputFactory
import org.signal.mediasend.screens.MediaSendMetrics
import org.signal.mediasend.screens.edit.document.DocumentPage
import org.signal.mediasend.screens.edit.image.BlurFacesBar
import org.signal.mediasend.screens.edit.image.BrushWidthBar
import org.signal.mediasend.screens.edit.image.BrushWidthPreview
import org.signal.mediasend.screens.edit.image.DrawAnywhereToBlurPill
import org.signal.mediasend.screens.edit.image.DrawModeColorBar
import org.signal.mediasend.screens.edit.image.ImageEditor
import org.signal.mediasend.screens.edit.image.ImageEditorClearAllButton
import org.signal.mediasend.screens.edit.image.ImageEditorToolbar
import org.signal.mediasend.screens.edit.image.ImageEditorUndoRedoButtons
import org.signal.mediasend.screens.edit.image.RotationDial
import org.signal.mediasend.screens.edit.video.VideoEditorFragment
import org.signal.mediasend.screens.edit.video.VideoEditorViewModel
import org.signal.mediasend.screens.edit.video.VideoSizeHint
import org.signal.mediasend.screens.edit.video.VideoTrimBar
import org.signal.mediasend.screens.edit.video.VideoTrimData
import org.thoughtcrime.securesms.video.TranscodingConfig
import org.thoughtcrime.securesms.video.interfaces.MediaInputFactory
import kotlin.time.Duration.Companion.milliseconds

/** After this, a kind's projection is fixed. */
private val CHROME_SETTLE_WINDOW = 500.milliseconds

@Composable
internal fun MediaEditScreen(
  state: MediaEditState,
  onEvent: (MediaEditScreenEvents) -> Unit,
  imageControllers: ImageController.Container,
  mediaInputFactory: MediaInputFactory
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

  // Read through the latest selection rather than the one captured when the effect below started, since removals and
  // reordering change which media a settled page refers to without restarting it.
  val currentSelectedMedia by rememberUpdatedState(state.selectedMedia)

  // Focus belongs to the pager rather than to any one piece of chrome: the thumbnail rail is not composed for
  // documents, and a swipe still has to be reported from there.
  //
  // Observed as the settled page rather than as the falling edge of isScrollInProgress. snapshotFlow re-reads its block
  // when it resumes and drops a value equal to the one it last emitted, so a scroll that both starts and finishes
  // between two resumptions -- one fling of a fast flick through a long selection, or the instant scroll of
  // scrollToPage -- collapses to a single false and reports nothing, leaving focus on the page the user swiped away
  // from. A page index cannot collapse that way: whatever the pager last came to rest on is what gets reported.
  // Reading the page from the emission rather than from currentPage also keeps the report tied to that resting page
  // instead of to wherever a later fling has since moved.
  LaunchedEffect(pagerState) {
    snapshotFlow { pagerState.settledPage }
      .drop(1)
      .collect { settledPage ->
        val settledMedia = currentSelectedMedia.getOrNull(settledPage)
        if (settledMedia != null) {
          onEvent(MediaEditScreenEvents.FocusedMediaChanged(settledMedia))
        }
      }
  }

  BackHandler(enabled = state.isOnlyCameraFirstCapture) {
    onEvent(MediaEditScreenEvents.NavigateBack)
  }

  val chromeInsets = remember { MediaEditChromeInsetsState() }
  var rootSize by remember { mutableStateOf(IntSize.Zero) }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .onGloballyPositioned { chromeInsets.rootCoordinates = it }
      .onSizeChanged { rootSize = it }
  ) {
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
      scope.launch { imageController?.onBackPressed() }
    }

    var isVideoInteracting by remember(focusedUri) { mutableStateOf(false) }
    var isAdjustingBrushWidth by remember(focusedUri) { mutableStateOf(false) }
    val isImageEditing = imageController?.isUserInEdit == true
    val isZooming = imageController?.mode == ImageController.Mode.ZOOM
    val isInteracting = isImageEditing || isVideoInteracting || isZooming

    // Drags of the media itself, which every piece of chrome gets out of the way for. Sliders are excluded -- they are
    // chrome themselves, and clearing the screen would hide what they adjust.
    val isDragging = imageController?.imageEditorState?.isGestureActive == true || isVideoInteracting

    // A zoomed image gets the screen to itself in the same way a drag does, until a tap asks for the chrome back.
    val isChromeFaded = isDragging || imageController?.isChromeFadedForZoom == true

    val isAtRest = !isImageEditing && !isVideoInteracting
    val isAtRestState by rememberUpdatedState(isAtRest)
    val focusedChromeKind by rememberUpdatedState(focusedEditorState.chromeKind())
    LaunchedEffect(chromeInsets, rootSize) {
      chromeInsets.thaw()

      val restingChrome = snapshotFlow { if (isAtRestState) chromeInsets.measured else null }
        .filterNotNull()
        .filter { it != ChromeInsets() }
        .distinctUntilChanged()

      snapshotFlow { if (isAtRestState) focusedChromeKind else null }
        .filterNotNull()
        .distinctUntilChanged()
        .collectLatest { kind ->
          if (chromeInsets.isFrozen(kind)) return@collectLatest

          chromeInsets.settle(kind, restingChrome.first())

          withTimeoutOrNull(CHROME_SETTLE_WINDOW) {
            restingChrome.collect { chromeInsets.settle(kind, it) }
          }
          chromeInsets.freeze(kind)
        }
    }

    val gutter = with(LocalDensity.current) { MediaSendMetrics.MediaProjectionGutter.toPx() }

    HorizontalPager(
      state = pagerState,
      modifier = Modifier.fillMaxSize(),
      snapPosition = SnapPosition.Center,
      userScrollEnabled = !isInteracting
    ) { index ->
      val uri = state.selectedMedia[index].uri
      val editorState = state.editorStateMap[uri]

      // This page's own kind, so a swiped-away video's trim bar does not go on padding an image.
      val pageInsets = chromeInsets.contentInsetsFor(editorState.chromeKind(), gutter)
      val pagePadding = animatedPagePadding(pageInsets)

      when (editorState) {
        is EditorState.Image -> {
          // Padded via the editor viewport, not the layout, so the canvas stays full-bleed.
          ImageEditor(
            controller = imageControllers.getOrCreate(uri, editorState.model),
            contentInsets = pageInsets,
            modifier = Modifier.fillMaxSize()
          )
        }

        is EditorState.Document -> {
          DocumentPage(
            document = editorState,
            modifier = Modifier
              .fillMaxSize()
              .padding(pagePadding)
          )
        }

        EditorState.Gif -> {
          if (!LocalInspectionMode.current) {
            GlideImage(
              model = DecryptableUri(uri),
              scaleType = GlideImageScaleType.FIT_CENTER,
              contentScale = ContentScale.Fit,
              modifier = Modifier
                .fillMaxSize()
                .padding(pagePadding)
            )
          }
        }

        is EditorState.VideoTrim, EditorState.VideoGif -> {
          if (LocalInspectionMode.current) {
            Box(
              modifier = Modifier
                .fillMaxSize()
                .background(color = Color.Red)
            )
            return@HorizontalPager
          }

          val media = state.selectedMedia[index]
          var videoEditorFragment by remember(media.uri) { mutableStateOf<VideoEditorFragment?>(null) }

          AndroidFragment<VideoEditorFragment>(
            modifier = Modifier
              .fillMaxSize()
              .padding(pagePadding),
            arguments = VideoEditorFragment.arguments(media.uri, maxAttachmentSize = 0L, isVideoGif = media.isVideoGif, width = media.width, height = media.height)
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
        faded = isChromeFaded,
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
        .reportChromeInset(chromeInsets, ChromeSlot.BOTTOM, ChromeEdge.BOTTOM)
    ) {
      Column(
        verticalArrangement = spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        if (focusedEditorState is EditorState.VideoTrim && MediaConstraints.isVideoTranscodeAvailable()) {
          VideoTrimTimeline(
            videoUri = focusedUri,
            editorState = focusedEditorState,
            transcodingTiers = state.videoTranscodingTiers,
            mediaInputFactory = mediaInputFactory,
            videoEditorViewModel = videoEditorViewModel,
            onInteractingChange = { isVideoInteracting = it },
            onEvent = onEvent
          )
        }

        if (state.selectedMedia.size > 1 && isAddMediaVisible(state, focusedEditorState)) {
          MediaEditControl(visible = !isImageEditing, faded = isChromeFaded) {
            ThumbnailRow(
              selectedMedia = state.selectedMedia,
              pagerState = pagerState,
              enabled = !isInteracting,
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
            MediaEditControl(faded = isChromeFaded) {
              DrawModeColorBar(imageEditorController = controller)
            }
          }

          if (controller.isUserBlurring) {
            MediaEditControl(faded = isChromeFaded) {
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
            faded = isChromeFaded
          )
        }
      }

      MediaEditControl(
        visible = !isImageEditing,
        faded = isChromeFaded,
        enter = MediaSendMetrics.SlidingControlEnterTransition,
        exit = MediaSendMetrics.SlidingControlExitTransition
      ) {
        AddAMessageRow(
          enabled = !isInteracting && !state.isSending,
          canScheduleSend = !state.isStory,
          viewOnceAvailable = state.isViewOnceAvailable,
          viewOnce = state.isViewOnceEnabled,
          isReply = state.isReply,
          message = state.message,
          recipientChatColor = recipientChatColor,
          onEvent = onEvent,
          onNextClick = { onEvent(MediaEditScreenEvents.NextClick) },
          modifier = Modifier
            .widthIn(max = MediaSendMetrics.BottomBarMaxWidth)
            .padding(horizontal = 16.dp)
            // Own padding rather than the stack's arrangement so the gap collapses along with the slide.
            .padding(top = 20.dp, bottom = 16.dp)
        )
      }
    }

    if (!isSmallWindowBreakpoint) {
      DisposableEffect(Unit) {
        onDispose { chromeInsets.clear(ChromeSlot.SIDE_RAIL) }
      }

      // Wrapped so the slot keeps reporting, at zero size, when MediaToolbar composes nothing.
      Box(
        modifier = Modifier
          .align(Alignment.CenterEnd)
          .reportChromeInset(chromeInsets, ChromeSlot.SIDE_RAIL, ChromeEdge.RIGHT)
      ) {
        MediaToolbar(
          focusedUri = focusedUri,
          focusedEditorState = focusedEditorState,
          state = state,
          onEvent = onEvent,
          imageController = imageController,
          isTextEditing = isTextEditing,
          faded = isChromeFaded
        )
      }
    }

    val displayNameState = state.recipientId?.let { LocalDisplayNameProvider.current(it.id) } ?: remember { mutableStateOf(null) }
    val displayName: String? by displayNameState

    // One band, so the media has a single top edge to clear.
    Box(
      modifier = Modifier
        .align(Alignment.TopCenter)
        .fillMaxWidth()
        .systemBarsPadding()
        .reportChromeInset(chromeInsets, ChromeSlot.TOP_BAND, ChromeEdge.TOP)
    ) {
      MediaEditControl(
        faded = isChromeFaded || isImageEditing,
        modifier = Modifier
          .align(Alignment.TopCenter)
          .padding(top = 10.dp)
      ) {
        if (imageController?.isUserBlurring == true) {
          DrawAnywhereToBlurPill()
        } else {
          MediaEditSummaryPill(
            displayName = displayName,
            selectedMedia = state.selectedMedia,
            selectedPage = pagerState.currentPage
          )
        }
      }

      ImageEditorUndoRedoButtons(
        imageEditorController = imageController,
        faded = isChromeFaded,
        modifier = Modifier
          .align(Alignment.TopStart)
          .padding(top = 12.dp, start = 16.dp)
      )

      ImageEditorClearAllButton(
        imageEditorController = imageController,
        faded = isChromeFaded,
        modifier = Modifier
          .align(Alignment.TopEnd)
          .padding(top = 12.dp, end = 16.dp)
      )
    }

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
  state: MediaEditState,
  onEvent: (MediaEditScreenEvents) -> Unit,
  focusedUri: Uri?,
  focusedEditorState: EditorState?,
  imageController: ImageController?,
  isTextEditing: Boolean,
  faded: Boolean,
  modifier: Modifier = Modifier
) {
  if (focusedUri == null || focusedEditorState == null) {
    return
  }

  // An empty toolbar would still claim its slot in the surrounding stack, so bail before the control is composed.
  if (focusedEditorState !is EditorState.Image && !hasSharedToolbarButtons(state, focusedEditorState)) {
    return
  }

  MediaEditControl(faded = faded, modifier = modifier) {
    when (focusedEditorState) {
      is EditorState.Image -> {
        val breakpoint = rememberWindowBreakpoint()
        val modifier = if (breakpoint is WindowBreakpoint.Small) {
          Modifier
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
        } else {
          Modifier.padding(end = 24.dp)
        }

        imageController?.let {
          ImageEditorToolbar(
            imageEditorController = it,
            state = state,
            editorState = focusedEditorState,
            onEvent = onEvent,
            modifier = modifier
              .then(if (isTextEditing) Modifier.imePadding() else Modifier),
            enabled = !faded
          )
        }
      }

      else -> MediaEditorToolbar {
        MediaEditorToolbarSharedButtons(
          state = state,
          editorState = focusedEditorState,
          onEvent = onEvent,
          enabled = !faded
        )
      }
    }
  }
}

/**
 * Trim/scrub timeline for the focused video, with the resulting duration and estimated upload size beneath it. Drag
 * state is reported through [onInteractingChange] so the rest of the stack can get out of the way, and seeks are
 * translated into player commands rather than screen events.
 */
@Composable
private fun VideoTrimTimeline(
  videoUri: Uri,
  editorState: EditorState.VideoTrim,
  transcodingTiers: List<TranscodingConfig.QualityTier>,
  mediaInputFactory: MediaInputFactory,
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

  Column(
    horizontalAlignment = Alignment.End,
    modifier = Modifier
      .widthIn(max = MediaSendMetrics.BottomBarMaxWidth)
      .fillMaxWidth()
  ) {
    VideoTrimBar(
      videoUri = videoUri,
      mediaInputFactory = mediaInputFactory,
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

    // Gutters to match the bar's, so the hint's end lines up with the end of the timeline.
    VideoSizeHint(
      transcodingTiers = transcodingTiers,
      duration = editorState.videoTrimData.getDuration(),
      modifier = Modifier
        .horizontalGutters()
        .padding(top = 4.dp)
    )
  }
}

/** Eases the one correction a page gets when its kind is first measured. */
@Composable
private fun animatedPagePadding(insets: ChromeInsets): PaddingValues {
  val density = LocalDensity.current
  val horizontal by animateDpAsState(with(density) { insets.left.toDp() }, label = "pageInsetHorizontal")
  val top by animateDpAsState(with(density) { insets.top.toDp() }, label = "pageInsetTop")
  val bottom by animateDpAsState(with(density) { insets.bottom.toDp() }, label = "pageInsetBottom")

  return PaddingValues(start = horizontal, top = top, end = horizontal, bottom = bottom)
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
      state = MediaEditState(
        selectedMedia = selectedMedia,
        focusedMedia = selectedMedia.first(),
        editorStateMap = mapOf(selectedMedia.first().uri to EditorState.Image(EditorModel.create(0)))
      ),
      onEvent = {},
      imageControllers = remember { ImageController.Container() },
      mediaInputFactory = PreviewMediaInputFactory
    )
  }
}

@AllDevicePreviews
@Composable
private fun MediaEditScreenVideoPreview() {
  val selectedMedia = rememberPreviewMedia(10, contentType = ContentTypeUtil.VIDEO_MP4)

  Previews.Preview {
    MediaEditScreen(
      state = MediaEditState(
        selectedMedia = selectedMedia,
        focusedMedia = selectedMedia.first(),
        editorStateMap = mapOf(selectedMedia.first().uri to EditorState.VideoTrim(VideoTrimData()))
      ),
      onEvent = {},
      imageControllers = remember { ImageController.Container() },
      mediaInputFactory = PreviewMediaInputFactory
    )
  }
}
