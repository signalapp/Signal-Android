/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.camera.hud

import android.content.res.Configuration
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.signal.camera.CameraScreenState
import org.signal.camera.CaptureError
import org.signal.camera.FlashMode
import org.signal.core.ui.compose.SignalIcons
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
  mediaSelectionCount: Int = 0,
  stringResources: StringResources = StringResources(0, 0)
) {
  val context = LocalContext.current

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

  StandardCameraHudContent(
    state = state,
    emitter = emitter,
    modifier = modifier,
    maxRecordingDurationMs = maxRecordingDurationMs,
    mediaSelectionCount = mediaSelectionCount,
    stringResources = stringResources
  )
}

@Composable
private fun BoxScope.StandardCameraHudContent(
  state: CameraScreenState,
  emitter: (StandardCameraHudEvents) -> Unit,
  modifier: Modifier = Modifier,
  maxRecordingDurationMs: Long = DEFAULT_MAX_RECORDING_DURATION_MS,
  mediaSelectionCount: Int = 0,
  stringResources: StringResources = StringResources()
) {
  val configuration = LocalConfiguration.current
  val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

  ShutterOverlay(state.showShutter)

  FlashToggleButton(
    flashMode = state.flashMode,
    onToggle = { emitter(StandardCameraHudEvents.ToggleFlash) },
    stringResources = stringResources,
    modifier = Modifier
      .align(if (isLandscape) Alignment.TopStart else Alignment.TopEnd)
      .padding(16.dp)
  )

  if (state.isRecording) {
    RecordingDurationDisplay(
      durationMillis = state.recordingDuration,
      modifier = Modifier
        .align(Alignment.TopCenter)
        .padding(top = 16.dp)
    )
  }

  CameraControls(
    isLandscape = isLandscape,
    isRecording = state.isRecording,
    recordingProgress = if (maxRecordingDurationMs > 0) {
      (state.recordingDuration.toFloat() / maxRecordingDurationMs).coerceIn(0f, 1f)
    } else {
      0f
    },
    mediaSelectionCount = mediaSelectionCount,
    emitter = emitter,
    stringResources = stringResources,
    modifier = modifier.align(if (isLandscape) Alignment.CenterEnd else Alignment.BottomCenter)
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

/**
 * Camera control buttons layout with center element always truly centered
 * and side elements at fixed distances from edges.
 */
@Composable
private fun CameraControls(
  isLandscape: Boolean,
  isRecording: Boolean,
  recordingProgress: Float,
  mediaSelectionCount: Int,
  emitter: (StandardCameraHudEvents) -> Unit,
  stringResources: StringResources,
  modifier: Modifier = Modifier
) {
  val galleryOrMediaCount: @Composable () -> Unit = {
    if (mediaSelectionCount > 0) {
      MediaCountIndicator(
        count = mediaSelectionCount,
        onClick = { emitter(StandardCameraHudEvents.MediaSelectionClick) },
        stringResources = stringResources
      )
    } else {
      GalleryThumbnailButton(onClick = { emitter(StandardCameraHudEvents.GalleryClick) })
    }
  }

  val captureButton: @Composable () -> Unit = {
    CaptureButton(
      isRecording = isRecording,
      recordingProgress = recordingProgress,
      onTap = { emitter(StandardCameraHudEvents.PhotoCaptureTriggered) },
      onLongPressStart = { emitter(StandardCameraHudEvents.VideoCaptureStarted) },
      onLongPressEnd = { emitter(StandardCameraHudEvents.VideoCaptureStopped) },
      onZoomChange = { emitter(StandardCameraHudEvents.SetZoomLevel(it)) }
    )
  }

  val cameraSwitchButton: @Composable () -> Unit = {
    CameraSwitchButton(
      onClick = { emitter(StandardCameraHudEvents.SwitchCamera) },
      stringResources = stringResources
    )
  }

  if (isLandscape) {
    Box(
      modifier = modifier
        .fillMaxHeight()
        .padding(end = 16.dp, top = 40.dp, bottom = 40.dp)
    ) {
      Box(modifier = Modifier.align(Alignment.TopCenter)) {
        galleryOrMediaCount()
      }
      Box(modifier = Modifier.align(Alignment.Center)) {
        captureButton()
      }
      Box(modifier = Modifier.align(Alignment.BottomCenter)) {
        cameraSwitchButton()
      }
    }
  } else {
    Box(
      modifier = modifier
        .fillMaxWidth()
        .padding(bottom = 16.dp, start = 40.dp, end = 40.dp)
    ) {
      Box(modifier = Modifier.align(Alignment.CenterStart)) {
        cameraSwitchButton()
      }
      Box(modifier = Modifier.align(Alignment.Center)) {
        captureButton()
      }
      Box(modifier = Modifier.align(Alignment.CenterEnd)) {
        galleryOrMediaCount()
      }
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
      .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)
      .padding(horizontal = 16.dp, vertical = 8.dp)
  ) {
    Text(
      text = timeText,
      color = Color.White,
      fontSize = 20.sp,
      fontWeight = FontWeight.Bold
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
    LocalContext.current.getString(stringResources.switchCamera)
  } else {
    null
  }

  IconButton(
    onClick = onClick,
    modifier = modifier
      .size(52.dp)
      .border(2.dp, Color.White, CircleShape)
      .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f), shape = CircleShape)
  ) {
    Icon(
      painter = SignalIcons.CameraSwitch.painter,
      contentDescription = contentDescription,
      tint = Color.White,
      modifier = Modifier.size(28.dp)
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
  val context = LocalContext.current

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
    context.getString(contentDescriptionRes)
  } else {
    null
  }

  IconButton(
    onClick = onToggle,
    modifier = modifier
      .size(48.dp)
      .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)
  ) {
    Icon(
      painter = icon.painter,
      contentDescription = contentDescription,
      tint = Color.White,
      modifier = Modifier.size(24.dp)
    )
  }
}

/** Signal ultramarine blue color for the count badge */
private val UltramarineBlue = Color(0xFF2C6BED)

/**
 * Media count indicator that shows the number of selected media items.
 * Displays a pill-shaped button with the count in a blue badge and a chevron icon.
 */
@Composable
private fun MediaCountIndicator(
  count: Int,
  onClick: () -> Unit,
  stringResources: StringResources,
  modifier: Modifier = Modifier
) {
  val contentDescription = if (stringResources.send != 0) {
    LocalContext.current.getString(stringResources.send)
  } else {
    null
  }

  Row(
    modifier = modifier
      .height(44.dp)
      .background(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(32.dp)
      )
      .clip(RoundedCornerShape(32.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 12.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    val size = with (LocalDensity.current) {
      22.sp.toDp()
    }
    Box(
      modifier = Modifier
        .background(
          color = UltramarineBlue,
          shape = CircleShape
        )
        .size(size),
      contentAlignment = Alignment.Center
    ) {
      Text(
        text = if (count > 99) "99+" else count.toString(),
        color = Color.White,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium
      )
    }

    Icon(
      painter = SignalIcons.ChevronRight.painter,
      contentDescription = contentDescription,
      tint = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier
        .padding(start = 3.dp)
        .size(24.dp)
    )
  }
}

@Preview(name = "Default", showBackground = true, backgroundColor = 0xFF444444, widthDp = 360, heightDp = 640)
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

@Preview(name = "With Media Selected", showBackground = true, backgroundColor = 0xFF444444, widthDp = 360, heightDp = 640)
@Composable
private fun StandardCameraHudWithMediaPreview() {
  Box(modifier = Modifier.fillMaxSize()) {
    StandardCameraHudContent(
      state = CameraScreenState(),
      mediaSelectionCount = 1,
      emitter = {}
    )
  }
}

@Preview(
  name = "Landscape",
  showBackground = true,
  backgroundColor = 0xFF444444,
  widthDp = 640,
  heightDp = 360,
  device = "spec:width=640dp,height=360dp,orientation=landscape"
)
@Composable
private fun StandardCameraHudLandscapePreview() {
  Box(modifier = Modifier.fillMaxSize()) {
    StandardCameraHudContent(
      state = CameraScreenState(),
      emitter = {}
    )
  }
}
