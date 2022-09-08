package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

class SetUsernameRequest {
  @JsonProperty
  private String nickname;

  @JsonProperty
  private String existingUsername;

  SetUsernameRequest(String nickname, String existingUsername) {
    this.nickname         = nickname;
    this.existingUsername = existingUsername;
  }

  String getNickname() {
    return nickname;
  }

  String getExistingUsername() {
    return existingUsername;
  }
}
