package org.thoughtcrime.securesms.storage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.RecipientTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.model.RecipientRecord;
import org.thoughtcrime.securesms.jobs.RetrieveProfileJob;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.PNI;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.storage.SignalContactRecord;
import org.whispersystems.signalservice.api.util.OptionalUtil;
import org.whispersystems.signalservice.internal.storage.protos.ContactRecord.IdentityState;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

public class ContactRecordProcessor extends DefaultStorageRecordProcessor<SignalContactRecord> {

  private static final String TAG = Log.tag(ContactRecordProcessor.class);

  private final RecipientTable recipientTable;

  private final ACI    selfAci;
  private final PNI    selfPni;
  private final String selfE164;

  public ContactRecordProcessor() {
    this(SignalStore.account().getAci(),
         SignalStore.account().getPni(),
         SignalStore.account().getE164(),
         SignalDatabase.recipients());
  }

  ContactRecordProcessor(@Nullable ACI selfAci, @Nullable PNI selfPni, @Nullable String selfE164, @NonNull RecipientTable recipientTable) {
    this.recipientTable = recipientTable;
    this.selfAci        = selfAci;
    this.selfPni        = selfPni;
    this.selfE164       = selfE164;
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
    if (remote.getServiceId() == null) {
      Log.w(TAG, "No address on the ContentRecord -- marking as invalid.");
      return true;
    } else if (remote.getServiceId().isUnknown()) {
      Log.w(TAG, "Found a ContactRecord without a UUID -- marking as invalid.");
      return true;
    } else if (remote.getServiceId().equals(selfAci) ||
               remote.getServiceId().equals(selfPni) ||
               (selfPni != null && selfPni.equals(remote.getPni().orElse(null))) ||
               (selfE164 != null && remote.getNumber().isPresent() && remote.getNumber().get().equals(selfE164)))
    {
      Log.w(TAG, "Found a ContactRecord for ourselves -- marking as invalid.");
      return true;
    } else if (!FeatureFlags.phoneNumberPrivacy() && remote.getServiceId().equals(remote.getPni().orElse(null))) {
      Log.w(TAG, "Found a PNI-only ContactRecord when PNP is disabled -- marking as invalid.");
      return true;
    } else {
      return false;
    }
  }

  @Override
  @NonNull Optional<SignalContactRecord> getMatching(@NonNull SignalContactRecord remote, @NonNull StorageKeyGenerator keyGenerator) {
    if (!FeatureFlags.phoneNumberPrivacy()) {
      remote = remote.withoutPni();
    }

    Optional<RecipientId> found = recipientTable.getByServiceId(remote.getServiceId());

    if (!found.isPresent() && remote.getNumber().isPresent()) {
      found = recipientTable.getByE164(remote.getNumber().get());
    }

    if (!found.isPresent() && remote.getPni().isPresent()) {
      found = recipientTable.getByServiceId(remote.getPni().get());
    }

    if (!found.isPresent() && remote.getPni().isPresent()) {
      found = recipientTable.getByPni(remote.getPni().get());
    }

    return found.map(recipientTable::getRecordForSync)
                .map(settings -> {
                  if (settings.getStorageId() != null) {
                    return StorageSyncModels.localToRemoteRecord(settings);
                  } else {
                    Log.w(TAG, "Newly discovering a registered user via storage service. Saving a storageId for them.");
                    recipientTable.updateStorageId(settings.getId(), keyGenerator.generate());

                    RecipientRecord updatedSettings = Objects.requireNonNull(recipientTable.getRecordForSync(settings.getId()));
                    return StorageSyncModels.localToRemoteRecord(updatedSettings);
                  }
                })
                .map(r -> r.getContact().get());
  }

