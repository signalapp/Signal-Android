package org.whispersystems.textsecure.push;

import java.io.IOException;

public class MismatchedDevicesException extends IOException {

  private final MismatchedDevices mismatchedDevices;

  public MismatchedDevicesException(MismatchedDevices mismatchedDevices) {
    this.mismatchedDevices = mismatchedDevices;
  }

  public MismatchedDevices getMismatchedDevices() {
    return mismatchedDevices;
  }
}
