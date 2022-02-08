package org.whispersystems.signalservice.api.messages.multidevice;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public class VerifyDeviceResponse {
  @JsonProperty
  private UUID uuid;

  @JsonProperty
  private UUID pni;

  @JsonProperty
  private int deviceId;

  public VerifyDeviceResponse() {}

  public VerifyDeviceResponse(UUID uuid, UUID pni, int deviceId) {
    this.uuid     = uuid;
    this.pni      = pni;
    this.deviceId = deviceId;
  }

  public UUID getUuid() {
    return uuid;
  }

  public UUID getPni() {
    return pni;
  }

  public int getDeviceId() {
    return deviceId;
  }
}
