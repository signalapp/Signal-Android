package org.whispersystems.signalservice.api.storage;

import com.google.protobuf.ByteString;

import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.OptionalUtil;
import org.whispersystems.signalservice.api.util.ProtoUtil;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.signalservice.internal.storage.protos.ContactRecord;
import org.whispersystems.signalservice.internal.storage.protos.ContactRecord.IdentityState;

import java.util.Objects;

public final class SignalContactRecord implements SignalRecord {

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

    this.address     = new SignalServiceAddress(UuidUtil.parseOrNull(proto.getServiceUuid()), proto.getServiceE164());
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

    private byte[] unknownFields;

    public Builder(byte[] rawId, SignalServiceAddress address) {
      this.id      = StorageId.forContact(rawId);
      this.builder = ContactRecord.newBuilder();

      builder.setServiceUuid(address.getUuid().isPresent() ? address.getUuid().get().toString() : "");
      builder.setServiceE164(address.getNumber().or(""));
    }

    public Builder setUnknownFields(byte[] serializedUnknowns) {
      this.unknownFields = serializedUnknowns;
      return this;
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

    public SignalContactRecord build() {
      ContactRecord proto = builder.build();

      if (unknownFields != null) {
        proto = ProtoUtil.combineWithUnknownFields(proto, unknownFields);
      }

      return new SignalContactRecord(id, proto);
    }
  }
}
