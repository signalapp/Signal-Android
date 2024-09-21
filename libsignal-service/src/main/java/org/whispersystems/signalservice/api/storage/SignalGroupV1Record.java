package org.whispersystems.signalservice.api.storage;

import org.signal.core.util.ProtoUtil;
import org.signal.libsignal.protocol.logging.Log;
import org.whispersystems.signalservice.internal.storage.protos.GroupV1Record;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import okio.ByteString;

public final class SignalGroupV1Record implements SignalRecord {

  private static final String TAG = SignalGroupV1Record.class.getSimpleName();

  private final StorageId     id;
  private final GroupV1Record proto;
  private final byte[]        groupId;
  private final boolean       hasUnknownFields;

  public SignalGroupV1Record(StorageId id, GroupV1Record proto) {
    this.id               = id;
    this.proto            = proto;
    this.groupId          = proto.id.toByteArray();
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
    return hasUnknownFields ? proto.encode() : null;
  }

  public byte[] getGroupId() {
    return groupId;
  }

  public boolean isBlocked() {
    return proto.blocked;
  }

  public boolean isProfileSharingEnabled() {
    return proto.whitelisted;
  }

  public boolean isArchived() {
    return proto.archived;
  }

  public boolean isForcedUnread() {
    return proto.markedUnread;
  }

  public long getMuteUntil() {
    return proto.mutedUntilTimestamp;
  }

  public GroupV1Record toProto() {
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
        this.builder = new GroupV1Record.Builder();
      }

      builder.id(ByteString.of(groupId));
    }

    public Builder setBlocked(boolean blocked) {
      builder.blocked(blocked);
      return this;
    }

    public Builder setProfileSharingEnabled(boolean profileSharingEnabled) {
      builder.whitelisted(profileSharingEnabled);
      return this;
    }

    public Builder setArchived(boolean archived) {
      builder.archived(archived);
      return this;
    }

    public Builder setForcedUnread(boolean forcedUnread) {
      builder.markedUnread(forcedUnread);
      return this;
    }

    public Builder setMuteUntil(long muteUntil) {
      builder.mutedUntilTimestamp(muteUntil);
      return this;
    }

    private static GroupV1Record.Builder parseUnknowns(byte[] serializedUnknowns) {
      try {
        return GroupV1Record.ADAPTER.decode(serializedUnknowns).newBuilder();
      } catch (IOException e) {
        Log.w(TAG, "Failed to combine unknown fields!", e);
        return new GroupV1Record.Builder();
      }
    }

    public SignalGroupV1Record build() {
      return new SignalGroupV1Record(id, builder.build());
    }
  }
}
