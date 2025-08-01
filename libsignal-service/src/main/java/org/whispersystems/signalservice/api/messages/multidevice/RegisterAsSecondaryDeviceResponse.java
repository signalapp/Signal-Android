package org.whispersystems.signalservice.api.messages.multidevice;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public class RegisterAsSecondaryDeviceResponse {
  @JsonProperty
  private UUID uuid;

  @JsonProperty
  private UUID pni;

  @JsonProperty
  private String deviceId;

  public RegisterAsSecondaryDeviceResponse() {}

  public RegisterAsSecondaryDeviceResponse(UUID uuid, UUID pni, String deviceId) {
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

  public String getDeviceId() {
    return deviceId;
  }
}
