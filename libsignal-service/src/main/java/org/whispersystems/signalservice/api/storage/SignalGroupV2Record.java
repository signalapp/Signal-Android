package org.whispersystems.signalservice.api.storage;

import org.jetbrains.annotations.NotNull;
import org.signal.core.util.ProtoUtil;
import org.signal.libsignal.protocol.logging.Log;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.groups.GroupMasterKey;
import org.whispersystems.signalservice.internal.storage.protos.GroupV2Record;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import okio.ByteString;

public final class SignalGroupV2Record implements SignalRecord<GroupV2Record> {

  private static final String TAG = SignalGroupV2Record.class.getSimpleName();

  private final StorageId     id;
  private final GroupV2Record proto;
  private final byte[]        masterKey;
  private final boolean       hasUnknownFields;

  public SignalGroupV2Record(StorageId id, GroupV2Record proto) {
    this.id               = id;
    this.proto            = proto;
    this.hasUnknownFields = ProtoUtil.hasUnknownFields(proto);
    this.masterKey        = proto.masterKey.toByteArray();
  }

  @Override
  public StorageId getId() {
    return id;
  }

  @Override public GroupV2Record getProto() {
    return proto;
  }

  public boolean hasUnknownFields() {
    return hasUnknownFields;
  }

  public byte[] serializeUnknownFields() {
    return hasUnknownFields ? proto.encode() : null;
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

  public boolean notifyForMentionsWhenMuted() {
    return !proto.dontNotifyForMentionsIfMuted;
  }

  public boolean shouldHideStory() {
    return proto.hideStory;
  }

  public GroupV2Record.StorySendMode getStorySendMode() {
    return proto.storySendMode;
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
        this.builder = new GroupV2Record.Builder();
      }

      builder.masterKey(ByteString.of(masterKey));
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

    public Builder setNotifyForMentionsWhenMuted(boolean value) {
      builder.dontNotifyForMentionsIfMuted(!value);
      return this;
    }

    public Builder setHideStory(boolean hideStory) {
      builder.hideStory(hideStory);
      return this;
    }

    public Builder setStorySendMode(GroupV2Record.StorySendMode storySendMode) {
      builder.storySendMode(storySendMode);
      return this;
    }

    private static GroupV2Record.Builder parseUnknowns(byte[] serializedUnknowns) {
      try {
        return GroupV2Record.ADAPTER.decode(serializedUnknowns).newBuilder();
      } catch (IOException e) {
        Log.w(TAG, "Failed to combine unknown fields!", e);
        return new GroupV2Record.Builder();
      }
    }

    public SignalGroupV2Record build() {
      return new SignalGroupV2Record(id, builder.build());
    }
  }
}
