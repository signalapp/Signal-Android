package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ConfirmUsernameRequest {
  @JsonProperty
  private String usernameHash;

  @JsonProperty
  private String zkProof;

  @JsonProperty
  private String encryptedUsername;

  public ConfirmUsernameRequest(String usernameHash, String zkProof, String encryptedUsername) {
    this.usernameHash      = usernameHash;
    this.zkProof           = zkProof;
    this.encryptedUsername = encryptedUsername;
  }
}
