/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.processor

import android.content.Context
import okio.ByteString.Companion.EMPTY
import okio.ByteString.Companion.toByteString
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.attachments.AttachmentId
import org.thoughtcrime.securesms.backup.v2.ImportState
import org.thoughtcrime.securesms.backup.v2.database.restoreSelfFromBackup
import org.thoughtcrime.securesms.backup.v2.database.restoreWallpaperAttachment
import org.thoughtcrime.securesms.backup.v2.proto.AccountData
import org.thoughtcrime.securesms.backup.v2.proto.ChatStyle
import org.thoughtcrime.securesms.backup.v2.proto.Frame
import org.thoughtcrime.securesms.backup.v2.stream.BackupFrameEmitter
import org.thoughtcrime.securesms.backup.v2.util.ChatStyleConverter
import org.thoughtcrime.securesms.backup.v2.util.parseChatWallpaper
import org.thoughtcrime.securesms.backup.v2.util.toLocal
import org.thoughtcrime.securesms.backup.v2.util.toLocalAttachment
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository
import org.thoughtcrime.securesms.components.settings.app.usernamelinks.UsernameQrCodeColorScheme
import org.thoughtcrime.securesms.conversation.colors.ChatColors
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.RetrieveProfileAvatarJob
import org.thoughtcrime.securesms.keyvalue.PhoneNumberPrivacyValues
import org.thoughtcrime.securesms.keyvalue.PhoneNumberPrivacyValues.PhoneNumberDiscoverabilityMode
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.ProfileUtil
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.api.push.UsernameLinkComponents
import org.whispersystems.signalservice.api.subscriptions.SubscriberId
import org.whispersystems.signalservice.api.util.UuidUtil
import org.whispersystems.signalservice.api.util.toByteArray
import java.util.Currency

/**
 * Handles importing/exporting [AccountData] frames for an archive.
 */
object AccountDataArchiveProcessor {

  private val TAG = Log.tag(AccountDataArchiveProcessor::class)

  fun export(db: SignalDatabase, signalStore: SignalStore, emitter: BackupFrameEmitter) {
    val context = AppDependencies.application

    val selfId = db.recipientTable.getByAci(signalStore.accountValues.aci!!).get()
    val selfRecord = db.recipientTable.getRecordForSync(selfId)!!

    val donationCurrency = signalStore.inAppPaymentValues.getSubscriptionCurrency(InAppPaymentSubscriberRecord.Type.DONATION)
    val donationSubscriber = db.inAppPaymentSubscriberTable.getByCurrencyCode(donationCurrency.currencyCode, InAppPaymentSubscriberRecord.Type.DONATION)

    val chatColors = SignalStore.chatColors.chatColors
    val chatWallpaper = SignalStore.wallpaper.currentRawWallpaper

    emitter.emit(
      Frame(
        account = AccountData(
          profileKey = selfRecord.profileKey?.toByteString() ?: EMPTY,
          givenName = selfRecord.signalProfileName.givenName,
          familyName = selfRecord.signalProfileName.familyName,
          avatarUrlPath = selfRecord.signalProfileAvatar ?: "",
          username = selfRecord.username,
          usernameLink = if (signalStore.accountValues.usernameLink != null) {
            AccountData.UsernameLink(
              entropy = signalStore.accountValues.usernameLink?.entropy?.toByteString() ?: EMPTY,
              serverId = signalStore.accountValues.usernameLink?.serverId?.toByteArray()?.toByteString() ?: EMPTY,
              color = signalStore.miscValues.usernameQrCodeColorScheme.toRemoteUsernameColor()
            )
          } else {
            null
          },
          accountSettings = AccountData.AccountSettings(
            storyViewReceiptsEnabled = signalStore.storyValues.viewedReceiptsEnabled,
            typingIndicators = TextSecurePreferences.isTypingIndicatorsEnabled(context),
            readReceipts = TextSecurePreferences.isReadReceiptsEnabled(context),
            sealedSenderIndicators = TextSecurePreferences.isShowUnidentifiedDeliveryIndicatorsEnabled(context),
            linkPreviews = signalStore.settingsValues.isLinkPreviewsEnabled,
            notDiscoverableByPhoneNumber = signalStore.phoneNumberPrivacyValues.phoneNumberDiscoverabilityMode == PhoneNumberDiscoverabilityMode.NOT_DISCOVERABLE,
            phoneNumberSharingMode = signalStore.phoneNumberPrivacyValues.phoneNumberSharingMode.toRemotePhoneNumberSharingMode(),
            preferContactAvatars = signalStore.settingsValues.isPreferSystemContactPhotos,
            universalExpireTimerSeconds = signalStore.settingsValues.universalExpireTimer,
            preferredReactionEmoji = signalStore.emojiValues.rawReactions,
            storiesDisabled = signalStore.storyValues.isFeatureDisabled,
            hasViewedOnboardingStory = signalStore.storyValues.userHasViewedOnboardingStory,
            hasSetMyStoriesPrivacy = signalStore.storyValues.userHasBeenNotifiedAboutStories,
            keepMutedChatsArchived = signalStore.settingsValues.shouldKeepMutedChatsArchived(),
            displayBadgesOnProfile = signalStore.inAppPaymentValues.getDisplayBadgesOnProfile(),
            hasSeenGroupStoryEducationSheet = signalStore.storyValues.userHasSeenGroupStoryEducationSheet,
            hasCompletedUsernameOnboarding = signalStore.uiHintValues.hasCompletedUsernameOnboarding(),
            customChatColors = db.chatColorsTable.getSavedChatColors().toRemoteChatColors(),
            defaultChatStyle = ChatStyleConverter.constructRemoteChatStyle(
              db = db,
              chatColors = chatColors,
              chatColorId = chatColors?.id ?: ChatColors.Id.NotSet,
              chatWallpaper = chatWallpaper
            )
          ),
          donationSubscriberData = donationSubscriber?.toSubscriberData(signalStore.inAppPaymentValues.isDonationSubscriptionManuallyCancelled())
        )
      )
    )
  }

