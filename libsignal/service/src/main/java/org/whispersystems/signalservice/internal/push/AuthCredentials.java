package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

import okhttp3.Credentials;

public class AuthCredentials {

  @JsonProperty
  private String username;

  @JsonProperty
  private String password;

  public String asBasic() {
    return Credentials.basic(username, password);
  }
}
