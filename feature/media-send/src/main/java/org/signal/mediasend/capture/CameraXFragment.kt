/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.capture

import android.Manifest
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.camera.core.CameraSelector
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.signal.camera.CameraCaptureMode
import org.signal.camera.CameraDependencies
import org.signal.camera.CameraDisplay
import org.signal.camera.CameraScreen
import org.signal.camera.CameraScreenEvents
import org.signal.camera.CameraScreenViewModel
import org.signal.camera.CameraXUtil
import org.signal.camera.VideoCaptureResult
import org.signal.camera.VideoOutput
import org.signal.camera.hud.GalleryThumbnailButton
import org.signal.camera.hud.StandardCameraHud
import org.signal.camera.hud.StandardCameraHudEvents
import org.signal.camera.hud.StringResources
import org.signal.core.ui.BottomSheetUtil
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.permissions.PermissionDeniedBottomSheet
import org.signal.core.ui.permissions.Permissions
import org.signal.core.util.MemoryFileDescriptor
import org.signal.core.util.logging.Log
import org.signal.mediasend.CameraFragment
import org.signal.mediasend.MediaConstraints
import org.signal.mediasend.MediaSendDependencies
import org.signal.mediasend.R
import org.signal.mediasend.VideoUtil
import java.io.ByteArrayOutputStream
import java.io.IOException

private val TAG = Log.tag(CameraXFragment::class.java)

/**
 * Camera capture implemented using a Compose-based CameraScreen with CameraX SDK under the hood.
 * This is the preferred camera implementation when supported.
 */
class CameraXFragment : ComposeFragment(), CameraFragment {
  companion object {
    private const val IS_VIDEO_ENABLED = "is_video_enabled"
    private const val IS_QR_SCAN_ENABLED = "is_qr_scan_enabled"
    private const val CONTROLS_ANIMATION_DURATION = 250L

    @JvmStatic
    fun newInstanceForAvatarCapture(): CameraXFragment {
      return CameraXFragment().apply {
        arguments = Bundle().apply {
          putBoolean(IS_VIDEO_ENABLED, false)
          putBoolean(IS_QR_SCAN_ENABLED, false)
        }
      }
    }

    @JvmStatic
    fun newInstance(qrScanEnabled: Boolean): CameraXFragment {
      return CameraXFragment().apply {
        arguments = Bundle().apply {
          putBoolean(IS_QR_SCAN_ENABLED, qrScanEnabled)
        }
      }
    }

    private fun readStateFromArgs(args: Bundle): CameraXScreenState {
      return CameraXScreenState(
        isVideoEnabled = args.getBoolean(IS_VIDEO_ENABLED, true),
        isQrScanEnabled = args.getBoolean(IS_QR_SCAN_ENABLED, false)
      )
    }
  }

  private var controller: CameraFragment.Controller? = null

  private val state by lazy {
    MutableStateFlow(readStateFromArgs(requireArguments()))
  }

