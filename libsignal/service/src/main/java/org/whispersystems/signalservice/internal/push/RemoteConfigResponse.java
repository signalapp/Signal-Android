package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class RemoteConfigResponse {
  @JsonProperty
  private List<Config> config;

  public List<Config> getConfig() {
    return config;
  }

  public static class Config {
    @JsonProperty
    private String name;

    @JsonProperty
    private boolean enabled;

    public String getName() {
      return name;
    }

    public boolean isEnabled() {
      return enabled;
    }
  }
}
