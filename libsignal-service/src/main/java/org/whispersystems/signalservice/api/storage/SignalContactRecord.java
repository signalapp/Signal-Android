package org.whispersystems.signalservice.api.storage;

import org.signal.core.util.ProtoUtil;
import org.signal.libsignal.protocol.logging.Log;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.ServiceId.ACI;
import org.whispersystems.signalservice.api.push.ServiceId.PNI;
import org.whispersystems.signalservice.api.util.OptionalUtil;
import org.whispersystems.signalservice.internal.storage.protos.ContactRecord;
import org.whispersystems.signalservice.internal.storage.protos.ContactRecord.IdentityState;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;

import okio.ByteString;

public final class SignalContactRecord implements SignalRecord {

  private static final String TAG = SignalContactRecord.class.getSimpleName();

  private final StorageId     id;
  private final ContactRecord proto;
  private final boolean       hasUnknownFields;

  private final Optional<ACI>    aci;
  private final Optional<PNI>    pni;
  private final Optional<String> e164;
  private final Optional<String> profileGivenName;
  private final Optional<String> profileFamilyName;
  private final Optional<String> systemGivenName;
  private final Optional<String> systemFamilyName;
  private final Optional<String> systemNickname;
  private final Optional<byte[]> profileKey;
  private final Optional<String> username;
  private final Optional<byte[]> identityKey;
  private final Optional<String> nicknameGivenName;
  private final Optional<String> nicknameFamilyName;
  private final Optional<String> note;

