/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.whispersystems.signalservice.api.push.SignedPreKeyEntity;

public class PreKeyResponseItem {

  @JsonProperty
  private int                deviceId;

  @JsonProperty
  private int                registrationId;

  @JsonProperty
  private SignedPreKeyEntity signedPreKey;

  @JsonProperty
  private PreKeyEntity       preKey;

  public int getDeviceId() {
    return deviceId;
  }

  public int getRegistrationId() {
    return registrationId;
  }

  public SignedPreKeyEntity getSignedPreKey() {
    return signedPreKey;
  }

  public PreKeyEntity getPreKey() {
    return preKey;
  }

}
