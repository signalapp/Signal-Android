package org.thoughtcrime.securesms.ringrtc;

import androidx.annotation.NonNull;

/**
 * Event listener that are (indirectly) bound to WebRTC events.
 * onFullyInitialized and onCameraStopped are hardware lifecycle methods triggered by our implementation of {@link org.webrtc.CapturerObserver}
 * onCameraSwitchCompleted is triggered by {@link org.webrtc.CameraVideoCapturer.CameraSwitchHandler}
 */
public interface CameraEventListener {
  void onFullyInitialized();
  void onCameraSwitchCompleted(@NonNull CameraState newCameraState);
  void onCameraStopped();
}
