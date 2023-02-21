package org.thoughtcrime.securesms.storage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;

import org.signal.libsignal.zkgroup.groups.GroupMasterKey;
import org.thoughtcrime.securesms.database.GroupTable;
import org.thoughtcrime.securesms.database.IdentityTable;
import org.thoughtcrime.securesms.database.RecipientTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.model.DistributionListId;
import org.thoughtcrime.securesms.database.model.DistributionListRecord;
import org.thoughtcrime.securesms.database.model.RecipientRecord;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.keyvalue.PhoneNumberPrivacyValues;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.subscription.Subscriber;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.storage.SignalAccountRecord;
import org.whispersystems.signalservice.api.storage.SignalContactRecord;
import org.whispersystems.signalservice.api.storage.SignalGroupV1Record;
import org.whispersystems.signalservice.api.storage.SignalGroupV2Record;
import org.whispersystems.signalservice.api.storage.SignalStorageRecord;
import org.whispersystems.signalservice.api.storage.SignalStoryDistributionListRecord;
import org.whispersystems.signalservice.api.subscriptions.SubscriberId;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.signalservice.internal.storage.protos.AccountRecord;
import org.whispersystems.signalservice.internal.storage.protos.ContactRecord.IdentityState;
import org.whispersystems.signalservice.internal.storage.protos.GroupV2Record;

import java.util.List;
import java.util.stream.Collectors;

public final class StorageSyncModels {

  private StorageSyncModels() {}

  public static @NonNull SignalStorageRecord localToRemoteRecord(@NonNull RecipientRecord settings) {
    if (settings.getStorageId() == null) {
      throw new AssertionError("Must have a storage key!");
    }

    return localToRemoteRecord(settings, settings.getStorageId());
  }

  public static @NonNull SignalStorageRecord localToRemoteRecord(@NonNull RecipientRecord settings, @NonNull GroupMasterKey groupMasterKey) {
    if (settings.getStorageId() == null) {
      throw new AssertionError("Must have a storage key!");
    }

    return SignalStorageRecord.forGroupV2(localToRemoteGroupV2(settings, settings.getStorageId(), groupMasterKey));
  }

  public static @NonNull SignalStorageRecord localToRemoteRecord(@NonNull RecipientRecord settings, @NonNull byte[] rawStorageId) {
    switch (settings.getGroupType()) {
      case NONE:              return SignalStorageRecord.forContact(localToRemoteContact(settings, rawStorageId));
      case SIGNAL_V1:         return SignalStorageRecord.forGroupV1(localToRemoteGroupV1(settings, rawStorageId));
      case SIGNAL_V2:         return SignalStorageRecord.forGroupV2(localToRemoteGroupV2(settings, rawStorageId, settings.getSyncExtras().getGroupMasterKey()));
      case DISTRIBUTION_LIST: return SignalStorageRecord.forStoryDistributionList(localToRemoteStoryDistributionList(settings, rawStorageId));
      default:                throw new AssertionError("Unsupported type!");
    }
  }

  public static AccountRecord.PhoneNumberSharingMode localToRemotePhoneNumberSharingMode(PhoneNumberPrivacyValues.PhoneNumberSharingMode phoneNumberPhoneNumberSharingMode) {
    switch (phoneNumberPhoneNumberSharingMode) {
      case EVERYONE: return AccountRecord.PhoneNumberSharingMode.EVERYBODY;
      case CONTACTS: return AccountRecord.PhoneNumberSharingMode.CONTACTS_ONLY;
      case NOBODY  : return AccountRecord.PhoneNumberSharingMode.NOBODY;
      default      : throw new AssertionError();
    }
  }

  public static PhoneNumberPrivacyValues.PhoneNumberSharingMode remoteToLocalPhoneNumberSharingMode(AccountRecord.PhoneNumberSharingMode phoneNumberPhoneNumberSharingMode) {
    switch (phoneNumberPhoneNumberSharingMode) {
      case EVERYBODY    : return PhoneNumberPrivacyValues.PhoneNumberSharingMode.EVERYONE;
      case CONTACTS_ONLY: return PhoneNumberPrivacyValues.PhoneNumberSharingMode.CONTACTS;
      case NOBODY       : return PhoneNumberPrivacyValues.PhoneNumberSharingMode.NOBODY;
      default           : return PhoneNumberPrivacyValues.PhoneNumberSharingMode.CONTACTS;
    }
  }

