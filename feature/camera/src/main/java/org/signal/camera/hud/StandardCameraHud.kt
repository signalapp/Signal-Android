/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.camera.hud

import android.content.res.Configuration
import android.view.KeyEvent
import android.view.Surface
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.signal.camera.CameraDisplay
import org.signal.camera.CameraScreenState
import org.signal.camera.CaptureError
import org.signal.camera.FlashMode
import org.signal.camera.R
import org.signal.camera.test.TestTags
import org.signal.core.ui.WindowBreakpoint
import org.signal.core.ui.compose.AllNightPreviews
import org.signal.core.ui.compose.Chrome
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.rememberWindowBreakpoint
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Default maximum recording duration: 60 seconds */
const val DEFAULT_MAX_RECORDING_DURATION_MS = 60_000L

/** Separates the zoom bar from the capture button below it on a phone. */
private val ZOOM_BAR_BOTTOM_MARGIN = 16.dp

/** How far the zoom bar sits in from the start edge on anything larger than a phone. */
private val ZOOM_BAR_SIDE_MARGIN = 16.dp

/** How long the time and the paused label take to trade the recording red between them. */
private const val PAUSED_TRANSITION_MS = 200

/** The close and flash buttons along the top of the window, whose middle the recording pill lines itself up with. */
private val TOP_CONTROL_SIZE = 48.dp

/** How far the top controls sit in from the edges of the window. */
private val TOP_CONTROL_MARGIN = 16.dp

data class StringResources(
  @param:StringRes val photoCaptureFailed: Int = 0,
  @param:StringRes val photoProcessingFailed: Int = 0,
  @param:StringRes val switchCamera: Int = 0,
  @param:StringRes val flashOff: Int = 0,
  @param:StringRes val flashOn: Int = 0,
  @param:StringRes val flashAuto: Int = 0,
  @param:StringRes val send: Int = 0,
  @param:StringRes val recordingPaused: Int = 0
)

/**
 * A standard camera HUD that provides common camera controls:
 * - Flash toggle button
 * - Capture button (tap for photo, long press for video)
 * - Camera switch button
 * - Gallery button
 * - Recording duration display
 * - Flash overlay animation
 *
 * This composable is designed to be used as the content of [org.signal.camera.CameraScreen]:
 *
 * ```kotlin
 * CameraScreen(
 *   state = viewModel.state.value,
 *   emitter = { viewModel.onEvent(it) }
 * ) {
 *   StandardCameraHud(
 *     state = viewModel.state.value,
 *     maxRecordingDurationMs = 30_000L,
 *     emitter = { event ->
 *       when (event) {
 *         is CameraHudEvents.PhotoCaptured -> savePhoto(event.bitmap)
 *         is CameraHudEvents.VideoCaptured -> handleVideo(event.result)
 *         is CameraHudEvents.GalleryClick -> openGallery()
 *       }
 *     }
 *   )
 * }
 * ```
 *
 * @param state The current camera screen state
 * @param maxRecordingDurationMs Maximum video recording duration in milliseconds, after which recording stops itself
 * @param captureButtonMode Which kind of capture the button offers while it is not recording
 * @param mediaSelectionCount Number of media items currently selected (shows count indicator when > 0)
 * @param emitter Callback for HUD events (photo captured, video captured, gallery click)
 */
