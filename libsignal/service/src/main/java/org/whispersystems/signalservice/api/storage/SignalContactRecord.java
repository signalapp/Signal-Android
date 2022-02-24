package org.whispersystems.signalservice.api.storage;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.whispersystems.libsignal.logging.Log;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.OptionalUtil;
import org.whispersystems.signalservice.api.util.ProtoUtil;
import org.whispersystems.signalservice.internal.storage.protos.ContactRecord;
import org.whispersystems.signalservice.internal.storage.protos.ContactRecord.IdentityState;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public final class SignalContactRecord implements SignalRecord {

  private static final String TAG = SignalContactRecord.class.getSimpleName();

  private final StorageId     id;
  private final ContactRecord proto;
  private final boolean       hasUnknownFields;

  private final SignalServiceAddress address;
  private final Optional<String>     givenName;
  private final Optional<String>     familyName;
  private final Optional<byte[]>     profileKey;
  private final Optional<String>     username;
  private final Optional<byte[]>     identityKey;

  public SignalContactRecord(StorageId id, ContactRecord proto) {
    this.id               = id;
    this.proto            = proto;
    this.hasUnknownFields = ProtoUtil.hasUnknownFields(proto);

    this.address     = new SignalServiceAddress(ServiceId.parseOrUnknown(proto.getServiceUuid()), proto.getServiceE164());
    this.givenName   = OptionalUtil.absentIfEmpty(proto.getGivenName());
    this.familyName  = OptionalUtil.absentIfEmpty(proto.getFamilyName());
    this.profileKey  = OptionalUtil.absentIfEmpty(proto.getProfileKey());
    this.username    = OptionalUtil.absentIfEmpty(proto.getUsername());
    this.identityKey = OptionalUtil.absentIfEmpty(proto.getIdentityKey());
  }

  @Override
  public StorageId getId() {
    return id;
  }

  @Override
  public SignalStorageRecord asStorageRecord() {
    return SignalStorageRecord.forContact(this);
  }

  @Override
  public String describeDiff(SignalRecord other) {
    if (other instanceof SignalContactRecord) {
      SignalContactRecord that = (SignalContactRecord) other;
      List<String>        diff = new LinkedList<>();

      if (!Arrays.equals(this.id.getRaw(), that.id.getRaw())) {
        diff.add("ID");
      }

      if (!Objects.equals(this.getAddress().getNumber(), that.getAddress().getNumber())) {
        diff.add("E164");
      }

      if (!Objects.equals(this.getAddress().getServiceId(), that.getAddress().getServiceId())) {
        diff.add("UUID");
      }

      if (!Objects.equals(this.givenName, that.givenName)) {
        diff.add("GivenName");
      }

      if (!Objects.equals(this.familyName, that.familyName)) {
        diff.add("FamilyName");
      }

      if (!OptionalUtil.byteArrayEquals(this.profileKey, that.profileKey)) {
        diff.add("ProfileKey");
      }

      if (!Objects.equals(this.username, that.username)) {
        diff.add("Username");
      }

      if (!OptionalUtil.byteArrayEquals(this.identityKey, that.identityKey)) {
        diff.add("IdentityKey");
      }

      if (!Objects.equals(this.getIdentityState(), that.getIdentityState())) {
        diff.add("IdentityState");
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

      if (shouldHideStory() != that.shouldHideStory()) {
        diff.add("HideStory");
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

  public SignalServiceAddress getAddress() {
    return address;
  }

  public Optional<String> getGivenName() {
    return givenName;
  }

  public Optional<String> getFamilyName() {
    return familyName;
  }

  public Optional<byte[]> getProfileKey() {
    return profileKey;
  }

  public Optional<String> getUsername() {
    return username;
  }

  public Optional<byte[]> getIdentityKey() {
    return identityKey;
  }

  public IdentityState getIdentityState() {
    return proto.getIdentityState();
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

  public boolean shouldHideStory() {
    return proto.getHideStory();
  }

  ContactRecord toProto() {
    return proto;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SignalContactRecord that = (SignalContactRecord) o;
    return id.equals(that.id) &&
        proto.equals(that.proto);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, proto);
  }

  public static final class Builder {
    private final StorageId             id;
    private final ContactRecord.Builder builder;

    public Builder(byte[] rawId, SignalServiceAddress address, byte[] serializedUnknowns) {
      this.id = StorageId.forContact(rawId);

      if (serializedUnknowns != null) {
        this.builder = parseUnknowns(serializedUnknowns);
      } else {
        this.builder = ContactRecord.newBuilder();
      }

      builder.setServiceUuid(address.getServiceId().toString());
      builder.setServiceE164(address.getNumber().or(""));
    }

    public Builder setGivenName(String givenName) {
      builder.setGivenName(givenName == null ? "" : givenName);
      return this;
    }

    public Builder setFamilyName(String familyName) {
      builder.setFamilyName(familyName == null ? "" : familyName);
      return this;
    }

    public Builder setProfileKey(byte[] profileKey) {
      builder.setProfileKey(profileKey == null ? ByteString.EMPTY : ByteString.copyFrom(profileKey));
      return this;
    }

    public Builder setUsername(String username) {
      builder.setUsername(username == null ? "" : username);
      return this;
    }

    public Builder setIdentityKey(byte[] identityKey) {
      builder.setIdentityKey(identityKey == null ? ByteString.EMPTY : ByteString.copyFrom(identityKey));
      return this;
    }

    public Builder setIdentityState(IdentityState identityState) {
      builder.setIdentityState(identityState == null ? IdentityState.DEFAULT : identityState);
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

    public Builder setMuteUntil(long muteUntil) {
      builder.setMutedUntilTimestamp(muteUntil);
      return this;
    }

    public Builder setHideStory(boolean hideStory) {
      builder.setHideStory(hideStory);
      return this;
    }

    private static ContactRecord.Builder parseUnknowns(byte[] serializedUnknowns) {
      try {
        return ContactRecord.parseFrom(serializedUnknowns).toBuilder();
      } catch (InvalidProtocolBufferException e) {
        Log.w(TAG, "Failed to combine unknown fields!", e);
        return ContactRecord.newBuilder();
      }
    }

    public SignalContactRecord build() {
      return new SignalContactRecord(id, builder.build());
    }
  }
}
