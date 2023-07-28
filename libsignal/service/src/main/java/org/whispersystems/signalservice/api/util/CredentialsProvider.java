/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api.util;

import org.whispersystems.signalservice.api.push.ServiceId.ACI;
import org.whispersystems.signalservice.api.push.ServiceId.PNI;

public interface CredentialsProvider {
  ACI getAci();
  PNI getPni();
  String getE164();
  int getDeviceId();
  String getPassword();
}
