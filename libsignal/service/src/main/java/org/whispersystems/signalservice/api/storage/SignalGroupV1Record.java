package org.whispersystems.signalservice.api.storage;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.whispersystems.libsignal.logging.Log;
import org.whispersystems.signalservice.api.util.ProtoUtil;
import org.whispersystems.signalservice.internal.storage.protos.GroupV1Record;
import org.whispersystems.signalservice.internal.storage.protos.GroupV2Record;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public final class SignalGroupV1Record implements SignalRecord {

  private static final String TAG = SignalGroupV1Record.class.getSimpleName();

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

  @Override
  public SignalStorageRecord asStorageRecord() {
    return SignalStorageRecord.forGroupV1(this);
  }

  @Override
  public String describeDiff(SignalRecord other) {
    if (other instanceof SignalGroupV1Record) {
      SignalGroupV1Record that = (SignalGroupV1Record) other;
      List<String>        diff = new LinkedList<>();

      if (!Arrays.equals(this.id.getRaw(), that.id.getRaw())) {
        diff.add("ID");
      }

      if (!Arrays.equals(this.groupId, that.groupId)) {
        diff.add("MasterKey");
      }

      if (!Objects.equals(this.isBlocked(), that.isBlocked())) {
        diff.add("Blocked");
      }

      if (!Objects.equals(this.isProfileSharingEnabled(), that.isProfileSharingEnabled())) {
        diff.add("ProfileSharing");
      }

      if (!Objects.equals(this.isArchived(), that.isArchived())) {
        diff.add("Archived");
      }

      if (!Objects.equals(this.isForcedUnread(), that.isForcedUnread())) {
        diff.add("ForcedUnread");
      }

      if (!Objects.equals(this.getMuteUntil(), that.getMuteUntil())) {
        diff.add("MuteUntil");
      }

      if (!Objects.equals(this.hasUnknownFields(), that.hasUnknownFields())) {
        diff.add("UnknownFields");
      }

      return diff.toString();
    } else {
      return "Different class. " + getClass().getSimpleName() + " | " + other.getClass().getSimpleName();
    }
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

  public long getMuteUntil() {
    return proto.getMutedUntilTimestamp();
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

    public Builder(byte[] rawId, byte[] groupId, byte[] serializedUnknowns) {
      this.id = StorageId.forGroupV1(rawId);

      if (serializedUnknowns != null) {
        this.builder = parseUnknowns(serializedUnknowns);
      } else {
        this.builder = GroupV1Record.newBuilder();
      }

      builder.setId(ByteString.copyFrom(groupId));
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

    public Builder setMuteUntil(long muteUntil) {
      builder.setMutedUntilTimestamp(muteUntil);
      return this;
    }

    private static GroupV1Record.Builder parseUnknowns(byte[] serializedUnknowns) {
      try {
        return GroupV1Record.parseFrom(serializedUnknowns).toBuilder();
      } catch (InvalidProtocolBufferException e) {
        Log.w(TAG, "Failed to combine unknown fields!", e);
        return GroupV1Record.newBuilder();
      }
    }

    public SignalGroupV1Record build() {
      return new SignalGroupV1Record(id, builder.build());
    }
  }
}
