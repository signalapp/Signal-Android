/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.processor

import okio.ByteString.Companion.EMPTY
import okio.ByteString.Companion.toByteString
import org.thoughtcrime.securesms.backup.v2.database.restoreSelfFromBackup
import org.thoughtcrime.securesms.backup.v2.proto.AccountData
import org.thoughtcrime.securesms.backup.v2.proto.Frame
import org.thoughtcrime.securesms.backup.v2.stream.BackupFrameEmitter
import org.thoughtcrime.securesms.components.settings.app.usernamelinks.UsernameQrCodeColorScheme
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.recipients
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobs.RetrieveProfileAvatarJob
import org.thoughtcrime.securesms.keyvalue.PhoneNumberPrivacyValues
import org.thoughtcrime.securesms.keyvalue.PhoneNumberPrivacyValues.PhoneNumberDiscoverabilityMode
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.subscription.Subscriber
import org.thoughtcrime.securesms.util.ProfileUtil
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.api.push.UsernameLinkComponents
import org.whispersystems.signalservice.api.storage.StorageRecordProtoUtil.defaultAccountRecord
import org.whispersystems.signalservice.api.subscriptions.SubscriberId
import org.whispersystems.signalservice.api.util.UuidUtil

object AccountDataProcessor {

  fun export(emitter: BackupFrameEmitter) {
    val context = ApplicationDependencies.getApplication()

    val self = Recipient.self().fresh()
    val record = recipients.getRecordForSync(self.id)

    val subscriber: Subscriber? = SignalStore.donationsValues().getSubscriber()

    emitter.emit(
      Frame(
        account = AccountData(
          profileKey = self.profileKey?.toByteString() ?: EMPTY,
          givenName = self.profileName.givenName,
          familyName = self.profileName.familyName,
          avatarUrlPath = self.profileAvatar ?: "",
          subscriptionManuallyCancelled = SignalStore.donationsValues().isUserManuallyCancelled(),
          username = SignalStore.account().username,
          subscriberId = subscriber?.subscriberId?.bytes?.toByteString() ?: defaultAccountRecord.subscriberId,
          subscriberCurrencyCode = subscriber?.currencyCode ?: defaultAccountRecord.subscriberCurrencyCode,
          accountSettings = AccountData.AccountSettings(
            storyViewReceiptsEnabled = SignalStore.storyValues().viewedReceiptsEnabled,
            noteToSelfMarkedUnread = record != null && record.syncExtras.isForcedUnread,
            typingIndicators = TextSecurePreferences.isTypingIndicatorsEnabled(context),
            readReceipts = TextSecurePreferences.isReadReceiptsEnabled(context),
            sealedSenderIndicators = TextSecurePreferences.isShowUnidentifiedDeliveryIndicatorsEnabled(context),
            linkPreviews = SignalStore.settings().isLinkPreviewsEnabled,
            notDiscoverableByPhoneNumber = SignalStore.phoneNumberPrivacy().phoneNumberDiscoverabilityMode == PhoneNumberDiscoverabilityMode.NOT_DISCOVERABLE,
            phoneNumberSharingMode = SignalStore.phoneNumberPrivacy().phoneNumberSharingMode.toBackupPhoneNumberSharingMode(),
            preferContactAvatars = SignalStore.settings().isPreferSystemContactPhotos,
            universalExpireTimer = SignalStore.settings().universalExpireTimer,
            preferredReactionEmoji = SignalStore.emojiValues().reactions,
            storiesDisabled = SignalStore.storyValues().isFeatureDisabled,
            hasViewedOnboardingStory = SignalStore.storyValues().userHasViewedOnboardingStory,
            hasSetMyStoriesPrivacy = SignalStore.storyValues().userHasBeenNotifiedAboutStories,
            keepMutedChatsArchived = SignalStore.settings().shouldKeepMutedChatsArchived(),
            displayBadgesOnProfile = SignalStore.donationsValues().getDisplayBadgesOnProfile(),
            hasSeenGroupStoryEducationSheet = SignalStore.storyValues().userHasSeenGroupStoryEducationSheet
          )
        )
      )
    )
  }

