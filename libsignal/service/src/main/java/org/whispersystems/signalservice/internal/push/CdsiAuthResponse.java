package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CdsiAuthResponse {

  @JsonProperty
  private String username;

  @JsonProperty
  private String password;

  public String getUsername() {
    return username;
  }

  public String getPassword() {
    return password;
  }
}
