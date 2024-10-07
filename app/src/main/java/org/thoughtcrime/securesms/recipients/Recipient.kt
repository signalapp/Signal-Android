package org.thoughtcrime.securesms.recipients

import android.content.Context
import android.net.Uri
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.collections.immutable.toImmutableList
import org.signal.core.util.StringUtil
import org.signal.core.util.isNotNullOrBlank
import org.signal.core.util.logging.Log
import org.signal.core.util.nullIfBlank
import org.signal.libsignal.zkgroup.profiles.ExpiringProfileKeyCredential
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.avatar.fallback.FallbackAvatar
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.contacts.avatars.ContactPhoto
import org.thoughtcrime.securesms.contacts.avatars.GroupRecordContactPhoto
import org.thoughtcrime.securesms.contacts.avatars.ProfileContactPhoto
import org.thoughtcrime.securesms.contacts.avatars.SystemContactPhoto
import org.thoughtcrime.securesms.conversation.colors.AvatarColor
import org.thoughtcrime.securesms.conversation.colors.ChatColors
import org.thoughtcrime.securesms.conversation.colors.ChatColors.Id.Auto
import org.thoughtcrime.securesms.conversation.colors.ChatColorsPalette
import org.thoughtcrime.securesms.database.RecipientTable.MentionSetting
import org.thoughtcrime.securesms.database.RecipientTable.MissingRecipientException
import org.thoughtcrime.securesms.database.RecipientTable.PhoneNumberSharingState
import org.thoughtcrime.securesms.database.RecipientTable.RegisteredState
import org.thoughtcrime.securesms.database.RecipientTable.SealedSenderAccessMode
import org.thoughtcrime.securesms.database.RecipientTable.VibrateState
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.DistributionListId
import org.thoughtcrime.securesms.database.model.GroupRecord
import org.thoughtcrime.securesms.database.model.ProfileAvatarFileDetails
import org.thoughtcrime.securesms.database.model.RecipientRecord
import org.thoughtcrime.securesms.database.model.databaseprotos.RecipientExtras
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.phonenumbers.NumberUtil
import org.thoughtcrime.securesms.phonenumbers.PhoneNumberFormatter
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.service.webrtc.links.CallLinkRoomId
import org.thoughtcrime.securesms.util.UsernameUtil.isValidUsernameForSearch
import org.thoughtcrime.securesms.util.Util
import org.thoughtcrime.securesms.wallpaper.ChatWallpaper
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.api.push.ServiceId.ACI
import org.whispersystems.signalservice.api.push.ServiceId.PNI
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.api.util.OptionalUtil
import org.whispersystems.signalservice.api.util.UuidUtil
import java.util.LinkedList
import java.util.Objects
import java.util.Optional

/**
 * A recipient represents something you can send messages to, or receive messages from. They could be individuals, groups, or even distribution lists.
 * This class is a snapshot of common state that is used to present recipients through the UI.
 *
 * It's important to note that this is only a snapshot, and the actual state of a recipient can change over time.
 * If you ever need to present a recipient, you should consider observing a [LiveRecipient], which will let you get up-to-date snapshots as the data changes.
 */
