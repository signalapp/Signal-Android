package org.whispersystems.signalservice.api.storage;

import com.google.protobuf.ByteString;

import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.OptionalUtil;
import org.whispersystems.signalservice.api.util.ProtoUtil;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.signalservice.internal.storage.protos.AccountRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SignalAccountRecord implements SignalRecord {

  private final StorageId     id;
  private final AccountRecord proto;
  private final boolean       hasUnknownFields;

  private final Optional<String>         givenName;
  private final Optional<String>         familyName;
  private final Optional<String>         avatarUrlPath;
  private final Optional<byte[]>         profileKey;
  private final List<PinnedConversation> pinnedConversations;
  private final boolean                  preferContactAvatars;

  public SignalAccountRecord(StorageId id, AccountRecord proto) {
    this.id               = id;
    this.proto            = proto;
    this.hasUnknownFields = ProtoUtil.hasUnknownFields(proto);

    this.givenName            = OptionalUtil.absentIfEmpty(proto.getGivenName());
    this.familyName           = OptionalUtil.absentIfEmpty(proto.getFamilyName());
    this.profileKey           = OptionalUtil.absentIfEmpty(proto.getProfileKey());
    this.avatarUrlPath        = OptionalUtil.absentIfEmpty(proto.getAvatarUrlPath());
    this.pinnedConversations  = new ArrayList<>(proto.getPinnedConversationsCount());
    this.preferContactAvatars = proto.getPreferContactAvatars();

    for (AccountRecord.PinnedConversation conversation : proto.getPinnedConversationsList()) {
      pinnedConversations.add(PinnedConversation.fromRemote(conversation));
    }
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

  public Optional<String> getGivenName() {
    return givenName;
  }

  public Optional<String> getFamilyName() {
    return familyName;
  }

  public Optional<byte[]> getProfileKey() {
    return profileKey;
  }

  public Optional<String> getAvatarUrlPath() {
    return avatarUrlPath;
  }

  public boolean isNoteToSelfArchived() {
    return proto.getNoteToSelfArchived();
  }

  public boolean isNoteToSelfForcedUnread() {
    return proto.getNoteToSelfMarkedUnread();
  }

  public boolean isReadReceiptsEnabled() {
    return proto.getReadReceipts();
  }

  public boolean isTypingIndicatorsEnabled() {
    return proto.getTypingIndicators();
  }

  public boolean isSealedSenderIndicatorsEnabled() {
    return proto.getSealedSenderIndicators();
  }

  public boolean isLinkPreviewsEnabled() {
    return proto.getLinkPreviews();
  }

  public AccountRecord.PhoneNumberSharingMode getPhoneNumberSharingMode() {
    return proto.getPhoneNumberSharingMode();
  }

  public boolean isPhoneNumberUnlisted() {
    return proto.getUnlistedPhoneNumber();
  }

  public List<PinnedConversation> getPinnedConversations() {
    return pinnedConversations;
  }

  public boolean isPreferContactAvatars() {
    return preferContactAvatars;
  }

  AccountRecord toProto() {
    return proto;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SignalAccountRecord that = (SignalAccountRecord) o;
    return id.equals(that.id) &&
        proto.equals(that.proto);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, proto);
  }

  public static class PinnedConversation {
    private final Optional<SignalServiceAddress> contact;
    private final Optional<byte[]>               groupV1Id;
    private final Optional<byte[]>               groupV2MasterKey;

    private PinnedConversation(Optional<SignalServiceAddress> contact, Optional<byte[]> groupV1Id, Optional<byte[]> groupV2MasterKey) {
      this.contact          = contact;
      this.groupV1Id        = groupV1Id;
      this.groupV2MasterKey = groupV2MasterKey;
    }

    public static PinnedConversation forContact(SignalServiceAddress address) {
      return new PinnedConversation(Optional.of(address), Optional.absent(), Optional.absent());
    }

    public static PinnedConversation forGroupV1(byte[] groupId) {
      return new PinnedConversation(Optional.absent(), Optional.of(groupId), Optional.absent());
    }

    public static PinnedConversation forGroupV2(byte[] masterKey) {
      return new PinnedConversation(Optional.absent(), Optional.absent(), Optional.of(masterKey));
    }

    private static PinnedConversation forEmpty() {
      return new PinnedConversation(Optional.absent(), Optional.absent(), Optional.absent());
    }

    static PinnedConversation fromRemote(AccountRecord.PinnedConversation remote) {
      if (remote.hasContact()) {
        return forContact(new SignalServiceAddress(UuidUtil.parseOrNull(remote.getContact().getUuid()), remote.getContact().getE164()));
      } else if (!remote.getLegacyGroupId().isEmpty()) {
        return forGroupV1(remote.getLegacyGroupId().toByteArray());
      } else if (!remote.getGroupMasterKey().isEmpty()) {
        return forGroupV2(remote.getGroupMasterKey().toByteArray());
      } else {
        return PinnedConversation.forEmpty();
      }
    }

    public Optional<SignalServiceAddress> getContact() {
      return contact;
    }

    public Optional<byte[]> getGroupV1Id() {
      return groupV1Id;
    }

    public Optional<byte[]> getGroupV2MasterKey() {
      return groupV2MasterKey;
    }

    public boolean isValid() {
      return contact.isPresent() || groupV1Id.isPresent() || groupV2MasterKey.isPresent();
    }

    private AccountRecord.PinnedConversation toRemote() {
      if (contact.isPresent()) {
        AccountRecord.PinnedConversation.Contact.Builder contactBuilder = AccountRecord.PinnedConversation.Contact.newBuilder();
        if (contact.get().getUuid().isPresent()) {
          contactBuilder.setUuid(contact.get().getUuid().get().toString());
        }
        if (contact.get().getNumber().isPresent()) {
          contactBuilder.setE164(contact.get().getNumber().get());
        }
        return AccountRecord.PinnedConversation.newBuilder().setContact(contactBuilder.build()).build();
      } else if (groupV1Id.isPresent()) {
        return AccountRecord.PinnedConversation.newBuilder().setLegacyGroupId(ByteString.copyFrom(groupV1Id.get())).build();
      } else if (groupV2MasterKey.isPresent()) {
        return AccountRecord.PinnedConversation.newBuilder().setGroupMasterKey(ByteString.copyFrom(groupV2MasterKey.get())).build();
      } else {
        return AccountRecord.PinnedConversation.newBuilder().build();
      }
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      PinnedConversation that = (PinnedConversation) o;
      return contact.equals(that.contact)     &&
             groupV1Id.equals(that.groupV1Id) &&
             groupV2MasterKey.equals(that.groupV2MasterKey);
    }

    @Override
    public int hashCode() {
      return Objects.hash(contact, groupV1Id, groupV2MasterKey);
    }
  }

  public static final class Builder {
    private final StorageId             id;
    private final AccountRecord.Builder builder;

    private byte[] unknownFields;

    public Builder(byte[] rawId) {
      this.id      = StorageId.forAccount(rawId);
      this.builder = AccountRecord.newBuilder();
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

    public Builder setAvatarUrlPath(String urlPath) {
      builder.setAvatarUrlPath(urlPath == null ? "" : urlPath);
      return this;
    }

    public Builder setNoteToSelfArchived(boolean archived) {
      builder.setNoteToSelfArchived(archived);
      return this;
    }

    public Builder setNoteToSelfForcedUnread(boolean forcedUnread) {
      builder.setNoteToSelfMarkedUnread(forcedUnread);
      return this;
    }

    public Builder setReadReceiptsEnabled(boolean enabled) {
      builder.setReadReceipts(enabled);
      return this;
    }

    public Builder setTypingIndicatorsEnabled(boolean enabled) {
      builder.setTypingIndicators(enabled);
      return this;
    }

    public Builder setSealedSenderIndicatorsEnabled(boolean enabled) {
      builder.setSealedSenderIndicators(enabled);
      return this;
    }

    public Builder setLinkPreviewsEnabled(boolean enabled) {
      builder.setLinkPreviews(enabled);
      return this;
    }

    public Builder setPhoneNumberSharingMode(AccountRecord.PhoneNumberSharingMode mode) {
      builder.setPhoneNumberSharingMode(mode);
      return this;
    }

    public Builder setUnlistedPhoneNumber(boolean unlisted) {
      builder.setUnlistedPhoneNumber(unlisted);
      return this;
    }

    public Builder setPinnedConversations(List<PinnedConversation> pinnedConversations) {
      builder.clearPinnedConversations();

      for (PinnedConversation pinned : pinnedConversations) {
        builder.addPinnedConversations(pinned.toRemote());
      }

      return this;
    }

    public Builder setPreferContactAvatars(boolean preferContactAvatars) {
      builder.setPreferContactAvatars(preferContactAvatars);

      return this;
    }

    public SignalAccountRecord build() {
      AccountRecord proto = builder.build();

      if (unknownFields != null) {
        proto = ProtoUtil.combineWithUnknownFields(proto, unknownFields);
      }

      return new SignalAccountRecord(id, proto);
    }
  }
}
