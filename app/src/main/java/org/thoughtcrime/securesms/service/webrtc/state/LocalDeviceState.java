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
  boolean     isLandscapeEnabled;
  Orientation deviceOrientation;

  LocalDeviceState() {
    this(CameraState.UNKNOWN, true, false, false, Orientation.PORTRAIT_BOTTOM_EDGE, false, Orientation.PORTRAIT_BOTTOM_EDGE);
  }

  LocalDeviceState(@NonNull LocalDeviceState toCopy) {
    this(toCopy.cameraState, toCopy.microphoneEnabled, toCopy.bluetoothAvailable, toCopy.wantsBluetooth, toCopy.orientation, toCopy.isLandscapeEnabled, toCopy.deviceOrientation);
  }

  LocalDeviceState(@NonNull CameraState cameraState,
                   boolean microphoneEnabled,
                   boolean bluetoothAvailable,
                   boolean wantsBluetooth,
                   @NonNull Orientation orientation,
                   boolean isLandscapeEnabled,
                   @NonNull Orientation deviceOrientation)
  {
    this.cameraState        = cameraState;
    this.microphoneEnabled  = microphoneEnabled;
    this.bluetoothAvailable = bluetoothAvailable;
    this.wantsBluetooth     = wantsBluetooth;
    this.orientation        = orientation;
    this.isLandscapeEnabled = isLandscapeEnabled;
    this.deviceOrientation  = deviceOrientation;
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

  public boolean isLandscapeEnabled() {
    return isLandscapeEnabled;
  }

  public @NonNull Orientation getDeviceOrientation() {
    return deviceOrientation;
  }
}
