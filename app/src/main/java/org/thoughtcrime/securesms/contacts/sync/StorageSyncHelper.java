package org.thoughtcrime.securesms.contacts.sync;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.RecipientDatabase.RecipientSettings;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.SetUtil;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.storage.SignalContactRecord;
import org.whispersystems.signalservice.api.storage.SignalContactRecord.IdentityState;
import org.whispersystems.signalservice.api.storage.SignalGroupV1Record;
import org.whispersystems.signalservice.api.storage.SignalStorageManifest;
import org.whispersystems.signalservice.api.storage.SignalStorageRecord;
import org.whispersystems.signalservice.api.util.OptionalUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import javax.crypto.KeyGenerator;

public final class StorageSyncHelper {

  private static final String TAG = Log.tag(StorageSyncHelper.class);

  private static final KeyGenerator KEY_GENERATOR = () -> Util.getSecretBytes(16);

  private static KeyGenerator testKeyGenerator = null;

  /**
   * Given the local state of pending storage mutations, this will generate a result that will
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
    Set<SignalStorageRecord> storageInserts    = new LinkedHashSet<>();
    Set<ByteBuffer>          storageDeletes    = new LinkedHashSet<>();
    Map<RecipientId, byte[]> storageKeyUpdates = new HashMap<>();

    for (RecipientSettings insert : inserts) {
      storageInserts.add(localToRemoteRecord(insert));
    }

    for (RecipientSettings delete : deletes) {
      byte[] key = Objects.requireNonNull(delete.getStorageKey());
      storageDeletes.add(ByteBuffer.wrap(key));
      completeKeys.remove(ByteBuffer.wrap(key));
    }

    for (RecipientSettings update : updates) {
      byte[] oldKey = Objects.requireNonNull(update.getStorageKey());
      byte[] newKey = generateKey();

      storageInserts.add(localToRemoteRecord(update, newKey));
      storageDeletes.add(ByteBuffer.wrap(oldKey));
      completeKeys.remove(ByteBuffer.wrap(oldKey));
      completeKeys.add(ByteBuffer.wrap(newKey));
      storageKeyUpdates.put(update.getId(), newKey);
    }

    if (storageInserts.isEmpty() && storageDeletes.isEmpty()) {
      return Optional.absent();
    } else {
      List<byte[]>              contactDeleteBytes   = Stream.of(storageDeletes).map(ByteBuffer::array).toList();
      List<byte[]>              completeKeysBytes    = Stream.of(completeKeys).map(ByteBuffer::array).toList();
      SignalStorageManifest     manifest             = new SignalStorageManifest(currentManifestVersion + 1, completeKeysBytes);
      WriteOperationResult      writeOperationResult = new WriteOperationResult(manifest, new ArrayList<>(storageInserts), contactDeleteBytes);

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

    List<SignalGroupV1Record> remoteOnlyGroupV1 = Stream.of(remoteOnlyRecords).filter(r -> r.getGroupV1().isPresent()).map(r -> r.getGroupV1().get()).toList();
    List<SignalGroupV1Record> localOnlyGroupV1  = Stream.of(localOnlyRecords).filter(r -> r.getGroupV1().isPresent()).map(r -> r.getGroupV1().get()).toList();

    List<SignalStorageRecord> remoteOnlyUnknowns = Stream.of(remoteOnlyRecords).filter(SignalStorageRecord::isUnknown).toList();
    List<SignalStorageRecord> localOnlyUnknowns  = Stream.of(localOnlyRecords).filter(SignalStorageRecord::isUnknown).toList();

    ContactRecordMergeResult contactMergeResult = resolveContactConflict(remoteOnlyContacts, localOnlyContacts);
    GroupV1RecordMergeResult groupV1MergeResult = resolveGroupV1Conflict(remoteOnlyGroupV1, localOnlyGroupV1);

    Set<SignalStorageRecord> remoteInserts = new HashSet<>();
    remoteInserts.addAll(Stream.of(contactMergeResult.remoteInserts).map(SignalStorageRecord::forContact).toList());
    remoteInserts.addAll(Stream.of(groupV1MergeResult.remoteInserts).map(SignalStorageRecord::forGroupV1).toList());

    Set<RecordUpdate> remoteUpdates = new HashSet<>();
    remoteUpdates.addAll(Stream.of(contactMergeResult.remoteUpdates)
                               .map(c -> new RecordUpdate(SignalStorageRecord.forContact(c.getOld()), SignalStorageRecord.forContact(c.getNew())))
                               .toList());
    remoteUpdates.addAll(Stream.of(groupV1MergeResult.remoteUpdates)
                               .map(c -> new RecordUpdate(SignalStorageRecord.forGroupV1(c.getOld()), SignalStorageRecord.forGroupV1(c.getNew())))
                               .toList());

    return new MergeResult(contactMergeResult.localInserts,
                           contactMergeResult.localUpdates,
                           groupV1MergeResult.localInserts,
                           groupV1MergeResult.localUpdates,
                           new LinkedHashSet<>(remoteOnlyUnknowns),
                           new LinkedHashSet<>(localOnlyUnknowns),
                           remoteInserts,
                           remoteUpdates);
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

    for (SignalGroupV1Record insert : mergeResult.getLocalGroupV1Inserts()) {
      completeKeys.add(ByteBuffer.wrap(insert.getKey()));
    }

    for (SignalStorageRecord insert : mergeResult.getRemoteInserts()) {
      completeKeys.add(ByteBuffer.wrap(insert.getKey()));
    }

    for (SignalStorageRecord insert : mergeResult.getLocalUnknownInserts()) {
      completeKeys.add(ByteBuffer.wrap(insert.getKey()));
    }

    for (ContactUpdate update : mergeResult.getLocalContactUpdates()) {
      completeKeys.remove(ByteBuffer.wrap(update.getOld().getKey()));
      completeKeys.add(ByteBuffer.wrap(update.getNew().getKey()));
    }

    for (GroupV1Update update : mergeResult.getLocalGroupV1Updates()) {
      completeKeys.remove(ByteBuffer.wrap(update.getOld().getKey()));
      completeKeys.add(ByteBuffer.wrap(update.getNew().getKey()));
    }

    for (RecordUpdate update : mergeResult.getRemoteUpdates()) {
      completeKeys.remove(ByteBuffer.wrap(update.getOld().getKey()));
      completeKeys.add(ByteBuffer.wrap(update.getNew().getKey()));
    }

    SignalStorageManifest manifest = new SignalStorageManifest(currentManifestVersion + 1, Stream.of(completeKeys).map(ByteBuffer::array).toList());

    List<SignalStorageRecord> inserts = new ArrayList<>();
    inserts.addAll(mergeResult.getRemoteInserts());
    inserts.addAll(Stream.of(mergeResult.getRemoteUpdates()).map(RecordUpdate::getNew).toList());

    List<byte[]> deletes = Stream.of(mergeResult.getRemoteUpdates()).map(RecordUpdate::getOld).map(SignalStorageRecord::getKey).toList();

    return new WriteOperationResult(manifest, inserts, deletes);
  }

  public static @NonNull SignalStorageRecord localToRemoteRecord(@NonNull RecipientSettings settings) {
    if (settings.getStorageKey() == null) {
      throw new AssertionError("Must have a storage key!");
    }

    return localToRemoteRecord(settings, settings.getStorageKey());
  }

  public static @NonNull SignalStorageRecord localToRemoteRecord(@NonNull RecipientSettings settings, @NonNull byte[] key) {
    if (settings.getGroupType() == RecipientDatabase.GroupType.NONE) {
      return SignalStorageRecord.forContact(localToRemoteContact(settings, key));
    } else if (settings.getGroupType() == RecipientDatabase.GroupType.SIGNAL_V1) {
      return SignalStorageRecord.forGroupV1(localToRemoteGroupV1(settings, key));
    } else {
      throw new AssertionError("Unsupported type!");
    }
  }

  private static @NonNull SignalContactRecord localToRemoteContact(@NonNull RecipientSettings recipient, byte[] storageKey) {
    if (recipient.getUuid() == null && recipient.getE164() == null) {
      throw new AssertionError("Must have either a UUID or a phone number!");
    }

    return new SignalContactRecord.Builder(storageKey, new SignalServiceAddress(recipient.getUuid(), recipient.getE164()))
                                   .setProfileKey(recipient.getProfileKey())
                                   .setGivenName(recipient.getProfileName().getGivenName())
                                   .setFamilyName(recipient.getProfileName().getFamilyName())
                                   .setBlocked(recipient.isBlocked())
                                   .setProfileSharingEnabled(recipient.isProfileSharing())
                                   .setIdentityKey(recipient.getIdentityKey())
                                   .setIdentityState(localToRemoteIdentityState(recipient.getIdentityStatus()))
                                   .build();
  }

  private static @NonNull SignalGroupV1Record localToRemoteGroupV1(@NonNull RecipientSettings recipient, byte[] storageKey) {
    if (recipient.getGroupId() == null) {
      throw new AssertionError("Must have a groupId!");
    }

    return new SignalGroupV1Record.Builder(storageKey, GroupUtil.getDecodedIdOrThrow(recipient.getGroupId()))
                                  .setBlocked(recipient.isBlocked())
                                  .setProfileSharingEnabled(recipient.isProfileSharing())
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
    String               givenName      = remote.getGivenName().or(local.getGivenName()).or("");
    String               familyName     = remote.getFamilyName().or(local.getFamilyName()).or("");
    byte[]               profileKey     = remote.getProfileKey().or(local.getProfileKey()).orNull();
    String               username       = remote.getUsername().or(local.getUsername()).or("");
    IdentityState        identityState  = remote.getIdentityState();
    byte[]               identityKey    = remote.getIdentityKey().or(local.getIdentityKey()).orNull();
    String               nickname       = local.getNickname().or(""); // TODO [greyson] Update this when we add real nickname support
    boolean              blocked        = remote.isBlocked();
    boolean              profileSharing = remote.isProfileSharingEnabled() || local.isProfileSharingEnabled();
    boolean              matchesRemote  = doParamsMatchContact(remote, address, givenName, familyName, profileKey, username, identityState, identityKey, blocked, profileSharing, nickname);
    boolean              matchesLocal   = doParamsMatchContact(local, address, givenName, familyName, profileKey, username, identityState, identityKey, blocked, profileSharing, nickname);

    if (remote.getProtoVersion() > 0) {
      Log.w(TAG, "Inbound model has version " + remote.getProtoVersion() + ", but our version is 0.");
    }

    if (matchesRemote) {
      return remote;
    } else if (matchesLocal) {
      return local;
    } else {
      return new SignalContactRecord.Builder(generateKey(), address)
                                    .setGivenName(givenName)
                                    .setFamilyName(familyName)
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
  static @NonNull SignalGroupV1Record mergeGroupV1(@NonNull SignalGroupV1Record remote,
                                                   @NonNull SignalGroupV1Record local)
  {
    boolean blocked        = remote.isBlocked();
    boolean profileSharing = remote.isProfileSharingEnabled() || local.isProfileSharingEnabled();

    boolean matchesRemote = blocked == remote.isBlocked() && profileSharing == remote.isProfileSharingEnabled();
    boolean matchesLocal  = blocked == local.isBlocked()  && profileSharing == local.isProfileSharingEnabled();

    if (matchesRemote) {
      return remote;
    } else if (matchesLocal) {
      return local;
    } else {
      return new SignalGroupV1Record.Builder(generateKey(), remote.getGroupId())
                                    .setBlocked(blocked)
                                    .setProfileSharingEnabled(blocked)
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
                                              @Nullable String givenName,
                                              @Nullable String familyName,
                                              @Nullable byte[] profileKey,
                                              @Nullable String username,
                                              @Nullable IdentityState identityState,
                                              @Nullable byte[] identityKey,
                                              boolean blocked,
                                              boolean profileSharing,
                                              @Nullable String nickname)
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
           Objects.equals(contact.getNickname().or(""), nickname);
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

  private static @NonNull GroupV1RecordMergeResult resolveGroupV1Conflict(@NonNull Collection<SignalGroupV1Record> remoteOnlyRecords,
                                                                          @NonNull Collection<SignalGroupV1Record> localOnlyRecords)
  {
    Map<String, SignalGroupV1Record> remoteByGroupId = Stream.of(remoteOnlyRecords).collect(Collectors.toMap(g -> GroupUtil.getEncodedId(g.getGroupId(), false), g -> g));
    Map<String, SignalGroupV1Record> localByGroupId  = Stream.of(localOnlyRecords).collect(Collectors.toMap(g -> GroupUtil.getEncodedId(g.getGroupId(), false), g -> g));

    Set<SignalGroupV1Record> localInserts  = new LinkedHashSet<>(remoteOnlyRecords);
    Set<SignalGroupV1Record> remoteInserts = new LinkedHashSet<>(localOnlyRecords);
    Set<GroupV1Update>       localUpdates  = new LinkedHashSet<>();
    Set<GroupV1Update>       remoteUpdates = new LinkedHashSet<>();

    for (Map.Entry<String, SignalGroupV1Record> entry : remoteByGroupId.entrySet()) {
      SignalGroupV1Record remote = entry.getValue();
      SignalGroupV1Record local  = localByGroupId.get(entry.getKey());

      if (local != null) {
        SignalGroupV1Record merged = mergeGroupV1(remote, local);

        if (!merged.equals(remote)) {
          remoteUpdates.add(new GroupV1Update(remote, merged));
        }

        if (!merged.equals(local)) {
          localUpdates.add(new GroupV1Update(local, merged));
        }

        localInserts.remove(remote);
        remoteInserts.remove(local);
      }
    }

    return new GroupV1RecordMergeResult(localInserts, localUpdates, remoteInserts, remoteUpdates);
  }

  public static final class ContactUpdate {
    private final SignalContactRecord oldContact;
    private final SignalContactRecord newContact;

    ContactUpdate(@NonNull SignalContactRecord oldContact, @NonNull SignalContactRecord newContact) {
      this.oldContact = oldContact;
      this.newContact = newContact;
    }

    public @NonNull SignalContactRecord getOld() {
      return oldContact;
    }

    public @NonNull SignalContactRecord getNew() {
      return newContact;
    }

    public boolean profileKeyChanged() {
      return !OptionalUtil.byteArrayEquals(oldContact.getProfileKey(), newContact.getProfileKey());
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

  public static final class GroupV1Update {
    private final SignalGroupV1Record oldGroup;
    private final SignalGroupV1Record newGroup;


    public GroupV1Update(@NonNull SignalGroupV1Record oldGroup, @NonNull SignalGroupV1Record newGroup) {
      this.oldGroup = oldGroup;
      this.newGroup = newGroup;
    }

    public @NonNull SignalGroupV1Record getOld() {
      return oldGroup;
    }

    public @NonNull SignalGroupV1Record getNew() {
      return newGroup;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      GroupV1Update that = (GroupV1Update) o;
      return oldGroup.equals(that.oldGroup) &&
          newGroup.equals(that.newGroup);
    }

    @Override
    public int hashCode() {
      return Objects.hash(oldGroup, newGroup);
    }
  }

  @VisibleForTesting
  static class RecordUpdate {
    private final SignalStorageRecord oldRecord;
    private final SignalStorageRecord newRecord;

    RecordUpdate(@NonNull SignalStorageRecord oldRecord, @NonNull SignalStorageRecord newRecord) {
      this.oldRecord = oldRecord;
      this.newRecord = newRecord;
    }

    public @NonNull SignalStorageRecord getOld() {
      return oldRecord;
    }

    public @NonNull SignalStorageRecord getNew() {
      return newRecord;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      RecordUpdate that = (RecordUpdate) o;
      return oldRecord.equals(that.oldRecord) &&
          newRecord.equals(that.newRecord);
    }

    @Override
    public int hashCode() {
      return Objects.hash(oldRecord, newRecord);
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
    private final Set<SignalGroupV1Record> localGroupV1Inserts;
    private final Set<GroupV1Update>       localGroupV1Updates;
    private final Set<SignalStorageRecord> localUnknownInserts;
    private final Set<SignalStorageRecord> localUnknownDeletes;
    private final Set<SignalStorageRecord> remoteInserts;
    private final Set<RecordUpdate>        remoteUpdates;

    @VisibleForTesting
    MergeResult(@NonNull Set<SignalContactRecord> localContactInserts,
                @NonNull Set<ContactUpdate>       localContactUpdates,
                @NonNull Set<SignalGroupV1Record> localGroupV1Inserts,
                @NonNull Set<GroupV1Update>       localGroupV1Updates,
                @NonNull Set<SignalStorageRecord> localUnknownInserts,
                @NonNull Set<SignalStorageRecord> localUnknownDeletes,
                @NonNull Set<SignalStorageRecord> remoteInserts,
                @NonNull Set<RecordUpdate>        remoteUpdates)
    {
      this.localContactInserts  = localContactInserts;
      this.localContactUpdates  = localContactUpdates;
      this.localGroupV1Inserts  = localGroupV1Inserts;
      this.localGroupV1Updates  = localGroupV1Updates;
      this.localUnknownInserts  = localUnknownInserts;
      this.localUnknownDeletes  = localUnknownDeletes;
      this.remoteInserts        = remoteInserts;
      this.remoteUpdates        = remoteUpdates;
    }

    public @NonNull Set<SignalContactRecord> getLocalContactInserts() {
      return localContactInserts;
    }

    public @NonNull Set<ContactUpdate> getLocalContactUpdates() {
      return localContactUpdates;
    }

    public @NonNull Set<SignalGroupV1Record> getLocalGroupV1Inserts() {
      return localGroupV1Inserts;
    }

    public @NonNull Set<GroupV1Update> getLocalGroupV1Updates() {
      return localGroupV1Updates;
    }

    public @NonNull Set<SignalStorageRecord> getLocalUnknownInserts() {
      return localUnknownInserts;
    }

    public @NonNull Set<SignalStorageRecord> getLocalUnknownDeletes() {
      return localUnknownDeletes;
    }

    public @NonNull Set<SignalStorageRecord> getRemoteInserts() {
      return remoteInserts;
    }

    public @NonNull Set<RecordUpdate> getRemoteUpdates() {
      return remoteUpdates;
    }

    @Override
    public @NonNull String toString() {
      return String.format(Locale.ENGLISH,
                           "localContactInserts: %d, localContactUpdates: %d, localGroupInserts: %d, localGroupUpdates: %d, localUnknownInserts: %d, localUnknownDeletes: %d, remoteInserts: %d, remoteUpdates: %d",
                           localContactInserts.size(), localContactUpdates.size(), localGroupV1Inserts.size(), localGroupV1Updates.size(), localUnknownInserts.size(), localUnknownDeletes.size(), remoteInserts.size(), remoteUpdates.size());
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

    public boolean isEmpty() {
      return inserts.isEmpty() && deletes.isEmpty();
    }

    @Override
    public @NonNull String toString() {
      return String.format(Locale.ENGLISH,
                           "ManifestVersion: %d, Total Keys: %d, Inserts: %d, Deletes: %d",
                           manifest.getVersion(),
                           manifest.getStorageKeys().size(),
                           inserts.size(),
                           deletes.size());
    }
  }

  public static class LocalWriteResult {
    private final WriteOperationResult     writeResult;
    private final Map<RecipientId, byte[]> storageKeyUpdates;

    private LocalWriteResult(WriteOperationResult writeResult, Map<RecipientId, byte[]> storageKeyUpdates) {
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
    final Set<ContactUpdate>       localUpdates;
    final Set<SignalContactRecord> remoteInserts;
    final Set<ContactUpdate>       remoteUpdates;

    ContactRecordMergeResult(@NonNull Set<SignalContactRecord> localInserts,
                             @NonNull Set<ContactUpdate> localUpdates,
                             @NonNull Set<SignalContactRecord> remoteInserts,
                             @NonNull Set<ContactUpdate> remoteUpdates)
    {
      this.localInserts  = localInserts;
      this.localUpdates  = localUpdates;
      this.remoteInserts = remoteInserts;
      this.remoteUpdates = remoteUpdates;
    }
  }

  private static final class GroupV1RecordMergeResult {
    final Set<SignalGroupV1Record> localInserts;
    final Set<GroupV1Update>       localUpdates;
    final Set<SignalGroupV1Record> remoteInserts;
    final Set<GroupV1Update>       remoteUpdates;

    GroupV1RecordMergeResult(@NonNull Set<SignalGroupV1Record> localInserts,
                             @NonNull Set<GroupV1Update> localUpdates,
                             @NonNull Set<SignalGroupV1Record> remoteInserts,
                             @NonNull Set<GroupV1Update> remoteUpdates)
    {
      this.localInserts  = localInserts;
      this.localUpdates  = localUpdates;
      this.remoteInserts = remoteInserts;
      this.remoteUpdates = remoteUpdates;
    }
  }

  interface KeyGenerator {
    @NonNull byte[] generate();
  }
}
