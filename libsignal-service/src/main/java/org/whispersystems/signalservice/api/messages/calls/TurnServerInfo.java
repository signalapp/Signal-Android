package org.whispersystems.signalservice.api.messages.calls;


import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class TurnServerInfo {

  @JsonProperty
  private String username;

  @JsonProperty
  private String password;

  @JsonProperty
  private List<String> urls;

  public String getUsername() {
    return username;
  }

  public String getPassword() {
    return password;
  }

  public List<String> getUrls() {
    return urls;
  }
}