  public static List<SignalAccountRecord.PinnedConversation> localToRemotePinnedConversations(@NonNull List<RecipientRecord> settings) {
    return Stream.of(settings)
                 .filter(s -> s.getGroupType() == RecipientTable.GroupType.SIGNAL_V1 ||
                              s.getGroupType() == RecipientTable.GroupType.SIGNAL_V2 ||
                              s.getRegistered() == RecipientTable.RegisteredState.REGISTERED)
                 .map(StorageSyncModels::localToRemotePinnedConversation)
                 .toList();
  }

  private static @NonNull SignalAccountRecord.PinnedConversation localToRemotePinnedConversation(@NonNull RecipientRecord settings) {
    switch (settings.getGroupType()) {
      case NONE     : return SignalAccountRecord.PinnedConversation.forContact(new SignalServiceAddress(settings.getServiceId(), settings.getE164()));
      case SIGNAL_V1: return SignalAccountRecord.PinnedConversation.forGroupV1(settings.getGroupId().requireV1().getDecodedId());
      case SIGNAL_V2: return SignalAccountRecord.PinnedConversation.forGroupV2(settings.getSyncExtras().getGroupMasterKey().serialize());
      default       : throw new AssertionError("Unexpected group type!");
    }
  }

  private static @NonNull SignalContactRecord localToRemoteContact(@NonNull RecipientRecord recipient, byte[] rawStorageId) {
    if (recipient.getServiceId() == null && recipient.getE164() == null) {
      throw new AssertionError("Must have either a UUID or a phone number!");
    }

    ServiceId serviceId = recipient.getServiceId() != null ? recipient.getServiceId() : ServiceId.UNKNOWN;
    boolean   hideStory = recipient.getExtras() != null && recipient.getExtras().hideStory();

    return new SignalContactRecord.Builder(rawStorageId, serviceId, recipient.getSyncExtras().getStorageProto())
                                  .setE164(recipient.getE164())
                                  .setPni(recipient.getPni())
                                  .setProfileKey(recipient.getProfileKey())
                                  .setProfileGivenName(recipient.getProfileName().getGivenName())
                                  .setProfileFamilyName(recipient.getProfileName().getFamilyName())
                                  .setSystemGivenName(recipient.getSystemProfileName().getGivenName())
                                  .setSystemFamilyName(recipient.getSystemProfileName().getFamilyName())
                                  .setSystemNickname(recipient.getSyncExtras().getSystemNickname())
                                  .setBlocked(recipient.isBlocked())
                                  .setProfileSharingEnabled(recipient.isProfileSharing() || recipient.getSystemContactUri() != null)
                                  .setIdentityKey(recipient.getSyncExtras().getIdentityKey())
                                  .setIdentityState(localToRemoteIdentityState(recipient.getSyncExtras().getIdentityStatus()))
                                  .setArchived(recipient.getSyncExtras().isArchived())
                                  .setForcedUnread(recipient.getSyncExtras().isForcedUnread())
                                  .setMuteUntil(recipient.getMuteUntil())
                                  .setHideStory(hideStory)
                                  .setUnregisteredTimestamp(recipient.getSyncExtras().getUnregisteredTimestamp())
                                  .setHidden(recipient.isHidden())
                                  .setUsername(recipient.getUsername())
                                  .build();
  }

  private static @NonNull SignalGroupV1Record localToRemoteGroupV1(@NonNull RecipientRecord recipient, byte[] rawStorageId) {
    GroupId groupId = recipient.getGroupId();

    if (groupId == null) {
      throw new AssertionError("Must have a groupId!");
    }

    if (!groupId.isV1()) {
      throw new AssertionError("Group is not V1");
    }

    return new SignalGroupV1Record.Builder(rawStorageId, groupId.getDecodedId(), recipient.getSyncExtras().getStorageProto())
                                  .setBlocked(recipient.isBlocked())
                                  .setProfileSharingEnabled(recipient.isProfileSharing())
                                  .setArchived(recipient.getSyncExtras().isArchived())
                                  .setForcedUnread(recipient.getSyncExtras().isForcedUnread())
                                  .setMuteUntil(recipient.getMuteUntil())
                                  .build();
  }

