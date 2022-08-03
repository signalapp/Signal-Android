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
  public long created;

  @JsonProperty
  public long lastSeen;

  public DeviceInfo() {}

  public int getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public long getCreated() {
    return created;
  }

  public long getLastSeen() {
    return lastSeen;
  }
}
