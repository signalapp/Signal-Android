package org.signal.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
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
import java.util.EnumMap
import java.util.concurrent.Executors
import kotlin.time.Duration.Companion.seconds

private const val TAG = "CameraScreenViewModel"

class CameraScreenViewModel : ViewModel() {
  companion object {
    private val imageAnalysisExecutor = Executors.newSingleThreadExecutor()
  }

  private val _state: MutableState<CameraScreenState> = mutableStateOf(CameraScreenState())
  val state: State<CameraScreenState>
    get() = _state

  private var camera: Camera? = null
  private var lifecycleOwner: LifecycleOwner? = null
  private var cameraProvider: ProcessCameraProvider? = null
  private var imageCapture: ImageCapture? = null
  private var videoCapture: VideoCapture<Recorder>? = null
  private var recording: Recording? = null

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

  /**
   * Capture a photo.
   * If using front camera with flash enabled but no hardware flash available,
   * uses a selfie flash (white screen overlay) to illuminate the subject.
   */
  @androidx.annotation.OptIn(markerClass = [androidx.camera.core.ExperimentalGetImage::class])
  fun capturePhoto(
    context: Context,
    onPhotoCaptured: (Bitmap) -> Unit,
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
    // Show selfie flash
    _state.value = state.copy(showSelfieFlash = true)

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
    }
  }

  /**
   * Start video recording.
   */
  @androidx.annotation.OptIn(markerClass = [androidx.camera.core.ExperimentalGetImage::class])
  @android.annotation.SuppressLint("MissingPermission", "RestrictedApi", "NewApi")
  fun startRecording(
    context: Context,
    output: VideoOutput,
    onVideoCaptured: (VideoCaptureResult) -> Unit
  ) {
    val capture = videoCapture ?: return

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

    val activeRecording = pendingRecording
      .withAudioEnabled()
      .start(ContextCompat.getMainExecutor(context)) { recordEvent ->
        when (recordEvent) {
          is VideoRecordEvent.Start -> {
            Log.d(TAG, "Video recording started")
            startRecordingTimer()
            vibrate(context)
          }
          is VideoRecordEvent.Finalize -> {
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
          }
        }
      }

    recording = activeRecording
  }

  /**
   * Stop video recording.
   */
  fun stopRecording() {
    recording?.stop()
    recording = null
  }

  override fun onCleared() {
    super.onCleared()
    stopRecording()
  }

  private fun handleBindCameraEvent(
    state: CameraScreenState,
    event: CameraScreenEvents.BindCamera
  ) {
    val resolutionSelector = ResolutionSelector.Builder()
      .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
      .build()

    // Preview with 16:9 aspect ratio - uses Compose Viewfinder
    val preview = Preview.Builder()
      .setResolutionSelector(resolutionSelector)
      .build()
      .also { it.surfaceProvider = event.surfaceProvider }

    // Image capture with 16:9 aspect ratio (optimized for speed)
    val imageCaptureUseCase = ImageCapture.Builder()
      .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
      .setResolutionSelector(resolutionSelector)
      .build()

    // Video capture (16:9 is default for video)
    val recorder = Recorder.Builder()
      .setAspectRatio(AspectRatio.RATIO_16_9)
      .setQualitySelector(
        androidx.camera.video.QualitySelector.from(
          androidx.camera.video.Quality.HIGHEST,
          androidx.camera.video.FallbackStrategy.higherQualityOrLowerThan(androidx.camera.video.Quality.HD)
        )
      )
      .build()
    val videoCaptureUseCase = VideoCapture.withOutput(recorder)

    // Image analysis for QR code detection
    val imageAnalysisUseCase = ImageAnalysis.Builder()
      .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
      .build()
      .also {
        it.setAnalyzer(imageAnalysisExecutor) { imageProxy ->
          processImageForQrCode(imageProxy)
        }
      }

    // Select camera based on lensFacing
    val cameraSelector = CameraSelector.Builder()
      .requireLensFacing(state.lensFacing)
      .build()

    try {
      // Unbind use cases before rebinding
      event.cameraProvider.unbindAll()

      // Bind use cases to camera
      camera = event.cameraProvider.bindToLifecycle(
        event.lifecycleOwner,
        cameraSelector,
        preview,
        imageCaptureUseCase,
        videoCaptureUseCase,
        imageAnalysisUseCase
      )

      lifecycleOwner = event.lifecycleOwner
      cameraProvider = event.cameraProvider
      imageCapture = imageCaptureUseCase
      videoCapture = videoCaptureUseCase
    } catch (e: Exception) {
      Log.e(TAG, "Use case binding failed", e)
    }
  }

  private fun handleTapToFocusEvent(
    state: CameraScreenState,
    event: CameraScreenEvents.TapToFocus
  ) {
    val currentCamera = camera ?: return

    val factory = SurfaceOrientedMeteringPointFactory(event.width, event.height)
    val point = factory.createPoint(event.x, event.y)
    val action = FocusMeteringAction.Builder(point).build()

    currentCamera.cameraControl.startFocusAndMetering(action)

    _state.value = state.copy(
      focusPoint = Offset(event.x, event.y),
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

    // Clamp linear zoom to valid range
    val clampedLinearZoom = linearZoom.coerceIn(0f, 1f)

    // CameraX setLinearZoom takes 0.0-1.0 and maps to min-max zoom ratio
    currentCamera.cameraControl.setLinearZoom(clampedLinearZoom)

    // Calculate the actual zoom ratio for state tracking
    val minZoom = currentCamera.cameraInfo.zoomState.value?.minZoomRatio ?: 1f
    val maxZoom = currentCamera.cameraInfo.zoomState.value?.maxZoomRatio ?: 1f
    val newZoomRatio = minZoom + (maxZoom - minZoom) * clampedLinearZoom

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
}
