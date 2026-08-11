/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.viewfinder.core.ImplementationMode
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
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
import org.signal.camera.rememberDeviceRotation
import org.signal.core.ui.BottomSheetUtil
import org.signal.core.ui.WindowBreakpoint
import org.signal.core.ui.compose.AllNightPreviews
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.permissions.PermissionDeniedBottomSheet
import org.signal.core.ui.permissions.Permissions
import org.signal.core.ui.rememberWindowBreakpoint
import org.signal.core.util.EncryptedProxyFileDescriptor
import org.signal.core.util.SeekableFileDescriptor
import org.signal.core.util.closeQuietly
import org.signal.core.util.logging.Log
import org.signal.mediasend.MediaConstraints
import org.signal.mediasend.MediaSendDependencies
import org.signal.mediasend.R
import org.signal.mediasend.util.VideoUtil
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
      videoRecordingConfig = rememberVideoRecordingConfig(
        mediaConstraints = controller?.mediaConstraints,
        maxDurationSecondsOverride = controller?.maxVideoDuration ?: 0
      ),
      onCheckPermissions = { checkPermissions(state.isVideoEnabled) },
      hasCameraPermission = { hasCameraPermission() },
      onRequestMicPermission = { requestMicPermission() }
    )
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

/**
 * How a recording is backed on this device, together with the duration caps that follow from it. They travel
 * together because they have to agree: a disk-backed recording may run far longer than a RAM-backed one, so
 * recording with a shorter-lived descriptor than the cap advertises would truncate the video.
 *
 * @param memoryBackedMaxDurationSeconds The cap that applies whenever the RAM-backed descriptor ends up being
 *   used, which includes the case where creating the disk-backed one fails at record time.
 */
data class VideoRecordingConfig(
  val useEncryptedDisk: Boolean = false,
  val maxDurationSeconds: Int = 0,
  val memoryBackedMaxDurationSeconds: Int = maxDurationSeconds
)

/**
 * Resolves the recording configuration for this device. Deciding whether the encrypted disk-backed
 * descriptor works runs a filesystem self-test, so it happens off the main thread; the conservative
 * RAM-backed configuration applies until the answer arrives.
 *
 * @param maxDurationSecondsOverride A positive value replaces the derived cap, leaving the backing choice intact.
 */
@Composable
internal fun rememberVideoRecordingConfig(mediaConstraints: MediaConstraints?, maxDurationSecondsOverride: Int = 0): VideoRecordingConfig {
  if (mediaConstraints == null) {
    return VideoRecordingConfig(maxDurationSeconds = maxDurationSecondsOverride)
  }

  val context = LocalContext.current

  // Keyed on the derived duration rather than the MediaConstraints instance, because implementations hand
  // back a fresh object on every call and would otherwise restart resolution on every recomposition.
  val memoryBackedDurationSeconds = VideoUtil.getMemoryBackedMaxRecordDurationSeconds(mediaConstraints)
  val memoryBackedCap = maxDurationSecondsOverride.takeIf { it > 0 } ?: memoryBackedDurationSeconds
  val memoryBacked = VideoRecordingConfig(
    useEncryptedDisk = false,
    maxDurationSeconds = memoryBackedCap
  )

  return produceState(memoryBacked, memoryBackedDurationSeconds, maxDurationSecondsOverride) {
    if (Build.VERSION.SDK_INT < 26) {
      return@produceState
    }

    value = withContext(Dispatchers.IO) {
      if (EncryptedProxyFileDescriptor.isSupported(context)) {
        VideoRecordingConfig(
          useEncryptedDisk = true,
          maxDurationSeconds = maxDurationSecondsOverride.takeIf { it > 0 } ?: VideoUtil.getDiskBackedMaxRecordDurationSeconds(),
          memoryBackedMaxDurationSeconds = memoryBackedCap
        )
      } else {
        memoryBacked
      }
    }
  }.value
}

/**
 * Bridges [CameraXScreenEvents]s emitted by [CameraXScreen] back onto the legacy [CameraFragment.Controller] callbacks
 * for Fragment-based consumers.
 */