@Composable
fun BoxScope.StandardCameraHud(
  state: CameraScreenState,
  emitter: (StandardCameraHudEvents) -> Unit,
  modifier: Modifier = Modifier,
  maxRecordingDurationMs: Long = DEFAULT_MAX_RECORDING_DURATION_MS,
  captureButtonMode: CaptureButtonMode = CaptureButtonMode.PHOTO,
  hasAudioPermission: () -> Boolean = { true },
  stringResources: StringResources = StringResources(0, 0)
) {
  val context = LocalContext.current
  val focusRequester = remember { FocusRequester() }
  val viewConfiguration = LocalViewConfiguration.current
  var volumeKeyPressStartTime by remember { mutableLongStateOf(0L) }
  var isRecordingFromVolumeKey by remember { mutableStateOf(false) }
  var activeVolumeKeyCode by remember { mutableIntStateOf(0) }

  LaunchedEffect(Unit) {
    focusRequester.requestFocus()
  }

  LaunchedEffect(state.isRecording) {
    if (!state.isRecording) {
      isRecordingFromVolumeKey = false
    }
  }

  LaunchedEffect(state.captureError) {
    state.captureError?.let { error ->
      val message = when (error) {
        is CaptureError.PhotoCaptureFailed -> stringResources.photoCaptureFailed
        is CaptureError.PhotoProcessingFailed -> stringResources.photoProcessingFailed
      }
      Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
      emitter(StandardCameraHudEvents.ClearCaptureError)
    }
  }

  LaunchedEffect(state.isRecording, state.recordingDuration, maxRecordingDurationMs) {
    if (state.isRecording && maxRecordingDurationMs > 0 && state.recordingDuration >= maxRecordingDurationMs) {
      emitter(StandardCameraHudEvents.VideoCaptureStopped)
    }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .focusRequester(focusRequester)
      .onPreviewKeyEvent { keyEvent ->
        val nativeEvent = keyEvent.nativeKeyEvent
        val keyCode = nativeEvent.keyCode

        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
          return@onPreviewKeyEvent false
        }

        when (nativeEvent.action) {
          KeyEvent.ACTION_DOWN -> {
            if (nativeEvent.repeatCount == 0) {
              if (activeVolumeKeyCode == 0) {
                activeVolumeKeyCode = keyCode
                volumeKeyPressStartTime = nativeEvent.eventTime
                isRecordingFromVolumeKey = false
              }
            } else if (keyCode == activeVolumeKeyCode &&
              !state.isRecording &&
              !isRecordingFromVolumeKey &&
              volumeKeyPressStartTime > 0 &&
              nativeEvent.eventTime - volumeKeyPressStartTime >= viewConfiguration.longPressTimeoutMillis
            ) {
              volumeKeyPressStartTime = 0
              if (hasAudioPermission()) {
                isRecordingFromVolumeKey = true
                emitter(StandardCameraHudEvents.VideoCaptureStarted(isLocked = false))
              } else {
                emitter(StandardCameraHudEvents.AudioPermissionRequired)
              }
            }
            true
          }

          KeyEvent.ACTION_UP -> {
            if (keyCode == activeVolumeKeyCode) {
              if (isRecordingFromVolumeKey) {
                isRecordingFromVolumeKey = false
                emitter(StandardCameraHudEvents.VideoCaptureStopped)
              } else if (volumeKeyPressStartTime > 0 && !state.isRecording) {
                emitter(StandardCameraHudEvents.PhotoCaptureTriggered)
              }
              volumeKeyPressStartTime = 0
              activeVolumeKeyCode = 0
            }
            true
          }

          else -> false
        }
      }
      .focusable()
  ) {
    StandardCameraHudContent(
      state = state,
      emitter = emitter,
      modifier = modifier,
      maxRecordingDurationMs = maxRecordingDurationMs,
      captureButtonMode = captureButtonMode,
      hasAudioPermission = hasAudioPermission,
      stringResources = stringResources
    )
  }
}

