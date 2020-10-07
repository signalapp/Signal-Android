package org.whispersystems.signalservice.api.storage;

import com.google.protobuf.ByteString;

import org.whispersystems.signalservice.api.util.ProtoUtil;
import org.whispersystems.signalservice.internal.storage.protos.GroupV1Record;

import java.util.Objects;

public final class SignalGroupV1Record implements SignalRecord {

  private final StorageId     id;
  private final GroupV1Record proto;
  private final byte[]        groupId;
  private final boolean       hasUnknownFields;

  public SignalGroupV1Record(StorageId id, GroupV1Record proto) {
    this.id               = id;
    this.proto            = proto;
    this.groupId          = proto.getId().toByteArray();
    this.hasUnknownFields = ProtoUtil.hasUnknownFields(proto);
  }

  @Override
  public StorageId getId() {
    return id;
  }

  public boolean hasUnknownFields() {
    return hasUnknownFields;
  }

  public byte[] serializeUnknownFields() {
    return hasUnknownFields ? proto.toByteArray() : null;
  }

  public byte[] getGroupId() {
    return groupId;
  }

  public boolean isBlocked() {
    return proto.getBlocked();
  }

  public boolean isProfileSharingEnabled() {
    return proto.getWhitelisted();
  }

  public boolean isArchived() {
    return proto.getArchived();
  }

  public boolean isForcedUnread() {
    return proto.getMarkedUnread();
  }

  GroupV1Record toProto() {
    return proto;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SignalGroupV1Record that = (SignalGroupV1Record) o;
    return id.equals(that.id) &&
        proto.equals(that.proto);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, proto);
  }

  public static final class Builder {
    private final StorageId             id;
    private final GroupV1Record.Builder builder;

    private byte[] unknownFields;

    public Builder(byte[] rawId, byte[] groupId) {
      this.id      = StorageId.forGroupV1(rawId);
      this.builder = GroupV1Record.newBuilder();

      builder.setId(ByteString.copyFrom(groupId));
    }

    public Builder setUnknownFields(byte[] serializedUnknowns) {
      this.unknownFields = serializedUnknowns;
      return this;
    }

    public Builder setBlocked(boolean blocked) {
      builder.setBlocked(blocked);
      return this;
    }

    public Builder setProfileSharingEnabled(boolean profileSharingEnabled) {
      builder.setWhitelisted(profileSharingEnabled);
      return this;
    }

    public Builder setArchived(boolean archived) {
      builder.setArchived(archived);
      return this;
    }

    public Builder setForcedUnread(boolean forcedUnread) {
      builder.setMarkedUnread(forcedUnread);
      return this;
    }

    public SignalGroupV1Record build() {
      GroupV1Record proto = builder.build();

      if (unknownFields != null) {
        proto = ProtoUtil.combineWithUnknownFields(proto, unknownFields);
      }

      return new SignalGroupV1Record(id, proto);
    }
  }
}
