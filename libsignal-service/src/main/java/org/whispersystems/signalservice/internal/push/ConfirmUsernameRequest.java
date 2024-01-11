package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

class ConfirmUsernameRequest {
  @JsonProperty
  private String usernameHash;

  @JsonProperty
  private String zkProof;

  @JsonProperty
  private String encryptedUsername;

  ConfirmUsernameRequest(String usernameHash, String zkProof, String encryptedUsername) {
    this.usernameHash      = usernameHash;
    this.zkProof           = zkProof;
    this.encryptedUsername = encryptedUsername;
  }
}
