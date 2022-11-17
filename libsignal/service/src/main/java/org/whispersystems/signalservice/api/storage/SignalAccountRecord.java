package org.whispersystems.signalservice.api.storage;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.signal.libsignal.protocol.logging.Log;
import org.whispersystems.signalservice.api.payments.PaymentsConstants;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.OptionalUtil;
import org.whispersystems.signalservice.api.util.ProtoUtil;
import org.whispersystems.signalservice.internal.storage.protos.AccountRecord;
import org.whispersystems.signalservice.internal.storage.protos.OptionalBool;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class SignalAccountRecord implements SignalRecord {

  private static final String TAG = SignalAccountRecord.class.getSimpleName();

  private final StorageId     id;
  private final AccountRecord proto;
  private final boolean       hasUnknownFields;

  private final Optional<String>         givenName;
  private final Optional<String>         familyName;
  private final Optional<String>         avatarUrlPath;
  private final Optional<byte[]>         profileKey;
  private final List<PinnedConversation> pinnedConversations;
  private final Payments                 payments;
  private final List<String>             defaultReactions;
  private final Subscriber               subscriber;

  public SignalAccountRecord(StorageId id, AccountRecord proto) {
    this.id               = id;
    this.proto            = proto;
    this.hasUnknownFields = ProtoUtil.hasUnknownFields(proto);

    this.givenName            = OptionalUtil.absentIfEmpty(proto.getGivenName());
    this.familyName           = OptionalUtil.absentIfEmpty(proto.getFamilyName());
    this.profileKey           = OptionalUtil.absentIfEmpty(proto.getProfileKey());
    this.avatarUrlPath        = OptionalUtil.absentIfEmpty(proto.getAvatarUrlPath());
    this.pinnedConversations  = new ArrayList<>(proto.getPinnedConversationsCount());
    this.payments             = new Payments(proto.getPayments().getEnabled(), OptionalUtil.absentIfEmpty(proto.getPayments().getEntropy()));
    this.defaultReactions     = new ArrayList<>(proto.getPreferredReactionEmojiList());
    this.subscriber           = new Subscriber(proto.getSubscriberCurrencyCode(), proto.getSubscriberId().toByteArray());

    for (AccountRecord.PinnedConversation conversation : proto.getPinnedConversationsList()) {
      pinnedConversations.add(PinnedConversation.fromRemote(conversation));
    }
  }

  @Override
  public StorageId getId() {
    return id;
  }

  @Override
  public SignalStorageRecord asStorageRecord() {
    return SignalStorageRecord.forAccount(this);
  }

  @Override
  public String describeDiff(SignalRecord other) {
    if (other instanceof SignalAccountRecord) {
      SignalAccountRecord that = (SignalAccountRecord) other;
      List<String>        diff = new LinkedList<>();

      if (!Arrays.equals(this.id.getRaw(), that.id.getRaw())) {
        diff.add("ID");
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

      if (!Objects.equals(this.avatarUrlPath, that.avatarUrlPath)) {
        diff.add("AvatarUrlPath");
      }

      if (!Objects.equals(this.isNoteToSelfArchived(), that.isNoteToSelfArchived())) {
        diff.add("NoteToSelfArchived");
      }

      if (!Objects.equals(this.isNoteToSelfForcedUnread(), that.isNoteToSelfForcedUnread())) {
        diff.add("NoteToSelfForcedUnread");
      }

      if (!Objects.equals(this.isReadReceiptsEnabled(), that.isReadReceiptsEnabled())) {
        diff.add("ReadReceipts");
      }

      if (!Objects.equals(this.isTypingIndicatorsEnabled(), that.isTypingIndicatorsEnabled())) {
        diff.add("TypingIndicators");
      }

      if (!Objects.equals(this.isSealedSenderIndicatorsEnabled(), that.isSealedSenderIndicatorsEnabled())) {
        diff.add("SealedSenderIndicators");
      }

      if (!Objects.equals(this.isLinkPreviewsEnabled(), that.isLinkPreviewsEnabled())) {
        diff.add("LinkPreviews");
      }

      if (!Objects.equals(this.getPhoneNumberSharingMode(), that.getPhoneNumberSharingMode())) {
        diff.add("PhoneNumberSharingMode");
      }

      if (!Objects.equals(this.isPhoneNumberUnlisted(), that.isPhoneNumberUnlisted())) {
        diff.add("PhoneNumberUnlisted");
      }

      if (!Objects.equals(this.pinnedConversations, that.pinnedConversations)) {
        diff.add("PinnedConversations");
      }

      if (!Objects.equals(this.isPreferContactAvatars(), that.isPreferContactAvatars())) {
        diff.add("PreferContactAvatars");
      }

      if (!Objects.equals(this.payments, that.payments)) {
        diff.add("Payments");
      }

      if (this.getUniversalExpireTimer() != that.getUniversalExpireTimer()) {
        diff.add("UniversalExpireTimer");
      }

      if (!Objects.equals(this.isPrimarySendsSms(), that.isPrimarySendsSms())) {
        diff.add("PrimarySendsSms");
      }

      if (!Objects.equals(this.getE164(), that.getE164())) {
        diff.add("E164");
      }

      if (!Objects.equals(this.getDefaultReactions(), that.getDefaultReactions())) {
        diff.add("DefaultReactions");
      }

      if (!Objects.equals(this.hasUnknownFields(), that.hasUnknownFields())) {
        diff.add("UnknownFields");
      }

      if (!Objects.equals(this.getSubscriber(), that.getSubscriber())) {
        diff.add("Subscriber");
      }

      if (!Objects.equals(this.isDisplayBadgesOnProfile(), that.isDisplayBadgesOnProfile())) {
        diff.add("DisplayBadgesOnProfile");
      }

      if (!Objects.equals(this.isSubscriptionManuallyCancelled(), that.isSubscriptionManuallyCancelled())) {
        diff.add("SubscriptionManuallyCancelled");
      }

      if (isKeepMutedChatsArchived() != that.isKeepMutedChatsArchived()) {
        diff.add("KeepMutedChatsArchived");
      }

      if (hasSetMyStoriesPrivacy() != that.hasSetMyStoriesPrivacy()) {
        diff.add("HasSetMyStoryPrivacy");
      }

      if (hasViewedOnboardingStory() != that.hasViewedOnboardingStory()) {
        diff.add("HasViewedOnboardingStory");
      }

      if (isStoriesDisabled() != that.isStoriesDisabled()) {
        diff.add("StoriesDisabled");
      }

      if (getStoryViewReceiptsState() != that.getStoryViewReceiptsState()) {
        diff.add("StoryViewedReceipts");
      }

      if (hasReadOnboardingStory() != that.hasReadOnboardingStory()) {
        diff.add("HasReadOnboardingStory");
      }

      if (hasSeenGroupStoryEducationSheet() != that.hasSeenGroupStoryEducationSheet()) {
        diff.add("HasSeenGroupStoryEducationSheet");
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
    return proto.getPreferContactAvatars();
  }

  public Payments getPayments() {
    return payments;
  }

  public int getUniversalExpireTimer() {
    return proto.getUniversalExpireTimer();
  }

  public boolean isPrimarySendsSms() {
    return proto.getPrimarySendsSms();
  }

  public String getE164() {
    return proto.getE164();
  }

  public List<String> getDefaultReactions() {
    return defaultReactions;
  }

  public Subscriber getSubscriber() {
    return subscriber;
  }

  public boolean isDisplayBadgesOnProfile() {
    return proto.getDisplayBadgesOnProfile();
  }

  public boolean isSubscriptionManuallyCancelled() {
    return proto.getSubscriptionManuallyCancelled();
  }

  public boolean isKeepMutedChatsArchived() {
    return proto.getKeepMutedChatsArchived();
  }

  public boolean hasSetMyStoriesPrivacy() {
    return proto.getHasSetMyStoriesPrivacy();
  }

  public boolean hasViewedOnboardingStory() {
    return proto.getHasViewedOnboardingStory();
  }

  public boolean isStoriesDisabled() {
    return proto.getStoriesDisabled();
  }

  public OptionalBool getStoryViewReceiptsState() {
    return proto.getStoryViewReceiptsEnabled();
  }

  public boolean hasReadOnboardingStory() {
    return proto.getHasReadOnboardingStory();
  }

  public boolean hasSeenGroupStoryEducationSheet() {
    return proto.getHasSeenGroupStoryEducationSheet();
  }

  public AccountRecord toProto() {
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
      return new PinnedConversation(Optional.of(address), Optional.empty(), Optional.empty());
    }

    public static PinnedConversation forGroupV1(byte[] groupId) {
      return new PinnedConversation(Optional.empty(), Optional.of(groupId), Optional.empty());
    }

    public static PinnedConversation forGroupV2(byte[] masterKey) {
      return new PinnedConversation(Optional.empty(), Optional.empty(), Optional.of(masterKey));
    }

    private static PinnedConversation forEmpty() {
      return new PinnedConversation(Optional.empty(), Optional.empty(), Optional.empty());
    }

    static PinnedConversation fromRemote(AccountRecord.PinnedConversation remote) {
      if (remote.hasContact()) {
        ServiceId serviceId = ServiceId.parseOrNull(remote.getContact().getUuid());
        if (serviceId != null) {
          return forContact(new SignalServiceAddress(serviceId, remote.getContact().getE164()));
        } else {
          Log.w(TAG, "Bad serviceId on pinned contact! Length: " + remote.getContact().getUuid());
          return PinnedConversation.forEmpty();
        }
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

        contactBuilder.setUuid(contact.get().getServiceId().toString());

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

  public static class Subscriber {
    private final Optional<String> currencyCode;
    private final Optional<byte[]> id;

    public Subscriber(String currencyCode, byte[] id) {
      if (currencyCode != null && id != null && id.length == 32) {
        this.currencyCode = Optional.of(currencyCode);
        this.id           = Optional.of(id);
      } else {
        this.currencyCode = Optional.empty();
        this.id           = Optional.empty();
      }
    }

    public Optional<String> getCurrencyCode() {
      return currencyCode;
    }

    public Optional<byte[]> getId() {
      return id;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      final Subscriber that = (Subscriber) o;
      return Objects.equals(currencyCode, that.currencyCode) && OptionalUtil.byteArrayEquals(id, that.id);
    }

    @Override
    public int hashCode() {
      return Objects.hash(currencyCode, id);
    }
  }

  public static class Payments {
    private static final String TAG = Payments.class.getSimpleName();

    private final boolean          enabled;
    private final Optional<byte[]> entropy;

    public Payments(boolean enabled, Optional<byte[]> entropy) {
      byte[] entropyBytes = entropy.orElse(null);
      if (entropyBytes != null && entropyBytes.length != PaymentsConstants.PAYMENTS_ENTROPY_LENGTH) {
        Log.w(TAG, "Blocked entropy of length " + entropyBytes.length);
        entropyBytes = null;
      }
      this.entropy = Optional.ofNullable(entropyBytes);
      this.enabled = enabled && this.entropy.isPresent();
    }

    public boolean isEnabled() {
      return enabled;
    }

    public Optional<byte[]> getEntropy() {
      return entropy;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Payments payments = (Payments) o;
      return enabled == payments.enabled &&
          OptionalUtil.byteArrayEquals(entropy, payments.entropy);
    }

    @Override
    public int hashCode() {
      return Objects.hash(enabled, entropy);
    }
  }

  public static final class Builder {
    private final StorageId             id;
    private final AccountRecord.Builder builder;

    public Builder(byte[] rawId, byte[] serializedUnknowns) {
      this.id = StorageId.forAccount(rawId);

      if (serializedUnknowns != null) {
        this.builder = parseUnknowns(serializedUnknowns);
      } else {
        this.builder = AccountRecord.newBuilder();
      }
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

    public Builder setPayments(boolean enabled, byte[] entropy) {
      org.whispersystems.signalservice.internal.storage.protos.Payments.Builder paymentsBuilder = org.whispersystems.signalservice.internal.storage.protos.Payments.newBuilder();

      boolean entropyPresent = entropy != null && entropy.length == PaymentsConstants.PAYMENTS_ENTROPY_LENGTH;

      paymentsBuilder.setEnabled(enabled && entropyPresent);

      if (entropyPresent) {
        paymentsBuilder.setEntropy(ByteString.copyFrom(entropy));
      }

      builder.setPayments(paymentsBuilder);

      return this;
    }

    public Builder setUniversalExpireTimer(int timer) {
      builder.setUniversalExpireTimer(timer);
      return this;
    }

    public Builder setPrimarySendsSms(boolean primarySendsSms) {
      builder.setPrimarySendsSms(primarySendsSms);
      return this;
    }

    public Builder setE164(String e164) {
      builder.setE164(e164);
      return this;
    }

    public Builder setDefaultReactions(List<String> defaultReactions) {
      builder.clearPreferredReactionEmoji();
      builder.addAllPreferredReactionEmoji(defaultReactions);
      return this;
    }

    public Builder setSubscriber(Subscriber subscriber) {
      if (subscriber.id.isPresent() && subscriber.currencyCode.isPresent()) {
        builder.setSubscriberId(ByteString.copyFrom(subscriber.id.get()));
        builder.setSubscriberCurrencyCode(subscriber.currencyCode.get());
      } else {
        builder.clearSubscriberId();
        builder.clearSubscriberCurrencyCode();
      }

      return this;
    }

    public Builder setDisplayBadgesOnProfile(boolean displayBadgesOnProfile) {
      builder.setDisplayBadgesOnProfile(displayBadgesOnProfile);
      return this;
    }

    public Builder setSubscriptionManuallyCancelled(boolean subscriptionManuallyCancelled) {
      builder.setSubscriptionManuallyCancelled(subscriptionManuallyCancelled);
      return this;
    }

    public Builder setKeepMutedChatsArchived(boolean keepMutedChatsArchived) {
      builder.setKeepMutedChatsArchived(keepMutedChatsArchived);
      return this;
    }

    public Builder setHasSetMyStoriesPrivacy(boolean hasSetMyStoriesPrivacy) {
      builder.setHasSetMyStoriesPrivacy(hasSetMyStoriesPrivacy);
      return this;
    }

    public Builder setHasViewedOnboardingStory(boolean hasViewedOnboardingStory) {
      builder.setHasViewedOnboardingStory(hasViewedOnboardingStory);
      return this;
    }

    public Builder setStoriesDisabled(boolean storiesDisabled) {
      builder.setStoriesDisabled(storiesDisabled);
      return this;
    }

    public Builder setStoryViewReceiptsState(OptionalBool storyViewedReceiptsEnabled) {
      builder.setStoryViewReceiptsEnabled(storyViewedReceiptsEnabled);
      return this;
    }

    public Builder setHasReadOnboardingStory(boolean hasReadOnboardingStory) {
      builder.setHasReadOnboardingStory(hasReadOnboardingStory);
      return this;
    }

    public Builder setHasSeenGroupStoryEducationSheet(boolean hasSeenGroupStoryEducationSheet) {
      builder.setHasSeenGroupStoryEducationSheet(hasSeenGroupStoryEducationSheet);
      return this;
    }

    private static AccountRecord.Builder parseUnknowns(byte[] serializedUnknowns) {
      try {
        return AccountRecord.parseFrom(serializedUnknowns).toBuilder();
      } catch (InvalidProtocolBufferException e) {
        Log.w(TAG, "Failed to combine unknown fields!", e);
        return AccountRecord.newBuilder();
      }
    }

    public SignalAccountRecord build() {
      return new SignalAccountRecord(id, builder.build());
    }
  }
}
