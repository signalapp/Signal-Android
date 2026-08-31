package org.signal.camera

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Size
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
import androidx.camera.core.ZoomState
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.zxing.BinaryBitmap
import com.google.zxing.ChecksumException
import com.google.zxing.DecodeHintType
import com.google.zxing.FormatException
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.util.Stopwatch
import org.signal.core.util.logging.Log
import org.signal.core.util.throttleLatest
import java.lang.ref.WeakReference
import java.util.EnumMap
import java.util.concurrent.Executors
import kotlin.math.exp
import kotlin.math.ln
import kotlin.time.Duration.Companion.nanoseconds
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

    /** Requested resolution for the QR analysis stream. */
    private val QR_ANALYSIS_RESOLUTION = Size(1280, 720)

    /** A single point, so a lens that has not reported its range yet reads as one that cannot zoom. */
    private val DEFAULT_ZOOM_RANGE = 1f..1f

    /** How long a zoom animation to a level picked off the zoom bar runs for. */
    private const val ZOOM_ANIMATION_DURATION_MS = 250L

    /** One frame at 60Hz. */
    private const val ZOOM_ANIMATION_FRAME_MS = 16L

    private val ZOOM_ANIMATION_EASING = FastOutSlowInEasing
  }

  private val _state: MutableState<CameraScreenState> = mutableStateOf(CameraScreenState())
  val state: State<CameraScreenState>
    get() = _state

  private var camera: Camera? = null
  private var lifecycleOwner: LifecycleOwner? = null
  private var cameraProvider: ProcessCameraProvider? = null
  private var lastSuccessfulAttempt: BindingAttempt? = null
  private var preview: Preview? = null
  private var imageCapture: ImageCapture? = null
  private var videoCapture: VideoCapture<Recorder>? = null
  private var recording: Recording? = null
  private var captureMode: CameraCaptureMode = CameraCaptureMode.ImageOnly
  private var brightnessBeforeFlash: Float = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
  private var brightnessWindow: WeakReference<Window>? = null
  private var deviceTargetRotation: Int = Surface.ROTATION_0
  private var surfaceProvider: Preview.SurfaceProvider? = null
  private var recordingStartZoomRatio: Float = 1f

  /** The in-flight animation to a level picked off the zoom bar. Anything else that moves the zoom cancels it. */
  private var zoomAnimation: Job? = null

  /**
   * A lock that arrived before the recorder reported the recording as started, applied by [startRecordingTimer] once it
   * does. Without it the HUD would settle into its held state with no finger on the capture button, where nothing but
   * the duration cap can stop the recording.
   */
  private var pendingRecordingLock: Boolean = false

  /**
   * Set when a lens is bound, so that the first zoom it reports is taken as the current ratio. A rebind comes up at the
   * new lens's own zoom rather than carrying the previous one's over.
   */
  private var needsZoomResync: Boolean = false

  /** Null for a recording that finalizes without being asked to stop, such as one that hits the recorder's own limits. */
  private var recordingStopwatch: Stopwatch? = null

  private val _qrCodeDetected = MutableSharedFlow<String>(extraBufferCapacity = 1)

  /**
   * Flow of detected QR codes. Observers can collect from this flow to receive QR code detections.
   * The flow filters consecutive duplicates and is throttled to avoid rapid-fire detections.
   */
  val qrCodeDetected: Flow<String> = _qrCodeDetected
    .throttleLatest(2.seconds)
    .onEach { Log.i(TAG, "Decoded a QR code. payloadLength: ${it.length}") }

  /**
   * Whether a recording has been started and not yet finished. Unlike [CameraScreenState.isRecording] this is true from
   * the moment [startRecording] is called rather than from when the recorder reports itself as running, so it is what a
   * caller has to check before starting another.
   */
  val hasActiveRecording: Boolean
    get() = recording != null

  /** Held so that binding a different lens can stop observing the one before it. */
  private var observedZoomState: LiveData<ZoomState>? = null

  private val zoomRangeObserver = Observer<ZoomState> { zoomState ->
    val zoomRange = zoomState.minZoomRatio..zoomState.maxZoomRatio

    if (needsZoomResync) {
      needsZoomResync = false
      Log.d(TAG, "Bound lens reaches $zoomRange at ${zoomState.zoomRatio}x")
      recordingStartZoomRatio = zoomState.zoomRatio
      _state.value = _state.value.copy(zoomRange = zoomRange, zoomRatio = zoomState.zoomRatio)
    } else if (zoomRange != _state.value.zoomRange) {
      Log.d(TAG, "Bound lens reaches $zoomRange")
      _state.value = _state.value.copy(zoomRange = zoomRange)
    } else if (zoomState.zoomRatio != _state.value.zoomRatio) {
      _state.value = _state.value.copy(zoomRatio = zoomState.zoomRatio)
    }
  }

  private val qrCodeReader = QRCodeReader()
  private val qrCodeHint = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java).apply {
    set(DecodeHintType.TRY_HARDER, true)
    set(DecodeHintType.CHARACTER_SET, "ISO-8859-1")
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
      is CameraScreenEvents.SetZoomRatio -> {
        handleSetZoomRatioEvent(currentState, event.zoomRatio)
      }
      is CameraScreenEvents.LockRecording -> {
        handleLockRecordingEvent(currentState)
      }
      is CameraScreenEvents.ToggleRecordingPaused -> {
        handleToggleRecordingPausedEvent(currentState)
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
      is CameraScreenEvents.SetDeviceRotation -> {
        handleSetDeviceRotation(event.rotation, event.isSmallScreen)
      }
    }
  }

  private fun logEvent(event: CameraScreenEvents) {
    when (event) {
      is CameraScreenEvents.BindCamera -> Log.d(TAG, "[Event] BindCamera(captureMode=${event.captureMode}, enableQrScanning=${event.enableQrScanning})")
      is CameraScreenEvents.TapToFocus -> Log.d(TAG, "[Event] TapToFocus(view=${event.viewX},${event.viewY}, surface=${event.surfaceX},${event.surfaceY})")
      is CameraScreenEvents.PinchZoom -> Log.d(TAG, "[Event] PinchZoom(factor=${event.zoomFactor})")
      is CameraScreenEvents.LinearZoom -> Log.d(TAG, "[Event] LinearZoom(${event.linearZoom})")
      is CameraScreenEvents.SetZoomRatio -> Log.d(TAG, "[Event] SetZoomRatio(${event.zoomRatio})")
      is CameraScreenEvents.LockRecording -> Log.d(TAG, "[Event] LockRecording")
      is CameraScreenEvents.ToggleRecordingPaused -> Log.d(TAG, "[Event] ToggleRecordingPaused")
      is CameraScreenEvents.SwitchCamera -> Log.d(TAG, "[Event] SwitchCamera")
      is CameraScreenEvents.SetFlashMode -> Log.d(TAG, "[Event] SetFlashMode(${event.flashMode})")
      is CameraScreenEvents.NextFlashMode -> Log.d(TAG, "[Event] NextFlashMode")
      is CameraScreenEvents.ClearCaptureError -> Log.d(TAG, "[Event] ClearCaptureError")
      is CameraScreenEvents.SetDeviceRotation -> Log.d(TAG, "[Event] SetDeviceRotation(rotation=${event.rotation}, isSmallScreen=${event.isSmallScreen})")
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
   *
   * @param isRecordingLocked Whether the recording runs until [stopRecording] rather than for only as long as the
   *   caller's gesture. Cleared alongside [CameraScreenState.isRecording] so it cannot outlive the recording.
   */
  @androidx.annotation.OptIn(markerClass = [androidx.camera.core.ExperimentalGetImage::class])
  @SuppressLint("MissingPermission", "RestrictedApi", "NewApi")
  fun startRecording(
    context: Context,
    output: VideoOutput,
    isRecordingLocked: Boolean = false,
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
            startRecordingTimer(isRecordingLocked)
            vibrate(context)
          }
          is VideoRecordEvent.Pause -> {
            Log.d(TAG, "Video recording paused")
            _state.value = _state.value.copy(isRecordingPaused = true)
          }
          is VideoRecordEvent.Resume -> {
            Log.d(TAG, "Video recording resumed")
            _state.value = _state.value.copy(isRecordingPaused = false)
          }
          is VideoRecordEvent.Finalize -> {
            val stopwatch = recordingStopwatch
            recordingStopwatch = null
            stopwatch?.split("finalize")

            if (enableTorch) {
              camera?.cameraControl?.enableTorch(false)
            }

            val result = if (!recordEvent.hasError()) {
              Log.d(TAG, "Video recording succeeded. bytes: ${recordEvent.recordingStats.numBytesRecorded}")
              val durationMs = recordEvent.recordingStats.recordedDurationNanos.nanoseconds.inWholeMilliseconds
              when (output) {
                is VideoOutput.FileOutput -> {
                  VideoCaptureResult.Success(outputFile = output.file, durationMs = durationMs)
                }
                is VideoOutput.FileDescriptorOutput -> {
                  VideoCaptureResult.Success(fileDescriptor = output.fileDescriptor, durationMs = durationMs)
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

            stopwatch?.split("handoff")
            stopwatch?.stop(TAG)

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

    val activeRecording = recording ?: return

    recordingStopwatch = Stopwatch("recording-stop")
    activeRecording.stop()
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
      observeZoomRange()
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
      observeZoomRange()
      Log.d(TAG, "Rebound to last successful configuration after video capture")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to rebind to last successful configuration after video capture", e)
    }
  }

  override fun onCleared() {
    super.onCleared()
    stopRecording()
    observedZoomState?.removeObserver(zoomRangeObserver)
    observedZoomState = null
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

        attempt.imageAnalysis?.let { Log.d(TAG, "Bound QR analysis at ${it.resolutionInfo?.resolution}") }

        lifecycleOwner = event.lifecycleOwner
        cameraProvider = event.cameraProvider
        lastSuccessfulAttempt = attempt
        preview = attempt.preview
        surfaceProvider = event.surfaceProvider
        imageCapture = attempt.imageCapture
        videoCapture = attempt.videoCapture
        captureMode = event.captureMode
        observeZoomRange()
      } catch (e: Exception) {
        Log.e(TAG, "Use case binding failed (attempt ${index + 1} of ${bindingAttempts.size})", e)
        continue
      }

      return true
    }

    Log.e(TAG, "All use case binding attempts failed")
    return false
  }

  @SuppressLint("RestrictedApi")
  private fun buildVideoCapture(): VideoCapture<Recorder> {
    val recorder = Recorder.Builder()
      .setAspectRatio(AspectRatio.RATIO_16_9)
      .setQualitySelector(
        QualitySelector.from(
          Quality.HD,
          FallbackStrategy.lowerQualityOrHigherThan(Quality.HD)
        )
      )
      // Recording at the highest transcoding target means no sent-media quality tier is sourced from a lower-bitrate capture.
      .setTargetVideoEncodingBitRate(CameraDependencies.getMaxVideoBitrateBps())
      .build()
    return VideoCapture.withOutput(recorder)
  }

  private fun buildBindingAttempts(
    event: CameraScreenEvents.BindCamera
  ): List<BindingAttempt> {
    val resolutionSelector = ResolutionSelector.Builder()
      .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
      .build()

    // Target rotation must be set at build time — the compose viewfinder ignores later changes.
    val preview = Preview.Builder()
      .setResolutionSelector(resolutionSelector)
      .setTargetRotation(deviceTargetRotation)
      .build()
      .also { it.surfaceProvider = event.surfaceProvider }

    // Image capture with 16:9 aspect ratio (optimized for speed).
    // The flash mode must be seeded from state: this builds a brand new ImageCapture, and a fresh one
    // defaults to FLASH_MODE_OFF. Rebinds happen on every camera switch and every re-entry into the
    // camera, so without this the HUD would keep showing the user's flash selection while the hardware
    // flash silently stopped firing.
    val imageCapture = ImageCapture.Builder()
      .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
      .setResolutionSelector(resolutionSelector)
      .setFlashMode(_state.value.flashMode.cameraxMode)
      .build()

    val videoCapture: VideoCapture<Recorder>? = if (event.captureMode == CameraCaptureMode.ImageAndVideoSimultaneous && !FORCE_LIMITED_BINDING) {
      buildVideoCapture()
    } else {
      null
    }

    val qrAnalysis: ImageAnalysis? = if (event.enableQrScanning) {
      val qrResolutionSelector = ResolutionSelector.Builder()
        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
        .setResolutionStrategy(ResolutionStrategy(QR_ANALYSIS_RESOLUTION, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
        .build()

      ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .setResolutionSelector(qrResolutionSelector)
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

  /** Single source of truth for rotation: drives capture, the Small-screen icon rotation, and the card/preview. */
  private fun handleSetDeviceRotation(rotation: Int, isSmallScreen: Boolean) {
    // Capture always follows the physical rotation so photos are upright, even on a locked-portrait phone.
    imageCapture?.targetRotation = rotation
    videoCapture?.targetRotation = rotation

    // Small screens stay portrait (icons rotate instead); only larger screens reorient the card/preview.
    val effectiveRotation = if (isSmallScreen) Surface.ROTATION_0 else rotation
    val rebuild = effectiveRotation != deviceTargetRotation
    deviceTargetRotation = effectiveRotation

    _state.value = _state.value.copy(
      deviceRotation = rotation,
      isLandscape = isLandscapeRotation(effectiveRotation)
    )

    if (rebuild) {
      handleRefreshPreviewRotation()
    }
  }

  /**
   * Rebuilds the preview at the current device rotation. The compose viewfinder only honors a preview's
   * target rotation at build time, so we rebuild the preview and swap it in — keeping capture bound (no
   * [ProcessCameraProvider.unbindAll]), which avoids the long black teardown that eventually wedges the HAL.
   */
  private fun handleRefreshPreviewRotation() {
    val cameraProvider = cameraProvider ?: return
    val lifecycleOwner = lifecycleOwner ?: return
    val oldPreview = preview ?: return
    val surfaceProvider = surfaceProvider ?: return

    val resolutionSelector = ResolutionSelector.Builder()
      .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
      .build()

    val newPreview = Preview.Builder()
      .setResolutionSelector(resolutionSelector)
      .setTargetRotation(deviceTargetRotation)
      .build()
      .also { it.surfaceProvider = surfaceProvider }

    val cameraSelector = CameraSelector.Builder()
      .requireLensFacing(_state.value.lensFacing)
      .build()

    try {
      cameraProvider.unbind(oldPreview)
      camera = cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, newPreview)
      preview = newPreview
      lastSuccessfulAttempt = lastSuccessfulAttempt?.copy(preview = newPreview)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to refresh preview rotation", e)
      // The old preview is already unbound; rebind it so we don't leave the viewfinder on a dead surface.
      try {
        camera = cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, oldPreview)
      } catch (restore: Exception) {
        Log.e(TAG, "Failed to restore preview after rotation failure", restore)
      }
    }
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

    // A pinch takes over any in-flight animation from wherever it has reached.
    zoomAnimation?.cancel()

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

    // The lens being animated is about to be unbound, and the one replacing it starts from its own zoom.
    zoomAnimation?.cancel()

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

  /**
   * Animates to a ratio, which is what the zoom bar asks for.
   *
   * The intermediate ratios are interpolated in log space, so doubling takes as long from 1x as it does from 8x;
   * interpolated linearly the same journey would tear away at the start and crawl at the end.
   *
   * [recordingStartZoomRatio] moves with the animation, so a capture-button drag picked up midway carries on from where
   * the lens has reached rather than from where the recording started.
   */
  private fun handleSetZoomRatioEvent(
    state: CameraScreenState,
    zoomRatio: Float
  ) {
    val currentCamera = camera ?: return
    val zoomState = currentCamera.cameraInfo.zoomState.value

    val clampedZoomRatio = zoomRatio.coerceIn(zoomState?.minZoomRatio ?: 1f, zoomState?.maxZoomRatio ?: 1f)
    val fromZoomRatio = state.zoomRatio

    zoomAnimation?.cancel()

    // Nothing to interpolate: a non-positive ratio has no logarithm, and one already at the level has nowhere to go.
    if (fromZoomRatio <= 0f || clampedZoomRatio <= 0f || fromZoomRatio == clampedZoomRatio) {
      applyZoomRatio(currentCamera, clampedZoomRatio)
      return
    }

    zoomAnimation = viewModelScope.launch {
      val fromLog = ln(fromZoomRatio)
      val toLog = ln(clampedZoomRatio)

      // Counting frames rather than reading a wall clock keeps the duration stable under a test's virtual time.
      var elapsedMs = 0L
      while (elapsedMs < ZOOM_ANIMATION_DURATION_MS) {
        delay(ZOOM_ANIMATION_FRAME_MS)
        elapsedMs += ZOOM_ANIMATION_FRAME_MS

        val fraction = ZOOM_ANIMATION_EASING.transform((elapsedMs.toFloat() / ZOOM_ANIMATION_DURATION_MS).coerceAtMost(1f))
        applyZoomRatio(currentCamera, exp(fromLog + (toLog - fromLog) * fraction))
      }

      // Interpolating through a logarithm leaves the last frame beside the level rather than on it, and the bar reads
      // the ratio to decide what is selected, so finish exactly on the level.
      applyZoomRatio(currentCamera, clampedZoomRatio)
    }
  }

  /** Sets the lens to [zoomRatio] and moves the drag base and state along with it. */
  private fun applyZoomRatio(camera: Camera, zoomRatio: Float) {
    camera.cameraControl.setZoomRatio(zoomRatio)
    recordingStartZoomRatio = zoomRatio

    _state.value = _state.value.copy(zoomRatio = zoomRatio)
  }

  /**
   * Leaves a running recording going without the capture button being held. A lock with no recording behind it is
   * dropped rather than held, so it cannot outlive the gesture that asked for it and apply to the next recording.
   */
  private fun handleLockRecordingEvent(state: CameraScreenState) {
    when {
      state.isRecording -> _state.value = state.copy(isRecordingLocked = true)
      hasActiveRecording -> pendingRecordingLock = true
      else -> Log.w(TAG, "Ignoring a lock with no recording to hold open")
    }
  }

  /**
   * Pauses the running recording, or resumes a paused one. The state is not written here: the recorder reports the pause
   * taking hold and that is what the screen goes by, so a pause it will not honor never shows.
   */
  private fun handleToggleRecordingPausedEvent(state: CameraScreenState) {
    val activeRecording = recording ?: return

    if (state.isRecordingPaused) {
      activeRecording.resume()
    } else {
      activeRecording.pause()
    }
  }

  /**
   * Observes what the bound lens can reach. A camera reports its zoom when it is ready rather than by the time it is
   * bound, so this observes rather than taking a single reading — a lens whose range arrives late would otherwise look
   * like one that cannot zoom.
   */
  private fun observeZoomRange() {
    val zoomState = camera?.cameraInfo?.zoomState

    if (zoomState === observedZoomState) {
      return
    }

    observedZoomState?.removeObserver(zoomRangeObserver)
    observedZoomState = zoomState
    needsZoomResync = true

    if (zoomState != null) {
      zoomState.observeForever(zoomRangeObserver)
    } else {
      _state.value = _state.value.copy(zoomRange = DEFAULT_ZOOM_RANGE)
    }
  }

  private fun handleSetLinearZoomEvent(
    state: CameraScreenState,
    linearZoom: Float
  ) {
    val currentCamera = camera ?: return

    // A drag takes over any in-flight animation from wherever it has reached.
    zoomAnimation?.cancel()

    // Clamp linear zoom to valid range (-1 to 1)
    val clampedLinearZoom = linearZoom.coerceIn(-1f, 1f)

    val zoomState = currentCamera.cameraInfo.zoomState.value
    val minZoom = zoomState?.minZoomRatio ?: 1f
    val maxZoom = zoomState?.maxZoomRatio ?: 1f

    // The drag runs from the ratio it started at rather than from 1x, so picking it up does not jump the lens. Positive
    // values zoom in from that base toward maxZoom, negative values out toward minZoom. The base is clamped as well as
    // the result: a base the lens can no longer reach would otherwise leave the whole drag pinned to one end.
    val baseZoom = recordingStartZoomRatio.coerceIn(minZoom, maxZoom)
    val targetZoomRatio = if (clampedLinearZoom >= 0f) {
      baseZoom + (maxZoom - baseZoom) * clampedLinearZoom
    } else {
      baseZoom + (baseZoom - minZoom) * clampedLinearZoom
    }

    // The camera clamps what it is sent, so the state is clamped the same way — otherwise the zoom bar reads a level the
    // lens is not at, and the next drag works from a base the lens never reached.
    val newZoomRatio = targetZoomRatio.coerceIn(minZoom, maxZoom)

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

  private fun startRecordingTimer(isRecordingLocked: Boolean) {
    _state.value = _state.value.copy(
      isRecording = true,
      isRecordingLocked = isRecordingLocked || pendingRecordingLock,
      isRecordingPaused = false,
      recordingDuration = 0L
    )
    pendingRecordingLock = false

    viewModelScope.launch {
      while (_state.value.isRecording) {
        delay(100L)

        // A paused recording is not recording anything, so its duration has nothing to add.
        if (!_state.value.isRecordingPaused) {
          _state.value = _state.value.copy(recordingDuration = _state.value.recordingDuration + 100L)
        }
      }
    }
  }

  private fun stopRecordingTimer() {
    pendingRecordingLock = false
    _state.value = _state.value.copy(
      isRecording = false,
      isRecordingLocked = false,
      isRecordingPaused = false,
      recordingDuration = 0L
    )
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
        val result = qrCodeReader.decode(binaryBitmap, qrCodeHint)
        if (result != null) {
          _qrCodeDetected.tryEmit(result.text)
        }
      } catch (_: NotFoundException) {
        // No QR code found in this frame, which is normal
      } catch (_: ChecksumException) {
        // QR code detected but checksum failed
      } catch (_: FormatException) {
        // QR code detected but format is invalid
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
      if (Build.VERSION.SDK_INT >= 26) {
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
