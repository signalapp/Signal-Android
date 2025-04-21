package org.thoughtcrime.securesms.storage

import android.content.Context
import androidx.annotation.VisibleForTesting
import okio.ByteString
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
import org.whispersystems.signalservice.api.storage.safeSetBackupsSubscriber
import org.whispersystems.signalservice.api.storage.safeSetPayments
import org.whispersystems.signalservice.api.storage.safeSetSubscriber
import org.whispersystems.signalservice.api.storage.toSignalAccountRecord
import org.whispersystems.signalservice.api.storage.toSignalStorageRecord
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
    return update.old.proto.profileKey != update.new.proto.profileKey
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

    val accountRecord = SignalAccountRecord.newBuilder(selfRecord?.syncExtras?.storageProto).apply {
      profileKey = self.profileKey?.toByteString() ?: ByteString.EMPTY
      givenName = self.profileName.givenName
      familyName = self.profileName.familyName
      avatarUrlPath = self.profileAvatar ?: ""
      noteToSelfArchived = selfRecord != null && selfRecord.syncExtras.isArchived
      noteToSelfMarkedUnread = selfRecord != null && selfRecord.syncExtras.isForcedUnread
      typingIndicators = TextSecurePreferences.isTypingIndicatorsEnabled(context)
      readReceipts = TextSecurePreferences.isReadReceiptsEnabled(context)
      sealedSenderIndicators = TextSecurePreferences.isShowUnidentifiedDeliveryIndicatorsEnabled(context)
      linkPreviews = SignalStore.settings.isLinkPreviewsEnabled
      unlistedPhoneNumber = SignalStore.phoneNumberPrivacy.phoneNumberDiscoverabilityMode == PhoneNumberDiscoverabilityMode.NOT_DISCOVERABLE
      phoneNumberSharingMode = StorageSyncModels.localToRemotePhoneNumberSharingMode(SignalStore.phoneNumberPrivacy.phoneNumberSharingMode)
      pinnedConversations = StorageSyncModels.localToRemotePinnedConversations(pinned)
      preferContactAvatars = SignalStore.settings.isPreferSystemContactPhotos
      primarySendsSms = false
      universalExpireTimer = SignalStore.settings.universalExpireTimer
      preferredReactionEmoji = SignalStore.emoji.reactions
      displayBadgesOnProfile = SignalStore.inAppPayments.getDisplayBadgesOnProfile()
      subscriptionManuallyCancelled = isUserManuallyCancelled(InAppPaymentSubscriberRecord.Type.DONATION)
      keepMutedChatsArchived = SignalStore.settings.shouldKeepMutedChatsArchived()
      hasSetMyStoriesPrivacy = SignalStore.story.userHasBeenNotifiedAboutStories
      hasViewedOnboardingStory = SignalStore.story.userHasViewedOnboardingStory
      storiesDisabled = SignalStore.story.isFeatureDisabled
      storyViewReceiptsEnabled = storyViewReceiptsState
      hasSeenGroupStoryEducationSheet = SignalStore.story.userHasSeenGroupStoryEducationSheet
      hasCompletedUsernameOnboarding = SignalStore.uiHints.hasCompletedUsernameOnboarding()
      avatarColor = StorageSyncModels.localToRemoteAvatarColor(self.avatarColor)
      username = SignalStore.account.username ?: ""
      usernameLink = SignalStore.account.usernameLink?.let { linkComponents ->
        AccountRecord.UsernameLink(
          entropy = linkComponents.entropy.toByteString(),
          serverId = linkComponents.serverId.toByteArray().toByteString(),
          color = StorageSyncModels.localToRemoteUsernameColor(SignalStore.misc.usernameQrCodeColorScheme)
        )
      }

      getSubscriber(InAppPaymentSubscriberRecord.Type.DONATION)?.let {
        safeSetSubscriber(it.subscriberId.bytes.toByteString(), it.currency?.currencyCode ?: "")
      }

      getSubscriber(InAppPaymentSubscriberRecord.Type.BACKUP)?.let {
        safeSetBackupsSubscriber(it.subscriberId.bytes.toByteString(), it.iapSubscriptionId)
      }

      safeSetPayments(SignalStore.payments.mobileCoinPaymentsEnabled(), Optional.ofNullable(SignalStore.payments.paymentsEntropy).map { obj: Entropy -> obj.bytes }.orElse(null))
    }

    return accountRecord.toSignalAccountRecord(StorageId.forAccount(storageId)).toSignalStorageRecord()
  }

  @JvmStatic
  fun applyAccountStorageSyncUpdates(context: Context, self: Recipient, updatedRecord: SignalAccountRecord, fetchProfile: Boolean) {
    val localRecord = buildAccountRecord(context, self).let { it.proto.account!!.toSignalAccountRecord(it.id) }
    applyAccountStorageSyncUpdates(context, self, StorageRecordUpdate(localRecord, updatedRecord), fetchProfile)
  }

  @JvmStatic
  fun applyAccountStorageSyncUpdates(context: Context, self: Recipient, update: StorageRecordUpdate<SignalAccountRecord>, fetchProfile: Boolean) {
    SignalDatabase.recipients.applyStorageSyncAccountUpdate(update)

    TextSecurePreferences.setReadReceiptsEnabled(context, update.new.proto.readReceipts)
    TextSecurePreferences.setTypingIndicatorsEnabled(context, update.new.proto.typingIndicators)
    TextSecurePreferences.setShowUnidentifiedDeliveryIndicatorsEnabled(context, update.new.proto.sealedSenderIndicators)
    SignalStore.settings.isLinkPreviewsEnabled = update.new.proto.linkPreviews
    SignalStore.phoneNumberPrivacy.phoneNumberDiscoverabilityMode = if (update.new.proto.unlistedPhoneNumber) PhoneNumberDiscoverabilityMode.NOT_DISCOVERABLE else PhoneNumberDiscoverabilityMode.DISCOVERABLE
    SignalStore.phoneNumberPrivacy.phoneNumberSharingMode = StorageSyncModels.remoteToLocalPhoneNumberSharingMode(update.new.proto.phoneNumberSharingMode)
    SignalStore.settings.isPreferSystemContactPhotos = update.new.proto.preferContactAvatars
    SignalStore.payments.setEnabledAndEntropy(update.new.proto.payments?.enabled == true, Entropy.fromBytes(update.new.proto.payments?.entropy?.toByteArray()))
    SignalStore.settings.universalExpireTimer = update.new.proto.universalExpireTimer
    SignalStore.emoji.reactions = update.new.proto.preferredReactionEmoji
    SignalStore.inAppPayments.setDisplayBadgesOnProfile(update.new.proto.displayBadgesOnProfile)
    SignalStore.settings.setKeepMutedChatsArchived(update.new.proto.keepMutedChatsArchived)
    SignalStore.story.userHasBeenNotifiedAboutStories = update.new.proto.hasSetMyStoriesPrivacy
    SignalStore.story.userHasViewedOnboardingStory = update.new.proto.hasViewedOnboardingStory
    SignalStore.story.isFeatureDisabled = update.new.proto.storiesDisabled
    SignalStore.story.userHasSeenGroupStoryEducationSheet = update.new.proto.hasSeenGroupStoryEducationSheet
    SignalStore.uiHints.setHasCompletedUsernameOnboarding(update.new.proto.hasCompletedUsernameOnboarding)

    if (update.new.proto.storyViewReceiptsEnabled == OptionalBool.UNSET) {
      SignalStore.story.viewedReceiptsEnabled = update.new.proto.readReceipts
    } else {
      SignalStore.story.viewedReceiptsEnabled = update.new.proto.storyViewReceiptsEnabled == OptionalBool.ENABLED
    }

    val remoteSubscriber = StorageSyncModels.remoteToLocalDonorSubscriber(update.new.proto.subscriberId, update.new.proto.subscriberCurrencyCode)
    if (remoteSubscriber != null) {
      setSubscriber(remoteSubscriber)
    }

    val remoteBackupsSubscriber = StorageSyncModels.remoteToLocalBackupSubscriber(update.new.proto.backupSubscriberData)
    if (remoteBackupsSubscriber != null) {
      setSubscriber(remoteBackupsSubscriber)
    }

    if (update.new.proto.subscriptionManuallyCancelled && !update.old.proto.subscriptionManuallyCancelled) {
      SignalStore.inAppPayments.updateLocalStateForManualCancellation(InAppPaymentSubscriberRecord.Type.DONATION)
    }

    if (fetchProfile && update.new.proto.avatarUrlPath.isNotBlank()) {
      AppDependencies.jobManager.add(RetrieveProfileAvatarJob(self, update.new.proto.avatarUrlPath))
    }

    if (update.new.proto.username != update.old.proto.username) {
      SignalStore.account.username = update.new.proto.username
      SignalStore.account.usernameSyncState = AccountValues.UsernameSyncState.IN_SYNC
      SignalStore.account.usernameSyncErrorCount = 0
    }

    if (update.new.proto.usernameLink != null) {
      SignalStore.account.usernameLink = UsernameLinkComponents(
        update.new.proto.usernameLink!!.entropy.toByteArray(),
        UuidUtil.parseOrThrow(update.new.proto.usernameLink!!.serverId.toByteArray())
      )

      SignalStore.misc.usernameQrCodeColorScheme = StorageSyncModels.remoteToLocalUsernameColor(update.new.proto.usernameLink!!.color)
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
