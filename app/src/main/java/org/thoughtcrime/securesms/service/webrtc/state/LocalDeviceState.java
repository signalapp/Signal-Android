package org.thoughtcrime.securesms.service.webrtc.state;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.components.sensors.Orientation;
import org.thoughtcrime.securesms.ringrtc.CameraState;

/**
 * Local device specific state.
 */
public final class LocalDeviceState {
  CameraState cameraState;
  boolean     microphoneEnabled;
  boolean     bluetoothAvailable;
  boolean     wantsBluetooth;
  Orientation orientation;

  LocalDeviceState() {
    this(CameraState.UNKNOWN, true, false, false, Orientation.PORTRAIT_BOTTOM_EDGE);
  }

  LocalDeviceState(@NonNull LocalDeviceState toCopy) {
    this(toCopy.cameraState, toCopy.microphoneEnabled, toCopy.bluetoothAvailable, toCopy.wantsBluetooth, toCopy.orientation);
  }

  LocalDeviceState(@NonNull CameraState cameraState,
                   boolean microphoneEnabled,
                   boolean bluetoothAvailable,
                   boolean wantsBluetooth,
                   @NonNull Orientation orientation)
  {
    this.cameraState        = cameraState;
    this.microphoneEnabled  = microphoneEnabled;
    this.bluetoothAvailable = bluetoothAvailable;
    this.wantsBluetooth     = wantsBluetooth;
    this.orientation        = orientation;
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

  public boolean wantsBluetooth() {
    return wantsBluetooth;
  }

  public @NonNull Orientation getOrientation() {
    return orientation;
  }
}
