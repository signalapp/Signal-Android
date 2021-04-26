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
import org.thoughtcrime.securesms.payments.Entropy;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.SetUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.storage.SignalAccountRecord;
import org.whispersystems.signalservice.api.storage.SignalContactRecord;
import org.whispersystems.signalservice.api.storage.SignalStorageManifest;
import org.whispersystems.signalservice.api.storage.SignalStorageRecord;
import org.whispersystems.signalservice.api.storage.StorageId;
import org.whispersystems.signalservice.api.util.OptionalUtil;
import org.whispersystems.signalservice.internal.storage.protos.ManifestRecord;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public final class StorageSyncHelper {

  private static final String TAG = Log.tag(StorageSyncHelper.class);

  public static final StorageKeyGenerator KEY_GENERATOR = () -> Util.getSecretBytes(16);

  private static StorageKeyGenerator keyGenerator = KEY_GENERATOR;

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

      SignalStorageRecord insertRecord = StorageSyncModels.localToRemoteRecord(insert);
      storageInserts.add(insertRecord);
      completeIds.add(insertRecord.getId());
    }

    if (accountInsert.isPresent()) {
      storageInserts.add(SignalStorageRecord.forAccount(accountInsert.get()));
      completeIds.add(accountInsert.get().getId());
    }

    for (RecipientSettings delete : deletes) {
      byte[] key = Objects.requireNonNull(delete.getStorageId());
      storageDeletes.add(ByteBuffer.wrap(key));
      completeIds.removeIf(id -> Arrays.equals(id.getRaw(), key));
    }

    for (RecipientSettings update : updates) {
      byte[] oldId = update.getStorageId();
      byte[] newId = generateKey();

      SignalStorageRecord insert = StorageSyncModels.localToRemoteRecord(update, newId);

      storageInserts.add(insert);
      storageDeletes.add(ByteBuffer.wrap(oldId));

      completeIds.add(insert.getId());
      completeIds.removeIf(id -> Arrays.equals(id.getRaw(), oldId));

      storageKeyUpdates.put(update.getId(), newId);
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
      SignalStorageManifest manifest             = new SignalStorageManifest(currentManifestVersion + 1, new ArrayList<>(completeIds));
      WriteOperationResult  writeOperationResult = new WriteOperationResult(manifest, new ArrayList<>(storageInserts), storageDeleteBytes);

      return Optional.of(new LocalWriteResult(writeOperationResult, storageKeyUpdates));
    }
  }

  /**
   * Given a list of all the local and remote keys you know about, this will return a result telling
   * you which keys are exclusively remote and which are exclusively local.
   *
   * @param remoteIds All remote keys available.
   * @param localIds All local keys available.
   *
   * @return An object describing which keys are exclusive to the remote data set and which keys are
   *         exclusive to the local data set.
   */
  public static @NonNull IdDifferenceResult findIdDifference(@NonNull Collection<StorageId> remoteIds,
                                                             @NonNull Collection<StorageId> localIds)
  {
    Map<String, StorageId> remoteByRawId = Stream.of(remoteIds).collect(Collectors.toMap(id -> Base64.encodeBytes(id.getRaw()), id -> id));
    Map<String, StorageId> localByRawId  = Stream.of(localIds).collect(Collectors.toMap(id -> Base64.encodeBytes(id.getRaw()), id -> id));

    boolean hasTypeMismatch = remoteByRawId.size() != remoteIds.size() || localByRawId.size() != localIds.size();

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
        Log.w(TAG, "Remote type " + remote.getType() + " did not match local type " + local.getType() + "!");
      }
    }

    List<StorageId> remoteOnlyKeys = Stream.of(remoteOnlyRawIds).map(remoteByRawId::get).toList();
    List<StorageId> localOnlyKeys  = Stream.of(localOnlyRawIds).map(localByRawId::get).toList();

    return new IdDifferenceResult(remoteOnlyKeys, localOnlyKeys, hasTypeMismatch);
  }

  public static @NonNull byte[] generateKey() {
    return keyGenerator.generate();
  }

  @VisibleForTesting
  static void setTestKeyGenerator(@Nullable StorageKeyGenerator testKeyGenerator) {
    keyGenerator = testKeyGenerator != null ? testKeyGenerator : KEY_GENERATOR;
  }

  public static boolean profileKeyChanged(StorageRecordUpdate<SignalContactRecord> update) {
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
                                                         .setPayments(SignalStore.paymentsValues().mobileCoinPaymentsEnabled(), Optional.fromNullable(SignalStore.paymentsValues().getPaymentsEntropy()).transform(Entropy::getBytes).orNull())
                                                         .build();

    return SignalStorageRecord.forAccount(account);
  }

  public static void applyAccountStorageSyncUpdates(@NonNull Context context, @NonNull Recipient self, @NonNull SignalAccountRecord update, boolean fetchProfile) {
    DatabaseFactory.getRecipientDatabase(context).applyStorageSyncAccountUpdate(StorageId.forAccount(self.getStorageServiceId()), update);

    TextSecurePreferences.setReadReceiptsEnabled(context, update.isReadReceiptsEnabled());
    TextSecurePreferences.setTypingIndicatorsEnabled(context, update.isTypingIndicatorsEnabled());
    TextSecurePreferences.setShowUnidentifiedDeliveryIndicatorsEnabled(context, update.isSealedSenderIndicatorsEnabled());
    SignalStore.settings().setLinkPreviewsEnabled(update.isLinkPreviewsEnabled());
    SignalStore.phoneNumberPrivacy().setPhoneNumberListingMode(update.isPhoneNumberUnlisted() ? PhoneNumberPrivacyValues.PhoneNumberListingMode.UNLISTED : PhoneNumberPrivacyValues.PhoneNumberListingMode.LISTED);
    SignalStore.phoneNumberPrivacy().setPhoneNumberSharingMode(StorageSyncModels.remoteToLocalPhoneNumberSharingMode(update.getPhoneNumberSharingMode()));
    SignalStore.settings().setPreferSystemContactPhotos(update.isPreferContactAvatars());
    SignalStore.paymentsValues().setEnabledAndEntropy(update.getPayments().isEnabled(), Entropy.fromBytes(update.getPayments().getEntropy().orNull()));

    if (fetchProfile && update.getAvatarUrlPath().isPresent()) {
      ApplicationDependencies.getJobManager().add(new RetrieveProfileAvatarJob(self, update.getAvatarUrlPath().get()));
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

  public static final class IdDifferenceResult {
    private final List<StorageId> remoteOnlyIds;
    private final List<StorageId> localOnlyIds;
    private final boolean         hasTypeMismatches;

    private IdDifferenceResult(@NonNull List<StorageId> remoteOnlyIds,
                               @NonNull List<StorageId> localOnlyIds,
                               boolean hasTypeMismatches)
    {
      this.remoteOnlyIds     = remoteOnlyIds;
      this.localOnlyIds      = localOnlyIds;
      this.hasTypeMismatches = hasTypeMismatches;
    }

    public @NonNull List<StorageId> getRemoteOnlyIds() {
      return remoteOnlyIds;
    }

    public @NonNull List<StorageId> getLocalOnlyIds() {
      return localOnlyIds;
    }

    /**
     * @return True if there exist some keys that have matching raw ID's but different types,
     *         otherwise false.
     */
    public boolean hasTypeMismatches() {
      return hasTypeMismatches;
    }

    public boolean isEmpty() {
      return remoteOnlyIds.isEmpty() && localOnlyIds.isEmpty();
    }

    @Override
    public @NonNull String toString() {
      return "remoteOnly: " + remoteOnlyIds.size() + ", localOnly: " + localOnlyIds.size() + ", hasTypeMismatches: " + hasTypeMismatches;
    }
  }

  public static final class WriteOperationResult {
    private final SignalStorageManifest     manifest;
    private final List<SignalStorageRecord> inserts;
    private final List<byte[]>              deletes;

    public WriteOperationResult(@NonNull SignalStorageManifest manifest,
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
      if (isEmpty()) {
        return "Empty";
      } else {
        return String.format(Locale.ENGLISH,
                             "ManifestVersion: %d, Total Keys: %d, Inserts: %d, Deletes: %d",
                             manifest.getVersion(),
                             manifest.getStorageIds().size(),
                             inserts.size(),
                             deletes.size());
      }
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

  private static final class MultipleExistingAccountsException extends IllegalArgumentException {}
  private static final class InvalidAccountInsertException extends IllegalArgumentException {}
  private static final class InvalidAccountUpdateException extends IllegalArgumentException {}
  private static final class InvalidAccountDualInsertUpdateException extends IllegalArgumentException {}
}