@Composable
private fun BoxScope.StandardCameraHudContent(
  state: CameraScreenState,
  emitter: (StandardCameraHudEvents) -> Unit,
  modifier: Modifier = Modifier,
  maxRecordingDurationMs: Long = DEFAULT_MAX_RECORDING_DURATION_MS,
  captureButtonMode: CaptureButtonMode = CaptureButtonMode.PHOTO,
  hasAudioPermission: () -> Boolean = { true },
  stringResources: StringResources = StringResources()
) {
  val breakpoint = rememberWindowBreakpoint()
  val orientation = LocalConfiguration.current.orientation
  val isPortraitPhone = breakpoint is WindowBreakpoint.Small && orientation == Configuration.ORIENTATION_PORTRAIT
  // The screen stays portrait on small; rotate the HUD icons to match the device so they stay upright.
  val iconRotation = if (isPortraitPhone) uprightRotationDegrees(state.deviceRotation) else 0f

  val captureButtonState = CaptureButtonState.of(
    captureButtonMode = captureButtonMode,
    isRecording = state.isRecording,
    isRecordingLocked = state.isRecordingLocked
  )

  // A held recording leaves only the capture button and what the finger can reach from it.
  val isRecordingHeld = captureButtonState == CaptureButtonState.RECORDING_HELD

  ShutterOverlay(state.showShutter)

  Chrome.Control(
    faded = isRecordingHeld,
    modifier = modifier.padding(TOP_CONTROL_MARGIN)
  ) {
    IconButton(
      onClick = { emitter(StandardCameraHudEvents.CloseClick) },
      enabled = !isRecordingHeld,
      modifier = Modifier
        .size(TOP_CONTROL_SIZE)
        .background(colorResource(R.color.CameraHud_control_background), shape = CircleShape)
        .testTag(TestTags.CAMERA_HUD_CLOSE_BUTTON)
    ) {
      Icon(
        imageVector = SignalIcons.X.imageVector,
        contentDescription = null,
        tint = colorResource(R.color.CameraHud_control_foreground),
        modifier = Modifier
          .size(24.dp)
          .rotate(iconRotation)
      )
    }
  }

  if (isPortraitPhone) {
    // The flash belongs to a photo the camera has yet to take, so the button goes for as long as a recording runs.
    Chrome.Control(
      visible = !state.isRecording,
      modifier = Modifier
        .align(Alignment.TopEnd)
        .padding(TOP_CONTROL_MARGIN)
    ) {
      FlashToggleButton(
        flashMode = state.flashMode,
        onToggle = { emitter(StandardCameraHudEvents.ToggleFlash) },
        stringResources = stringResources,
        modifier = Modifier.rotate(iconRotation)
      )
    }
  }

  if (state.isRecording) {
    RecordingDurationDisplay(
      durationMillis = state.recordingDuration,
      isPaused = state.isRecordingPaused,
      pausedLabel = if (stringResources.recordingPaused != 0) stringResource(stringResources.recordingPaused) else null,
      modifier = Modifier
        .align(Alignment.TopCenter)
        .padding(top = TOP_CONTROL_MARGIN)
    )
  }

  val cameraDisplay = CameraDisplay.rememberCameraDisplay(state.isLandscape)

  // The bar takes its modifier from wherever it is put. On a display it has nothing to offer on it draws no node at
  // all, so the spacing meant to separate it goes with it.
  val zoomBar: @Composable (Modifier) -> Unit = { zoomBarModifier ->
    ZoomBar(
      zoomRatio = state.zoomRatio,
      zoomRange = state.zoomRange,
      cameraDisplay = cameraDisplay,
      onZoomLevelClick = { emitter(StandardCameraHudEvents.SetZoomRatio(it.zoomLevel)) },
      modifier = zoomBarModifier,
      levelRotation = iconRotation,
      visible = !isRecordingHeld
    )
  }

  if (!isPortraitPhone) {
    zoomBar(
      Modifier
        .align(Alignment.CenterStart)
        .padding(start = ZOOM_BAR_SIDE_MARGIN)
    )
  }

  CameraControls(
    breakpoint = breakpoint,
    iconRotation = iconRotation,
    flashMode = state.flashMode,
    captureButtonState = captureButtonState,
    isRecordingPaused = state.isRecordingPaused,
    emitter = emitter,
    hasAudioPermission = hasAudioPermission,
    stringResources = stringResources,
    zoomBarSlot = zoomBar,
    modifier = modifier.align(if (isPortraitPhone) Alignment.BottomCenter else Alignment.CenterEnd)
  )
}

@Composable
private fun ShutterOverlay(showFlash: Boolean) {
  AnimatedVisibility(
    visible = showFlash,
    enter = fadeIn(animationSpec = tween(50)),
    exit = fadeOut(animationSpec = tween(200))
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .clip(RoundedCornerShape(16.dp))
        .background(Color.Black)
    )
  }
}

/** Rotates an offset by [degrees], to carry it between two frames rotated against each other. */
private fun Offset.rotatedBy(degrees: Float): Offset {
  if (degrees == 0f) {
    return this
  }

  val radians = degrees * PI.toFloat() / 180f
  val cosine = cos(radians)
  val sine = sin(radians)

  return Offset(x = x * cosine - y * sine, y = x * sine + y * cosine)
}

