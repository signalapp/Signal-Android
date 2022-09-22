package org.whispersystems.signalservice.api.storage;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.signal.libsignal.protocol.logging.Log;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.groups.GroupMasterKey;
import org.whispersystems.signalservice.api.util.ProtoUtil;
import org.whispersystems.signalservice.internal.storage.protos.GroupV2Record;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public final class SignalGroupV2Record implements SignalRecord {

  private static final String TAG = SignalGroupV2Record.class.getSimpleName();

  private final StorageId     id;
  private final GroupV2Record proto;
  private final byte[]        masterKey;
  private final boolean       hasUnknownFields;

  public SignalGroupV2Record(StorageId id, GroupV2Record proto) {
    this.id               = id;
    this.proto            = proto;
    this.hasUnknownFields = ProtoUtil.hasUnknownFields(proto);
    this.masterKey        = proto.getMasterKey().toByteArray();
  }

  @Override
  public StorageId getId() {
    return id;
  }

  @Override
  public SignalStorageRecord asStorageRecord() {
    return SignalStorageRecord.forGroupV2(this);
  }

  @Override
  public String describeDiff(SignalRecord other) {
    if (other instanceof SignalGroupV2Record) {
      SignalGroupV2Record that = (SignalGroupV2Record) other;
      List<String>        diff = new LinkedList<>();

      if (!Arrays.equals(this.id.getRaw(), that.id.getRaw())) {
        diff.add("ID");
      }

      if (!Arrays.equals(this.getMasterKeyBytes(), that.getMasterKeyBytes())) {
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

      if (!Objects.equals(this.notifyForMentionsWhenMuted(), that.notifyForMentionsWhenMuted())) {
        diff.add("NotifyForMentionsWhenMuted");
      }

      if (shouldHideStory() != that.shouldHideStory()) {
        diff.add("HideStory");
      }

      if (!Objects.equals(this.getStorySendMode(), that.getStorySendMode())) {
        diff.add("StorySendMode");
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

  public byte[] getMasterKeyBytes() {
    return masterKey;
  }

  public GroupMasterKey getMasterKeyOrThrow() {
    try {
      return new GroupMasterKey(masterKey);
    } catch (InvalidInputException e) {
      throw new AssertionError(e);
    }
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

  public boolean notifyForMentionsWhenMuted() {
    return !proto.getDontNotifyForMentionsIfMuted();
  }

  public boolean shouldHideStory() {
    return proto.getHideStory();
  }

  public GroupV2Record.StorySendMode getStorySendMode() {
    return proto.getStorySendMode();
  }

  public GroupV2Record toProto() {
    return proto;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SignalGroupV2Record that = (SignalGroupV2Record) o;
    return id.equals(that.id) &&
        proto.equals(that.proto);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, proto);
  }

  public static final class Builder {
    private final StorageId             id;
    private final GroupV2Record.Builder builder;

    public Builder(byte[] rawId, GroupMasterKey masterKey, byte[] serializedUnknowns) {
      this(rawId, masterKey.serialize(), serializedUnknowns);
    }

    public Builder(byte[] rawId, byte[] masterKey, byte[] serializedUnknowns) {
      this.id = StorageId.forGroupV2(rawId);

      if (serializedUnknowns != null) {
        this.builder = parseUnknowns(serializedUnknowns);
      } else {
        this.builder = GroupV2Record.newBuilder();
      }

      builder.setMasterKey(ByteString.copyFrom(masterKey));
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

    public Builder setNotifyForMentionsWhenMuted(boolean value) {
      builder.setDontNotifyForMentionsIfMuted(!value);
      return this;
    }

    public Builder setHideStory(boolean hideStory) {
      builder.setHideStory(hideStory);
      return this;
    }

    public Builder setStorySendMode(GroupV2Record.StorySendMode storySendMode) {
      builder.setStorySendMode(storySendMode);
      return this;
    }

    private static GroupV2Record.Builder parseUnknowns(byte[] serializedUnknowns) {
      try {
        return GroupV2Record.parseFrom(serializedUnknowns).toBuilder();
      } catch (InvalidProtocolBufferException e) {
        Log.w(TAG, "Failed to combine unknown fields!", e);
        return GroupV2Record.newBuilder();
      }
    }

    public SignalGroupV2Record build() {
      return new SignalGroupV2Record(id, builder.build());
    }
  }
}
