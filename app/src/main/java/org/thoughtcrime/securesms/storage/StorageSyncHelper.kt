package org.thoughtcrime.securesms.storage

import android.content.Context
import androidx.annotation.VisibleForTesting
import okio.ByteString.Companion.toByteString
import org.signal.core.util.Base64.encodeWithPadding
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository.getSubscriber
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository.isUserManuallyCancelled
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository.setSubscriber
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.database.model.RecipientRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.RetrieveProfileAvatarJob
import org.thoughtcrime.securesms.jobs.StorageSyncJob
import org.thoughtcrime.securesms.keyvalue.AccountValues
import org.thoughtcrime.securesms.keyvalue.PhoneNumberPrivacyValues.PhoneNumberDiscoverabilityMode
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.payments.Entropy
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.Recipient.Companion.self
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.Util
import org.whispersystems.signalservice.api.push.UsernameLinkComponents
import org.whispersystems.signalservice.api.storage.SignalAccountRecord
import org.whispersystems.signalservice.api.storage.SignalContactRecord
import org.whispersystems.signalservice.api.storage.SignalStorageManifest
import org.whispersystems.signalservice.api.storage.SignalStorageRecord
import org.whispersystems.signalservice.api.storage.StorageId
import org.whispersystems.signalservice.api.util.OptionalUtil.byteArrayEquals
import org.whispersystems.signalservice.api.util.UuidUtil
import org.whispersystems.signalservice.api.util.toByteArray
import org.whispersystems.signalservice.internal.storage.protos.AccountRecord
import org.whispersystems.signalservice.internal.storage.protos.OptionalBool
import java.util.Optional
import java.util.concurrent.TimeUnit

object StorageSyncHelper {
  private val TAG = Log.tag(StorageSyncHelper::class.java)

  val KEY_GENERATOR: StorageKeyGenerator = StorageKeyGenerator { Util.getSecretBytes(16) }

  private var keyGenerator = KEY_GENERATOR

  private val REFRESH_INTERVAL = TimeUnit.HOURS.toMillis(2)

  /**
   * Given a list of all the local and remote keys you know about, this will return a result telling
   * you which keys are exclusively remote and which are exclusively local.
   *
   * @param remoteIds All remote keys available.
   * @param localIds  All local keys available.
   * @return An object describing which keys are exclusive to the remote data set and which keys are
   * exclusive to the local data set.
   */
  @JvmStatic
  fun findIdDifference(
    remoteIds: Collection<StorageId>,
    localIds: Collection<StorageId>
  ): IdDifferenceResult {
    val remoteByRawId: Map<String, StorageId> = remoteIds.associateBy { encodeWithPadding(it.raw) }
    val localByRawId: Map<String, StorageId> = localIds.associateBy { encodeWithPadding(it.raw) }

    var hasTypeMismatch = remoteByRawId.size != remoteIds.size || localByRawId.size != localIds.size

    val remoteOnlyRawIds: MutableSet<String> = (remoteByRawId.keys - localByRawId.keys).toMutableSet()
    val localOnlyRawIds: MutableSet<String> = (localByRawId.keys - remoteByRawId.keys).toMutableSet()
    val sharedRawIds: Set<String> = localByRawId.keys.intersect(remoteByRawId.keys)

    for (rawId in sharedRawIds) {
      val remote = remoteByRawId[rawId]!!
      val local = localByRawId[rawId]!!

      if (remote.type != local.type) {
        remoteOnlyRawIds.remove(rawId)
        localOnlyRawIds.remove(rawId)
        hasTypeMismatch = true
        Log.w(TAG, "Remote type ${remote.type} did not match local type ${local.type}!")
      }
    }

    val remoteOnlyKeys = remoteOnlyRawIds.mapNotNull { remoteByRawId[it] }
    val localOnlyKeys = localOnlyRawIds.mapNotNull { localByRawId[it] }

    return IdDifferenceResult(remoteOnlyKeys, localOnlyKeys, hasTypeMismatch)
  }

  @JvmStatic
  fun generateKey(): ByteArray {
    return keyGenerator.generate()
  }

