package org.thoughtcrime.securesms.database

import android.net.Uri
import org.signal.core.util.Bitmask
import org.signal.libsignal.zkgroup.profiles.ExpiringProfileKeyCredential
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.conversation.colors.AvatarColor
import org.thoughtcrime.securesms.conversation.colors.ChatColors
import org.thoughtcrime.securesms.database.model.ProfileAvatarFileDetails
import org.thoughtcrime.securesms.database.model.RecipientRecord
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientDetails
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.wallpaper.ChatWallpaper
import org.whispersystems.signalservice.api.push.ServiceId
import java.util.Optional
import java.util.UUID
import kotlin.random.Random

/**
 * Test utilities to create recipients in different states.
 */
object RecipientDatabaseTestUtils {

  fun createRecipient(
    resolved: Boolean = false,
    groupName: String? = null,
    groupAvatarId: Optional<Long> = Optional.empty(),
    systemContact: Boolean = false,
    isSelf: Boolean = false,
    participants: List<RecipientId> = listOf(),
    recipientId: RecipientId = RecipientId.from(Random.nextLong()),
    serviceId: ServiceId? = ServiceId.from(UUID.randomUUID()),
    username: String? = null,
    e164: String? = null,
    email: String? = null,
    groupId: GroupId? = null,
    groupType: RecipientTable.GroupType = RecipientTable.GroupType.NONE,
    blocked: Boolean = false,
    muteUntil: Long = -1,
    messageVibrateState: RecipientTable.VibrateState = RecipientTable.VibrateState.DEFAULT,
    callVibrateState: RecipientTable.VibrateState = RecipientTable.VibrateState.DEFAULT,
    messageRingtone: Uri = Uri.EMPTY,
    callRingtone: Uri = Uri.EMPTY,
    defaultSubscriptionId: Int = 0,
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
    unidentifiedAccessMode: RecipientTable.UnidentifiedAccessMode = RecipientTable.UnidentifiedAccessMode.UNKNOWN,
    forceSmsSelection: Boolean = false,
    capabilities: Long = 0L,
    insightBannerTier: RecipientTable.InsightsBannerTier = RecipientTable.InsightsBannerTier.NO_TIER,
    storageId: ByteArray? = null,
    mentionSetting: RecipientTable.MentionSetting = RecipientTable.MentionSetting.ALWAYS_NOTIFY,
    wallpaper: ChatWallpaper? = null,
    chatColors: ChatColors? = null,
    avatarColor: AvatarColor = AvatarColor.A100,
    about: String? = null,
    aboutEmoji: String? = null,
    syncExtras: RecipientRecord.SyncExtras = RecipientRecord.SyncExtras(
      null,
      null,
      null,
      IdentityTable.VerifiedStatus.DEFAULT,
      false,
      false,
      0,
      null
    ),
    extras: Recipient.Extras? = null,
    hasGroupsInCommon: Boolean = false,
    badges: List<Badge> = emptyList(),
    isReleaseChannel: Boolean = false
  ): Recipient = Recipient(
    recipientId,
    RecipientDetails(
      groupName,
      systemDisplayName,
      groupAvatarId,
      systemContact,
      isSelf,
      registered,
      RecipientRecord(
        recipientId,
        serviceId,
        null,
        username,
        e164,
        email,
        groupId,
        null,
        groupType,
        blocked,
        muteUntil,
        messageVibrateState,
        callVibrateState,
        messageRingtone,
        callRingtone,
        defaultSubscriptionId,
        expireMessages,
        registered,
        profileKey,
        expiringProfileKeyCredential,
        systemProfileName,
        systemDisplayName,
        systemContactPhoto,
        systemPhoneLabel,
        systemContactUri,
        signalProfileName,
        signalProfileAvatar,
        profileAvatarFileDetails,
        profileSharing,
        lastProfileFetch,
        notificationChannel,
        unidentifiedAccessMode,
        forceSmsSelection,
        RecipientRecord.Capabilities(
          capabilities,
          Recipient.Capability.deserialize(Bitmask.read(capabilities, RecipientTable.Capabilities.GROUPS_V1_MIGRATION, RecipientTable.Capabilities.BIT_LENGTH).toInt()),
          Recipient.Capability.deserialize(Bitmask.read(capabilities, RecipientTable.Capabilities.SENDER_KEY, RecipientTable.Capabilities.BIT_LENGTH).toInt()),
          Recipient.Capability.deserialize(Bitmask.read(capabilities, RecipientTable.Capabilities.ANNOUNCEMENT_GROUPS, RecipientTable.Capabilities.BIT_LENGTH).toInt()),
          Recipient.Capability.deserialize(Bitmask.read(capabilities, RecipientTable.Capabilities.CHANGE_NUMBER, RecipientTable.Capabilities.BIT_LENGTH).toInt()),
          Recipient.Capability.deserialize(Bitmask.read(capabilities, RecipientTable.Capabilities.STORIES, RecipientTable.Capabilities.BIT_LENGTH).toInt()),
          Recipient.Capability.deserialize(Bitmask.read(capabilities, RecipientTable.Capabilities.GIFT_BADGES, RecipientTable.Capabilities.BIT_LENGTH).toInt()),
          Recipient.Capability.deserialize(Bitmask.read(capabilities, RecipientTable.Capabilities.PNP, RecipientTable.Capabilities.BIT_LENGTH).toInt()),
          Recipient.Capability.deserialize(Bitmask.read(capabilities, RecipientTable.Capabilities.PAYMENT_ACTIVATION, RecipientTable.Capabilities.BIT_LENGTH).toInt())
        ),
        insightBannerTier,
        storageId,
        mentionSetting,
        wallpaper,
        chatColors,
        avatarColor,
        about,
        aboutEmoji,
        syncExtras,
        extras,
        hasGroupsInCommon,
        badges,
        needsPniSignature = false,
        isHidden = false
      ),
      participants,
      isReleaseChannel
    ),
    resolved
  )
}
