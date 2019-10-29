package org.whispersystems.signalservice.api.profiles;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import org.whispersystems.signalservice.internal.util.JsonUtil;

import java.util.UUID;

public class SignalServiceProfile {

  @JsonProperty
  private String identityKey;

  @JsonProperty
  private String name;

  @JsonProperty
  private String avatar;

  @JsonProperty
  private String unidentifiedAccess;

  @JsonProperty
  private boolean unrestrictedUnidentifiedAccess;

  @JsonProperty
  private Capabilities capabilities;

  @JsonProperty
  private String username;

  @JsonProperty
  @JsonSerialize(using = JsonUtil.UuidSerializer.class)
  @JsonDeserialize(using = JsonUtil.UuidDeserializer.class)
  private UUID uuid;

  public SignalServiceProfile() {}

  public String getIdentityKey() {
    return identityKey;
  }

  public String getName() {
    return name;
  }

  public String getAvatar() {
    return avatar;
  }

  public String getUnidentifiedAccess() {
    return unidentifiedAccess;
  }

  public boolean isUnrestrictedUnidentifiedAccess() {
    return unrestrictedUnidentifiedAccess;
  }

  public Capabilities getCapabilities() {
    return capabilities;
  }

  public String getUsername() {
    return username;
  }

  public UUID getUuid() {
    return uuid;
  }

  public static class Capabilities {
    @JsonProperty
    private boolean uuid;

    public Capabilities() {}

    public boolean isUuid() {
      return uuid;
    }
  }
}