  override fun onAttach(context: Context) {
    super.onAttach(context)
    controller = when {
      activity is CameraFragment.Controller -> activity as CameraFragment.Controller
      parentFragment is CameraFragment.Controller -> parentFragment as CameraFragment.Controller
      else -> controller ?: throw IllegalStateException("Parent must implement Controller interface.")
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Log.d(TAG, "Starting CameraX")
  }

  @Composable
  override fun FragmentContent() {
    val state by state.collectAsStateWithLifecycle()
    val controller = controller
    CameraXScreen(
      state = state,
      onEvent = { event -> controller?.onCameraXScreenEvent(event) },
      maxVideoDurationSeconds = controller?.let { getMaxVideoDurationInSeconds(it.mediaConstraints, it.maxVideoDuration) } ?: 0,
      onCheckPermissions = { checkPermissions(state.isVideoEnabled) },
      hasCameraPermission = { hasCameraPermission() },
      onRequestMicPermission = { requestMicPermission() }
    )
  }

  override fun onResume() {
    super.onResume()
    requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
  }

  override fun onDestroyView() {
    super.onDestroyView()
    requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
  }

  override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<String>,
    grantResults: IntArray
  ) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults)
  }

  override fun presentHud(selectedMediaCount: Int) {
    state.update { it.copy(selectedMediaCount = selectedMediaCount) }
  }

  override fun fadeOutControls(onEndAction: Runnable) {
    state.update { it.copy(controlsVisible = false) }
    // Post the end action after a short delay to allow animation to complete
    view?.postDelayed({ onEndAction.run() }, CONTROLS_ANIMATION_DURATION)
  }

  override fun fadeInControls() {
    state.update { it.copy(controlsVisible = true) }
  }

  private fun checkPermissions(includeAudio: Boolean) {
    if (hasCameraPermission()) {
      return
    }

    if (includeAudio) {
      Permissions.with(this)
        .request(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        .ifNecessary()
        .onSomeGranted { permissions ->
          // Will trigger recomposition via hasCameraPermission check
        }
        .onSomePermanentlyDenied { deniedPermissions ->
          if (deniedPermissions.containsAll(listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))) {
            PermissionDeniedBottomSheet.showPermissionFragment(
              R.string.CameraXFragment_allow_access_camera_microphone,
              R.string.CameraXFragment_to_capture_photos_videos,
              false
            ).show(parentFragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
          } else if (deniedPermissions.contains(Manifest.permission.CAMERA)) {
            PermissionDeniedBottomSheet.showPermissionFragment(
              R.string.CameraXFragment_allow_access_camera,
              R.string.CameraXFragment_to_capture_photos_videos,
              false
            ).show(parentFragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
          }
        }
        .onSomeDenied { deniedPermissions ->
          if (deniedPermissions.contains(Manifest.permission.CAMERA)) {
            Toast.makeText(
              requireContext(),
              R.string.CameraXFragment_signal_needs_camera_access_capture_photos,
              Toast.LENGTH_LONG
            ).show()
          }
        }
        .execute()
    } else {
      Permissions.with(this)
        .request(Manifest.permission.CAMERA)
        .ifNecessary()
        .onAllGranted { /* Will trigger recomposition */ }
        .onAnyDenied {
          Toast.makeText(
            requireContext(),
            R.string.CameraXFragment_signal_needs_camera_access_capture_photos,
            Toast.LENGTH_LONG
          ).show()
        }
        .withPermanentDenialDialog(
          getString(R.string.CameraXFragment_signal_needs_camera_access_capture_photos),
          null,
          R.string.CameraXFragment_allow_access_camera,
          R.string.CameraXFragment_to_capture_photos,
          parentFragmentManager
        )
        .execute()
    }
  }

  private fun hasCameraPermission(): Boolean {
    return Permissions.hasAll(requireContext(), Manifest.permission.CAMERA)
  }

  private fun requestMicPermission() {
    Permissions.with(this)
      .request(Manifest.permission.RECORD_AUDIO)
      .ifNecessary()
      .withRationaleDialog(getString(R.string.CameraXFragment_allow_access_microphone), getString(R.string.CameraXFragment_to_capture_videos_with_sound), org.signal.core.ui.R.drawable.symbol_mic_24)
      .withPermanentDenialDialog(
        getString(R.string.CameraXFragment_signal_needs_the_recording_permissions_to_capture_video),
        null,
        R.string.CameraXFragment_allow_access_microphone,
        R.string.CameraXFragment_to_capture_videos,
        parentFragmentManager
      )
      .onAnyDenied { Toast.makeText(requireContext(), R.string.CameraXFragment_signal_needs_microphone_access_video, Toast.LENGTH_LONG).show() }
      .execute()
  }
}

internal fun getMaxVideoDurationInSeconds(mediaConstraints: MediaConstraints, maxVideoDuration: Int): Int {
  var maxDuration = VideoUtil.getMaxVideoRecordDurationInSeconds(mediaConstraints)
  if (maxVideoDuration > 0) {
    maxDuration = maxVideoDuration
  }
  return maxDuration
}

/**
 * Bridges [CameraXScreenEvent]s emitted by [CameraXScreen] back onto the legacy [CameraFragment.Controller] callbacks
 * for Fragment-based consumers.
 */
private fun CameraFragment.Controller.onCameraXScreenEvent(event: CameraXScreenEvent) {
  when (event) {
    is CameraXScreenEvent.ImageCaptured -> onImageCaptured(event.data, event.width, event.height)
    is CameraXScreenEvent.VideoCaptured -> onVideoCaptured(event.fd)
    is CameraXScreenEvent.QrCodeFound -> onQrCodeFound(event.data)
    CameraXScreenEvent.VideoCaptureError -> onVideoCaptureError()
    CameraXScreenEvent.GalleryClicked -> onGalleryClicked()
    CameraXScreenEvent.CameraCountButtonClicked -> onCameraCountButtonClicked()
  }
}

private fun resolveCaptureMode(context: Context, isVideoEnabled: Boolean): CameraCaptureMode {
  val isVideoSupported = Build.VERSION.SDK_INT >= 26 &&
    isVideoEnabled &&
    MediaConstraints.isVideoTranscodeAvailable()

  val isMixedModeSupported = isVideoSupported &&
    CameraXUtil.isMixedModeSupported(context) &&
    MediaSendDependencies.mediaSendRepository.isMixedModeAvailable()

  return when {
    isMixedModeSupported -> CameraCaptureMode.ImageAndVideoSimultaneous
    isVideoSupported -> CameraCaptureMode.ImageAndVideoExclusive
    else -> CameraCaptureMode.ImageOnly
  }
}

data class CameraXScreenState(
  val isVideoEnabled: Boolean = true,
  val isQrScanEnabled: Boolean = false,
  val controlsVisible: Boolean = true,
  val selectedMediaCount: Int = 0
)

@Stable
class VideoFileDescriptor(val context: Context) {

  private var videoFileDescriptor: MemoryFileDescriptor? = null

  fun create(): ParcelFileDescriptor? {
    if (Build.VERSION.SDK_INT < 26) {
      throw IllegalStateException("Video capture requires API 26 or higher")
    }

    return try {
      destroy()
      videoFileDescriptor = CameraXUtil.createVideoFileDescriptor(context)
      videoFileDescriptor?.parcelFd
    } catch (e: IOException) {
      Log.w(TAG, "Failed to create video file descriptor", e)
      null
    }
  }

  fun destroy() {
    videoFileDescriptor?.let {
      try {
        it.close()
      } catch (e: IOException) {
        Log.w(TAG, "Failed to close video file descriptor", e)
      }
      videoFileDescriptor = null
    }
  }
}

@Composable
fun CameraXScreen(
  state: CameraXScreenState,
  onEvent: (CameraXScreenEvent) -> Unit,
  maxVideoDurationSeconds: Int,
  onCheckPermissions: () -> Unit,
  hasCameraPermission: () -> Boolean,
  onRequestMicPermission: () -> Unit,
  storiesEnabled: Boolean = CameraDependencies.isStoriesFeatureEnabled()
) {
  val context = LocalContext.current
  val activity = LocalActivity.current

  val captureMode = remember { resolveCaptureMode(context, state.isVideoEnabled) }
  val cameraDisplay = remember { CameraDisplay.getDisplay(activity!!) }
  val videoFileDescriptor = remember { VideoFileDescriptor(context) }

  val cameraViewModel: CameraScreenViewModel = viewModel()
  val cameraState by cameraViewModel.state
  var hasPermission by remember { mutableStateOf(hasCameraPermission()) }

  DisposableEffect(Unit) {
    onDispose { videoFileDescriptor.destroy() }
  }

  LaunchedEffect(cameraViewModel) {
    val lensFacing = if (MediaSendDependencies.mediaSendRepository.isCameraFacingFront) {
      CameraSelector.LENS_FACING_FRONT
    } else {
      CameraSelector.LENS_FACING_BACK
    }
    cameraViewModel.setLensFacing(lensFacing)
  }

  LaunchedEffect(cameraViewModel) {
    snapshotFlow { cameraState.lensFacing }
      .collect { lensFacing ->
        MediaSendDependencies.mediaSendRepository.isCameraFacingFront = lensFacing == CameraSelector.LENS_FACING_FRONT
      }
  }

  LaunchedEffect(Unit) {
    if (!hasPermission) {
      onCheckPermissions()
    }
  }

  LaunchedEffect(cameraViewModel, state.isQrScanEnabled) {
    if (state.isQrScanEnabled) {
      cameraViewModel.qrCodeDetected.collect { qrCode ->
        onEvent(CameraXScreenEvent.QrCodeFound(qrCode))
      }
    }
  }

  LaunchedEffect(Unit) {
    while (true) {
      delay(500)
      val newHasPermission = hasCameraPermission()
      if (newHasPermission != hasPermission) {
        hasPermission = newHasPermission
      }
    }
  }

  val resources = LocalContext.current.resources

  val hudBottomMargin = with(LocalDensity.current) {
    cameraDisplay.getCameraCaptureMarginBottom(resources, storiesEnabled).toDp()
  }

  val viewportGravity = cameraDisplay.getCameraViewportGravity(storiesEnabled)
  val cameraAlignment = when (viewportGravity) {
    CameraDisplay.CameraViewportGravity.CENTER -> Alignment.Center
    CameraDisplay.CameraViewportGravity.BOTTOM -> Alignment.BottomCenter
  }

  val viewportBottomMargin = if (viewportGravity == CameraDisplay.CameraViewportGravity.BOTTOM) {
    with(LocalDensity.current) { cameraDisplay.getCameraViewportMarginBottom(storiesEnabled).toDp() }
  } else {
    0.dp
  }

  BoxWithConstraints(
    modifier = Modifier.fillMaxSize()
  ) {
    // We have to do a bunch of match to figure out how to place the camera buttons because
    // the logic relies on positining things from the edge of the screen, which doesn't jive
    // with how the composables are arranged. When this screen is re-written, we should simplify
    // this whole setup. For now, I'm just doing my best to match current behavior.
    val cameraAspectRatio = 9f / 16f
    val availableHeight = maxHeight - viewportBottomMargin
    val availableAspectRatio = maxWidth / availableHeight
    val matchHeightFirst = availableAspectRatio > cameraAspectRatio

    val viewportHeight = if (matchHeightFirst) {
      availableHeight
    } else {
      maxWidth / cameraAspectRatio
    }

    val bottomGapFromAlignment = when (viewportGravity) {
      CameraDisplay.CameraViewportGravity.CENTER -> (availableHeight - viewportHeight) / 2
      CameraDisplay.CameraViewportGravity.BOTTOM -> 0.dp
    }

    val totalBottomOffset = viewportBottomMargin + bottomGapFromAlignment
    val hudBottomPaddingInsideViewport = maxOf(0.dp, hudBottomMargin - totalBottomOffset)

    if (hasPermission) {
      CameraScreen(
        state = cameraState,
        emitter = { event -> cameraViewModel.onEvent(event) },
        roundCorners = cameraDisplay.roundViewFinderCorners,
        contentAlignment = cameraAlignment,
        captureMode = captureMode,
        enableQrScanning = state.isQrScanEnabled,
        modifier = Modifier.padding(bottom = viewportBottomMargin)
      ) {
        AnimatedVisibility(
          visible = state.controlsVisible,
          enter = fadeIn(animationSpec = tween(durationMillis = 150)),
          exit = fadeOut(animationSpec = tween(durationMillis = 150))
        ) {
          Box(modifier = Modifier.fillMaxSize()) {
            StandardCameraHud(
              state = cameraState,
              modifier = Modifier.padding(bottom = hudBottomPaddingInsideViewport),
              maxRecordingDurationMs = maxVideoDurationSeconds * 1000L,
              mediaSelectionCount = state.selectedMediaCount,
              hasAudioPermission = { context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED },
              emitter = { event ->
                handleHudEvent(
                  event = event,
                  context = context,
                  cameraViewModel = cameraViewModel,
                  onEvent = onEvent,
                  isVideoEnabled = captureMode != CameraCaptureMode.ImageOnly,
                  onRequestMicPermission = onRequestMicPermission,
                  createVideoFileDescriptor = { videoFileDescriptor.create() }
                )
              },
              stringResources = StringResources(
                photoCaptureFailed = R.string.CameraXFragment_photo_capture_failed,
                photoProcessingFailed = R.string.CameraXFragment_photo_processing_failed
              )
            )
          }
        }
      }
    } else {
      PermissionMissingContent(
        isVideoEnabled = captureMode != CameraCaptureMode.ImageOnly,
        onRequestPermissions = onCheckPermissions,
        onGalleryClicked = { onEvent(CameraXScreenEvent.GalleryClicked) },
        galleryButtonBottomPadding = hudBottomMargin + 16.dp
      )
    }
  }
}

@Composable
private fun PermissionMissingContent(
  isVideoEnabled: Boolean,
  onRequestPermissions: () -> Unit,
  onGalleryClicked: () -> Unit,
  galleryButtonBottomPadding: Dp = 16.dp
) {
  val context = LocalContext.current
  val hasAudioPermission = remember { Permissions.hasAll(context, Manifest.permission.RECORD_AUDIO) }

  val textResId = if (!isVideoEnabled || hasAudioPermission) {
    R.string.CameraXFragment_to_capture_photos_and_video_allow_camera
  } else {
    R.string.CameraXFragment_to_capture_photos_and_video_allow_camera_microphone
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.Black),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier.padding(16.dp)
    ) {
      Text(
        text = stringResource(textResId),
        color = Color.White,
        textAlign = TextAlign.Center
      )
      Spacer(modifier = Modifier.height(16.dp))
      Button(onClick = onRequestPermissions) {
        Text(text = stringResource(R.string.CameraXFragment_allow_access))
      }
    }

    Box(
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(bottom = galleryButtonBottomPadding, end = 40.dp)
    ) {
      GalleryThumbnailButton(onClick = onGalleryClicked)
    }
  }
}