  private static @NonNull SignalGroupV2Record localToRemoteGroupV2(@NonNull RecipientRecord recipient, byte[] rawStorageId, @NonNull GroupMasterKey groupMasterKey) {
    GroupId groupId = recipient.getGroupId();

    if (groupId == null) {
      throw new AssertionError("Must have a groupId!");
    }

    if (!groupId.isV2()) {
      throw new AssertionError("Group is not V2");
    }

    if (groupMasterKey == null) {
      throw new AssertionError("Group master key not on recipient record");
    }

    boolean                     hideStory        = recipient.getExtras() != null && recipient.getExtras().hideStory();
    GroupTable.ShowAsStoryState showAsStoryState = SignalDatabase.groups().getShowAsStoryState(groupId);
    GroupV2Record.StorySendMode storySendMode;

    switch (showAsStoryState) {
      case ALWAYS:
        storySendMode = GroupV2Record.StorySendMode.ENABLED;
        break;
      case NEVER:
        storySendMode = GroupV2Record.StorySendMode.DISABLED;
        break;
      default:
        storySendMode = GroupV2Record.StorySendMode.DEFAULT;
    }

    return new SignalGroupV2Record.Builder(rawStorageId, groupMasterKey, recipient.getSyncExtras().getStorageProto())
                                  .setBlocked(recipient.isBlocked())
                                  .setProfileSharingEnabled(recipient.isProfileSharing())
                                  .setArchived(recipient.getSyncExtras().isArchived())
                                  .setForcedUnread(recipient.getSyncExtras().isForcedUnread())
                                  .setMuteUntil(recipient.getMuteUntil())
                                  .setNotifyForMentionsWhenMuted(recipient.getMentionSetting() == RecipientTable.MentionSetting.ALWAYS_NOTIFY)
                                  .setHideStory(hideStory)
                                  .setStorySendMode(storySendMode)
                                  .build();
  }

  private static @NonNull SignalStoryDistributionListRecord localToRemoteStoryDistributionList(@NonNull RecipientRecord recipient, @NonNull byte[] rawStorageId) {
    DistributionListId distributionListId = recipient.getDistributionListId();

    if (distributionListId == null) {
      throw new AssertionError("Must have a distributionListId!");
    }

    DistributionListRecord record = SignalDatabase.distributionLists().getListForStorageSync(distributionListId);
    if (record == null) {
      throw new AssertionError("Must have a distribution list record!");
    }

    if (record.getDeletedAtTimestamp() > 0L) {
      return new SignalStoryDistributionListRecord.Builder(rawStorageId, recipient.getSyncExtras().getStorageProto())
                                                  .setIdentifier(UuidUtil.toByteArray(record.getDistributionId().asUuid()))
                                                  .setDeletedAtTimestamp(record.getDeletedAtTimestamp())
                                                  .build();
    }

    return new SignalStoryDistributionListRecord.Builder(rawStorageId, recipient.getSyncExtras().getStorageProto())
                                                .setIdentifier(UuidUtil.toByteArray(record.getDistributionId().asUuid()))
                                                .setName(record.getName())
                                                .setRecipients(record.getMembersToSync()
                                                                     .stream()
                                                                     .map(Recipient::resolved)
                                                                     .filter(Recipient::hasServiceId)
                                                                     .map(Recipient::requireServiceId)
                                                                     .map(SignalServiceAddress::new)
                                                                     .collect(Collectors.toList()))
                                                .setAllowsReplies(record.getAllowsReplies())
                                                .setIsBlockList(record.getPrivacyMode().isBlockList())
                                                .build();
  }

  public static @NonNull IdentityTable.VerifiedStatus remoteToLocalIdentityStatus(@NonNull IdentityState identityState) {
    switch (identityState) {
      case VERIFIED:   return IdentityTable.VerifiedStatus.VERIFIED;
      case UNVERIFIED: return IdentityTable.VerifiedStatus.UNVERIFIED;
      default:         return IdentityTable.VerifiedStatus.DEFAULT;
    }
  }

  private static IdentityState localToRemoteIdentityState(@NonNull IdentityTable.VerifiedStatus local) {
    switch (local) {
      case VERIFIED:   return IdentityState.VERIFIED;
      case UNVERIFIED: return IdentityState.UNVERIFIED;
      default:         return IdentityState.DEFAULT;
    }
  }

  public static @NonNull SignalAccountRecord.Subscriber localToRemoteSubscriber(@Nullable Subscriber subscriber) {
    if (subscriber == null) {
      return new SignalAccountRecord.Subscriber(null, null);
    } else {
      return new SignalAccountRecord.Subscriber(subscriber.getCurrencyCode(), subscriber.getSubscriberId().getBytes());
    }
  }

  public static @Nullable Subscriber remoteToLocalSubscriber(@NonNull SignalAccountRecord.Subscriber subscriber) {
    if (subscriber.getId().isPresent()) {
      return new Subscriber(SubscriberId.fromBytes(subscriber.getId().get()), subscriber.getCurrencyCode().get());
    } else {
      return null;
    }
  }
}
