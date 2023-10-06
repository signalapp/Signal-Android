package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

class ReserveUsernameRequest {
  @JsonProperty
  private List<String> usernameHashes;

  ReserveUsernameRequest(List<String> usernameHashes) {
    this.usernameHashes = Collections.unmodifiableList(usernameHashes);
  }

  List<String> getUsernameHashes() {
    return usernameHashes;
  }
}
