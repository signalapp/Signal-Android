package org.whispersystems.signalservice.api.messages.calls;


import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

public class TurnServerInfo {

  @JsonProperty
  private String username;

  @JsonProperty
  private String password;

  @JsonProperty
  private String hostname;

  @JsonProperty
  private List<String> urls;

  @JsonProperty
  private List<String> urlsWithIps;

  @JsonProperty
  private List<TurnServerInfo> iceServers;

  public String getUsername() {
    return username;
  }

  public String getPassword() {
    return password;
  }

  // Hostname for the ips in urlsWithIps
  public String getHostname() {
    return hostname;
  }

  public List<String> getUrls() {
    return urls;
  }

  public List<String> getUrlsWithIps() {
    return urlsWithIps;
  }

  public List<TurnServerInfo> getIceServers() {
    return (iceServers != null) ? iceServers : Collections.emptyList();
  }
}
