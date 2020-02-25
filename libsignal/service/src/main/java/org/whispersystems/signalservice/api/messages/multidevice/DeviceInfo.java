/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api.messages.multidevice;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DeviceInfo {

  @JsonProperty
  private long id;

  @JsonProperty
  private String name;

  @JsonProperty
  private long created;

  @JsonProperty
  private long lastSeen;

  public DeviceInfo() {}

  public long getId() {
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