private fun handleHudEvent(
  event: StandardCameraHudEvents,
  context: Context,
  cameraViewModel: CameraScreenViewModel,
  onEvent: (CameraXScreenEvent) -> Unit,
  isVideoEnabled: Boolean,
  onRequestMicPermission: () -> Unit,
  createVideoFileDescriptor: () -> ParcelFileDescriptor?
) {
  when (event) {
    is StandardCameraHudEvents.PhotoCaptureTriggered -> {
      cameraViewModel.capturePhoto(
        context = context,
        onPhotoCaptured = { bitmap ->
          handlePhotoCaptured(bitmap, onEvent)
        }
      )
    }

    is StandardCameraHudEvents.VideoCaptureStarted -> {
      if (Build.VERSION.SDK_INT >= 26 && isVideoEnabled) {
        val fileDescriptor = createVideoFileDescriptor()
        if (fileDescriptor != null) {
          cameraViewModel.startRecording(
            context = context,
            output = VideoOutput.FileDescriptorOutput(fileDescriptor),
            onVideoCaptured = { result ->
              handleVideoCaptured(result, onEvent)
            }
          )
        } else {
          Toast.makeText(context, R.string.CameraFragment__video_recording_is_not_supported_on_your_device, Toast.LENGTH_SHORT)
            .show()
        }
      } else {
        Toast.makeText(context, R.string.CameraFragment__video_recording_is_not_supported_on_your_device, Toast.LENGTH_SHORT)
          .show()
      }
    }

    is StandardCameraHudEvents.VideoCaptureStopped -> {
      cameraViewModel.stopRecording()
    }

    is StandardCameraHudEvents.GalleryClick -> {
      onEvent(CameraXScreenEvent.GalleryClicked)
    }

    is StandardCameraHudEvents.MediaSelectionClick -> {
      onEvent(CameraXScreenEvent.CameraCountButtonClicked)
    }

    is StandardCameraHudEvents.ToggleFlash -> {
      cameraViewModel.onEvent(CameraScreenEvents.NextFlashMode)
    }

    is StandardCameraHudEvents.ClearCaptureError -> {
      cameraViewModel.onEvent(CameraScreenEvents.ClearCaptureError)
    }

    is StandardCameraHudEvents.SwitchCamera -> {
      cameraViewModel.onEvent(CameraScreenEvents.SwitchCamera(context))
    }

    is StandardCameraHudEvents.SetZoomLevel -> {
      cameraViewModel.onEvent(CameraScreenEvents.LinearZoom(event.zoomLevel))
    }

    is StandardCameraHudEvents.AudioPermissionRequired -> {
      onRequestMicPermission()
    }
  }
}