  @JvmStatic
  @VisibleForTesting
  fun setTestKeyGenerator(testKeyGenerator: StorageKeyGenerator?) {
    keyGenerator = testKeyGenerator ?: KEY_GENERATOR
  }

  @JvmStatic
  fun profileKeyChanged(update: StorageRecordUpdate<SignalContactRecord>): Boolean {
    return !byteArrayEquals(update.old.profileKey, update.new.profileKey)
  }

  @JvmStatic
  fun buildAccountRecord(context: Context, self: Recipient): SignalStorageRecord {
    var self = self
    var selfRecord: RecipientRecord? = SignalDatabase.recipients.getRecordForSync(self.id)
    val pinned: List<RecipientRecord> = SignalDatabase.threads.getPinnedRecipientIds()
      .mapNotNull { SignalDatabase.recipients.getRecordForSync(it) }

    val storyViewReceiptsState = if (SignalStore.story.viewedReceiptsEnabled) {
      OptionalBool.ENABLED
    } else {
      OptionalBool.DISABLED
    }

    if (self.storageId == null || (selfRecord != null && selfRecord.storageId == null)) {
      Log.w(TAG, "[buildAccountRecord] No storageId for self or record! Generating. (Self: ${self.storageId != null}, Record: ${selfRecord?.storageId != null})")
      SignalDatabase.recipients.updateStorageId(self.id, generateKey())
      self = self().fresh()
      selfRecord = SignalDatabase.recipients.getRecordForSync(self.id)
    }

    if (selfRecord == null) {
      Log.w(TAG, "[buildAccountRecord] Could not find a RecipientRecord for ourselves! ID: ${self.id}")
    } else if (!selfRecord.storageId.contentEquals(self.storageId)) {
      Log.w(TAG, "[buildAccountRecord] StorageId on RecipientRecord did not match self! ID: ${self.id}")
    }

    val storageId = selfRecord?.storageId ?: self.storageId

    val account = SignalAccountRecord.Builder(storageId, selfRecord?.syncExtras?.storageProto)
      .setProfileKey(self.profileKey)
      .setGivenName(self.profileName.givenName)
      .setFamilyName(self.profileName.familyName)
      .setAvatarUrlPath(self.profileAvatar)
      .setNoteToSelfArchived(selfRecord != null && selfRecord.syncExtras.isArchived)
      .setNoteToSelfForcedUnread(selfRecord != null && selfRecord.syncExtras.isForcedUnread)
      .setTypingIndicatorsEnabled(TextSecurePreferences.isTypingIndicatorsEnabled(context))
      .setReadReceiptsEnabled(TextSecurePreferences.isReadReceiptsEnabled(context))
      .setSealedSenderIndicatorsEnabled(TextSecurePreferences.isShowUnidentifiedDeliveryIndicatorsEnabled(context))
      .setLinkPreviewsEnabled(SignalStore.settings.isLinkPreviewsEnabled)
      .setUnlistedPhoneNumber(SignalStore.phoneNumberPrivacy.phoneNumberDiscoverabilityMode == PhoneNumberDiscoverabilityMode.NOT_DISCOVERABLE)
      .setPhoneNumberSharingMode(StorageSyncModels.localToRemotePhoneNumberSharingMode(SignalStore.phoneNumberPrivacy.phoneNumberSharingMode))
      .setPinnedConversations(StorageSyncModels.localToRemotePinnedConversations(pinned))
      .setPreferContactAvatars(SignalStore.settings.isPreferSystemContactPhotos)
      .setPayments(SignalStore.payments.mobileCoinPaymentsEnabled(), Optional.ofNullable(SignalStore.payments.paymentsEntropy).map { obj: Entropy -> obj.bytes }.orElse(null))
      .setPrimarySendsSms(false)
      .setUniversalExpireTimer(SignalStore.settings.universalExpireTimer)
      .setDefaultReactions(SignalStore.emoji.reactions)
      .setSubscriber(StorageSyncModels.localToRemoteSubscriber(getSubscriber(InAppPaymentSubscriberRecord.Type.DONATION)))
      .setBackupsSubscriber(StorageSyncModels.localToRemoteSubscriber(getSubscriber(InAppPaymentSubscriberRecord.Type.BACKUP)))
      .setDisplayBadgesOnProfile(SignalStore.inAppPayments.getDisplayBadgesOnProfile())
      .setSubscriptionManuallyCancelled(isUserManuallyCancelled(InAppPaymentSubscriberRecord.Type.DONATION))
      .setKeepMutedChatsArchived(SignalStore.settings.shouldKeepMutedChatsArchived())
      .setHasSetMyStoriesPrivacy(SignalStore.story.userHasBeenNotifiedAboutStories)
      .setHasViewedOnboardingStory(SignalStore.story.userHasViewedOnboardingStory)
      .setStoriesDisabled(SignalStore.story.isFeatureDisabled)
      .setStoryViewReceiptsState(storyViewReceiptsState)
      .setHasSeenGroupStoryEducationSheet(SignalStore.story.userHasSeenGroupStoryEducationSheet)
      .setUsername(SignalStore.account.username)
      .setHasCompletedUsernameOnboarding(SignalStore.uiHints.hasCompletedUsernameOnboarding())

    val linkComponents = SignalStore.account.usernameLink
    if (linkComponents != null) {
      account.setUsernameLink(
        AccountRecord.UsernameLink.Builder()
          .entropy(linkComponents.entropy.toByteString())
          .serverId(linkComponents.serverId.toByteArray().toByteString())
          .color(StorageSyncModels.localToRemoteUsernameColor(SignalStore.misc.usernameQrCodeColorScheme))
          .build()
      )
    } else {
      account.setUsernameLink(null)
    }

    return SignalStorageRecord.forAccount(account.build())
  }

