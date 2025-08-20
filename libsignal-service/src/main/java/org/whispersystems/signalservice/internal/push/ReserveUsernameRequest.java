package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class ReserveUsernameRequest {
  @JsonProperty
  private List<String> usernameHashes;

  public ReserveUsernameRequest(List<String> usernameHashes) {
    this.usernameHashes = usernameHashes;
  }

  List<String> getUsernameHashes() {
    return usernameHashes;
  }
}
