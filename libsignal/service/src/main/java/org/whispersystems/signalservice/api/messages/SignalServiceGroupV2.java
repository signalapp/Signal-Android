package org.whispersystems.signalservice.api.messages;

import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.groups.GroupMasterKey;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;

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

  /**
   * Creates a context model populated from a protobuf group V2 context.
   */
  public static SignalServiceGroupV2 fromProtobuf(SignalServiceProtos.GroupContextV2 groupContextV2) {
    GroupMasterKey masterKey;
    try {
      masterKey = new GroupMasterKey(groupContextV2.getMasterKey().toByteArray());
    } catch (InvalidInputException e) {
      throw new AssertionError(e);
    }

    Builder builder = newBuilder(masterKey);

    if (groupContextV2.hasGroupChange() && !groupContextV2.getGroupChange().isEmpty()) {
      builder.withSignedGroupChange(groupContextV2.getGroupChange().toByteArray());
    }

    return builder.withRevision(groupContextV2.getRevision())
                  .build();
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

  public boolean hasSignedGroupChange() {
    return signedGroupChange != null && signedGroupChange.length > 0;
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