  @JvmStatic
  fun applyAccountStorageSyncUpdates(context: Context, self: Recipient, updatedRecord: SignalAccountRecord, fetchProfile: Boolean) {
    val localRecord = buildAccountRecord(context, self).account.get()
    applyAccountStorageSyncUpdates(context, self, StorageRecordUpdate(localRecord, updatedRecord), fetchProfile)
  }

  @JvmStatic
  fun applyAccountStorageSyncUpdates(context: Context, self: Recipient, update: StorageRecordUpdate<SignalAccountRecord>, fetchProfile: Boolean) {
    SignalDatabase.recipients.applyStorageSyncAccountUpdate(update)

    TextSecurePreferences.setReadReceiptsEnabled(context, update.new.isReadReceiptsEnabled)
    TextSecurePreferences.setTypingIndicatorsEnabled(context, update.new.isTypingIndicatorsEnabled)
    TextSecurePreferences.setShowUnidentifiedDeliveryIndicatorsEnabled(context, update.new.isSealedSenderIndicatorsEnabled)
    SignalStore.settings.isLinkPreviewsEnabled = update.new.isLinkPreviewsEnabled
    SignalStore.phoneNumberPrivacy.phoneNumberDiscoverabilityMode = if (update.new.isPhoneNumberUnlisted) PhoneNumberDiscoverabilityMode.NOT_DISCOVERABLE else PhoneNumberDiscoverabilityMode.DISCOVERABLE
    SignalStore.phoneNumberPrivacy.phoneNumberSharingMode = StorageSyncModels.remoteToLocalPhoneNumberSharingMode(update.new.phoneNumberSharingMode)
    SignalStore.settings.isPreferSystemContactPhotos = update.new.isPreferContactAvatars
    SignalStore.payments.setEnabledAndEntropy(update.new.payments.isEnabled, Entropy.fromBytes(update.new.payments.entropy.orElse(null)))
    SignalStore.settings.universalExpireTimer = update.new.universalExpireTimer
    SignalStore.emoji.reactions = update.new.defaultReactions
    SignalStore.inAppPayments.setDisplayBadgesOnProfile(update.new.isDisplayBadgesOnProfile)
    SignalStore.settings.setKeepMutedChatsArchived(update.new.isKeepMutedChatsArchived)
    SignalStore.story.userHasBeenNotifiedAboutStories = update.new.hasSetMyStoriesPrivacy()
    SignalStore.story.userHasViewedOnboardingStory = update.new.hasViewedOnboardingStory()
    SignalStore.story.isFeatureDisabled = update.new.isStoriesDisabled
    SignalStore.story.userHasSeenGroupStoryEducationSheet = update.new.hasSeenGroupStoryEducationSheet()
    SignalStore.uiHints.setHasCompletedUsernameOnboarding(update.new.hasCompletedUsernameOnboarding())

    if (update.new.storyViewReceiptsState == OptionalBool.UNSET) {
      SignalStore.story.viewedReceiptsEnabled = update.new.isReadReceiptsEnabled
    } else {
      SignalStore.story.viewedReceiptsEnabled = update.new.storyViewReceiptsState == OptionalBool.ENABLED
    }

    if (update.new.storyViewReceiptsState == OptionalBool.UNSET) {
      SignalStore.story.viewedReceiptsEnabled = update.new.isReadReceiptsEnabled
    } else {
      SignalStore.story.viewedReceiptsEnabled = update.new.storyViewReceiptsState == OptionalBool.ENABLED
    }

    val remoteSubscriber = StorageSyncModels.remoteToLocalSubscriber(update.new.subscriber, InAppPaymentSubscriberRecord.Type.DONATION)
    if (remoteSubscriber != null) {
      setSubscriber(remoteSubscriber)
    }

    if (update.new.isSubscriptionManuallyCancelled && !update.old.isSubscriptionManuallyCancelled) {
      SignalStore.inAppPayments.updateLocalStateForManualCancellation(InAppPaymentSubscriberRecord.Type.DONATION)
    }

    if (fetchProfile && update.new.avatarUrlPath.isPresent) {
      AppDependencies.jobManager.add(RetrieveProfileAvatarJob(self, update.new.avatarUrlPath.get()))
    }

    if (update.new.username != update.old.username) {
      SignalStore.account.username = update.new.username
      SignalStore.account.usernameSyncState = AccountValues.UsernameSyncState.IN_SYNC
      SignalStore.account.usernameSyncErrorCount = 0
    }

    if (update.new.usernameLink != null) {
      SignalStore.account.usernameLink = UsernameLinkComponents(
        update.new.usernameLink!!.entropy.toByteArray(),
        UuidUtil.parseOrThrow(update.new.usernameLink!!.serverId.toByteArray())
      )

      SignalStore.misc.usernameQrCodeColorScheme = StorageSyncModels.remoteToLocalUsernameColor(update.new.usernameLink!!.color)
    }
  }

