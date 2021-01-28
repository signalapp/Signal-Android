package org.thoughtcrime.securesms.storage;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.RecipientDatabase.RecipientSettings;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobs.RetrieveProfileAvatarJob;
import org.thoughtcrime.securesms.jobs.StorageSyncJob;
import org.thoughtcrime.securesms.keyvalue.PhoneNumberPrivacyValues;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.SetUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.storage.SignalAccountRecord;
import org.whispersystems.signalservice.api.storage.SignalContactRecord;
import org.whispersystems.signalservice.api.storage.SignalGroupV1Record;
import org.whispersystems.signalservice.api.storage.SignalGroupV2Record;
import org.whispersystems.signalservice.api.storage.SignalRecord;
import org.whispersystems.signalservice.api.storage.SignalStorageManifest;
import org.whispersystems.signalservice.api.storage.SignalStorageRecord;
import org.whispersystems.signalservice.api.storage.StorageId;
import org.whispersystems.signalservice.api.util.OptionalUtil;
import org.whispersystems.signalservice.internal.storage.protos.ManifestRecord;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public final class StorageSyncHelper {

  private static final String TAG = Log.tag(StorageSyncHelper.class);

  private static final KeyGenerator KEY_GENERATOR = () -> Util.getSecretBytes(16);

  private static KeyGenerator keyGenerator = KEY_GENERATOR;

  private static final long REFRESH_INTERVAL = TimeUnit.HOURS.toMillis(2);

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
                                                                                @NonNull List<StorageId> currentLocalKeys,
                                                                                @NonNull List<RecipientSettings> updates,
                                                                                @NonNull List<RecipientSettings> inserts,
                                                                                @NonNull List<RecipientSettings> deletes,
                                                                                @NonNull Optional<SignalAccountRecord> accountUpdate,
                                                                                @NonNull Optional<SignalAccountRecord> accountInsert)
  {
    int accountCount = Stream.of(currentLocalKeys)
                             .filter(id -> id.getType() == ManifestRecord.Identifier.Type.ACCOUNT_VALUE)
                             .toList()
                             .size();

    if (accountCount > 1) {
      throw new MultipleExistingAccountsException();
    }

    Optional<StorageId> accountId = Optional.fromNullable(Stream.of(currentLocalKeys)
                                            .filter(id -> id.getType() == ManifestRecord.Identifier.Type.ACCOUNT_VALUE)
                                            .findFirst()
                                            .orElse(null));


    if (accountId.isPresent() && accountInsert.isPresent() && !accountInsert.get().getId().equals(accountId.get())) {
      throw new InvalidAccountInsertException();
    }

    if (accountId.isPresent() && accountUpdate.isPresent() && !accountUpdate.get().getId().equals(accountId.get())) {
      throw new InvalidAccountUpdateException();
    }

    if (accountUpdate.isPresent() && accountInsert.isPresent()) {
      throw new InvalidAccountDualInsertUpdateException();
    }

    Set<StorageId>           completeIds       = new LinkedHashSet<>(currentLocalKeys);
    Set<SignalStorageRecord> storageInserts    = new LinkedHashSet<>();
    Set<ByteBuffer>          storageDeletes    = new LinkedHashSet<>();
    Map<RecipientId, byte[]> storageKeyUpdates = new HashMap<>();

    for (RecipientSettings insert : inserts) {
      if (insert.getGroupType() == RecipientDatabase.GroupType.SIGNAL_V2 && insert.getSyncExtras().getGroupMasterKey() == null) {
        Log.w(TAG, "Missing master key on gv2 recipient");
        continue;
      }

      storageInserts.add(StorageSyncModels.localToRemoteRecord(insert));

      switch (insert.getGroupType()) {
        case NONE:
          completeIds.add(StorageId.forContact(insert.getStorageId()));
          break;
        case SIGNAL_V1:
          completeIds.add(StorageId.forGroupV1(insert.getStorageId()));
          break;
        case SIGNAL_V2:
          completeIds.add(StorageId.forGroupV2(insert.getStorageId()));
          break;
        default:
          throw new AssertionError("Unsupported type!");
      }
    }

    if (accountInsert.isPresent()) {
      storageInserts.add(SignalStorageRecord.forAccount(accountInsert.get()));
      completeIds.add(accountInsert.get().getId());
    }

    for (RecipientSettings delete : deletes) {
      byte[] key = Objects.requireNonNull(delete.getStorageId());
      storageDeletes.add(ByteBuffer.wrap(key));
      completeIds.remove(StorageId.forContact(key));
    }

    for (RecipientSettings update : updates) {
      StorageId oldId;
      StorageId newId;

      switch (update.getGroupType()) {
        case NONE:
          oldId = StorageId.forContact(update.getStorageId());
          newId = StorageId.forContact(generateKey());
          break;
        case SIGNAL_V1:
          oldId = StorageId.forGroupV1(update.getStorageId());
          newId = StorageId.forGroupV1(generateKey());
          break;
        case SIGNAL_V2:
          oldId = StorageId.forGroupV2(update.getStorageId());
          newId = StorageId.forGroupV2(generateKey());
          break;
        default:
          throw new AssertionError("Unsupported type!");
      }

      storageInserts.add(StorageSyncModels.localToRemoteRecord(update, newId.getRaw()));
      storageDeletes.add(ByteBuffer.wrap(oldId.getRaw()));
      completeIds.remove(oldId);
      completeIds.add(newId);
      storageKeyUpdates.put(update.getId(), newId.getRaw());
    }

    if (accountUpdate.isPresent()) {
      StorageId oldId = accountUpdate.get().getId();
      StorageId newId = StorageId.forAccount(generateKey());

      storageInserts.add(SignalStorageRecord.forAccount(newId, accountUpdate.get()));
      storageDeletes.add(ByteBuffer.wrap(oldId.getRaw()));
      completeIds.remove(oldId);
      completeIds.add(newId);
      storageKeyUpdates.put(Recipient.self().getId(), newId.getRaw());
    }

    if (storageInserts.isEmpty() && storageDeletes.isEmpty()) {
      return Optional.absent();
    } else {
      List<byte[]>          storageDeleteBytes   = Stream.of(storageDeletes).map(ByteBuffer::array).toList();
      List<StorageId>       completeIdsBytes     = new ArrayList<>(completeIds);
      SignalStorageManifest manifest             = new SignalStorageManifest(currentManifestVersion + 1, completeIdsBytes);
      WriteOperationResult  writeOperationResult = new WriteOperationResult(manifest, new ArrayList<>(storageInserts), storageDeleteBytes);

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
  public static @NonNull KeyDifferenceResult findKeyDifference(@NonNull Collection<StorageId> remoteKeys,
                                                               @NonNull Collection<StorageId> localKeys)
  {
    Map<String, StorageId> remoteByRawId = Stream.of(remoteKeys).collect(Collectors.toMap(id -> Base64.encodeBytes(id.getRaw()), id -> id));
    Map<String, StorageId> localByRawId  = Stream.of(localKeys).collect(Collectors.toMap(id -> Base64.encodeBytes(id.getRaw()), id -> id));

    boolean hasTypeMismatch = remoteByRawId.size() != remoteKeys.size() || localByRawId.size() != localKeys.size();

    Set<String> remoteOnlyRawIds = SetUtil.difference(remoteByRawId.keySet(), localByRawId.keySet());
    Set<String> localOnlyRawIds  = SetUtil.difference(localByRawId.keySet(), remoteByRawId.keySet());
    Set<String> sharedRawIds     = SetUtil.intersection(localByRawId.keySet(), remoteByRawId.keySet());

    for (String rawId : sharedRawIds) {
      StorageId remote = Objects.requireNonNull(remoteByRawId.get(rawId));
      StorageId local  = Objects.requireNonNull(localByRawId.get(rawId));

      if (remote.getType() != local.getType()) {
        remoteOnlyRawIds.remove(rawId);
        localOnlyRawIds.remove(rawId);
        hasTypeMismatch = true;
      }
    }

    List<StorageId> remoteOnlyKeys = Stream.of(remoteOnlyRawIds).map(remoteByRawId::get).toList();
    List<StorageId> localOnlyKeys  = Stream.of(localOnlyRawIds).map(localByRawId::get).toList();

    return new KeyDifferenceResult(remoteOnlyKeys, localOnlyKeys, hasTypeMismatch);
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
                                                     @NonNull Collection<SignalStorageRecord> localOnlyRecords,
                                                     @NonNull GroupV2ExistenceChecker groupExistenceChecker)
  {
    List<SignalContactRecord> remoteOnlyContacts = Stream.of(remoteOnlyRecords).filter(r -> r.getContact().isPresent()).map(r -> r.getContact().get()).toList();
    List<SignalContactRecord> localOnlyContacts  = Stream.of(localOnlyRecords).filter(r -> r.getContact().isPresent()).map(r -> r.getContact().get()).toList();

    List<SignalGroupV1Record> remoteOnlyGroupV1 = Stream.of(remoteOnlyRecords).filter(r -> r.getGroupV1().isPresent()).map(r -> r.getGroupV1().get()).toList();
    List<SignalGroupV1Record> localOnlyGroupV1  = Stream.of(localOnlyRecords).filter(r -> r.getGroupV1().isPresent()).map(r -> r.getGroupV1().get()).toList();
    
    List<SignalGroupV2Record> remoteOnlyGroupV2 = Stream.of(remoteOnlyRecords).filter(r -> r.getGroupV2().isPresent()).map(r -> r.getGroupV2().get()).toList();
    List<SignalGroupV2Record> localOnlyGroupV2  = Stream.of(localOnlyRecords).filter(r -> r.getGroupV2().isPresent()).map(r -> r.getGroupV2().get()).toList();

    List<SignalStorageRecord> remoteOnlyUnknowns = Stream.of(remoteOnlyRecords).filter(SignalStorageRecord::isUnknown).toList();
    List<SignalStorageRecord> localOnlyUnknowns  = Stream.of(localOnlyRecords).filter(SignalStorageRecord::isUnknown).toList();

    List<SignalAccountRecord> remoteOnlyAccount = Stream.of(remoteOnlyRecords).filter(r -> r.getAccount().isPresent()).map(r -> r.getAccount().get()).toList();
    List<SignalAccountRecord> localOnlyAccount  = Stream.of(localOnlyRecords).filter(r -> r.getAccount().isPresent()).map(r -> r.getAccount().get()).toList();
    if (remoteOnlyAccount.size() > 0 && localOnlyAccount.isEmpty()) {
      throw new AssertionError("Found a remote-only account, but no local-only account!");
    }
    if (localOnlyAccount.size() > 1) {
      throw new AssertionError("Multiple local accounts?");
    }

    RecordMergeResult<SignalContactRecord> contactMergeResult = resolveRecordConflict(remoteOnlyContacts, localOnlyContacts, new ContactConflictMerger(localOnlyContacts, Recipient.self()));
    RecordMergeResult<SignalGroupV1Record> groupV1MergeResult = resolveRecordConflict(remoteOnlyGroupV1, localOnlyGroupV1, new GroupV1ConflictMerger(localOnlyGroupV1, groupExistenceChecker));
    RecordMergeResult<SignalGroupV2Record> groupV2MergeResult = resolveRecordConflict(remoteOnlyGroupV2, localOnlyGroupV2, new GroupV2ConflictMerger(localOnlyGroupV2));
    RecordMergeResult<SignalAccountRecord> accountMergeResult = resolveRecordConflict(remoteOnlyAccount, localOnlyAccount, new AccountConflictMerger(localOnlyAccount.isEmpty() ? Optional.absent() : Optional.of(localOnlyAccount.get(0))));

    Set<SignalStorageRecord> remoteInserts = new HashSet<>();
    remoteInserts.addAll(Stream.of(contactMergeResult.remoteInserts).map(SignalStorageRecord::forContact).toList());
    remoteInserts.addAll(Stream.of(groupV1MergeResult.remoteInserts).map(SignalStorageRecord::forGroupV1).toList());
    remoteInserts.addAll(Stream.of(groupV2MergeResult.remoteInserts).map(SignalStorageRecord::forGroupV2).toList());
    remoteInserts.addAll(Stream.of(accountMergeResult.remoteInserts).map(SignalStorageRecord::forAccount).toList());

    Set<RecordUpdate<SignalStorageRecord>> remoteUpdates = new HashSet<>();
    remoteUpdates.addAll(Stream.of(contactMergeResult.remoteUpdates)
                               .map(c -> new RecordUpdate<>(SignalStorageRecord.forContact(c.getOld()), SignalStorageRecord.forContact(c.getNew())))
                               .toList());
    remoteUpdates.addAll(Stream.of(groupV1MergeResult.remoteUpdates)
                               .map(c -> new RecordUpdate<>(SignalStorageRecord.forGroupV1(c.getOld()), SignalStorageRecord.forGroupV1(c.getNew())))
                               .toList());
    remoteUpdates.addAll(Stream.of(groupV2MergeResult.remoteUpdates)
                               .map(c -> new RecordUpdate<>(SignalStorageRecord.forGroupV2(c.getOld()), SignalStorageRecord.forGroupV2(c.getNew())))
                               .toList());
    remoteUpdates.addAll(Stream.of(accountMergeResult.remoteUpdates)
                               .map(c -> new RecordUpdate<>(SignalStorageRecord.forAccount(c.getOld()), SignalStorageRecord.forAccount(c.getNew())))
                               .toList());

    Set<SignalRecord> remoteDeletes = new HashSet<>();
    remoteDeletes.addAll(contactMergeResult.remoteDeletes);
    remoteDeletes.addAll(groupV1MergeResult.remoteDeletes);
    remoteDeletes.addAll(groupV2MergeResult.remoteDeletes);
    remoteDeletes.addAll(accountMergeResult.remoteDeletes);

    return new MergeResult(contactMergeResult.localInserts,
                           contactMergeResult.localUpdates,
                           groupV1MergeResult.localInserts,
                           groupV1MergeResult.localUpdates,
                           groupV2MergeResult.localInserts,
                           groupV2MergeResult.localUpdates,
                           new LinkedHashSet<>(remoteOnlyUnknowns),
                           new LinkedHashSet<>(localOnlyUnknowns),
                           accountMergeResult.localUpdates.isEmpty() ? Optional.absent() : Optional.of(accountMergeResult.localUpdates.iterator().next()),
                           remoteInserts,
                           remoteUpdates,
                           remoteDeletes);
  }

  /**
   * Assumes that the merge result has *not* yet been applied to the local data. That means that
   * this method will handle generating the correct final key set based on the merge result.
   */
  public static @NonNull WriteOperationResult createWriteOperation(long currentManifestVersion,
                                                                   @NonNull List<StorageId> currentLocalStorageKeys,
                                                                   @NonNull MergeResult mergeResult)
  {
    List<SignalStorageRecord> inserts = new ArrayList<>();
    inserts.addAll(mergeResult.getRemoteInserts());
    inserts.addAll(Stream.of(mergeResult.getRemoteUpdates()).map(RecordUpdate::getNew).toList());

    List<StorageId> deletes = new ArrayList<>();
    deletes.addAll(Stream.of(mergeResult.getRemoteDeletes()).map(SignalRecord::getId).toList());
    deletes.addAll(Stream.of(mergeResult.getRemoteUpdates()).map(RecordUpdate::getOld).map(SignalStorageRecord::getId).toList());

    Set<StorageId> completeKeys = new HashSet<>(currentLocalStorageKeys);
    completeKeys.addAll(Stream.of(mergeResult.getAllNewRecords()).map(SignalRecord::getId).toList());
    completeKeys.removeAll(Stream.of(mergeResult.getAllRemovedRecords()).map(SignalRecord::getId).toList());
    completeKeys.addAll(Stream.of(inserts).map(SignalStorageRecord::getId).toList());
    completeKeys.removeAll(deletes);

    SignalStorageManifest manifest = new SignalStorageManifest(currentManifestVersion + 1, new ArrayList<>(completeKeys));

    return new WriteOperationResult(manifest, inserts, Stream.of(deletes).map(StorageId::getRaw).toList());
  }

  public static @NonNull byte[] generateKey() {
    return keyGenerator.generate();
  }

  @VisibleForTesting
  static void setTestKeyGenerator(@Nullable KeyGenerator testKeyGenerator) {
    keyGenerator = testKeyGenerator;
  }

  private static @NonNull <E extends SignalRecord> RecordMergeResult<E> resolveRecordConflict(@NonNull Collection<E> remoteOnlyRecords,
                                                                                              @NonNull Collection<E> localOnlyRecords,
                                                                                              @NonNull ConflictMerger<E> merger)
  {
    Set<E>               localInserts  = new HashSet<>(remoteOnlyRecords);
    Set<E>               remoteInserts = new HashSet<>(localOnlyRecords);
    Set<RecordUpdate<E>> localUpdates  = new HashSet<>();
    Set<RecordUpdate<E>> remoteUpdates = new HashSet<>();
    Set<E>               remoteDeletes = new HashSet<>(merger.getInvalidEntries(remoteOnlyRecords));

    remoteOnlyRecords.removeAll(remoteDeletes);
    localInserts.removeAll(remoteDeletes);

    for (E remote : remoteOnlyRecords) {
      Optional<E> local = merger.getMatching(remote);

      if (local.isPresent()) {
        E merged = merger.merge(remote, local.get(), keyGenerator);

        if (!merged.equals(remote)) {
          remoteUpdates.add(new RecordUpdate<>(remote, merged));
        }

        if (!merged.equals(local.get())) {
          localUpdates.add(new RecordUpdate<>(local.get(), merged));
        }

        localInserts.remove(remote);
        remoteInserts.remove(local.get());
      }
    }

    return new RecordMergeResult<>(localInserts, localUpdates, remoteInserts, remoteUpdates, remoteDeletes);
  }

  public static boolean profileKeyChanged(RecordUpdate<SignalContactRecord> update) {
    return !OptionalUtil.byteArrayEquals(update.getOld().getProfileKey(), update.getNew().getProfileKey());
  }

  public static Optional<SignalAccountRecord> getPendingAccountSyncUpdate(@NonNull Context context, @NonNull Recipient self) {
    if (DatabaseFactory.getRecipientDatabase(context).getDirtyState(self.getId()) != RecipientDatabase.DirtyState.UPDATE) {
      return Optional.absent();
    }
    return Optional.of(buildAccountRecord(context, self).getAccount().get());
  }

  public static Optional<SignalAccountRecord> getPendingAccountSyncInsert(@NonNull Context context, @NonNull Recipient self) {
    if (DatabaseFactory.getRecipientDatabase(context).getDirtyState(self.getId()) != RecipientDatabase.DirtyState.INSERT) {
      return Optional.absent();
    }
    return Optional.of(buildAccountRecord(context, self).getAccount().get());
  }

  public static SignalStorageRecord buildAccountRecord(@NonNull Context context, @NonNull Recipient self) {
    RecipientDatabase       recipientDatabase = DatabaseFactory.getRecipientDatabase(context);
    RecipientSettings       settings          = recipientDatabase.getRecipientSettingsForSync(self.getId());
    List<RecipientSettings> pinned            = Stream.of(DatabaseFactory.getThreadDatabase(context).getPinnedRecipientIds())
                                                      .map(recipientDatabase::getRecipientSettingsForSync)
                                                      .toList();

    SignalAccountRecord account = new SignalAccountRecord.Builder(self.getStorageServiceId())
                                                         .setUnknownFields(settings != null ? settings.getSyncExtras().getStorageProto() : null)
                                                         .setProfileKey(self.getProfileKey())
                                                         .setGivenName(self.getProfileName().getGivenName())
                                                         .setFamilyName(self.getProfileName().getFamilyName())
                                                         .setAvatarUrlPath(self.getProfileAvatar())
                                                         .setNoteToSelfArchived(settings != null && settings.getSyncExtras().isArchived())
                                                         .setNoteToSelfForcedUnread(settings != null && settings.getSyncExtras().isForcedUnread())
                                                         .setTypingIndicatorsEnabled(TextSecurePreferences.isTypingIndicatorsEnabled(context))
                                                         .setReadReceiptsEnabled(TextSecurePreferences.isReadReceiptsEnabled(context))
                                                         .setSealedSenderIndicatorsEnabled(TextSecurePreferences.isShowUnidentifiedDeliveryIndicatorsEnabled(context))
                                                         .setLinkPreviewsEnabled(SignalStore.settings().isLinkPreviewsEnabled())
                                                         .setUnlistedPhoneNumber(SignalStore.phoneNumberPrivacy().getPhoneNumberListingMode().isUnlisted())
                                                         .setPhoneNumberSharingMode(StorageSyncModels.localToRemotePhoneNumberSharingMode(SignalStore.phoneNumberPrivacy().getPhoneNumberSharingMode()))
                                                         .setPinnedConversations(StorageSyncModels.localToRemotePinnedConversations(pinned))
                                                         .setPreferContactAvatars(SignalStore.settings().isPreferSystemContactPhotos())
                                                         .build();

    return SignalStorageRecord.forAccount(account);
  }

  public static void applyAccountStorageSyncUpdates(@NonNull Context context, Optional<StorageSyncHelper.RecordUpdate<SignalAccountRecord>> update) {
    if (!update.isPresent()) {
      return;
    }
    applyAccountStorageSyncUpdates(context, StorageId.forAccount(Recipient.self().getStorageServiceId()), update.get().getNew(), true);
  }

  public static void applyAccountStorageSyncUpdates(@NonNull Context context, @NonNull StorageId storageId, @NonNull SignalAccountRecord update, boolean fetchProfile) {
    DatabaseFactory.getRecipientDatabase(context).applyStorageSyncUpdates(storageId, update);

    TextSecurePreferences.setReadReceiptsEnabled(context, update.isReadReceiptsEnabled());
    TextSecurePreferences.setTypingIndicatorsEnabled(context, update.isTypingIndicatorsEnabled());
    TextSecurePreferences.setShowUnidentifiedDeliveryIndicatorsEnabled(context, update.isSealedSenderIndicatorsEnabled());
    SignalStore.settings().setLinkPreviewsEnabled(update.isLinkPreviewsEnabled());
    SignalStore.phoneNumberPrivacy().setPhoneNumberListingMode(update.isPhoneNumberUnlisted() ? PhoneNumberPrivacyValues.PhoneNumberListingMode.UNLISTED : PhoneNumberPrivacyValues.PhoneNumberListingMode.LISTED);
    SignalStore.phoneNumberPrivacy().setPhoneNumberSharingMode(StorageSyncModels.remoteToLocalPhoneNumberSharingMode(update.getPhoneNumberSharingMode()));
    SignalStore.settings().setPreferSystemContactPhotos(update.isPreferContactAvatars());

    if (fetchProfile && update.getAvatarUrlPath().isPresent()) {
      ApplicationDependencies.getJobManager().add(new RetrieveProfileAvatarJob(Recipient.self(), update.getAvatarUrlPath().get()));
    }
  }

  public static void scheduleSyncForDataChange() {
    if (!SignalStore.registrationValues().isRegistrationComplete()) {
      Log.d(TAG, "Registration still ongoing. Ignore sync request.");
      return;
    }
    ApplicationDependencies.getJobManager().add(new StorageSyncJob());
  }

  public static void scheduleRoutineSync() {
    long timeSinceLastSync = System.currentTimeMillis() - SignalStore.storageServiceValues().getLastSyncTime();

    if (timeSinceLastSync > REFRESH_INTERVAL) {
      Log.d(TAG, "Scheduling a sync. Last sync was " + timeSinceLastSync + " ms ago.");
      scheduleSyncForDataChange();
    } else {
      Log.d(TAG, "No need for sync. Last sync was " + timeSinceLastSync + " ms ago.");
    }
  }

  public static final class KeyDifferenceResult {
    private final List<StorageId> remoteOnlyKeys;
    private final List<StorageId> localOnlyKeys;
    private final boolean         hasTypeMismatches;

    private KeyDifferenceResult(@NonNull List<StorageId> remoteOnlyKeys,
                                @NonNull List<StorageId> localOnlyKeys,
                                boolean hasTypeMismatches)
    {
      this.remoteOnlyKeys    = remoteOnlyKeys;
      this.localOnlyKeys     = localOnlyKeys;
      this.hasTypeMismatches = hasTypeMismatches;
    }

    public @NonNull List<StorageId> getRemoteOnlyKeys() {
      return remoteOnlyKeys;
    }

    public @NonNull List<StorageId> getLocalOnlyKeys() {
      return localOnlyKeys;
    }

    /**
     * @return True if there exist some keys that have matching raw ID's but different types,
     *         otherwise false.
     */
    public boolean hasTypeMismatches() {
      return hasTypeMismatches;
    }

    public boolean isEmpty() {
      return remoteOnlyKeys.isEmpty() && localOnlyKeys.isEmpty();
    }
  }

  public static final class MergeResult {
    private final Set<SignalContactRecord>                    localContactInserts;
    private final Set<RecordUpdate<SignalContactRecord>>      localContactUpdates;
    private final Set<SignalGroupV1Record>                    localGroupV1Inserts;
    private final Set<RecordUpdate<SignalGroupV1Record>>      localGroupV1Updates;
    private final Set<SignalGroupV2Record>                    localGroupV2Inserts;
    private final Set<RecordUpdate<SignalGroupV2Record>>      localGroupV2Updates;
    private final Set<SignalStorageRecord>                    localUnknownInserts;
    private final Set<SignalStorageRecord>                    localUnknownDeletes;
    private final Optional<RecordUpdate<SignalAccountRecord>> localAccountUpdate;
    private final Set<SignalStorageRecord>                    remoteInserts;
    private final Set<RecordUpdate<SignalStorageRecord>>      remoteUpdates;
    private final Set<SignalRecord>                           remoteDeletes;

    @VisibleForTesting
    MergeResult(@NonNull Set<SignalContactRecord>                    localContactInserts,
                @NonNull Set<RecordUpdate<SignalContactRecord>>      localContactUpdates,
                @NonNull Set<SignalGroupV1Record>                    localGroupV1Inserts,
                @NonNull Set<RecordUpdate<SignalGroupV1Record>>      localGroupV1Updates,
                @NonNull Set<SignalGroupV2Record>                    localGroupV2Inserts,
                @NonNull Set<RecordUpdate<SignalGroupV2Record>>      localGroupV2Updates,
                @NonNull Set<SignalStorageRecord>                    localUnknownInserts,
                @NonNull Set<SignalStorageRecord>                    localUnknownDeletes,
                @NonNull Optional<RecordUpdate<SignalAccountRecord>> localAccountUpdate,
                @NonNull Set<SignalStorageRecord>                    remoteInserts,
                @NonNull Set<RecordUpdate<SignalStorageRecord>>      remoteUpdates,
                @NonNull Set<SignalRecord>                           remoteDeletes)
    {
      this.localContactInserts  = localContactInserts;
      this.localContactUpdates  = localContactUpdates;
      this.localGroupV1Inserts  = localGroupV1Inserts;
      this.localGroupV1Updates  = localGroupV1Updates;
      this.localGroupV2Inserts  = localGroupV2Inserts;
      this.localGroupV2Updates  = localGroupV2Updates;
      this.localUnknownInserts  = localUnknownInserts;
      this.localUnknownDeletes  = localUnknownDeletes;
      this.localAccountUpdate   = localAccountUpdate;
      this.remoteInserts        = remoteInserts;
      this.remoteUpdates        = remoteUpdates;
      this.remoteDeletes        = remoteDeletes;
    }

    public @NonNull Set<SignalContactRecord> getLocalContactInserts() {
      return localContactInserts;
    }

    public @NonNull Set<RecordUpdate<SignalContactRecord>> getLocalContactUpdates() {
      return localContactUpdates;
    }

    public @NonNull Set<SignalGroupV1Record> getLocalGroupV1Inserts() {
      return localGroupV1Inserts;
    }

    public @NonNull Set<RecordUpdate<SignalGroupV1Record>> getLocalGroupV1Updates() {
      return localGroupV1Updates;
    }
    
    public @NonNull Set<SignalGroupV2Record> getLocalGroupV2Inserts() {
      return localGroupV2Inserts;
    }

    public @NonNull Set<RecordUpdate<SignalGroupV2Record>> getLocalGroupV2Updates() {
      return localGroupV2Updates;
    }

    public @NonNull Set<SignalStorageRecord> getLocalUnknownInserts() {
      return localUnknownInserts;
    }

    public @NonNull Set<SignalStorageRecord> getLocalUnknownDeletes() {
      return localUnknownDeletes;
    }

    public @NonNull Optional<RecordUpdate<SignalAccountRecord>> getLocalAccountUpdate() {
      return localAccountUpdate;
    }

    public @NonNull Set<SignalStorageRecord> getRemoteInserts() {
      return remoteInserts;
    }

    public @NonNull Set<RecordUpdate<SignalStorageRecord>> getRemoteUpdates() {
      return remoteUpdates;
    }

    public @NonNull Set<SignalRecord> getRemoteDeletes() {
      return remoteDeletes;
    }

    @NonNull Set<SignalRecord> getAllNewRecords() {
      Set<SignalRecord> records = new HashSet<>();

      records.addAll(localContactInserts);
      records.addAll(localGroupV1Inserts);
      records.addAll(localGroupV2Inserts);
      records.addAll(remoteInserts);
      records.addAll(localUnknownInserts);
      records.addAll(Stream.of(localContactUpdates).map(RecordUpdate::getNew).toList());
      records.addAll(Stream.of(localGroupV1Updates).map(RecordUpdate::getNew).toList());
      records.addAll(Stream.of(localGroupV2Updates).map(RecordUpdate::getNew).toList());
      records.addAll(Stream.of(remoteUpdates).map(RecordUpdate::getNew).toList());
      if (localAccountUpdate.isPresent()) records.add(localAccountUpdate.get().getNew());

      return records;
    }

    @NonNull Set<SignalRecord> getAllRemovedRecords() {
      Set<SignalRecord> records = new HashSet<>();

      records.addAll(localUnknownDeletes);
      records.addAll(Stream.of(localContactUpdates).map(RecordUpdate::getOld).toList());
      records.addAll(Stream.of(localGroupV1Updates).map(RecordUpdate::getOld).toList());
      records.addAll(Stream.of(localGroupV2Updates).map(RecordUpdate::getOld).toList());
      records.addAll(Stream.of(remoteUpdates).map(RecordUpdate::getOld).toList());
      records.addAll(remoteDeletes);
      if (localAccountUpdate.isPresent()) records.add(localAccountUpdate.get().getOld());

      return records;
    }

    @Override
    public @NonNull String toString() {
      return String.format(Locale.ENGLISH,
                           "localContactInserts: %d, localContactUpdates: %d, localGroupV1Inserts: %d, localGroupV1Updates: %d, localGroupV2Inserts: %d, localGroupV2Updates: %d, localUnknownInserts: %d, localUnknownDeletes: %d, localAccountUpdate: %b, remoteInserts: %d, remoteUpdates: %d, remoteDeletes: %d",
                           localContactInserts.size(), localContactUpdates.size(), localGroupV1Inserts.size(), localGroupV1Updates.size(), localGroupV2Inserts.size(), localGroupV2Updates.size(), localUnknownInserts.size(), localUnknownDeletes.size(), localAccountUpdate.isPresent(), remoteInserts.size(), remoteUpdates.size(), remoteDeletes.size());
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
                           manifest.getStorageIds().size(),
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

  public static class RecordUpdate<E extends SignalRecord> {
    private final E oldRecord;
    private final E newRecord;

    RecordUpdate(@NonNull E oldRecord, @NonNull E newRecord) {
      this.oldRecord = oldRecord;
      this.newRecord = newRecord;
    }

    public @NonNull E getOld() {
      return oldRecord;
    }

    public @NonNull E getNew() {
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

  private static class RecordMergeResult<Record extends SignalRecord> {
    final Set<Record>               localInserts;
    final Set<RecordUpdate<Record>> localUpdates;
    final Set<Record>               remoteInserts;
    final Set<RecordUpdate<Record>> remoteUpdates;
    final Set<Record>               remoteDeletes;

    RecordMergeResult(@NonNull Set<Record>               localInserts,
                      @NonNull Set<RecordUpdate<Record>> localUpdates,
                      @NonNull Set<Record>               remoteInserts,
                      @NonNull Set<RecordUpdate<Record>> remoteUpdates,
                      @NonNull Set<Record>               remoteDeletes)
    {
      this.localInserts  = localInserts;
      this.localUpdates  = localUpdates;
      this.remoteInserts = remoteInserts;
      this.remoteUpdates = remoteUpdates;
      this.remoteDeletes = remoteDeletes;
    }
  }

  interface ConflictMerger<E extends SignalRecord> {
    @NonNull Optional<E> getMatching(@NonNull E record);
    @NonNull Collection<E> getInvalidEntries(@NonNull Collection<E> remoteRecords);
    @NonNull E merge(@NonNull E remote, @NonNull E local, @NonNull KeyGenerator keyGenerator);
  }

  interface KeyGenerator {
    @NonNull byte[] generate();
  }

  private static final class MultipleExistingAccountsException extends IllegalArgumentException {}
  private static final class InvalidAccountInsertException extends IllegalArgumentException {}
  private static final class InvalidAccountUpdateException extends IllegalArgumentException {}
  private static final class InvalidAccountDualInsertUpdateException extends IllegalArgumentException {}
}