  public SignalContactRecord(StorageId id, ContactRecord proto) {
    this.id                 = id;
    this.proto              = proto;
    this.hasUnknownFields   = ProtoUtil.hasUnknownFields(proto);
    this.aci                = OptionalUtil.absentIfEmpty(proto.aci).map(ACI::parseOrNull).map(it -> it.isUnknown() ? null : it);
    this.pni                = OptionalUtil.absentIfEmpty(proto.pni).map(PNI::parseOrNull).map(it -> it.isUnknown() ? null : it);
    this.e164               = OptionalUtil.absentIfEmpty(proto.e164);
    this.profileGivenName   = OptionalUtil.absentIfEmpty(proto.givenName);
    this.profileFamilyName  = OptionalUtil.absentIfEmpty(proto.familyName);
    this.systemGivenName    = OptionalUtil.absentIfEmpty(proto.systemGivenName);
    this.systemFamilyName   = OptionalUtil.absentIfEmpty(proto.systemFamilyName);
    this.systemNickname     = OptionalUtil.absentIfEmpty(proto.systemNickname);
    this.profileKey         = OptionalUtil.absentIfEmpty(proto.profileKey);
    this.username           = OptionalUtil.absentIfEmpty(proto.username);
    this.identityKey        = OptionalUtil.absentIfEmpty(proto.identityKey);
    this.nicknameGivenName  = Optional.ofNullable(proto.nickname).flatMap(n -> OptionalUtil.absentIfEmpty(n.given));
    this.nicknameFamilyName = Optional.ofNullable(proto.nickname).flatMap(n -> OptionalUtil.absentIfEmpty(n.family));
    this.note               = OptionalUtil.absentIfEmpty(proto.note);
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

      if (!Objects.equals(this.getAci(), that.getAci())) {
        diff.add("ACI");
      }

      if (!Objects.equals(this.getPni(), that.getPni())) {
        diff.add("PNI");
      }

      if (!Objects.equals(this.getNumber(), that.getNumber())) {
        diff.add("E164");
      }

      if (!Objects.equals(this.profileGivenName, that.profileGivenName)) {
        diff.add("ProfileGivenName");
      }

      if (!Objects.equals(this.profileFamilyName, that.profileFamilyName)) {
        diff.add("ProfileFamilyName");
      }

      if (!Objects.equals(this.systemGivenName, that.systemGivenName)) {
        diff.add("SystemGivenName");
      }

      if (!Objects.equals(this.systemFamilyName, that.systemFamilyName)) {
        diff.add("SystemFamilyName");
      }

      if (!Objects.equals(this.systemNickname, that.systemNickname)) {
        diff.add("SystemNickname");
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

      if (getUnregisteredTimestamp() != that.getUnregisteredTimestamp()) {
        diff.add("UnregisteredTimestamp");
      }

      if (isHidden() != that.isHidden()) {
        diff.add("Hidden");
      }

      if (isPniSignatureVerified() != that.isPniSignatureVerified()) {
        diff.add("PniSignatureVerified");
      }

      if (!Objects.equals(this.hasUnknownFields(), that.hasUnknownFields())) {
        diff.add("UnknownFields");
      }

      if (!Objects.equals(this.nicknameGivenName, that.nicknameGivenName)) {
        diff.add("NicknameGivenName");
      }

      if (!Objects.equals(this.nicknameFamilyName, that.nicknameFamilyName)) {
        diff.add("NicknameFamilyName");
      }

      if (!Objects.equals(this.note, that.note)) {
        diff.add("Note");
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

  public Optional<ACI> getAci() {
    return aci;
  }

  public Optional<PNI> getPni() {
    return pni;
  }

  public Optional<? extends ServiceId> getServiceId() {
    if (aci.isPresent()) {
      return aci;
    } else if (pni.isPresent()) {
      return pni;
    } else {
      return Optional.empty();
    }
  }

  public Optional<String> getNumber() {
    return e164;
  }

  public Optional<String> getProfileGivenName() {
    return profileGivenName;
  }

  public Optional<String> getProfileFamilyName() {
    return profileFamilyName;
  }

  public Optional<String> getSystemGivenName() {
    return systemGivenName;
  }

  public Optional<String> getSystemFamilyName() {
    return systemFamilyName;
  }

  public Optional<String> getSystemNickname() {
    return systemNickname;
  }

  public Optional<String> getNicknameGivenName() {
    return nicknameGivenName;
  }

  public Optional<String> getNicknameFamilyName() {
    return nicknameFamilyName;
  }

  public Optional<String> getNote() {
    return note;
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
    return proto.identityState;
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

  public boolean shouldHideStory() {
    return proto.hideStory;
  }

  public long getUnregisteredTimestamp() {
    return proto.unregisteredAtTimestamp;
  }

  public boolean isHidden() {
    return proto.hidden;
  }

  public boolean isPniSignatureVerified() {
    return proto.pniSignatureVerified;
  }

  /**
   * Returns the same record, but stripped of the PNI field. Only used while PNP is in development.
   */
  public SignalContactRecord withoutPni() {
    return new SignalContactRecord(id, proto.newBuilder().pni("").build());
  }

  public ContactRecord toProto() {
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

    public Builder(byte[] rawId, @Nullable ACI aci, byte[] serializedUnknowns) {
      this.id = StorageId.forContact(rawId);

      if (serializedUnknowns != null) {
        this.builder = parseUnknowns(serializedUnknowns);
      } else {
        this.builder = new ContactRecord.Builder();
      }

      builder.aci(aci == null ? "" : aci.toString());
    }

    public Builder setE164(String e164) {
      builder.e164(e164 == null ? "" : e164);
      return this;
    }

    public Builder setPni(PNI pni) {
      builder.pni(pni == null ? "" : pni.toStringWithoutPrefix());
      return this;
    }

    public Builder setProfileGivenName(String givenName) {
      builder.givenName(givenName == null ? "" : givenName);
      return this;
    }

    public Builder setProfileFamilyName(String familyName) {
      builder.familyName(familyName == null ? "" : familyName);
      return this;
    }

    public Builder setSystemGivenName(String givenName) {
      builder.systemGivenName(givenName == null ? "" : givenName);
      return this;
    }

    public Builder setSystemFamilyName(String familyName) {
      builder.systemFamilyName(familyName == null ? "" : familyName);
      return this;
    }

    public Builder setSystemNickname(String nickname) {
      builder.systemNickname(nickname == null ? "" : nickname);
      return this;
    }

    public Builder setProfileKey(byte[] profileKey) {
      builder.profileKey(profileKey == null ? ByteString.EMPTY : ByteString.of(profileKey));
      return this;
    }

    public Builder setUsername(String username) {
      builder.username(username == null ? "" : username);
      return this;
    }

    public Builder setIdentityKey(byte[] identityKey) {
      builder.identityKey(identityKey == null ? ByteString.EMPTY : ByteString.of(identityKey));
      return this;
    }

    public Builder setIdentityState(IdentityState identityState) {
      builder.identityState(identityState == null ? IdentityState.DEFAULT : identityState);
      return this;
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

    public Builder setHideStory(boolean hideStory) {
      builder.hideStory(hideStory);
      return this;
    }

    public Builder setUnregisteredTimestamp(long timestamp) {
      builder.unregisteredAtTimestamp(timestamp);
      return this;
    }

    public Builder setHidden(boolean hidden) {
      builder.hidden(hidden);
      return this;
    }

    public Builder setPniSignatureVerified(boolean verified) {
      builder.pniSignatureVerified(verified);
      return this;
    }

    public Builder setNicknameGivenName(String nicknameGivenName) {
      ContactRecord.Name.Builder name = builder.nickname == null ? new ContactRecord.Name.Builder() : builder.nickname.newBuilder();
      name.given(nicknameGivenName);
      builder.nickname(name.build());
      return this;
    }

    public Builder setNicknameFamilyName(String nicknameFamilyName) {
      ContactRecord.Name.Builder name = builder.nickname == null ? new ContactRecord.Name.Builder() : builder.nickname.newBuilder();
      name.family(nicknameFamilyName);
      builder.nickname(name.build());
      return this;
    }

    public Builder setNote(String note) {
      builder.note(note == null ? "" : note);
      return this;
    }

    private static ContactRecord.Builder parseUnknowns(byte[] serializedUnknowns) {
      try {
        return ContactRecord.ADAPTER.decode(serializedUnknowns).newBuilder();
      } catch (IOException e) {
        Log.w(TAG, "Failed to combine unknown fields!", e);
        return new ContactRecord.Builder();
      }
    }

    public SignalContactRecord build() {
      return new SignalContactRecord(id, builder.build());
    }
  }
}
