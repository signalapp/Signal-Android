/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api.util;

public interface CredentialsProvider {
  public String getUser();
  public String getPassword();
  public String getSignalingKey();
}
