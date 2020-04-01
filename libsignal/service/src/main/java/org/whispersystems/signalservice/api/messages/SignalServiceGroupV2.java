package org.whispersystems.signalservice.api.messages;

import org.signal.zkgroup.groups.GroupMasterKey;

/**
 * Group information to include in SignalServiceMessages destined to v2 groups.
 * <p>
 * This class represents a "context" that is included with Signal Service messages
 * to make them group messages.
 */
public final class SignalServiceGroupV2 {

  private final GroupMasterKey masterKey;
  private final int            revision;
  private final byte[]         signedGroupChange;

  private SignalServiceGroupV2(Builder builder) {
    this.masterKey         = builder.masterKey;
    this.revision          = builder.revision;
    this.signedGroupChange = builder.signedGroupChange != null ? builder.signedGroupChange.clone() : null;
  }

  public GroupMasterKey getMasterKey() {
    return masterKey;
  }

  public int getRevision() {
    return revision;
  }

  public byte[] getSignedGroupChange() {
    return signedGroupChange;
  }

  public static Builder newBuilder(GroupMasterKey masterKey) {
    return new Builder(masterKey);
  }

  public static class Builder {

    private final GroupMasterKey masterKey;
    private       int            revision;
    private       byte[]         signedGroupChange;

    private Builder(GroupMasterKey masterKey) {
      if (masterKey == null) {
        throw new IllegalArgumentException();
      }
      this.masterKey = masterKey;
    }

    public Builder withRevision(int revision) {
      this.revision = revision;
      return this;
    }

    public Builder withSignedGroupChange(byte[] signedGroupChange) {
      this.signedGroupChange = signedGroupChange;
      return this;
    }

    public SignalServiceGroupV2 build() {
      return new SignalServiceGroupV2(this);
    }
  }
}
