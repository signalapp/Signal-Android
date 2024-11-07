package org.thoughtcrime.securesms.storage

import android.content.Context
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.storage.StorageSyncHelper.applyAccountStorageSyncUpdates
import org.thoughtcrime.securesms.storage.StorageSyncHelper.buildAccountRecord
import org.whispersystems.signalservice.api.storage.SignalAccountRecord
import org.whispersystems.signalservice.api.util.OptionalUtil
import org.whispersystems.signalservice.internal.storage.protos.AccountRecord
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

  constructor(context: Context, self: Recipient) : this(context, self, buildAccountRecord(context, self).account.get())

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

  override fun getMatching(record: SignalAccountRecord, keyGenerator: StorageKeyGenerator): Optional<SignalAccountRecord> {
    return Optional.of(localAccountRecord)
  }

  override fun merge(remote: SignalAccountRecord, local: SignalAccountRecord, keyGenerator: StorageKeyGenerator): SignalAccountRecord {
    val givenName: String
    val familyName: String

    if (remote.givenName.isPresent || remote.familyName.isPresent) {
      givenName = remote.givenName.orElse("")
      familyName = remote.familyName.orElse("")
    } else {
      givenName = local.givenName.orElse("")
      familyName = local.familyName.orElse("")
    }

    val payments = if (remote.payments.entropy.isPresent) {
      remote.payments
    } else {
      local.payments
    }

    val subscriber = if (remote.subscriber.id.isPresent) {
      remote.subscriber
    } else {
      local.subscriber
    }

    val backupsSubscriber = if (remote.subscriber.id.isPresent) {
      remote.subscriber
    } else {
      local.subscriber
    }
    val storyViewReceiptsState = if (remote.storyViewReceiptsState == OptionalBool.UNSET) {
      local.storyViewReceiptsState
    } else {
      remote.storyViewReceiptsState
    }

    val unknownFields = remote.serializeUnknownFields()
    val avatarUrlPath = OptionalUtil.or(remote.avatarUrlPath, local.avatarUrlPath).orElse("")
    val profileKey = OptionalUtil.or(remote.profileKey, local.profileKey).orElse(null)
    val noteToSelfArchived = remote.isNoteToSelfArchived
    val noteToSelfForcedUnread = remote.isNoteToSelfForcedUnread
    val readReceipts = remote.isReadReceiptsEnabled
    val typingIndicators = remote.isTypingIndicatorsEnabled
    val sealedSenderIndicators = remote.isSealedSenderIndicatorsEnabled
    val linkPreviews = remote.isLinkPreviewsEnabled
    val unlisted = remote.isPhoneNumberUnlisted
    val pinnedConversations = remote.pinnedConversations
    val phoneNumberSharingMode = remote.phoneNumberSharingMode
    val preferContactAvatars = remote.isPreferContactAvatars
    val universalExpireTimer = remote.universalExpireTimer
    val primarySendsSms = if (SignalStore.account.isPrimaryDevice) local.isPrimarySendsSms else remote.isPrimarySendsSms
    val e164 = if (SignalStore.account.isPrimaryDevice) local.e164 else remote.e164
    val defaultReactions = if (remote.defaultReactions.size > 0) remote.defaultReactions else local.defaultReactions
    val displayBadgesOnProfile = remote.isDisplayBadgesOnProfile
    val subscriptionManuallyCancelled = remote.isSubscriptionManuallyCancelled
    val keepMutedChatsArchived = remote.isKeepMutedChatsArchived
    val hasSetMyStoriesPrivacy = remote.hasSetMyStoriesPrivacy()
    val hasViewedOnboardingStory = remote.hasViewedOnboardingStory() || local.hasViewedOnboardingStory()
    val storiesDisabled = remote.isStoriesDisabled
    val hasSeenGroupStoryEducation = remote.hasSeenGroupStoryEducationSheet() || local.hasSeenGroupStoryEducationSheet()
    val hasSeenUsernameOnboarding = remote.hasCompletedUsernameOnboarding() || local.hasCompletedUsernameOnboarding()
    val username = remote.username
    val usernameLink = remote.usernameLink

    val matchesRemote = doParamsMatch(
      contact = remote,
      unknownFields = unknownFields,
      givenName = givenName,
      familyName = familyName,
      avatarUrlPath = avatarUrlPath,
      profileKey = profileKey,
      noteToSelfArchived = noteToSelfArchived,
      noteToSelfForcedUnread = noteToSelfForcedUnread,
      readReceipts = readReceipts,
      typingIndicators = typingIndicators,
      sealedSenderIndicators = sealedSenderIndicators,
      linkPreviewsEnabled = linkPreviews,
      phoneNumberSharingMode = phoneNumberSharingMode,
      unlistedPhoneNumber = unlisted,
      pinnedConversations = pinnedConversations,
      preferContactAvatars = preferContactAvatars,
      payments = payments,
      universalExpireTimer = universalExpireTimer,
      primarySendsSms = primarySendsSms,
      e164 = e164,
      defaultReactions = defaultReactions,
      subscriber = subscriber,
      displayBadgesOnProfile = displayBadgesOnProfile,
      subscriptionManuallyCancelled = subscriptionManuallyCancelled,
      keepMutedChatsArchived = keepMutedChatsArchived,
      hasSetMyStoriesPrivacy = hasSetMyStoriesPrivacy,
      hasViewedOnboardingStory = hasViewedOnboardingStory,
      hasCompletedUsernameOnboarding = hasSeenUsernameOnboarding,
      storiesDisabled = storiesDisabled,
      storyViewReceiptsState = storyViewReceiptsState,
      username = username,
      usernameLink = usernameLink,
      backupsSubscriber = backupsSubscriber
    )
    val matchesLocal = doParamsMatch(
      contact = local,
      unknownFields = unknownFields,
      givenName = givenName,
      familyName = familyName,
      avatarUrlPath = avatarUrlPath,
      profileKey = profileKey,
      noteToSelfArchived = noteToSelfArchived,
      noteToSelfForcedUnread = noteToSelfForcedUnread,
      readReceipts = readReceipts,
      typingIndicators = typingIndicators,
      sealedSenderIndicators = sealedSenderIndicators,
      linkPreviewsEnabled = linkPreviews,
      phoneNumberSharingMode = phoneNumberSharingMode,
      unlistedPhoneNumber = unlisted,
      pinnedConversations = pinnedConversations,
      preferContactAvatars = preferContactAvatars,
      payments = payments,
      universalExpireTimer = universalExpireTimer,
      primarySendsSms = primarySendsSms,
      e164 = e164,
      defaultReactions = defaultReactions,
      subscriber = subscriber,
      displayBadgesOnProfile = displayBadgesOnProfile,
      subscriptionManuallyCancelled = subscriptionManuallyCancelled,
      keepMutedChatsArchived = keepMutedChatsArchived,
      hasSetMyStoriesPrivacy = hasSetMyStoriesPrivacy,
      hasViewedOnboardingStory = hasViewedOnboardingStory,
      hasCompletedUsernameOnboarding = hasSeenUsernameOnboarding,
      storiesDisabled = storiesDisabled,
      storyViewReceiptsState = storyViewReceiptsState,
      username = username,
      usernameLink = usernameLink,
      backupsSubscriber = backupsSubscriber
    )

    if (matchesRemote) {
      return remote
    } else if (matchesLocal) {
      return local
    } else {
      val builder = SignalAccountRecord.Builder(keyGenerator.generate(), unknownFields)
        .setGivenName(givenName)
        .setFamilyName(familyName)
        .setAvatarUrlPath(avatarUrlPath)
        .setProfileKey(profileKey)
        .setNoteToSelfArchived(noteToSelfArchived)
        .setNoteToSelfForcedUnread(noteToSelfForcedUnread)
        .setReadReceiptsEnabled(readReceipts)
        .setTypingIndicatorsEnabled(typingIndicators)
        .setSealedSenderIndicatorsEnabled(sealedSenderIndicators)
        .setLinkPreviewsEnabled(linkPreviews)
        .setUnlistedPhoneNumber(unlisted)
        .setPhoneNumberSharingMode(phoneNumberSharingMode)
        .setUnlistedPhoneNumber(unlisted)
        .setPinnedConversations(pinnedConversations)
        .setPreferContactAvatars(preferContactAvatars)
        .setPayments(payments.isEnabled, payments.entropy.orElse(null))
        .setUniversalExpireTimer(universalExpireTimer)
        .setPrimarySendsSms(primarySendsSms)
        .setDefaultReactions(defaultReactions)
        .setSubscriber(subscriber)
        .setStoryViewReceiptsState(storyViewReceiptsState)
        .setDisplayBadgesOnProfile(displayBadgesOnProfile)
        .setSubscriptionManuallyCancelled(subscriptionManuallyCancelled)
        .setKeepMutedChatsArchived(keepMutedChatsArchived)
        .setHasSetMyStoriesPrivacy(hasSetMyStoriesPrivacy)
        .setHasViewedOnboardingStory(hasViewedOnboardingStory)
        .setStoriesDisabled(storiesDisabled)
        .setHasSeenGroupStoryEducationSheet(hasSeenGroupStoryEducation)
        .setHasCompletedUsernameOnboarding(hasSeenUsernameOnboarding)
        .setUsername(username)
        .setUsernameLink(usernameLink)
        .setBackupsSubscriber(backupsSubscriber)

      return builder.build()
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

  private fun doParamsMatch(
    contact: SignalAccountRecord,
    unknownFields: ByteArray?,
    givenName: String,
    familyName: String,
    avatarUrlPath: String,
    profileKey: ByteArray?,
    noteToSelfArchived: Boolean,
    noteToSelfForcedUnread: Boolean,
    readReceipts: Boolean,
    typingIndicators: Boolean,
    sealedSenderIndicators: Boolean,
    linkPreviewsEnabled: Boolean,
    phoneNumberSharingMode: AccountRecord.PhoneNumberSharingMode,
    unlistedPhoneNumber: Boolean,
    pinnedConversations: List<SignalAccountRecord.PinnedConversation>,
    preferContactAvatars: Boolean,
    payments: SignalAccountRecord.Payments,
    universalExpireTimer: Int,
    primarySendsSms: Boolean,
    e164: String,
    defaultReactions: List<String>,
    subscriber: SignalAccountRecord.Subscriber,
    displayBadgesOnProfile: Boolean,
    subscriptionManuallyCancelled: Boolean,
    keepMutedChatsArchived: Boolean,
    hasSetMyStoriesPrivacy: Boolean,
    hasViewedOnboardingStory: Boolean,
    hasCompletedUsernameOnboarding: Boolean,
    storiesDisabled: Boolean,
    storyViewReceiptsState: OptionalBool,
    username: String?,
    usernameLink: AccountRecord.UsernameLink?,
    backupsSubscriber: SignalAccountRecord.Subscriber
  ): Boolean {
    return contact.serializeUnknownFields().contentEquals(unknownFields) &&
      contact.givenName.orElse("") == givenName &&
      contact.familyName.orElse("") == familyName &&
      contact.avatarUrlPath.orElse("") == avatarUrlPath &&
      contact.payments == payments &&
      contact.e164 == e164 &&
      contact.defaultReactions == defaultReactions &&
      contact.profileKey.orElse(null).contentEquals(profileKey) &&
      contact.isNoteToSelfArchived == noteToSelfArchived &&
      contact.isNoteToSelfForcedUnread == noteToSelfForcedUnread &&
      contact.isReadReceiptsEnabled == readReceipts &&
      contact.isTypingIndicatorsEnabled == typingIndicators &&
      contact.isSealedSenderIndicatorsEnabled == sealedSenderIndicators &&
      contact.isLinkPreviewsEnabled == linkPreviewsEnabled &&
      contact.phoneNumberSharingMode == phoneNumberSharingMode &&
      contact.isPhoneNumberUnlisted == unlistedPhoneNumber &&
      contact.isPreferContactAvatars == preferContactAvatars &&
      contact.universalExpireTimer == universalExpireTimer &&
      contact.isPrimarySendsSms == primarySendsSms &&
      contact.pinnedConversations == pinnedConversations &&
      contact.subscriber == subscriber &&
      contact.isDisplayBadgesOnProfile == displayBadgesOnProfile &&
      contact.isSubscriptionManuallyCancelled == subscriptionManuallyCancelled &&
      contact.isKeepMutedChatsArchived == keepMutedChatsArchived &&
      contact.hasSetMyStoriesPrivacy() == hasSetMyStoriesPrivacy &&
      contact.hasViewedOnboardingStory() == hasViewedOnboardingStory &&
      contact.hasCompletedUsernameOnboarding() == hasCompletedUsernameOnboarding &&
      contact.isStoriesDisabled == storiesDisabled &&
      contact.storyViewReceiptsState == storyViewReceiptsState &&
      contact.username == username &&
      contact.usernameLink == usernameLink &&
      contact.backupsSubscriber == backupsSubscriber
  }
}
