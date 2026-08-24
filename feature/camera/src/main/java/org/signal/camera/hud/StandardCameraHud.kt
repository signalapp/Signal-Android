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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.signal.camera.CameraScreenState
import org.signal.camera.CaptureError
import org.signal.camera.FlashMode
import org.signal.camera.R
import org.signal.core.ui.WindowBreakpoint
import org.signal.core.ui.compose.AllNightPreviews
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.rememberWindowBreakpoint
import java.util.Locale

/** Default maximum recording duration: 60 seconds */
const val DEFAULT_MAX_RECORDING_DURATION_MS = 60_000L

data class StringResources(
  @param:StringRes val photoCaptureFailed: Int = 0,
  @param:StringRes val photoProcessingFailed: Int = 0,
  @param:StringRes val switchCamera: Int = 0,
  @param:StringRes val flashOff: Int = 0,
  @param:StringRes val flashOn: Int = 0,
  @param:StringRes val flashAuto: Int = 0,
  @param:StringRes val send: Int = 0
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
 * @param maxRecordingDurationMs Maximum video recording duration in milliseconds (for progress indicator)
 * @param mediaSelectionCount Number of media items currently selected (shows count indicator when > 0)
 * @param emitter Callback for HUD events (photo captured, video captured, gallery click)
 */
@Composable
fun BoxScope.StandardCameraHud(
  state: CameraScreenState,
  emitter: (StandardCameraHudEvents) -> Unit,
  modifier: Modifier = Modifier,
  maxRecordingDurationMs: Long = DEFAULT_MAX_RECORDING_DURATION_MS,
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
                emitter(StandardCameraHudEvents.VideoCaptureStarted)
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
  hasAudioPermission: () -> Boolean = { true },
  stringResources: StringResources = StringResources()
) {
  val breakpoint = rememberWindowBreakpoint()
  val orientation = LocalConfiguration.current.orientation
  val isPortraitPhone = breakpoint is WindowBreakpoint.Small && orientation == Configuration.ORIENTATION_PORTRAIT
  // The screen stays portrait on small; rotate the HUD icons to match the device so they stay upright.
  val iconRotation = if (isPortraitPhone) uprightRotationDegrees(state.deviceRotation) else 0f

  ShutterOverlay(state.showShutter)

  IconButton(
    onClick = { emitter(StandardCameraHudEvents.CloseClick) },
    modifier = modifier
      .padding(16.dp)
      .size(48.dp)
      .background(colorResource(R.color.CameraHud_control_background), shape = CircleShape)
  ) {
    Icon(
      imageVector = SignalIcons.X.imageVector,
      contentDescription = null,
      tint = Color.White,
      modifier = Modifier
        .size(24.dp)
        .rotate(iconRotation)
    )
  }

  if (isPortraitPhone) {
    FlashToggleButton(
      flashMode = state.flashMode,
      onToggle = { emitter(StandardCameraHudEvents.ToggleFlash) },
      stringResources = stringResources,
      modifier = Modifier
        .align(Alignment.TopEnd)
        .padding(16.dp)
        .rotate(iconRotation)
    )
  }

  if (state.isRecording) {
    RecordingDurationDisplay(
      durationMillis = state.recordingDuration,
      modifier = Modifier
        .align(Alignment.TopCenter)
        .padding(top = 16.dp)
    )
  }

  CameraControls(
    breakpoint = breakpoint,
    iconRotation = iconRotation,
    flashMode = state.flashMode,
    isRecording = state.isRecording,
    recordingProgress = if (maxRecordingDurationMs > 0) {
      (state.recordingDuration.toFloat() / maxRecordingDurationMs).coerceIn(0f, 1f)
    } else {
      0f
    },
    emitter = emitter,
    hasAudioPermission = hasAudioPermission,
    stringResources = stringResources,
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

/** Degrees to rotate a HUD icon so it stays upright at the given committed [Surface] rotation. */
private fun uprightRotationDegrees(surfaceRotation: Int): Float = when (surfaceRotation) {
  Surface.ROTATION_90 -> 90f
  Surface.ROTATION_180 -> 180f
  Surface.ROTATION_270 -> 270f
  else -> 0f
}

/**
 * Camera control buttons layout with center element always truly centered
 * and side elements at fixed distances from edges.
 */
@Composable
private fun CameraControls(
  breakpoint: WindowBreakpoint,
  iconRotation: Float,
  isRecording: Boolean,
  recordingProgress: Float,
  flashMode: FlashMode,
  emitter: (StandardCameraHudEvents) -> Unit,
  hasAudioPermission: () -> Boolean,
  stringResources: StringResources,
  modifier: Modifier = Modifier
) {
  val orientation = LocalConfiguration.current.orientation

  val currentEmitter by rememberUpdatedState(emitter)
  val currentHasAudioPermission by rememberUpdatedState(hasAudioPermission)

  val gallery: @Composable () -> Unit = remember {
    movableContentOf {
      GalleryThumbnailButton(onClick = { currentEmitter(StandardCameraHudEvents.GalleryClick) })
    }
  }

  // isRecording/recordingProgress are passed as movable-content parameters so they are read fresh on
  // every invocation; capturing them in the remembered lambda would freeze them at first composition.
  val captureButton: @Composable (Boolean, Float) -> Unit = remember {
    movableContentOf { isRecording, recordingProgress ->
      CaptureButton(
        isRecording = isRecording,
        recordingProgress = recordingProgress,
        onTap = { currentEmitter(StandardCameraHudEvents.PhotoCaptureTriggered) },
        onLongPressStart = {
          if (currentHasAudioPermission()) {
            currentEmitter(StandardCameraHudEvents.VideoCaptureStarted)
          } else {
            currentEmitter(StandardCameraHudEvents.AudioPermissionRequired)
          }
        },
        onLongPressEnd = { currentEmitter(StandardCameraHudEvents.VideoCaptureStopped) },
        onZoomChange = { currentEmitter(StandardCameraHudEvents.SetZoomLevel(it)) }
      )
    }
  }

  when {
    breakpoint is WindowBreakpoint.Small && orientation == Configuration.ORIENTATION_PORTRAIT -> {
      HorizontalControlBar(
        gallerySlot = gallery,
        captureSlot = captureButton,
        isRecording = isRecording,
        recordingProgress = recordingProgress,
        iconRotation = iconRotation,
        stringResources = stringResources,
        emitter = emitter,
        modifier = modifier
      )
    }

    else -> {
      VerticalControlBar(
        flashMode = flashMode,
        gallerySlot = gallery,
        captureSlot = captureButton,
        isRecording = isRecording,
        recordingProgress = recordingProgress,
        stringResources = stringResources,
        emitter = emitter,
        modifier = modifier
      )
    }
  }
}

@Composable
private fun HorizontalControlBar(
  gallerySlot: @Composable () -> Unit,
  captureSlot: @Composable (Boolean, Float) -> Unit,
  isRecording: Boolean,
  recordingProgress: Float,
  iconRotation: Float,
  stringResources: StringResources,
  emitter: (StandardCameraHudEvents) -> Unit,
  modifier: Modifier
) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .padding(bottom = 40.dp, start = 40.dp, end = 40.dp)
  ) {
    Box(modifier = Modifier.align(Alignment.CenterEnd).rotate(iconRotation)) {
      CameraSwitchButton(
        onClick = { emitter(StandardCameraHudEvents.SwitchCamera) },
        stringResources = stringResources
      )
    }
    Box(modifier = Modifier.align(Alignment.Center).rotate(iconRotation)) {
      captureSlot(isRecording, recordingProgress)
    }
    Box(modifier = Modifier.align(Alignment.CenterStart).rotate(iconRotation)) {
      gallerySlot()
    }
  }
}

@Composable
private fun VerticalControlBar(
  flashMode: FlashMode,
  gallerySlot: @Composable () -> Unit,
  captureSlot: @Composable (Boolean, Float) -> Unit,
  isRecording: Boolean,
  recordingProgress: Float,
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
      FlashAndCameraTogglePill(
        flashMode = flashMode,
        emitter = emitter,
        stringResources = stringResources
      )
    }

    captureSlot(isRecording, recordingProgress)

    Box(
      contentAlignment = Alignment.TopCenter,
      modifier = Modifier
        .weight(1f)
        .padding(top = 40.dp)
    ) {
      gallerySlot()
    }
  }
}

