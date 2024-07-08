package org.thoughtcrime.securesms.database

import android.net.Uri
import org.signal.core.util.Bitmask
import org.signal.core.util.toOptional
import org.signal.libsignal.zkgroup.profiles.ExpiringProfileKeyCredential
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.conversation.colors.AvatarColor
import org.thoughtcrime.securesms.conversation.colors.ChatColors
import org.thoughtcrime.securesms.database.model.GroupRecord
import org.thoughtcrime.securesms.database.model.ProfileAvatarFileDetails
import org.thoughtcrime.securesms.database.model.RecipientRecord
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientCreator
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.wallpaper.ChatWallpaper
import org.whispersystems.signalservice.api.push.ServiceId.ACI
import java.util.UUID
import kotlin.random.Random

/**
 * Test utilities to create recipients in different states.
 */
object RecipientDatabaseTestUtils {

  fun createRecipient(
    resolved: Boolean = false,
    groupName: String? = null,
    isSelf: Boolean = false,
    participants: List<RecipientId> = listOf(),
    recipientId: RecipientId = RecipientId.from(Random.nextLong()),
    serviceId: ACI? = ACI.from(UUID.randomUUID()),
    username: String? = null,
    e164: String? = null,
    email: String? = null,
    groupId: GroupId? = null,
    groupType: RecipientTable.RecipientType = RecipientTable.RecipientType.INDIVIDUAL,
    blocked: Boolean = false,
    muteUntil: Long = -1,
    messageVibrateState: RecipientTable.VibrateState = RecipientTable.VibrateState.DEFAULT,
    callVibrateState: RecipientTable.VibrateState = RecipientTable.VibrateState.DEFAULT,
    messageRingtone: Uri = Uri.EMPTY,
    callRingtone: Uri = Uri.EMPTY,
    expireMessages: Int = 0,
    registered: RecipientTable.RegisteredState = RecipientTable.RegisteredState.REGISTERED,
    profileKey: ByteArray = Random.nextBytes(32),
    expiringProfileKeyCredential: ExpiringProfileKeyCredential? = null,
    systemProfileName: ProfileName = ProfileName.EMPTY,
    systemDisplayName: String? = null,
    systemContactPhoto: String? = null,
    systemPhoneLabel: String? = null,
    systemContactUri: String? = null,
    signalProfileName: ProfileName = ProfileName.EMPTY,
    signalProfileAvatar: String? = null,
    profileAvatarFileDetails: ProfileAvatarFileDetails = ProfileAvatarFileDetails.NO_DETAILS,
    profileSharing: Boolean = false,
    lastProfileFetch: Long = 0L,
    notificationChannel: String? = null,
    sealedSenderAccessMode: RecipientTable.SealedSenderAccessMode = RecipientTable.SealedSenderAccessMode.UNKNOWN,
    capabilities: Long = 0L,
    storageId: ByteArray? = null,
    mentionSetting: RecipientTable.MentionSetting = RecipientTable.MentionSetting.ALWAYS_NOTIFY,
    wallpaper: ChatWallpaper? = null,
    chatColors: ChatColors? = null,
    avatarColor: AvatarColor = AvatarColor.A100,
    about: String? = null,
    aboutEmoji: String? = null,
    syncExtras: RecipientRecord.SyncExtras = RecipientRecord.SyncExtras(
      storageProto = null,
      groupMasterKey = null,
      identityKey = null,
      identityStatus = IdentityTable.VerifiedStatus.DEFAULT,
      isArchived = false,
      isForcedUnread = false,
      unregisteredTimestamp = 0,
      systemNickname = null,
      pniSignatureVerified = false
    ),
    extras: Recipient.Extras? = null,
    hasGroupsInCommon: Boolean = false,
    badges: List<Badge> = emptyList(),
    isReleaseChannel: Boolean = false,
    isActive: Boolean = true,
    groupRecord: GroupRecord? = null
  ): Recipient = RecipientCreator.create(
    resolved = resolved,
    groupName = groupName,
    systemContactName = systemDisplayName,
    isSelf = isSelf,
    registeredState = registered,
    record = RecipientRecord(
      id = recipientId,
      aci = serviceId,
      pni = null,
      username = username,
      e164 = e164,
      email = email,
      groupId = groupId,
      distributionListId = null,
      recipientType = groupType,
      isBlocked = blocked,
      muteUntil = muteUntil,
      messageVibrateState = messageVibrateState,
      callVibrateState = callVibrateState,
      messageRingtone = messageRingtone,
      callRingtone = callRingtone,
      expireMessages = expireMessages,
      registered = registered,
      profileKey = profileKey,
      expiringProfileKeyCredential = expiringProfileKeyCredential,
      systemProfileName = systemProfileName,
      systemDisplayName = systemDisplayName,
      systemContactPhotoUri = systemContactPhoto,
      systemPhoneLabel = systemPhoneLabel,
      systemContactUri = systemContactUri,
      signalProfileName = signalProfileName,
      signalProfileAvatar = signalProfileAvatar,
      profileAvatarFileDetails = profileAvatarFileDetails,
      profileSharing = profileSharing,
      lastProfileFetch = lastProfileFetch,
      notificationChannel = notificationChannel,
      sealedSenderAccessMode = sealedSenderAccessMode,
      capabilities = RecipientRecord.Capabilities(
        rawBits = capabilities,
        deleteSync = Recipient.Capability.deserialize(Bitmask.read(capabilities, RecipientTable.Capabilities.DELETE_SYNC, RecipientTable.Capabilities.BIT_LENGTH).toInt())
      ),
      storageId = storageId,
      mentionSetting = mentionSetting,
      wallpaper = wallpaper,
      chatColors = chatColors,
      avatarColor = avatarColor,
      about = about,
      aboutEmoji = aboutEmoji,
      syncExtras = syncExtras,
      extras = extras,
      hasGroupsInCommon = hasGroupsInCommon,
      badges = badges,
      needsPniSignature = false,
      hiddenState = Recipient.HiddenState.NOT_HIDDEN,
      callLinkRoomId = null,
      phoneNumberSharing = RecipientTable.PhoneNumberSharingState.UNKNOWN,
      nickname = ProfileName.EMPTY,
      note = null
    ),
    participantIds = participants,
    isReleaseChannel = isReleaseChannel,
    avatarColor = null,
    groupRecord = groupRecord.toOptional()
  )
}
