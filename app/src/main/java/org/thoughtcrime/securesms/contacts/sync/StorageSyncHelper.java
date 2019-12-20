package org.thoughtcrime.securesms.contacts.sync;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.database.RecipientDatabase.RecipientSettings;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.SetUtil;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.storage.SignalContactRecord;
import org.whispersystems.signalservice.api.storage.SignalContactRecord.IdentityState;
import org.whispersystems.signalservice.api.storage.SignalStorageManifest;
import org.whispersystems.signalservice.api.storage.SignalStorageRecord;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class StorageSyncHelper {

  private static final String TAG = Log.tag(StorageSyncHelper.class);

  private static final KeyGenerator KEY_GENERATOR = () -> Util.getSecretBytes(16);

  private static KeyGenerator testKeyGenerator = null;

  /**
   * Given the local state of pending storage mutatations, this will generate a result that will
   * include that data that needs to be written to the storage service, as well as any changes you
   * need to write back to local storage (like storage keys that might have changed for updated
   * contacts).
   *
   * @param currentManifestVersion What you think the version is locally.
   * @param currentLocalKeys All local keys you have. This assumes that 'inserts' were given keys
   *                         already, and that deletes still have keys.
   * @param updates Contacts that have been altered.
   * @param inserts Contacts that have been inserted (or newly marked as registered).
   * @param deletes Contacts that are no longer registered.
   *
   * @return If changes need to be written, then it will return those changes. If no changes need
   *         to be written, this will return {@link Optional#absent()}.
   */
  public static @NonNull Optional<LocalWriteResult> buildStorageUpdatesForLocal(long currentManifestVersion,
                                                                                @NonNull List<byte[]> currentLocalKeys,
                                                                                @NonNull List<RecipientSettings> updates,
                                                                                @NonNull List<RecipientSettings> inserts,
                                                                                @NonNull List<RecipientSettings> deletes)
  {
    Set<ByteBuffer>          completeKeys      = new LinkedHashSet<>(Stream.of(currentLocalKeys).map(ByteBuffer::wrap).toList());
    Set<SignalContactRecord> contactInserts    = new LinkedHashSet<>();
    Set<ByteBuffer>          contactDeletes    = new LinkedHashSet<>();
    Map<RecipientId, byte[]> storageKeyUpdates = new HashMap<>();

    for (RecipientSettings insert : inserts) {
      contactInserts.add(localToRemoteContact(insert));
    }

    for (RecipientSettings delete : deletes) {
      byte[] key = Objects.requireNonNull(delete.getStorageKey());
      contactDeletes.add(ByteBuffer.wrap(key));
      completeKeys.remove(ByteBuffer.wrap(key));
    }

    for (RecipientSettings update : updates) {
      byte[] oldKey = Objects.requireNonNull(update.getStorageKey());
      byte[] newKey = generateKey();

      contactInserts.add(localToRemoteContact(update, newKey));
      contactDeletes.add(ByteBuffer.wrap(oldKey));
      completeKeys.remove(ByteBuffer.wrap(oldKey));
      completeKeys.add(ByteBuffer.wrap(newKey));
      storageKeyUpdates.put(update.getId(), newKey);
    }

    if (contactInserts.isEmpty() && contactDeletes.isEmpty()) {
      return Optional.absent();
    } else {
      List<SignalStorageRecord> storageInserts       = Stream.of(contactInserts).map(c -> SignalStorageRecord.forContact(c.getKey(), c)).toList();
      List<byte[]>              contactDeleteBytes   = Stream.of(contactDeletes).map(ByteBuffer::array).toList();
      List<byte[]>              completeKeysBytes    = Stream.of(completeKeys).map(ByteBuffer::array).toList();
      SignalStorageManifest     manifest             = new SignalStorageManifest(currentManifestVersion + 1, completeKeysBytes);
      WriteOperationResult      writeOperationResult = new WriteOperationResult(manifest, storageInserts, contactDeleteBytes);

      return Optional.of(new LocalWriteResult(writeOperationResult, storageKeyUpdates));
    }
  }

  /**
   * Given a list of all the local and remote keys you know about, this will return a result telling
   * you which keys are exclusively remote and which are exclusively local.
   *
   * @param remoteKeys All remote keys available.
   * @param localKeys All local keys available.
   *
   * @return An object describing which keys are exclusive to the remote data set and which keys are
   *         exclusive to the local data set.
   */
  public static @NonNull KeyDifferenceResult findKeyDifference(@NonNull List<byte[]> remoteKeys,
                                                               @NonNull List<byte[]> localKeys)
  {
    Set<ByteBuffer> allRemoteKeys = Stream.of(remoteKeys).map(ByteBuffer::wrap).collect(LinkedHashSet::new, HashSet::add);
    Set<ByteBuffer> allLocalKeys  = Stream.of(localKeys).map(ByteBuffer::wrap).collect(LinkedHashSet::new, HashSet::add);

    Set<ByteBuffer> remoteOnlyKeys = SetUtil.difference(allRemoteKeys, allLocalKeys);
    Set<ByteBuffer> localOnlyKeys  = SetUtil.difference(allLocalKeys, allRemoteKeys);

    return new KeyDifferenceResult(Stream.of(remoteOnlyKeys).map(ByteBuffer::array).toList(),
                                   Stream.of(localOnlyKeys).map(ByteBuffer::array).toList());
  }

  /**
   * Given two sets of storage records, this will resolve the data into a set of actions that need
   * to be applied to resolve the differences. This will handle discovering which records between
   * the two collections refer to the same contacts and are actually updates, which are brand new,
   * etc.
   *
   * @param remoteOnlyRecords Records that are only present remotely.
   * @param localOnlyRecords Records that are only present locally.
   *
   * @return A set of actions that should be applied to resolve the conflict.
   */
  public static @NonNull MergeResult resolveConflict(@NonNull Collection<SignalStorageRecord> remoteOnlyRecords,
                                                     @NonNull Collection<SignalStorageRecord> localOnlyRecords)
  {
    List<SignalContactRecord> remoteOnlyContacts = Stream.of(remoteOnlyRecords).filter(r -> r.getContact().isPresent()).map(r -> r.getContact().get()).toList();
    List<SignalContactRecord> localOnlyContacts  = Stream.of(localOnlyRecords).filter(r -> r.getContact().isPresent()).map(r -> r.getContact().get()).toList();

    List<SignalStorageRecord> remoteOnlyUnknowns = Stream.of(remoteOnlyRecords).filter(SignalStorageRecord::isUnknown).toList();
    List<SignalStorageRecord> localOnlyUnknowns  = Stream.of(localOnlyRecords).filter(SignalStorageRecord::isUnknown).toList();

    ContactRecordMergeResult contactMergeResult = resolveContactConflict(remoteOnlyContacts, localOnlyContacts);

    return new MergeResult(contactMergeResult.localInserts,
                           contactMergeResult.localUpdates,
                           contactMergeResult.remoteInserts,
                           contactMergeResult.remoteUpdates,
                           new LinkedHashSet<>(remoteOnlyUnknowns),
                           new LinkedHashSet<>(localOnlyUnknowns));
  }

  /**
   * Assumes that the merge result has *not* yet been applied to the local data. That means that
   * this method will handle generating the correct final key set based on the merge result.
   */
  public static @NonNull WriteOperationResult createWriteOperation(long currentManifestVersion,
                                                                   @NonNull List<byte[]> currentLocalStorageKeys,
                                                                   @NonNull MergeResult mergeResult)
  {
    Set<ByteBuffer> completeKeys = new LinkedHashSet<>(Stream.of(currentLocalStorageKeys).map(ByteBuffer::wrap).toList());

    for (SignalContactRecord insert : mergeResult.getLocalContactInserts()) {
      completeKeys.add(ByteBuffer.wrap(insert.getKey()));
    }

    for (SignalContactRecord insert : mergeResult.getRemoteContactInserts()) {
      completeKeys.add(ByteBuffer.wrap(insert.getKey()));
    }

    for (SignalStorageRecord insert : mergeResult.getLocalUnknownInserts()) {
      completeKeys.add(ByteBuffer.wrap(insert.getKey()));
    }

    for (ContactUpdate update : mergeResult.getLocalContactUpdates()) {
      completeKeys.remove(ByteBuffer.wrap(update.getOldContact().getKey()));
      completeKeys.add(ByteBuffer.wrap(update.getNewContact().getKey()));
    }

    for (ContactUpdate update : mergeResult.getRemoteContactUpdates()) {
      completeKeys.remove(ByteBuffer.wrap(update.getOldContact().getKey()));
      completeKeys.add(ByteBuffer.wrap(update.getNewContact().getKey()));
    }

    SignalStorageManifest manifest = new SignalStorageManifest(currentManifestVersion + 1, Stream.of(completeKeys).map(ByteBuffer::array).toList());

    List<SignalContactRecord> contactInserts = new ArrayList<>();
    contactInserts.addAll(mergeResult.getRemoteContactInserts());
    contactInserts.addAll(Stream.of(mergeResult.getRemoteContactUpdates()).map(ContactUpdate::getNewContact).toList());

    List<SignalStorageRecord> inserts = Stream.of(contactInserts).map(c -> SignalStorageRecord.forContact(c.getKey(), c)).toList();

    List<byte[]> deletes = Stream.of(mergeResult.getRemoteContactUpdates()).map(ContactUpdate::getOldContact).map(SignalContactRecord::getKey).toList();

    return new WriteOperationResult(manifest, inserts, deletes);
  }

  public static @NonNull SignalContactRecord localToRemoteContact(@NonNull RecipientSettings recipient) {
    if (recipient.getStorageKey() == null) {
      throw new AssertionError("Must have a storage key!");
    }

    return localToRemoteContact(recipient, recipient.getStorageKey());
  }

  private static @NonNull SignalContactRecord localToRemoteContact(@NonNull RecipientSettings recipient, byte[] storageKey) {
    if (recipient.getUuid() == null && recipient.getE164() == null) {
      throw new AssertionError("Must have either a UUID or a phone number!");
    }

    return new SignalContactRecord.Builder(storageKey, new SignalServiceAddress(recipient.getUuid(), recipient.getE164()))
                                   .setProfileKey(recipient.getProfileKey())
                                   .setProfileName(recipient.getProfileName().serialize())
                                   .setBlocked(recipient.isBlocked())
                                   .setProfileSharingEnabled(recipient.isProfileSharing())
                                   .setIdentityKey(recipient.getIdentityKey())
                                   .setIdentityState(localToRemoteIdentityState(recipient.getIdentityStatus()))
                                   .build();
  }

  public static @NonNull IdentityDatabase.VerifiedStatus remoteToLocalIdentityStatus(@NonNull IdentityState identityState) {
    switch (identityState) {
      case VERIFIED:   return IdentityDatabase.VerifiedStatus.VERIFIED;
      case UNVERIFIED: return IdentityDatabase.VerifiedStatus.UNVERIFIED;
      default:         return IdentityDatabase.VerifiedStatus.DEFAULT;
    }
  }

  public static @NonNull byte[] generateKey() {
    if (testKeyGenerator != null) {
      return testKeyGenerator.generate();
    } else {
      return KEY_GENERATOR.generate();
    }
  }

  @VisibleForTesting
  static @NonNull SignalContactRecord mergeContacts(@NonNull SignalContactRecord remote,
                                                    @NonNull SignalContactRecord local)
  {
    UUID                 uuid           = remote.getAddress().getUuid().or(local.getAddress().getUuid()).orNull();
    String               e164           = remote.getAddress().getNumber().or(local.getAddress().getNumber()).orNull();
    SignalServiceAddress address        = new SignalServiceAddress(uuid, e164);
    String               profileName    = remote.getProfileName().or(local.getProfileName()).orNull();
    byte[]               profileKey     = remote.getProfileKey().or(local.getProfileKey()).orNull();
    String               username       = remote.getUsername().or(local.getUsername()).orNull();
    IdentityState        identityState  = remote.getIdentityState();
    byte[]               identityKey    = remote.getIdentityKey().or(local.getIdentityKey()).orNull();
    String               nickname       = local.getNickname().orNull(); // TODO [greyson] Update this when we add real nickname support
    boolean              blocked        = remote.isBlocked();
    boolean              profileSharing = remote.isProfileSharingEnabled() | local.isProfileSharingEnabled();
    boolean              matchesRemote  = doParamsMatchContact(remote, address, profileName, profileKey, username, identityState, identityKey, blocked, profileSharing, nickname);
    boolean              matchesLocal   = doParamsMatchContact(local, address, profileName, profileKey, username, identityState, identityKey, blocked, profileSharing, nickname);

    if (remote.getProtoVersion() > 0) {
      Log.w(TAG, "Inbound model has version " + remote.getProtoVersion() + ", but our version is 0.");
    }

    if (matchesRemote) {
      return remote;
    } else if (matchesLocal) {
      return local;
    } else {
      return new SignalContactRecord.Builder(generateKey(), address)
                                     .setProfileName(profileName)
                                     .setProfileKey(profileKey)
                                     .setUsername(username)
                                     .setIdentityState(identityState)
                                     .setIdentityKey(identityKey)
                                     .setBlocked(blocked)
                                     .setProfileSharingEnabled(profileSharing)
                                     .setNickname(nickname)
                                     .build();
    }
  }

  @VisibleForTesting
  static void setTestKeyGenerator(@Nullable KeyGenerator keyGenerator) {
    testKeyGenerator = keyGenerator;
  }

  private static IdentityState localToRemoteIdentityState(@NonNull IdentityDatabase.VerifiedStatus local) {
    switch (local) {
      case VERIFIED:   return IdentityState.VERIFIED;
      case UNVERIFIED: return IdentityState.UNVERIFIED;
      default:         return IdentityState.DEFAULT;
    }
  }

  private static boolean doParamsMatchContact(@NonNull SignalContactRecord contact,
                                              @NonNull SignalServiceAddress address,
                                              @Nullable String profileName,
                                              @Nullable byte[] profileKey,
                                              @Nullable String username,
                                              @Nullable IdentityState identityState,
                                              @Nullable byte[] identityKey,
                                              boolean blocked,
                                              boolean profileSharing,
                                              @Nullable String nickname)
  {
    return Objects.equals(contact.getAddress(), address)                  &&
           Objects.equals(contact.getProfileName().orNull(), profileName) &&
           Arrays.equals(contact.getProfileKey().orNull(), profileKey)    &&
           Objects.equals(contact.getUsername().orNull(), username)       &&
           Objects.equals(contact.getIdentityState(), identityState)      &&
           Arrays.equals(contact.getIdentityKey().orNull(), identityKey)  &&
           contact.isBlocked() == blocked                                 &&
           contact.isProfileSharingEnabled() == profileSharing            &&
           Objects.equals(contact.getNickname().orNull(), nickname);
  }

  private static @NonNull ContactRecordMergeResult resolveContactConflict(@NonNull Collection<SignalContactRecord> remoteOnlyRecords,
                                                                          @NonNull Collection<SignalContactRecord> localOnlyRecords)
  {
    Map<UUID, SignalContactRecord>   localByUuid = new HashMap<>();
    Map<String, SignalContactRecord> localByE164 = new HashMap<>();

    for (SignalContactRecord contact : localOnlyRecords) {
      if (contact.getAddress().getUuid().isPresent()) {
        localByUuid.put(contact.getAddress().getUuid().get(), contact);
      }
      if (contact.getAddress().getNumber().isPresent()) {
        localByE164.put(contact.getAddress().getNumber().get(), contact);
      }
    }

    Set<SignalContactRecord> localInserts  = new LinkedHashSet<>(remoteOnlyRecords);
    Set<SignalContactRecord> remoteInserts = new LinkedHashSet<>(localOnlyRecords);
    Set<ContactUpdate>       localUpdates  = new LinkedHashSet<>();
    Set<ContactUpdate>       remoteUpdates = new LinkedHashSet<>();

    for (SignalContactRecord remote : remoteOnlyRecords) {
      SignalContactRecord localUuid = remote.getAddress().getUuid().isPresent() ? localByUuid.get(remote.getAddress().getUuid().get()) : null;
      SignalContactRecord localE164 = remote.getAddress().getNumber().isPresent() ? localByE164.get(remote.getAddress().getNumber().get()) : null;

      Optional<SignalContactRecord> local = Optional.fromNullable(localUuid).or(Optional.fromNullable(localE164));

      if (local.isPresent()) {
        SignalContactRecord merged = mergeContacts(remote, local.get());

        if (!merged.equals(remote)) {
          remoteUpdates.add(new ContactUpdate(remote, merged));
        }

        if (!merged.equals(local.get())) {
          localUpdates.add(new ContactUpdate(local.get(), merged));
        }

        localInserts.remove(remote);
        remoteInserts.remove(local.get());
      }
    }

    return new ContactRecordMergeResult(localInserts, localUpdates, remoteInserts, remoteUpdates);
  }

  public static final class ContactUpdate {
    private final SignalContactRecord oldContact;
    private final SignalContactRecord newContact;

    public ContactUpdate(@NonNull SignalContactRecord oldContact, @NonNull SignalContactRecord newContact) {
      this.oldContact = oldContact;
      this.newContact = newContact;
    }

    public @NonNull
    SignalContactRecord getOldContact() {
      return oldContact;
    }

    public @NonNull
    SignalContactRecord getNewContact() {
      return newContact;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      ContactUpdate that = (ContactUpdate) o;
      return oldContact.equals(that.oldContact) &&
          newContact.equals(that.newContact);
    }

    @Override
    public int hashCode() {
      return Objects.hash(oldContact, newContact);
    }
  }

  public static final class KeyDifferenceResult {
    private final List<byte[]> remoteOnlyKeys;
    private final List<byte[]> localOnlyKeys;

    private KeyDifferenceResult(@NonNull List<byte[]> remoteOnlyKeys, @NonNull List<byte[]> localOnlyKeys) {
      this.remoteOnlyKeys = remoteOnlyKeys;
      this.localOnlyKeys  = localOnlyKeys;
    }

    public @NonNull List<byte[]> getRemoteOnlyKeys() {
      return remoteOnlyKeys;
    }

    public @NonNull List<byte[]> getLocalOnlyKeys() {
      return localOnlyKeys;
    }

    public boolean isEmpty() {
      return remoteOnlyKeys.isEmpty() && localOnlyKeys.isEmpty();
    }
  }

  public static final class MergeResult {
    private final Set<SignalContactRecord> localContactInserts;
    private final Set<ContactUpdate>       localContactUpdates;
    private final Set<SignalContactRecord> remoteContactInserts;
    private final Set<ContactUpdate>       remoteContactUpdates;
    private final Set<SignalStorageRecord> localUnknownInserts;
    private final Set<SignalStorageRecord> localUnknownDeletes;

    @VisibleForTesting
    MergeResult(@NonNull Set<SignalContactRecord> localContactInserts,
                @NonNull Set<ContactUpdate> localContactUpdates,
                @NonNull Set<SignalContactRecord> remoteContactInserts,
                @NonNull Set<ContactUpdate> remoteContactUpdates,
                @NonNull Set<SignalStorageRecord> localUnknownInserts,
                @NonNull Set<SignalStorageRecord> localUnknownDeletes)
    {
      this.localContactInserts  = localContactInserts;
      this.localContactUpdates  = localContactUpdates;
      this.remoteContactInserts = remoteContactInserts;
      this.remoteContactUpdates = remoteContactUpdates;
      this.localUnknownInserts  = localUnknownInserts;
      this.localUnknownDeletes  = localUnknownDeletes;
    }

    public @NonNull Set<SignalContactRecord> getLocalContactInserts() {
      return localContactInserts;
    }

    public @NonNull Set<ContactUpdate> getLocalContactUpdates() {
      return localContactUpdates;
    }

    public @NonNull Set<SignalContactRecord> getRemoteContactInserts() {
      return remoteContactInserts;
    }

    public @NonNull Set<ContactUpdate> getRemoteContactUpdates() {
      return remoteContactUpdates;
    }

    public @NonNull Set<SignalStorageRecord> getLocalUnknownInserts() {
      return localUnknownInserts;
    }

    public @NonNull Set<SignalStorageRecord> getLocalUnknownDeletes() {
      return localUnknownDeletes;
    }
  }

  public static final class WriteOperationResult {
    private final SignalStorageManifest     manifest;
    private final List<SignalStorageRecord> inserts;
    private final List<byte[]>              deletes;

    private WriteOperationResult(@NonNull SignalStorageManifest manifest,
                                 @NonNull List<SignalStorageRecord> inserts,
                                 @NonNull List<byte[]> deletes)
    {
      this.manifest = manifest;
      this.inserts  = inserts;
      this.deletes  = deletes;
    }

    public @NonNull SignalStorageManifest getManifest() {
      return manifest;
    }

    public @NonNull List<SignalStorageRecord> getInserts() {
      return inserts;
    }

    public @NonNull List<byte[]> getDeletes() {
      return deletes;
    }
  }

  public static class LocalWriteResult {
    private final WriteOperationResult     writeResult;
    private final Map<RecipientId, byte[]> storageKeyUpdates;

    public LocalWriteResult(WriteOperationResult writeResult, Map<RecipientId, byte[]> storageKeyUpdates) {
      this.writeResult       = writeResult;
      this.storageKeyUpdates = storageKeyUpdates;
    }

    public @NonNull WriteOperationResult getWriteResult() {
      return writeResult;
    }

    public @NonNull Map<RecipientId, byte[]> getStorageKeyUpdates() {
      return storageKeyUpdates;
    }
  }

  private static final class ContactRecordMergeResult {
    final Set<SignalContactRecord> localInserts;
    final Set<ContactUpdate> localUpdates;
    final Set<SignalContactRecord> remoteInserts;
    final Set<ContactUpdate> remoteUpdates;

    ContactRecordMergeResult(@NonNull Set<SignalContactRecord> localInserts,
                             @NonNull Set<ContactUpdate> localUpdates,
                             @NonNull Set<SignalContactRecord> remoteInserts,
                             @NonNull Set<ContactUpdate> remoteUpdates)
    {
      this.localInserts = localInserts;
      this.localUpdates = localUpdates;
      this.remoteInserts = remoteInserts;
      this.remoteUpdates = remoteUpdates;
    }
  }

  interface KeyGenerator {
    @NonNull byte[] generate();
  }
}
