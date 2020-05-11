package org.thoughtcrime.securesms.storage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.storage.SignalContactRecord;
import org.whispersystems.signalservice.internal.storage.protos.ContactRecord.IdentityState;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

class ContactConflictMerger implements StorageSyncHelper.ConflictMerger<SignalContactRecord> {

  private static final String TAG = Log.tag(ContactConflictMerger.class);

  private final Map<UUID, SignalContactRecord>   localByUuid = new HashMap<>();
  private final Map<String, SignalContactRecord> localByE164 = new HashMap<>();

  private final Recipient self;

  ContactConflictMerger(@NonNull Collection<SignalContactRecord> localOnly, @NonNull Recipient self) {
    for (SignalContactRecord contact : localOnly) {
      if (contact.getAddress().getUuid().isPresent()) {
        localByUuid.put(contact.getAddress().getUuid().get(), contact);
      }
      if (contact.getAddress().getNumber().isPresent()) {
        localByE164.put(contact.getAddress().getNumber().get(), contact);
      }
    }

    this.self = self.resolve();
  }

  @Override
  public @NonNull Optional<SignalContactRecord> getMatching(@NonNull SignalContactRecord record) {
    SignalContactRecord localUuid = record.getAddress().getUuid().isPresent()   ? localByUuid.get(record.getAddress().getUuid().get())   : null;
    SignalContactRecord localE164 = record.getAddress().getNumber().isPresent() ? localByE164.get(record.getAddress().getNumber().get()) : null;

    return Optional.fromNullable(localUuid).or(Optional.fromNullable(localE164));
  }

  @Override
  public @NonNull Collection<SignalContactRecord> getInvalidEntries(@NonNull Collection<SignalContactRecord> remoteRecords) {
    List<SignalContactRecord> invalid = Stream.of(remoteRecords)
                                              .filter(r -> r.getAddress().getUuid().equals(self.getUuid()) || r.getAddress().getNumber().equals(self.getE164()))
                                              .toList();
    if (invalid.size() > 0) {
      Log.w(TAG, "Found invalid contact entries! Count: " + invalid.size());
    }

    return invalid;
  }

  @Override
  public @NonNull SignalContactRecord merge(@NonNull SignalContactRecord remote, @NonNull SignalContactRecord local, @NonNull StorageSyncHelper.KeyGenerator keyGenerator) {
    String givenName;
    String familyName;

    if (remote.getGivenName().isPresent() || remote.getFamilyName().isPresent()) {
      givenName  = remote.getGivenName().or("");
      familyName = remote.getFamilyName().or("");
    } else {
      givenName  = local.getGivenName().or("");
      familyName = local.getFamilyName().or("");
    }

    UUID                 uuid           = remote.getAddress().getUuid().or(local.getAddress().getUuid()).orNull();
    String               e164           = remote.getAddress().getNumber().or(local.getAddress().getNumber()).orNull();
    SignalServiceAddress address        = new SignalServiceAddress(uuid, e164);
    byte[]               profileKey     = remote.getProfileKey().or(local.getProfileKey()).orNull();
    String               username       = remote.getUsername().or(local.getUsername()).or("");
    IdentityState        identityState  = remote.getIdentityState();
    byte[]               identityKey    = remote.getIdentityKey().or(local.getIdentityKey()).orNull();
    boolean              blocked        = remote.isBlocked();
    boolean              profileSharing = remote.isProfileSharingEnabled() || local.isProfileSharingEnabled();
    boolean              archived       = remote.isArchived();
    boolean              matchesRemote  = doParamsMatch(remote, address, givenName, familyName, profileKey, username, identityState, identityKey, blocked, profileSharing, archived);
    boolean              matchesLocal   = doParamsMatch(local, address, givenName, familyName, profileKey, username, identityState, identityKey, blocked, profileSharing, archived);

    if (matchesRemote) {
      return remote;
    } else if (matchesLocal) {
      return local;
    } else {
      return new SignalContactRecord.Builder(keyGenerator.generate(), address)
                                    .setGivenName(givenName)
                                    .setFamilyName(familyName)
                                    .setProfileKey(profileKey)
                                    .setUsername(username)
                                    .setIdentityState(identityState)
                                    .setIdentityKey(identityKey)
                                    .setBlocked(blocked)
                                    .setProfileSharingEnabled(profileSharing)
                                    .build();
    }
  }

  private static boolean doParamsMatch(@NonNull SignalContactRecord contact,
                                       @NonNull SignalServiceAddress address,
                                       @NonNull String givenName,
                                       @NonNull String familyName,
                                       @Nullable byte[] profileKey,
                                       @NonNull String username,
                                       @Nullable IdentityState identityState,
                                       @Nullable byte[] identityKey,
                                       boolean blocked,
                                       boolean profileSharing,
                                       boolean archived)
  {
    return Objects.equals(contact.getAddress(), address)                 &&
           Objects.equals(contact.getGivenName().or(""), givenName)      &&
           Objects.equals(contact.getFamilyName().or(""), familyName)    &&
           Arrays.equals(contact.getProfileKey().orNull(), profileKey)   &&
           Objects.equals(contact.getUsername().or(""), username)        &&
           Objects.equals(contact.getIdentityState(), identityState)     &&
           Arrays.equals(contact.getIdentityKey().orNull(), identityKey) &&
           contact.isBlocked() == blocked                                &&
           contact.isProfileSharingEnabled() == profileSharing           &&
           contact.isArchived() == archived;
  }
}
