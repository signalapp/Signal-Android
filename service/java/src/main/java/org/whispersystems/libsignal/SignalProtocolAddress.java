/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */
package org.whispersystems.libsignal;

public class SignalProtocolAddress {

  private final String name;
  private final int    deviceId;

  public SignalProtocolAddress(String name, int deviceId) {
    this.name     = name;
    this.deviceId = deviceId;
  }

  public String getName() {
    return name;
  }

  public int getDeviceId() {
    return deviceId;
  }

  @Override
  public String toString() {
    return name + ":" + deviceId;
  }

  @Override
  public boolean equals(Object other) {
    if (other == null)                       return false;
    if (!(other instanceof SignalProtocolAddress)) return false;

    SignalProtocolAddress that = (SignalProtocolAddress)other;
    return this.name.equals(that.name) && this.deviceId == that.deviceId;
  }

  @Override
  public int hashCode() {
    return this.name.hashCode() ^ this.deviceId;
  }
}
