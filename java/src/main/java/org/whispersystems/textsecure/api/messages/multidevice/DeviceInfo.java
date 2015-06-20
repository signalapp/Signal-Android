package org.whispersystems.textsecure.api.messages.multidevice;

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
