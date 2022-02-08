/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api.util;

import org.whispersystems.signalservice.api.push.ACI;

public interface CredentialsProvider {
  ACI getAci();
  String getE164();
  int getDeviceId();
  String getPassword();
}
