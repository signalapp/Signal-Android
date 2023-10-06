package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ReserveUsernameResponse {
  @JsonProperty
  private String usernameHash;

  ReserveUsernameResponse() {}

  /**
   * Visible for testing.
   */
  public ReserveUsernameResponse(String usernameHash) {
    this.usernameHash = usernameHash;
  }

  public String getUsernameHash() {
    return usernameHash;
  }
}