/** Degrees to rotate a HUD icon so it stays upright at the given committed [Surface] rotation. */
private fun uprightRotationDegrees(surfaceRotation: Int): Float = when (surfaceRotation) {
  Surface.ROTATION_90 -> 90f
  Surface.ROTATION_180 -> 180f
  Surface.ROTATION_270 -> 270f
  else -> 0f
}

/** What the HUD has asked the camera for, which it may not have reported yet. */
private enum class RequestedRecording {
  /** Nothing outstanding, so the button is free to ask for a recording. */
  NONE,

  /** A recording that runs on its own, so lifting a finger does not end it. */
  UNHELD,

  /** A recording the finger still on the capture button is keeping open, so lifting it ends it. */
  HELD
}

/**
 * What stands in the gallery's corner. Everything it can hold is the same circle, so each fades into the next in place.
 *
 * @param isOverLock Whether a drag from the capture button has reached the lock, which the lock answers for itself.
 * @param onLockCenterChanged Where the lock sits in the root's frame, which is one end of the drag that takes it.
 */
@Composable
private fun GallerySlot(
  captureButtonState: CaptureButtonState,
  isRecordingPaused: Boolean,
  isOverLock: Boolean,
  emitter: (StandardCameraHudEvents) -> Unit,
  onLockCenterChanged: (Offset) -> Unit
) {
  AnimatedContent(
    targetState = GallerySlotContent.of(captureButtonState, isOverLock),
    transitionSpec = { CameraHudMotion.swap },
    label = "GallerySlotContent",
    modifier = Modifier.onGloballyPositioned { onLockCenterChanged(it.boundsInRoot().center) }
  ) { slotContent ->
    when (slotContent) {
      GallerySlotContent.GALLERY -> GalleryThumbnailButton(
        onClick = { emitter(StandardCameraHudEvents.GalleryClick) },
        enabled = !captureButtonState.isRecording
      )

      GallerySlotContent.LOCK -> RecordingLockButton()
      GallerySlotContent.LOCK_ENGAGED -> RecordingLockButton(isEngaged = true)
      GallerySlotContent.PAUSE -> RecordingPauseButton(
        isPaused = isRecordingPaused,
        onClick = { emitter(StandardCameraHudEvents.RecordingPauseToggled) }
      )
    }
  }
}

/**
 * Camera control buttons layout with center element always truly centered
 * and side elements at fixed distances from edges.
 */
