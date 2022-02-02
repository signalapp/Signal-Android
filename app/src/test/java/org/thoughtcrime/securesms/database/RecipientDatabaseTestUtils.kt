package org.thoughtcrime.securesms.database

import android.net.Uri
import org.signal.zkgroup.profiles.ProfileKeyCredential
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.conversation.colors.AvatarColor
import org.thoughtcrime.securesms.conversation.colors.ChatColors
import org.thoughtcrime.securesms.database.model.RecipientRecord
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientDetails
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.Bitmask
import org.thoughtcrime.securesms.wallpaper.ChatWallpaper
import org.whispersystems.libsignal.util.guava.Optional
import org.whispersystems.signalservice.api.push.ACI
import java.util.UUID
import kotlin.random.Random

/**
 * Test utilities to create recipients in different states.
 */
object RecipientDatabaseTestUtils {

  fun createRecipient(
    resolved: Boolean = false,
    groupName: String? = null,
    groupAvatarId: Optional<Long> = Optional.absent(),
    systemContact: Boolean = false,
    isSelf: Boolean = false,
    participants: List<Recipient> = listOf(),
    recipientId: RecipientId = RecipientId.from(Random.nextLong()),
    aci: ACI? = ACI.from(UUID.randomUUID()),
    username: String? = null,
    e164: String? = null,
    email: String? = null,
    groupId: GroupId? = null,
    groupType: RecipientDatabase.GroupType = RecipientDatabase.GroupType.NONE,
    blocked: Boolean = false,
    muteUntil: Long = -1,
    messageVibrateState: RecipientDatabase.VibrateState = RecipientDatabase.VibrateState.DEFAULT,
    callVibrateState: RecipientDatabase.VibrateState = RecipientDatabase.VibrateState.DEFAULT,
    messageRingtone: Uri = Uri.EMPTY,
    callRingtone: Uri = Uri.EMPTY,
    defaultSubscriptionId: Int = 0,
    expireMessages: Int = 0,
    registered: RecipientDatabase.RegisteredState = RecipientDatabase.RegisteredState.REGISTERED,
    profileKey: ByteArray = Random.nextBytes(32),
    profileKeyCredential: ProfileKeyCredential? = null,
    systemProfileName: ProfileName = ProfileName.EMPTY,
    systemDisplayName: String? = null,
    systemContactPhoto: String? = null,
    systemPhoneLabel: String? = null,
    systemContactUri: String? = null,
    signalProfileName: ProfileName = ProfileName.EMPTY,
    signalProfileAvatar: String? = null,
    hasProfileImage: Boolean = false,
    profileSharing: Boolean = false,
    lastProfileFetch: Long = 0L,
    notificationChannel: String? = null,
    unidentifiedAccessMode: RecipientDatabase.UnidentifiedAccessMode = RecipientDatabase.UnidentifiedAccessMode.UNKNOWN,
    forceSmsSelection: Boolean = false,
    capabilities: Long = 0L,
    insightBannerTier: RecipientDatabase.InsightsBannerTier = RecipientDatabase.InsightsBannerTier.NO_TIER,
    storageId: ByteArray? = null,
    mentionSetting: RecipientDatabase.MentionSetting = RecipientDatabase.MentionSetting.ALWAYS_NOTIFY,
    wallpaper: ChatWallpaper? = null,
    chatColors: ChatColors? = null,
    avatarColor: AvatarColor = AvatarColor.A100,
    about: String? = null,
    aboutEmoji: String? = null,
    syncExtras: RecipientRecord.SyncExtras = RecipientRecord.SyncExtras(
      null,
      null,
      null,
      IdentityDatabase.VerifiedStatus.DEFAULT,
      false,
      false
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
        aci,
        null,
        username,
        e164,
        email,
        groupId,
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
        profileKeyCredential,
        systemProfileName,
        systemDisplayName,
        systemContactPhoto,
        systemPhoneLabel,
        systemContactUri,
        signalProfileName,
        signalProfileAvatar,
        hasProfileImage,
        profileSharing,
        lastProfileFetch,
        notificationChannel,
        unidentifiedAccessMode,
        forceSmsSelection,
        capabilities,
        Recipient.Capability.deserialize(Bitmask.read(capabilities, RecipientDatabase.Capabilities.GROUPS_V2, RecipientDatabase.Capabilities.BIT_LENGTH).toInt()),
        Recipient.Capability.deserialize(Bitmask.read(capabilities, RecipientDatabase.Capabilities.GROUPS_V1_MIGRATION, RecipientDatabase.Capabilities.BIT_LENGTH).toInt()),
        Recipient.Capability.deserialize(Bitmask.read(capabilities, RecipientDatabase.Capabilities.SENDER_KEY, RecipientDatabase.Capabilities.BIT_LENGTH).toInt()),
        Recipient.Capability.deserialize(Bitmask.read(capabilities, RecipientDatabase.Capabilities.ANNOUNCEMENT_GROUPS, RecipientDatabase.Capabilities.BIT_LENGTH).toInt()),
        Recipient.Capability.deserialize(Bitmask.read(capabilities, RecipientDatabase.Capabilities.CHANGE_NUMBER, RecipientDatabase.Capabilities.BIT_LENGTH).toInt()),
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
        badges
      ),
      participants,
      isReleaseChannel
    ),
    resolved
  )
}