private fun handlePhotoCaptured(bitmap: Bitmap, onEvent: (CameraXScreenEvent) -> Unit) {
  // Convert bitmap to JPEG byte array
  val outputStream = ByteArrayOutputStream()
  bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
  val data = outputStream.toByteArray()

  onEvent(CameraXScreenEvent.ImageCaptured(data, bitmap.width, bitmap.height))
}

private fun handleVideoCaptured(result: VideoCaptureResult, onEvent: (CameraXScreenEvent) -> Unit) {
  when (result) {
    is VideoCaptureResult.Success -> {
      result.fileDescriptor?.let { parcelFd ->
        try {
          // Seek to beginning before reading
          Os.lseek(parcelFd.fileDescriptor, 0, OsConstants.SEEK_SET)
          onEvent(CameraXScreenEvent.VideoCaptured(parcelFd.fileDescriptor))
        } catch (e: Exception) {
          Log.w(TAG, "Failed to seek video file descriptor", e)
          onEvent(CameraXScreenEvent.VideoCaptureError)
        }
      } ?: onEvent(CameraXScreenEvent.VideoCaptureError)
    }

    is VideoCaptureResult.Error -> {
      Log.w(TAG, "Video capture failed: ${result.message}", result.throwable)
      onEvent(CameraXScreenEvent.VideoCaptureError)
    }
  }
}

