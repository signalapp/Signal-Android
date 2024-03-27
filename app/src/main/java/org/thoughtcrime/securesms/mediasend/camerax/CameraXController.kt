/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.mediasend.camerax

import android.Manifest
import android.content.Context
import android.util.Size
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ZoomState
import androidx.camera.video.FileDescriptorOutputOptions
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.video.AudioConfig
import androidx.core.util.Consumer
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executor

interface CameraXController {

  fun isInitialized(): Boolean

  fun initializeAndBind(context: Context, lifecycleOwner: LifecycleOwner)

  @RequiresPermission(Manifest.permission.CAMERA)
  fun bindToLifecycle(lifecycleOwner: LifecycleOwner, onCameraBoundListener: Runnable)

  @MainThread
  fun unbind()

  @MainThread
  fun takePicture(executor: Executor, callback: ImageCapture.OnImageCapturedCallback)

  @RequiresApi(26)
  @MainThread
  fun startRecording(outputOptions: FileDescriptorOutputOptions, audioConfig: AudioConfig, executor: Executor, videoSavedListener: Consumer<VideoRecordEvent>): Recording

  @MainThread
  fun setImageAnalysisAnalyzer(executor: Executor, analyzer: ImageAnalysis.Analyzer)

  @MainThread
  fun setEnabledUseCases(useCaseFlags: Int)

  @MainThread
  fun getImageCaptureFlashMode(): Int

  @MainThread
  fun setPreviewTargetSize(size: Size)

  @MainThread
  fun setImageCaptureTargetSize(size: Size)

  @MainThread
  fun setImageRotation(rotation: Int)

  @MainThread
  fun setImageCaptureFlashMode(flashMode: Int)

  @MainThread
  fun setZoomRatio(ratio: Float): ListenableFuture<Void>

  @MainThread
  fun getZoomState(): LiveData<ZoomState>

  @MainThread
  fun setCameraSelector(selector: CameraSelector)

  @MainThread
  fun getCameraSelector(): CameraSelector

  @MainThread
  fun hasCamera(selectedCamera: CameraSelector): Boolean

  @MainThread
  fun addInitializationCompletedListener(executor: Executor, onComplete: () -> Unit)
}
