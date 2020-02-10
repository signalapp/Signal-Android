package org.whispersystems.signalservice.api.storage;

import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.OptionalUtil;

import java.util.Arrays;
import java.util.Objects;

public final class SignalGroupV1Record implements SignalRecord {

  private final byte[]  key;
  private final byte[]  groupId;
  private final boolean blocked;
  private final boolean profileSharingEnabled;

  private SignalGroupV1Record(byte[] key, byte[] groupId, boolean blocked, boolean profileSharingEnabled) {
    this.key                   = key;
    this.groupId               = groupId;
    this.blocked               = blocked;
    this.profileSharingEnabled = profileSharingEnabled;
  }

  @Override
  public byte[] getKey() {
    return key;
  }

  public byte[] getGroupId() {
    return groupId;
  }

  public boolean isBlocked() {
    return blocked;
  }

  public boolean isProfileSharingEnabled() {
    return profileSharingEnabled;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SignalGroupV1Record that = (SignalGroupV1Record) o;
    return blocked == that.blocked &&
        profileSharingEnabled == that.profileSharingEnabled &&
        Arrays.equals(key, that.key) &&
        Arrays.equals(groupId, that.groupId);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(blocked, profileSharingEnabled);
    result = 31 * result + Arrays.hashCode(key);
    result = 31 * result + Arrays.hashCode(groupId);
    return result;
  }

  public static final class Builder {
    private final byte[]  key;
    private final byte[]  groupId;
    private       boolean blocked;
    private       boolean profileSharingEnabled;

    public Builder(byte[] key, byte[] groupId) {
      this.key     = key;
      this.groupId = groupId;
    }

    public Builder setBlocked(boolean blocked) {
      this.blocked = blocked;
      return this;
    }

    public Builder setProfileSharingEnabled(boolean profileSharingEnabled) {
      this.profileSharingEnabled = profileSharingEnabled;
      return this;
    }

    public SignalGroupV1Record build() {
      return new SignalGroupV1Record(key, groupId, blocked, profileSharingEnabled);
    }
  }
}