@Composable
private fun CameraControls(
  breakpoint: WindowBreakpoint,
  iconRotation: Float,
  captureButtonState: CaptureButtonState,
  isRecordingPaused: Boolean,
  flashMode: FlashMode,
  emitter: (StandardCameraHudEvents) -> Unit,
  hasAudioPermission: () -> Boolean,
  stringResources: StringResources,
  zoomBarSlot: @Composable (Modifier) -> Unit,
  modifier: Modifier = Modifier
) {
  val orientation = LocalConfiguration.current.orientation

  val currentEmitter by rememberUpdatedState(emitter)
  val currentHasAudioPermission by rememberUpdatedState(hasAudioPermission)
  val currentIsRecordingPaused by rememberUpdatedState(isRecordingPaused)

  // A recording takes a moment to report itself as running, so this is what the button goes by in the meantime.
  var requestedRecording by remember { mutableStateOf(RequestedRecording.NONE) }

  LaunchedEffect(captureButtonState.isRecording) {
    if (!captureButtonState.isRecording) {
      requestedRecording = RequestedRecording.NONE
    }
  }

  // Where the two ends of the drag to the lock are. They are measured rather than derived, since the lock sits beside
  // the capture button on a phone and below it on anything larger.
  var captureButtonCenter by remember { mutableStateOf(Offset.Zero) }
  var lockCenter by remember { mutableStateOf(Offset.Zero) }

  // Whether the thumb dragging from the capture button has reached the lock. The lock is the one that shows it: the
  // circle the drag carries there arrives underneath it, since this corner is drawn after the capture button.
  var isOverLock by remember { mutableStateOf(false) }

  val gallery: @Composable (CaptureButtonState) -> Unit = remember {
    movableContentOf { captureButtonState ->
      GallerySlot(
        captureButtonState = captureButtonState,
        isRecordingPaused = currentIsRecordingPaused,
        isOverLock = isOverLock,
        emitter = currentEmitter,
        onLockCenterChanged = { lockCenter = it }
      )
    }
  }

  // The state is passed as a movable-content parameter so it is read fresh on every invocation; capturing it in the
  // remembered lambda would freeze it at first composition.
  val captureButton: @Composable (CaptureButtonState) -> Unit = remember {
    movableContentOf { captureButtonState ->
      // Emits an event and reports whether it went out. A recording is turned away while the microphone is unavailable,
      // or while one already asked for has yet to be reported: the camera would refuse it, and the gesture behind it
      // would still believe it owned what the first ask started.
      val request: (StandardCameraHudEvents) -> Boolean = { event ->
        when {
          event is StandardCameraHudEvents.VideoCaptureStarted && !currentHasAudioPermission() -> {
            currentEmitter(StandardCameraHudEvents.AudioPermissionRequired)
            false
          }

          event is StandardCameraHudEvents.VideoCaptureStarted && requestedRecording != RequestedRecording.NONE -> false

          else -> {
            requestedRecording = when (event) {
              is StandardCameraHudEvents.VideoCaptureStarted -> if (event.isLocked) RequestedRecording.UNHELD else RequestedRecording.HELD
              is StandardCameraHudEvents.VideoCaptureStopped -> RequestedRecording.NONE
              else -> requestedRecording
            }

            currentEmitter(event)
            true
          }
        }
      }

      // A drag is measured in the button's own frame, and on a phone that frame turns with the device, so the way to
      // the lock has to be turned with it.
      val lockOffset = if (captureButtonState == CaptureButtonState.RECORDING_HELD && lockCenter != Offset.Zero) {
        (lockCenter - captureButtonCenter).rotatedBy(-iconRotation)
      } else {
        Offset.Zero
      }

      CaptureButton(
        state = captureButtonState,
        modifier = Modifier.onGloballyPositioned { captureButtonCenter = it.boundsInRoot().center },
        lockOffset = lockOffset,
        onLock = {
          requestedRecording = RequestedRecording.UNHELD
          currentEmitter(StandardCameraHudEvents.VideoCaptureLocked)
        },
        onOverLockChanged = { isOverLock = it },
        onTap = { captureButtonState.tapRequest?.let { request(it) } },
        onLongPressStart = {
          if (!captureButtonState.isRecording) {
            request(StandardCameraHudEvents.VideoCaptureStarted(isLocked = false))
          }
        },
        onLongPressEnd = {
          if (requestedRecording == RequestedRecording.HELD || captureButtonState == CaptureButtonState.RECORDING_HELD) {
            request(StandardCameraHudEvents.VideoCaptureStopped)
          }
        },
        onZoomChange = { currentEmitter(StandardCameraHudEvents.SetZoomLevel(it)) }
      )
    }
  }

  when {
    breakpoint is WindowBreakpoint.Small && orientation == Configuration.ORIENTATION_PORTRAIT -> {
      HorizontalControlBar(
        gallerySlot = gallery,
        captureSlot = captureButton,
        captureButtonState = captureButtonState,
        iconRotation = iconRotation,
        stringResources = stringResources,
        emitter = emitter,
        zoomBarSlot = zoomBarSlot,
        modifier = modifier
      )
    }

    else -> {
      VerticalControlBar(
        flashMode = flashMode,
        gallerySlot = gallery,
        captureSlot = captureButton,
        captureButtonState = captureButtonState,
        stringResources = stringResources,
        emitter = emitter,
        modifier = modifier
      )
    }
  }
}