  @Override
  @NonNull SignalContactRecord merge(@NonNull SignalContactRecord remote, @NonNull SignalContactRecord local, @NonNull StorageKeyGenerator keyGenerator) {
    if (!FeatureFlags.phoneNumberPrivacy()) {
      local  = local.withoutPni();
      remote = remote.withoutPni();
    }

    String profileGivenName;
    String profileFamilyName;

    if (remote.getProfileGivenName().isPresent() || remote.getProfileFamilyName().isPresent()) {
      profileGivenName  = remote.getProfileGivenName().orElse("");
      profileFamilyName = remote.getProfileFamilyName().orElse("");
    } else {
      profileGivenName  = local.getProfileGivenName().orElse("");
      profileFamilyName = local.getProfileFamilyName().orElse("");
    }

    IdentityState identityState;
    byte[]        identityKey;

    if ((remote.getIdentityState() != local.getIdentityState() && remote.getIdentityKey().isPresent()) ||
        (remote.getIdentityKey().isPresent() && !local.getIdentityKey().isPresent()))
    {
      identityState = remote.getIdentityState();
      identityKey   = remote.getIdentityKey().get();
    } else {
      identityState = local.getIdentityState();
      identityKey   = local.getIdentityKey().orElse(null);
    }

    if (identityKey != null && remote.getIdentityKey().isPresent() && !Arrays.equals(identityKey, remote.getIdentityKey().get())) {
      Log.w(TAG, "The local and remote identity keys do not match for " + local.getServiceId() + ". Enqueueing a profile fetch.");
      RetrieveProfileJob.enqueue(Recipient.trustedPush(local.getServiceId(), local.getPni().orElse(null), local.getNumber().orElse(null)).getId());
    }

    PNI    pni;
    String e164;

    if (FeatureFlags.phoneNumberPrivacy()) {
      boolean e164sMatchButPnisDont = local.getNumber().isPresent() &&
                                      local.getNumber().get().equals(remote.getNumber().orElse(null)) &&
                                      local.getPni().isPresent() &&
                                      remote.getPni().isPresent() &&
                                      !local.getPni().get().equals(remote.getPni().get());

      boolean pnisMatchButE164sDont = local.getPni().isPresent() &&
                                      local.getPni().get().equals(remote.getPni().orElse(null)) &&
                                      local.getNumber().isPresent() &&
                                      remote.getNumber().isPresent() &&
                                      !local.getNumber().get().equals(remote.getNumber().get());

      if (e164sMatchButPnisDont) {
        Log.w(TAG, "Matching E164s, but the PNIs differ! Trusting our local pair.");
        // TODO [pnp] Schedule CDS fetch?
        pni  = local.getPni().get();
        e164 = local.getNumber().get();
      } else if (pnisMatchButE164sDont) {
        Log.w(TAG, "Matching PNIs, but the E164s differ! Trusting our local pair.");
        // TODO [pnp] Schedule CDS fetch?
        pni  = local.getPni().get();
        e164 = local.getNumber().get();
      } else {
        pni  = OptionalUtil.or(remote.getPni(), local.getPni()).orElse(null);
        e164 = OptionalUtil.or(remote.getNumber(), local.getNumber()).orElse(null);
      }
    } else {
      pni  = null;
      e164 = OptionalUtil.or(remote.getNumber(), local.getNumber()).orElse(null);
    }

    byte[]               unknownFields         = remote.serializeUnknownFields();
    ServiceId            serviceId             = local.getServiceId() == ServiceId.UNKNOWN ? remote.getServiceId() : local.getServiceId();
    byte[]               profileKey            = OptionalUtil.or(remote.getProfileKey(), local.getProfileKey()).orElse(null);
    String               username              = OptionalUtil.or(remote.getUsername(), local.getUsername()).orElse("");
    boolean              blocked               = remote.isBlocked();
    boolean              profileSharing        = remote.isProfileSharingEnabled();
    boolean              archived              = remote.isArchived();
    boolean              forcedUnread          = remote.isForcedUnread();
    long                 muteUntil             = remote.getMuteUntil();
    boolean              hideStory             = remote.shouldHideStory();
    long                 unregisteredTimestamp = remote.getUnregisteredTimestamp();
    boolean              hidden                = remote.isHidden();
    String               systemGivenName       = SignalStore.account().isPrimaryDevice() ? local.getSystemGivenName().orElse("") : remote.getSystemGivenName().orElse("");
    String               systemFamilyName      = SignalStore.account().isPrimaryDevice() ? local.getSystemFamilyName().orElse("") : remote.getSystemFamilyName().orElse("");
    boolean              matchesRemote         = doParamsMatch(remote, unknownFields, serviceId, pni, e164, profileGivenName, profileFamilyName, systemGivenName, systemFamilyName, profileKey, username, identityState, identityKey, blocked, profileSharing, archived, forcedUnread, muteUntil, hideStory, unregisteredTimestamp, hidden);
    boolean              matchesLocal          = doParamsMatch(local, unknownFields, serviceId, pni, e164, profileGivenName, profileFamilyName, systemGivenName, systemFamilyName, profileKey, username, identityState, identityKey, blocked, profileSharing, archived, forcedUnread, muteUntil, hideStory, unregisteredTimestamp, hidden);

    if (matchesRemote) {
      return remote;
    } else if (matchesLocal) {
      return local;
    } else {
      return new SignalContactRecord.Builder(keyGenerator.generate(), serviceId, unknownFields)
                                    .setE164(e164)
                                    .setPni(pni)
                                    .setProfileGivenName(profileGivenName)
                                    .setProfileFamilyName(profileFamilyName)
                                    .setSystemGivenName(systemGivenName)
                                    .setSystemFamilyName(systemFamilyName)
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
                                    .setUnregisteredTimestamp(unregisteredTimestamp)
                                    .setHidden(hidden)
                                    .build();
    }
  }

