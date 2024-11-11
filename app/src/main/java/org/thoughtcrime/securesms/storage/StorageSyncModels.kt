package org.thoughtcrime.securesms.storage

import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.signal.core.util.isNotEmpty
import org.signal.libsignal.zkgroup.groups.GroupMasterKey
import org.thoughtcrime.securesms.components.settings.app.usernamelinks.UsernameQrCodeColorScheme
import org.thoughtcrime.securesms.database.GroupTable.ShowAsStoryState
import org.thoughtcrime.securesms.database.IdentityTable.VerifiedStatus
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.RecipientTable.RecipientType
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.callLinks
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.distributionLists
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.groups
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.inAppPaymentSubscribers
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.database.model.RecipientRecord
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData
import org.thoughtcrime.securesms.keyvalue.PhoneNumberPrivacyValues
import org.thoughtcrime.securesms.recipients.Recipient
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.api.storage.SignalCallLinkRecord
import org.whispersystems.signalservice.api.storage.SignalContactRecord
import org.whispersystems.signalservice.api.storage.SignalGroupV1Record
import org.whispersystems.signalservice.api.storage.SignalGroupV2Record
import org.whispersystems.signalservice.api.storage.SignalStorageRecord
import org.whispersystems.signalservice.api.storage.SignalStoryDistributionListRecord
import org.whispersystems.signalservice.api.subscriptions.SubscriberId
import org.whispersystems.signalservice.api.util.UuidUtil
import org.whispersystems.signalservice.internal.storage.protos.AccountRecord
import org.whispersystems.signalservice.internal.storage.protos.ContactRecord.IdentityState
import org.whispersystems.signalservice.internal.storage.protos.GroupV2Record
import java.util.Currency
import kotlin.math.max

object StorageSyncModels {

  @JvmStatic
  fun localToRemoteRecord(settings: RecipientRecord): SignalStorageRecord {
    if (settings.storageId == null) {
      throw AssertionError("Must have a storage key!")
    }

    return localToRemoteRecord(settings, settings.storageId)
  }

  @JvmStatic
  fun localToRemoteRecord(settings: RecipientRecord, groupMasterKey: GroupMasterKey): SignalStorageRecord {
    if (settings.storageId == null) {
      throw AssertionError("Must have a storage key!")
    }

    return SignalStorageRecord.forGroupV2(localToRemoteGroupV2(settings, settings.storageId, groupMasterKey))
  }

  @JvmStatic
  fun localToRemoteRecord(settings: RecipientRecord, rawStorageId: ByteArray): SignalStorageRecord {
    return when (settings.recipientType) {
      RecipientType.INDIVIDUAL -> SignalStorageRecord.forContact(localToRemoteContact(settings, rawStorageId))
      RecipientType.GV1 -> SignalStorageRecord.forGroupV1(localToRemoteGroupV1(settings, rawStorageId))
      RecipientType.GV2 -> SignalStorageRecord.forGroupV2(localToRemoteGroupV2(settings, rawStorageId, settings.syncExtras.groupMasterKey!!))
      RecipientType.DISTRIBUTION_LIST -> SignalStorageRecord.forStoryDistributionList(localToRemoteStoryDistributionList(settings, rawStorageId))
      RecipientType.CALL_LINK -> SignalStorageRecord.forCallLink(localToRemoteCallLink(settings, rawStorageId))
      else -> throw AssertionError("Unsupported type!")
    }
  }

  @JvmStatic
  fun localToRemotePhoneNumberSharingMode(phoneNumberPhoneNumberSharingMode: PhoneNumberPrivacyValues.PhoneNumberSharingMode): AccountRecord.PhoneNumberSharingMode {
    return when (phoneNumberPhoneNumberSharingMode) {
      PhoneNumberPrivacyValues.PhoneNumberSharingMode.DEFAULT -> AccountRecord.PhoneNumberSharingMode.NOBODY
      PhoneNumberPrivacyValues.PhoneNumberSharingMode.EVERYBODY -> AccountRecord.PhoneNumberSharingMode.EVERYBODY
      PhoneNumberPrivacyValues.PhoneNumberSharingMode.NOBODY -> AccountRecord.PhoneNumberSharingMode.NOBODY
    }
  }

