package org.signal.camera

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.OrientationEventListener
import android.view.Surface
import android.view.Window
import android.view.WindowManager
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.UseCase
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.util.logging.Log
import org.signal.core.util.throttleLatest
import java.lang.ref.WeakReference
import java.util.EnumMap
import java.util.concurrent.Executors
import kotlin.time.Duration.Companion.seconds

private const val TAG = "CameraScreenViewModel"

class CameraScreenViewModel : ViewModel() {
  companion object {
    private val imageAnalysisExecutor = Executors.newSingleThreadExecutor()

    /** Debug flag for testing limited binding (i.e. no simultaneous binding to image + video). */
    private const val FORCE_LIMITED_BINDING = false

    /** Number of times to retry camera binding when initial attempt fails due to camera unavailability. */
    private const val CAMERA_BIND_MAX_RETRIES = 3

    /** Initial delay between camera binding retries, in milliseconds. Doubles on each subsequent retry. */
    private const val CAMERA_BIND_RETRY_DELAY_MS = 500L
  }

  private val _state: MutableState<CameraScreenState> = mutableStateOf(CameraScreenState())
  val state: State<CameraScreenState>
    get() = _state

  private var camera: Camera? = null
  private var lifecycleOwner: LifecycleOwner? = null
  private var cameraProvider: ProcessCameraProvider? = null
  private var lastSuccessfulAttempt: BindingAttempt? = null
  private var imageCapture: ImageCapture? = null
  private var videoCapture: VideoCapture<Recorder>? = null
  private var recording: Recording? = null
  private var captureMode: CameraCaptureMode = CameraCaptureMode.ImageOnly
  private var brightnessBeforeFlash: Float = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
  private var brightnessWindow: WeakReference<Window>? = null
  private var orientationListener: OrientationEventListener? = null
  private var recordingStartZoomRatio: Float = 1f

  private val _qrCodeDetected = MutableSharedFlow<String>(extraBufferCapacity = 1)

  /**
   * Flow of detected QR codes. Observers can collect from this flow to receive QR code detections.
   * The flow filters consecutive duplicates and is throttled to avoid rapid-fire detections.
   */
  val qrCodeDetected: Flow<String> = _qrCodeDetected.throttleLatest(2.seconds)

