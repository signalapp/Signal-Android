package org.whispersystems.signalservice.api.storage;

import org.signal.core.util.ProtoUtil;
import org.signal.libsignal.protocol.logging.Log;
import org.whispersystems.signalservice.api.payments.PaymentsConstants;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.OptionalUtil;
import org.whispersystems.signalservice.internal.storage.protos.AccountRecord;
import org.whispersystems.signalservice.internal.storage.protos.OptionalBool;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import okio.ByteString;

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
  private final Subscriber               backupsSubscriber;

  public SignalAccountRecord(StorageId id, AccountRecord proto) {
    this.id               = id;
    this.proto            = proto;
    this.hasUnknownFields = ProtoUtil.hasUnknownFields(proto);

    this.givenName            = OptionalUtil.absentIfEmpty(proto.givenName);
    this.familyName           = OptionalUtil.absentIfEmpty(proto.familyName);
    this.profileKey           = OptionalUtil.absentIfEmpty(proto.profileKey);
    this.avatarUrlPath        = OptionalUtil.absentIfEmpty(proto.avatarUrlPath);
    this.pinnedConversations  = new ArrayList<>(proto.pinnedConversations.size());
    this.defaultReactions     = new ArrayList<>(proto.preferredReactionEmoji);
    this.subscriber           = new Subscriber(proto.subscriberCurrencyCode, proto.subscriberId.toByteArray());
    this.backupsSubscriber    = new Subscriber(proto.backupsSubscriberCurrencyCode, proto.backupsSubscriberId.toByteArray());

    if (proto.payments != null) {
      this.payments = new Payments(proto.payments.enabled, OptionalUtil.absentIfEmpty(proto.payments.entropy));
    } else {
      this.payments = new Payments(false, Optional.empty());
    }

    for (AccountRecord.PinnedConversation conversation : proto.pinnedConversations) {
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

      if (hasSeenGroupStoryEducationSheet() != that.hasSeenGroupStoryEducationSheet()) {
        diff.add("HasSeenGroupStoryEducationSheet");
      }

      if (!Objects.equals(getUsername(), that.getUsername())) {
        diff.add("Username");
      }

      if (hasCompletedUsernameOnboarding() != that.hasCompletedUsernameOnboarding())  {
        diff.add("HasCompletedUsernameOnboarding");
      }

      if (!Objects.equals(this.getBackupsSubscriber(), that.getBackupsSubscriber())) {
        diff.add("BackupsSubscriber");
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
    return proto.noteToSelfArchived;
  }

  public boolean isNoteToSelfForcedUnread() {
    return proto.noteToSelfMarkedUnread;
  }

  public boolean isReadReceiptsEnabled() {
    return proto.readReceipts;
  }

  public boolean isTypingIndicatorsEnabled() {
    return proto.typingIndicators;
  }

  public boolean isSealedSenderIndicatorsEnabled() {
    return proto.sealedSenderIndicators;
  }

  public boolean isLinkPreviewsEnabled() {
    return proto.linkPreviews;
  }

  public AccountRecord.PhoneNumberSharingMode getPhoneNumberSharingMode() {
    return proto.phoneNumberSharingMode;
  }

  public boolean isPhoneNumberUnlisted() {
    return proto.unlistedPhoneNumber;
  }

  public List<PinnedConversation> getPinnedConversations() {
    return pinnedConversations;
  }

  public boolean isPreferContactAvatars() {
    return proto.preferContactAvatars;
  }

  public Payments getPayments() {
    return payments;
  }

  public int getUniversalExpireTimer() {
    return proto.universalExpireTimer;
  }

  public boolean isPrimarySendsSms() {
    return proto.primarySendsSms;
  }

  public String getE164() {
    return proto.e164;
  }

  public List<String> getDefaultReactions() {
    return defaultReactions;
  }

  public Subscriber getSubscriber() {
    return subscriber;
  }

  public Subscriber getBackupsSubscriber() {
    return backupsSubscriber;
  }

  public boolean isDisplayBadgesOnProfile() {
    return proto.displayBadgesOnProfile;
  }

  public boolean isSubscriptionManuallyCancelled() {
    return proto.subscriptionManuallyCancelled;
  }

  public boolean isKeepMutedChatsArchived() {
    return proto.keepMutedChatsArchived;
  }

  public boolean hasSetMyStoriesPrivacy() {
    return proto.hasSetMyStoriesPrivacy;
  }

  public boolean hasViewedOnboardingStory() {
    return proto.hasViewedOnboardingStory;
  }

  public boolean isStoriesDisabled() {
    return proto.storiesDisabled;
  }

  public OptionalBool getStoryViewReceiptsState() {
    return proto.storyViewReceiptsEnabled;
  }

  public boolean hasSeenGroupStoryEducationSheet() {
    return proto.hasSeenGroupStoryEducationSheet;
  }

  public boolean hasCompletedUsernameOnboarding() {
    return proto.hasCompletedUsernameOnboarding;
  }

  public @Nullable String getUsername() {
    return proto.username;
  }

  public @Nullable AccountRecord.UsernameLink getUsernameLink() {
    return proto.usernameLink;
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
      if (remote.contact != null) {
        ServiceId serviceId = ServiceId.parseOrNull(remote.contact.serviceId);
        if (serviceId != null) {
          return forContact(new SignalServiceAddress(serviceId, remote.contact.e164));
        } else {
          Log.w(TAG, "Bad serviceId on pinned contact! Length: " + remote.contact.serviceId);
          return PinnedConversation.forEmpty();
        }
      } else if (remote.legacyGroupId != null && remote.legacyGroupId.size() > 0) {
        return forGroupV1(remote.legacyGroupId.toByteArray());
      } else if (remote.groupMasterKey != null && remote.groupMasterKey.size() > 0) {
        return forGroupV2(remote.groupMasterKey.toByteArray());
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
        AccountRecord.PinnedConversation.Contact.Builder contactBuilder = new AccountRecord.PinnedConversation.Contact.Builder();

        contactBuilder.serviceId(contact.get().getServiceId().toString());

        if (contact.get().getNumber().isPresent()) {
          contactBuilder.e164(contact.get().getNumber().get());
        }
        return new AccountRecord.PinnedConversation.Builder().contact(contactBuilder.build()).build();
      } else if (groupV1Id.isPresent()) {
        return new AccountRecord.PinnedConversation.Builder().legacyGroupId(ByteString.of(groupV1Id.get())).build();
      } else if (groupV2MasterKey.isPresent()) {
        return new AccountRecord.PinnedConversation.Builder().groupMasterKey(ByteString.of(groupV2MasterKey.get())).build();
      } else {
        return new AccountRecord.PinnedConversation.Builder().build();
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
        this.builder = new AccountRecord.Builder();
      }
    }

    public Builder setGivenName(String givenName) {
      builder.givenName(givenName == null ? "" : givenName);
      return this;
    }

    public Builder setFamilyName(String familyName) {
      builder.familyName(familyName == null ? "" : familyName);
      return this;
    }

    public Builder setProfileKey(byte[] profileKey) {
      builder.profileKey(profileKey == null ? ByteString.EMPTY : ByteString.of(profileKey));
      return this;
    }

    public Builder setAvatarUrlPath(String urlPath) {
      builder.avatarUrlPath(urlPath == null ? "" : urlPath);
      return this;
    }

    public Builder setNoteToSelfArchived(boolean archived) {
      builder.noteToSelfArchived(archived);
      return this;
    }

    public Builder setNoteToSelfForcedUnread(boolean forcedUnread) {
      builder.noteToSelfMarkedUnread(forcedUnread);
      return this;
    }

    public Builder setReadReceiptsEnabled(boolean enabled) {
      builder.readReceipts(enabled);
      return this;
    }

    public Builder setTypingIndicatorsEnabled(boolean enabled) {
      builder.typingIndicators(enabled);
      return this;
    }

    public Builder setSealedSenderIndicatorsEnabled(boolean enabled) {
      builder.sealedSenderIndicators(enabled);
      return this;
    }

    public Builder setLinkPreviewsEnabled(boolean enabled) {
      builder.linkPreviews(enabled);
      return this;
    }

    public Builder setPhoneNumberSharingMode(AccountRecord.PhoneNumberSharingMode mode) {
      builder.phoneNumberSharingMode(mode);
      return this;
    }

    public Builder setUnlistedPhoneNumber(boolean unlisted) {
      builder.unlistedPhoneNumber(unlisted);
      return this;
    }

    public Builder setPinnedConversations(List<PinnedConversation> pinnedConversations) {
      builder.pinnedConversations(pinnedConversations.stream().map(PinnedConversation::toRemote).collect(Collectors.toList()));
      return this;
    }

    public Builder setPreferContactAvatars(boolean preferContactAvatars) {
      builder.preferContactAvatars(preferContactAvatars);
      return this;
    }

    public Builder setPayments(boolean enabled, byte[] entropy) {
      org.whispersystems.signalservice.internal.storage.protos.Payments.Builder paymentsBuilder = new org.whispersystems.signalservice.internal.storage.protos.Payments.Builder();

      boolean entropyPresent = entropy != null && entropy.length == PaymentsConstants.PAYMENTS_ENTROPY_LENGTH;

      paymentsBuilder.enabled(enabled && entropyPresent);

      if (entropyPresent) {
        paymentsBuilder.entropy(ByteString.of(entropy));
      }

      builder.payments(paymentsBuilder.build());

      return this;
    }

    public Builder setUniversalExpireTimer(int timer) {
      builder.universalExpireTimer(timer);
      return this;
    }

    public Builder setPrimarySendsSms(boolean primarySendsSms) {
      builder.primarySendsSms(primarySendsSms);
      return this;
    }

    public Builder setE164(String e164) {
      builder.e164(e164);
      return this;
    }

    public Builder setDefaultReactions(List<String> defaultReactions) {
      builder.preferredReactionEmoji(new ArrayList<>(defaultReactions));
      return this;
    }

    public Builder setSubscriber(Subscriber subscriber) {
      if (subscriber.id.isPresent() && subscriber.currencyCode.isPresent()) {
        builder.subscriberId(ByteString.of(subscriber.id.get()));
        builder.subscriberCurrencyCode(subscriber.currencyCode.get());
      } else {
        builder.subscriberId(StorageRecordProtoUtil.getDefaultAccountRecord().subscriberId);
        builder.subscriberCurrencyCode(StorageRecordProtoUtil.getDefaultAccountRecord().subscriberCurrencyCode);
      }

      return this;
    }

    public Builder setBackupsSubscriber(Subscriber subscriber) {
      if (subscriber.id.isPresent() && subscriber.currencyCode.isPresent()) {
        builder.backupsSubscriberId(ByteString.of(subscriber.id.get()));
        builder.backupsSubscriberCurrencyCode(subscriber.currencyCode.get());
      } else {
        builder.backupsSubscriberId(StorageRecordProtoUtil.getDefaultAccountRecord().subscriberId);
        builder.backupsSubscriberCurrencyCode(StorageRecordProtoUtil.getDefaultAccountRecord().subscriberCurrencyCode);
      }

      return this;
    }

    public Builder setDisplayBadgesOnProfile(boolean displayBadgesOnProfile) {
      builder.displayBadgesOnProfile(displayBadgesOnProfile);
      return this;
    }

    public Builder setSubscriptionManuallyCancelled(boolean subscriptionManuallyCancelled) {
      builder.subscriptionManuallyCancelled(subscriptionManuallyCancelled);
      return this;
    }

    public Builder setKeepMutedChatsArchived(boolean keepMutedChatsArchived) {
      builder.keepMutedChatsArchived(keepMutedChatsArchived);
      return this;
    }

    public Builder setHasSetMyStoriesPrivacy(boolean hasSetMyStoriesPrivacy) {
      builder.hasSetMyStoriesPrivacy(hasSetMyStoriesPrivacy);
      return this;
    }

    public Builder setHasViewedOnboardingStory(boolean hasViewedOnboardingStory) {
      builder.hasViewedOnboardingStory(hasViewedOnboardingStory);
      return this;
    }

    public Builder setStoriesDisabled(boolean storiesDisabled) {
      builder.storiesDisabled(storiesDisabled);
      return this;
    }

    public Builder setStoryViewReceiptsState(OptionalBool storyViewedReceiptsEnabled) {
      builder.storyViewReceiptsEnabled(storyViewedReceiptsEnabled);
      return this;
    }

    public Builder setHasSeenGroupStoryEducationSheet(boolean hasSeenGroupStoryEducationSheet) {
      builder.hasSeenGroupStoryEducationSheet(hasSeenGroupStoryEducationSheet);
      return this;
    }

    public Builder setHasCompletedUsernameOnboarding(boolean hasCompletedUsernameOnboarding) {
      builder.hasCompletedUsernameOnboarding(hasCompletedUsernameOnboarding);
      return this;
    }

    public Builder setUsername(@Nullable String username) {
      if (username == null || username.isEmpty()) {
        builder.username(StorageRecordProtoUtil.getDefaultAccountRecord().username);
      } else {
        builder.username(username);
      }

      return this;
    }

    public Builder setUsernameLink(@Nullable AccountRecord.UsernameLink link) {
      if (link == null) {
        builder.usernameLink(StorageRecordProtoUtil.getDefaultAccountRecord().usernameLink);
      } else {
        builder.usernameLink(link);
      }

      return this;
    }

    private static AccountRecord.Builder parseUnknowns(byte[] serializedUnknowns) {
      try {
        return AccountRecord.ADAPTER.decode(serializedUnknowns).newBuilder();
      } catch (IOException e) {
        Log.w(TAG, "Failed to combine unknown fields!", e);
        return new AccountRecord.Builder();
      }
    }

    public SignalAccountRecord build() {
      return new SignalAccountRecord(id, builder.build());
    }
  }
}