  fun import(accountData: AccountData, selfId: RecipientId) {
    recipients.restoreSelfFromBackup(accountData, selfId)

    SignalStore.account().setRegistered(true)

    val context = ApplicationDependencies.getApplication()
    val settings = accountData.accountSettings

    if (settings != null) {
      TextSecurePreferences.setReadReceiptsEnabled(context, settings.readReceipts)
      TextSecurePreferences.setTypingIndicatorsEnabled(context, settings.typingIndicators)
      TextSecurePreferences.setShowUnidentifiedDeliveryIndicatorsEnabled(context, settings.sealedSenderIndicators)
      SignalStore.settings().isLinkPreviewsEnabled = settings.linkPreviews
      SignalStore.phoneNumberPrivacy().phoneNumberDiscoverabilityMode = if (settings.notDiscoverableByPhoneNumber) PhoneNumberDiscoverabilityMode.NOT_DISCOVERABLE else PhoneNumberDiscoverabilityMode.DISCOVERABLE
      SignalStore.phoneNumberPrivacy().phoneNumberSharingMode = settings.phoneNumberSharingMode.toLocalPhoneNumberMode()
      SignalStore.settings().isPreferSystemContactPhotos = settings.preferContactAvatars
      SignalStore.settings().universalExpireTimer = settings.universalExpireTimer
      SignalStore.emojiValues().reactions = settings.preferredReactionEmoji
      SignalStore.donationsValues().setDisplayBadgesOnProfile(settings.displayBadgesOnProfile)
      SignalStore.settings().setKeepMutedChatsArchived(settings.keepMutedChatsArchived)
      SignalStore.storyValues().userHasBeenNotifiedAboutStories = settings.hasSetMyStoriesPrivacy
      SignalStore.storyValues().userHasViewedOnboardingStory = settings.hasViewedOnboardingStory
      SignalStore.storyValues().isFeatureDisabled = settings.storiesDisabled
      SignalStore.storyValues().userHasSeenGroupStoryEducationSheet = settings.hasSeenGroupStoryEducationSheet
      SignalStore.storyValues().viewedReceiptsEnabled = settings.storyViewReceiptsEnabled ?: settings.readReceipts

      if (accountData.subscriptionManuallyCancelled) {
        SignalStore.donationsValues().updateLocalStateForManualCancellation()
      } else {
        SignalStore.donationsValues().clearUserManuallyCancelled()
      }

      if (accountData.subscriberId.size > 0) {
        val subscriber = Subscriber(SubscriberId.fromBytes(accountData.subscriberId.toByteArray()), accountData.subscriberCurrencyCode)
        SignalStore.donationsValues().setSubscriber(subscriber)
      }

      if (accountData.avatarUrlPath.isNotEmpty()) {
        ApplicationDependencies.getJobManager().add(RetrieveProfileAvatarJob(Recipient.self().fresh(), accountData.avatarUrlPath))
      }

      if (accountData.usernameLink != null) {
        SignalStore.account().usernameLink = UsernameLinkComponents(
          accountData.usernameLink.entropy.toByteArray(),
          UuidUtil.parseOrThrow(accountData.usernameLink.serverId.toByteArray())
        )
        SignalStore.misc().usernameQrCodeColorScheme = accountData.usernameLink.color.toLocalUsernameColor()
      }
    }

    SignalDatabase.runPostSuccessfulTransaction { ProfileUtil.handleSelfProfileKeyChange() }

    Recipient.self().live().refresh()
  }

  private fun PhoneNumberPrivacyValues.PhoneNumberSharingMode.toBackupPhoneNumberSharingMode(): AccountData.PhoneNumberSharingMode {
    return when (this) {
      PhoneNumberPrivacyValues.PhoneNumberSharingMode.DEFAULT -> AccountData.PhoneNumberSharingMode.EVERYBODY
      PhoneNumberPrivacyValues.PhoneNumberSharingMode.EVERYBODY -> AccountData.PhoneNumberSharingMode.EVERYBODY
      PhoneNumberPrivacyValues.PhoneNumberSharingMode.NOBODY -> AccountData.PhoneNumberSharingMode.NOBODY
    }
  }

  private fun AccountData.PhoneNumberSharingMode.toLocalPhoneNumberMode(): PhoneNumberPrivacyValues.PhoneNumberSharingMode {
    return when (this) {
      AccountData.PhoneNumberSharingMode.UNKNOWN -> PhoneNumberPrivacyValues.PhoneNumberSharingMode.EVERYBODY
      AccountData.PhoneNumberSharingMode.EVERYBODY -> PhoneNumberPrivacyValues.PhoneNumberSharingMode.EVERYBODY
      AccountData.PhoneNumberSharingMode.NOBODY -> PhoneNumberPrivacyValues.PhoneNumberSharingMode.NOBODY
    }
  }

  private fun AccountData.UsernameLink.Color?.toLocalUsernameColor(): UsernameQrCodeColorScheme {
    return when (this) {
      AccountData.UsernameLink.Color.BLUE -> UsernameQrCodeColorScheme.Blue
      AccountData.UsernameLink.Color.WHITE -> UsernameQrCodeColorScheme.White
      AccountData.UsernameLink.Color.GREY -> UsernameQrCodeColorScheme.Grey
      AccountData.UsernameLink.Color.OLIVE -> UsernameQrCodeColorScheme.Tan
      AccountData.UsernameLink.Color.GREEN -> UsernameQrCodeColorScheme.Green
      AccountData.UsernameLink.Color.ORANGE -> UsernameQrCodeColorScheme.Orange
      AccountData.UsernameLink.Color.PINK -> UsernameQrCodeColorScheme.Pink
      AccountData.UsernameLink.Color.PURPLE -> UsernameQrCodeColorScheme.Purple
      else -> UsernameQrCodeColorScheme.Blue
    }
  }
}
