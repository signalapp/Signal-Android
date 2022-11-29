package org.thoughtcrime.securesms.storage;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import org.signal.core.util.SetUtil;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.RecipientTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.model.RecipientRecord;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobs.RetrieveProfileAvatarJob;
import org.thoughtcrime.securesms.jobs.StorageSyncJob;
import org.thoughtcrime.securesms.keyvalue.PhoneNumberPrivacyValues;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.payments.Entropy;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.subscription.Subscriber;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.signalservice.api.storage.SignalAccountRecord;
import org.whispersystems.signalservice.api.storage.SignalContactRecord;
import org.whispersystems.signalservice.api.storage.SignalStorageManifest;
import org.whispersystems.signalservice.api.storage.SignalStorageRecord;
import org.whispersystems.signalservice.api.storage.StorageId;
import org.whispersystems.signalservice.api.util.OptionalUtil;
import org.whispersystems.signalservice.internal.storage.protos.OptionalBool;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public final class StorageSyncHelper {

  private static final String TAG = Log.tag(StorageSyncHelper.class);

  public static final StorageKeyGenerator KEY_GENERATOR = () -> Util.getSecretBytes(16);

  private static StorageKeyGenerator keyGenerator = KEY_GENERATOR;

  private static final long REFRESH_INTERVAL = TimeUnit.HOURS.toMillis(2);

  /**
   * Given a list of all the local and remote keys you know about, this will return a result telling
   * you which keys are exclusively remote and which are exclusively local.
   *
   * @param remoteIds All remote keys available.
   * @param localIds  All local keys available.
   * @return An object describing which keys are exclusive to the remote data set and which keys are
   * exclusive to the local data set.
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

  public static SignalStorageRecord buildAccountRecord(@NonNull Context context, @NonNull Recipient self) {
    RecipientTable  recipientTable = SignalDatabase.recipients();
    RecipientRecord record         = recipientTable.getRecordForSync(self.getId());
    List<RecipientRecord> pinned            = Stream.of(SignalDatabase.threads().getPinnedRecipientIds())
                                                    .map(recipientTable::getRecordForSync)
                                                    .toList();

    final OptionalBool storyViewReceiptsState = SignalStore.storyValues().getViewedReceiptsEnabled() ? OptionalBool.ENABLED
                                                                                                     : OptionalBool.DISABLED;

    if (self.getStorageServiceId() == null) {
      Log.w(TAG, "[buildAccountRecord] No storageId for self! Generating. (Record had ID: " + (record != null && record.getStorageId() != null) + ")");
      SignalDatabase.recipients().updateStorageId(self.getId(), generateKey());
      self = Recipient.self().fresh();
      record = recipientTable.getRecordForSync(self.getId());
    }

    final boolean hasReadOnboardingStory = SignalStore.storyValues().getUserHasViewedOnboardingStory() || SignalStore.storyValues().getUserHasReadOnboardingStory();

    SignalAccountRecord account = new SignalAccountRecord.Builder(self.getStorageServiceId(), record != null ? record.getSyncExtras().getStorageProto() : null)
                                                         .setProfileKey(self.getProfileKey())
                                                         .setGivenName(self.getProfileName().getGivenName())
                                                         .setFamilyName(self.getProfileName().getFamilyName())
                                                         .setAvatarUrlPath(self.getProfileAvatar())
                                                         .setNoteToSelfArchived(record != null && record.getSyncExtras().isArchived())
                                                         .setNoteToSelfForcedUnread(record != null && record.getSyncExtras().isForcedUnread())
                                                         .setTypingIndicatorsEnabled(TextSecurePreferences.isTypingIndicatorsEnabled(context))
                                                         .setReadReceiptsEnabled(TextSecurePreferences.isReadReceiptsEnabled(context))
                                                         .setSealedSenderIndicatorsEnabled(TextSecurePreferences.isShowUnidentifiedDeliveryIndicatorsEnabled(context))
                                                         .setLinkPreviewsEnabled(SignalStore.settings().isLinkPreviewsEnabled())
                                                         .setUnlistedPhoneNumber(SignalStore.phoneNumberPrivacy().getPhoneNumberListingMode().isUnlisted())
                                                         .setPhoneNumberSharingMode(StorageSyncModels.localToRemotePhoneNumberSharingMode(SignalStore.phoneNumberPrivacy().getPhoneNumberSharingMode()))
                                                         .setPinnedConversations(StorageSyncModels.localToRemotePinnedConversations(pinned))
                                                         .setPreferContactAvatars(SignalStore.settings().isPreferSystemContactPhotos())
                                                         .setPayments(SignalStore.paymentsValues().mobileCoinPaymentsEnabled(), Optional.ofNullable(SignalStore.paymentsValues().getPaymentsEntropy()).map(Entropy::getBytes).orElse(null))
                                                         .setPrimarySendsSms(Util.isDefaultSmsProvider(context))
                                                         .setUniversalExpireTimer(SignalStore.settings().getUniversalExpireTimer())
                                                         .setE164(self.requireE164())
                                                         .setDefaultReactions(SignalStore.emojiValues().getReactions())
                                                         .setSubscriber(StorageSyncModels.localToRemoteSubscriber(SignalStore.donationsValues().getSubscriber()))
                                                         .setDisplayBadgesOnProfile(SignalStore.donationsValues().getDisplayBadgesOnProfile())
                                                         .setSubscriptionManuallyCancelled(SignalStore.donationsValues().isUserManuallyCancelled())
                                                         .setKeepMutedChatsArchived(SignalStore.settings().shouldKeepMutedChatsArchived())
                                                         .setHasSetMyStoriesPrivacy(SignalStore.storyValues().getUserHasBeenNotifiedAboutStories())
                                                         .setHasViewedOnboardingStory(SignalStore.storyValues().getUserHasViewedOnboardingStory())
                                                         .setStoriesDisabled(SignalStore.storyValues().isFeatureDisabled())
                                                         .setStoryViewReceiptsState(storyViewReceiptsState)
                                                         .setHasReadOnboardingStory(hasReadOnboardingStory)
                                                         .setHasSeenGroupStoryEducationSheet(SignalStore.storyValues().getUserHasSeenGroupStoryEducationSheet())
                                                         .build();

    return SignalStorageRecord.forAccount(account);
  }

  public static void applyAccountStorageSyncUpdates(@NonNull Context context, @NonNull Recipient self, @NonNull SignalAccountRecord updatedRecord, boolean fetchProfile) {
    SignalAccountRecord localRecord = buildAccountRecord(context, self).getAccount().get();
    applyAccountStorageSyncUpdates(context, self, new StorageRecordUpdate<>(localRecord, updatedRecord), fetchProfile);
  }

  public static void applyAccountStorageSyncUpdates(@NonNull Context context, @NonNull Recipient self, @NonNull StorageRecordUpdate<SignalAccountRecord> update, boolean fetchProfile) {
    SignalDatabase.recipients().applyStorageSyncAccountUpdate(update);

    TextSecurePreferences.setReadReceiptsEnabled(context, update.getNew().isReadReceiptsEnabled());
    TextSecurePreferences.setTypingIndicatorsEnabled(context, update.getNew().isTypingIndicatorsEnabled());
    TextSecurePreferences.setShowUnidentifiedDeliveryIndicatorsEnabled(context, update.getNew().isSealedSenderIndicatorsEnabled());
    SignalStore.settings().setLinkPreviewsEnabled(update.getNew().isLinkPreviewsEnabled());
    SignalStore.phoneNumberPrivacy().setPhoneNumberListingMode(update.getNew().isPhoneNumberUnlisted() ? PhoneNumberPrivacyValues.PhoneNumberListingMode.UNLISTED : PhoneNumberPrivacyValues.PhoneNumberListingMode.LISTED);
    SignalStore.phoneNumberPrivacy().setPhoneNumberSharingMode(StorageSyncModels.remoteToLocalPhoneNumberSharingMode(update.getNew().getPhoneNumberSharingMode()));
    SignalStore.settings().setPreferSystemContactPhotos(update.getNew().isPreferContactAvatars());
    SignalStore.paymentsValues().setEnabledAndEntropy(update.getNew().getPayments().isEnabled(), Entropy.fromBytes(update.getNew().getPayments().getEntropy().orElse(null)));
    SignalStore.settings().setUniversalExpireTimer(update.getNew().getUniversalExpireTimer());
    SignalStore.emojiValues().setReactions(update.getNew().getDefaultReactions());
    SignalStore.donationsValues().setDisplayBadgesOnProfile(update.getNew().isDisplayBadgesOnProfile());
    SignalStore.settings().setKeepMutedChatsArchived(update.getNew().isKeepMutedChatsArchived());
    SignalStore.storyValues().setUserHasBeenNotifiedAboutStories(update.getNew().hasSetMyStoriesPrivacy());
    SignalStore.storyValues().setUserHasViewedOnboardingStory(update.getNew().hasViewedOnboardingStory());
    SignalStore.storyValues().setFeatureDisabled(update.getNew().isStoriesDisabled());
    SignalStore.storyValues().setUserHasReadOnboardingStory(update.getNew().hasReadOnboardingStory());
    SignalStore.storyValues().setUserHasSeenGroupStoryEducationSheet(update.getNew().hasSeenGroupStoryEducationSheet());

    if (update.getNew().getStoryViewReceiptsState() == OptionalBool.UNSET) {
      SignalStore.storyValues().setViewedReceiptsEnabled(update.getNew().isReadReceiptsEnabled());
    } else {
      SignalStore.storyValues().setViewedReceiptsEnabled(update.getNew().getStoryViewReceiptsState() == OptionalBool.ENABLED);
    }

    if (update.getNew().getStoryViewReceiptsState() == OptionalBool.UNSET) {
      SignalStore.storyValues().setViewedReceiptsEnabled(update.getNew().isReadReceiptsEnabled());
    } else {
      SignalStore.storyValues().setViewedReceiptsEnabled(update.getNew().getStoryViewReceiptsState() == OptionalBool.ENABLED);
    }

    if (update.getNew().isSubscriptionManuallyCancelled()) {
      SignalStore.donationsValues().updateLocalStateForManualCancellation();
    } else {
      SignalStore.donationsValues().clearUserManuallyCancelled();
    }

    Subscriber subscriber = StorageSyncModels.remoteToLocalSubscriber(update.getNew().getSubscriber());
    if (subscriber != null) {
      SignalStore.donationsValues().setSubscriber(subscriber);
    }

    if (fetchProfile && update.getNew().getAvatarUrlPath().isPresent()) {
      ApplicationDependencies.getJobManager().add(new RetrieveProfileAvatarJob(self, update.getNew().getAvatarUrlPath().get()));
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
    long timeSinceLastSync = System.currentTimeMillis() - SignalStore.storageService().getLastSyncTime();

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
     * otherwise false.
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
}
