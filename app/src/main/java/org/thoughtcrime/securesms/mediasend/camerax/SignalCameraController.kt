/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.mediasend.camerax

import android.Manifest
import android.content.Context
import android.util.Size
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.Surface
import android.view.View
import android.view.ViewConfiguration
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.DisplayOrientedMeteringPointFactory
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.FocusMeteringResult
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.core.ZoomState
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileDescriptorOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.CameraController
import androidx.camera.view.PreviewView
import androidx.camera.view.video.AudioConfig
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.signal.core.util.ThreadUtil
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.visible
import java.util.concurrent.Executor
import kotlin.math.max
import kotlin.math.min

/**
 * This is a class to manage the camera resource, and relies on the AndroidX CameraX library.
 *
 * The API is a subset of the [CameraController] class, but with a few additions such as [setImageRotation].
 */
class SignalCameraController(
  private val context: Context,
  private val lifecycleOwner: LifecycleOwner,
  private val previewView: PreviewView,
  private val focusIndicator: View
) {
  companion object {
    val TAG = Log.tag(SignalCameraController::class.java)

    private const val AF_SIZE = 1.0f / 6.0f
    private const val AE_SIZE = AF_SIZE * 1.5f

    @JvmStatic
    private fun isLandscape(surfaceRotation: Int): Boolean {
      return surfaceRotation == Surface.ROTATION_90 || surfaceRotation == Surface.ROTATION_270
    }
  }

  private val videoQualitySelector: QualitySelector = QualitySelector.from(Quality.HD, FallbackStrategy.lowerQualityThan(Quality.HD))
  private val imageMode = CameraXUtil.getOptimalCaptureMode()

  private val cameraProviderFuture: ListenableFuture<ProcessCameraProvider> = ProcessCameraProvider.getInstance(context)
  private val scaleGestureDetector = ScaleGestureDetector(context, PinchToZoomGestureListener())
  private val viewPort: ViewPort? = previewView.getViewPort(Surface.ROTATION_0)
  private val initializationCompleteListeners: MutableSet<InitializationListener> = mutableSetOf()
  private val customUseCases: MutableList<UseCase> = mutableListOf()

  private var tapToFocusEvents = 0

  private var imageRotation = 0
  private var recording: Recording? = null
  private var previewTargetSize: Size? = null
  private var imageCaptureTargetSize: Size? = null
  private var cameraSelector: CameraSelector = CameraXUtil.toCameraSelector(TextSecurePreferences.getDirectCaptureCameraId(context))
  private var enabledUseCases: Int = CameraController.IMAGE_CAPTURE

  private var previewUseCase: Preview = createPreviewUseCase()
  private var imageCaptureUseCase: ImageCapture = createImageCaptureUseCase()
  private var videoCaptureUseCase: VideoCapture<Recorder> = createVideoCaptureRecorder()

  private lateinit var cameraProvider: ProcessCameraProvider
  private lateinit var cameraProperty: Camera

  @RequiresPermission(Manifest.permission.CAMERA)
  fun bindToLifecycle(onCameraBoundListener: Runnable) {
    ThreadUtil.assertMainThread()
    if (this::cameraProvider.isInitialized) {
      bindToLifecycleInternal()
      onCameraBoundListener.run()
    } else {
      cameraProviderFuture.addListener({
        cameraProvider = cameraProviderFuture.get()
        initializationCompleteListeners.forEach { it.onInitialized(cameraProvider) }
        bindToLifecycleInternal()
        onCameraBoundListener.run()
      }, ContextCompat.getMainExecutor(context))
    }
  }

  @MainThread
  fun unbind() {
    ThreadUtil.assertMainThread()
    cameraProvider.unbindAll()
  }

  @MainThread
  private fun bindToLifecycleInternal() {
    ThreadUtil.assertMainThread()
    try {
      if (!this::cameraProvider.isInitialized) {
        Log.d(TAG, "Camera provider not yet initialized.")
        return
      }
      val camera = cameraProvider.bindToLifecycle(
        lifecycleOwner,
        cameraSelector,
        buildUseCaseGroup()
      )

      initializeTapToFocusAndPinchToZoom(camera)
      this.cameraProperty = camera
    } catch (e: Exception) {
      Log.e(TAG, "Use case binding failed", e)
    }
  }

  @MainThread
  fun addUseCase(useCase: UseCase) {
    ThreadUtil.assertMainThread()

    customUseCases += useCase

    if (isRecording()) {
      stopRecording()
    }

    tryToBindCamera()
  }

  @MainThread
  fun takePicture(executor: Executor, callback: ImageCapture.OnImageCapturedCallback) {
    ThreadUtil.assertMainThread()
    assertImageEnabled()
    imageCaptureUseCase.takePicture(executor, callback)
  }

  @RequiresApi(26)
  @MainThread
  fun startRecording(outputOptions: FileDescriptorOutputOptions, audioConfig: AudioConfig, videoSavedListener: Consumer<VideoRecordEvent>): Recording {
    ThreadUtil.assertMainThread()
    assertVideoEnabled()

    recording?.stop()
    recording = null
    val startedRecording = videoCaptureUseCase.output
      .prepareRecording(context, outputOptions)
      .apply {
        if (audioConfig.audioEnabled) {
          withAudioEnabled()
        }
      }
      .start(ContextCompat.getMainExecutor(context)) {
        videoSavedListener.accept(it)
        if (it is VideoRecordEvent.Finalize) {
          recording = null
        }
      }

    recording = startedRecording
    return startedRecording
  }

  @MainThread
  fun setEnabledUseCases(useCaseFlags: Int) {
    ThreadUtil.assertMainThread()
    if (enabledUseCases == useCaseFlags) {
      return
    }

    val oldEnabledUseCases = enabledUseCases
    enabledUseCases = useCaseFlags
    if (isRecording()) {
      stopRecording()
    }
    tryToBindCamera { enabledUseCases = oldEnabledUseCases }
  }

  @MainThread
  fun getImageCaptureFlashMode(): Int {
    ThreadUtil.assertMainThread()
    return imageCaptureUseCase.flashMode
  }

  @MainThread
  fun setPreviewTargetSize(size: Size) {
    ThreadUtil.assertMainThread()
    if (size == previewTargetSize || previewTargetSize?.equals(size) == true) {
      return
    }
    Log.d(TAG, "Setting Preview dimensions to $size")
    previewTargetSize = size
    if (this::cameraProvider.isInitialized) {
      cameraProvider.unbind(previewUseCase)
    }
    previewUseCase = createPreviewUseCase()

    tryToBindCamera(null)
  }

  @MainThread
  fun setImageCaptureTargetSize(size: Size) {
    ThreadUtil.assertMainThread()
    if (size == imageCaptureTargetSize || imageCaptureTargetSize?.equals(size) == true) {
      return
    }
    imageCaptureTargetSize = size
    if (this::cameraProvider.isInitialized) {
      cameraProvider.unbind(imageCaptureUseCase)
    }
    imageCaptureUseCase = createImageCaptureUseCase()
    tryToBindCamera(null)
  }

  @MainThread
  fun setImageRotation(rotation: Int) {
    ThreadUtil.assertMainThread()
    val newRotation = UseCase.snapToSurfaceRotation(rotation.coerceIn(0, 359))

    if (newRotation == imageRotation) {
      return
    }

    if (isLandscape(newRotation) != isLandscape(imageRotation)) {
      imageCaptureTargetSize = imageCaptureTargetSize?.swap()
    }

    videoCaptureUseCase.targetRotation = newRotation
    imageCaptureUseCase.targetRotation = newRotation

    imageRotation = newRotation
  }

  @MainThread
  fun setImageCaptureFlashMode(flashMode: Int) {
    ThreadUtil.assertMainThread()
    imageCaptureUseCase.flashMode = flashMode
  }

  @MainThread
  fun setZoomRatio(ratio: Float): ListenableFuture<Void> {
    ThreadUtil.assertMainThread()
    return cameraProperty.cameraControl.setZoomRatio(ratio)
  }

  @MainThread
  fun getZoomState(): LiveData<ZoomState> {
    ThreadUtil.assertMainThread()
    return cameraProperty.cameraInfo.zoomState
  }

  @MainThread
  fun setCameraSelector(selector: CameraSelector) {
    ThreadUtil.assertMainThread()
    if (selector == cameraSelector) {
      return
    }

    val oldCameraSelector: CameraSelector = cameraSelector
    cameraSelector = selector
    if (!this::cameraProvider.isInitialized) {
      return
    }
    cameraProvider.unbindAll()
    tryToBindCamera { cameraSelector = oldCameraSelector }
  }

  @MainThread
  fun getCameraSelector(): CameraSelector {
    ThreadUtil.assertMainThread()
    return cameraSelector
  }

  @MainThread
  fun hasCamera(selectedCamera: CameraSelector): Boolean {
    ThreadUtil.assertMainThread()
    return cameraProvider.hasCamera(selectedCamera)
  }

  @MainThread
  fun addInitializationCompletedListener(listener: InitializationListener) {
    ThreadUtil.assertMainThread()
    initializationCompleteListeners.add(listener)
  }

  @MainThread
  private fun tryToBindCamera(restoreStateRunnable: (() -> Unit)? = null) {
    ThreadUtil.assertMainThread()
    try {
      bindToLifecycleInternal()
    } catch (e: RuntimeException) {
      Log.i(TAG, "Could not re-bind camera!", e)
      restoreStateRunnable?.invoke()
    }
  }

  @MainThread
  private fun stopRecording() {
    ThreadUtil.assertMainThread()
    recording?.close()
  }

  private fun createVideoCaptureRecorder() = VideoCapture.Builder(
    Recorder.Builder()
      .setQualitySelector(videoQualitySelector)
      .build()
  )
    .setTargetRotation(imageRotation)
    .build()

  private fun createPreviewUseCase() = Preview.Builder()
    .apply {
      setTargetRotation(Surface.ROTATION_0)
      val size = previewTargetSize
      if (size != null) {
        setResolutionSelector(
          ResolutionSelector.Builder()
            .setResolutionStrategy(ResolutionStrategy(size, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
            .build()
        )
      }
    }.build()
    .also {
      it.setSurfaceProvider(previewView.surfaceProvider)
    }

  private fun createImageCaptureUseCase(): ImageCapture = ImageCapture.Builder()
    .apply {
      setCaptureMode(imageMode)
      setTargetRotation(imageRotation)

      val size = imageCaptureTargetSize
      if (size != null) {
        setResolutionSelector(
          ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(ResolutionStrategy(size, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
            .build()
        )
      }
    }.build()

  private fun buildUseCaseGroup() = UseCaseGroup.Builder().apply {
    addUseCase(previewUseCase)
    if (isUseCaseEnabled(CameraController.IMAGE_CAPTURE)) {
      addUseCase(imageCaptureUseCase)
    } else {
      cameraProvider.unbind(imageCaptureUseCase)
    }
    if (isUseCaseEnabled(CameraController.VIDEO_CAPTURE)) {
      addUseCase(videoCaptureUseCase)
    } else {
      cameraProvider.unbind(videoCaptureUseCase)
    }

    for (useCase in customUseCases) {
      addUseCase(useCase)
    }

    if (viewPort != null) {
      setViewPort(viewPort)
    } else {
      Log.d(TAG, "ViewPort was null, not adding to UseCase builder.")
    }
  }.build()

  @MainThread
  private fun initializeTapToFocusAndPinchToZoom(camera: Camera) {
    ThreadUtil.assertMainThread()
    previewView.setOnTouchListener { v: View?, event: MotionEvent ->
      val isSingleTouch = event.pointerCount == 1
      val isUpEvent = event.action == MotionEvent.ACTION_UP
      val notALongPress = (event.eventTime - event.downTime < ViewConfiguration.getLongPressTimeout())
      if (isSingleTouch && isUpEvent && notALongPress) {
        focusAndMeterOnPoint(camera, event.x, event.y)
        v?.performClick()
        return@setOnTouchListener true
      }
      return@setOnTouchListener scaleGestureDetector.onTouchEvent(event)
    }
  }

  @MainThread
  private fun focusAndMeterOnPoint(camera: Camera, x: Float, y: Float) {
    ThreadUtil.assertMainThread()
    val meteringPointFactory = DisplayOrientedMeteringPointFactory(previewView.display, camera.cameraInfo, previewView.width.toFloat(), previewView.height.toFloat())
    val afPoint = meteringPointFactory.createPoint(x, y, AF_SIZE)
    val aePoint = meteringPointFactory.createPoint(x, y, AE_SIZE)
    val action = FocusMeteringAction.Builder(afPoint, FocusMeteringAction.FLAG_AF)
      .addPoint(aePoint, FocusMeteringAction.FLAG_AE)
      .build()

    focusIndicator.x = x - (focusIndicator.width / 2)
    focusIndicator.y = y - (focusIndicator.height / 2)
    focusIndicator.visible = true

    tapToFocusEvents += 1

    Futures.addCallback(
      camera.cameraControl.startFocusAndMetering(action),
      object : FutureCallback<FocusMeteringResult> {
        override fun onSuccess(result: FocusMeteringResult?) {
          Log.d(TAG, "Tap to focus was successful? ${result?.isFocusSuccessful}")
          tapToFocusEvents -= 1
          if (tapToFocusEvents <= 0) {
            ViewUtil.fadeOut(focusIndicator, 80)
          }
        }

        override fun onFailure(t: Throwable) {
          Log.d(TAG, "Tap to focus could not be completed due to an exception.", t)
          tapToFocusEvents -= 1
          if (tapToFocusEvents <= 0) {
            ViewUtil.fadeOut(focusIndicator, 80)
          }
        }
      },
      ContextCompat.getMainExecutor(context)
    )
  }

  @MainThread
  private fun onPinchToZoom(pinchToZoomScale: Float) {
    val zoomState = getZoomState().getValue() ?: return
    var clampedRatio: Float = zoomState.zoomRatio * if (pinchToZoomScale > 1f) {
      1.0f + (pinchToZoomScale - 1.0f) * 2
    } else {
      1.0f - (1.0f - pinchToZoomScale) * 2
    }
    clampedRatio = min(
      max(clampedRatio.toDouble(), zoomState.minZoomRatio.toDouble()),
      zoomState.maxZoomRatio.toDouble()
    ).toFloat()
    setZoomRatio(clampedRatio)
  }

  private fun isRecording(): Boolean {
    return recording != null
  }

  private fun isUseCaseEnabled(mask: Int): Boolean {
    return (enabledUseCases and mask) != 0
  }

  private fun assertVideoEnabled() {
    if (!isUseCaseEnabled(CameraController.VIDEO_CAPTURE)) {
      throw IllegalStateException("VideoCapture disabled.")
    }
  }

  private fun assertImageEnabled() {
    if (!isUseCaseEnabled(CameraController.IMAGE_CAPTURE)) {
      throw IllegalStateException("ImageCapture disabled.")
    }
  }

  private fun Size.swap(): Size {
    return Size(this.height, this.width)
  }

  inner class PinchToZoomGestureListener : ScaleGestureDetector.OnScaleGestureListener {
    override fun onScale(detector: ScaleGestureDetector): Boolean {
      onPinchToZoom(detector.scaleFactor)
      return true
    }

    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean = true

    override fun onScaleEnd(detector: ScaleGestureDetector) = Unit
  }

  interface InitializationListener {
    fun onInitialized(cameraProvider: ProcessCameraProvider)
  }
}
