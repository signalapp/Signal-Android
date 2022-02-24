package org.thoughtcrime.securesms.storage;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.model.RecipientRecord;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.storage.SignalContactRecord;
import org.whispersystems.signalservice.internal.storage.protos.ContactRecord.IdentityState;

import java.util.Arrays;
import java.util.Objects;

public class ContactRecordProcessor extends DefaultStorageRecordProcessor<SignalContactRecord> {

  private static final String TAG = Log.tag(ContactRecordProcessor.class);

  private final Recipient         self;
  private final RecipientDatabase recipientDatabase;

  public ContactRecordProcessor(@NonNull Context context, @NonNull Recipient self) {
    this(self, SignalDatabase.recipients());
  }

  ContactRecordProcessor(@NonNull Recipient self, @NonNull RecipientDatabase recipientDatabase) {
    this.self              = self;
    this.recipientDatabase = recipientDatabase;
  }

  /**
   * Error cases:
   * - You can't have a contact record without an address component.
   * - You can't have a contact record for yourself. That should be an account record.
   *
   * Note: This method could be written more succinctly, but the logs are useful :)
   */
  @Override
  boolean isInvalid(@NonNull SignalContactRecord remote) {
    SignalServiceAddress address = remote.getAddress();

    if (address == null) {
      Log.w(TAG, "No address on the ContentRecord -- marking as invalid.");
      return true;
    } else if (!address.hasValidServiceId()) {
      Log.w(TAG, "Found a ContactRecord without a UUID -- marking as invalid.");
      return true;
    } else if ((self.getServiceId().isPresent() && address.getServiceId().equals(self.requireServiceId())) ||
               (self.getE164().isPresent() && address.getNumber().equals(self.getE164())))
    {
      Log.w(TAG, "Found a ContactRecord for ourselves -- marking as invalid.");
      return true;
    } else {
      return false;
    }
  }

  @Override
  @NonNull Optional<SignalContactRecord> getMatching(@NonNull SignalContactRecord remote, @NonNull StorageKeyGenerator keyGenerator) {
    SignalServiceAddress  address = remote.getAddress();
    Optional<RecipientId> byUuid  = recipientDatabase.getByServiceId(address.getServiceId());
    Optional<RecipientId> byE164  = address.getNumber().isPresent() ? recipientDatabase.getByE164(address.getNumber().get()) : Optional.absent();

    return byUuid.or(byE164).transform(recipientDatabase::getRecordForSync)
                            .transform(settings -> {
                              if (settings.getStorageId() != null) {
                                return StorageSyncModels.localToRemoteRecord(settings);
                              } else {
                                Log.w(TAG, "Newly discovering a registered user via storage service. Saving a storageId for them.");
                                recipientDatabase.updateStorageId(settings.getId(), keyGenerator.generate());

                                RecipientRecord updatedSettings = Objects.requireNonNull(recipientDatabase.getRecordForSync(settings.getId()));
                                return StorageSyncModels.localToRemoteRecord(updatedSettings);
                              }
                            })
                            .transform(r -> r.getContact().get());
  }

