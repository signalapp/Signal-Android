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
import org.whispersystems.signalservice.api.push.ServiceId.ACI;
import org.whispersystems.signalservice.api.push.ServiceId.PNI;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.storage.SignalContactRecord;
import org.whispersystems.signalservice.api.util.OptionalUtil;
import org.whispersystems.signalservice.internal.storage.protos.ContactRecord.IdentityState;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.regex.Pattern;

public class ContactRecordProcessor extends DefaultStorageRecordProcessor<SignalContactRecord> {

  private static final String TAG = Log.tag(ContactRecordProcessor.class);

  private static final Pattern E164_PATTERN = Pattern.compile("^\\+[1-9]\\d{0,18}$");

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
   * For contact records specifically, we have some extra work that needs to be done before we process all of the records.
   *
   * We have to look and see if there is an unregistered ACI-only record and another E164/PNI-only record that points to the
   * same local contact row.
   *
   * If so, we actually want to mimic the split and turn them into two separate contact rows locally. The reasons are nuanced,
   * but the TL;DR is that we want to split unregistered users into separate rows so that a user could re-register and get a
   * different ACI.
   */
  @Override
  public void process(@NonNull Collection<SignalContactRecord> remoteRecords, @NonNull StorageKeyGenerator keyGenerator) throws IOException {
    if (!FeatureFlags.phoneNumberPrivacy()) {
      super.process(remoteRecords, keyGenerator);
      return;
    }

    List<SignalContactRecord> unregisteredAciOnly = new ArrayList<>();
    List<SignalContactRecord> pniE164Only         = new ArrayList<>();

    for (SignalContactRecord remoteRecord : remoteRecords) {
      if (isInvalid(remoteRecord)) {
        continue;
      }

      if (remoteRecord.getUnregisteredTimestamp() > 0 && remoteRecord.getAci() != null && !remoteRecord.getPni().isPresent() && !remoteRecord.getNumber().isPresent()) {
        unregisteredAciOnly.add(remoteRecord);
      } else if (remoteRecord.getAci() != null && remoteRecord.getAci().equals(remoteRecord.getPni().orElse(null))) {
        pniE164Only.add(remoteRecord);
      }
    }

    if (unregisteredAciOnly.isEmpty() || pniE164Only.isEmpty()) {
      super.process(remoteRecords, keyGenerator);
      return;
    }

    Log.i(TAG, "We have some unregistered ACI-only contacts as well as some PNI-only contacts. Need to do an intersection to detect any possible required splits.");

    TreeSet<SignalContactRecord> localMatches = new TreeSet<>(this);

    for (SignalContactRecord aciOnly : unregisteredAciOnly) {
      Optional<SignalContactRecord> localMatch = getMatching(aciOnly, keyGenerator);

      if (localMatch.isPresent()) {
        localMatches.add(localMatch.get());
      }
    }

    for (SignalContactRecord pniOnly : pniE164Only) {
      Optional<SignalContactRecord> localMatch = getMatching(pniOnly, keyGenerator);

      if (localMatch.isPresent() && localMatches.contains(localMatch.get())) {
        Log.w(TAG, "Found a situation where we need to split our local record in two in order to match the remote state.");

        SignalDatabase.recipients().splitForStorageSync(localMatch.get().getId().getRaw());
      }
    }


    super.process(remoteRecords, keyGenerator);
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
    if (remote.getAci() == null) {
      Log.w(TAG, "No address on the ContentRecord -- marking as invalid.");
      return true;
    } else if (remote.getAci().isUnknown()) {
      Log.w(TAG, "Found a ContactRecord without a UUID -- marking as invalid.");
      return true;
    } else if (remote.getAci().equals(selfAci) ||
               remote.getAci().equals(selfPni) ||
               (selfPni != null && selfPni.equals(remote.getPni().orElse(null))) ||
               (selfE164 != null && remote.getNumber().isPresent() && remote.getNumber().get().equals(selfE164)))
    {
      Log.w(TAG, "Found a ContactRecord for ourselves -- marking as invalid.");
      return true;
    } else if (remote.getNumber().isPresent() && !isValidE164(remote.getNumber().get())) {
      Log.w(TAG, "Found a record with an invalid E164. Marking as invalid.");
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

    Optional<RecipientId> found = recipientTable.getByAci(remote.getAci());

    if (found.isEmpty() && remote.getNumber().isPresent()) {
      found = recipientTable.getByE164(remote.getNumber().get());
    }

    if (found.isEmpty() && remote.getPni().isPresent()) {
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
      Log.w(TAG, "The local and remote identity keys do not match for " + local.getAci() + ". Enqueueing a profile fetch.");
      RetrieveProfileJob.enqueue(Recipient.trustedPush(local.getAci(), local.getPni().orElse(null), local.getNumber().orElse(null)).getId());
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
    ACI                  aci                   = local.getAci() == ACI.UNKNOWN ? remote.getAci() : local.getAci();
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
    String               systemNickname        = remote.getSystemNickname().orElse("");
    boolean              matchesRemote         = doParamsMatch(remote, unknownFields, aci, pni, e164, profileGivenName, profileFamilyName, systemGivenName, systemFamilyName, systemNickname, profileKey, username, identityState, identityKey, blocked, profileSharing, archived, forcedUnread, muteUntil, hideStory, unregisteredTimestamp, hidden);
    boolean              matchesLocal          = doParamsMatch(local, unknownFields, aci, pni, e164, profileGivenName, profileFamilyName, systemGivenName, systemFamilyName, systemNickname, profileKey, username, identityState, identityKey, blocked, profileSharing, archived, forcedUnread, muteUntil, hideStory, unregisteredTimestamp, hidden);

    if (matchesRemote) {
      return remote;
    } else if (matchesLocal) {
      return local;
    } else {
      return new SignalContactRecord.Builder(keyGenerator.generate(), aci, unknownFields)
                                    .setE164(e164)
                                    .setPni(pni)
                                    .setProfileGivenName(profileGivenName)
                                    .setProfileFamilyName(profileFamilyName)
                                    .setSystemGivenName(systemGivenName)
                                    .setSystemFamilyName(systemFamilyName)
                                    .setSystemNickname(systemNickname)
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
    if (Objects.equals(lhs.getAci(), rhs.getAci()) ||
        (lhs.getNumber().isPresent() && Objects.equals(lhs.getNumber(), rhs.getNumber())) ||
        (lhs.getPni().isPresent() && Objects.equals(lhs.getPni(), rhs.getPni())))
    {
      return 0;
    } else {
      return 1;
    }
  }

  private static boolean isValidE164(String value) {
    return E164_PATTERN.matcher(value).matches();
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
                                       @NonNull String systemNickname,
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
           Objects.equals(contact.getAci(), serviceId) &&
           Objects.equals(contact.getPni().orElse(null), pni) &&
           Objects.equals(contact.getNumber().orElse(null), e164) &&
           Objects.equals(contact.getProfileGivenName().orElse(""), profileGivenName) &&
           Objects.equals(contact.getProfileFamilyName().orElse(""), profileFamilyName) &&
           Objects.equals(contact.getSystemGivenName().orElse(""), systemGivenName) &&
           Objects.equals(contact.getSystemFamilyName().orElse(""), systemFamilyName) &&
           Objects.equals(contact.getSystemNickname().orElse(""), systemNickname) &&
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
