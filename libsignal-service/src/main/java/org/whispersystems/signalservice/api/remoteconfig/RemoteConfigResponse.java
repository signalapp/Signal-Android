/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.remoteconfig;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

import javax.annotation.Nullable;

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

    @JsonProperty
    private String value;

    public String getName() {
      return name;
    }

    public boolean isEnabled() {
      return enabled;
    }

    public @Nullable String getValue() {
      return value;
    }
  }
}
