package org.thoughtcrime.securesms.mediasend

import android.Manifest
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.widget.Toast
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.signal.camera.CameraScreen
import org.signal.camera.CameraScreenEvents
import org.signal.camera.CameraScreenViewModel
import org.signal.camera.VideoCaptureResult
import org.signal.camera.VideoOutput
import org.signal.camera.hud.StringResources
import org.signal.camera.hud.StandardCameraHud
import org.signal.camera.hud.StandardCameraHudEvents
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.mediasend.camerax.CameraXModePolicy
import org.thoughtcrime.securesms.permissions.PermissionDeniedBottomSheet.Companion.showPermissionFragment
import org.thoughtcrime.securesms.permissions.Permissions
import org.thoughtcrime.securesms.stories.Stories
import org.thoughtcrime.securesms.util.BottomSheetUtil
import org.thoughtcrime.securesms.util.MemoryFileDescriptor
import org.thoughtcrime.securesms.video.VideoUtil
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
  }

  private var controller: CameraFragment.Controller? = null
  private var videoFileDescriptor: MemoryFileDescriptor? = null
  private var cameraXModePolicy: CameraXModePolicy? = null

  private val isVideoEnabled: Boolean
    get() = requireArguments().getBoolean(IS_VIDEO_ENABLED, true)

  private val isQrScanEnabled: Boolean
    get() = requireArguments().getBoolean(IS_QR_SCAN_ENABLED, false)

  // Compose state holders for HUD visibility
  private var controlsVisible = mutableStateOf(true)
  private var selectedMediaCount = mutableIntStateOf(0)

  override fun onAttach(context: Context) {
    super.onAttach(context)
    controller = when {
      activity is CameraFragment.Controller -> activity as CameraFragment.Controller
      parentFragment is CameraFragment.Controller -> parentFragment as CameraFragment.Controller
      else -> throw IllegalStateException("Parent must implement Controller interface.")
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    cameraXModePolicy = CameraXModePolicy.acquire(
      requireContext(),
      controller!!.mediaConstraints,
      isVideoEnabled,
      isQrScanEnabled
    )

    Log.d(TAG, "Starting CameraX with mode policy ${cameraXModePolicy?.javaClass?.simpleName}")
  }

  @Composable
  override fun FragmentContent() {
    CameraXScreen(
      controller = controller,
      isVideoEnabled = isVideoEnabled && Build.VERSION.SDK_INT >= 26,
      isQrScanEnabled = isQrScanEnabled,
      controlsVisible = controlsVisible.value,
      selectedMediaCount = selectedMediaCount.intValue,
      onCheckPermissions = { checkPermissions(isVideoEnabled) },
      hasCameraPermission = { hasCameraPermission() },
      createVideoFileDescriptor = { createVideoFileDescriptor() },
      getMaxVideoDurationInSeconds = { getMaxVideoDurationInSeconds() },
      cameraDisplay = CameraDisplay.getDisplay(requireActivity())
    )
  }

  override fun onResume() {
    super.onResume()
    requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
  }

  override fun onDestroyView() {
    super.onDestroyView()
    closeVideoFileDescriptor()
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
    this.selectedMediaCount.intValue = selectedMediaCount
  }

  override fun fadeOutControls(onEndAction: Runnable) {
    controlsVisible.value = false
    // Post the end action after a short delay to allow animation to complete
    view?.postDelayed({ onEndAction.run() }, CONTROLS_ANIMATION_DURATION)
  }

  override fun fadeInControls() {
    controlsVisible.value = true
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
            showPermissionFragment(
              R.string.CameraXFragment_allow_access_camera_microphone,
              R.string.CameraXFragment_to_capture_photos_videos,
              false
            ).show(parentFragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
          } else if (deniedPermissions.contains(Manifest.permission.CAMERA)) {
            showPermissionFragment(
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

  private fun createVideoFileDescriptor(): ParcelFileDescriptor? {
    if (Build.VERSION.SDK_INT < 26) {
      throw IllegalStateException("Video capture requires API 26 or higher")
    }

    return try {
      closeVideoFileDescriptor()
      videoFileDescriptor = CameraXVideoCaptureHelper.createFileDescriptor(requireContext())
      videoFileDescriptor?.parcelFileDescriptor
    } catch (e: IOException) {
      Log.w(TAG, "Failed to create video file descriptor", e)
      null
    }
  }

  private fun closeVideoFileDescriptor() {
    videoFileDescriptor?.let {
      try {
        it.close()
      } catch (e: IOException) {
        Log.w(TAG, "Failed to close video file descriptor", e)
      }
      videoFileDescriptor = null
    }
  }

  private fun getMaxVideoDurationInSeconds(): Int {
    var maxDuration = VideoUtil.getMaxVideoRecordDurationInSeconds(requireContext(), controller!!.mediaConstraints)
    val controllerMaxDuration = controller?.maxVideoDuration ?: 0
    if (controllerMaxDuration > 0) {
      maxDuration = controllerMaxDuration
    }
    return maxDuration
  }
}

@Composable
private fun CameraXScreen(
  controller: CameraFragment.Controller?,
  isVideoEnabled: Boolean,
  isQrScanEnabled: Boolean,
  controlsVisible: Boolean,
  selectedMediaCount: Int,
  onCheckPermissions: () -> Unit,
  hasCameraPermission: () -> Boolean,
  createVideoFileDescriptor: () -> ParcelFileDescriptor?,
  getMaxVideoDurationInSeconds: () -> Int,
  cameraDisplay: CameraDisplay,
  storiesEnabled: Boolean = Stories.isFeatureEnabled()
) {
  val context = LocalContext.current
  val cameraViewModel: CameraScreenViewModel = viewModel()
  val cameraState by cameraViewModel.state
  var hasPermission by remember { mutableStateOf(hasCameraPermission()) }

  LaunchedEffect(Unit) {
    if (!hasPermission) {
      onCheckPermissions()
    }
  }

  LaunchedEffect(cameraViewModel, isQrScanEnabled) {
    if (isQrScanEnabled) {
      cameraViewModel.qrCodeDetected.collect { qrCode ->
        controller?.onQrCodeFound(qrCode)
      }
    }
  }

  LaunchedEffect(Unit) {
    while (true) {
      kotlinx.coroutines.delay(500)
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
        modifier = Modifier.padding(bottom = viewportBottomMargin)
      ) {
        AnimatedVisibility(
          visible = controlsVisible,
          enter = fadeIn(animationSpec = tween(durationMillis = 150)),
          exit = fadeOut(animationSpec = tween(durationMillis = 150))
        ) {
          Box(modifier = Modifier.fillMaxSize()) {
            StandardCameraHud(
              state = cameraState,
              modifier = Modifier.padding(bottom = hudBottomPaddingInsideViewport),
              maxRecordingDurationMs = getMaxVideoDurationInSeconds() * 1000L,
              mediaSelectionCount = selectedMediaCount,
              emitter = { event ->
                handleHudEvent(
                  event = event,
                  context = context,
                  cameraViewModel = cameraViewModel,
                  controller = controller,
                  isVideoEnabled = isVideoEnabled,
                  createVideoFileDescriptor = createVideoFileDescriptor
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
        isVideoEnabled = isVideoEnabled,
        onRequestPermissions = onCheckPermissions
      )
    }
  }
}

@Composable
private fun PermissionMissingContent(
  isVideoEnabled: Boolean,
  onRequestPermissions: () -> Unit
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
  }
}

private fun handleHudEvent(
  event: StandardCameraHudEvents,
  context: Context,
  cameraViewModel: CameraScreenViewModel,
  controller: CameraFragment.Controller?,
  isVideoEnabled: Boolean,
  createVideoFileDescriptor: () -> ParcelFileDescriptor?
) {
  when (event) {
    is StandardCameraHudEvents.PhotoCaptureTriggered -> {
      cameraViewModel.capturePhoto(
        context = context,
        onPhotoCaptured = { bitmap ->
          handlePhotoCaptured(bitmap, controller)
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
              handleVideoCaptured(result, controller)
            }
          )
        } else {
          CameraFragment.toastVideoRecordingNotAvailable(context)
        }
      } else {
        CameraFragment.toastVideoRecordingNotAvailable(context)
      }
    }

    is StandardCameraHudEvents.VideoCaptureStopped -> {
      cameraViewModel.stopRecording()
    }

    is StandardCameraHudEvents.GalleryClick -> {
      controller?.onGalleryClicked()
    }

    is StandardCameraHudEvents.MediaSelectionClick -> {
      controller?.onCameraCountButtonClicked()
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
  }
}

private fun handlePhotoCaptured(bitmap: Bitmap, controller: CameraFragment.Controller?) {
  // Convert bitmap to JPEG byte array
  val outputStream = ByteArrayOutputStream()
  bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
  val data = outputStream.toByteArray()

  controller?.onImageCaptured(data, bitmap.width, bitmap.height)
}

private fun handleVideoCaptured(result: VideoCaptureResult, controller: CameraFragment.Controller?) {
  when (result) {
    is VideoCaptureResult.Success -> {
      result.fileDescriptor?.let { parcelFd ->
        try {
          // Seek to beginning before reading
          android.system.Os.lseek(parcelFd.fileDescriptor, 0, android.system.OsConstants.SEEK_SET)
          controller?.onVideoCaptured(parcelFd.fileDescriptor)
        } catch (e: Exception) {
          Log.w(TAG, "Failed to seek video file descriptor", e)
          controller?.onVideoCaptureError()
        }
      } ?: controller?.onVideoCaptureError()
    }

    is VideoCaptureResult.Error -> {
      Log.w(TAG, "Video capture failed: ${result.message}", result.throwable)
      controller?.onVideoCaptureError()
    }
  }
}

@androidx.compose.ui.tooling.preview.Preview(
  name = "20:9 Display",
  showBackground = true,
  widthDp = 360,
  heightDp = 800
)
@Composable
private fun CameraXScreenPreview_20_9() {
  org.signal.core.ui.compose.Previews.Preview {
    CameraXScreen(
      controller = null,
      isVideoEnabled = true,
      isQrScanEnabled = false,
      controlsVisible = true,
      selectedMediaCount = 0,
      onCheckPermissions = {},
      hasCameraPermission = { true },
      createVideoFileDescriptor = { null },
      getMaxVideoDurationInSeconds = { 60 },
      cameraDisplay = CameraDisplay.DISPLAY_20_9,
      storiesEnabled = true
    )
  }
}

@androidx.compose.ui.tooling.preview.Preview(
  name = "19:9 Display",
  showBackground = true,
  widthDp = 360,
  heightDp = 760
)
@Composable
private fun CameraXScreenPreview_19_9() {
  org.signal.core.ui.compose.Previews.Preview {
    CameraXScreen(
      controller = null,
      isVideoEnabled = true,
      isQrScanEnabled = false,
      controlsVisible = true,
      selectedMediaCount = 0,
      onCheckPermissions = {},
      hasCameraPermission = { true },
      createVideoFileDescriptor = { null },
      getMaxVideoDurationInSeconds = { 60 },
      cameraDisplay = CameraDisplay.DISPLAY_19_9,
      storiesEnabled = true
    )
  }
}

@androidx.compose.ui.tooling.preview.Preview(
  name = "18:9 Display",
  showBackground = true,
  widthDp = 360,
  heightDp = 720
)
@Composable
private fun CameraXScreenPreview_18_9() {
  org.signal.core.ui.compose.Previews.Preview {
    CameraXScreen(
      controller = null,
      isVideoEnabled = true,
      isQrScanEnabled = false,
      controlsVisible = true,
      selectedMediaCount = 0,
      onCheckPermissions = {},
      hasCameraPermission = { true },
      createVideoFileDescriptor = { null },
      getMaxVideoDurationInSeconds = { 60 },
      cameraDisplay = CameraDisplay.DISPLAY_18_9,
      storiesEnabled = true
    )
  }
}

@androidx.compose.ui.tooling.preview.Preview(
  name = "16:9 Display",
  showBackground = true,
  widthDp = 360,
  heightDp = 640
)
@Composable
private fun CameraXScreenPreview_16_9() {
  org.signal.core.ui.compose.Previews.Preview {
    CameraXScreen(
      controller = null,
      isVideoEnabled = true,
      isQrScanEnabled = false,
      controlsVisible = true,
      selectedMediaCount = 0,
      onCheckPermissions = {},
      hasCameraPermission = { true },
      createVideoFileDescriptor = { null },
      getMaxVideoDurationInSeconds = { 60 },
      cameraDisplay = CameraDisplay.DISPLAY_16_9,
      storiesEnabled = true
    )
  }
}

@androidx.compose.ui.tooling.preview.Preview(
  name = "6:5 Display (Tablet)",
  showBackground = true,
  widthDp = 480,
  heightDp = 576
)
@Composable
private fun CameraXScreenPreview_6_5() {
  org.signal.core.ui.compose.Previews.Preview {
    CameraXScreen(
      controller = null,
      isVideoEnabled = true,
      isQrScanEnabled = false,
      controlsVisible = true,
      selectedMediaCount = 0,
      onCheckPermissions = {},
      hasCameraPermission = { true },
      createVideoFileDescriptor = { null },
      getMaxVideoDurationInSeconds = { 60 },
      cameraDisplay = CameraDisplay.DISPLAY_6_5,
      storiesEnabled = true
    )
  }
}
