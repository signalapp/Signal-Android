package org.whispersystems.signalservice.api.groupsv2;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TemporalCredential {

  @JsonProperty
  private byte[] credential;

  @JsonProperty
  private long redemptionTime;

  public byte[] getCredential() {
    return credential;
  }

  public long getRedemptionTime() {
    return redemptionTime;
  }
}
