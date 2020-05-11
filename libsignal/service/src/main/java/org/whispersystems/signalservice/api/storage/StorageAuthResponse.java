package org.whispersystems.signalservice.api.storage;

import com.fasterxml.jackson.annotation.JsonProperty;

public class StorageAuthResponse {

  @JsonProperty
  private String username;

  @JsonProperty
  private String password;

  public StorageAuthResponse() { }

  public StorageAuthResponse(String username, String password) {
    this.username = username;
    this.password = password;
  }

  public String getUsername() {
    return username;
  }

  public String getPassword() {
    return password;
  }
}
