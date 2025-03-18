/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api.util;

import org.whispersystems.signalservice.api.push.ServiceId.ACI;
import org.whispersystems.signalservice.api.push.ServiceId.PNI;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

public interface CredentialsProvider {
  ACI getAci();
  PNI getPni();
  String getE164();
  int getDeviceId();
  String getPassword();

  default boolean isInvalid() {
    return (getAci() == null && getE164() == null) || getPassword() == null;
  }

  default String getUsername() {
    StringBuilder sb = new StringBuilder();
    sb.append(getAci().toString());
    if (getDeviceId() != SignalServiceAddress.DEFAULT_DEVICE_ID) {
      sb.append(".");
      sb.append(getDeviceId());
    }
    return sb.toString();
  }
}