  @JvmStatic
  fun remoteToLocalPhoneNumberSharingMode(phoneNumberPhoneNumberSharingMode: AccountRecord.PhoneNumberSharingMode): PhoneNumberPrivacyValues.PhoneNumberSharingMode {
    return when (phoneNumberPhoneNumberSharingMode) {
      AccountRecord.PhoneNumberSharingMode.EVERYBODY -> PhoneNumberPrivacyValues.PhoneNumberSharingMode.EVERYBODY
      AccountRecord.PhoneNumberSharingMode.NOBODY -> PhoneNumberPrivacyValues.PhoneNumberSharingMode.NOBODY
      else -> PhoneNumberPrivacyValues.PhoneNumberSharingMode.DEFAULT
    }
  }

  @JvmStatic
  fun localToRemotePinnedConversations(records: List<RecipientRecord>): List<AccountRecord.PinnedConversation> {
    return records
      .filter { it.recipientType == RecipientType.GV1 || it.recipientType == RecipientType.GV2 || it.registered == RecipientTable.RegisteredState.REGISTERED }
      .map { localToRemotePinnedConversation(it) }
  }

  @JvmStatic
  private fun localToRemotePinnedConversation(settings: RecipientRecord): AccountRecord.PinnedConversation {
    return when (settings.recipientType) {
      RecipientType.INDIVIDUAL -> {
        AccountRecord.PinnedConversation(
          contact = AccountRecord.PinnedConversation.Contact(
            serviceId = settings.serviceId?.toString() ?: "",
            e164 = settings.e164 ?: ""
          )
        )
      }
      RecipientType.GV1 -> {
        AccountRecord.PinnedConversation(
          legacyGroupId = settings.groupId!!.requireV1().decodedId.toByteString()
        )
      }
      RecipientType.GV2 -> {
        AccountRecord.PinnedConversation(
          groupMasterKey = settings.syncExtras.groupMasterKey!!.serialize().toByteString()
        )
      }
      else -> throw AssertionError("Unexpected group type!")
    }
  }

  @JvmStatic
  fun localToRemoteUsernameColor(local: UsernameQrCodeColorScheme): AccountRecord.UsernameLink.Color {
    return when (local) {
      UsernameQrCodeColorScheme.Blue -> AccountRecord.UsernameLink.Color.BLUE
      UsernameQrCodeColorScheme.White -> AccountRecord.UsernameLink.Color.WHITE
      UsernameQrCodeColorScheme.Grey -> AccountRecord.UsernameLink.Color.GREY
      UsernameQrCodeColorScheme.Tan -> AccountRecord.UsernameLink.Color.OLIVE
      UsernameQrCodeColorScheme.Green -> AccountRecord.UsernameLink.Color.GREEN
      UsernameQrCodeColorScheme.Orange -> AccountRecord.UsernameLink.Color.ORANGE
      UsernameQrCodeColorScheme.Pink -> AccountRecord.UsernameLink.Color.PINK
      UsernameQrCodeColorScheme.Purple -> AccountRecord.UsernameLink.Color.PURPLE
    }
  }

  @JvmStatic
  fun remoteToLocalUsernameColor(remote: AccountRecord.UsernameLink.Color): UsernameQrCodeColorScheme {
    return when (remote) {
      AccountRecord.UsernameLink.Color.BLUE -> UsernameQrCodeColorScheme.Blue
      AccountRecord.UsernameLink.Color.WHITE -> UsernameQrCodeColorScheme.White
      AccountRecord.UsernameLink.Color.GREY -> UsernameQrCodeColorScheme.Grey
      AccountRecord.UsernameLink.Color.OLIVE -> UsernameQrCodeColorScheme.Tan
      AccountRecord.UsernameLink.Color.GREEN -> UsernameQrCodeColorScheme.Green
      AccountRecord.UsernameLink.Color.ORANGE -> UsernameQrCodeColorScheme.Orange
      AccountRecord.UsernameLink.Color.PINK -> UsernameQrCodeColorScheme.Pink
      AccountRecord.UsernameLink.Color.PURPLE -> UsernameQrCodeColorScheme.Purple
      else -> UsernameQrCodeColorScheme.Blue
    }
  }

