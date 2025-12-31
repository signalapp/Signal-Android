/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 * <p>
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.whispersystems.signalservice.api.push.SignedPreKeyEntity;

public class PreKeyResponseItem {

  @JsonProperty
  public int deviceId;

  @JsonProperty
  public int registrationId;

  @JsonProperty
  public SignedPreKeyEntity signedPreKey;

  @JsonProperty
  public PreKeyEntity preKey;

  @JsonProperty("pqPreKey")
  public KyberPreKeyEntity kyberPreKey;

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

  public KyberPreKeyEntity getKyberPreKey() {
    return kyberPreKey;
  }

}
