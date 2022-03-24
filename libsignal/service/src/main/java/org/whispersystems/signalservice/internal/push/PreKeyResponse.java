/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.signalservice.internal.util.JsonUtil;

import java.util.List;

public class PreKeyResponse {

  @JsonProperty
  @JsonSerialize(using = JsonUtil.IdentityKeySerializer.class)
  @JsonDeserialize(using = JsonUtil.IdentityKeyDeserializer.class)
  private IdentityKey identityKey;

  @JsonProperty
  private List<PreKeyResponseItem> devices;

  public IdentityKey getIdentityKey() {
    return identityKey;
  }

  public List<PreKeyResponseItem> getDevices() {
    return devices;
  }


}