  fun import(accountData: AccountData, selfId: RecipientId, importState: ImportState) {
    SignalDatabase.recipients.restoreSelfFromBackup(accountData, selfId)

    SignalStore.account.setRegistered(true)

    val context = AppDependencies.application
    val settings = accountData.accountSettings

    if (settings != null) {
      importSettings(context, settings, importState)
    }

    if (accountData.donationSubscriberData != null) {
      if (accountData.donationSubscriberData.subscriberId.size > 0) {
        val remoteSubscriberId = SubscriberId.fromBytes(accountData.donationSubscriberData.subscriberId.toByteArray())
        val localSubscriber = InAppPaymentsRepository.getSubscriber(InAppPaymentSubscriberRecord.Type.DONATION)

        val subscriber = InAppPaymentSubscriberRecord(
          remoteSubscriberId,
          Currency.getInstance(accountData.donationSubscriberData.currencyCode),
          InAppPaymentSubscriberRecord.Type.DONATION,
          localSubscriber?.requiresCancel ?: accountData.donationSubscriberData.manuallyCancelled,
          InAppPaymentsRepository.getLatestPaymentMethodType(InAppPaymentSubscriberRecord.Type.DONATION)
        )

        InAppPaymentsRepository.setSubscriber(subscriber)
      }

      if (accountData.donationSubscriberData.manuallyCancelled) {
        SignalStore.inAppPayments.updateLocalStateForManualCancellation(InAppPaymentSubscriberRecord.Type.DONATION)
      }
    }

    if (accountData.avatarUrlPath.isNotEmpty()) {
      AppDependencies.jobManager.add(RetrieveProfileAvatarJob(Recipient.self().fresh(), accountData.avatarUrlPath))
    }

    if (accountData.usernameLink != null) {
      SignalStore.account.usernameLink = UsernameLinkComponents(
        accountData.usernameLink.entropy.toByteArray(),
        UuidUtil.parseOrThrow(accountData.usernameLink.serverId.toByteArray())
      )
      SignalStore.misc.usernameQrCodeColorScheme = accountData.usernameLink.color.toLocalUsernameColor()
    } else {
      SignalStore.account.usernameLink = null
    }

    SignalDatabase.runPostSuccessfulTransaction { ProfileUtil.handleSelfProfileKeyChange() }

    Recipient.self().live().refresh()
  }

