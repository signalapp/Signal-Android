package org.thoughtcrime.securesms.storage;

import androidx.annotation.NonNull;

import org.signal.zkgroup.groups.GroupMasterKey;
import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.database.RecipientDatabase.RecipientSettings;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.storage.SignalContactRecord;
import org.whispersystems.signalservice.api.storage.SignalGroupV1Record;
import org.whispersystems.signalservice.api.storage.SignalGroupV2Record;
import org.whispersystems.signalservice.api.storage.SignalStorageRecord;
import org.whispersystems.signalservice.internal.storage.protos.ContactRecord.IdentityState;

import java.util.Set;

public final class StorageSyncModels {

  private StorageSyncModels() {}

  public static @NonNull SignalStorageRecord localToRemoteRecord(@NonNull RecipientSettings settings, @NonNull Set<RecipientId> archived) {
    if (settings.getStorageId() == null) {
      throw new AssertionError("Must have a storage key!");
    }

    return localToRemoteRecord(settings, settings.getStorageId(), archived);
  }

  public static @NonNull SignalStorageRecord localToRemoteRecord(@NonNull RecipientSettings settings, @NonNull byte[] rawStorageId, @NonNull Set<RecipientId> archived) {
    switch (settings.getGroupType()) {
      case NONE:      return SignalStorageRecord.forContact(localToRemoteContact(settings, rawStorageId, archived));
      case SIGNAL_V1: return SignalStorageRecord.forGroupV1(localToRemoteGroupV1(settings, rawStorageId, archived));
      case SIGNAL_V2: return SignalStorageRecord.forGroupV2(localToRemoteGroupV2(settings, rawStorageId, archived));
      default:        throw new AssertionError("Unsupported type!");
    }
  }

  private static @NonNull SignalContactRecord localToRemoteContact(@NonNull RecipientSettings recipient, byte[] rawStorageId, @NonNull Set<RecipientId> archived) {
    if (recipient.getUuid() == null && recipient.getE164() == null) {
      throw new AssertionError("Must have either a UUID or a phone number!");
    }

    return new SignalContactRecord.Builder(rawStorageId, new SignalServiceAddress(recipient.getUuid(), recipient.getE164()))
                                  .setProfileKey(recipient.getProfileKey())
                                  .setGivenName(recipient.getProfileName().getGivenName())
                                  .setFamilyName(recipient.getProfileName().getFamilyName())
                                  .setBlocked(recipient.isBlocked())
                                  .setProfileSharingEnabled(recipient.isProfileSharing() || recipient.getSystemContactUri() != null)
                                  .setIdentityKey(recipient.getIdentityKey())
                                  .setIdentityState(localToRemoteIdentityState(recipient.getIdentityStatus()))
                                  .setArchived(archived.contains(recipient.getId()))
                                  .build();
  }

  private static @NonNull SignalGroupV1Record localToRemoteGroupV1(@NonNull RecipientSettings recipient, byte[] rawStorageId, @NonNull Set<RecipientId> archived) {
    GroupId groupId = recipient.getGroupId();

    if (groupId == null) {
      throw new AssertionError("Must have a groupId!");
    }

    if (!groupId.isV1()) {
      throw new AssertionError("Group is not V1");
    }

    return new SignalGroupV1Record.Builder(rawStorageId, groupId.getDecodedId())
                                  .setBlocked(recipient.isBlocked())
                                  .setProfileSharingEnabled(recipient.isProfileSharing())
                                  .setArchived(archived.contains(recipient.getId()))
                                  .build();
  }

  private static @NonNull SignalGroupV2Record localToRemoteGroupV2(@NonNull RecipientSettings recipient, byte[] rawStorageId, @NonNull Set<RecipientId> archived) {
    GroupId groupId = recipient.getGroupId();

    if (groupId == null) {
      throw new AssertionError("Must have a groupId!");
    }

    if (!groupId.isV2()) {
      throw new AssertionError("Group is not V2");
    }

    GroupMasterKey groupMasterKey = recipient.getGroupMasterKey();

    if (groupMasterKey == null) {
      throw new AssertionError("Group master key not on recipient record");
    }

    return new SignalGroupV2Record.Builder(rawStorageId, groupMasterKey)
                                  .setBlocked(recipient.isBlocked())
                                  .setProfileSharingEnabled(recipient.isProfileSharing())
                                  .setArchived(archived.contains(recipient.getId()))
                                  .build();
  }

  public static @NonNull IdentityDatabase.VerifiedStatus remoteToLocalIdentityStatus(@NonNull IdentityState identityState) {
    switch (identityState) {
      case VERIFIED:   return IdentityDatabase.VerifiedStatus.VERIFIED;
      case UNVERIFIED: return IdentityDatabase.VerifiedStatus.UNVERIFIED;
      default:         return IdentityDatabase.VerifiedStatus.DEFAULT;
    }
  }

  private static IdentityState localToRemoteIdentityState(@NonNull IdentityDatabase.VerifiedStatus local) {
    switch (local) {
      case VERIFIED:   return IdentityState.VERIFIED;
      case UNVERIFIED: return IdentityState.UNVERIFIED;
      default:         return IdentityState.DEFAULT;
    }
  }

}