  private fun localToRemoteContact(recipient: RecipientRecord, rawStorageId: ByteArray): SignalContactRecord {
    if (recipient.aci == null && recipient.pni == null && recipient.e164 == null) {
      throw AssertionError("Must have either a UUID or a phone number!")
    }

    val hideStory = recipient.extras != null && recipient.extras.hideStory()

    return SignalContactRecord.Builder(rawStorageId, recipient.aci, recipient.syncExtras.storageProto)
      .setE164(recipient.e164)
      .setPni(recipient.pni)
      .setProfileKey(recipient.profileKey)
      .setProfileGivenName(recipient.signalProfileName.givenName)
      .setProfileFamilyName(recipient.signalProfileName.familyName)
      .setSystemGivenName(recipient.systemProfileName.givenName)
      .setSystemFamilyName(recipient.systemProfileName.familyName)
      .setSystemNickname(recipient.syncExtras.systemNickname)
      .setBlocked(recipient.isBlocked)
      .setProfileSharingEnabled(recipient.profileSharing || recipient.systemContactUri != null)
      .setIdentityKey(recipient.syncExtras.identityKey)
      .setIdentityState(localToRemoteIdentityState(recipient.syncExtras.identityStatus))
      .setArchived(recipient.syncExtras.isArchived)
      .setForcedUnread(recipient.syncExtras.isForcedUnread)
      .setMuteUntil(recipient.muteUntil)
      .setHideStory(hideStory)
      .setUnregisteredTimestamp(recipient.syncExtras.unregisteredTimestamp)
      .setHidden(recipient.hiddenState != Recipient.HiddenState.NOT_HIDDEN)
      .setUsername(recipient.username)
      .setPniSignatureVerified(recipient.syncExtras.pniSignatureVerified)
      .setNicknameGivenName(recipient.nickname.givenName)
      .setNicknameFamilyName(recipient.nickname.familyName)
      .setNote(recipient.note)
      .build()
  }

  private fun localToRemoteGroupV1(recipient: RecipientRecord, rawStorageId: ByteArray): SignalGroupV1Record {
    val groupId = recipient.groupId ?: throw AssertionError("Must have a groupId!")

    if (!groupId.isV1) {
      throw AssertionError("Group is not V1")
    }

    return SignalGroupV1Record.Builder(rawStorageId, groupId.decodedId, recipient.syncExtras.storageProto)
      .setBlocked(recipient.isBlocked)
      .setProfileSharingEnabled(recipient.profileSharing)
      .setArchived(recipient.syncExtras.isArchived)
      .setForcedUnread(recipient.syncExtras.isForcedUnread)
      .setMuteUntil(recipient.muteUntil)
      .build()
  }

  private fun localToRemoteGroupV2(recipient: RecipientRecord, rawStorageId: ByteArray?, groupMasterKey: GroupMasterKey): SignalGroupV2Record {
    val groupId = recipient.groupId ?: throw AssertionError("Must have a groupId!")

    if (!groupId.isV2) {
      throw AssertionError("Group is not V2")
    }

    if (groupMasterKey == null) {
      throw AssertionError("Group master key not on recipient record")
    }

    val hideStory = recipient.extras != null && recipient.extras.hideStory()
    val showAsStoryState = groups.getShowAsStoryState(groupId)

    val storySendMode = when (showAsStoryState) {
      ShowAsStoryState.ALWAYS -> GroupV2Record.StorySendMode.ENABLED
      ShowAsStoryState.NEVER -> GroupV2Record.StorySendMode.DISABLED
      else -> GroupV2Record.StorySendMode.DEFAULT
    }

    return SignalGroupV2Record.Builder(rawStorageId, groupMasterKey, recipient.syncExtras.storageProto)
      .setBlocked(recipient.isBlocked)
      .setProfileSharingEnabled(recipient.profileSharing)
      .setArchived(recipient.syncExtras.isArchived)
      .setForcedUnread(recipient.syncExtras.isForcedUnread)
      .setMuteUntil(recipient.muteUntil)
      .setNotifyForMentionsWhenMuted(recipient.mentionSetting == RecipientTable.MentionSetting.ALWAYS_NOTIFY)
      .setHideStory(hideStory)
      .setStorySendMode(storySendMode)
      .build()
  }