  private fun importSettings(context: Context, settings: AccountData.AccountSettings, importState: ImportState) {
    TextSecurePreferences.setReadReceiptsEnabled(context, settings.readReceipts)
    TextSecurePreferences.setTypingIndicatorsEnabled(context, settings.typingIndicators)
    TextSecurePreferences.setShowUnidentifiedDeliveryIndicatorsEnabled(context, settings.sealedSenderIndicators)
    SignalStore.settings.isLinkPreviewsEnabled = settings.linkPreviews
    SignalStore.phoneNumberPrivacy.phoneNumberDiscoverabilityMode = if (settings.notDiscoverableByPhoneNumber) PhoneNumberDiscoverabilityMode.NOT_DISCOVERABLE else PhoneNumberDiscoverabilityMode.DISCOVERABLE
    SignalStore.phoneNumberPrivacy.phoneNumberSharingMode = settings.phoneNumberSharingMode.toLocalPhoneNumberMode()
    SignalStore.settings.isPreferSystemContactPhotos = settings.preferContactAvatars
    SignalStore.settings.universalExpireTimer = settings.universalExpireTimerSeconds
    SignalStore.emoji.reactions = settings.preferredReactionEmoji
    SignalStore.inAppPayments.setDisplayBadgesOnProfile(settings.displayBadgesOnProfile)
    SignalStore.settings.setKeepMutedChatsArchived(settings.keepMutedChatsArchived)
    SignalStore.story.userHasBeenNotifiedAboutStories = settings.hasSetMyStoriesPrivacy
    SignalStore.story.userHasViewedOnboardingStory = settings.hasViewedOnboardingStory
    SignalStore.story.isFeatureDisabled = settings.storiesDisabled
    SignalStore.story.userHasSeenGroupStoryEducationSheet = settings.hasSeenGroupStoryEducationSheet
    SignalStore.story.viewedReceiptsEnabled = settings.storyViewReceiptsEnabled ?: settings.readReceipts

    settings.customChatColors
      .mapNotNull { chatColor ->
        val id = ChatColors.Id.forLongValue(chatColor.id)
        when {
          chatColor.solid != null -> {
            ChatColors.forColor(id, chatColor.solid)
          }
          chatColor.gradient != null -> {
            ChatColors.forGradient(
              id,
              ChatColors.LinearGradient(
                degrees = chatColor.gradient.angle.toFloat(),
                colors = chatColor.gradient.colors.toIntArray(),
                positions = chatColor.gradient.positions.toFloatArray()
              )
            )
          }
          else -> null
        }
      }
      .forEach { chatColor ->
        // We need to use the "NotSet" chatId so that this operation is treated as an insert rather than an update
        val saved = SignalDatabase.chatColors.saveChatColors(chatColor.withId(ChatColors.Id.NotSet))
        importState.remoteToLocalColorId[chatColor.id.longValue] = saved.id.longValue
      }

    if (settings.defaultChatStyle != null) {
      val chatColors = settings.defaultChatStyle.toLocal(importState)
      SignalStore.chatColors.chatColors = chatColors

      val wallpaperAttachmentId: AttachmentId? = settings.defaultChatStyle.wallpaperPhoto?.let { filePointer ->
        filePointer.toLocalAttachment(importState)?.let {
          SignalDatabase.attachments.restoreWallpaperAttachment(it)
        }
      }

      SignalStore.wallpaper.wallpaper = settings.defaultChatStyle.parseChatWallpaper(wallpaperAttachmentId)
    } else {
      SignalStore.chatColors.chatColors = null
      SignalStore.wallpaper.wallpaper = null
    }

    if (settings.preferredReactionEmoji.isNotEmpty()) {
      SignalStore.emoji.reactions = settings.preferredReactionEmoji
    }

    if (settings.hasCompletedUsernameOnboarding) {
      SignalStore.uiHints.setHasCompletedUsernameOnboarding(true)
    }
  }

  private fun PhoneNumberPrivacyValues.PhoneNumberSharingMode.toRemotePhoneNumberSharingMode(): AccountData.PhoneNumberSharingMode {
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

  private fun UsernameQrCodeColorScheme.toRemoteUsernameColor(): AccountData.UsernameLink.Color {
    return when (this) {
      UsernameQrCodeColorScheme.Blue -> AccountData.UsernameLink.Color.BLUE
      UsernameQrCodeColorScheme.White -> AccountData.UsernameLink.Color.WHITE
      UsernameQrCodeColorScheme.Grey -> AccountData.UsernameLink.Color.GREY
      UsernameQrCodeColorScheme.Tan -> AccountData.UsernameLink.Color.OLIVE
      UsernameQrCodeColorScheme.Green -> AccountData.UsernameLink.Color.GREEN
      UsernameQrCodeColorScheme.Orange -> AccountData.UsernameLink.Color.ORANGE
      UsernameQrCodeColorScheme.Pink -> AccountData.UsernameLink.Color.PINK
      UsernameQrCodeColorScheme.Purple -> AccountData.UsernameLink.Color.PURPLE
    }
  }

  private fun InAppPaymentSubscriberRecord.toSubscriberData(manuallyCancelled: Boolean): AccountData.SubscriberData {
    val subscriberId = subscriberId.bytes.toByteString()
    val currencyCode = currency.currencyCode
    return AccountData.SubscriberData(subscriberId = subscriberId, currencyCode = currencyCode, manuallyCancelled = manuallyCancelled)
  }

  private fun List<ChatColors>.toRemoteChatColors(): List<ChatStyle.CustomChatColor> {
    return this
      .mapNotNull { local ->
        if (local.linearGradient != null) {
          ChatStyle.CustomChatColor(
            id = local.id.longValue,
            gradient = ChatStyle.Gradient(
              angle = local.linearGradient.degrees.toInt(),
              colors = local.linearGradient.colors.toList(),
              positions = local.linearGradient.positions.toList()
            )
          )
        } else if (local.singleColor != null) {
          ChatStyle.CustomChatColor(
            id = local.id.longValue,
            solid = local.singleColor
          )
        } else {
          Log.w(TAG, "Invalid custom color (id = ${local.id}, no gradient or solid color!")
          null
        }
      }
  }
}