class Recipient(
  val id: RecipientId = RecipientId.UNKNOWN,
  val isResolving: Boolean = true,
  private val aciValue: ACI? = null,
  private val pniValue: PNI? = null,
  private val usernameValue: String? = null,
  private val e164Value: String? = null,
  private val emailValue: String? = null,
  private val groupIdValue: GroupId? = null,
  private val distributionListIdValue: DistributionListId? = null,
  private val participantIdsValue: List<RecipientId> = emptyList(),
  private val groupAvatarId: Optional<Long> = Optional.empty(),
  val isActiveGroup: Boolean = false,
  val isSelf: Boolean = false,
  val isBlocked: Boolean = false,
  val muteUntil: Long = 0,
  val messageVibrate: VibrateState = VibrateState.DEFAULT,
  val callVibrate: VibrateState = VibrateState.DEFAULT,
  private val messageRingtoneUri: Uri? = null,
  private val callRingtoneUri: Uri? = null,
  val expiresInSeconds: Int = 0,
  val expireTimerVersion: Int = 1,
  private val registeredValue: RegisteredState = RegisteredState.UNKNOWN,
  val profileKey: ByteArray? = null,
  val expiringProfileKeyCredential: ExpiringProfileKeyCredential? = null,
  private val groupName: String? = null,
  private val systemContactPhoto: Uri? = null,
  private val customLabel: String? = null,
  val contactUri: Uri? = null,
  val profileName: ProfileName = ProfileName.EMPTY,
  val profileAvatar: String? = null,
  val profileAvatarFileDetails: ProfileAvatarFileDetails = ProfileAvatarFileDetails.NO_DETAILS,
  val isProfileSharing: Boolean = false,
  val hiddenState: HiddenState = HiddenState.NOT_HIDDEN,
  val lastProfileFetchTime: Long = 0,
  private val notificationChannelValue: String? = null,
  private val sealedSenderAccessModeValue: SealedSenderAccessMode = SealedSenderAccessMode.UNKNOWN,
  private val capabilities: RecipientRecord.Capabilities = RecipientRecord.Capabilities.UNKNOWN,
  val storageId: ByteArray? = null,
  val mentionSetting: MentionSetting = MentionSetting.ALWAYS_NOTIFY,
  private val wallpaperValue: ChatWallpaper? = null,
  private val chatColorsValue: ChatColors? = null,
  val avatarColor: AvatarColor = AvatarColor.UNKNOWN,
  val about: String? = null,
  val aboutEmoji: String? = null,
  private val systemProfileName: ProfileName = ProfileName.EMPTY,
  private val systemContactName: String? = null,
  private val extras: Optional<Extras> = Optional.empty(),
  val hasGroupsInCommon: Boolean = false,
  val badges: List<Badge> = emptyList(),
  val isReleaseNotes: Boolean = false,
  val needsPniSignature: Boolean = false,
  private val callLinkRoomId: CallLinkRoomId? = null,
  private val groupRecord: Optional<GroupRecord> = Optional.empty(),
  val phoneNumberSharing: PhoneNumberSharingState = PhoneNumberSharingState.UNKNOWN,
  val nickname: ProfileName = ProfileName.EMPTY,
  val note: String? = null
) {

  /** The recipient's [ServiceId], which could be either an [ACI] or [PNI]. */
  val serviceId: Optional<ServiceId> = OptionalUtil.or(Optional.ofNullable(aciValue), Optional.ofNullable(pniValue))

  /** The recipient's [ACI], if present. */
  val aci: Optional<ACI> = Optional.ofNullable(aciValue)

  /** The recipient's [PNI], if present. */
  val pni: Optional<PNI> = Optional.ofNullable(pniValue)

  /** The recipient's [DistributionListId], if present. */
  val distributionListId: Optional<DistributionListId> = Optional.ofNullable(distributionListIdValue)

  /** The recipient's username (concatenated nickname + discriminator), if present. */
  val username: Optional<String> = OptionalUtil.absentIfEmpty(usernameValue)

  /** Where or not this recipient is a system contact. */
  val isSystemContact: Boolean = contactUri != null

  /** The recipient's e164, if present. */
  val e164: Optional<String> = Optional.ofNullable(e164Value)

  /** Whether or not we should show this user's e164 in the interface. */
  val shouldShowE164: Boolean = e164Value.isNotNullOrBlank() && (isSystemContact || phoneNumberSharing == PhoneNumberSharingState.ENABLED)

  /** The recipient's email, if present. Emails are only for legacy SMS contacts that were reached via email. */
  val email: Optional<String> = Optional.ofNullable(emailValue)

  /** The recipients groupId, if present. */
  val groupId: Optional<GroupId> = Optional.ofNullable(groupIdValue)

  /** Whether or not the recipient has an address that could allow them to be reached via SMS. */
  val hasSmsAddress: Boolean = e164Value.isNotNullOrBlank() || emailValue.isNotNullOrBlank()

  /** Whether or not an [e164] is present. */
  val hasE164: Boolean = e164.isPresent

  /** Whether or not a [serviceId] is present. */
  val hasServiceId: Boolean = serviceId.isPresent

  /** Whether or not an [aci] is present. */
  val hasAci: Boolean = aci.isPresent

  /** Whether or not a [pni] is present. */
  val hasPni: Boolean = pni.isPresent

  /** True if the recipient has a [serviceId] and no other identifier, otherwise false. */
  val isServiceIdOnly: Boolean = hasServiceId && !hasSmsAddress

  /** Whether this recipient's story should be hidden from the main story list. */
  val shouldHideStory: Boolean = extras.map { it.hideStory() }.orElse(false)

  /** Whether or not you've seen all of this recipient's current stories. */
  val hasViewedStory: Boolean = extras.map { obj: Extras -> obj.hasViewedStory() }.orElse(false)

  /** Whether this recipient represents a link to group call.  */
  val isCallLink: Boolean = callLinkRoomId != null

  /** Whether the recipient has been hidden from the contact list. */
  val isHidden: Boolean = hiddenState != HiddenState.NOT_HIDDEN

  /** Whether the recipient represents an individual person (as opposed to a group or list). */
  val isIndividual: Boolean
    get() = !isGroup && !isCallLink && !isDistributionList && !isReleaseNotes

  /** Whether the recipient represents a group. It could be a Signal group or MMS group. */
  val isGroup: Boolean
    get() = resolved.groupIdValue != null

  /** Whether the recipient represents an MMS group. */
  val isMmsGroup: Boolean
    get() {
      val groupId = resolved.groupIdValue
      return groupId != null && groupId.isMms()
    }

  /** Whether the recipient represents a Signal group. */
  val isPushGroup: Boolean
    get() {
      val groupId = resolved.groupIdValue
      return groupId != null && groupId.isPush()
    }

  /** Whether the recipient represents a V1 Signal group. These types of groups were deprecated in 2020. */
  val isPushV1Group: Boolean
    get() {
      val groupId = resolved.groupIdValue
      return groupId != null && groupId.isV1()
    }

  /** Whether the recipient represents a V2 Signal group. */
  val isPushV2Group: Boolean
    get() {
      val groupId = resolved.groupIdValue
      return groupId != null && groupId.isV2()
    }

  /** Whether the recipient represents a distribution list (a specific list of people to send a story to). */
  val isDistributionList: Boolean
    get() = resolved.distributionListIdValue != null

  /** Whether the recipient represents the "My Story" distribution list. */
  val isMyStory: Boolean
    get() = resolved.distributionListIdValue == DistributionListId.from(DistributionListId.MY_STORY_ID)

  /** A group is considered "unknown" if we don't have any data to render it. */
  val isUnknownGroup: Boolean
    get() = if ((groupAvatarId.isPresent && groupAvatarId.get() != -1L) || groupName.isNotNullOrBlank()) {
      false
    } else {
      participantIdsValue.isEmpty() || participantIdsValue.size == 1 && participantIdsValue.contains(self().id)
    }

  /** Whether the group is inactive. Groups become inactive when you leave them. */
  val isInactiveGroup: Boolean
    get() = isGroup && !isActiveGroup

  /** A photo to render for this recipient. */
  val contactPhoto: ContactPhoto?
    get() = if (isSelf) {
      null
    } else if (groupIdValue != null && groupAvatarId.isPresent) {
      GroupRecordContactPhoto(groupIdValue, groupAvatarId.get())
    } else if (systemContactPhoto != null && SignalStore.settings.isPreferSystemContactPhotos) {
      SystemContactPhoto(id, systemContactPhoto, 0)
    } else if (profileAvatar != null && profileAvatarFileDetails.hasFile()) {
      ProfileContactPhoto(this)
    } else if (systemContactPhoto != null) {
      SystemContactPhoto(id, systemContactPhoto, 0)
    } else {
      null
    }

  /** The URI of the ringtone that should be used when receiving a message from this recipient, if set. */
  val messageRingtone: Uri? by lazy {
    if (messageRingtoneUri != null && messageRingtoneUri.scheme != null && messageRingtoneUri.scheme!!.startsWith("file")) {
      null
    } else {
      messageRingtoneUri
    }
  }

  /** The URI of the ringtone that should be used when receiving a call from this recipient, if set. */
  val callRingtone: Uri? by lazy {
    if (callRingtoneUri != null && callRingtoneUri.scheme != null && callRingtoneUri.scheme!!.startsWith("file")) {
      null
    } else {
      callRingtoneUri
    }
  }

  /** Whether or not the chat for the recipient is currently muted based on the current time. */
  val isMuted: Boolean
    get() = System.currentTimeMillis() <= muteUntil

  /** The ID's of the members if this recipient is a group or distribution list, otherwise empty. */
  val participantIds: List<RecipientId>
    get() = ArrayList(participantIdsValue)

  /** The [ACI]'s of the members if this recipient is a group, otherwise empty. */
  val participantAcis: List<ServiceId>
    get() {
      check(groupRecord.isPresent)
      return groupRecord.get().requireV2GroupProperties().getMemberServiceIds().toImmutableList()
    }

  /** The [RegisteredState] of this recipient. Signal groups/lists are always registered. */
  val registered: RegisteredState
    get() = if (isPushGroup || isDistributionList) {
      RegisteredState.REGISTERED
    } else if (isMmsGroup) {
      RegisteredState.NOT_REGISTERED
    } else {
      registeredValue
    }

  /** Shorthand to check if a user has been explicitly marked registered. */
  val isRegistered: Boolean
    get() = registered == RegisteredState.REGISTERED

  /** Shorthand to check if a user has _not_ been explicitly marked unregistered. */
  val isMaybeRegistered: Boolean
    get() = registered != RegisteredState.NOT_REGISTERED

  /** Shorthand to check if a user has been explicitly marked unregistered. */
  val isUnregistered: Boolean
    get() = registered == RegisteredState.NOT_REGISTERED

  /** Whether or not to show a special verified badge, indicating this is a special conversation (like release notes or note to self). */
  val showVerified: Boolean = isReleaseNotes || isSelf

  /** The notification channel, if both set and supported by the system. Otherwise null. */
  val notificationChannel: String? = if (!NotificationChannels.supported()) null else notificationChannelValue

  /** The user's capability to handle synchronizing deletes across linked devices. */
  val deleteSyncCapability: Capability = capabilities.deleteSync

  /** The user's capability to handle tracking an expire timer version. */
  val versionedExpirationTimerCapability: Capability = capabilities.versionedExpirationTimer

  /** The state around whether we can send sealed sender to this user. */
  val sealedSenderAccessMode: SealedSenderAccessMode = if (pni.isPresent && pni == serviceId) {
    SealedSenderAccessMode.DISABLED
  } else {
    sealedSenderAccessModeValue
  }

  /** The wallpaper to render as the chat background, if present. */
  val wallpaper: ChatWallpaper?
    get() {
      return if (wallpaperValue != null) {
        wallpaperValue
      } else if (isReleaseNotes) {
        null
      } else {
        SignalStore.wallpaper.getWallpaper()
      }
    }

  /** Whether or not [wallpaper] was a value set specifically for this recipient. In other words, false means that we're showing a default wallpaper. */
  val hasOwnWallpaper: Boolean = wallpaperValue != null

  /** A cheap way to check if wallpaper is set without doing any unnecessary proto parsing. */
  val hasWallpaper: Boolean
    get() = wallpaperValue != null || SignalStore.wallpaper.hasWallpaperSet()

  /** The color of the chat bubbles to use in a chat with this recipient. */
  val chatColors: ChatColors
    get() {
      return if (chatColorsValue != null && chatColorsValue.id !is Auto) {
        chatColorsValue
      } else if (chatColorsValue != null) {
        autoChatColor
      } else {
        val global = SignalStore.chatColors.chatColors
        if (global != null && global.id !is Auto) {
          global
        } else {
          autoChatColor
        }
      }
    }

  /** The badge to feature on a recipient's avatar, if any. */
  val featuredBadge: Badge? = badges.firstOrNull()

  /** A string combining the about emoji + text for displaying various places. */
  val combinedAboutAndEmoji: String? by lazy { listOf(aboutEmoji, about).filter { it.isNotNullOrBlank() }.joinToString(separator = " ").nullIfBlank() }

  /** Whether or not we should blur the recipient's avatar when showing it in the chat list and other locations. */
  val shouldBlurAvatar: Boolean
    get() {
      val showOverride = extras.isPresent && extras.get().manuallyShownAvatar()
      return !showOverride && !isSelf && !isProfileSharing && !isSystemContact && !hasGroupsInCommon && isRegistered
    }

  /** The chat color to use when the "automatic" chat color setting is active, which derives a color from the wallpaper. */
  private val autoChatColor: ChatColors
    get() = wallpaper?.autoChatColors ?: ChatColorsPalette.Bubbles.default.withId(Auto)

  /** A fully resolved copy of this recipient, if needed. */
  private val resolved: Recipient
    get() = if (isResolving) live().resolve() else this

  /** Convenience method to get a non-null [serviceId] hen you know it is there. */
  fun requireServiceId(): ServiceId {
    return resolved.aciValue ?: resolved.pniValue ?: throw MissingAddressError(id)
  }

  /** Convenience method to get a non-null [aci] hen you know it is there. */
  fun requireAci(): ACI {
    return resolved.aciValue ?: throw MissingAddressError(id)
  }

  /** Convenience method to get a non-null [pni] when you know it is there. */
  fun requirePni(): PNI {
    return resolved.pniValue ?: throw MissingAddressError(id)
  }

  /** Convenience method to get a non-null [e164] when you know it is there. */
  fun requireE164(): String {
    return resolved.e164Value ?: throw MissingAddressError(id)
  }

  /** Convenience method to get a non-null [email] when you know it is there. */
  fun requireEmail(): String {
    return resolved.emailValue ?: throw MissingAddressError(id)
  }

  /** Convenience method to get a non-null sms address (either e164 or email) when you know it is there. */
  fun requireSmsAddress(): String {
    return resolved.e164Value ?: resolved.emailValue ?: throw MissingAddressError(id)
  }

  /** Convenience method to get a non-null [groupId] when you know it is there. */
  fun requireGroupId(): GroupId {
    return resolved.groupIdValue ?: throw MissingAddressError(id)
  }

  /** Convenience method to get a non-null distributionListId when you know it is there. */
  fun requireDistributionListId(): DistributionListId {
    return resolved.distributionListIdValue ?: throw MissingAddressError(id)
  }

  /** Convenience method to get a non-null callLinkRoomId when you know it is there. */
  fun requireCallLinkRoomId(): CallLinkRoomId {
    return resolved.callLinkRoomId ?: throw MissingAddressError(id)
  }

  /** Convenience method to get a non-null call conversation ID when you know it is there. */
  fun requireCallConversationId(): ByteArray {
    return if (isPushGroup) {
      requireGroupId().decodedId
    } else if (isCallLink) {
      requireCallLinkRoomId().encodeForProto().toByteArray()
    } else if (isIndividual) {
      requireServiceId().toByteArray()
    } else {
      throw IllegalStateException("Recipient does not support conversation id")
    }
  }

  /** A single string to represent the recipient, in order of precedence: Group ID > ServiceId > Phone > Email */
  fun requireStringId(): String {
    return when {
      resolved.isGroup -> resolved.requireGroupId().toString()
      resolved.serviceId.isPresent -> resolved.requireServiceId().toString()
      else -> resolved.requireSmsAddress()
    }
  }

  /** The name to show for a group. It will be the group name if present, otherwise we default to a list of shortened member names. */
  fun getGroupName(context: Context): String? {
    return if (groupIdValue != null && Util.isEmpty(groupName)) {
      val selfId = AppDependencies.recipientCache.getSelfId()
      val others = participantIdsValue
        .filter { id: RecipientId -> id != selfId }
        .take(MAX_MEMBER_NAMES)
        .map { resolved(it) }

      val shortNameCounts: MutableMap<String, Int> = HashMap()
      for (participant in others) {
        val shortName = participant.getShortDisplayName(context)
        val count = Objects.requireNonNull(shortNameCounts.getOrDefault(shortName, 0))
        shortNameCounts[shortName] = count + 1
      }

      val names: MutableList<String> = LinkedList()
      for (participant in others) {
        val shortName = participant.getShortDisplayName(context)
        val count = Objects.requireNonNull(shortNameCounts.getOrDefault(shortName, 0))
        if (count <= 1) {
          names.add(shortName)
        } else {
          names.add(participant.getDisplayName(context))
        }
      }

      if (participantIdsValue.stream().anyMatch { id: RecipientId -> id == selfId }) {
        names.add(context.getString(R.string.Recipient_you))
      }

      Util.join(names, ", ")
    } else if (!isResolving && isMyStory) {
      context.getString(R.string.Recipient_my_story)
    } else if (!isResolving && Util.isEmpty(groupName) && isCallLink) {
      context.getString(R.string.Recipient_signal_call)
    } else {
      groupName
    }
  }

  /** False iff it [getDisplayName] would fall back to e164, email, or unknown. */
  fun hasAUserSetDisplayName(context: Context): Boolean {
    return getGroupName(context).isNotNullOrBlank() ||
      nickname.toString().isNotNullOrBlank() ||
      systemContactName.isNotNullOrBlank() ||
      profileName.toString().isNotNullOrBlank()
  }

  /** A full-length display name to render for this recipient. */
  fun getDisplayName(context: Context): String {
    var name = getNameFromLocalData(context)
    if (Util.isEmpty(name)) {
      name = usernameValue
    }
    if (Util.isEmpty(name)) {
      name = getUnknownDisplayName(context)
    }
    return StringUtil.isolateBidi(name)
  }

  fun hasNonUsernameDisplayName(context: Context): Boolean {
    return getNameFromLocalData(context).isNotNullOrBlank()
  }

  /** A full-length display name for this user, ignoring the username. */
  private fun getNameFromLocalData(context: Context): String? {
    var name = getGroupName(context)

    if (name.isNullOrBlank()) {
      name = nickname.toString()
    }

    if (name.isBlank() && systemContactName != null) {
      name = systemContactName
    }

    if (name.isBlank()) {
      name = profileName.toString()
    }

    if (name.isBlank() && e164Value.isNotNullOrBlank()) {
      name = PhoneNumberFormatter.prettyPrint(e164Value)
    }

    if (name.isBlank() && emailValue != null) {
      name = emailValue
    }

    return name
  }

  /** A display name to use when rendering a mention of this user. */
  fun getMentionDisplayName(context: Context): String {
    var name: String? = if (isSelf) profileName.toString() else getGroupName(context)
    name = StringUtil.isolateBidi(name)

    if (name.isBlank()) {
      name = if (isSelf) getGroupName(context) else nickname.toString()
      name = StringUtil.isolateBidi(name)
    }

    if (name.isBlank()) {
      name = if (isSelf) getGroupName(context) else systemContactName
      name = StringUtil.isolateBidi(name)
    }

    if (name.isBlank()) {
      name = if (isSelf) getGroupName(context) else profileName.toString()
      name = StringUtil.isolateBidi(name)
    }

    if (name.isBlank() && e164Value.isNotNullOrBlank()) {
      name = PhoneNumberFormatter.prettyPrint(e164Value)
    }

    if (name.isBlank()) {
      name = StringUtil.isolateBidi(emailValue)
    }

    if (name.isBlank()) {
      name = StringUtil.isolateBidi(context.getString(R.string.Recipient_unknown))
    }

    return name
  }

  /** A shortened [getDisplayName], preferring given names. */
  fun getShortDisplayName(context: Context): String {
    val name = listOf(
      getGroupName(context),
      nickname.givenName,
      nickname.toString(),
      systemProfileName.givenName,
      systemProfileName.toString(),
      profileName.givenName,
      profileName.toString(),
      username.orElse(null),
      getDisplayName(context)
    ).firstOrNull { it.isNotNullOrBlank() }

    return StringUtil.isolateBidi(name)
  }

  private fun getUnknownDisplayName(context: Context): String {
    return if (registered == RegisteredState.NOT_REGISTERED) {
      context.getString(R.string.Recipient_deleted_account)
    } else {
      context.getString(R.string.Recipient_unknown)
    }
  }

  fun getFallbackAvatar(): FallbackAvatar {
    return if (isSelf) {
      FallbackAvatar.Resource.Local(avatarColor)
    } else if (isResolving) {
      FallbackAvatar.Transparent
    } else if (isDistributionList) {
      FallbackAvatar.Resource.DistributionList(avatarColor)
    } else if (isCallLink) {
      FallbackAvatar.Resource.CallLink(avatarColor)
    } else if (groupIdValue != null) {
      FallbackAvatar.Resource.Group(avatarColor)
    } else if (isGroup) {
      FallbackAvatar.Resource.Group(avatarColor)
    } else if (groupName.isNotNullOrBlank()) {
      FallbackAvatar.forTextOrDefault(groupName, avatarColor, FallbackAvatar.Resource.Group(avatarColor))
    } else if (!nickname.isEmpty) {
      FallbackAvatar.forTextOrDefault(nickname.toString(), avatarColor)
    } else if (systemContactName.isNotNullOrBlank()) {
      FallbackAvatar.forTextOrDefault(systemContactName, avatarColor)
    } else if (!profileName.isEmpty) {
      FallbackAvatar.forTextOrDefault(profileName.toString(), avatarColor)
    } else {
      FallbackAvatar.Resource.Person(avatarColor)
    }
  }

  /**
   * If this recipient is missing crucial data, this will return a populated copy. Otherwise it
   * returns itself.
   */
  fun resolve(): Recipient {
    return resolved
  }

  /** Forces retrieving a fresh copy of the recipient, regardless of its state. */
  fun fresh(): Recipient {
    return live().refresh().resolve()
  }

  /** Returns a live, observable copy of this recipient. */
  fun live(): LiveRecipient {
    return AppDependencies.recipientCache.getLive(id)
  }

  enum class HiddenState(private val value: Int) {
    NOT_HIDDEN(0),
    HIDDEN(1),
    HIDDEN_MESSAGE_REQUEST(2);

    fun serialize(): Int {
      return value
    }

    companion object {
      fun deserialize(value: Int): HiddenState {
        return when (value) {
          0 -> NOT_HIDDEN
          1 -> HIDDEN
          2 -> HIDDEN_MESSAGE_REQUEST
          else -> throw IllegalArgumentException()
        }
      }
    }
  }

  enum class Capability(private val value: Int) {
    UNKNOWN(0),
    SUPPORTED(1),
    NOT_SUPPORTED(2);

    fun serialize(): Int {
      return value
    }

    val isSupported: Boolean
      get() = this == SUPPORTED

    companion object {
      fun deserialize(value: Int): Capability {
        return when (value) {
          0 -> UNKNOWN
          1 -> SUPPORTED
          2 -> NOT_SUPPORTED
          else -> throw IllegalArgumentException()
        }
      }

      fun fromBoolean(supported: Boolean): Capability {
        return if (supported) SUPPORTED else NOT_SUPPORTED
      }
    }
  }

  class Extras private constructor(private val recipientExtras: RecipientExtras) {
    fun manuallyShownAvatar(): Boolean {
      return recipientExtras.manuallyShownAvatar
    }

    fun hideStory(): Boolean {
      return recipientExtras.hideStory
    }

    fun hasViewedStory(): Boolean {
      return recipientExtras.lastStoryView > 0L
    }

    override fun equals(o: Any?): Boolean {
      if (this === o) return true
      if (o == null || javaClass != o.javaClass) return false
      val that = o as Extras
      return manuallyShownAvatar() == that.manuallyShownAvatar() && hideStory() == that.hideStory() && hasViewedStory() == that.hasViewedStory()
    }

    override fun hashCode(): Int {
      return Objects.hash(manuallyShownAvatar(), hideStory(), hasViewedStory())
    }

    companion object {
      fun from(recipientExtras: RecipientExtras?): Extras? {
        return if (recipientExtras != null) {
          Extras(recipientExtras)
        } else {
          null
        }
      }
    }
  }

  fun hasSameContent(other: Recipient): Boolean {
    return id == other.id &&
      isResolving == other.isResolving &&
      isSelf == other.isSelf &&
      isBlocked == other.isBlocked &&
      muteUntil == other.muteUntil &&
      expiresInSeconds == other.expiresInSeconds &&
      profileAvatarFileDetails == other.profileAvatarFileDetails &&
      isProfileSharing == other.isProfileSharing &&
      hiddenState == other.hiddenState &&
      aciValue == other.aciValue &&
      usernameValue == other.usernameValue &&
      e164Value == other.e164Value &&
      emailValue == other.emailValue &&
      groupIdValue == other.groupIdValue &&
      participantIdsValue == other.participantIdsValue &&
      groupAvatarId == other.groupAvatarId &&
      messageVibrate == other.messageVibrate &&
      callVibrate == other.callVibrate &&
      messageRingtoneUri == other.messageRingtoneUri &&
      callRingtoneUri == other.callRingtoneUri &&
      registeredValue == other.registeredValue &&
      profileKey.contentEquals(other.profileKey) &&
      expiringProfileKeyCredential == other.expiringProfileKeyCredential &&
      groupName == other.groupName &&
      systemContactPhoto == other.systemContactPhoto &&
      customLabel == other.customLabel &&
      contactUri == other.contactUri &&
      profileName == other.profileName &&
      systemProfileName == other.systemProfileName &&
      profileAvatar == other.profileAvatar &&
      notificationChannelValue == other.notificationChannelValue &&
      sealedSenderAccessModeValue == other.sealedSenderAccessModeValue &&
      storageId.contentEquals(other.storageId) &&
      mentionSetting == other.mentionSetting &&
      wallpaperValue == other.wallpaperValue &&
      chatColorsValue == other.chatColorsValue &&
      avatarColor == other.avatarColor &&
      about == other.about &&
      aboutEmoji == other.aboutEmoji &&
      extras == other.extras &&
      hasGroupsInCommon == other.hasGroupsInCommon &&
      badges == other.badges &&
      isActiveGroup == other.isActiveGroup &&
      callLinkRoomId == other.callLinkRoomId &&
      phoneNumberSharing == other.phoneNumberSharing &&
      nickname == other.nickname &&
      note == other.note
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Recipient

    if (id != other.id) return false

    return true
  }

  override fun hashCode(): Int {
    return id.hashCode()
  }

  private class MissingAddressError(recipientId: RecipientId) : AssertionError("Missing address for " + recipientId.serialize())

  companion object {
    private val TAG = Log.tag(Recipient::class.java)

    @JvmField
    val UNKNOWN = Recipient()

    private const val MAX_MEMBER_NAMES = 10

    /**
     * Returns a [LiveRecipient], which contains a [Recipient] that may or may not be
     * populated with data. However, you can observe the value that's returned to be notified when the
     * [Recipient] changes.
     */
    @JvmStatic
    @AnyThread
    fun live(id: RecipientId): LiveRecipient {
      return AppDependencies.recipientCache.getLive(id)
    }

    /**
     * Returns a live recipient wrapped in an Observable. All work is done on the IO threadpool.
     */
    @JvmStatic
    @AnyThread
    fun observable(id: RecipientId): Observable<Recipient> {
      return live(id).observable().subscribeOn(Schedulers.io())
    }

    /**
     * Returns a fully-populated [Recipient]. May hit the disk, and therefore should be
     * called on a background thread.
     */
    @JvmStatic
    @WorkerThread
    fun resolved(id: RecipientId): Recipient {
      return live(id).resolve()
    }

    @JvmStatic
    @WorkerThread
    fun resolvedList(ids: Collection<RecipientId>): List<Recipient> {
      return ids.map { resolved(it) }
    }

    @JvmStatic
    @WorkerThread
    fun distributionList(distributionListId: DistributionListId): Recipient {
      val id = SignalDatabase.recipients.getOrInsertFromDistributionListId(distributionListId)
      return resolved(id)
    }

    /**
     * Returns a fully-populated [Recipient] and associates it with the provided username.
     */
    @JvmStatic
    @WorkerThread
    fun externalUsername(serviceId: ServiceId, username: String): Recipient {
      val recipient = externalPush(serviceId)
      SignalDatabase.recipients.setUsername(recipient.id, username)
      return recipient
    }

    /**
     * Returns a fully-populated [Recipient] based off of a [SignalServiceAddress], creating one in the database if necessary.
     */
    @JvmStatic
    @WorkerThread
    fun externalPush(signalServiceAddress: SignalServiceAddress): Recipient {
      return externalPush(signalServiceAddress.serviceId, signalServiceAddress.number.orElse(null))
    }

    /**
     * Returns a fully-populated [Recipient] based off of a ServiceId, creating one in the database if necessary.
     */
    @JvmStatic
    @WorkerThread
    fun externalPush(serviceId: ServiceId): Recipient {
      return externalPush(serviceId, null)
    }

    /**
     * Create a recipient with a full (ACI, PNI, E164) tuple. It is assumed that the association between the PNI and serviceId is trusted.
     * That means it must be from either storage service (with the verified field set) or a PNI verification message.
     */
    @JvmStatic
    @WorkerThread
    fun trustedPush(aci: ACI, pni: PNI?, e164: String?): Recipient {
      if (ACI.UNKNOWN == aci || PNI.UNKNOWN == pni) {
        throw AssertionError("Unknown serviceId!")
      }

      val recipientId = SignalDatabase.recipients.getAndPossiblyMergePnpVerified(aci, pni, e164)
      val resolved = resolved(recipientId)

      if (resolved.id != recipientId) {
        Log.w(TAG, "Resolved $recipientId, but got back a recipient with ${resolved.id}")
      }

      if (!resolved.isRegistered) {
        Log.w(TAG, "External push was locally marked unregistered. Marking as registered.")
        SignalDatabase.recipients.markRegistered(recipientId, aci)
      }

      return resolved
    }

    /**
     * Returns a fully-populated [Recipient] based off of a ServiceId and phone number, creating one
     * in the database if necessary. We want both piece of information so we're able to associate them
     * both together, depending on which are available.
     *
     * In particular, while we may eventually get the ACI of a user created via a phone number
     * (through a directory sync), the only way we can store the phone number is by retrieving it from
     * sent messages and whatnot. So we should store it when available.
     */
    @JvmStatic
    @WorkerThread
    fun externalPush(serviceId: ServiceId?, e164: String?): Recipient {
      if (ACI.UNKNOWN == serviceId || PNI.UNKNOWN == serviceId) {
        throw AssertionError()
      }

      val recipientId = RecipientId.from(SignalServiceAddress(serviceId, e164))
      val resolved = resolved(recipientId)

      if (resolved.id != recipientId) {
        Log.w(TAG, "Resolved $recipientId, but got back a recipient with ${resolved.id}")
      }

      if (!resolved.isRegistered && serviceId != null) {
        Log.w(TAG, "External push was locally marked unregistered. Marking as registered.")
        SignalDatabase.recipients.markRegistered(recipientId, serviceId)
      } else if (!resolved.isRegistered) {
        Log.w(TAG, "External push was locally marked unregistered, but we don't have an ACI, so we can't do anything.", Throwable())
      }

      return resolved
    }

    /**
     * A safety wrapper around [.external] for when you know you're using an
     * identifier for a system contact, and therefore always want to prevent interpreting it as a
     * UUID. This will crash if given a UUID.
     *
     * (This may seem strange, but apparently some devices are returning valid UUIDs for contacts)
     */
    @JvmStatic
    @WorkerThread
    fun externalContact(identifier: String): Recipient {
      val id: RecipientId = if (UuidUtil.isUuid(identifier)) {
        throw AssertionError("UUIDs are not valid system contact identifiers!")
      } else if (NumberUtil.isValidEmail(identifier)) {
        SignalDatabase.recipients.getOrInsertFromEmail(identifier)
      } else {
        SignalDatabase.recipients.getOrInsertFromE164(identifier)
      }

      return resolved(id)
    }

    /**
     * A version of [external] that should be used when you know the
     * identifier is a groupId.
     *
     * Important: This will throw an exception if the groupId you're using could have been migrated.
     * If you're dealing with inbound data, you should be using
     * [.externalPossiblyMigratedGroup], or checking the database before
     * calling this method.
     */
    @JvmStatic
    @WorkerThread
    fun externalGroupExact(groupId: GroupId): Recipient {
      return resolved(SignalDatabase.recipients.getOrInsertFromGroupId(groupId))
    }

    /**
     * Will give you one of:
     * - The recipient that matches the groupId specified exactly
     * - The recipient whose V1 ID would map to the provided V2 ID
     * - The recipient whose V2 ID would be derived from the provided V1 ID
     * - A newly-created recipient for the provided ID if none of the above match
     *
     * Important: You could get back a recipient with a different groupId than the one you provided.
     * You should be very cautious when using the groupId on the returned recipient.
     */
    @JvmStatic
    @WorkerThread
    fun externalPossiblyMigratedGroup(groupId: GroupId): Recipient {
      val id = RecipientId.from(groupId)
      return try {
        resolved(id)
      } catch (ex: MissingRecipientException) {
        Log.w(TAG, "Could not find recipient ($id) for group $groupId. Clearing RecipientId cache and trying again.", ex)
        RecipientId.clearCache()
        resolved(SignalDatabase.recipients.getOrInsertFromPossiblyMigratedGroupId(groupId))
      }
    }

    /**
     * Returns a fully-populated [Recipient] based off of a string identifier, creating one in
     * the database if necessary. The identifier may be a uuid, phone number, email,
     * or serialized groupId.
     *
     * If the identifier is a UUID of a Signal user, prefer using
     * [.externalPush] or its overload, as this will let us associate
     * the phone number with the recipient.
     */
    @JvmStatic
    @WorkerThread
    fun external(context: Context, identifier: String): Recipient {
      val serviceId = ServiceId.parseOrNull(identifier, logFailures = false)

      val id: RecipientId = if (serviceId != null) {
        SignalDatabase.recipients.getOrInsertFromServiceId(serviceId)
      } else if (GroupId.isEncodedGroup(identifier)) {
        SignalDatabase.recipients.getOrInsertFromGroupId(GroupId.parseOrThrow(identifier))
      } else if (NumberUtil.isValidEmail(identifier)) {
        SignalDatabase.recipients.getOrInsertFromEmail(identifier)
      } else if (isValidUsernameForSearch(identifier)) {
        throw IllegalArgumentException("Creating a recipient based on username alone is not supported!")
      } else {
        val e164 = PhoneNumberFormatter.get(context).format(identifier)
        SignalDatabase.recipients.getOrInsertFromE164(e164)
      }

      return resolved(id)
    }

    @JvmStatic
    fun self(): Recipient {
      return AppDependencies.recipientCache.getSelf()
    }

    /** Whether we've set a recipient for 'self' yet. We do this during registration. */
    val isSelfSet: Boolean
      get() = AppDependencies.recipientCache.getSelfId() != null
  }
}