  private fun localToRemoteCallLink(recipient: RecipientRecord, rawStorageId: ByteArray): SignalCallLinkRecord {
    val callLinkRoomId = recipient.callLinkRoomId ?: throw AssertionError("Must have a callLinkRoomId!")

    val callLink = callLinks.getCallLinkByRoomId(callLinkRoomId) ?: throw AssertionError("Must have a call link record!")

    if (callLink.credentials == null) {
      throw AssertionError("Must have call link credentials!")
    }

    val deletedTimestamp = max(0.0, callLinks.getDeletedTimestampByRoomId(callLinkRoomId).toDouble()).toLong()
    val adminPassword = if (deletedTimestamp > 0) byteArrayOf() else callLink.credentials.adminPassBytes!!

    return SignalCallLinkRecord.Builder(rawStorageId, null)
      .setRootKey(callLink.credentials.linkKeyBytes)
      .setAdminPassKey(adminPassword)
      .setDeletedTimestamp(deletedTimestamp)
      .build()
  }

  private fun localToRemoteStoryDistributionList(recipient: RecipientRecord, rawStorageId: ByteArray): SignalStoryDistributionListRecord {
    val distributionListId = recipient.distributionListId ?: throw AssertionError("Must have a distributionListId!")

    val record = distributionLists.getListForStorageSync(distributionListId) ?: throw AssertionError("Must have a distribution list record!")

    if (record.deletedAtTimestamp > 0L) {
      return SignalStoryDistributionListRecord.Builder(rawStorageId, recipient.syncExtras.storageProto)
        .setIdentifier(UuidUtil.toByteArray(record.distributionId.asUuid()))
        .setDeletedAtTimestamp(record.deletedAtTimestamp)
        .build()
    }

    return SignalStoryDistributionListRecord.Builder(rawStorageId, recipient.syncExtras.storageProto)
      .setIdentifier(UuidUtil.toByteArray(record.distributionId.asUuid()))
      .setName(record.name)
      .setRecipients(
        record.getMembersToSync()
          .map { Recipient.resolved(it) }
          .filter { it.hasServiceId }
          .map { it.requireServiceId() }
          .map { SignalServiceAddress(it) }
      )
      .setAllowsReplies(record.allowsReplies)
      .setIsBlockList(record.privacyMode.isBlockList)
      .build()
  }

  fun remoteToLocalIdentityStatus(identityState: IdentityState): VerifiedStatus {
    return when (identityState) {
      IdentityState.VERIFIED -> VerifiedStatus.VERIFIED
      IdentityState.UNVERIFIED -> VerifiedStatus.UNVERIFIED
      else -> VerifiedStatus.DEFAULT
    }
  }

  private fun localToRemoteIdentityState(local: VerifiedStatus): IdentityState {
    return when (local) {
      VerifiedStatus.VERIFIED -> IdentityState.VERIFIED
      VerifiedStatus.UNVERIFIED -> IdentityState.UNVERIFIED
      else -> IdentityState.DEFAULT
    }
  }

  fun remoteToLocalSubscriber(
    subscriberId: ByteString,
    subscriberCurrencyCode: String,
    type: InAppPaymentSubscriberRecord.Type
  ): InAppPaymentSubscriberRecord? {
    if (subscriberId.isNotEmpty()) {
      val subscriberId = SubscriberId.fromBytes(subscriberId.toByteArray())
      val localSubscriberRecord = inAppPaymentSubscribers.getBySubscriberId(subscriberId)
      val requiresCancel = localSubscriberRecord != null && localSubscriberRecord.requiresCancel
      val paymentMethodType = localSubscriberRecord?.paymentMethodType ?: InAppPaymentData.PaymentMethodType.UNKNOWN

      val currency: Currency
      if (subscriberCurrencyCode.isBlank()) {
        return null
      } else {
        try {
          currency = Currency.getInstance(subscriberCurrencyCode)
        } catch (e: IllegalArgumentException) {
          return null
        }
      }

      return InAppPaymentSubscriberRecord(subscriberId, currency, type, requiresCancel, paymentMethodType)
    } else {
      return null
    }
  }
}
