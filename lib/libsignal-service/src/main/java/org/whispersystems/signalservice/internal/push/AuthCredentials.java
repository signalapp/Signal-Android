package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

import okhttp3.Credentials;

public class AuthCredentials {

  @JsonProperty
  private String username;

  @JsonProperty
  private String password;

  public static AuthCredentials create(String username, String password) {
    AuthCredentials authCredentials = new AuthCredentials();
    authCredentials.username = username;
    authCredentials.password = password;
    return authCredentials;
  }

  public String asBasic() {
    return Credentials.basic(username, password);
  }

  public String username() { return username; }

  public String password() { return password; }

  @Override
  public String toString() {
    return "AuthCredentials(xxx)";
  }
}
