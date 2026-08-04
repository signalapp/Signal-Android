/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.capture;

import androidx.annotation.NonNull;

import org.signal.core.util.SeekableFileDescriptor;
import org.signal.mediasend.MediaConstraints;

public interface CameraFragment {

  void presentHud(int selectedMediaCount);
  void fadeOutControls(@NonNull Runnable onEndAction);
  void fadeInControls();

  interface Controller {
    void onImageCaptured(@NonNull byte[] data, int width, int height);
    /**
     * The descriptor is owned by the callee, which must close it once it is finished reading the recording.
     *
     * @param durationMs How long the recording ran, as reported by the recorder.
     */
    void onVideoCaptured(@NonNull SeekableFileDescriptor fd, long durationMs);
    void onVideoCaptureError();
    void onGalleryClicked();
    void onCameraCloseClicked();
    void onQrCodeFound(@NonNull String data);
    @NonNull MediaConstraints getMediaConstraints();
    int getMaxVideoDuration();
  }
}