@Composable
private fun FlashAndCameraTogglePill(
  flashMode: FlashMode,
  stringResources: StringResources,
  emitter: (StandardCameraHudEvents) -> Unit
) {
  Column(
    modifier = Modifier.background(
      color = colorResource(R.color.CameraHud_control_background),
      shape = RoundedCornerShape(50)
    )
  ) {
    IconButton(
      onClick = { emitter(StandardCameraHudEvents.ToggleFlash) }
    ) {
      FlashToggleButtonIcon(
        flashMode = flashMode,
        stringResources = stringResources
      )
    }

    IconButton(
      onClick = { emitter(StandardCameraHudEvents.SwitchCamera) }
    ) {
      Icon(
        imageVector = SignalIcons.CameraSwitch.imageVector,
        contentDescription = if (stringResources.switchCamera != 0) stringResource(stringResources.switchCamera) else null,
        tint = Color.White
      )
    }
  }
}

@Composable
private fun RecordingDurationDisplay(
  durationMillis: Long,
  modifier: Modifier = Modifier
) {
  val seconds = (durationMillis / 1000) % 60
  val minutes = (durationMillis / 1000) / 60
  val timeText = String.format(Locale.US, "%02d:%02d", minutes, seconds)

  Box(
    modifier = modifier
      .background(colorResource(R.color.CameraHud_control_red_background), shape = CircleShape)
      .padding(horizontal = 16.dp, vertical = 4.dp)
  ) {
    Text(
      text = timeText,
      color = Color.White,
      fontSize = 18.sp,
      fontWeight = FontWeight.Medium
    )
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
  ) {
    Icon(
      imageVector = SignalIcons.CameraSwitch.imageVector,
      contentDescription = contentDescription,
      tint = Color.White,
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
      .size(48.dp)
      .background(colorResource(R.color.CameraHud_control_background), shape = CircleShape)
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
    tint = Color.White,
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
