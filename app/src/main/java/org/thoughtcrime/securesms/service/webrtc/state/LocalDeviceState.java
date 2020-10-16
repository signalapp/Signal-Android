package org.thoughtcrime.securesms.service.webrtc.state;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.ringrtc.CameraState;

/**
 * Local device specific state.
 */
public final class LocalDeviceState {
  CameraState cameraState;
  boolean     microphoneEnabled;
  boolean     bluetoothAvailable;

  LocalDeviceState() {
    this(CameraState.UNKNOWN, true, false);
  }

  LocalDeviceState(@NonNull LocalDeviceState toCopy) {
    this(toCopy.cameraState, toCopy.microphoneEnabled, toCopy.bluetoothAvailable);
  }

  LocalDeviceState(@NonNull CameraState cameraState, boolean microphoneEnabled, boolean bluetoothAvailable) {
    this.cameraState        = cameraState;
    this.microphoneEnabled  = microphoneEnabled;
    this.bluetoothAvailable = bluetoothAvailable;
  }

  public @NonNull CameraState getCameraState() {
    return cameraState;
  }

  public boolean isMicrophoneEnabled() {
    return microphoneEnabled;
  }

  public boolean isBluetoothAvailable() {
    return bluetoothAvailable;
  }
}
