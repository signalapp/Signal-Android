package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

public class ReserveUsernameRequest {
  @JsonProperty
  private List<String> usernameHashes;

  public ReserveUsernameRequest(List<String> usernameHashes) {
    this.usernameHashes = Collections.unmodifiableList(usernameHashes);
  }

  List<String> getUsernameHashes() {
    return usernameHashes;
  }
}
