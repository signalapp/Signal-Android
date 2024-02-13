package org.thoughtcrime.securesms.recipients

import android.content.Context
import android.net.Uri
import androidx.annotation.VisibleForTesting
import org.signal.libsignal.zkgroup.profiles.ExpiringProfileKeyCredential
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.conversation.colors.AvatarColor
import org.thoughtcrime.securesms.conversation.colors.ChatColors
import org.thoughtcrime.securesms.database.RecipientTable.MentionSetting
import org.thoughtcrime.securesms.database.RecipientTable.PhoneNumberSharingState
import org.thoughtcrime.securesms.database.RecipientTable.RegisteredState
import org.thoughtcrime.securesms.database.RecipientTable.UnidentifiedAccessMode
import org.thoughtcrime.securesms.database.RecipientTable.VibrateState
import org.thoughtcrime.securesms.database.model.DistributionListId
import org.thoughtcrime.securesms.database.model.GroupRecord
import org.thoughtcrime.securesms.database.model.ProfileAvatarFileDetails
import org.thoughtcrime.securesms.database.model.RecipientRecord
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.recipients.Recipient.HiddenState
import org.thoughtcrime.securesms.service.webrtc.links.CallLinkRoomId
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.Util
import org.thoughtcrime.securesms.wallpaper.ChatWallpaper
import org.whispersystems.signalservice.api.push.ServiceId.ACI
import org.whispersystems.signalservice.api.push.ServiceId.PNI
import java.util.LinkedList
import java.util.Optional