  private val qrCodeReader = MultiFormatReader().apply {
    val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java)
    hints[DecodeHintType.POSSIBLE_FORMATS] = listOf(BarcodeFormat.QR_CODE)
    hints[DecodeHintType.TRY_HARDER] = true
    setHints(hints)
  }

  fun onEvent(event: CameraScreenEvents) {
    logEvent(event)

    val currentState = _state.value
    when (event) {
      is CameraScreenEvents.BindCamera -> {
        handleBindCameraEvent(currentState, event)
      }
      is CameraScreenEvents.TapToFocus -> {
        handleTapToFocusEvent(currentState, event)
      }
      is CameraScreenEvents.PinchZoom -> {
        handlePinchZoomEvent(currentState, event)
      }
      is CameraScreenEvents.LinearZoom -> {
        handleSetLinearZoomEvent(currentState, event.linearZoom)
      }
      is CameraScreenEvents.SwitchCamera -> {
        handleSwitchCameraEvent(currentState)
      }
      is CameraScreenEvents.SetFlashMode -> {
        handleSetFlashModeEvent(currentState, event.flashMode)
      }
      is CameraScreenEvents.NextFlashMode -> {
        handleSetFlashModeEvent(currentState, currentState.flashMode.next())
      }
      is CameraScreenEvents.ClearCaptureError -> {
        handleClearCaptureErrorEvent(currentState)
      }
    }
  }

  private fun logEvent(event: CameraScreenEvents) {
    when (event) {
      is CameraScreenEvents.BindCamera -> Log.d(TAG, "[Event] BindCamera(captureMode=${event.captureMode}, enableQrScanning=${event.enableQrScanning})")
      is CameraScreenEvents.TapToFocus -> Log.d(TAG, "[Event] TapToFocus(view=${event.viewX},${event.viewY}, surface=${event.surfaceX},${event.surfaceY})")
      is CameraScreenEvents.PinchZoom -> Log.d(TAG, "[Event] PinchZoom(factor=${event.zoomFactor})")
      is CameraScreenEvents.LinearZoom -> Log.d(TAG, "[Event] LinearZoom(${event.linearZoom})")
      is CameraScreenEvents.SwitchCamera -> Log.d(TAG, "[Event] SwitchCamera")
      is CameraScreenEvents.SetFlashMode -> Log.d(TAG, "[Event] SetFlashMode(${event.flashMode})")
      is CameraScreenEvents.NextFlashMode -> Log.d(TAG, "[Event] NextFlashMode")
      is CameraScreenEvents.ClearCaptureError -> Log.d(TAG, "[Event] ClearCaptureError")
    }
  }

  /**
   * Capture a photo.
   * If using front camera with flash enabled but no hardware flash available,
   * uses a selfie flash (white screen overlay) to illuminate the subject.
   */
  @androidx.annotation.OptIn(markerClass = [androidx.camera.core.ExperimentalGetImage::class])
  fun capturePhoto(
    context: Context,
    onPhotoCaptured: (Bitmap) -> Unit
  ) {
    val state = _state.value
    val capture = imageCapture ?: run {
      _state.value = state.copy(captureError = CaptureError.PhotoCaptureFailed("Camera not ready"))
      return
    }

    val needsSelfieFlash = state.lensFacing == CameraSelector.LENS_FACING_FRONT &&
      state.flashMode == FlashMode.On

    if (needsSelfieFlash) {
      captureWithSelfieFlash(context, capture, state, onPhotoCaptured)
    } else {
      capturePhotoInternal(context, capture, state, onPhotoCaptured)
    }
  }

  private fun captureWithSelfieFlash(
    context: Context,
    capture: ImageCapture,
    state: CameraScreenState,
    onPhotoCaptured: (Bitmap) -> Unit
  ) {
    // Show selfie flash and maximize screen brightness
    _state.value = state.copy(showSelfieFlash = true)
    setMaxScreenBrightness(context)

    // Wait for screen to brighten, then capture
    viewModelScope.launch {
      delay(150L) // Give screen time to brighten
      capturePhotoInternal(context, capture, _state.value, onPhotoCaptured)
    }
  }

  @androidx.annotation.OptIn(markerClass = [androidx.camera.core.ExperimentalGetImage::class])
  private fun capturePhotoInternal(
    context: Context,
    capture: ImageCapture,
    state: CameraScreenState,
    onPhotoCaptured: (Bitmap) -> Unit
  ) {
    // Vibrate for haptic feedback
    vibrate(context)

    capture.takePicture(
      ContextCompat.getMainExecutor(context),
      object : ImageCapture.OnImageCapturedCallback() {
        override fun onCaptureSuccess(imageProxy: ImageProxy) {
          viewModelScope.launch {
            try {
              // Convert ImageProxy to Bitmap, mirroring for front camera to match preview
              val mirrorImage = state.lensFacing == CameraSelector.LENS_FACING_FRONT
              val bitmap = imageProxy.toBitmapWithTransforms(mirrorHorizontally = mirrorImage)
              // Pass bitmap to callback
              triggerShutter(state)
              onPhotoCaptured(bitmap)
            } catch (e: Exception) {
              Log.e(TAG, "Failed to process image: ${e.message}", e)
              _state.value = state.copy(captureError = CaptureError.PhotoCaptureFailed(e.message))
            } finally {
              imageProxy.close()
              hideSelfieFlash()
            }
          }
        }

        override fun onError(e: ImageCaptureException) {
          Log.e(TAG, "Photo capture failed: ${e.message}", e)
          _state.value = state.copy(captureError = CaptureError.PhotoCaptureFailed(e.message))
          hideSelfieFlash()
        }
      }
    )
  }

  private fun hideSelfieFlash() {
    if (_state.value.showSelfieFlash) {
      _state.value = _state.value.copy(showSelfieFlash = false)
      restoreScreenBrightness()
    }
  }

  /**
   * Start video recording.
   * If flash is enabled, turns on the torch for the duration of the recording.
   */
  @androidx.annotation.OptIn(markerClass = [androidx.camera.core.ExperimentalGetImage::class])
  @android.annotation.SuppressLint("MissingPermission", "RestrictedApi", "NewApi")
  fun startRecording(
    context: Context,
    output: VideoOutput,
    onVideoCaptured: (VideoCaptureResult) -> Unit
  ) {
    val capture = videoCapture ?: rebindForVideoCapture() ?: return

    recordingStartZoomRatio = _state.value.zoomRatio

    val enableTorch = _state.value.flashMode == FlashMode.On &&
      _state.value.lensFacing == CameraSelector.LENS_FACING_BACK

    if (enableTorch) {
      camera?.cameraControl?.enableTorch(true)
    }

    // Prepare recording based on configuration
    val pendingRecording = when (output) {
      is VideoOutput.FileOutput -> {
        val fileOutputOptions = androidx.camera.video.FileOutputOptions.Builder(output.file).build()
        capture.output.prepareRecording(context, fileOutputOptions)
      }
      is VideoOutput.FileDescriptorOutput -> {
        val fileDescriptorOutputOptions = androidx.camera.video.FileDescriptorOutputOptions.Builder(
          output.fileDescriptor
        ).build()
        capture.output.prepareRecording(context, fileDescriptorOutputOptions)
      }
    }

    val hasAudioPermission = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    val configuredRecording = if (hasAudioPermission) {
      pendingRecording.withAudioEnabled()
    } else {
      Log.w(TAG, "RECORD_AUDIO permission not granted, recording without audio")
      pendingRecording
    }

    val activeRecording = configuredRecording
      .start(ContextCompat.getMainExecutor(context)) { recordEvent ->
        when (recordEvent) {
          is VideoRecordEvent.Start -> {
            Log.d(TAG, "Video recording started")
            startRecordingTimer()
            vibrate(context)
          }
          is VideoRecordEvent.Finalize -> {
            if (enableTorch) {
              camera?.cameraControl?.enableTorch(false)
            }

            val result = if (!recordEvent.hasError()) {
              Log.d(TAG, "Video recording succeeded")
              when (output) {
                is VideoOutput.FileOutput -> {
                  VideoCaptureResult.Success(outputFile = output.file)
                }
                is VideoOutput.FileDescriptorOutput -> {
                  VideoCaptureResult.Success(fileDescriptor = output.fileDescriptor)
                }
              }
            } else {
              Log.e(TAG, "Video recording failed: ${recordEvent.error}")
              val fileDescriptor = (output as? VideoOutput.FileDescriptorOutput)?.fileDescriptor
              VideoCaptureResult.Error(
                message = "Video recording failed",
                throwable = recordEvent.cause,
                fileDescriptor = fileDescriptor
              )
            }

            // Call the callback
            onVideoCaptured(result)
            stopRecordingTimer()

            // Clear recording
            recording = null

            if (captureMode == CameraCaptureMode.ImageAndVideoExclusive) {
              rebindToLastSuccessfulAttempt()
            }
          }
        }
      }

    recording = activeRecording
  }

  /**
   * Stop video recording.
   */
  fun stopRecording() {
    camera?.cameraControl?.enableTorch(false)
    recording?.stop()
    recording = null
  }

  /**
   * Rebinds to just the video use case, needed for devices that cannot bind to image + video capture simultaneously.
   * Upon failure, will rebind to [lastSuccessfulAttempt].
   */
  private fun rebindForVideoCapture(): VideoCapture<Recorder>? {
    val lastAttempt = lastSuccessfulAttempt ?: return null
    val cameraProvider = cameraProvider ?: return null
    val lifecycleOwner = lifecycleOwner ?: return null

    val videoCapture = buildVideoCapture()

    val cameraSelector = CameraSelector.Builder()
      .requireLensFacing(_state.value.lensFacing)
      .build()

    return try {
      cameraProvider.unbindAll()
      camera = cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, lastAttempt.preview, lastAttempt.imageCapture, videoCapture)
      this.videoCapture = videoCapture
      Log.d(TAG, "Rebound with video capture for limited device")
      videoCapture
    } catch (e: Exception) {
      Log.e(TAG, "Failed to rebind with video capture on limited device", e)
      rebindToLastSuccessfulAttempt()
      null
    }
  }

  /**
   * On limited devices, restore the last known-good binding after video recording completes.
   */
  private fun rebindToLastSuccessfulAttempt() {
    val attempt = lastSuccessfulAttempt ?: return
    val cameraProvider = cameraProvider ?: return
    val lifecycleOwner = lifecycleOwner ?: return

    val cameraSelector = CameraSelector.Builder()
      .requireLensFacing(_state.value.lensFacing)
      .build()

    try {
      cameraProvider.unbindAll()
      camera = cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, *attempt.toTypedArray())
      videoCapture = attempt.videoCapture
      Log.d(TAG, "Rebound to last successful configuration after video capture")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to rebind to last successful configuration after video capture", e)
    }
  }

  override fun onCleared() {
    super.onCleared()
    stopRecording()
    orientationListener?.disable()
    orientationListener = null
  }

  private fun handleBindCameraEvent(
    state: CameraScreenState,
    event: CameraScreenEvents.BindCamera
  ) {
    if (tryBindCamera(state, event)) {
      return
    }

    // Initial binding failed. On some devices (e.g. Fairphone 6), the camera HAL may not
    // release resources promptly after a previous session ends, causing CameraX to report
    // zero available cameras. Retry with exponential backoff to give the hardware time to recover.
    viewModelScope.launch {
      for (retry in 1..CAMERA_BIND_MAX_RETRIES) {
        Log.d(TAG, "Retrying camera binding (retry $retry of $CAMERA_BIND_MAX_RETRIES) after $CAMERA_BIND_RETRY_DELAY_MS ms")
        delay(CAMERA_BIND_RETRY_DELAY_MS)

        if (tryBindCamera(_state.value, event)) {
          Log.i(TAG, "Camera binding succeeded on retry $retry")
          return@launch
        }
      }

      Log.e(TAG, "All camera binding retries exhausted")
    }
  }

  /**
   * Attempts to bind the camera with progressively fewer optional use cases.
   * Returns true if binding succeeded, false if all attempts failed.
   */
  private fun tryBindCamera(
    state: CameraScreenState,
    event: CameraScreenEvents.BindCamera
  ): Boolean {
    val cameraSelector = CameraSelector.Builder()
      .requireLensFacing(state.lensFacing)
      .build()

    // Build binding attempts with progressively fewer optional use cases.
    // Some devices cannot support all use cases simultaneously, so we fall back
    // by first dropping video capture, then QR scanning.
    val bindingAttempts = buildBindingAttempts(event)

    for ((index, attempt) in bindingAttempts.withIndex()) {
      try {
        event.cameraProvider.unbindAll()

        camera = event.cameraProvider.bindToLifecycle(
          event.lifecycleOwner,
          cameraSelector,
          *attempt.toTypedArray()
        )

        if (index > 0) {
          Log.w(TAG, "Use case binding succeeded on fallback attempt ${index + 1} of ${bindingAttempts.size}")
        }

        lifecycleOwner = event.lifecycleOwner
        cameraProvider = event.cameraProvider
        lastSuccessfulAttempt = attempt
        imageCapture = attempt.imageCapture
        videoCapture = attempt.videoCapture
        captureMode = event.captureMode
      } catch (e: Exception) {
        Log.e(TAG, "Use case binding failed (attempt ${index + 1} of ${bindingAttempts.size})", e)
        continue
      }

      setupOrientationListener(event.context)
      return true
    }

    Log.e(TAG, "All use case binding attempts failed")
    return false
  }

  @android.annotation.SuppressLint("RestrictedApi")
  private fun buildVideoCapture(): VideoCapture<Recorder> {
    val recorder = Recorder.Builder()
      .setAspectRatio(AspectRatio.RATIO_16_9)
      .setQualitySelector(
        androidx.camera.video.QualitySelector.from(
          androidx.camera.video.Quality.HIGHEST,
          androidx.camera.video.FallbackStrategy.higherQualityOrLowerThan(androidx.camera.video.Quality.HD)
        )
      )
      .build()
    return VideoCapture.withOutput(recorder)
  }

  private fun buildBindingAttempts(
    event: CameraScreenEvents.BindCamera
  ): List<BindingAttempt> {
    val resolutionSelector = ResolutionSelector.Builder()
      .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
      .build()

    // Preview with 16:9 aspect ratio - uses Compose Viewfinder
    val preview = Preview.Builder()
      .setResolutionSelector(resolutionSelector)
      .build()
      .also { it.surfaceProvider = event.surfaceProvider }

    // Image capture with 16:9 aspect ratio (optimized for speed)
    val imageCapture = ImageCapture.Builder()
      .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
      .setResolutionSelector(resolutionSelector)
      .build()

    val videoCapture: VideoCapture<Recorder>? = if (event.captureMode == CameraCaptureMode.ImageAndVideoSimultaneous && !FORCE_LIMITED_BINDING) {
      buildVideoCapture()
    } else {
      null
    }

    val qrAnalysis: ImageAnalysis? = if (event.enableQrScanning) {
      ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .build()
        .also {
          it.setAnalyzer(imageAnalysisExecutor) { imageProxy ->
            processImageForQrCode(imageProxy)
          }
        }
    } else {
      null
    }

    return buildList {
      // Attempt 1: All use cased
      add(BindingAttempt(preview = preview, imageCapture = imageCapture, videoCapture = videoCapture, imageAnalysis = qrAnalysis))

      // Attempt 2: Drop video capture
      if (videoCapture != null) {
        add(BindingAttempt(preview = preview, imageCapture = imageCapture, videoCapture = null, imageAnalysis = qrAnalysis))
      }

      // Attempt 3: Drop QR scanning
      if (qrAnalysis != null) {
        add(BindingAttempt(preview = preview, imageCapture = imageCapture, videoCapture = null, imageAnalysis = null))
      }
    }
  }

  private fun setupOrientationListener(context: Context) {
    orientationListener?.disable()

    orientationListener = object : OrientationEventListener(context) {
      override fun onOrientationChanged(orientation: Int) {
        if (orientation == ORIENTATION_UNKNOWN) return

        val targetRotation = when {
          orientation > 315 || orientation < 45 -> Surface.ROTATION_0
          orientation < 135 -> Surface.ROTATION_270
          orientation < 225 -> Surface.ROTATION_180
          else -> Surface.ROTATION_90
        }

        imageCapture?.targetRotation = targetRotation
        videoCapture?.targetRotation = targetRotation
      }
    }.also { it.enable() }
  }

  private fun handleTapToFocusEvent(
    state: CameraScreenState,
    event: CameraScreenEvents.TapToFocus
  ) {
    val currentCamera = camera ?: return

    val factory = SurfaceOrientedMeteringPointFactory(event.surfaceWidth, event.surfaceHeight)
    val point = factory.createPoint(event.surfaceX, event.surfaceY)
    val action = FocusMeteringAction.Builder(point).build()

    currentCamera.cameraControl.startFocusAndMetering(action)

    _state.value = state.copy(
      focusPoint = Offset(event.viewX, event.viewY),
      showFocusIndicator = true
    )

    // Hide indicator after animation
    viewModelScope.launch {
      delay(800L) // Duration for spring animation + fade out
      _state.value = _state.value.copy(showFocusIndicator = false)
    }
  }

  private fun handlePinchZoomEvent(
    state: CameraScreenState,
    event: CameraScreenEvents.PinchZoom
  ) {
    val currentCamera = camera ?: return

    // Get current zoom ratio and calculate new zoom
    val currentZoom = state.zoomRatio
    val newZoom = (currentZoom * event.zoomFactor).coerceIn(
      currentCamera.cameraInfo.zoomState.value?.minZoomRatio ?: 1f,
      currentCamera.cameraInfo.zoomState.value?.maxZoomRatio ?: 1f
    )

    // Apply zoom to camera
    currentCamera.cameraControl.setZoomRatio(newZoom)

    // Update state
    _state.value = state.copy(zoomRatio = newZoom)
  }

  fun setLensFacing(lensFacing: Int) {
    _state.value = _state.value.copy(lensFacing = lensFacing)
  }

  private fun handleSwitchCameraEvent(state: CameraScreenState) {
    if (state.isRecording) {
      return
    }

    // Toggle between front and back camera
    val newLensFacing = if (state.lensFacing == CameraSelector.LENS_FACING_BACK) {
      CameraSelector.LENS_FACING_FRONT
    } else {
      CameraSelector.LENS_FACING_BACK
    }

    _state.value = state.copy(lensFacing = newLensFacing)
  }

  private fun handleSetFlashModeEvent(
    state: CameraScreenState,
    flashMode: FlashMode
  ) {
    _state.value = state.copy(flashMode = flashMode)

    imageCapture?.flashMode = flashMode.cameraxMode
  }

  private fun handleSetLinearZoomEvent(
    state: CameraScreenState,
    linearZoom: Float
  ) {
    val currentCamera = camera ?: return

    // Clamp linear zoom to valid range (-1 to 1)
    val clampedLinearZoom = linearZoom.coerceIn(-1f, 1f)

    // Use the zoom ratio from when recording started as the base, so that the
    // drag gesture is relative to the user's current zoom level rather than jumping.
    // Positive values (0 to 1) zoom in from base toward maxZoomRatio.
    // Negative values (-1 to 0) zoom out from base toward minZoomRatio.
    val baseZoom = recordingStartZoomRatio
    val minZoom = currentCamera.cameraInfo.zoomState.value?.minZoomRatio ?: 1f
    val maxZoom = currentCamera.cameraInfo.zoomState.value?.maxZoomRatio ?: 1f
    val newZoomRatio = if (clampedLinearZoom >= 0f) {
      baseZoom + (maxZoom - baseZoom) * clampedLinearZoom
    } else {
      baseZoom + (baseZoom - minZoom) * clampedLinearZoom
    }

    currentCamera.cameraControl.setZoomRatio(newZoomRatio)

    _state.value = state.copy(zoomRatio = newZoomRatio)
  }

  private fun triggerShutter(state: CameraScreenState) {
    _state.value = state.copy(showShutter = true)

    // Hide flash after animation
    viewModelScope.launch {
      delay(200L)
      _state.value = _state.value.copy(showShutter = false)
    }
  }

  private fun handleClearCaptureErrorEvent(state: CameraScreenState) {
    _state.value = state.copy(captureError = null)
  }

  private fun startRecordingTimer() {
    _state.value = _state.value.copy(isRecording = true, recordingDuration = 0L)

    viewModelScope.launch {
      while (_state.value.isRecording) {
        delay(100L)
        _state.value = _state.value.copy(recordingDuration = _state.value.recordingDuration + 100L)
      }
    }
  }

  private fun stopRecordingTimer() {
    _state.value = _state.value.copy(isRecording = false, recordingDuration = 0L)
  }

  @androidx.annotation.OptIn(markerClass = [androidx.camera.core.ExperimentalGetImage::class])
  private suspend fun ImageProxy.toBitmapWithTransforms(mirrorHorizontally: Boolean = false): Bitmap = withContext(Dispatchers.Default) {
    val imageProxy = this@toBitmapWithTransforms
    val bitmap = imageProxy.toBitmap()

    val needsRotation = imageProxy.imageInfo.rotationDegrees != 0

    if (needsRotation || mirrorHorizontally) {
      val matrix = Matrix()
      if (needsRotation) {
        matrix.postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
      }
      if (mirrorHorizontally) {
        // Mirror horizontally (flip around vertical axis)
        matrix.postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
      }
      Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    } else {
      bitmap
    }
  }

  @androidx.annotation.OptIn(markerClass = [androidx.camera.core.ExperimentalGetImage::class])
  private fun processImageForQrCode(imageProxy: ImageProxy) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
      imageProxy.close()
      return
    }

    try {
      // Get the Y plane (luminance)
      val yPlane = mediaImage.planes[0]
      val yBuffer = yPlane.buffer
      val ySize = yBuffer.remaining()

      val yData = ByteArray(ySize)
      yBuffer.get(yData)

      // Create a planar YUV source with proper stride handling
      val width = mediaImage.width
      val height = mediaImage.height
      val rowStride = yPlane.rowStride
      val pixelStride = yPlane.pixelStride

      // If row stride equals width and pixel stride is 1, we can use the data directly
      val source = if (rowStride == width && pixelStride == 1) {
        PlanarYUVLuminanceSource(
          yData,
          width,
          height,
          0,
          0,
          width,
          height,
          false
        )
      } else {
        // Need to account for stride - copy row by row
        val adjustedData = ByteArray(width * height)
        var outputPos = 0
        for (row in 0 until height) {
          val inputPos = row * rowStride
          for (col in 0 until width) {
            adjustedData[outputPos++] = yData[inputPos + col * pixelStride]
          }
        }
        PlanarYUVLuminanceSource(
          adjustedData,
          width,
          height,
          0,
          0,
          width,
          height,
          false
        )
      }

      val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

      try {
        val result = qrCodeReader.decodeWithState(binaryBitmap)
        qrCodeReader.reset() // Reset state after successful decode
        _qrCodeDetected.tryEmit(result.text)
      } catch (_: NotFoundException) {
        // No QR code found in this frame, which is normal
        qrCodeReader.reset() // Reset state for next attempt
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error processing image for QR code: ${e.message}", e)
    }
    imageProxy.close()
  }

  private fun setMaxScreenBrightness(context: Context) {
    val window = context.findActivity()?.window ?: return

    brightnessBeforeFlash = window.attributes.screenBrightness
    brightnessWindow = WeakReference(window)
    window.attributes = window.attributes.apply {
      screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
    }
  }

  private fun restoreScreenBrightness() {
    val window = brightnessWindow?.get() ?: return
    window.attributes = window.attributes.apply {
      screenBrightness = brightnessBeforeFlash
    }
    brightnessWindow = null
  }

  private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
      if (context is Activity) return context
      context = context.baseContext
    }
    return null
  }

  private fun vibrate(context: Context) {
    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    vibrator?.let {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        it.vibrate(VibrationEffect.createOneShot(50, 75))
      } else {
        @Suppress("DEPRECATION")
        it.vibrate(50)
      }
    }
  }

  private data class BindingAttempt(
    val preview: Preview,
    val imageCapture: ImageCapture,
    val videoCapture: VideoCapture<Recorder>?,
    val imageAnalysis: ImageAnalysis?
  ) {
    fun toTypedArray(): Array<UseCase> {
      return listOfNotNull(preview, imageCapture, videoCapture, imageAnalysis).toTypedArray()
    }
  }
}
