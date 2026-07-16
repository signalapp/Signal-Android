/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend;

import androidx.annotation.NonNull;

import java.io.FileDescriptor;

public interface CameraFragment {

  void presentHud(int selectedMediaCount);
  void fadeOutControls(@NonNull Runnable onEndAction);
  void fadeInControls();

  interface Controller {
    void onImageCaptured(@NonNull byte[] data, int width, int height);
    void onVideoCaptured(@NonNull FileDescriptor fd);
    void onVideoCaptureError();
    void onGalleryClicked();
    void onCameraCountButtonClicked();
    void onQrCodeFound(@NonNull String data);
    @NonNull MediaConstraints getMediaConstraints();
    int getMaxVideoDuration();
  }
}