private fun CameraFragment.Controller.onCameraXScreenEvent(event: CameraXScreenEvents) {
  when (event) {
    is CameraXScreenEvents.ImageCaptured -> onImageCaptured(event.data, event.width, event.height)
    is CameraXScreenEvents.VideoCaptured -> onVideoCaptured(event.fd, event.durationMs)
    is CameraXScreenEvents.QrCodeFound -> onQrCodeFound(event.data)
    CameraXScreenEvents.VideoCaptureError -> onVideoCaptureError()
    CameraXScreenEvents.GalleryClicked -> onGalleryClicked()
    CameraXScreenEvents.CameraCloseClicked -> onCameraCloseClicked()
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

/** A descriptor to record into, paired with the duration cap that the descriptor actually supports. */
class ActiveRecording(val parcelFd: ParcelFileDescriptor, val maxDurationSeconds: Int)

@Stable
class VideoFileDescriptor(val context: Context) {

  private var videoFileDescriptor: SeekableFileDescriptor? = null
  private var usingEncryptedDisk: Boolean = false

  /**
   * Creates the descriptor to record into, reporting the cap that goes with whichever descriptor was actually
   * created. A disk-backed descriptor that fails to be created falls back to the RAM-backed one and its shorter
   * cap, rather than blocking recording entirely.
   */
  fun create(config: VideoRecordingConfig): ActiveRecording? {
    if (Build.VERSION.SDK_INT < 26) {
      throw IllegalStateException("Video capture requires API 26 or higher")
    }

    destroy()

    if (config.useEncryptedDisk) {
      val encrypted = CameraXUtil.createEncryptedDiskVideoFileDescriptor(context)
      if (encrypted != null) {
        videoFileDescriptor = encrypted
        usingEncryptedDisk = true
        return ActiveRecording(encrypted.parcelFd, config.maxDurationSeconds)
      }
      Log.w(TAG, "Failed to create encrypted disk file descriptor, falling back to memory")
    }

    return try {
      val memory = CameraXUtil.createMemoryVideoFileDescriptor(context)
      videoFileDescriptor = memory
      ActiveRecording(memory.parcelFd, config.memoryBackedMaxDurationSeconds)
    } catch (e: IOException) {
      Log.w(TAG, "Failed to create video file descriptor", e)
      null
    }
  }

  /**
   * The recording never produced a file. A disk-backed descriptor that fails this late fails the same way on
   * every retry, so retire it for the rest of the process and let the next recording take the RAM-backed one
   * and its shorter cap.
   */
  fun onRecordingFailed() {
    if (usingEncryptedDisk && Build.VERSION.SDK_INT >= 26) {
      Log.w(TAG, "Recording failed on an encrypted disk descriptor. Falling back to memory from here on.")
      EncryptedProxyFileDescriptor.markUnsupported()
    }

    destroy()
  }

  /**
   * Hands the recorded descriptor to the consumer, which becomes responsible for closing it. The copy on the
   * consuming side runs asynchronously and can outlive this screen, so ownership has to travel with it.
   */
  fun releaseForReading(): SeekableFileDescriptor? {
    val descriptor = videoFileDescriptor ?: return null
    videoFileDescriptor = null
    usingEncryptedDisk = false

    return try {
      Os.lseek(descriptor.fileDescriptor, 0, OsConstants.SEEK_SET)
      descriptor
    } catch (e: ErrnoException) {
      Log.w(TAG, "Failed to seek video file descriptor", e)
      descriptor.closeQuietly()
      null
    }
  }

  fun destroy() {
    videoFileDescriptor?.closeQuietly()
    videoFileDescriptor = null
    usingEncryptedDisk = false
  }
}

@Composable
fun CameraXScreen(
  state: CameraXScreenState,
  onEvent: (CameraXScreenEvents) -> Unit,
  videoRecordingConfig: VideoRecordingConfig,
  onCheckPermissions: () -> Unit,
  hasCameraPermission: () -> Boolean,
  onRequestMicPermission: () -> Unit,
  storiesEnabled: Boolean = CameraDependencies.isStoriesFeatureEnabled(),
  implementationMode: ImplementationMode = ImplementationMode.EXTERNAL
) {
  val context = LocalContext.current

  val captureMode = if (LocalInspectionMode.current) {
    CameraCaptureMode.ImageAndVideoSimultaneous
  } else {
    remember { resolveCaptureMode(context, state.isVideoEnabled) }
  }

  val videoFileDescriptor = remember { VideoFileDescriptor(context) }

  val cameraViewModel: CameraScreenViewModel = viewModel()
  val cameraState by cameraViewModel.state

  // Single rotation source; the view model decides card/preview/icon behavior from it.
  val breakpoint = rememberWindowBreakpoint()
  val isSmallScreen = breakpoint is WindowBreakpoint.Small
  val deviceRotation = rememberDeviceRotation()
  LaunchedEffect(deviceRotation, isSmallScreen) {
    cameraViewModel.onEvent(CameraScreenEvents.SetDeviceRotation(deviceRotation, isSmallScreen))
  }

  val cameraDisplay = CameraDisplay.rememberCameraDisplay(cameraState.isLandscape)
  var hasPermission by remember { mutableStateOf(hasCameraPermission()) }
  var activeRecordingMaxDurationMs by remember { mutableLongStateOf(0L) }

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
        onEvent(CameraXScreenEvents.QrCodeFound(qrCode))
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

  val resources = LocalResources.current

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

  val pad = if (!isSmallScreen) {
    Modifier.systemBarsPadding()
  } else {
    Modifier
  }

  BoxWithConstraints(
    modifier = Modifier
      .fillMaxSize()
      .then(pad)
  ) {
    // We have to do a bunch of match to figure out how to place the camera buttons because
    // the logic relies on positining things from the edge of the screen, which doesn't jive
    // with how the composables are arranged. When this screen is re-written, we should simplify
    // this whole setup. For now, I'm just doing my best to match current behavior.
    val cameraAspectRatio = if (cameraDisplay.isLandscape()) 16f / 9f else 9f / 16f
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

    val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    val isPortraitPhone = breakpoint is WindowBreakpoint.Small && isPortrait

    val controls: @Composable () -> Unit = {
      AnimatedVisibility(
        visible = state.controlsVisible,
        enter = fadeIn(animationSpec = tween(durationMillis = 150)),
        exit = fadeOut(animationSpec = tween(durationMillis = 150))
      ) {
        Box(modifier = Modifier.fillMaxSize()) {
          StandardCameraHud(
            state = cameraState,
            modifier = Modifier.padding(bottom = if (isPortraitPhone) hudBottomPaddingInsideViewport else 0.dp),
            maxRecordingDurationMs = activeRecordingMaxDurationMs,
            hasAudioPermission = { context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED },
            emitter = { event ->
              handleHudEvent(
                event = event,
                context = context,
                cameraViewModel = cameraViewModel,
                onEvent = onEvent,
                isVideoEnabled = captureMode != CameraCaptureMode.ImageOnly,
                onRequestMicPermission = onRequestMicPermission,
                createVideoFileDescriptor = {
                  videoFileDescriptor.create(videoRecordingConfig)?.also {
                    activeRecordingMaxDurationMs = it.maxDurationSeconds * 1000L
                  }
                },
                releaseVideoFileDescriptor = { videoFileDescriptor.releaseForReading() },
                onVideoCaptureFailed = { videoFileDescriptor.onRecordingFailed() }
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

    if (hasPermission) {
      CameraScreen(
        state = cameraState,
        emitter = { event -> cameraViewModel.onEvent(event) },
        roundCorners = cameraDisplay.roundViewFinderCorners,
        contentAlignment = cameraAlignment,
        captureMode = captureMode,
        enableQrScanning = state.isQrScanEnabled,
        landscape = cameraState.isLandscape,
        implementationMode = implementationMode,
        modifier = Modifier.padding(bottom = viewportBottomMargin)
      ) {
        if (isPortraitPhone) {
          controls()
        }
      }

      if (!isPortraitPhone) {
        controls()
      }
    } else {
      PermissionMissingContent(
        isVideoEnabled = captureMode != CameraCaptureMode.ImageOnly,
        onRequestPermissions = onCheckPermissions,
        onGalleryClicked = { onEvent(CameraXScreenEvents.GalleryClicked) },
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
        .padding(bottom = max(galleryButtonBottomPadding, 16.dp), end = 40.dp)
    ) {
      GalleryThumbnailButton(onClick = onGalleryClicked)
    }
  }
}

private fun handleHudEvent(
  event: StandardCameraHudEvents,
  context: Context,
  cameraViewModel: CameraScreenViewModel,
  onEvent: (CameraXScreenEvents) -> Unit,
  isVideoEnabled: Boolean,
  onRequestMicPermission: () -> Unit,
  createVideoFileDescriptor: () -> ActiveRecording?,
  releaseVideoFileDescriptor: () -> SeekableFileDescriptor?,
  onVideoCaptureFailed: () -> Unit
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
      val recording = if (Build.VERSION.SDK_INT >= 26 && isVideoEnabled) createVideoFileDescriptor() else null

      if (recording != null) {
        cameraViewModel.startRecording(
          context = context,
          output = VideoOutput.FileDescriptorOutput(recording.parcelFd),
          onVideoCaptured = { result ->
            handleVideoCaptured(result, releaseVideoFileDescriptor, onVideoCaptureFailed, onEvent)
          }
        )
      } else {
        Toast.makeText(context, R.string.CameraFragment__video_recording_is_not_supported_on_your_device, Toast.LENGTH_SHORT)
          .show()
      }
    }

    is StandardCameraHudEvents.VideoCaptureStopped -> {
      cameraViewModel.stopRecording()
    }

    is StandardCameraHudEvents.GalleryClick -> {
      onEvent(CameraXScreenEvents.GalleryClicked)
    }

    is StandardCameraHudEvents.CloseClick -> {
      onEvent(CameraXScreenEvents.CameraCloseClicked)
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

private fun handlePhotoCaptured(bitmap: Bitmap, onEvent: (CameraXScreenEvents) -> Unit) {
  // Convert bitmap to JPEG byte array
  val outputStream = ByteArrayOutputStream()
  bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
  val data = outputStream.toByteArray()

  onEvent(CameraXScreenEvents.ImageCaptured(data, bitmap.width, bitmap.height))
}

private fun handleVideoCaptured(
  result: VideoCaptureResult,
  releaseVideoFileDescriptor: () -> SeekableFileDescriptor?,
  onVideoCaptureFailed: () -> Unit,
  onEvent: (CameraXScreenEvents) -> Unit
) {
  when (result) {
    is VideoCaptureResult.Success -> {
      val descriptor = releaseVideoFileDescriptor()
      if (descriptor != null) {
        onEvent(CameraXScreenEvents.VideoCaptured(descriptor, result.durationMs))
      } else {
        onEvent(CameraXScreenEvents.VideoCaptureError)
      }
    }

    is VideoCaptureResult.Error -> {
      Log.w(TAG, "Video capture failed: ${result.message}", result.throwable)
      onVideoCaptureFailed()
      onEvent(CameraXScreenEvents.VideoCaptureError)
    }
  }
}

@AllNightPreviews
@Composable
private fun CameraXScreenPreview() {
  Previews.Preview {
    CameraXScreen(
      state = CameraXScreenState(),
      onEvent = {},
      videoRecordingConfig = VideoRecordingConfig(),
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
      videoRecordingConfig = VideoRecordingConfig(),
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
      videoRecordingConfig = VideoRecordingConfig(),
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
      videoRecordingConfig = VideoRecordingConfig(),
      onCheckPermissions = {},
      hasCameraPermission = { true },
      onRequestMicPermission = { },
      storiesEnabled = true
    )
  }
}