@Composable
private fun HorizontalControlBar(
  gallerySlot: @Composable (CaptureButtonState) -> Unit,
  captureSlot: @Composable (CaptureButtonState) -> Unit,
  captureButtonState: CaptureButtonState,
  iconRotation: Float,
  stringResources: StringResources,
  emitter: (StandardCameraHudEvents) -> Unit,
  zoomBarSlot: @Composable (Modifier) -> Unit,
  modifier: Modifier
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = modifier.fillMaxWidth()
  ) {
    zoomBarSlot(Modifier.padding(bottom = ZOOM_BAR_BOTTOM_MARGIN))

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 40.dp, start = 40.dp, end = 40.dp)
    ) {
      CameraSwitchCorner(
        isRecording = captureButtonState.isRecording,
        iconRotation = iconRotation,
        stringResources = stringResources,
        emitter = emitter
      )
      Box(modifier = Modifier.align(Alignment.Center).rotate(iconRotation)) {
        captureSlot(captureButtonState)
      }
      Box(modifier = Modifier.align(Alignment.CenterStart).rotate(iconRotation)) {
        gallerySlot(captureButtonState)
      }
    }
  }
}

/**
 * The camera switch in its own corner of the bottom bar. The camera cannot be swapped out from under a running
 * recording, so the button goes for as long as one runs; it is aligned into the corner rather than laid out beside
 * anything, so nothing moves when it does.
 */
@Composable
private fun BoxScope.CameraSwitchCorner(
  isRecording: Boolean,
  iconRotation: Float,
  stringResources: StringResources,
  emitter: (StandardCameraHudEvents) -> Unit
) {
  Chrome.Control(
    visible = !isRecording,
    modifier = Modifier.align(Alignment.CenterEnd)
  ) {
    Box(modifier = Modifier.rotate(iconRotation)) {
      CameraSwitchButton(
        onClick = { emitter(StandardCameraHudEvents.SwitchCamera) },
        stringResources = stringResources
      )
    }
  }
}

@Composable
private fun VerticalControlBar(
  flashMode: FlashMode,
  gallerySlot: @Composable (CaptureButtonState) -> Unit,
  captureSlot: @Composable (CaptureButtonState) -> Unit,
  captureButtonState: CaptureButtonState,
  stringResources: StringResources,
  emitter: (StandardCameraHudEvents) -> Unit,
  modifier: Modifier
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = modifier
      .padding(vertical = 16.dp)
      .fillMaxHeight()
      .padding(end = 16.dp)
  ) {
    Box(
      contentAlignment = Alignment.BottomCenter,
      modifier = Modifier
        .weight(1f)
        .padding(bottom = 40.dp)
    ) {
      // Neither the flash nor the camera switch has anything to offer a running recording, so the pill goes whole.
      Chrome.Control(visible = !captureButtonState.isRecording) {
        FlashAndCameraTogglePill(
          flashMode = flashMode,
          emitter = emitter,
          stringResources = stringResources
        )
      }
    }

    captureSlot(captureButtonState)

    Box(
      contentAlignment = Alignment.TopCenter,
      modifier = Modifier
        .weight(1f)
        .padding(top = 40.dp)
    ) {
      gallerySlot(captureButtonState)
    }
  }
}

@Composable
private fun FlashAndCameraTogglePill(
  flashMode: FlashMode,
  stringResources: StringResources,
  emitter: (StandardCameraHudEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier.background(
      color = colorResource(R.color.CameraHud_control_background),
      shape = RoundedCornerShape(50)
    )
  ) {
    IconButton(
      onClick = { emitter(StandardCameraHudEvents.ToggleFlash) },
      modifier = Modifier.testTag(TestTags.CAMERA_HUD_FLASH_BUTTON)
    ) {
      FlashToggleButtonIcon(
        flashMode = flashMode,
        stringResources = stringResources
      )
    }

    IconButton(
      onClick = { emitter(StandardCameraHudEvents.SwitchCamera) },
      modifier = Modifier.testTag(TestTags.CAMERA_HUD_SWITCH_BUTTON)
    ) {
      Icon(
        imageVector = SignalIcons.CameraSwitch.imageVector,
        contentDescription = if (stringResources.switchCamera != 0) stringResource(stringResources.switchCamera) else null,
        tint = colorResource(R.color.CameraHud_control_foreground)
      )
    }
  }
}

/**
 * The elapsed time, and below it while paused, the caller's label for a paused recording.
 *
 * @param pausedLabel What to call a paused recording, or null to show the time alone.
 */
