/*
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api.messages.multidevice;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DeviceInfo {

  @JsonProperty
  public int id;

  @JsonProperty
  public String name;

  @JsonProperty
  public long lastSeen;

  @JsonProperty
  public int registrationId;

  @JsonProperty
  public String createdAtCiphertext;

  public DeviceInfo() {}

  public int getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public long getLastSeen() {
    return lastSeen;
  }

  public int getRegistrationId() {
    return registrationId;
  }

  public String getCreatedAtCiphertext() {
    return createdAtCiphertext;
  }
}
