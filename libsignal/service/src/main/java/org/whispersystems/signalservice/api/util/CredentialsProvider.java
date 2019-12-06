/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api.util;

import java.util.UUID;

public interface CredentialsProvider {
  public UUID getUuid();
  public String getE164();
  public String getPassword();
  public String getSignalingKey();
}
