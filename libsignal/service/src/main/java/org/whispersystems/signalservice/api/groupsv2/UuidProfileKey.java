package org.whispersystems.signalservice.api.groupsv2;

import org.signal.zkgroup.profiles.ProfileKey;

import java.util.UUID;

public final class UuidProfileKey {

  private final UUID       uuid;
  private final ProfileKey profileKey;

  public UuidProfileKey(UUID uuid, ProfileKey profileKey) {
    this.uuid       = uuid;
    this.profileKey = profileKey;
  }

  public UUID getUuid() {
    return uuid;
  }

  public ProfileKey getProfileKey() {
    return profileKey;
  }
}
