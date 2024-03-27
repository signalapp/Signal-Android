/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.mediasend.camerax

import android.content.Context
import android.util.Size
import androidx.annotation.RequiresApi
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ZoomState
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileDescriptorOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.video.AudioConfig
import androidx.core.util.Consumer
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import com.google.common.util.concurrent.ListenableFuture
import org.thoughtcrime.securesms.util.TextSecurePreferences
import java.util.concurrent.Executor

class PlatformCameraController(context: Context) : CameraXController {
  val delegate = LifecycleCameraController(context)

  override fun isInitialized(): Boolean {
    return delegate.initializationFuture.isDone
  }

  override fun initializeAndBind(context: Context, lifecycleOwner: LifecycleOwner) {
    delegate.bindToLifecycle(lifecycleOwner)
    delegate.setCameraSelector(CameraXUtil.toCameraSelector(TextSecurePreferences.getDirectCaptureCameraId(context)))
    delegate.setTapToFocusEnabled(true)
    delegate.setImageCaptureMode(CameraXUtil.getOptimalCaptureMode())
    delegate.setVideoCaptureQualitySelector(QualitySelector.from(Quality.HD, FallbackStrategy.lowerQualityThan(Quality.HD)))
  }

  override fun bindToLifecycle(lifecycleOwner: LifecycleOwner, onCameraBoundListener: Runnable) {
    delegate.bindToLifecycle(lifecycleOwner)
    onCameraBoundListener.run()
  }

  override fun unbind() {
    delegate.unbind()
  }

  override fun takePicture(executor: Executor, callback: ImageCapture.OnImageCapturedCallback) {
    delegate.takePicture(executor, callback)
  }

  @RequiresApi(26)
  override fun startRecording(outputOptions: FileDescriptorOutputOptions, audioConfig: AudioConfig, executor: Executor, videoSavedListener: Consumer<VideoRecordEvent>): Recording {
    return delegate.startRecording(outputOptions, audioConfig, executor, videoSavedListener)
  }

  override fun setImageAnalysisAnalyzer(executor: Executor, analyzer: ImageAnalysis.Analyzer) {
    delegate.setImageAnalysisAnalyzer(executor, analyzer)
  }

  override fun setEnabledUseCases(useCaseFlags: Int) {
    delegate.setEnabledUseCases(useCaseFlags)
  }

  override fun getImageCaptureFlashMode(): Int {
    return delegate.imageCaptureFlashMode
  }

  override fun setPreviewTargetSize(size: Size) {
    delegate.previewTargetSize = CameraController.OutputSize(size)
  }

  override fun setImageCaptureTargetSize(size: Size) {
    delegate.imageCaptureTargetSize = CameraController.OutputSize(size)
  }

  override fun setImageRotation(rotation: Int) {
    throw NotImplementedError("Not supported by the platform camera controller!")
  }

  override fun setImageCaptureFlashMode(flashMode: Int) {
    delegate.imageCaptureFlashMode = flashMode
  }

  override fun setZoomRatio(ratio: Float): ListenableFuture<Void> {
    return delegate.setZoomRatio(ratio)
  }

  override fun getZoomState(): LiveData<ZoomState> {
    return delegate.zoomState
  }

  override fun setCameraSelector(selector: CameraSelector) {
    delegate.cameraSelector = selector
  }

  override fun getCameraSelector(): CameraSelector {
    return delegate.cameraSelector
  }

  override fun hasCamera(selectedCamera: CameraSelector): Boolean {
    return delegate.hasCamera(selectedCamera)
  }

  override fun addInitializationCompletedListener(executor: Executor, onComplete: () -> Unit) {
    delegate.initializationFuture.addListener(onComplete, executor)
  }
}