  @JvmStatic
  fun scheduleSyncForDataChange() {
    if (!SignalStore.registration.isRegistrationComplete) {
      Log.d(TAG, "Registration still ongoing. Ignore sync request.")
      return
    }
    AppDependencies.jobManager.add(StorageSyncJob())
  }

  @JvmStatic
  fun scheduleRoutineSync() {
    val timeSinceLastSync = System.currentTimeMillis() - SignalStore.storageService.lastSyncTime

    if (timeSinceLastSync > REFRESH_INTERVAL) {
      Log.d(TAG, "Scheduling a sync. Last sync was $timeSinceLastSync ms ago.")
      scheduleSyncForDataChange()
    } else {
      Log.d(TAG, "No need for sync. Last sync was $timeSinceLastSync ms ago.")
    }
  }

  class IdDifferenceResult(
    @JvmField val remoteOnlyIds: List<StorageId>,
    @JvmField val localOnlyIds: List<StorageId>,
    val hasTypeMismatches: Boolean
  ) {
    val isEmpty: Boolean
      get() = remoteOnlyIds.isEmpty() && localOnlyIds.isEmpty()

    override fun toString(): String {
      return "remoteOnly: ${remoteOnlyIds.size}, localOnly: ${localOnlyIds.size}, hasTypeMismatches: $hasTypeMismatches"
    }
  }

  class WriteOperationResult(
    @JvmField val manifest: SignalStorageManifest,
    @JvmField val inserts: List<SignalStorageRecord>,
    @JvmField val deletes: List<ByteArray>
  ) {
    val isEmpty: Boolean
      get() = inserts.isEmpty() && deletes.isEmpty()

    override fun toString(): String {
      return if (isEmpty) {
        "Empty"
      } else {
        "ManifestVersion: ${manifest.version}, Total Keys: ${manifest.storageIds.size}, Inserts: ${inserts.size}, Deletes: ${deletes.size}"
      }
    }
  }
}