@Composable
private fun RecordingDurationDisplay(
  durationMillis: Long,
  isPaused: Boolean,
  pausedLabel: String?,
  modifier: Modifier = Modifier
) {
  val seconds = (durationMillis / 1000) % 60
  val minutes = (durationMillis / 1000) / 60
  val timeText = String.format(Locale.US, "%02d:%02d", minutes, seconds)

  val pausedProgress by animateFloatAsState(
    targetValue = if (isPaused) 1f else 0f,
    animationSpec = tween(PAUSED_TRANSITION_MS),
    label = "RecordingPausedProgress"
  )

  val recordingRed = colorResource(R.color.CameraHud_control_red_background)
  val pausedGray = colorResource(R.color.CameraHud_control_background)

  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = modifier
  ) {
    // The pill is shorter than the close and flash buttons it sits between, so it is centered in a row of their height
    // to put the three of them on one line.
    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier.heightIn(min = TOP_CONTROL_SIZE)
    ) {
      Box(
        modifier = Modifier
          .background(lerp(recordingRed, pausedGray, pausedProgress), shape = CircleShape)
          .padding(horizontal = 16.dp, vertical = 4.dp)
          .testTag(TestTags.CAMERA_HUD_RECORDING_DURATION)
      ) {
        Text(
          text = timeText,
          color = colorResource(R.color.CameraHud_control_foreground),
          fontSize = 18.sp,
          fontWeight = FontWeight.Medium
        )
      }
    }

    if (pausedLabel != null && pausedProgress > 0f) {
      Box(
        modifier = Modifier
          .padding(top = 8.dp)
          .graphicsLayer { alpha = pausedProgress }
          .background(recordingRed, shape = CircleShape)
          .padding(horizontal = 16.dp, vertical = 4.dp)
          .testTag(TestTags.CAMERA_HUD_RECORDING_PAUSED)
      ) {
        Text(
          text = pausedLabel,
          color = colorResource(R.color.CameraHud_control_foreground),
          fontSize = 16.sp,
          fontWeight = FontWeight.Medium
        )
      }
    }
  }
}

@Composable
private fun CameraSwitchButton(
  onClick: () -> Unit,
  stringResources: StringResources,
  modifier: Modifier = Modifier
) {
  val contentDescription = if (stringResources.switchCamera != 0) {
    stringResource(stringResources.switchCamera)
  } else {
    null
  }

  IconButton(
    onClick = onClick,
    modifier = modifier
      .size(52.dp)
      .background(colorResource(R.color.CameraHud_control_background), shape = CircleShape)
      .testTag(TestTags.CAMERA_HUD_SWITCH_BUTTON)
  ) {
    Icon(
      imageVector = SignalIcons.CameraSwitch.imageVector,
      contentDescription = contentDescription,
      tint = colorResource(R.color.CameraHud_control_foreground),
      modifier = Modifier.size(24.dp)
    )
  }
}

@Composable
private fun FlashToggleButton(
  flashMode: FlashMode,
  onToggle: () -> Unit,
  stringResources: StringResources,
  modifier: Modifier = Modifier
) {
  IconButton(
    onClick = onToggle,
    modifier = modifier
      .size(TOP_CONTROL_SIZE)
      .background(colorResource(R.color.CameraHud_control_background), shape = CircleShape)
      .testTag(TestTags.CAMERA_HUD_FLASH_BUTTON)
  ) {
    FlashToggleButtonIcon(
      flashMode = flashMode,
      stringResources = stringResources
    )
  }
}

@Composable
private fun FlashToggleButtonIcon(
  flashMode: FlashMode,
  stringResources: StringResources
) {
  val icon = when (flashMode) {
    FlashMode.Off -> SignalIcons.FlashOff
    FlashMode.On -> SignalIcons.FlashOn
    FlashMode.Auto -> SignalIcons.FlashAuto
  }

  val contentDescriptionRes = when (flashMode) {
    FlashMode.Off -> stringResources.flashOff
    FlashMode.On -> stringResources.flashOn
    FlashMode.Auto -> stringResources.flashAuto
  }

  val contentDescription = if (contentDescriptionRes != 0) {
    stringResource(contentDescriptionRes)
  } else {
    null
  }

  Icon(
    painter = icon.painter,
    contentDescription = contentDescription,
    tint = colorResource(R.color.CameraHud_control_foreground),
    modifier = Modifier.size(24.dp)
  )
}

