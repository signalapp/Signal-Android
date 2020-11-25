package org.session.libsignal.service.internal.push;

import org.session.libsignal.service.api.push.exceptions.NonSuccessfulResponseCodeException;

public class DeviceLimitExceededException extends NonSuccessfulResponseCodeException {

  private final DeviceLimit deviceLimit;

  public DeviceLimitExceededException(DeviceLimit deviceLimit) {
    this.deviceLimit = deviceLimit;
  }

  public int getCurrent() {
    return deviceLimit.getCurrent();
  }

  public int getMax() {
    return deviceLimit.getMax();
  }
}
