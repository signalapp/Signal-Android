package org.thoughtcrime.securesms.storage

import android.content.Context
import okio.ByteString
import org.signal.core.util.isNotEmpty
import org.signal.core.util.logging.Log
import org.signal.core.util.nullIfEmpty
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.storage.StorageSyncHelper.applyAccountStorageSyncUpdates
import org.whispersystems.signalservice.api.storage.IAPSubscriptionId
import org.whispersystems.signalservice.api.storage.SignalAccountRecord
import org.whispersystems.signalservice.api.storage.StorageId
import org.whispersystems.signalservice.api.storage.safeSetBackupsSubscriber
import org.whispersystems.signalservice.api.storage.safeSetPayments
import org.whispersystems.signalservice.api.storage.safeSetSubscriber
import org.whispersystems.signalservice.api.storage.toSignalAccountRecord
import org.whispersystems.signalservice.internal.storage.protos.OptionalBool
import java.util.Optional

/**
 * Processes [SignalAccountRecord]s. Unlike some other [StorageRecordProcessor]s, this
 * one has some statefulness in order to reject all but one account record (since we should have
 * exactly one account record).
 */
class AccountRecordProcessor(
  private val context: Context,
  private val self: Recipient,
  private val localAccountRecord: SignalAccountRecord
) : DefaultStorageRecordProcessor<SignalAccountRecord>() {

  companion object {
    private val TAG = Log.tag(AccountRecordProcessor::class.java)
  }

  private var foundAccountRecord = false

  constructor(context: Context, self: Recipient) : this(
    context = context,
    self = self,
    localAccountRecord = StorageSyncHelper.buildAccountRecord(context, self).let { it.proto.account!!.toSignalAccountRecord(it.id) }
  )

  /**
   * We want to catch:
   * - Multiple account records
   */
  override fun isInvalid(remote: SignalAccountRecord): Boolean {
    if (foundAccountRecord) {
      Log.w(TAG, "Found an additional account record! Considering it invalid.")
      return true
    }

    foundAccountRecord = true
    return false
  }

  override fun getMatching(remote: SignalAccountRecord, keyGenerator: StorageKeyGenerator): Optional<SignalAccountRecord> {
    return Optional.of(localAccountRecord)
  }

  override fun merge(remote: SignalAccountRecord, local: SignalAccountRecord, keyGenerator: StorageKeyGenerator): SignalAccountRecord {
    val mergedGivenName: String
    val mergedFamilyName: String

    if (remote.proto.givenName.isNotBlank() || remote.proto.familyName.isNotBlank()) {
      mergedGivenName = remote.proto.givenName
      mergedFamilyName = remote.proto.familyName
    } else {
      mergedGivenName = local.proto.givenName
      mergedFamilyName = local.proto.familyName
    }

    val payments = if (remote.proto.payments?.entropy != null) {
      remote.proto.payments
    } else {
      local.proto.payments
    }

    val donationSubscriberId: ByteString
    val donationSubscriberCurrencyCode: String

    if (remote.proto.subscriberId.isNotEmpty()) {
      donationSubscriberId = remote.proto.subscriberId
      donationSubscriberCurrencyCode = remote.proto.subscriberCurrencyCode
    } else {
      donationSubscriberId = local.proto.subscriberId
      donationSubscriberCurrencyCode = remote.proto.subscriberCurrencyCode
    }

    val backupsSubscriberId: ByteString
    val backupsPurchaseToken: IAPSubscriptionId?

    val remoteBackupSubscriberData = remote.proto.backupSubscriberData
    if (remoteBackupSubscriberData != null && remoteBackupSubscriberData.subscriberId.isNotEmpty()) {
      backupsSubscriberId = remoteBackupSubscriberData.subscriberId
      backupsPurchaseToken = IAPSubscriptionId.from(remoteBackupSubscriberData)
    } else {
      backupsSubscriberId = local.proto.backupSubscriberData?.subscriberId ?: ByteString.EMPTY
      backupsPurchaseToken = IAPSubscriptionId.from(local.proto.backupSubscriberData)
    }

    val storyViewReceiptsState = if (remote.proto.storyViewReceiptsEnabled == OptionalBool.UNSET) {
      local.proto.storyViewReceiptsEnabled
    } else {
      remote.proto.storyViewReceiptsEnabled
    }

    val unknownFields = remote.serializedUnknowns

    val merged = SignalAccountRecord.newBuilder(unknownFields).apply {
      givenName = mergedGivenName
      familyName = mergedFamilyName
      avatarUrlPath = remote.proto.avatarUrlPath.nullIfEmpty() ?: local.proto.avatarUrlPath
      profileKey = remote.proto.profileKey.nullIfEmpty() ?: local.proto.profileKey
      noteToSelfArchived = remote.proto.noteToSelfArchived
      noteToSelfMarkedUnread = remote.proto.noteToSelfMarkedUnread
      readReceipts = remote.proto.readReceipts
      typingIndicators = remote.proto.typingIndicators
      sealedSenderIndicators = remote.proto.sealedSenderIndicators
      linkPreviews = remote.proto.linkPreviews
      unlistedPhoneNumber = remote.proto.unlistedPhoneNumber
      pinnedConversations = remote.proto.pinnedConversations
      phoneNumberSharingMode = remote.proto.phoneNumberSharingMode
      preferContactAvatars = remote.proto.preferContactAvatars
      universalExpireTimer = remote.proto.universalExpireTimer
      primarySendsSms = false
      e164 = if (SignalStore.account.isPrimaryDevice) local.proto.e164 else remote.proto.e164
      preferredReactionEmoji = remote.proto.preferredReactionEmoji.takeIf { it.isNotEmpty() } ?: local.proto.preferredReactionEmoji
      displayBadgesOnProfile = remote.proto.displayBadgesOnProfile
      subscriptionManuallyCancelled = remote.proto.subscriptionManuallyCancelled
      keepMutedChatsArchived = remote.proto.keepMutedChatsArchived
      hasSetMyStoriesPrivacy = remote.proto.hasSetMyStoriesPrivacy
      hasViewedOnboardingStory = remote.proto.hasViewedOnboardingStory || local.proto.hasViewedOnboardingStory
      storiesDisabled = remote.proto.storiesDisabled
      storyViewReceiptsEnabled = storyViewReceiptsState
      hasSeenGroupStoryEducationSheet = remote.proto.hasSeenGroupStoryEducationSheet || local.proto.hasSeenGroupStoryEducationSheet
      hasCompletedUsernameOnboarding = remote.proto.hasCompletedUsernameOnboarding || local.proto.hasCompletedUsernameOnboarding
      username = remote.proto.username
      usernameLink = remote.proto.usernameLink

      safeSetPayments(payments?.enabled == true, payments?.entropy?.toByteArray())
      safeSetSubscriber(donationSubscriberId, donationSubscriberCurrencyCode)
      safeSetBackupsSubscriber(backupsSubscriberId, backupsPurchaseToken)
    }.toSignalAccountRecord(StorageId.forAccount(keyGenerator.generate()))

    return if (doParamsMatch(remote, merged)) {
      remote
    } else if (doParamsMatch(local, merged)) {
      local
    } else {
      merged
    }
  }

  override fun insertLocal(record: SignalAccountRecord) {
    throw UnsupportedOperationException("We should always have a local AccountRecord, so we should never been inserting a new one.")
  }

  override fun updateLocal(update: StorageRecordUpdate<SignalAccountRecord>) {
    applyAccountStorageSyncUpdates(context, self, update, true)
  }

  override fun compare(lhs: SignalAccountRecord, rhs: SignalAccountRecord): Int {
    return 0
  }
}
