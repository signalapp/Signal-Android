package org.whispersystems.signalservice.api.groupsv2;

import org.signal.zkgroup.profiles.ProfileKeyCredential;

import java.util.UUID;

public final class UuidProfileKeyCredential {

  private final UUID                 uuid;
  private final ProfileKeyCredential profileKeyCredential;

  public UuidProfileKeyCredential(UUID uuid, ProfileKeyCredential profileKeyCredential) {
    this.uuid = uuid;
    this.profileKeyCredential = profileKeyCredential;
  }

  public UUID getUuid() {
    return uuid;
  }

  public ProfileKeyCredential getProfileKeyCredential() {
    return profileKeyCredential;
  }
}
