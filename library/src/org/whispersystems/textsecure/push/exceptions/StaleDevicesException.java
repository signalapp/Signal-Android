package org.whispersystems.textsecure.push.exceptions;

import org.whispersystems.textsecure.push.StaleDevices;
import org.whispersystems.textsecure.push.exceptions.NonSuccessfulResponseCodeException;

public class StaleDevicesException extends NonSuccessfulResponseCodeException {

  private final StaleDevices staleDevices;

  public StaleDevicesException(StaleDevices staleDevices) {
    this.staleDevices = staleDevices;
  }

  public StaleDevices getStaleDevices() {
    return staleDevices;
  }
}
