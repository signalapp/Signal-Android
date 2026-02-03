/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.mediasend.v3

import androidx.compose.runtime.Composable
import io.reactivex.rxjava3.core.Flowable
import org.signal.core.models.media.Media
import org.signal.mediasend.MediaSendActivity
import org.thoughtcrime.securesms.mediasend.CameraFragment
import org.thoughtcrime.securesms.mms.MediaConstraints
import java.io.FileDescriptor
import java.util.Optional

/**
 * App-layer implementation of the feature module media send activity.
 */
class MediaSendV3Activity : MediaSendActivity(), CameraFragment.Controller {

  override val preUploadCallback = MediaSendV3PreUploadCallback()
  override val repository by lazy { MediaSendV3Repository(applicationContext) }

  @Composable
  override fun CameraSlot() {
    MediaSendV3CameraSlot()
  }

  @Composable
  override fun TextStoryEditorSlot() {
    MediaSendV3PlaceholderScreen(text = "Text Story Editor")
  }

  @Composable
  override fun VideoEditorSlot() {
    MediaSendV3PlaceholderScreen(text = "Video Editor")
  }

  @Composable
  override fun SendSlot() {
    MediaSendV3PlaceholderScreen(text = "Send Review")
  }

  // region Camera Callbacks

  override fun onCameraError() {
    error("Not yet implemented")
  }

  override fun onImageCaptured(data: ByteArray, width: Int, height: Int) {
    error("Not yet implemented")
  }

  override fun onVideoCaptured(fd: FileDescriptor) {
    error("Not yet implemented")
  }

  override fun onVideoCaptureError() {
    error("Not yet implemented")
  }

  override fun onGalleryClicked() {
    error("Not yet implemented")
  }

  override fun onCameraCountButtonClicked() {
    error("Not yet implemented")
  }

  override fun onQrCodeFound(data: String) {
    error("Not yet implemented")
  }

  override fun getMostRecentMediaItem(): Flowable<Optional<Media?>> {
    error("Not yet implemented")
  }

  override fun getMediaConstraints(): MediaConstraints {
    return MediaConstraints.getPushMediaConstraints()
  }

  override fun getMaxVideoDuration(): Int {
    error("Not yet implemented")
  }
  // endregion
}
