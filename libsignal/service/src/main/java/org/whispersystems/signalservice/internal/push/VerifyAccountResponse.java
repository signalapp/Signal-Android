package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class VerifyAccountResponse {
  @JsonProperty
  public String uuid;

  @JsonProperty
  public String pni;

  @JsonProperty
  public boolean storageCapable;

  @JsonProperty
  public String number;

  @JsonCreator
  public VerifyAccountResponse() {}

  public VerifyAccountResponse(String uuid, String pni, boolean storageCapable) {
    this.uuid           = uuid;
    this.pni            = pni;
    this.storageCapable = storageCapable;
  }

  public String getUuid() {
    return uuid;
  }

  public boolean isStorageCapable() {
    return storageCapable;
  }

  public String getPni() {
    return pni;
  }
}