@AllNightPreviews
@Composable
private fun StandardCameraHudPreview() {
  Box(modifier = Modifier.fillMaxSize()) {
    StandardCameraHudContent(
      state = CameraScreenState(),
      emitter = {}
    )
  }
}

/**
 * The zoom bar above the capture button. The canvas is taller than 16:9 on purpose — that is the one window the bar has
 * no room on, so a 640dp-tall preview would show nothing.
 */
@Preview(name = "Zoom bar", showBackground = true, backgroundColor = 0xFF444444, widthDp = 360, heightDp = 760)
@Composable
private fun StandardCameraHudZoomBarPreview() {
  Box(modifier = Modifier.fillMaxSize()) {
    StandardCameraHudContent(
      state = CameraScreenState(zoomRange = 0.5f..10f),
      emitter = {}
    )
  }
}

@Preview(name = "Recording", showBackground = true, backgroundColor = 0xFF444444, widthDp = 360, heightDp = 640)
@Composable
private fun StandardCameraHudRecordingPreview() {
  Box(modifier = Modifier.fillMaxSize()) {
    StandardCameraHudContent(
      state = CameraScreenState(
        isRecording = true,
        recordingDuration = 18_000L,
        flashMode = FlashMode.On
      ),
      maxRecordingDurationMs = 30_000L,
      emitter = {}
    )
  }
}

/**
 * A recording being held: everything but the capture button and the lock that has taken the gallery's place is faded
 * out, since the finger holding the recording open cannot reach any of it.
 */
@Preview(name = "Recording held", showBackground = true, backgroundColor = 0xFF444444, widthDp = 360, heightDp = 760)
@Composable
private fun StandardCameraHudHeldRecordingPreview() {
  Box(modifier = Modifier.fillMaxSize()) {
    StandardCameraHudContent(
      state = CameraScreenState(
        isRecording = true,
        recordingDuration = 4_000L,
        zoomRange = 0.5f..10f
      ),
      maxRecordingDurationMs = 30_000L,
      emitter = {}
    )
  }
}

@Preview(name = "Recording locked", showBackground = true, backgroundColor = 0xFF444444, widthDp = 360, heightDp = 640)
@Composable
private fun StandardCameraHudLockedRecordingPreview() {
  Box(modifier = Modifier.fillMaxSize()) {
    StandardCameraHudContent(
      state = CameraScreenState(
        isRecording = true,
        isRecordingLocked = true,
        recordingDuration = 18_000L
      ),
      maxRecordingDurationMs = 30_000L,
      captureButtonMode = CaptureButtonMode.VIDEO,
      emitter = {}
    )
  }
}

/** Both pills, which the HUD previews cannot show without a caller to supply the label. */
@Preview(name = "Recording duration", showBackground = true, backgroundColor = 0xFF444444)
@Composable
private fun RecordingDurationDisplayPreview() {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(16.dp),
    modifier = Modifier.padding(16.dp)
  ) {
    RecordingDurationDisplay(durationMillis = 12_000L, isPaused = false, pausedLabel = "Paused")
    RecordingDurationDisplay(durationMillis = 12_000L, isPaused = true, pausedLabel = "Paused")
  }
}

@Preview(name = "Video mode", showBackground = true, backgroundColor = 0xFF444444, widthDp = 360, heightDp = 640)
@Composable
private fun StandardCameraHudVideoModePreview() {
  Box(modifier = Modifier.fillMaxSize()) {
    StandardCameraHudContent(
      state = CameraScreenState(),
      captureButtonMode = CaptureButtonMode.VIDEO,
      emitter = {}
    )
  }
}

@Preview(name = "With Close Button", showBackground = true, backgroundColor = 0xFF444444, widthDp = 360, heightDp = 640)
@Composable
private fun StandardCameraHudWithMediaPreview() {
  Box(modifier = Modifier.fillMaxSize()) {
    StandardCameraHudContent(
      state = CameraScreenState(),
      emitter = {}
    )
  }
}