class RecipientDetails private constructor(
  @JvmField val aci: ACI?,
  @JvmField val pni: PNI?,
  @JvmField val username: String?,
  @JvmField val e164: String?,
  @JvmField val email: String?,
  @JvmField val groupId: GroupId?,
  @JvmField val distributionListId: DistributionListId?,
  /** Used for groups, dlists, and call links */
  @JvmField val groupName: String?,
  @JvmField val systemContactName: String?,
  @JvmField val customLabel: String?,
  @JvmField val systemContactPhoto: Uri?,
  @JvmField val contactUri: Uri?,
  @JvmField val groupAvatarId: Optional<Long>,
  @JvmField val messageRingtone: Uri?,
  @JvmField val callRingtone: Uri?,
  @JvmField val mutedUntil: Long,
  @JvmField val messageVibrateState: VibrateState,
  @JvmField val callVibrateState: VibrateState,
  @JvmField val blocked: Boolean,
  @JvmField val expireMessages: Int,
  @JvmField val participantIds: List<RecipientId>,
  @JvmField val profileName: ProfileName,
  @JvmField val registered: RegisteredState,
  @JvmField val profileKey: ByteArray?,
  @JvmField val expiringProfileKeyCredential: ExpiringProfileKeyCredential?,
  @JvmField val profileAvatar: String?,
  @JvmField val profileAvatarFileDetails: ProfileAvatarFileDetails,
  @JvmField val profileSharing: Boolean,
  @JvmField val hiddenState: HiddenState,
  @JvmField val isActiveGroup: Boolean,
  @JvmField val lastProfileFetch: Long,
  @JvmField val isSelf: Boolean,
  @JvmField val notificationChannel: String?,
  @JvmField val unidentifiedAccessMode: UnidentifiedAccessMode,
  @JvmField val capabilities: RecipientRecord.Capabilities,
  @JvmField val storageId: ByteArray?,
  @JvmField val mentionSetting: MentionSetting,
  @JvmField val wallpaper: ChatWallpaper?,
  @JvmField val chatColors: ChatColors?,
  @JvmField val avatarColor: AvatarColor,
  @JvmField val about: String?,
  @JvmField val aboutEmoji: String?,
  @JvmField val systemProfileName: ProfileName,
  @JvmField val extras: Optional<Recipient.Extras>,
  @JvmField val hasGroupsInCommon: Boolean,
  @JvmField val badges: List<Badge>,
  @JvmField val isReleaseChannel: Boolean,
  @JvmField val needsPniSignature: Boolean,
  @JvmField val callLinkRoomId: CallLinkRoomId?,
  @JvmField val groupRecord: Optional<GroupRecord>,
  @JvmField val phoneNumberSharing: PhoneNumberSharingState
) {

  @VisibleForTesting
  constructor(
    groupName: String?,
    systemContactName: String?,
    isSelf: Boolean,
    registeredState: RegisteredState,
    record: RecipientRecord,
    participantIds: List<RecipientId>?,
    isReleaseChannel: Boolean,
    avatarColor: AvatarColor?,
    groupRecord: Optional<GroupRecord>
  ) : this(
    groupAvatarId = groupRecord.map { if (it.hasAvatar()) it.avatarId else null },
    systemContactPhoto = Util.uri(record.systemContactPhotoUri),
    customLabel = record.systemPhoneLabel,
    contactUri = Util.uri(record.systemContactUri),
    aci = record.aci,
    pni = record.pni,
    username = record.username,
    e164 = record.e164,
    email = record.email,
    groupId = record.groupId,
    distributionListId = record.distributionListId,
    messageRingtone = record.messageRingtone,
    callRingtone = record.callRingtone,
    mutedUntil = record.muteUntil,
    messageVibrateState = record.messageVibrateState,
    callVibrateState = record.callVibrateState,
    blocked = record.isBlocked,
    expireMessages = record.expireMessages,
    participantIds = participantIds ?: LinkedList(),
    isActiveGroup = groupRecord.map { it.isActive }.orElse(false),
    profileName = record.signalProfileName,
    registered = registeredState,
    profileKey = record.profileKey,
    expiringProfileKeyCredential = record.expiringProfileKeyCredential,
    profileAvatar = record.signalProfileAvatar,
    profileAvatarFileDetails = record.profileAvatarFileDetails,
    profileSharing = record.profileSharing,
    hiddenState = record.hiddenState,
    lastProfileFetch = record.lastProfileFetch,
    isSelf = isSelf,
    notificationChannel = record.notificationChannel,
    unidentifiedAccessMode = record.unidentifiedAccessMode,
    capabilities = record.capabilities,
    storageId = record.storageId,
    mentionSetting = record.mentionSetting,
    wallpaper = record.wallpaper,
    chatColors = record.chatColors,
    avatarColor = avatarColor ?: record.avatarColor,
    about = record.about,
    aboutEmoji = record.aboutEmoji,
    systemProfileName = record.systemProfileName,
    groupName = groupName,
    systemContactName = systemContactName,
    extras = Optional.ofNullable(record.extras),
    hasGroupsInCommon = record.hasGroupsInCommon,
    badges = record.badges,
    isReleaseChannel = isReleaseChannel,
    needsPniSignature = record.needsPniSignature,
    callLinkRoomId = record.callLinkRoomId,
    groupRecord = groupRecord,
    phoneNumberSharing = record.phoneNumberSharing
  )

  companion object {
    @JvmStatic
    fun forIndividual(context: Context, record: RecipientRecord): RecipientDetails {
      val isSelf = record.e164 != null && record.e164 == SignalStore.account().e164 || record.aci != null && record.aci == SignalStore.account().aci
      val isReleaseChannel = record.id == SignalStore.releaseChannelValues().releaseChannelRecipientId
      var registeredState = record.registered

      if (isSelf) {
        registeredState = if (SignalStore.account().isRegistered && !TextSecurePreferences.isUnauthorizedReceived(context)) {
          RegisteredState.REGISTERED
        } else {
          RegisteredState.NOT_REGISTERED
        }
      }

      return RecipientDetails(
        groupName = null,
        systemContactName = record.systemDisplayName,
        isSelf = isSelf,
        registeredState = registeredState,
        record = record,
        participantIds = null,
        isReleaseChannel = isReleaseChannel,
        avatarColor = null,
        groupRecord = Optional.empty()
      )
    }

    @JvmStatic
    fun forGroup(groupRecord: GroupRecord, recipientRecord: RecipientRecord): RecipientDetails {
      return RecipientDetails(
        groupName = groupRecord.title,
        systemContactName = null,
        isSelf = false,
        registeredState = recipientRecord.registered,
        record = recipientRecord,
        participantIds = groupRecord.members,
        isReleaseChannel = false,
        avatarColor = null,
        groupRecord = Optional.of(groupRecord)
      )
    }

    @JvmStatic
    fun forDistributionList(title: String?, members: List<RecipientId>?, record: RecipientRecord): RecipientDetails {
      return RecipientDetails(
        groupName = title,
        systemContactName = null,
        isSelf = false,
        registeredState = record.registered,
        record = record,
        participantIds = members,
        isReleaseChannel = false,
        avatarColor = null,
        groupRecord = Optional.empty()
      )
    }

    @JvmStatic
    fun forCallLink(name: String?, record: RecipientRecord, avatarColor: AvatarColor): RecipientDetails {
      return RecipientDetails(
        groupName = name,
        systemContactName = null,
        isSelf = false,
        registeredState = record.registered,
        record = record,
        participantIds = emptyList(),
        isReleaseChannel = false,
        avatarColor = avatarColor,
        groupRecord = Optional.empty()
      )
    }

    @JvmStatic
    fun forUnknown(): RecipientDetails {
      return RecipientDetails(
        groupAvatarId = Optional.empty(),
        systemContactPhoto = null,
        customLabel = null,
        contactUri = null,
        aci = null,
        pni = null,
        username = null,
        e164 = null,
        email = null,
        groupId = null,
        distributionListId = null,
        messageRingtone = null,
        callRingtone = null,
        mutedUntil = 0,
        messageVibrateState = VibrateState.DEFAULT,
        callVibrateState = VibrateState.DEFAULT,
        blocked = false,
        expireMessages = 0,
        participantIds = LinkedList(),
        profileName = ProfileName.EMPTY,
        registered = RegisteredState.UNKNOWN,
        profileKey = null,
        expiringProfileKeyCredential = null,
        profileAvatar = null,
        profileAvatarFileDetails = ProfileAvatarFileDetails.NO_DETAILS,
        profileSharing = false,
        hiddenState = HiddenState.NOT_HIDDEN,
        lastProfileFetch = 0,
        isSelf = false,
        notificationChannel = null,
        unidentifiedAccessMode = UnidentifiedAccessMode.UNKNOWN,
        groupName = null,
        capabilities = RecipientRecord.Capabilities.UNKNOWN,
        storageId = null,
        mentionSetting = MentionSetting.ALWAYS_NOTIFY,
        wallpaper = null,
        chatColors = null,
        avatarColor = AvatarColor.UNKNOWN,
        about = null,
        aboutEmoji = null,
        systemProfileName = ProfileName.EMPTY,
        systemContactName = null,
        extras = Optional.empty(),
        hasGroupsInCommon = false,
        badges = emptyList(),
        isReleaseChannel = false,
        needsPniSignature = false,
        isActiveGroup = false,
        callLinkRoomId = null,
        groupRecord = Optional.empty(),
        phoneNumberSharing = PhoneNumberSharingState.UNKNOWN
      )
    }
  }
}
