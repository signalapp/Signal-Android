package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

class ConfirmUsernameRequest {
  @JsonProperty
  private String usernameHash;

  @JsonProperty
  private String zkProof;

  ConfirmUsernameRequest(String usernameHash, String zkProof) {
    this.usernameHash = usernameHash;
    this.zkProof      = zkProof;
  }
}