  @NonNull SignalContactRecord merge(@NonNull SignalContactRecord remote, @NonNull SignalContactRecord local, @NonNull StorageKeyGenerator keyGenerator) {
    String givenName;
    String familyName;

    if (remote.getGivenName().isPresent() || remote.getFamilyName().isPresent()) {
      givenName  = remote.getGivenName().or("");
      familyName = remote.getFamilyName().or("");
    } else {
      givenName  = local.getGivenName().or("");
      familyName = local.getFamilyName().or("");
    }

    byte[]               unknownFields  = remote.serializeUnknownFields();
    ServiceId            serviceId      = local.getAddress().getServiceId() == ServiceId.UNKNOWN ? remote.getAddress().getServiceId() : local.getAddress().getServiceId();
    String               e164           = remote.getAddress().getNumber().or(local.getAddress().getNumber()).orNull();
    SignalServiceAddress address        = new SignalServiceAddress(serviceId, e164);
    byte[]               profileKey     = remote.getProfileKey().or(local.getProfileKey()).orNull();
    String               username       = remote.getUsername().or(local.getUsername()).or("");
    IdentityState        identityState  = remote.getIdentityState();
    byte[]               identityKey    = remote.getIdentityKey().or(local.getIdentityKey()).orNull();
    boolean              blocked        = remote.isBlocked();
    boolean              profileSharing = remote.isProfileSharingEnabled();
    boolean              archived       = remote.isArchived();
    boolean              forcedUnread   = remote.isForcedUnread();
    long                 muteUntil      = remote.getMuteUntil();
    boolean              hideStory      = remote.shouldHideStory();
    boolean              matchesRemote  = doParamsMatch(remote, unknownFields, address, givenName, familyName, profileKey, username, identityState, identityKey, blocked, profileSharing, archived, forcedUnread, muteUntil, hideStory);
    boolean              matchesLocal   = doParamsMatch(local, unknownFields, address, givenName, familyName, profileKey, username, identityState, identityKey, blocked, profileSharing, archived, forcedUnread, muteUntil, hideStory);

    if (matchesRemote) {
      return remote;
    } else if (matchesLocal) {
      return local;
    } else {
      return new SignalContactRecord.Builder(keyGenerator.generate(), address, unknownFields)
                                    .setGivenName(givenName)
                                    .setFamilyName(familyName)
                                    .setProfileKey(profileKey)
                                    .setUsername(username)
                                    .setIdentityState(identityState)
                                    .setIdentityKey(identityKey)
                                    .setBlocked(blocked)
                                    .setProfileSharingEnabled(profileSharing)
                                    .setArchived(archived)
                                    .setForcedUnread(forcedUnread)
                                    .setMuteUntil(muteUntil)
                                    .setHideStory(hideStory)
                                    .build();
    }
  }

  @Override
  void insertLocal(@NonNull SignalContactRecord record) {
    recipientDatabase.applyStorageSyncContactInsert(record);
  }

  @Override
  void updateLocal(@NonNull StorageRecordUpdate<SignalContactRecord> update) {
    recipientDatabase.applyStorageSyncContactUpdate(update);
  }

  @Override
  public int compare(@NonNull SignalContactRecord lhs, @NonNull SignalContactRecord rhs) {
    if (Objects.equals(lhs.getAddress().getServiceId(), rhs.getAddress().getServiceId()) ||
        Objects.equals(lhs.getAddress().getNumber(), rhs.getAddress().getNumber()))
    {
      return 0;
    } else {
      return 1;
    }
  }

  private static boolean doParamsMatch(@NonNull SignalContactRecord contact,
                                       @Nullable byte[] unknownFields,
                                       @NonNull SignalServiceAddress address,
                                       @NonNull String givenName,
                                       @NonNull String familyName,
                                       @Nullable byte[] profileKey,
                                       @NonNull String username,
                                       @Nullable IdentityState identityState,
                                       @Nullable byte[] identityKey,
                                       boolean blocked,
                                       boolean profileSharing,
                                       boolean archived,
                                       boolean forcedUnread,
                                       long muteUntil,
                                       boolean hideStory)
  {
    return Arrays.equals(contact.serializeUnknownFields(), unknownFields) &&
           Objects.equals(contact.getAddress(), address)                  &&
           Objects.equals(contact.getGivenName().or(""), givenName)       &&
           Objects.equals(contact.getFamilyName().or(""), familyName)     &&
           Arrays.equals(contact.getProfileKey().orNull(), profileKey)    &&
           Objects.equals(contact.getUsername().or(""), username)         &&
           Objects.equals(contact.getIdentityState(), identityState)      &&
           Arrays.equals(contact.getIdentityKey().orNull(), identityKey)  &&
           contact.isBlocked() == blocked                                 &&
           contact.isProfileSharingEnabled() == profileSharing            &&
           contact.isArchived() == archived                               &&
           contact.isForcedUnread() == forcedUnread                       &&
           contact.getMuteUntil() == muteUntil                            &&
           contact.shouldHideStory() == hideStory;
  }
}
