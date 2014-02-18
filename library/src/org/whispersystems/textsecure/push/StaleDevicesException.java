package org.whispersystems.textsecure.push;

import java.io.IOException;

public class StaleDevicesException extends IOException {

  private final StaleDevices staleDevices;

  public StaleDevicesException(StaleDevices staleDevices) {
    this.staleDevices = staleDevices;
  }

  public StaleDevices getStaleDevices() {
    return staleDevices;
  }
}