@Preview(
  name = "20:9 Display",
  showBackground = true,
  widthDp = 360,
  heightDp = 800
)
@Composable
private fun CameraXScreenPreview_20_9() {
  Previews.Preview {
    CameraXScreen(
      state = CameraXScreenState(),
      onEvent = {},
      maxVideoDurationSeconds = 0,
      onCheckPermissions = {},
      hasCameraPermission = { true },
      onRequestMicPermission = { },
      storiesEnabled = true
    )
  }
}

@Preview(
  name = "19:9 Display",
  showBackground = true,
  widthDp = 360,
  heightDp = 760
)
@Composable
private fun CameraXScreenPreview_19_9() {
  Previews.Preview {
    CameraXScreen(
      state = CameraXScreenState(),
      onEvent = {},
      maxVideoDurationSeconds = 0,
      onCheckPermissions = {},
      hasCameraPermission = { true },
      onRequestMicPermission = { },
      storiesEnabled = true
    )
  }
}

@Preview(
  name = "18:9 Display",
  showBackground = true,
  widthDp = 360,
  heightDp = 720
)
@Composable
private fun CameraXScreenPreview_18_9() {
  Previews.Preview {
    CameraXScreen(
      state = CameraXScreenState(),
      onEvent = {},
      maxVideoDurationSeconds = 0,
      onCheckPermissions = {},
      hasCameraPermission = { true },
      onRequestMicPermission = { },
      storiesEnabled = true
    )
  }
}

@Preview(
  name = "16:9 Display",
  showBackground = true,
  widthDp = 360,
  heightDp = 640
)
@Composable
private fun CameraXScreenPreview_16_9() {
  Previews.Preview {
    CameraXScreen(
      state = CameraXScreenState(),
      onEvent = {},
      maxVideoDurationSeconds = 0,
      onCheckPermissions = {},
      hasCameraPermission = { true },
      onRequestMicPermission = { },
      storiesEnabled = true
    )
  }
}

@Preview(
  name = "6:5 Display (Tablet)",
  showBackground = true,
  widthDp = 480,
  heightDp = 576
)
@Composable
private fun CameraXScreenPreview_6_5() {
  Previews.Preview {
    CameraXScreen(
      state = CameraXScreenState(),
      onEvent = {},
      maxVideoDurationSeconds = 0,
      onCheckPermissions = {},
      hasCameraPermission = { true },
      onRequestMicPermission = { },
      storiesEnabled = true
    )
  }
}
