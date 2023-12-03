package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class RemoteConfigResponse {
  @JsonProperty
  private List<Config> config;

  @JsonProperty
  private long serverEpochTime;

  public List<Config> getConfig() {
    return config;
  }

  public long getServerEpochTime() {
    return serverEpochTime;
  }

  public static class Config {
    @JsonProperty
    private String name;

    @JsonProperty
    private boolean enabled;

    @JsonProperty
    private String value;

    public String getName() {
      return name;
    }

    public boolean isEnabled() {
      return enabled;
    }

    public String getValue() {
      return value;
    }
  }
}