  @Override
  void insertLocal(@NonNull SignalContactRecord record) {
    recipientTable.applyStorageSyncContactInsert(record);
  }

  @Override
  void updateLocal(@NonNull StorageRecordUpdate<SignalContactRecord> update) {
    recipientTable.applyStorageSyncContactUpdate(update);
  }

  @Override
  public int compare(@NonNull SignalContactRecord lhs, @NonNull SignalContactRecord rhs) {
    if (Objects.equals(lhs.getServiceId(), rhs.getServiceId()) ||
        (lhs.getNumber().isPresent() && Objects.equals(lhs.getNumber(), rhs.getNumber())) ||
        (lhs.getPni().isPresent() && Objects.equals(lhs.getPni(), rhs.getPni())))
    {
      return 0;
    } else {
      return 1;
    }
  }

  private static boolean doParamsMatch(@NonNull SignalContactRecord contact,
                                       @Nullable byte[] unknownFields,
                                       @NonNull ServiceId serviceId,
                                       @Nullable PNI pni,
                                       @Nullable String e164,
                                       @NonNull String profileGivenName,
                                       @NonNull String profileFamilyName,
                                       @NonNull String systemGivenName,
                                       @NonNull String systemFamilyName,
                                       @Nullable byte[] profileKey,
                                       @NonNull String username,
                                       @Nullable IdentityState identityState,
                                       @Nullable byte[] identityKey,
                                       boolean blocked,
                                       boolean profileSharing,
                                       boolean archived,
                                       boolean forcedUnread,
                                       long muteUntil,
                                       boolean hideStory,
                                       long unregisteredTimestamp,
                                       boolean hidden)
  {
    return Arrays.equals(contact.serializeUnknownFields(), unknownFields) &&
           Objects.equals(contact.getServiceId(), serviceId) &&
           Objects.equals(contact.getPni().orElse(null), pni) &&
           Objects.equals(contact.getNumber().orElse(null), e164) &&
           Objects.equals(contact.getProfileGivenName().orElse(""), profileGivenName) &&
           Objects.equals(contact.getProfileFamilyName().orElse(""), profileFamilyName) &&
           Objects.equals(contact.getSystemGivenName().orElse(""), systemGivenName) &&
           Objects.equals(contact.getSystemFamilyName().orElse(""), systemFamilyName) &&
           Arrays.equals(contact.getProfileKey().orElse(null), profileKey) &&
           Objects.equals(contact.getUsername().orElse(""), username) &&
           Objects.equals(contact.getIdentityState(), identityState) &&
           Arrays.equals(contact.getIdentityKey().orElse(null), identityKey) &&
           contact.isBlocked() == blocked &&
           contact.isProfileSharingEnabled() == profileSharing &&
           contact.isArchived() == archived &&
           contact.isForcedUnread() == forcedUnread &&
           contact.getMuteUntil() == muteUntil &&
           contact.shouldHideStory() == hideStory &&
           contact.getUnregisteredTimestamp() == unregisteredTimestamp &&
           contact.isHidden() == hidden;
  }
}
