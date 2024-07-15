package org.thoughtcrime.securesms.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import android.net.Uri
import android.text.TextUtils
import androidx.annotation.VisibleForTesting
import androidx.core.content.contentValuesOf
import app.cash.exhaustive.Exhaustive
import okio.ByteString.Companion.toByteString
import org.signal.core.util.Base64
import org.signal.core.util.Bitmask
import org.signal.core.util.CursorUtil
import org.signal.core.util.SqlUtil
import org.signal.core.util.delete
import org.signal.core.util.exists
import org.signal.core.util.forEach
import org.signal.core.util.logging.Log
import org.signal.core.util.nullIfBlank
import org.signal.core.util.optionalString
import org.signal.core.util.or
import org.signal.core.util.orNull
import org.signal.core.util.readToList
import org.signal.core.util.readToSet
import org.signal.core.util.readToSingleBoolean
import org.signal.core.util.readToSingleLong
import org.signal.core.util.readToSingleObject
import org.signal.core.util.requireBlob
import org.signal.core.util.requireInt
import org.signal.core.util.requireLong
import org.signal.core.util.requireNonNullString
import org.signal.core.util.requireString
import org.signal.core.util.select
import org.signal.core.util.toInt
import org.signal.core.util.update
import org.signal.core.util.updateAll
import org.signal.core.util.withinTransaction
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.InvalidKeyException
import org.signal.libsignal.zkgroup.profiles.ExpiringProfileKeyCredential
import org.signal.libsignal.zkgroup.profiles.ProfileKey
import org.signal.storageservice.protos.groups.local.DecryptedGroup
import org.thoughtcrime.securesms.badges.Badges.toDatabaseBadge
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.color.MaterialColor
import org.thoughtcrime.securesms.color.MaterialColor.UnknownColorException
import org.thoughtcrime.securesms.contacts.paged.ContactSearchSortOrder
import org.thoughtcrime.securesms.conversation.colors.AvatarColor
import org.thoughtcrime.securesms.conversation.colors.AvatarColorHash
import org.thoughtcrime.securesms.conversation.colors.ChatColors
import org.thoughtcrime.securesms.conversation.colors.ChatColors.Companion.forChatColor
import org.thoughtcrime.securesms.conversation.colors.ChatColors.Id.Companion.forLongValue
import org.thoughtcrime.securesms.conversation.colors.ChatColorsMapper.getChatColors
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil
import org.thoughtcrime.securesms.database.GroupTable.LegacyGroupInsertException
import org.thoughtcrime.securesms.database.GroupTable.ShowAsStoryState
import org.thoughtcrime.securesms.database.IdentityTable.VerifiedStatus
import org.thoughtcrime.securesms.database.RecipientTableCursorUtil.getRecipientExtras
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.groups
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.identities
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.runPostSuccessfulTransaction
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.sessions
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.threads
import org.thoughtcrime.securesms.database.model.DistributionListId
import org.thoughtcrime.securesms.database.model.RecipientRecord
import org.thoughtcrime.securesms.database.model.ThreadRecord
import org.thoughtcrime.securesms.database.model.databaseprotos.BadgeList
import org.thoughtcrime.securesms.database.model.databaseprotos.ChatColor
import org.thoughtcrime.securesms.database.model.databaseprotos.DeviceLastResetTime
import org.thoughtcrime.securesms.database.model.databaseprotos.ExpiringProfileKeyCredentialColumnData
import org.thoughtcrime.securesms.database.model.databaseprotos.RecipientExtras
import org.thoughtcrime.securesms.database.model.databaseprotos.SessionSwitchoverEvent
import org.thoughtcrime.securesms.database.model.databaseprotos.ThreadMergeEvent
import org.thoughtcrime.securesms.database.model.databaseprotos.Wallpaper
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.groups.BadGroupIdException
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.groups.GroupId.V1
import org.thoughtcrime.securesms.groups.GroupId.V2
import org.thoughtcrime.securesms.groups.v2.ProfileKeySet
import org.thoughtcrime.securesms.groups.v2.processing.GroupsV2StateProcessor
import org.thoughtcrime.securesms.jobs.RequestGroupV2InfoJob
import org.thoughtcrime.securesms.jobs.RetrieveProfileJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.recipients.RecipientUtil
import org.thoughtcrime.securesms.service.webrtc.links.CallLinkRoomId
import org.thoughtcrime.securesms.storage.StorageRecordUpdate
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.storage.StorageSyncModels
import org.thoughtcrime.securesms.util.IdentityUtil
import org.thoughtcrime.securesms.util.ProfileUtil
import org.thoughtcrime.securesms.util.RemoteConfig
import org.thoughtcrime.securesms.util.Util
import org.thoughtcrime.securesms.wallpaper.ChatWallpaper
import org.thoughtcrime.securesms.wallpaper.WallpaperStorage
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.api.push.ServiceId.ACI
import org.whispersystems.signalservice.api.push.ServiceId.PNI
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.api.storage.SignalAccountRecord
import org.whispersystems.signalservice.api.storage.SignalContactRecord
import org.whispersystems.signalservice.api.storage.SignalGroupV1Record
import org.whispersystems.signalservice.api.storage.SignalGroupV2Record
import org.whispersystems.signalservice.api.storage.StorageId
import org.whispersystems.signalservice.internal.storage.protos.GroupV2Record
import java.io.Closeable
import java.io.IOException
import java.util.Collections
import java.util.LinkedList
import java.util.Objects
import java.util.Optional
import java.util.concurrent.TimeUnit
import kotlin.jvm.optionals.getOrNull
import kotlin.math.max

open class RecipientTable(context: Context, databaseHelper: SignalDatabase) : DatabaseTable(context, databaseHelper) {

  val TAG = Log.tag(RecipientTable::class.java)

  companion object {
    private val UNREGISTERED_LIFESPAN: Long = TimeUnit.DAYS.toMillis(30)

    const val TABLE_NAME = "recipient"

    const val ID = "_id"
    const val TYPE = "type"
    const val E164 = "e164"
    const val ACI_COLUMN = "aci"
    const val PNI_COLUMN = "pni"
    const val USERNAME = "username"
    const val EMAIL = "email"
    const val GROUP_ID = "group_id"
    const val DISTRIBUTION_LIST_ID = "distribution_list_id"
    const val CALL_LINK_ROOM_ID = "call_link_room_id"
    const val REGISTERED = "registered"
    const val UNREGISTERED_TIMESTAMP = "unregistered_timestamp"
    const val BLOCKED = "blocked"
    const val HIDDEN = "hidden"
    const val PROFILE_KEY = "profile_key"
    const val EXPIRING_PROFILE_KEY_CREDENTIAL = "profile_key_credential"
    const val PROFILE_SHARING = "profile_sharing"
    const val PROFILE_GIVEN_NAME = "profile_given_name"
    const val PROFILE_FAMILY_NAME = "profile_family_name"
    const val PROFILE_JOINED_NAME = "profile_joined_name"
    const val PROFILE_AVATAR = "profile_avatar"
    const val LAST_PROFILE_FETCH = "last_profile_fetch"
    const val SYSTEM_GIVEN_NAME = "system_given_name"
    const val SYSTEM_FAMILY_NAME = "system_family_name"
    const val SYSTEM_JOINED_NAME = "system_joined_name"
    const val SYSTEM_NICKNAME = "system_nickname"
    const val SYSTEM_PHOTO_URI = "system_photo_uri"
    const val SYSTEM_PHONE_LABEL = "system_phone_label"
    const val SYSTEM_PHONE_TYPE = "system_phone_type"
    const val SYSTEM_CONTACT_URI = "system_contact_uri"
    const val SYSTEM_INFO_PENDING = "system_info_pending"
    const val NOTIFICATION_CHANNEL = "notification_channel"
    const val MESSAGE_RINGTONE = "message_ringtone"
    const val MESSAGE_VIBRATE = "message_vibrate"
    const val CALL_RINGTONE = "call_ringtone"
    const val CALL_VIBRATE = "call_vibrate"
    const val MUTE_UNTIL = "mute_until"
    const val MESSAGE_EXPIRATION_TIME = "message_expiration_time"
    const val SEALED_SENDER_MODE = "sealed_sender_mode"
    const val STORAGE_SERVICE_ID = "storage_service_id"
    const val STORAGE_SERVICE_PROTO = "storage_service_proto"
    const val MENTION_SETTING = "mention_setting"
    const val CAPABILITIES = "capabilities"
    const val LAST_SESSION_RESET = "last_session_reset"
    const val WALLPAPER = "wallpaper"
    const val WALLPAPER_URI = "wallpaper_uri"
    const val ABOUT = "about"
    const val ABOUT_EMOJI = "about_emoji"
    const val EXTRAS = "extras"
    const val GROUPS_IN_COMMON = "groups_in_common"
    const val AVATAR_COLOR = "avatar_color"
    const val CHAT_COLORS = "chat_colors"
    const val CUSTOM_CHAT_COLORS_ID = "custom_chat_colors_id"
    const val BADGES = "badges"
    const val NEEDS_PNI_SIGNATURE = "needs_pni_signature"
    const val REPORTING_TOKEN = "reporting_token"
    const val PHONE_NUMBER_SHARING = "phone_number_sharing"
    const val PHONE_NUMBER_DISCOVERABLE = "phone_number_discoverable"
    const val PNI_SIGNATURE_VERIFIED = "pni_signature_verified"
    const val NICKNAME_GIVEN_NAME = "nickname_given_name"
    const val NICKNAME_FAMILY_NAME = "nickname_family_name"
    const val NICKNAME_JOINED_NAME = "nickname_joined_name"
    const val NOTE = "note"

    const val SEARCH_PROFILE_NAME = "search_signal_profile"
    const val SORT_NAME = "sort_name"
    const val IDENTITY_STATUS = "identity_status"
    const val IDENTITY_KEY = "identity_key"

    @JvmField
    val CREATE_TABLE =
      """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY AUTOINCREMENT,
        $TYPE INTEGER DEFAULT ${RecipientType.INDIVIDUAL.id},
        $E164 TEXT UNIQUE DEFAULT NULL,
        $ACI_COLUMN TEXT UNIQUE DEFAULT NULL,
        $PNI_COLUMN TEXT UNIQUE DEFAULT NULL CHECK (pni LIKE 'PNI:%'),
        $USERNAME TEXT UNIQUE DEFAULT NULL,
        $EMAIL TEXT UNIQUE DEFAULT NULL,
        $GROUP_ID TEXT UNIQUE DEFAULT NULL,
        $DISTRIBUTION_LIST_ID INTEGER DEFAULT NULL,
        $CALL_LINK_ROOM_ID TEXT DEFAULT NULL,
        $REGISTERED INTEGER DEFAULT ${RegisteredState.UNKNOWN.id},
        $UNREGISTERED_TIMESTAMP INTEGER DEFAULT 0,
        $BLOCKED INTEGER DEFAULT 0,
        $HIDDEN INTEGER DEFAULT 0,
        $PROFILE_KEY TEXT DEFAULT NULL, 
        $EXPIRING_PROFILE_KEY_CREDENTIAL TEXT DEFAULT NULL, 
        $PROFILE_SHARING INTEGER DEFAULT 0, 
        $PROFILE_GIVEN_NAME TEXT DEFAULT NULL, 
        $PROFILE_FAMILY_NAME TEXT DEFAULT NULL, 
        $PROFILE_JOINED_NAME TEXT DEFAULT NULL, 
        $PROFILE_AVATAR TEXT DEFAULT NULL, 
        $LAST_PROFILE_FETCH INTEGER DEFAULT 0, 
        $SYSTEM_GIVEN_NAME TEXT DEFAULT NULL, 
        $SYSTEM_FAMILY_NAME TEXT DEFAULT NULL, 
        $SYSTEM_JOINED_NAME TEXT DEFAULT NULL, 
        $SYSTEM_NICKNAME TEXT DEFAULT NULL,
        $SYSTEM_PHOTO_URI TEXT DEFAULT NULL, 
        $SYSTEM_PHONE_LABEL TEXT DEFAULT NULL, 
        $SYSTEM_PHONE_TYPE INTEGER DEFAULT -1, 
        $SYSTEM_CONTACT_URI TEXT DEFAULT NULL, 
        $SYSTEM_INFO_PENDING INTEGER DEFAULT 0, 
        $NOTIFICATION_CHANNEL TEXT DEFAULT NULL, 
        $MESSAGE_RINGTONE TEXT DEFAULT NULL, 
        $MESSAGE_VIBRATE INTEGER DEFAULT ${VibrateState.DEFAULT.id}, 
        $CALL_RINGTONE TEXT DEFAULT NULL, 
        $CALL_VIBRATE INTEGER DEFAULT ${VibrateState.DEFAULT.id}, 
        $MUTE_UNTIL INTEGER DEFAULT 0, 
        $MESSAGE_EXPIRATION_TIME INTEGER DEFAULT 0,
        $SEALED_SENDER_MODE INTEGER DEFAULT 0, 
        $STORAGE_SERVICE_ID TEXT UNIQUE DEFAULT NULL, 
        $STORAGE_SERVICE_PROTO TEXT DEFAULT NULL,
        $MENTION_SETTING INTEGER DEFAULT ${MentionSetting.ALWAYS_NOTIFY.id}, 
        $CAPABILITIES INTEGER DEFAULT 0,
        $LAST_SESSION_RESET BLOB DEFAULT NULL,
        $WALLPAPER BLOB DEFAULT NULL,
        $WALLPAPER_URI TEXT DEFAULT NULL,
        $ABOUT TEXT DEFAULT NULL,
        $ABOUT_EMOJI TEXT DEFAULT NULL,
        $EXTRAS BLOB DEFAULT NULL,
        $GROUPS_IN_COMMON INTEGER DEFAULT 0,
        $AVATAR_COLOR TEXT DEFAULT NULL, 
        $CHAT_COLORS BLOB DEFAULT NULL,
        $CUSTOM_CHAT_COLORS_ID INTEGER DEFAULT 0,
        $BADGES BLOB DEFAULT NULL,
        $NEEDS_PNI_SIGNATURE INTEGER DEFAULT 0,
        $REPORTING_TOKEN BLOB DEFAULT NULL,
        $PHONE_NUMBER_SHARING INTEGER DEFAULT ${PhoneNumberSharingState.UNKNOWN.id},
        $PHONE_NUMBER_DISCOVERABLE INTEGER DEFAULT ${PhoneNumberDiscoverableState.UNKNOWN.id},
        $PNI_SIGNATURE_VERIFIED INTEGER DEFAULT 0,
        $NICKNAME_GIVEN_NAME TEXT DEFAULT NULL,
        $NICKNAME_FAMILY_NAME TEXT DEFAULT NULL,
        $NICKNAME_JOINED_NAME TEXT DEFAULT NULL,
        $NOTE TEXT DEFAULT NULL
      )
      """

    val CREATE_INDEXS = arrayOf(
      "CREATE INDEX IF NOT EXISTS recipient_type_index ON $TABLE_NAME ($TYPE);",
      "CREATE INDEX IF NOT EXISTS recipient_aci_profile_key_index ON $TABLE_NAME ($ACI_COLUMN, $PROFILE_KEY) WHERE $ACI_COLUMN NOT NULL AND $PROFILE_KEY NOT NULL"
    )

    private val RECIPIENT_PROJECTION: Array<String> = arrayOf(
      ID,
      TYPE,
      E164,
      ACI_COLUMN,
      PNI_COLUMN,
      USERNAME,
      EMAIL,
      GROUP_ID,
      DISTRIBUTION_LIST_ID,
      CALL_LINK_ROOM_ID,
      REGISTERED,
      BLOCKED,
      HIDDEN,
      PROFILE_KEY,
      EXPIRING_PROFILE_KEY_CREDENTIAL,
      PROFILE_SHARING,
      PROFILE_GIVEN_NAME,
      PROFILE_FAMILY_NAME,
      PROFILE_AVATAR,
      LAST_PROFILE_FETCH,
      SYSTEM_GIVEN_NAME,
      SYSTEM_FAMILY_NAME,
      SYSTEM_JOINED_NAME,
      SYSTEM_PHOTO_URI,
      SYSTEM_PHONE_LABEL,
      SYSTEM_PHONE_TYPE,
      SYSTEM_CONTACT_URI,
      NOTIFICATION_CHANNEL,
      MESSAGE_RINGTONE,
      MESSAGE_VIBRATE,
      CALL_RINGTONE,
      CALL_VIBRATE,
      MUTE_UNTIL,
      MESSAGE_EXPIRATION_TIME,
      SEALED_SENDER_MODE,
      STORAGE_SERVICE_ID,
      MENTION_SETTING,
      CAPABILITIES,
      WALLPAPER,
      WALLPAPER_URI,
      ABOUT,
      ABOUT_EMOJI,
      EXTRAS,
      GROUPS_IN_COMMON,
      AVATAR_COLOR,
      CHAT_COLORS,
      CUSTOM_CHAT_COLORS_ID,
      BADGES,
      NEEDS_PNI_SIGNATURE,
      REPORTING_TOKEN,
      PHONE_NUMBER_SHARING,
      NICKNAME_GIVEN_NAME,
      NICKNAME_FAMILY_NAME,
      NOTE
    )

    private val ID_PROJECTION = arrayOf(ID)

    private val SEARCH_PROJECTION = arrayOf(
      ID,
      SYSTEM_JOINED_NAME,
      E164,
      EMAIL,
      SYSTEM_PHONE_LABEL,
      SYSTEM_PHONE_TYPE,
      REGISTERED,
      ABOUT,
      ABOUT_EMOJI,
      EXTRAS,
      GROUPS_IN_COMMON,
      "COALESCE(NULLIF($PROFILE_JOINED_NAME, ''), NULLIF($PROFILE_GIVEN_NAME, '')) AS $SEARCH_PROFILE_NAME",
      """
      LOWER(
        COALESCE(
          NULLIF($NICKNAME_JOINED_NAME, ''),
          NULLIF($NICKNAME_GIVEN_NAME, ''),
          NULLIF($SYSTEM_JOINED_NAME, ''),
          NULLIF($SYSTEM_GIVEN_NAME, ''),
          NULLIF($PROFILE_JOINED_NAME, ''),
          NULLIF($PROFILE_GIVEN_NAME, ''),
          NULLIF($USERNAME, '')
        )
      ) AS $SORT_NAME
      """
    )

    @JvmField
    val SEARCH_PROJECTION_NAMES = arrayOf(
      ID,
      SYSTEM_JOINED_NAME,
      E164,
      EMAIL,
      SYSTEM_PHONE_LABEL,
      SYSTEM_PHONE_TYPE,
      REGISTERED,
      ABOUT,
      ABOUT_EMOJI,
      EXTRAS,
      GROUPS_IN_COMMON,
      SEARCH_PROFILE_NAME,
      SORT_NAME
    )

    private val TYPED_RECIPIENT_PROJECTION: Array<String> = RECIPIENT_PROJECTION
      .map { columnName -> "$TABLE_NAME.$columnName" }
      .toTypedArray()

    @JvmField
    val TYPED_RECIPIENT_PROJECTION_NO_ID: Array<String> = TYPED_RECIPIENT_PROJECTION.copyOfRange(1, TYPED_RECIPIENT_PROJECTION.size)

    private val MENTION_SEARCH_PROJECTION = arrayOf(
      ID,
      """
      REPLACE(
        COALESCE(
          NULLIF($NICKNAME_JOINED_NAME, ''),
          NULLIF($NICKNAME_GIVEN_NAME, ''),
          NULLIF($SYSTEM_JOINED_NAME, ''), 
          NULLIF($SYSTEM_GIVEN_NAME, ''), 
          NULLIF($PROFILE_JOINED_NAME, ''), 
          NULLIF($PROFILE_GIVEN_NAME, ''),
          NULLIF($USERNAME, ''),
          NULLIF($E164, '')
        ),
        ' ',
        ''
      ) AS $SORT_NAME
      """
    )

    /** Used as a placeholder recipient for self during migrations when self isn't yet available. */
    private val PLACEHOLDER_SELF_ID = -2L

    @JvmStatic
    fun maskCapabilitiesToLong(capabilities: SignalServiceProfile.Capabilities): Long {
      var value: Long = 0
      value = Bitmask.update(value, Capabilities.DELETE_SYNC, Capabilities.BIT_LENGTH, Recipient.Capability.fromBoolean(capabilities.isDeleteSync).serialize().toLong())
      return value
    }
  }

  fun getByE164(e164: String): Optional<RecipientId> {
    return getByColumn(E164, e164)
  }

  fun getByGroupId(groupId: GroupId): Optional<RecipientId> {
    return getByColumn(GROUP_ID, groupId.toString())
  }

  fun getByServiceId(serviceId: ServiceId): Optional<RecipientId> {
    return when (serviceId) {
      is ACI -> getByAci(serviceId)
      is PNI -> getByPni(serviceId)
    }
  }

  fun getByAci(aci: ACI): Optional<RecipientId> {
    return getByColumn(ACI_COLUMN, aci.toString())
  }

  fun getByPni(pni: PNI): Optional<RecipientId> {
    return getByColumn(PNI_COLUMN, pni.toString())
  }

  fun getByUsername(username: String): Optional<RecipientId> {
    return getByColumn(USERNAME, username)
  }

  fun getByCallLinkRoomId(callLinkRoomId: CallLinkRoomId): Optional<RecipientId> {
    return getByColumn(CALL_LINK_ROOM_ID, callLinkRoomId.serialize())
  }

  fun isAssociated(serviceId: ServiceId, pni: PNI): Boolean {
    return readableDatabase.exists(TABLE_NAME).where("$ACI_COLUMN = ? AND $PNI_COLUMN = ?", serviceId.toString(), pni.toString()).run()
  }

  fun getByE164IfRegisteredAndDiscoverable(e164: String): RecipientId? {
    return readableDatabase
      .select(ID)
      .from(TABLE_NAME)
      .where("$E164 = ? AND $REGISTERED = ${RegisteredState.REGISTERED.id} AND $PHONE_NUMBER_DISCOVERABLE = ${PhoneNumberDiscoverableState.DISCOVERABLE.id} AND ($PNI_COLUMN NOT NULL OR $ACI_COLUMN NOT NULL)", e164)
      .run()
      .readToSingleObject { RecipientId.from(it.requireLong(ID)) }
  }

  @JvmOverloads
  fun getAndPossiblyMerge(serviceId: ServiceId?, e164: String?, changeSelf: Boolean = false): RecipientId {
    require(serviceId != null || e164 != null) { "Must provide an ACI or E164!" }
    return when (serviceId) {
      is ACI -> getAndPossiblyMerge(aci = serviceId, pni = null, e164 = e164, pniVerified = false, changeSelf = changeSelf)
      is PNI -> getAndPossiblyMerge(aci = null, pni = serviceId, e164 = e164, pniVerified = false, changeSelf = changeSelf)
      else -> getAndPossiblyMerge(aci = null, pni = null, e164 = e164, pniVerified = false, changeSelf = changeSelf)
    }
  }

  /**
   * Gets and merges a (serviceId, pni, e164) tuple, doing merges/updates as needed, and giving you back the final RecipientId.
   * It is assumed that the tuple is verified. Do not give this method an untrusted association.
   */
  fun getAndPossiblyMergePnpVerified(aci: ACI?, pni: PNI?, e164: String?): RecipientId {
    return getAndPossiblyMerge(aci = aci, pni = pni, e164 = e164, pniVerified = true, changeSelf = false)
  }

  @VisibleForTesting
  fun getAndPossiblyMerge(aci: ACI?, pni: PNI?, e164: String?, pniVerified: Boolean = false, changeSelf: Boolean = false): RecipientId {
    require(aci != null || pni != null || e164 != null) { "Must provide an ACI, PNI, or E164!" }

    // To avoid opening a transaction and doing extra reads, we start with a single read that checks if all of the fields already match a single recipient
    val singleMatch: RecipientId? = getRecipientIdIfAllFieldsMatch(aci, pni, e164)
    if (singleMatch != null) {
      return singleMatch
    }

    Log.d(TAG, "[getAndPossiblyMerge] Requires a transaction.")

    val db = writableDatabase
    lateinit var result: ProcessPnpTupleResult

    db.withinTransaction {
      result = processPnpTuple(e164 = e164, pni = pni, aci = aci, pniVerified = pniVerified, changeSelf = changeSelf)

      if (result.operations.isNotEmpty() || result.requiredInsert) {
        Log.i(TAG, "[getAndPossiblyMerge] ($aci, $pni, $e164) BreadCrumbs: ${result.breadCrumbs}, Operations: ${result.operations}, RequiredInsert: ${result.requiredInsert}, FinalId: ${result.finalId}")
      }

      db.runPostSuccessfulTransaction {
        if (result.affectedIds.isNotEmpty()) {
          result.affectedIds.forEach { AppDependencies.databaseObserver.notifyRecipientChanged(it) }
          RetrieveProfileJob.enqueue(result.affectedIds)
        }

        if (result.oldIds.isNotEmpty()) {
          result.oldIds.forEach { oldId ->
            Recipient.live(oldId).refresh(result.finalId)
            AppDependencies.recipientCache.remap(oldId, result.finalId)
          }
        }

        if (result.affectedIds.isNotEmpty() || result.oldIds.isNotEmpty()) {
          StorageSyncHelper.scheduleSyncForDataChange()
          RecipientId.clearCache()
        }
      }
    }

    return result.finalId
  }

  fun getAllServiceIdProfileKeyPairs(): Map<ServiceId, ProfileKey> {
    val serviceIdToProfileKey: MutableMap<ServiceId, ProfileKey> = mutableMapOf()

    readableDatabase
      .select(ACI_COLUMN, PROFILE_KEY)
      .from(TABLE_NAME)
      .where("$ACI_COLUMN NOT NULL AND $PROFILE_KEY NOT NULL")
      .run()
      .use { cursor ->
        while (cursor.moveToNext()) {
          val aci: ACI? = ACI.parseOrNull(cursor.requireString(ACI_COLUMN))
          val profileKey: ProfileKey? = ProfileKeyUtil.profileKeyOrNull(cursor.requireString(PROFILE_KEY))

          if (aci != null && profileKey != null) {
            serviceIdToProfileKey[aci] = profileKey
          }
        }
      }

    return serviceIdToProfileKey
  }

  fun getOrInsertFromServiceId(serviceId: ServiceId): RecipientId {
    return getAndPossiblyMerge(serviceId = serviceId, e164 = null)
  }

  fun getOrInsertFromE164(e164: String): RecipientId {
    return getAndPossiblyMerge(serviceId = null, e164 = e164)
  }

  fun getOrInsertFromEmail(email: String): RecipientId {
    return getOrInsertByColumn(EMAIL, email).recipientId
  }

  @JvmOverloads
  fun getOrInsertFromDistributionListId(distributionListId: DistributionListId, storageId: ByteArray? = null): RecipientId {
    return getOrInsertByColumn(
      DISTRIBUTION_LIST_ID,
      distributionListId.serialize(),
      ContentValues().apply {
        put(TYPE, RecipientType.DISTRIBUTION_LIST.id)
        put(DISTRIBUTION_LIST_ID, distributionListId.serialize())
        put(STORAGE_SERVICE_ID, Base64.encodeWithPadding(storageId ?: StorageSyncHelper.generateKey()))
        put(PROFILE_SHARING, 1)
      }
    ).recipientId
  }

  fun getOrInsertFromCallLinkRoomId(callLinkRoomId: CallLinkRoomId): RecipientId {
    return getOrInsertByColumn(
      CALL_LINK_ROOM_ID,
      callLinkRoomId.serialize(),
      contentValuesOf(
        TYPE to RecipientType.CALL_LINK.id,
        CALL_LINK_ROOM_ID to callLinkRoomId.serialize(),
        PROFILE_SHARING to 1
      )
    ).recipientId
  }

  fun getDistributionListRecipientIds(): List<RecipientId> {
    val recipientIds = mutableListOf<RecipientId>()
    readableDatabase.query(TABLE_NAME, arrayOf(ID), "$DISTRIBUTION_LIST_ID is not NULL", null, null, null, null).use { cursor ->
      while (cursor != null && cursor.moveToNext()) {
        recipientIds.add(RecipientId.from(CursorUtil.requireLong(cursor, ID)))
      }
    }

    return recipientIds
  }

  fun getOrInsertFromGroupId(groupId: GroupId): RecipientId {
    var existing = getByGroupId(groupId)

    if (existing.isPresent) {
      return existing.get()
    } else if (groupId.isV1 && groups.groupExists(groupId.requireV1().deriveV2MigrationGroupId())) {
      throw LegacyGroupInsertException(groupId)
    } else {
      val values = ContentValues().apply {
        put(GROUP_ID, groupId.toString())
        put(AVATAR_COLOR, AvatarColorHash.forGroupId(groupId).serialize())
      }

      val id = writableDatabase.insert(TABLE_NAME, null, values)
      if (id < 0) {
        existing = getByColumn(GROUP_ID, groupId.toString())
        if (existing.isPresent) {
          return existing.get()
        } else if (groupId.isV1 && groups.groupExists(groupId.requireV1().deriveV2MigrationGroupId())) {
          throw LegacyGroupInsertException(groupId)
        } else {
          throw AssertionError("Failed to insert recipient!")
        }
      } else {
        val groupUpdates = ContentValues().apply {
          if (groupId.isMms) {
            put(TYPE, RecipientType.MMS.id)
          } else {
            if (groupId.isV2) {
              put(TYPE, RecipientType.GV2.id)
            } else {
              put(TYPE, RecipientType.GV1.id)
            }
            put(STORAGE_SERVICE_ID, Base64.encodeWithPadding(StorageSyncHelper.generateKey()))
          }
        }

        val recipientId = RecipientId.from(id)
        val updateSuccess = update(recipientId, groupUpdates)

        if (!updateSuccess) {
          Log.w(TAG, "Failed to update newly-created record for $recipientId")
        }

        Log.i(TAG, "Group $groupId was newly-inserted as $recipientId")

        return recipientId
      }
    }
  }

  /**
   * See [Recipient.externalPossiblyMigratedGroup].
   */
  fun getOrInsertFromPossiblyMigratedGroupId(groupId: GroupId): RecipientId {
    val db = writableDatabase
    db.beginTransaction()

    try {
      val existing = getByColumn(GROUP_ID, groupId.toString())
      if (existing.isPresent) {
        db.setTransactionSuccessful()
        return existing.get()
      }

      if (groupId.isV1) {
        val v2 = getByGroupId(groupId.requireV1().deriveV2MigrationGroupId())
        if (v2.isPresent) {
          db.setTransactionSuccessful()
          return v2.get()
        }
      }

      val id = getOrInsertFromGroupId(groupId)
      db.setTransactionSuccessful()
      return id
    } finally {
      db.endTransaction()
    }
  }

  fun getAll(): RecipientIterator {
    val cursor = readableDatabase
      .select()
      .from(TABLE_NAME)
      .run()

    return RecipientIterator(context, cursor)
  }

  /**
   * Only call once to create initial release channel recipient.
   */
  fun insertReleaseChannelRecipient(): RecipientId {
    val values = ContentValues().apply {
      put(AVATAR_COLOR, AvatarColor.random().serialize())
    }

    val id = writableDatabase.insert(TABLE_NAME, null, values)
    if (id < 0) {
      throw AssertionError("Failed to insert recipient!")
    } else {
      return GetOrInsertResult(RecipientId.from(id), true).recipientId
    }
  }

  fun getBlocked(): Cursor {
    return readableDatabase.query(TABLE_NAME, ID_PROJECTION, "$BLOCKED = 1", null, null, null, null)
  }

  fun readerForBlocked(cursor: Cursor): RecipientReader {
    return RecipientReader(cursor)
  }

  fun getRecipientsWithNotificationChannels(): RecipientReader {
    val cursor = readableDatabase.query(TABLE_NAME, ID_PROJECTION, "$NOTIFICATION_CHANNEL NOT NULL", null, null, null, null)
    return RecipientReader(cursor)
  }

  fun getRecords(ids: Collection<RecipientId>): Map<RecipientId, RecipientRecord> {
    val queries = SqlUtil.buildCollectionQuery(
      column = ID,
      values = ids.map { it.serialize() }
    )

    val foundRecords = queries.flatMap { query ->
      readableDatabase.query(TABLE_NAME, null, query.where, query.whereArgs, null, null, null).readToList { cursor ->
        RecipientTableCursorUtil.getRecord(context, cursor)
      }
    }

    val foundIds = foundRecords.map { record -> record.id }
    val remappedRecords = ids.filterNot { it in foundIds }.map(::findRemappedIdRecord)

    return (foundRecords + remappedRecords).associateBy { it.id }
  }

  fun getRecord(id: RecipientId): RecipientRecord {
    val query = "$ID = ?"
    val args = arrayOf(id.serialize())

    readableDatabase.query(TABLE_NAME, RECIPIENT_PROJECTION, query, args, null, null, null).use { cursor ->
      return if (cursor != null && cursor.moveToNext()) {
        RecipientTableCursorUtil.getRecord(context, cursor)
      } else {
        findRemappedIdRecord(id)
      }
    }
  }

  private fun findRemappedIdRecord(id: RecipientId): RecipientRecord {
    val remapped = RemappedRecords.getInstance().getRecipient(id)

    return if (remapped.isPresent) {
      Log.w(TAG, "Missing recipient for $id, but found it in the remapped records as ${remapped.get()}")
      getRecord(remapped.get())
    } else {
      throw MissingRecipientException(id)
    }
  }

  fun getRecordForSync(id: RecipientId): RecipientRecord? {
    val query = "$TABLE_NAME.$ID = ?"
    val args = arrayOf(id.serialize())
    val recordForSync = getRecordForSync(query, args)

    if (recordForSync.isEmpty()) {
      return null
    }

    if (recordForSync.size > 1) {
      throw AssertionError()
    }

    return recordForSync[0]
  }

  fun getByStorageId(storageId: ByteArray): RecipientRecord? {
    val result = getRecordForSync("$TABLE_NAME.$STORAGE_SERVICE_ID = ?", arrayOf(Base64.encodeWithPadding(storageId)))

    return if (result.isNotEmpty()) {
      result[0]
    } else {
      null
    }
  }

  fun markNeedsSyncWithoutRefresh(recipientIds: Collection<RecipientId>) {
    val db = writableDatabase
    db.beginTransaction()
    try {
      for (recipientId in recipientIds) {
        rotateStorageId(recipientId)
      }
      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }
  }

  fun markNeedsSync(recipientIds: Collection<RecipientId>) {
    writableDatabase
      .withinTransaction {
        for (recipientId in recipientIds) {
          markNeedsSync(recipientId)
        }
      }
  }

  fun markNeedsSync(recipientId: RecipientId) {
    rotateStorageId(recipientId)
    AppDependencies.databaseObserver.notifyRecipientChanged(recipientId)
  }

  fun markAllSystemContactsNeedsSync() {
    writableDatabase.withinTransaction { db ->
      db
        .select(ID)
        .from(TABLE_NAME)
        .where("$SYSTEM_CONTACT_URI NOT NULL")
        .run()
        .use { cursor ->
          while (cursor.moveToNext()) {
            rotateStorageId(RecipientId.from(cursor.requireLong(ID)))
          }
        }
    }
  }

  fun applyStorageIdUpdates(storageIds: Map<RecipientId, StorageId>) {
    val db = writableDatabase
    db.beginTransaction()
    try {
      val query = "$ID = ?"
      for ((key, value) in storageIds) {
        val values = ContentValues().apply {
          put(STORAGE_SERVICE_ID, Base64.encodeWithPadding(value.raw))
        }
        db.update(TABLE_NAME, values, query, arrayOf(key.serialize()))
      }
      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }

    for (id in storageIds.keys) {
      AppDependencies.databaseObserver.notifyRecipientChanged(id)
    }
  }

  fun applyStorageSyncContactInsert(insert: SignalContactRecord) {
    val db = writableDatabase
    val threadDatabase = threads
    val values = getValuesForStorageContact(insert, true)
    val id = db.insertWithOnConflict(TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_IGNORE)

    val recipientId: RecipientId
    if (id < 0) {
      Log.w(TAG, "[applyStorageSyncContactInsert] Failed to insert. Possibly merging.")
      recipientId = getAndPossiblyMerge(aci = insert.aci.orNull(), pni = insert.pni.orNull(), e164 = insert.number.orNull(), pniVerified = insert.isPniSignatureVerified)
      resolvePotentialUsernameConflicts(values.getAsString(USERNAME), recipientId)

      db.update(TABLE_NAME, values, ID_WHERE, SqlUtil.buildArgs(recipientId))
    } else {
      recipientId = RecipientId.from(id)
    }

    if (insert.identityKey.isPresent && (insert.aci.isPresent || insert.pni.isPresent)) {
      try {
        val serviceId: ServiceId = insert.aci.orNull() ?: insert.pni.get()
        val identityKey = IdentityKey(insert.identityKey.get(), 0)
        identities.updateIdentityAfterSync(serviceId.toString(), recipientId, identityKey, StorageSyncModels.remoteToLocalIdentityStatus(insert.identityState))
      } catch (e: InvalidKeyException) {
        Log.w(TAG, "Failed to process identity key during insert! Skipping.", e)
      }
    }

    updateExtras(recipientId) {
      it.hideStory(insert.shouldHideStory())
    }

    threadDatabase.applyStorageSyncUpdate(recipientId, insert)
  }

  fun applyStorageSyncContactUpdate(update: StorageRecordUpdate<SignalContactRecord>) {
    val db = writableDatabase
    val identityStore = AppDependencies.protocolStore.aci().identities()
    val values = getValuesForStorageContact(update.new, false)

    try {
      val updateCount = db.update(TABLE_NAME, values, "$STORAGE_SERVICE_ID = ?", arrayOf(Base64.encodeWithPadding(update.old.id.raw)))
      if (updateCount < 1) {
        throw AssertionError("Had an update, but it didn't match any rows!")
      }
    } catch (e: SQLiteConstraintException) {
      Log.w(TAG, "[applyStorageSyncContactUpdate] Failed to update a user by storageId.")
      var recipientId = getByColumn(STORAGE_SERVICE_ID, Base64.encodeWithPadding(update.old.id.raw)).get()

      Log.w(TAG, "[applyStorageSyncContactUpdate] Found user $recipientId. Possibly merging.")
      recipientId = getAndPossiblyMerge(aci = update.new.aci.orElse(null), pni = update.new.pni.orElse(null), e164 = update.new.number.orElse(null), pniVerified = update.new.isPniSignatureVerified)

      Log.w(TAG, "[applyStorageSyncContactUpdate] Merged into $recipientId")
      resolvePotentialUsernameConflicts(values.getAsString(USERNAME), recipientId)

      db.update(TABLE_NAME, values, ID_WHERE, SqlUtil.buildArgs(recipientId))
    }

    val recipientId = getByStorageKeyOrThrow(update.new.id.raw)
    if (StorageSyncHelper.profileKeyChanged(update)) {
      val clearValues = ContentValues(1).apply {
        putNull(EXPIRING_PROFILE_KEY_CREDENTIAL)
      }
      db.update(TABLE_NAME, clearValues, ID_WHERE, SqlUtil.buildArgs(recipientId))
    }

    try {
      val oldIdentityRecord = identityStore.getIdentityRecord(recipientId)
      if (update.new.identityKey.isPresent && update.new.aci.isPresent) {
        val identityKey = IdentityKey(update.new.identityKey.get(), 0)
        identities.updateIdentityAfterSync(update.new.aci.get().toString(), recipientId, identityKey, StorageSyncModels.remoteToLocalIdentityStatus(update.new.identityState))
      }

      val newIdentityRecord = identityStore.getIdentityRecord(recipientId)
      if (newIdentityRecord.isPresent && newIdentityRecord.get().verifiedStatus == VerifiedStatus.VERIFIED && (!oldIdentityRecord.isPresent || oldIdentityRecord.get().verifiedStatus != VerifiedStatus.VERIFIED)) {
        IdentityUtil.markIdentityVerified(context, Recipient.resolved(recipientId), true, true)
      } else if (newIdentityRecord.isPresent && newIdentityRecord.get().verifiedStatus != VerifiedStatus.VERIFIED && oldIdentityRecord.isPresent && oldIdentityRecord.get().verifiedStatus == VerifiedStatus.VERIFIED) {
        IdentityUtil.markIdentityVerified(context, Recipient.resolved(recipientId), false, true)
      }
    } catch (e: InvalidKeyException) {
      Log.w(TAG, "Failed to process identity key during update! Skipping.", e)
    }

    updateExtras(recipientId) {
      it.hideStory(update.new.shouldHideStory())
    }

    threads.applyStorageSyncUpdate(recipientId, update.new)
    AppDependencies.databaseObserver.notifyRecipientChanged(recipientId)
  }

  private fun resolvePotentialUsernameConflicts(username: String?, recipientId: RecipientId) {
    if (username != null) {
      writableDatabase
        .update(TABLE_NAME)
        .values(USERNAME to null)
        .where("$USERNAME = ? AND $ID != ?", username, recipientId.serialize())
        .run()
    }
  }

  fun applyStorageSyncGroupV1Insert(insert: SignalGroupV1Record) {
    val id = writableDatabase.insertOrThrow(TABLE_NAME, null, getValuesForStorageGroupV1(insert, true))

    val recipientId = RecipientId.from(id)
    threads.applyStorageSyncUpdate(recipientId, insert)
    AppDependencies.databaseObserver.notifyRecipientChanged(recipientId)
  }

  fun applyStorageSyncGroupV1Update(update: StorageRecordUpdate<SignalGroupV1Record>) {
    val values = getValuesForStorageGroupV1(update.new, false)

    val updateCount = writableDatabase.update(TABLE_NAME, values, STORAGE_SERVICE_ID + " = ?", arrayOf(Base64.encodeWithPadding(update.old.id.raw)))
    if (updateCount < 1) {
      throw AssertionError("Had an update, but it didn't match any rows!")
    }

    val recipient = Recipient.externalGroupExact(GroupId.v1orThrow(update.old.groupId))
    threads.applyStorageSyncUpdate(recipient.id, update.new)
    recipient.live().refresh()
  }

  fun applyStorageSyncGroupV2Insert(insert: SignalGroupV2Record) {
    val masterKey = insert.masterKeyOrThrow
    val groupId = GroupId.v2(masterKey)
    val values = getValuesForStorageGroupV2(insert, true)

    writableDatabase.insertOrThrow(TABLE_NAME, null, values)

    Log.i(TAG, "Creating restore placeholder for $groupId")
    val createdId = groups.create(
      groupMasterKey = masterKey,
      groupState = DecryptedGroup(revision = GroupsV2StateProcessor.RESTORE_PLACEHOLDER_REVISION),
      groupSendEndorsements = null
    )

    if (createdId == null) {
      Log.w(TAG, "Unable to create restore placeholder for $groupId, group already exists")
    }

    groups.setShowAsStoryState(groupId, insert.storySendMode.toShowAsStoryState())

    val recipient = Recipient.externalGroupExact(groupId)

    updateExtras(recipient.id) {
      it.hideStory(insert.shouldHideStory())
    }

    Log.i(TAG, "Scheduling request for latest group info for $groupId")
    AppDependencies.jobManager.add(RequestGroupV2InfoJob(groupId))
    threads.applyStorageSyncUpdate(recipient.id, insert)
    recipient.live().refresh()
  }

  fun applyStorageSyncGroupV2Update(update: StorageRecordUpdate<SignalGroupV2Record>) {
    val values = getValuesForStorageGroupV2(update.new, false)

    val updateCount = writableDatabase.update(TABLE_NAME, values, "$STORAGE_SERVICE_ID = ?", arrayOf(Base64.encodeWithPadding(update.old.id.raw)))
    if (updateCount < 1) {
      throw AssertionError("Had an update, but it didn't match any rows!")
    }

    val masterKey = update.old.masterKeyOrThrow
    val groupId = GroupId.v2(masterKey)
    val recipient = Recipient.externalGroupExact(groupId)

    updateExtras(recipient.id) {
      it.hideStory(update.new.shouldHideStory())
    }

    groups.setShowAsStoryState(groupId, update.new.storySendMode.toShowAsStoryState())
    threads.applyStorageSyncUpdate(recipient.id, update.new)
    recipient.live().refresh()
  }

  fun applyStorageSyncAccountUpdate(update: StorageRecordUpdate<SignalAccountRecord>) {
    val profileName = ProfileName.fromParts(update.new.givenName.orElse(null), update.new.familyName.orElse(null))
    val localKey = ProfileKeyUtil.profileKeyOptional(update.old.profileKey.orElse(null))
    val remoteKey = ProfileKeyUtil.profileKeyOptional(update.new.profileKey.orElse(null))
    val profileKey: String? = remoteKey.or(localKey).map { obj: ProfileKey -> obj.serialize() }.map { source: ByteArray? -> Base64.encodeWithPadding(source!!) }.orElse(null)
    if (!remoteKey.isPresent) {
      Log.w(TAG, "Got an empty profile key while applying an account record update! The parsed local key is ${if (localKey.isPresent) "present" else "not present"}. The raw local key is ${if (update.old.profileKey.isPresent) "present" else "not present"}. The resulting key is ${if (profileKey != null) "present" else "not present"}.")
    }

    val values = ContentValues().apply {
      put(PROFILE_GIVEN_NAME, profileName.givenName)
      put(PROFILE_FAMILY_NAME, profileName.familyName)
      put(PROFILE_JOINED_NAME, profileName.toString())

      if (profileKey != null) {
        put(PROFILE_KEY, profileKey)
      } else {
        Log.w(TAG, "Avoided attempt to apply null profile key in account record update!")
      }

      put(USERNAME, update.new.username)
      put(STORAGE_SERVICE_ID, Base64.encodeWithPadding(update.new.id.raw))

      if (update.new.hasUnknownFields()) {
        put(STORAGE_SERVICE_PROTO, Base64.encodeWithPadding(Objects.requireNonNull(update.new.serializeUnknownFields())))
      } else {
        putNull(STORAGE_SERVICE_PROTO)
      }
    }

    if (update.new.username != null) {
      writableDatabase
        .update(TABLE_NAME)
        .values(USERNAME to null)
        .where("$USERNAME = ?", update.new.username!!)
        .run()
    }

    val updateCount = writableDatabase.update(TABLE_NAME, values, "$STORAGE_SERVICE_ID = ?", arrayOf(Base64.encodeWithPadding(update.old.id.raw)))
    if (updateCount < 1) {
      throw AssertionError("Account update didn't match any rows!")
    }

    if (remoteKey != localKey) {
      Log.i(TAG, "Our own profile key was changed during a storage sync.", Throwable())
      runPostSuccessfulTransaction { ProfileUtil.handleSelfProfileKeyChange() }
    }

    threads.applyStorageSyncUpdate(Recipient.self().id, update.new)
    Recipient.self().live().refresh()
  }

  /**
   * Removes storageIds from unregistered recipients who were unregistered more than [UNREGISTERED_LIFESPAN] ago.
   * @return The number of rows affected.
   */
  fun removeStorageIdsFromOldUnregisteredRecipients(now: Long): Int {
    return writableDatabase
      .update(TABLE_NAME)
      .values(STORAGE_SERVICE_ID to null)
      .where("$STORAGE_SERVICE_ID NOT NULL AND $UNREGISTERED_TIMESTAMP > 0 AND $UNREGISTERED_TIMESTAMP < ?", now - UNREGISTERED_LIFESPAN)
      .run()
  }

  /**
   * Removes storageIds from unregistered contacts that have storageIds in the provided collection.
   * @return The number of updated rows.
   */
  fun removeStorageIdsFromLocalOnlyUnregisteredRecipients(storageIds: Collection<StorageId>): Int {
    val values = contentValuesOf(STORAGE_SERVICE_ID to null)
    var updated = 0

    SqlUtil.buildCollectionQuery(STORAGE_SERVICE_ID, storageIds.map { Base64.encodeWithPadding(it.raw) }, "$UNREGISTERED_TIMESTAMP > 0 AND")
      .forEach {
        updated += writableDatabase.update(TABLE_NAME, values, it.where, it.whereArgs)
      }

    return updated
  }

  /**
   * Takes a mapping of old->new phone numbers and updates the table to match.
   * Intended to be used to handle changing number formats.
   */
  fun rewritePhoneNumbers(mapping: Map<String, String>) {
    if (mapping.isEmpty()) return

    Log.i(TAG, "Rewriting ${mapping.size} phone numbers.")

    writableDatabase.withinTransaction {
      for ((originalE164, updatedE164) in mapping) {
        writableDatabase.update(TABLE_NAME)
          .values(E164 to updatedE164)
          .where("$E164 = ?", originalE164)
          .run(SQLiteDatabase.CONFLICT_IGNORE)
      }
    }
  }

  private fun getByStorageKeyOrThrow(storageKey: ByteArray): RecipientId {
    val query = "$STORAGE_SERVICE_ID = ?"
    val args = arrayOf(Base64.encodeWithPadding(storageKey))

    readableDatabase.query(TABLE_NAME, ID_PROJECTION, query, args, null, null, null).use { cursor ->
      return if (cursor != null && cursor.moveToFirst()) {
        val id = cursor.getLong(cursor.getColumnIndexOrThrow(ID))
        RecipientId.from(id)
      } else {
        throw AssertionError("No recipient with that storage key!")
      }
    }
  }

  private fun GroupV2Record.StorySendMode.toShowAsStoryState(): ShowAsStoryState {
    return when (this) {
      GroupV2Record.StorySendMode.DEFAULT -> ShowAsStoryState.IF_ACTIVE
      GroupV2Record.StorySendMode.DISABLED -> ShowAsStoryState.NEVER
      GroupV2Record.StorySendMode.ENABLED -> ShowAsStoryState.ALWAYS
      else -> ShowAsStoryState.IF_ACTIVE
    }
  }

  private fun getRecordForSync(query: String?, args: Array<String>?): List<RecipientRecord> {
    val table =
      """
      $TABLE_NAME LEFT OUTER JOIN ${IdentityTable.TABLE_NAME} ON ($TABLE_NAME.$ACI_COLUMN = ${IdentityTable.TABLE_NAME}.${IdentityTable.ADDRESS} OR ($TABLE_NAME.$ACI_COLUMN IS NULL AND $TABLE_NAME.$PNI_COLUMN = ${IdentityTable.TABLE_NAME}.${IdentityTable.ADDRESS}))
                  LEFT OUTER JOIN ${GroupTable.TABLE_NAME} ON $TABLE_NAME.$GROUP_ID = ${GroupTable.TABLE_NAME}.${GroupTable.GROUP_ID} 
                  LEFT OUTER JOIN ${ThreadTable.TABLE_NAME} ON $TABLE_NAME.$ID = ${ThreadTable.TABLE_NAME}.${ThreadTable.RECIPIENT_ID}
      """
    val out: MutableList<RecipientRecord> = ArrayList()
    val columns: Array<String> = TYPED_RECIPIENT_PROJECTION + arrayOf(
      SYSTEM_NICKNAME,
      "$TABLE_NAME.$STORAGE_SERVICE_PROTO",
      "$TABLE_NAME.$UNREGISTERED_TIMESTAMP",
      "$TABLE_NAME.$PNI_SIGNATURE_VERIFIED",
      "${GroupTable.TABLE_NAME}.${GroupTable.V2_MASTER_KEY}",
      "${ThreadTable.TABLE_NAME}.${ThreadTable.ARCHIVED}",
      "${ThreadTable.TABLE_NAME}.${ThreadTable.READ}",
      "${IdentityTable.TABLE_NAME}.${IdentityTable.VERIFIED} AS $IDENTITY_STATUS",
      "${IdentityTable.TABLE_NAME}.${IdentityTable.IDENTITY_KEY} AS $IDENTITY_KEY"
    )

    readableDatabase.query(table, columns, query, args, "$TABLE_NAME.$ID", null, null).use { cursor ->
      while (cursor != null && cursor.moveToNext()) {
        out.add(RecipientTableCursorUtil.getRecord(context, cursor))
      }
    }

    return out
  }

  /**
   * @return All storage ids for ContactRecords, excluding the ones that need to be deleted.
   */
  fun getContactStorageSyncIds(): List<StorageId> {
    return ArrayList(getContactStorageSyncIdsMap().values)
  }

  /**
   * @return All storage IDs for synced records, excluding the ones that need to be deleted.
   */
  fun getContactStorageSyncIdsMap(): Map<RecipientId, StorageId> {
    val out: MutableMap<RecipientId, StorageId> = HashMap()

    readableDatabase
      .select(ID, STORAGE_SERVICE_ID, TYPE)
      .from(TABLE_NAME)
      .where(
        """
        $STORAGE_SERVICE_ID NOT NULL AND (
            ($TYPE = ? AND ($ACI_COLUMN NOT NULL OR $PNI_COLUMN NOT NULL) AND $ID != ?)
            OR
            $TYPE = ?
            OR
            $DISTRIBUTION_LIST_ID NOT NULL AND $DISTRIBUTION_LIST_ID IN (
              SELECT ${DistributionListTables.ListTable.ID}
              FROM ${DistributionListTables.ListTable.TABLE_NAME}
            )
        )
        """,
        RecipientType.INDIVIDUAL.id,
        Recipient.self().id,
        RecipientType.GV1.id
      )
      .run()
      .use { cursor ->
        while (cursor.moveToNext()) {
          val id = RecipientId.from(cursor.requireLong(ID))
          val encodedKey = cursor.requireNonNullString(STORAGE_SERVICE_ID)
          val recipientType = RecipientType.fromId(cursor.requireInt(TYPE))
          val key = Base64.decodeOrThrow(encodedKey)

          when (recipientType) {
            RecipientType.INDIVIDUAL -> out[id] = StorageId.forContact(key)
            RecipientType.GV1 -> out[id] = StorageId.forGroupV1(key)
            RecipientType.DISTRIBUTION_LIST -> out[id] = StorageId.forStoryDistributionList(key)
            else -> throw AssertionError()
          }
        }
      }

    for (id in groups.getAllGroupV2Ids()) {
      val recipient = Recipient.externalGroupExact(id)
      val recipientId = recipient.id
      val existing: RecipientRecord = getRecordForSync(recipientId) ?: throw AssertionError()
      val key = existing.storageId ?: throw AssertionError()
      out[recipientId] = StorageId.forGroupV2(key)
    }

    return out
  }

  /**
   * Given a collection of [RecipientId]s, this will do an efficient bulk query to find all matching E164s.
   * If one cannot be found, no error thrown, it will just be omitted.
   */
  fun getE164sForIds(ids: Collection<RecipientId>): Set<String> {
    val queries: List<SqlUtil.Query> = SqlUtil.buildCustomCollectionQuery(
      "$ID = ?",
      ids.map { arrayOf(it.serialize()) }.toList()
    )

    val out: MutableSet<String> = mutableSetOf()

    for (query in queries) {
      readableDatabase.query(TABLE_NAME, arrayOf(E164), query.where, query.whereArgs, null, null, null).use { cursor ->
        while (cursor.moveToNext()) {
          val e164: String? = cursor.requireString(E164)
          if (e164 != null) {
            out.add(e164)
          }
        }
      }
    }

    return out
  }

  /**
   * @param clearInfoForMissingContacts If true, this will clear any saved contact details for any recipient that hasn't been updated
   *                                    by the time finish() is called. Basically this should be true for full syncs and false for
   *                                    partial syncs.
   */
  fun beginBulkSystemContactUpdate(clearInfoForMissingContacts: Boolean): BulkOperationsHandle {
    writableDatabase.beginTransaction()

    if (clearInfoForMissingContacts) {
      writableDatabase
        .update(TABLE_NAME)
        .values(SYSTEM_INFO_PENDING to 1)
        .where("$SYSTEM_CONTACT_URI NOT NULL")
        .run()
    }

    return BulkOperationsHandle(writableDatabase)
  }

  fun onUpdatedChatColors(chatColors: ChatColors) {
    val where = "$CUSTOM_CHAT_COLORS_ID = ?"
    val args = SqlUtil.buildArgs(chatColors.id.longValue)
    val updated: MutableList<RecipientId> = LinkedList()

    readableDatabase.query(TABLE_NAME, SqlUtil.buildArgs(ID), where, args, null, null, null).use { cursor ->
      while (cursor != null && cursor.moveToNext()) {
        updated.add(RecipientId.from(cursor.requireLong(ID)))
      }
    }

    if (updated.isEmpty()) {
      Log.d(TAG, "No recipients utilizing updated chat color.")
    } else {
      val values = ContentValues(2).apply {
        put(CHAT_COLORS, chatColors.serialize().encode())
        put(CUSTOM_CHAT_COLORS_ID, chatColors.id.longValue)
      }

      writableDatabase.update(TABLE_NAME, values, where, args)

      for (recipientId in updated) {
        AppDependencies.databaseObserver.notifyRecipientChanged(recipientId)
      }
    }
  }

  fun onDeletedChatColors(chatColors: ChatColors) {
    val where = "$CUSTOM_CHAT_COLORS_ID = ?"
    val args = SqlUtil.buildArgs(chatColors.id.longValue)
    val updated: MutableList<RecipientId> = LinkedList()

    readableDatabase.query(TABLE_NAME, SqlUtil.buildArgs(ID), where, args, null, null, null).use { cursor ->
      while (cursor != null && cursor.moveToNext()) {
        updated.add(RecipientId.from(cursor.requireLong(ID)))
      }
    }

    if (updated.isEmpty()) {
      Log.d(TAG, "No recipients utilizing deleted chat color.")
    } else {
      val values = ContentValues(2).apply {
        put(CHAT_COLORS, null as ByteArray?)
        put(CUSTOM_CHAT_COLORS_ID, ChatColors.Id.NotSet.longValue)
      }

      writableDatabase.update(TABLE_NAME, values, where, args)

      for (recipientId in updated) {
        AppDependencies.databaseObserver.notifyRecipientChanged(recipientId)
      }
    }
  }

  fun getColorUsageCount(chatColorsId: ChatColors.Id): Int {
    val where = "$CUSTOM_CHAT_COLORS_ID = ?"
    val args = SqlUtil.buildArgs(chatColorsId.longValue)

    readableDatabase.query(TABLE_NAME, arrayOf("COUNT(*)"), where, args, null, null, null).use { cursor ->
      return if (cursor.moveToFirst()) {
        cursor.getInt(0)
      } else {
        0
      }
    }
  }

  fun clearAllColors() {
    val database = writableDatabase
    val where = "$CUSTOM_CHAT_COLORS_ID != ?"
    val args = SqlUtil.buildArgs(ChatColors.Id.NotSet.longValue)
    val toUpdate: MutableList<RecipientId> = LinkedList()

    database.query(TABLE_NAME, SqlUtil.buildArgs(ID), where, args, null, null, null).use { cursor ->
      while (cursor != null && cursor.moveToNext()) {
        toUpdate.add(RecipientId.from(cursor.requireLong(ID)))
      }
    }

    if (toUpdate.isEmpty()) {
      return
    }

    val values = ContentValues().apply {
      put(CHAT_COLORS, null as ByteArray?)
      put(CUSTOM_CHAT_COLORS_ID, ChatColors.Id.NotSet.longValue)
    }
    database.update(TABLE_NAME, values, where, args)

    for (id in toUpdate) {
      AppDependencies.databaseObserver.notifyRecipientChanged(id)
    }
  }

  fun clearColor(id: RecipientId) {
    val values = ContentValues().apply {
      put(CHAT_COLORS, null as ByteArray?)
      put(CUSTOM_CHAT_COLORS_ID, ChatColors.Id.NotSet.longValue)
    }
    if (update(id, values)) {
      AppDependencies.databaseObserver.notifyRecipientChanged(id)
    }
  }

  fun setColor(id: RecipientId, color: ChatColors) {
    val values = ContentValues().apply {
      put(CHAT_COLORS, color.serialize().encode())
      put(CUSTOM_CHAT_COLORS_ID, color.id.longValue)
    }
    if (update(id, values)) {
      AppDependencies.databaseObserver.notifyRecipientChanged(id)
    }
  }

  fun setBlocked(id: RecipientId, blocked: Boolean) {
    val values = ContentValues().apply {
      put(BLOCKED, if (blocked) 1 else 0)
    }
    if (update(id, values)) {
      rotateStorageId(id)
      AppDependencies.databaseObserver.notifyRecipientChanged(id)
    }
  }

  fun setMessageRingtone(id: RecipientId, notification: Uri?) {
    val values = ContentValues().apply {
      put(MESSAGE_RINGTONE, notification?.toString())
    }
    if (update(id, values)) {
      AppDependencies.databaseObserver.notifyRecipientChanged(id)
    }
  }

  fun setCallRingtone(id: RecipientId, ringtone: Uri?) {
    val values = ContentValues().apply {
      put(CALL_RINGTONE, ringtone?.toString())
    }
    if (update(id, values)) {
      AppDependencies.databaseObserver.notifyRecipientChanged(id)
    }
  }

  fun setMessageVibrate(id: RecipientId, enabled: VibrateState) {
    val values = ContentValues().apply {
      put(MESSAGE_VIBRATE, enabled.id)
    }
    if (update(id, values)) {
      AppDependencies.databaseObserver.notifyRecipientChanged(id)
    }
  }

  fun setCallVibrate(id: RecipientId, enabled: VibrateState) {
    val values = ContentValues().apply {
      put(CALL_VIBRATE, enabled.id)
    }
    if (update(id, values)) {
      AppDependencies.databaseObserver.notifyRecipientChanged(id)
    }
  }

  fun setMuted(id: RecipientId, until: Long) {
    val values = ContentValues().apply {
      put(MUTE_UNTIL, until)
    }

    if (update(id, values)) {
      rotateStorageId(id)
      AppDependencies.databaseObserver.notifyRecipientChanged(id)
    }

    StorageSyncHelper.scheduleSyncForDataChange()
  }

  fun setMuted(ids: Collection<RecipientId>, until: Long) {
    val db = writableDatabase

    db.beginTransaction()
    try {
      val query = SqlUtil.buildSingleCollectionQuery(ID, ids)
      val values = ContentValues().apply {
        put(MUTE_UNTIL, until)
      }

      db.update(TABLE_NAME, values, query.where, query.whereArgs)
      for (id in ids) {
        rotateStorageId(id)
      }

      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }

    for (id in ids) {
      AppDependencies.databaseObserver.notifyRecipientChanged(id)
    }

    StorageSyncHelper.scheduleSyncForDataChange()
  }

  fun setExpireMessages(id: RecipientId, expiration: Int) {
    val values = ContentValues(1).apply {
      put(MESSAGE_EXPIRATION_TIME, expiration)
    }
    if (update(id, values)) {
      AppDependencies.databaseObserver.notifyRecipientChanged(id)
    }
  }

  fun setSealedSenderAccessMode(id: RecipientId, sealedSenderAccessMode: SealedSenderAccessMode) {
    val values = ContentValues(1).apply {
      put(SEALED_SENDER_MODE, sealedSenderAccessMode.mode)
    }
    if (update(id, values)) {
      AppDependencies.databaseObserver.notifyRecipientChanged(id)
    }
  }

  fun setLastSessionResetTime(id: RecipientId, lastResetTime: DeviceLastResetTime) {
    val values = ContentValues(1).apply {
      put(LAST_SESSION_RESET, lastResetTime.encode())
    }
    update(id, values)
  }

  fun getLastSessionResetTimes(id: RecipientId): DeviceLastResetTime {
    readableDatabase.query(TABLE_NAME, arrayOf(LAST_SESSION_RESET), ID_WHERE, SqlUtil.buildArgs(id), null, null, null).use { cursor ->
      if (cursor.moveToFirst()) {
        return try {
          val serialized = cursor.requireBlob(LAST_SESSION_RESET)
          if (serialized != null) {
            DeviceLastResetTime.ADAPTER.decode(serialized)
          } else {
            DeviceLastResetTime()
          }
        } catch (e: IOException) {
          Log.w(TAG, e)
          DeviceLastResetTime()
        }
      }
    }

    return DeviceLastResetTime()
  }

  fun setBadges(id: RecipientId, badges: List<Badge>) {
    val badgeList = BadgeList(badges = badges.map { toDatabaseBadge(it) })

    val values = ContentValues(1).apply {
      put(BADGES, badgeList.encode())
    }

    if (update(id, values)) {
      AppDependencies.databaseObserver.notifyRecipientChanged(id)
    }
  }

  fun setCapabilities(id: RecipientId, capabilities: SignalServiceProfile.Capabilities) {
    val values = ContentValues(1).apply {
      put(CAPABILITIES, maskCapabilitiesToLong(capabilities))
    }

    if (update(id, values)) {
      AppDependencies.databaseObserver.notifyRecipientChanged(id)
    }
  }

  fun setMentionSetting(id: RecipientId, mentionSetting: MentionSetting) {
    val values = ContentValues().apply {
      put(MENTION_SETTING, mentionSetting.id)
    }
    if (update(id, values)) {
      rotateStorageId(id)
      AppDependencies.databaseObserver.notifyRecipientChanged(id)
      StorageSyncHelper.scheduleSyncForDataChange()
    }
  }

  /**
   * Updates the profile key.
   *
   * If it changes, it clears out the profile key credential and resets the unidentified access mode.
   * @return true iff changed.
   */
  fun setProfileKey(id: RecipientId, profileKey: ProfileKey): Boolean {
    val selection = "$ID = ?"
    val args = arrayOf(id.serialize())
    val encodedProfileKey = Base64.encodeWithPadding(profileKey.serialize())
    val valuesToCompare = ContentValues(1).apply {
      put(PROFILE_KEY, encodedProfileKey)
    }
    val valuesToSet = ContentValues(3).apply {
      put(PROFILE_KEY, encodedProfileKey)
      putNull(EXPIRING_PROFILE_KEY_CREDENTIAL)
      put(SEALED_SENDER_MODE, SealedSenderAccessMode.UNKNOWN.mode)
    }

    val updateQuery = SqlUtil.buildTrueUpdateQuery(selection, args, valuesToCompare)

    if (update(updateQuery, valuesToSet)) {
      rotateStorageId(id)
      AppDependencies.databaseObserver.notifyRecipientChanged(id)
      StorageSyncHelper.scheduleSyncForDataChange()

      if (id == Recipient.self().id) {
        Log.i(TAG, "Our own profile key was changed.", Throwable())
        runPostSuccessfulTransaction { ProfileUtil.handleSelfProfileKeyChange() }
      }

      return true
    }
    return false
  }

  /**
   * Sets the profile key iff currently null.
   *
   * If it sets it, it also clears out the profile key credential and resets the unidentified access mode.
   * @return true iff changed.
   */
  fun setProfileKeyIfAbsent(id: RecipientId, profileKey: ProfileKey): Boolean {
    val selection = "$ID = ? AND $PROFILE_KEY is NULL"
    val args = arrayOf(id.serialize())
    val valuesToSet = ContentValues(3).apply {
      put(PROFILE_KEY, Base64.encodeWithPadding(profileKey.serialize()))
      putNull(EXPIRING_PROFILE_KEY_CREDENTIAL)
      put(SEALED_SENDER_MODE, SealedSenderAccessMode.UNKNOWN.mode)
    }

    if (writableDatabase.update(TABLE_NAME, valuesToSet, selection, args) > 0) {
      rotateStorageId(id)
      AppDependencies.databaseObserver.notifyRecipientChanged(id)
      return true
    } else {
      return false
    }
  }

  /**
   * Updates the profile key credential as long as the profile key matches.
   */
  fun setProfileKeyCredential(
    id: RecipientId,
    profileKey: ProfileKey,
    expiringProfileKeyCredential: ExpiringProfileKeyCredential
  ): Boolean {
    val selection = "$ID = ? AND $PROFILE_KEY = ?"
    val args = arrayOf(id.serialize(), Base64.encodeWithPadding(profileKey.serialize()))
    val columnData = ExpiringProfileKeyCredentialColumnData.Builder()
      .profileKey(profileKey.serialize().toByteString())
      .expiringProfileKeyCredential(expiringProfileKeyCredential.serialize().toByteString())
      .build()
    val values = ContentValues(1).apply {
      put(EXPIRING_PROFILE_KEY_CREDENTIAL, Base64.encodeWithPadding(columnData.encode()))
    }
    val updateQuery = SqlUtil.buildTrueUpdateQuery(selection, args, values)

    val updated = update(updateQuery, values)
    if (updated) {
      AppDependencies.databaseObserver.notifyRecipientChanged(id)
    }

    return updated
  }

  fun clearProfileKeyCredential(id: RecipientId) {
    val values = ContentValues(1)
    values.putNull(EXPIRING_PROFILE_KEY_CREDENTIAL)
    if (update(id, values)) {
      AppDependencies.databaseObserver.notifyRecipientChanged(id)
    }
  }

  /**
   * Fills in gaps (nulls) in profile key knowledge from new profile keys.
   *
   *
   * If from authoritative source, this will overwrite local, otherwise it will only write to the
   * database if missing.
   */
  fun persistProfileKeySet(profileKeySet: ProfileKeySet): Set<RecipientId> {
    val profileKeys = profileKeySet.profileKeys
    val authoritativeProfileKeys = profileKeySet.authoritativeProfileKeys
    val totalKeys = profileKeys.size + authoritativeProfileKeys.size

    if (totalKeys == 0) {
      return emptySet()
    }

    Log.i(TAG, "Persisting $totalKeys Profile keys, ${authoritativeProfileKeys.size} of which are authoritative")

    val updated = HashSet<RecipientId>(totalKeys)
    val selfId = Recipient.self().id

    for ((key, value) in profileKeys) {
      val recipientId = getOrInsertFromServiceId(key)
      if (setProfileKeyIfAbsent(recipientId, value)) {
        Log.i(TAG, "Learned new profile key")
        updated.add(recipientId)
      }
    }

    for ((key, value) in authoritativeProfileKeys) {
      val recipientId = getOrInsertFromServiceId(key)

      if (selfId == recipientId) {
        Log.i(TAG, "Seen authoritative update for self")
        if (value != ProfileKeyUtil.getSelfProfileKey()) {
          Log.w(TAG, "Seen authoritative update for self that didn't match local, scheduling storage sync")
          StorageSyncHelper.scheduleSyncForDataChange()
        }
      } else {
        Log.i(TAG, "Profile key from owner $recipientId")
        if (setProfileKey(recipientId, value)) {
          Log.i(TAG, "Learned new profile key from owner")
          updated.add(recipientId)
        }
      }
    }

    return updated
  }

  fun containsId(id: RecipientId): Boolean {
    return readableDatabase
      .exists(TABLE_NAME)
      .where("$ID = ?", id.serialize())
      .run()
  }

  fun setReportingToken(id: RecipientId, reportingToken: ByteArray) {
    val values = ContentValues(1).apply {
      put(REPORTING_TOKEN, reportingToken)
    }

    if (update(id, values)) {
      AppDependencies.databaseObserver.notifyRecipientChanged(id)
    }
  }

  fun getReportingToken(id: RecipientId): ByteArray? {
    readableDatabase
      .select(REPORTING_TOKEN)
      .from(TABLE_NAME)
      .where(ID_WHERE, id)
      .run()
      .use { cursor ->
        if (cursor.moveToFirst()) {
          return cursor.requireBlob(REPORTING_TOKEN)
        } else {
          return null
        }
      }
  }

  fun getSimilarRecipientIds(recipient: Recipient): List<RecipientId> {
    if (!recipient.nickname.isEmpty || recipient.isSystemContact) {
      return emptyList()
    }

    val threadId = threads.getThreadIdFor(recipient.id)
    val isMessageRequestAccepted = RecipientUtil.isMessageRequestAccepted(threadId, recipient)
    if (isMessageRequestAccepted) {
      return emptyList()
    }

    val glob = SqlUtil.buildCaseInsensitiveGlobPattern(recipient.profileName.toString())
    val projection = SqlUtil.buildArgs(ID, "COALESCE(NULLIF($NICKNAME_JOINED_NAME, ''), NULLIF($SYSTEM_JOINED_NAME, ''), NULLIF($PROFILE_JOINED_NAME, '')) AS checked_name")
    val where = "checked_name GLOB ? AND $HIDDEN = ? AND $BLOCKED = ?"
    val arguments = SqlUtil.buildArgs(glob, 0, 0)

    readableDatabase.query(TABLE_NAME, projection, where, arguments, null, null, null).use { cursor ->
      if (cursor == null || cursor.count == 0) {
        return emptyList()
      }
      val results: MutableList<RecipientId> = ArrayList(cursor.count)
      while (cursor.moveToNext()) {
        results.add(RecipientId.from(cursor.requireLong(ID)))
      }
      return results
    }
  }

  fun setSystemContactName(id: RecipientId, systemContactName: String) {
    val values = ContentValues().apply {
      put(SYSTEM_JOINED_NAME, systemContactName)
    }
    if (update(id, values)) {
      AppDependencies.databaseObserver.notifyRecipientChanged(id)
    }
  }

  fun setNicknameAndNote(id: RecipientId, nickname: ProfileName, note: String) {
    val contentValues = contentValuesOf(
      NICKNAME_GIVEN_NAME to nickname.givenName.nullIfBlank(),
      NICKNAME_FAMILY_NAME to nickname.familyName.nullIfBlank(),
      NICKNAME_JOINED_NAME to nickname.toString().nullIfBlank(),
      NOTE to note.nullIfBlank()
    )
    if (update(id, contentValues)) {
      rotateStorageId(id)
      AppDependencies.databaseObserver.notifyRecipientChanged(id)
      StorageSyncHelper.scheduleSyncForDataChange()
    }
  }

  fun setProfileName(id: RecipientId, profileName: ProfileName) {
    val contentValues = ContentValues(1).apply {
      put(PROFILE_GIVEN_NAME, profileName.givenName.nullIfBlank())
      put(PROFILE_FAMILY_NAME, profileName.familyName.nullIfBlank())
      put(PROFILE_JOINED_NAME, profileName.toString().nullIfBlank())
    }
    if (update(id, contentValues)) {
      rotateStorageId(id)
      AppDependencies.databaseObserver.notifyRecipientChanged(id)
      StorageSyncHelper.scheduleSyncForDataChange()
    }
  }

  fun setProfileAvatar(id: RecipientId, profileAvatar: String?) {
    val contentValues = ContentValues(1).apply {
      put(PROFILE_AVATAR, profileAvatar)
    }
    if (update(id, contentValues)) {
      AppDependencies.databaseObserver.notifyRecipientChanged(id)
      if (id == Recipient.self().id) {
        rotateStorageId(id)
        StorageSyncHelper.scheduleSyncForDataChange()
      }
    }
  }

  fun setAbout(id: RecipientId, about: String?, emoji: String?) {
    val contentValues = ContentValues().apply {
      put(ABOUT, about)
      put(ABOUT_EMOJI, emoji)
    }

    if (update(id, contentValues)) {
      AppDependencies.databaseObserver.notifyRecipientChanged(id)
    }
  }

  fun markHidden(id: RecipientId, clearProfileKey: Boolean = false, showMessageRequest: Boolean = false) {
    val contentValues = if (clearProfileKey) {
      contentValuesOf(
        HIDDEN to if (showMessageRequest) Recipient.HiddenState.HIDDEN_MESSAGE_REQUEST.serialize() else Recipient.HiddenState.HIDDEN.serialize(),
        PROFILE_SHARING to 0,
        PROFILE_KEY to null
      )
    } else {
      contentValuesOf(
        HIDDEN to if (showMessageRequest) Recipient.HiddenState.HIDDEN_MESSAGE_REQUEST.serialize() else Recipient.HiddenState.HIDDEN.serialize(),
        PROFILE_SHARING to 0
      )
    }

    val updated = writableDatabase.update(TABLE_NAME, contentValues, "$ID_WHERE AND $TYPE = ?", SqlUtil.buildArgs(id, RecipientType.INDIVIDUAL.id)) > 0
    if (updated) {
      SignalDatabase.distributionLists.removeMemberFromAllLists(id)
      SignalDatabase.messages.deleteStoriesForRecipient(id)
      rotateStorageId(id)
      AppDependencies.databaseObserver.notifyRecipientChanged(id)
      StorageSyncHelper.scheduleSyncForDataChange()
    } else {
      Log.w(TAG, "Failed to hide recipient $id")
    }
  }

  fun setProfileSharing(id: RecipientId, enabled: Boolean) {
    val contentValues = ContentValues(1).apply {
      put(PROFILE_SHARING, if (enabled) 1 else 0)
    }

    if (enabled) {
      contentValues.put(HIDDEN, 0)
    }

    val profiledUpdated = update(id, contentValues)

    if (profiledUpdated && enabled) {
      val group = groups.getGroup(id)
      if (group.isPresent) {
        setHasGroupsInCommon(group.get().members)
      }
    }

    if (profiledUpdated) {
      rotateStorageId(id)
      AppDependencies.databaseObserver.notifyRecipientChanged(id)
      StorageSyncHelper.scheduleSyncForDataChange()
    }
  }

  fun setNotificationChannel(id: RecipientId, notificationChannel: String?) {
    val contentValues = ContentValues(1).apply {
      put(NOTIFICATION_CHANNEL, notificationChannel)
    }
    if (update(id, contentValues)) {
      AppDependencies.databaseObserver.notifyRecipientChanged(id)
    }
  }

  fun setPhoneNumberSharing(id: RecipientId, phoneNumberSharing: PhoneNumberSharingState) {
    val contentValues = contentValuesOf(
      PHONE_NUMBER_SHARING to phoneNumberSharing.id
    )
    if (update(id, contentValues)) {
      AppDependencies.databaseObserver.notifyRecipientChanged(id)
    }
  }

  fun resetAllWallpaper() {
    val database = writableDatabase
    val selection = SqlUtil.buildArgs(ID, WALLPAPER_URI)
    val where = "$WALLPAPER IS NOT NULL"
    val idWithWallpaper: MutableList<Pair<RecipientId, String?>> = LinkedList()

    database.beginTransaction()
    try {
      database.query(TABLE_NAME, selection, where, null, null, null, null).use { cursor ->
        while (cursor != null && cursor.moveToNext()) {
          idWithWallpaper.add(
            Pair(
              RecipientId.from(cursor.requireInt(ID).toLong()),
              cursor.optionalString(WALLPAPER_URI).orElse(null)
            )
          )
        }
      }

      if (idWithWallpaper.isEmpty()) {
        return
      }

      val values = ContentValues(2).apply {
        putNull(WALLPAPER_URI)
        putNull(WALLPAPER)
      }

      val rowsUpdated = database.update(TABLE_NAME, values, where, null)
      if (rowsUpdated == idWithWallpaper.size) {
        for (pair in idWithWallpaper) {
          AppDependencies.databaseObserver.notifyRecipientChanged(pair.first)
          if (pair.second != null) {
            WallpaperStorage.onWallpaperDeselected(context, Uri.parse(pair.second))
          }
        }
      } else {
        throw AssertionError("expected " + idWithWallpaper.size + " but got " + rowsUpdated)
      }
    } finally {
      database.setTransactionSuccessful()
      database.endTransaction()
    }
  }

  fun setWallpaper(id: RecipientId, chatWallpaper: ChatWallpaper?) {
    setWallpaper(id, chatWallpaper?.serialize())
  }

  private fun setWallpaper(id: RecipientId, wallpaper: Wallpaper?) {
    val existingWallpaperUri = getWallpaperUri(id)
    val values = ContentValues().apply {
      put(WALLPAPER, wallpaper?.encode())
      if (wallpaper?.file_ != null) {
        put(WALLPAPER_URI, wallpaper.file_.uri)
      } else {
        putNull(WALLPAPER_URI)
      }
    }

    if (update(id, values)) {
      AppDependencies.databaseObserver.notifyRecipientChanged(id)
    }

    if (existingWallpaperUri != null) {
      WallpaperStorage.onWallpaperDeselected(context, existingWallpaperUri)
    }
  }

  fun setDimWallpaperInDarkTheme(id: RecipientId, enabled: Boolean) {
    val wallpaper = getWallpaper(id) ?: throw IllegalStateException("No wallpaper set for $id")
    val updated = wallpaper.newBuilder()
      .dimLevelInDarkTheme(if (enabled) ChatWallpaper.FIXED_DIM_LEVEL_FOR_DARK_THEME else 0f)
      .build()

    setWallpaper(id, updated)
  }

  private fun getWallpaper(id: RecipientId): Wallpaper? {
    readableDatabase.query(TABLE_NAME, arrayOf(WALLPAPER), ID_WHERE, SqlUtil.buildArgs(id), null, null, null).use { cursor ->
      if (cursor.moveToFirst()) {
        val raw = cursor.requireBlob(WALLPAPER)
        return if (raw != null) {
          try {
            Wallpaper.ADAPTER.decode(raw)
          } catch (e: IOException) {
            null
          }
        } else {
          null
        }
      }
    }

    return null
  }

  private fun getWallpaperUri(id: RecipientId): Uri? {
    val wallpaper = getWallpaper(id)

    return if (wallpaper != null && wallpaper.file_ != null) {
      Uri.parse(wallpaper.file_.uri)
    } else {
      null
    }
  }

  fun getWallpaperUriUsageCount(uri: Uri): Int {
    val query = "$WALLPAPER_URI = ?"
    val args = SqlUtil.buildArgs(uri)

    readableDatabase.query(TABLE_NAME, arrayOf("COUNT(*)"), query, args, null, null, null).use { cursor ->
      if (cursor.moveToFirst()) {
        return cursor.getInt(0)
      }
    }

    return 0
  }

  fun getPhoneNumberDiscoverability(id: RecipientId): PhoneNumberDiscoverableState? {
    return readableDatabase
      .select(PHONE_NUMBER_DISCOVERABLE)
      .from(TABLE_NAME)
      .where("$ID = ?", id)
      .run()
      .readToSingleObject { PhoneNumberDiscoverableState.fromId(it.requireInt(PHONE_NUMBER_DISCOVERABLE)) }
  }

  /**
   * @return True if setting the phone number resulted in changed recipientId, otherwise false.
   */
  fun setPhoneNumber(id: RecipientId, e164: String): Boolean {
    val db = writableDatabase

    db.beginTransaction()
    return try {
      setPhoneNumberOrThrow(id, e164)
      db.setTransactionSuccessful()
      false
    } catch (e: SQLiteConstraintException) {
      Log.w(TAG, "[setPhoneNumber] Hit a conflict when trying to update $id. Possibly merging.")

      val existing: RecipientRecord = getRecord(id)
      val newId = getAndPossiblyMerge(existing.aci, e164)
      Log.w(TAG, "[setPhoneNumber] Resulting id: $newId")

      db.setTransactionSuccessful()
      newId != existing.id
    } finally {
      db.endTransaction()
    }
  }

  private fun removePhoneNumber(recipientId: RecipientId) {
    val values = ContentValues().apply {
      putNull(E164)
      putNull(PNI_COLUMN)
    }

    if (update(recipientId, values)) {
      rotateStorageId(recipientId)
    }
  }

  /**
   * Should only use if you are confident that this will not result in any contact merging.
   */
  @Throws(SQLiteConstraintException::class)
  fun setPhoneNumberOrThrow(id: RecipientId, e164: String) {
    val contentValues = ContentValues(1).apply {
      put(E164, e164)
    }
    if (update(id, contentValues)) {
      rotateStorageId(id)
      AppDependencies.databaseObserver.notifyRecipientChanged(id)
      StorageSyncHelper.scheduleSyncForDataChange()
    }
  }

  @Throws(SQLiteConstraintException::class)
  fun setPhoneNumberOrThrowSilent(id: RecipientId, e164: String) {
    val contentValues = ContentValues(1).apply {
      put(E164, e164)
    }
    if (update(id, contentValues)) {
      rotateStorageId(id)
    }
  }

  /**
   * Associates the provided IDs together. The assumption here is that all of the IDs correspond to the local user and have been verified.
   */
  fun linkIdsForSelf(aci: ACI, pni: PNI, e164: String) {
    val id: RecipientId = getAndPossiblyMerge(aci = aci, pni = pni, e164 = e164, changeSelf = true, pniVerified = true)
    updatePendingSelfData(id)
  }

  /**
   * Does *not* handle clearing the recipient cache. It is assumed the caller handles this.
   */
  fun updateSelfE164(e164: String, pni: PNI) {
    val db = writableDatabase

    db.beginTransaction()
    try {
      val id = Recipient.self().id
      val newId = getAndPossiblyMerge(aci = SignalStore.account.requireAci(), pni = pni, e164 = e164, pniVerified = true, changeSelf = true)

      if (id == newId) {
        Log.i(TAG, "[updateSelfPhone] Phone updated for self")
      } else {
        throw AssertionError("[updateSelfPhone] Self recipient id changed when updating e164. old: $id new: $newId")
      }

      db.updateAll(TABLE_NAME)
        .values(NEEDS_PNI_SIGNATURE to 0)
        .run()

      SignalDatabase.pendingPniSignatureMessages.deleteAll()

      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }
  }

  fun getUsername(id: RecipientId): String? {
    return writableDatabase.query(TABLE_NAME, arrayOf(USERNAME), "$ID = ?", SqlUtil.buildArgs(id), null, null, null).use {
      if (it.moveToFirst()) {
        it.requireString(USERNAME)
      } else {
        null
      }
    }
  }

  fun setUsername(id: RecipientId, username: String?) {
    writableDatabase.withinTransaction {
      if (username != null) {
        val existingUsername = getByUsername(username)
        if (existingUsername.isPresent && id != existingUsername.get()) {
          Log.i(TAG, "Username was previously thought to be owned by " + existingUsername.get() + ". Clearing their username.")
          setUsername(existingUsername.get(), null)
        }
      }

      if (update(id, contentValuesOf(USERNAME to username))) {
        AppDependencies.databaseObserver.notifyRecipientChanged(id)
        rotateStorageId(id)
        StorageSyncHelper.scheduleSyncForDataChange()
      }
    }
  }

  fun setHideStory(id: RecipientId, hideStory: Boolean) {
    updateExtras(id) { it.hideStory(hideStory) }
    rotateStorageId(id)
    StorageSyncHelper.scheduleSyncForDataChange()
  }

  fun updateLastStoryViewTimestamp(id: RecipientId) {
    updateExtras(id) { it.lastStoryView(System.currentTimeMillis()) }
  }

  fun getAllE164s(): Set<String> {
    val results: MutableSet<String> = HashSet()
    readableDatabase.query(TABLE_NAME, arrayOf(E164), null, null, null, null, null).use { cursor ->
      while (cursor != null && cursor.moveToNext()) {
        val number = cursor.getString(cursor.getColumnIndexOrThrow(E164))
        if (!TextUtils.isEmpty(number)) {
          results.add(number)
        }
      }
    }
    return results
  }

  /** A function that's just to help with some temporary bug investigation. */
  private fun getAllPnis(): Set<PNI> {
    return readableDatabase
      .select(PNI_COLUMN)
      .from(TABLE_NAME)
      .where("$PNI_COLUMN NOT NULL")
      .run()
      .readToSet { PNI.parseOrThrow(it.requireString(PNI_COLUMN)) }
  }

  /**
   * Gives you all of the recipientIds of possibly-registered users (i.e. REGISTERED or UNKNOWN) that can be found by the set of
   * provided E164s.
   */
  fun getAllPossiblyRegisteredByE164(e164s: Set<String>): Set<RecipientId> {
    val results: MutableSet<RecipientId> = mutableSetOf()
    val queries: List<SqlUtil.Query> = SqlUtil.buildCollectionQuery(E164, e164s)

    for (query in queries) {
      readableDatabase.query(TABLE_NAME, arrayOf(ID, REGISTERED), query.where, query.whereArgs, null, null, null).use { cursor ->
        while (cursor.moveToNext()) {
          if (RegisteredState.fromId(cursor.requireInt(REGISTERED)) != RegisteredState.NOT_REGISTERED) {
            results += RecipientId.from(cursor.requireLong(ID))
          }
        }
      }
    }

    return results
  }

  fun setPni(id: RecipientId, pni: PNI) {
    writableDatabase
      .update(TABLE_NAME)
      .values(ACI_COLUMN to pni.toString())
      .where("$ID = ? AND ($ACI_COLUMN IS NULL OR $ACI_COLUMN = $PNI_COLUMN)", id)
      .run()

    writableDatabase
      .update(TABLE_NAME)
      .values(PNI_COLUMN to pni.toString())
      .where("$ID = ?", id)
      .run()
  }

  /**
   * @return True if setting the UUID resulted in changed recipientId, otherwise false.
   */
  fun markRegistered(id: RecipientId, serviceId: ServiceId): Boolean {
    val db = writableDatabase

    db.beginTransaction()
    try {
      markRegisteredOrThrow(id, serviceId)
      db.setTransactionSuccessful()
      return false
    } catch (e: SQLiteConstraintException) {
      Log.w(TAG, "[markRegistered] Hit a conflict when trying to update $id. Possibly merging.")

      val existing = getRecord(id)
      val newId = getAndPossiblyMerge(serviceId, existing.e164)
      Log.w(TAG, "[markRegistered] Merged into $newId")

      db.setTransactionSuccessful()
      return newId != existing.id
    } finally {
      db.endTransaction()
    }
  }

  /**
   * Should only use if you are confident that this shouldn't result in any contact merging.
   */
  fun markRegisteredOrThrow(id: RecipientId, serviceId: ServiceId) {
    val contentValues = contentValuesOf(
      REGISTERED to RegisteredState.REGISTERED.id,
      ACI_COLUMN to serviceId.toString().lowercase(),
      UNREGISTERED_TIMESTAMP to 0
    )
    if (update(id, contentValues)) {
      Log.i(TAG, "Newly marked $id as registered.")
      setStorageIdIfNotSet(id)
      AppDependencies.databaseObserver.notifyRecipientChanged(id)
    }
  }

  fun markUnregistered(id: RecipientId) {
    val record = getRecord(id)

    if (record.aci != null && record.pni != null) {
      markUnregisteredAndSplit(id, record)
    } else {
      markUnregisteredWithoutSplit(id)
    }
  }

  /**
   * Marks the user unregistered and also splits it into an ACI-only and PNI-only contact.
   * This is to allow a new user to register the number with a new ACI.
   */
  private fun markUnregisteredAndSplit(id: RecipientId, record: RecipientRecord) {
    check(record.aci != null && record.pni != null)

    val contentValues = contentValuesOf(
      REGISTERED to RegisteredState.NOT_REGISTERED.id,
      UNREGISTERED_TIMESTAMP to System.currentTimeMillis(),
      E164 to null,
      PNI_COLUMN to null
    )

    if (update(id, contentValues)) {
      Log.i(TAG, "[WithSplit] Newly marked $id as unregistered.")
      markNeedsSync(id)
      AppDependencies.databaseObserver.notifyRecipientChanged(id)
    }

    val splitId = getAndPossiblyMerge(null, record.pni, record.e164)
    Log.i(TAG, "Split off new recipient as $splitId (ACI-only recipient is $id)")
  }

  /**
   * Marks the user unregistered without splitting the contact into an ACI-only and PNI-only contact.
   */
  private fun markUnregisteredWithoutSplit(id: RecipientId) {
    val contentValues = contentValuesOf(
      REGISTERED to RegisteredState.NOT_REGISTERED.id,
      UNREGISTERED_TIMESTAMP to System.currentTimeMillis()
    )

    if (update(id, contentValues)) {
      Log.i(TAG, "[WithoutSplit] Newly marked $id as unregistered.")
      markNeedsSync(id)
      AppDependencies.databaseObserver.notifyRecipientChanged(id)
    }
  }

  /**
   * Removes the target recipient's E164+PNI, then creates a new recipient with that E164+PNI.
   * Done so we can match a split contact during storage sync.
   */
  fun splitForStorageSyncIfNecessary(aci: ACI) {
    val recipientId = getByAci(aci).getOrNull() ?: return
    val record = getRecord(recipientId)

    if (record.pni == null && record.e164 == null) {
      return
    }

    Log.i(TAG, "Splitting $recipientId for storage sync", true)

    writableDatabase
      .update(TABLE_NAME)
      .values(
        PNI_COLUMN to null,
        E164 to null
      )
      .where("$ID = ?", record.id)
      .run()

    getAndPossiblyMerge(null, record.pni, record.e164)
  }

  fun processIndividualCdsLookup(aci: ACI?, pni: PNI, e164: String): RecipientId {
    return getAndPossiblyMerge(aci = aci, pni = pni, e164 = e164)
  }

  /**
   * Processes CDSv2 results, merging recipients as necessary. Does not mark users as
   * registered.
   *
   * @return A set of [RecipientId]s that were updated/inserted.
   */
  fun bulkProcessCdsResult(mapping: Map<String, CdsV2Result>): Set<RecipientId> {
    val ids: MutableSet<RecipientId> = mutableSetOf()
    val db = writableDatabase

    db.beginTransaction()
    try {
      for ((e164, result) in mapping) {
        ids += getAndPossiblyMerge(aci = result.aci, pni = result.pni, e164 = e164, pniVerified = false, changeSelf = false)
      }

      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }

    return ids
  }

  fun bulkUpdatedRegisteredStatus(registered: Set<RecipientId>, unregistered: Collection<RecipientId>) {
    writableDatabase.withinTransaction {
      val existingRegistered: Set<RecipientId> = getRegistered()
      val needsMarkRegistered: Set<RecipientId> = registered - existingRegistered

      val registeredValues = contentValuesOf(
        REGISTERED to RegisteredState.REGISTERED.id,
        UNREGISTERED_TIMESTAMP to 0
      )

      val newlyRegistered: MutableSet<RecipientId> = mutableSetOf()

      for (id in needsMarkRegistered) {
        if (update(id, registeredValues)) {
          newlyRegistered += id
          setStorageIdIfNotSet(id)
          AppDependencies.databaseObserver.notifyRecipientChanged(id)
        }
      }

      if (newlyRegistered.isNotEmpty()) {
        Log.i(TAG, "Newly marked the following as registered: $newlyRegistered")
      }

      val newlyUnregistered: MutableSet<RecipientId> = mutableSetOf()

      val unregisteredValues = contentValuesOf(
        REGISTERED to RegisteredState.NOT_REGISTERED.id,
        UNREGISTERED_TIMESTAMP to System.currentTimeMillis()
      )

      for (id in unregistered) {
        if (update(id, unregisteredValues)) {
          newlyUnregistered += id
          AppDependencies.databaseObserver.notifyRecipientChanged(id)
        }
      }

      if (newlyUnregistered.isNotEmpty()) {
        Log.i(TAG, "Newly marked the following as unregistered: $newlyUnregistered")
      }
    }
  }

  /**
   * Takes a tuple of (e164, pni, aci) and incorporates it into our database.
   * It is assumed that we are in a transaction.
   *
   * @return The [RecipientId] of the resulting recipient.
   */
  @VisibleForTesting
  fun processPnpTuple(e164: String?, pni: PNI?, aci: ACI?, pniVerified: Boolean, changeSelf: Boolean = false): ProcessPnpTupleResult {
    val changeSet: PnpChangeSet = processPnpTupleToChangeSet(e164, pni, aci, pniVerified, changeSelf)

    val affectedIds: MutableSet<RecipientId> = mutableSetOf()
    val oldIds: MutableSet<RecipientId> = mutableSetOf()
    var changedNumberId: RecipientId? = null

    for (operation in changeSet.operations) {
      @Exhaustive
      when (operation) {
        is PnpOperation.RemoveE164,
        is PnpOperation.RemovePni,
        is PnpOperation.SetAci,
        is PnpOperation.SetE164,
        is PnpOperation.SetPni -> {
          affectedIds.add(operation.recipientId)
        }

        is PnpOperation.Merge -> {
          oldIds.add(operation.secondaryId)
          affectedIds.add(operation.primaryId)
        }

        is PnpOperation.SessionSwitchoverInsert -> {}
        is PnpOperation.ChangeNumberInsert -> changedNumberId = operation.recipientId
      }
    }

    val finalId: RecipientId = writePnpChangeSetToDisk(changeSet, pni, pniVerified)

    return ProcessPnpTupleResult(
      finalId = finalId,
      requiredInsert = changeSet.id is PnpIdResolver.PnpInsert,
      affectedIds = affectedIds,
      oldIds = oldIds,
      changedNumberId = changedNumberId,
      operations = changeSet.operations.toList(),
      breadCrumbs = changeSet.breadCrumbs
    )
  }

  @VisibleForTesting
  fun writePnpChangeSetToDisk(changeSet: PnpChangeSet, inputPni: PNI?, pniVerified: Boolean): RecipientId {
    var hadThreadMerge = false
    for (operation in changeSet.operations) {
      @Exhaustive
      when (operation) {
        is PnpOperation.RemoveE164 -> {
          writableDatabase
            .update(TABLE_NAME)
            .values(E164 to null)
            .where("$ID = ?", operation.recipientId)
            .run()
        }

        is PnpOperation.RemovePni -> {
          writableDatabase
            .update(TABLE_NAME)
            .values(
              PNI_COLUMN to null,
              PNI_SIGNATURE_VERIFIED to 0
            )
            .where("$ID = ?", operation.recipientId)
            .run()
        }

        is PnpOperation.SetAci -> {
          writableDatabase
            .update(TABLE_NAME)
            .values(
              ACI_COLUMN to operation.aci.toString(),
              REGISTERED to RegisteredState.REGISTERED.id,
              UNREGISTERED_TIMESTAMP to 0,
              PNI_SIGNATURE_VERIFIED to pniVerified.toInt()
            )
            .where("$ID = ?", operation.recipientId)
            .run()
        }

        is PnpOperation.SetE164 -> {
          writableDatabase
            .update(TABLE_NAME)
            .values(E164 to operation.e164)
            .where("$ID = ?", operation.recipientId)
            .run()
        }

        is PnpOperation.SetPni -> {
          writableDatabase
            .update(TABLE_NAME)
            .values(
              PNI_COLUMN to operation.pni.toString(),
              REGISTERED to RegisteredState.REGISTERED.id,
              UNREGISTERED_TIMESTAMP to 0,
              PNI_SIGNATURE_VERIFIED to 0
            )
            .where("$ID = ?", operation.recipientId)
            .run()
        }

        is PnpOperation.Merge -> {
          val mergeResult: MergeResult = merge(operation.primaryId, operation.secondaryId, inputPni, pniVerified)
          hadThreadMerge = hadThreadMerge || mergeResult.neededThreadMerge
        }

        is PnpOperation.SessionSwitchoverInsert -> {
          if (hadThreadMerge) {
            Log.d(TAG, "Skipping SSE insert because we already had a thread merge event.")
          } else {
            val threadId: Long? = threads.getThreadIdFor(operation.recipientId)
            if (threadId != null) {
              val event = SessionSwitchoverEvent(e164 = operation.e164 ?: "")
              try {
                SignalDatabase.messages.insertSessionSwitchoverEvent(operation.recipientId, threadId, event)
              } catch (e: Exception) {
                Log.e(TAG, "About to crash! Breadcrumbs: ${changeSet.breadCrumbs}, Operations: ${changeSet.operations}, ID: ${changeSet.id}", true)

                val allPnis: Set<PNI> = getAllPnis()
                val pnisWithSessions: Set<PNI> = sessions.findAllThatHaveAnySession(allPnis)
                Log.e(TAG, "We know of ${allPnis.size} PNIs, and there are sessions with ${pnisWithSessions.size} of them.", true)

                val record = getRecord(operation.recipientId)
                Log.e(TAG, "ID: ${record.id}, E164: ${record.e164}, ACI: ${record.aci}, PNI: ${record.pni}, Registered: ${record.registered}", true)

                if (record.aci != null && record.aci == SignalStore.account.aci) {
                  if (pnisWithSessions.contains(SignalStore.account.pni!!)) {
                    throw SseWithSelfAci(e)
                  } else {
                    throw SseWithSelfAciNoSession(e)
                  }
                }

                if (record.pni != null && record.pni == SignalStore.account.pni) {
                  if (pnisWithSessions.contains(SignalStore.account.pni!!)) {
                    throw SseWithSelfPni(e)
                  } else {
                    throw SseWithSelfPniNoSession(e)
                  }
                }

                if (record.e164 != null && record.e164 == SignalStore.account.e164) {
                  if (pnisWithSessions.contains(SignalStore.account.pni!!)) {
                    throw SseWithSelfE164(e)
                  } else {
                    throw SseWithSelfE164NoSession(e)
                  }
                }

                if (pnisWithSessions.isEmpty()) {
                  throw SseWithNoPniSessionsException(e)
                } else if (pnisWithSessions.size == 1) {
                  if (pnisWithSessions.first() == SignalStore.account.pni) {
                    throw SseWithASinglePniSessionForSelfException(e)
                  } else {
                    throw SseWithASinglePniSessionException(e)
                  }
                } else {
                  throw SseWithMultiplePniSessionsException(e)
                }
              }
            }
          }
        }

        is PnpOperation.ChangeNumberInsert -> {
          if (changeSet.id is PnpIdResolver.PnpNoopId) {
            SignalDatabase.messages.insertNumberChangeMessages(changeSet.id.recipientId)
          } else {
            throw IllegalStateException("There's a change number event on a newly-inserted recipient?")
          }
        }
      }
    }

    return when (changeSet.id) {
      is PnpIdResolver.PnpNoopId -> {
        changeSet.id.recipientId
      }

      is PnpIdResolver.PnpInsert -> {
        val id: Long = writableDatabase.insert(TABLE_NAME, null, buildContentValuesForNewUser(changeSet.id.e164, changeSet.id.pni, changeSet.id.aci, pniVerified))
        RecipientId.from(id)
      }
    }
  }

  /**
   * Takes a tuple of (e164, pni, aci) and converts that into a list of changes that would need to be made to
   * merge that data into our database.
   *
   * The database will be read, but not written to, during this function.
   * It is assumed that we are in a transaction.
   */
  @VisibleForTesting
  fun processPnpTupleToChangeSet(e164: String?, pni: PNI?, aci: ACI?, pniVerified: Boolean, changeSelf: Boolean = false): PnpChangeSet {
    check(e164 != null || pni != null || aci != null) { "Must provide at least one field!" }

    val breadCrumbs: MutableList<String> = mutableListOf()

    val partialData = PnpDataSet(
      e164 = e164,
      pni = pni,
      aci = aci,
      byE164 = e164?.let { getByE164(it).orElse(null) },
      byPni = pni?.let { getByPni(it).orElse(null) },
      byAci = aci?.let { getByAci(it).orElse(null) }
    )

    val allRequiredDbFields: MutableList<RecipientId?> = mutableListOf()
    if (e164 != null) {
      allRequiredDbFields += partialData.byE164
    }
    if (aci != null) {
      allRequiredDbFields += partialData.byAci
    }
    if (pni != null) {
      allRequiredDbFields += partialData.byPni
    }

    val allRequiredDbFieldPopulated: Boolean = allRequiredDbFields.all { it != null }

    // All IDs agree and the database is up-to-date
    if (partialData.commonId != null && allRequiredDbFieldPopulated) {
      breadCrumbs.add("CommonIdAndUpToDate")
      return PnpChangeSet(id = PnpIdResolver.PnpNoopId(partialData.commonId), breadCrumbs = breadCrumbs)
    }

    // All ID's agree, but we need to update the database
    if (partialData.commonId != null && !allRequiredDbFieldPopulated) {
      breadCrumbs.add("CommonIdButNeedsUpdate")
      return processNonMergePnpUpdate(e164, pni, aci, commonId = partialData.commonId, pniVerified = pniVerified, changeSelf = changeSelf, breadCrumbs = breadCrumbs)
    }

    // Nothing matches
    if (partialData.byE164 == null && partialData.byPni == null && partialData.byAci == null) {
      breadCrumbs += "NothingMatches"
      return PnpChangeSet(
        id = PnpIdResolver.PnpInsert(
          e164 = e164,
          pni = pni,
          aci = aci
        ),
        breadCrumbs = breadCrumbs
      )
    }

    // At this point, we know that records have been found for at least two of the fields,
    // and that there are at least two unique IDs among the records.
    //
    // In other words, *some* sort of merging of data must now occur.
    // It may be that some data just gets shuffled around, or it may be that
    // two or more records get merged into one record, with the others being deleted.

    breadCrumbs += "NeedsMerge"

    val preMergeData = partialData.copy(
      e164Record = partialData.byE164?.let { getRecord(it) },
      pniRecord = partialData.byPni?.let { getRecord(it) },
      aciRecord = partialData.byAci?.let { getRecord(it) }
    )

    check(preMergeData.commonId == null)
    check(listOfNotNull(preMergeData.byE164, preMergeData.byPni, preMergeData.byAci).size >= 2)

    val operations: LinkedHashSet<PnpOperation> = linkedSetOf()

    operations += processPossibleE164PniMerge(preMergeData, pniVerified, changeSelf, breadCrumbs)
    operations += processPossiblePniAciMerge(preMergeData.perform(operations), pniVerified, changeSelf, breadCrumbs)
    operations += processPossibleE164AciMerge(preMergeData.perform(operations), pniVerified, changeSelf, breadCrumbs)

    val postMergeData: PnpDataSet = preMergeData.perform(operations)
    val primaryId: RecipientId = listOfNotNull(postMergeData.byAci, postMergeData.byE164, postMergeData.byPni).first()

    if (postMergeData.byAci == null && aci != null) {
      breadCrumbs += "FinalUpdateAci"
      operations += PnpOperation.SetAci(
        recipientId = primaryId,
        aci = aci
      )

      if (needsSessionSwitchoverEvent(pniVerified, postMergeData.pni, aci)) {
        breadCrumbs += "FinalUpdateAciSSE"
        operations += PnpOperation.SessionSwitchoverInsert(
          recipientId = primaryId,
          e164 = postMergeData.e164
        )
      }
    }

    if (postMergeData.byE164 == null && e164 != null && (changeSelf || notSelf(e164, pni, aci))) {
      breadCrumbs += "FinalUpdateE164"
      operations += PnpOperation.SetE164(
        recipientId = primaryId,
        e164 = e164
      )
    }

    if (postMergeData.byPni == null && pni != null) {
      breadCrumbs += "FinalUpdatePni"
      operations += PnpOperation.SetPni(
        recipientId = primaryId,
        pni = pni
      )
    }

    sessionSwitchoverEventIfNeeded(pniVerified, preMergeData.pniRecord, postMergeData.pniRecord)?.let {
      breadCrumbs += "FinalUpdateSSEPniRecord"
      operations += it
    }

    sessionSwitchoverEventIfNeeded(pniVerified, preMergeData.aciRecord, postMergeData.aciRecord)?.let {
      breadCrumbs += "FinalUpdateSSEPniAciRecord"
      operations += it
    }

    return PnpChangeSet(
      id = PnpIdResolver.PnpNoopId(primaryId),
      operations = operations,
      breadCrumbs = breadCrumbs
    )
  }

  /**
   * If all of the non-null fields match a single recipient, return it. Otherwise null.
   */
  private fun getRecipientIdIfAllFieldsMatch(aci: ACI?, pni: PNI?, e164: String?): RecipientId? {
    if (aci == null && pni == null && e164 == null) {
      return null
    }

    val columns = listOf(
      ACI_COLUMN to aci?.toString(),
      PNI_COLUMN to pni?.toString(),
      E164 to e164
    ).filter { it.second != null }

    val query = columns
      .map { "${it.first} = ?" }
      .joinToString(separator = " AND ")

    val args: Array<String> = columns.map { it.second!! }.toTypedArray()

    val ids: List<Long> = readableDatabase
      .select(ID)
      .from(TABLE_NAME)
      .where(query, args)
      .run()
      .readToList { it.requireLong(ID) }

    return if (ids.size == 1) {
      RecipientId.from(ids[0])
    } else {
      null
    }
  }

  /**
   * A session switchover event indicates a situation where we start communicating with a different session that we were before.
   * If a switchover is "verified" (i.e. proven safe cryptographically by the sender), then this doesn't require a user-visible event.
   * But if it's not verified and we're switching from one established session to another, the user needs to be aware.
   */
  private fun needsSessionSwitchoverEvent(pniVerified: Boolean, oldServiceId: ServiceId?, newServiceId: ServiceId?): Boolean {
    return !pniVerified &&
      oldServiceId != null &&
      newServiceId != null &&
      oldServiceId != newServiceId &&
      sessions.hasAnySessionFor(oldServiceId.toString()) &&
      identities.getIdentityStoreRecord(oldServiceId)?.identityKey != identities.getIdentityStoreRecord(newServiceId)?.identityKey
  }

  /**
   * For details on SSE's, see [needsSessionSwitchoverEvent]. This method is just a helper around comparing service ID's from two
   * records and turning it into a possible event.
   */
  private fun sessionSwitchoverEventIfNeeded(pniVerified: Boolean, oldRecord: RecipientRecord?, newRecord: RecipientRecord?): PnpOperation? {
    return if (oldRecord != null && newRecord != null && oldRecord.serviceId == oldRecord.pni && newRecord.serviceId == newRecord.aci && needsSessionSwitchoverEvent(pniVerified, oldRecord.serviceId, newRecord.serviceId)) {
      PnpOperation.SessionSwitchoverInsert(
        recipientId = newRecord.id,
        e164 = newRecord.e164
      )
    } else {
      null
    }
  }

  private fun notSelf(data: PnpDataSet): Boolean {
    return notSelf(data.e164, data.pni, data.aci)
  }

  private fun notSelf(e164: String?, pni: PNI?, aci: ACI?): Boolean {
    return (e164 == null || e164 != SignalStore.account.e164) &&
      (pni == null || pni != SignalStore.account.pni) &&
      (aci == null || aci != SignalStore.account.aci)
  }

  private fun isSelf(data: PnpDataSet): Boolean {
    return isSelf(data.e164, data.pni, data.aci)
  }

  private fun isSelf(e164: String?, pni: PNI?, aci: ACI?): Boolean {
    return (e164 != null && e164 == SignalStore.account.e164) ||
      (pni != null && pni == SignalStore.account.pni) ||
      (aci != null && aci == SignalStore.account.aci)
  }

  private fun processNonMergePnpUpdate(e164: String?, pni: PNI?, aci: ACI?, pniVerified: Boolean, changeSelf: Boolean, commonId: RecipientId, breadCrumbs: MutableList<String>): PnpChangeSet {
    val record: RecipientRecord = getRecord(commonId)

    val operations: LinkedHashSet<PnpOperation> = linkedSetOf()

    // This is a special case. The ACI passed in doesn't match the common record. We can't change ACIs, so we need to make a new record.
    if (aci != null && aci != record.aci && record.aci != null) {
      breadCrumbs += "AciDoesNotMatchCommonRecord"

      if (record.e164 == e164 && (changeSelf || notSelf(e164, pni, aci))) {
        breadCrumbs += "StealingE164"
        operations += PnpOperation.RemoveE164(record.id)
        operations += PnpOperation.RemovePni(record.id)
      } else if (record.pni == pni) {
        breadCrumbs += "StealingPni"
        operations += PnpOperation.RemovePni(record.id)
      }

      val insertE164: String? = if (changeSelf || notSelf(e164, pni, aci)) e164 else null
      val insertPni: PNI? = if (changeSelf || notSelf(e164, pni, aci)) pni else null

      return PnpChangeSet(
        id = PnpIdResolver.PnpInsert(insertE164, insertPni, aci),
        operations = operations,
        breadCrumbs = breadCrumbs
      )
    }

    var updatedNumber = false
    if (e164 != null && record.e164 != e164 && (changeSelf || notSelf(e164, pni, aci))) {
      operations += PnpOperation.SetE164(
        recipientId = commonId,
        e164 = e164
      )
      updatedNumber = true
    }

    if (pni != null && record.pni != pni) {
      operations += PnpOperation.SetPni(
        recipientId = commonId,
        pni = pni
      )
    }

    if (aci != null && record.aci != aci) {
      operations += PnpOperation.SetAci(
        recipientId = commonId,
        aci = aci
      )
    }

    if (record.e164 != null && updatedNumber && notSelf(e164, pni, aci) && !record.isBlocked) {
      breadCrumbs += "NonMergeChangeNumber"
      operations += PnpOperation.ChangeNumberInsert(
        recipientId = commonId,
        oldE164 = record.e164,
        newE164 = e164!!
      )
    }

    val oldServiceId: ServiceId? = record.aci ?: record.pni
    val newServiceId: ServiceId? = aci ?: pni ?: oldServiceId

    if (needsSessionSwitchoverEvent(pniVerified, oldServiceId, newServiceId)) {
      breadCrumbs += "NonMergeSSE"
      operations += PnpOperation.SessionSwitchoverInsert(recipientId = commonId, e164 = record.e164 ?: e164)
    }

    return PnpChangeSet(
      id = PnpIdResolver.PnpNoopId(commonId),
      operations = operations,
      breadCrumbs = breadCrumbs
    )
  }

  /**
   * Resolves any possible E164-PNI conflicts/merges. In these situations, the E164-based row is more dominant
   * and can "steal" data from PNI-based rows, or merge PNI-based rows into itself.
   *
   * We do have to be careful when merging/stealing data to leave possible ACI's that could be on the PNI
   * row alone: remember, ACI's are forever-bound to a given RecipientId.
   */
  private fun processPossibleE164PniMerge(data: PnpDataSet, pniVerified: Boolean, changeSelf: Boolean, breadCrumbs: MutableList<String>): LinkedHashSet<PnpOperation> {
    // Filter to ensure that we're only looking at situations where a PNI and E164 record both exist but do not match
    if (data.pni == null || data.byPni == null || data.pniRecord == null || data.e164 == null || data.byE164 == null || data.e164Record == null || data.e164Record.id == data.pniRecord.id) {
      return linkedSetOf()
    }

    // We have found records for both the E164 and PNI, and they're different
    breadCrumbs += "E164PniMerge"

    if (!changeSelf && isSelf(data)) {
      breadCrumbs += "ChangeSelfPreventsE164PniMerge"
      return linkedSetOf()
    }

    val operations: LinkedHashSet<PnpOperation> = linkedSetOf()

    if (data.pniRecord.pniOnly()) {
      // The PNI record only has a single identifier. We know we must merge.
      breadCrumbs += "PniOnly"

      if (data.e164Record.pni != null) {
        // The e164 record we're merging into has a PNI already. This means that we've entered an 'unstable PNI mapping' scenario.
        // This isn't expected, but we need to handle it gracefully and merge the two rows together.
        operations += PnpOperation.RemovePni(data.byE164)

        if (needsSessionSwitchoverEvent(pniVerified, data.e164Record.pni, data.pni)) {
          breadCrumbs += "E164IdentityMismatchesPniIdentity"
          operations += PnpOperation.SessionSwitchoverInsert(data.byE164, data.e164)
        }
      }

      operations += PnpOperation.Merge(
        primaryId = data.byE164,
        secondaryId = data.byPni
      )
    } else {
      // The record we're taking data from also has either an ACI or e164, so we need to leave that data behind

      breadCrumbs += if (data.pniRecord.aci != null && data.pniRecord.e164 != null) {
        "PniRecordHasE164AndAci"
      } else if (data.pniRecord.aci != null) {
        "PniRecordHasAci"
      } else {
        "PniRecordHasE164"
      }

      // Move the PNI from the PNI record to the e164 record
      operations += PnpOperation.RemovePni(data.byPni)
      operations += PnpOperation.SetPni(
        recipientId = data.byE164,
        pni = data.pni
      )

      // By migrating the PNI to the e164 record, we may cause an SSE
      if (needsSessionSwitchoverEvent(pniVerified, data.e164Record.serviceId, data.e164Record.aci ?: data.pni)) {
        breadCrumbs += "PniE164SSE"
        operations += PnpOperation.SessionSwitchoverInsert(recipientId = data.byE164, e164 = data.e164Record.e164)
      }

      // This is a defensive move where we put an SSE in the session we stole the PNI from and where we're moving it to in order
      // to avoid a multi-step PNI swap. You could imagine that we might remove the PNI in this function call, but then add one back
      // in the next function call, and each step on it's own would think that no SSE is necessary. Given that this scenario only
      // happens with an unstable PNI-E164 mapping, we get out ahead of it by putting an SSE in both preemptively.
      if (!pniVerified && data.pniRecord.aci == null && sessions.hasAnySessionFor(data.pni.toString())) {
        breadCrumbs += "DefensiveSSEByPni"
        operations += PnpOperation.SessionSwitchoverInsert(recipientId = data.byPni, e164 = data.pniRecord.e164)

        if (data.e164Record.aci == null) {
          breadCrumbs += "DefensiveSSEByE164"
          operations += PnpOperation.SessionSwitchoverInsert(recipientId = data.byE164, e164 = data.e164Record.e164)
        }
      }
    }

    return operations
  }

  /**
   * Resolves any possible PNI-ACI conflicts/merges. In these situations, the ACI-based row is more dominant
   * and can "steal" data from PNI-based rows, or merge PNI-based rows into itself.
   */
  private fun processPossiblePniAciMerge(data: PnpDataSet, pniVerified: Boolean, changeSelf: Boolean, breadCrumbs: MutableList<String>): LinkedHashSet<PnpOperation> {
    // Filter to ensure that we're only looking at situations where a PNI and ACI record both exist but do not match
    if (data.pni == null || data.byPni == null || data.pniRecord == null || data.aci == null || data.byAci == null || data.aciRecord == null || data.pniRecord.id == data.aciRecord.id) {
      return linkedSetOf()
    }

    // We have found records for both the PNI and ACI, and they're different
    breadCrumbs += "PniAciMerge"

    if (!changeSelf && isSelf(data)) {
      breadCrumbs += "ChangeSelfPreventsPniAciMerge"
      return linkedSetOf()
    }

    val operations: LinkedHashSet<PnpOperation> = linkedSetOf()

    // The PNI record only has a single identifier. We know we must merge.
    if (data.pniRecord.pniOnly()) {
      breadCrumbs += "PniOnly"

      if (data.aciRecord.pni != null) {
        operations += PnpOperation.RemovePni(data.byAci)
      }

      operations += PnpOperation.Merge(
        primaryId = data.byAci,
        secondaryId = data.byPni
      )
    } else if (data.pniRecord.aci == null && (data.e164 == null || data.pniRecord.e164 == data.e164)) {
      // The PNI has no ACI and possibly some e164. We're going to be stealing all of it's fields,
      // so this is basically a merge with a little bit of extra prep.
      breadCrumbs += "PniRecordHasNoAci"

      if (data.aciRecord.pni != null) {
        operations += PnpOperation.RemovePni(data.byAci)
      }

      val newE164 = data.pniRecord.e164 ?: data.e164

      if (data.aciRecord.e164 != null && data.aciRecord.e164 != newE164 && newE164 != null) {
        operations += PnpOperation.RemoveE164(data.byAci)

        // This also becomes a change number event
        if (notSelf(data) && !data.aciRecord.isBlocked) {
          breadCrumbs += "PniMatchingE164NoAciChangeNumber"
          operations += PnpOperation.ChangeNumberInsert(
            recipientId = data.byAci,
            oldE164 = data.aciRecord.e164,
            newE164 = newE164
          )
        }
      }

      operations += PnpOperation.Merge(
        primaryId = data.byAci,
        secondaryId = data.byPni
      )
    } else {
      // The PNI record has a different ACI, meaning we need to steal what we need and leave the rest behind

      breadCrumbs += if (data.pniRecord.aci != null && data.pniRecord.e164 != data.e164) {
        "PniRecordHasAci"
      } else if (data.pniRecord.aci != null) {
        "PniRecordHasAci"
      } else {
        "PniRecordHasNonMatchingE164"
      }

      operations += PnpOperation.RemovePni(data.byPni)

      operations += PnpOperation.SetPni(
        recipientId = data.byAci,
        pni = data.pni
      )

      if (data.e164 != null && data.aciRecord.e164 != data.e164) {
        if (data.pniRecord.e164 == data.e164) {
          operations += PnpOperation.RemoveE164(
            recipientId = data.byPni
          )
        }

        operations += PnpOperation.SetE164(
          recipientId = data.byAci,
          e164 = data.e164
        )

        if (data.aciRecord.e164 != null && notSelf(data) && !data.aciRecord.isBlocked) {
          breadCrumbs += "PniHasExtraFieldChangeNumber"
          operations += PnpOperation.ChangeNumberInsert(
            recipientId = data.byAci,
            oldE164 = data.aciRecord.e164,
            newE164 = data.e164
          )
        }
      }
    }

    return operations
  }

  /**
   * Resolves any possible E164-ACI conflicts/merges. In these situations, the ACI-based row is more dominant
   * and can "steal" data from E164-based rows, or merge E164-based rows into itself.
   */
  private fun processPossibleE164AciMerge(data: PnpDataSet, pniVerified: Boolean, changeSelf: Boolean, breadCrumbs: MutableList<String>): List<PnpOperation> {
    // Filter to ensure that we're only looking at situations where a E164 and ACI record both exist but do not match
    if (data.e164 == null || data.byE164 == null || data.e164Record == null || data.aci == null || data.byAci == null || data.aciRecord == null || data.e164Record.id == data.aciRecord.id) {
      return emptyList()
    }

    // We have found records for both the E164 and ACI, and they're different
    breadCrumbs += "E164AciMerge"

    if (!changeSelf && isSelf(data)) {
      breadCrumbs += "ChangeSelfPreventsE164AciMerge"
      return emptyList()
    }

    val operations: MutableList<PnpOperation> = mutableListOf()

    // The E164 record only has a single identifier. We know we must merge.
    if (data.e164Record.e164Only()) {
      breadCrumbs += "E164Only"

      if (data.aciRecord.e164 != null && data.aciRecord.e164 != data.e164) {
        operations += PnpOperation.RemoveE164(data.byAci)
      }

      operations += PnpOperation.Merge(
        primaryId = data.byAci,
        secondaryId = data.byE164
      )

      if (data.aciRecord.e164 != null && data.aciRecord.e164 != data.e164 && notSelf(data) && !data.aciRecord.isBlocked) {
        breadCrumbs += "E164OnlyChangeNumber"
        operations += PnpOperation.ChangeNumberInsert(
          recipientId = data.byAci,
          oldE164 = data.aciRecord.e164,
          newE164 = data.e164
        )
      }
    } else if (data.e164Record.pni != null && data.e164Record.pni == data.pni) {
      // The E164 record also has the PNI on it. We're going to be stealing both fields,
      // so this is basically a merge with a little bit of extra prep.
      breadCrumbs += "E164RecordHasMatchingPni"

      if (data.aciRecord.pni != null) {
        operations += PnpOperation.RemovePni(data.byAci)
      }

      if (data.aciRecord.e164 != null && data.aciRecord.e164 != data.e164) {
        operations += PnpOperation.RemoveE164(data.byAci)
      }

      operations += PnpOperation.Merge(
        primaryId = data.byAci,
        secondaryId = data.byE164
      )

      if (data.aciRecord.e164 != null && data.aciRecord.e164 != data.e164 && notSelf(data) && !data.aciRecord.isBlocked) {
        breadCrumbs += "E164MatchingPniChangeNumber"
        operations += PnpOperation.ChangeNumberInsert(
          recipientId = data.byAci,
          oldE164 = data.aciRecord.e164,
          newE164 = data.e164
        )
      }
    } else {
      check(data.e164Record.pni == null || data.e164Record.pni != data.pni)
      breadCrumbs += "E164RecordHasNonMatchingPni"

      operations += PnpOperation.RemoveE164(data.byE164)

      operations += PnpOperation.SetE164(
        recipientId = data.byAci,
        e164 = data.e164
      )

      if (data.aciRecord.e164 != null && data.aciRecord.e164 != data.e164 && notSelf(data) && !data.aciRecord.isBlocked) {
        breadCrumbs += "E164NonMatchingPniChangeNumber"
        operations += PnpOperation.ChangeNumberInsert(
          recipientId = data.byAci,
          oldE164 = data.aciRecord.e164,
          newE164 = data.e164
        )
      }
    }

    return operations
  }

  fun getRegistered(): Set<RecipientId> {
    val results: MutableSet<RecipientId> = mutableSetOf()

    readableDatabase.query(TABLE_NAME, ID_PROJECTION, "$REGISTERED = ? and $HIDDEN = ?", arrayOf("1", "${Recipient.HiddenState.NOT_HIDDEN.serialize()}"), null, null, null).use { cursor ->
      while (cursor != null && cursor.moveToNext()) {
        results += RecipientId.from(cursor.getLong(cursor.getColumnIndexOrThrow(ID)))
      }
    }

    return results
  }

  fun getSystemContacts(): List<RecipientId> {
    val results: MutableList<RecipientId> = LinkedList()

    readableDatabase.query(TABLE_NAME, ID_PROJECTION, "$SYSTEM_JOINED_NAME IS NOT NULL AND $SYSTEM_JOINED_NAME != \"\"", null, null, null, null).use { cursor ->
      while (cursor != null && cursor.moveToNext()) {
        results.add(RecipientId.from(cursor.getLong(cursor.getColumnIndexOrThrow(ID))))
      }
    }

    return results
  }

  /** True if the recipient exists and is muted, otherwise false. */
  fun isMuted(id: RecipientId): Boolean {
    return readableDatabase
      .select(MUTE_UNTIL)
      .from(TABLE_NAME)
      .where("$ID = ?", id)
      .run()
      .readToSingleBoolean()
  }

  /** All e164's that are eligible for having a signal link added to their system contact entry. */
  fun getE164sForSystemContactLinks(): Set<String> {
    return readableDatabase
      .select(E164)
      .from(TABLE_NAME)
      .where("$REGISTERED = ? AND $HIDDEN = ? AND $E164 NOT NULL AND $PHONE_NUMBER_DISCOVERABLE != ?", RegisteredState.REGISTERED.id, Recipient.HiddenState.NOT_HIDDEN.serialize(), PhoneNumberDiscoverableState.NOT_DISCOVERABLE.id)
      .run()
      .readToSet { cursor ->
        cursor.requireNonNullString(E164)
      }
  }

  /**
   * We no longer automatically generate a chat color. This method is used only
   * in the case of a legacy migration and otherwise should not be called.
   */
  @Deprecated("")
  fun updateSystemContactColors() {
    val db = readableDatabase
    val updates: MutableMap<RecipientId, ChatColors> = HashMap()

    db.beginTransaction()
    try {
      db.query(TABLE_NAME, arrayOf(ID, "color", CHAT_COLORS, CUSTOM_CHAT_COLORS_ID, SYSTEM_JOINED_NAME), "$SYSTEM_JOINED_NAME IS NOT NULL AND $SYSTEM_JOINED_NAME != \"\"", null, null, null, null).use { cursor ->
        while (cursor != null && cursor.moveToNext()) {
          val id = cursor.requireLong(ID)
          val serializedColor = cursor.requireString("color")
          val customChatColorsId = cursor.requireLong(CUSTOM_CHAT_COLORS_ID)
          val serializedChatColors = cursor.requireBlob(CHAT_COLORS)
          var chatColors: ChatColors? = if (serializedChatColors != null) {
            try {
              forChatColor(forLongValue(customChatColorsId), ChatColor.ADAPTER.decode(serializedChatColors))
            } catch (e: IOException) {
              null
            }
          } else {
            null
          }

          if (chatColors != null) {
            return
          }

          chatColors = if (serializedColor != null) {
            try {
              getChatColors(MaterialColor.fromSerialized(serializedColor))
            } catch (e: UnknownColorException) {
              return
            }
          } else {
            return
          }

          val contentValues = ContentValues().apply {
            put(CHAT_COLORS, chatColors.serialize().encode())
            put(CUSTOM_CHAT_COLORS_ID, chatColors.id.longValue)
          }
          db.update(TABLE_NAME, contentValues, "$ID = ?", arrayOf(id.toString()))
          updates[RecipientId.from(id)] = chatColors
        }
      }
    } finally {
      db.setTransactionSuccessful()
      db.endTransaction()
      updates.entries.forEach { AppDependencies.databaseObserver.notifyRecipientChanged(it.key) }
    }
  }

  fun queryByInternalFields(query: String): List<RecipientRecord> {
    if (query.isBlank()) {
      return emptyList()
    }

    return readableDatabase
      .select()
      .from(TABLE_NAME)
      .where("$ID LIKE ? OR $ACI_COLUMN LIKE ? OR $PNI_COLUMN LIKE ?", "%$query%", "%$query%", "%$query%")
      .run()
      .readToList { cursor ->
        RecipientTableCursorUtil.getRecord(context, cursor)
      }
  }

  fun getSignalContacts(includeSelf: Boolean): Cursor? {
    return getSignalContacts(includeSelf, "$SORT_NAME, $SYSTEM_JOINED_NAME, $SEARCH_PROFILE_NAME, $USERNAME, $E164")
  }

  fun getSignalContactsCount(includeSelf: Boolean): Int {
    return getSignalContacts(includeSelf)?.count ?: 0
  }

  private fun getSignalContacts(includeSelf: Boolean, orderBy: String? = null): Cursor? {
    val searchSelection = ContactSearchSelection.Builder()
      .withRegistered(true)
      .withGroups(false)
      .excludeId(if (includeSelf) null else Recipient.self().id)
      .build()
    val selection = searchSelection.where
    val args = searchSelection.args
    return readableDatabase.query(TABLE_NAME, SEARCH_PROJECTION, selection, args, null, null, orderBy)
  }

  fun querySignalContacts(contactSearchQuery: ContactSearchQuery): Cursor? {
    val query = SqlUtil.buildCaseInsensitiveGlobPattern(contactSearchQuery.query)

    val searchSelection = ContactSearchSelection.Builder()
      .withRegistered(true)
      .withGroups(false)
      .excludeId(if (contactSearchQuery.includeSelf) null else Recipient.self().id)
      .withSearchQuery(query)
      .build()
    val selection = searchSelection.where
    val args = searchSelection.args
    val orderBy = "${if (contactSearchQuery.contactSearchSortOrder == ContactSearchSortOrder.RECENCY) "${ThreadTable.TABLE_NAME}.${ThreadTable.DATE} DESC, " else ""}$SORT_NAME, $SYSTEM_JOINED_NAME, $SEARCH_PROFILE_NAME, $E164"

    //language=roomsql
    val join = if (contactSearchQuery.contactSearchSortOrder == ContactSearchSortOrder.RECENCY) {
      "LEFT OUTER JOIN ${ThreadTable.TABLE_NAME} ON ${ThreadTable.TABLE_NAME}.${ThreadTable.RECIPIENT_ID} = $TABLE_NAME.$ID"
    } else {
      ""
    }

    return if (contactSearchQuery.contactSearchSortOrder == ContactSearchSortOrder.RECENCY) {
      val ambiguous = listOf(ID)
      val projection = SEARCH_PROJECTION.map {
        if (it in ambiguous) "$TABLE_NAME.$it" else it
      } + "${ThreadTable.TABLE_NAME}.${ThreadTable.DATE}"

      //language=roomsql
      readableDatabase.query(
        """
          SELECT ${projection.joinToString(",")}
          FROM $TABLE_NAME
          $join
          WHERE $selection
          ORDER BY $orderBy
        """.trimIndent(),
        args
      )
    } else {
      readableDatabase.query(TABLE_NAME, SEARCH_PROJECTION, selection, args, null, null, orderBy)
    }
  }

  fun querySignalContactLetterHeaders(inputQuery: String, includeSelf: Boolean, includePush: Boolean, includeSms: Boolean): Map<RecipientId, String> {
    val searchSelection = ContactSearchSelection.Builder()
      .withRegistered(includePush)
      .withNonRegistered(includeSms)
      .withGroups(false)
      .excludeId(if (includeSelf) null else Recipient.self().id)
      .withSearchQuery(inputQuery)
      .build()

    return readableDatabase.query(
      """
        SELECT
          _id,
          UPPER(SUBSTR($SORT_NAME, 0, 2)) AS letter_header
        FROM (
          SELECT ${SEARCH_PROJECTION.joinToString(", ")}
          FROM recipient
          WHERE ${searchSelection.where}
          ORDER BY $SORT_NAME, $SYSTEM_JOINED_NAME, $SEARCH_PROFILE_NAME, $E164
        )
        GROUP BY letter_header
      """,
      searchSelection.args
    ).use { cursor ->
      if (cursor.count == 0) {
        emptyMap()
      } else {
        val resultsMap = mutableMapOf<RecipientId, String>()
        while (cursor.moveToNext()) {
          cursor.requireString("letter_header")?.let {
            resultsMap[RecipientId.from(cursor.requireLong(ID))] = it
          }
        }

        resultsMap
      }
    }
  }

  fun getNonSignalContacts(): Cursor? {
    val searchSelection = ContactSearchSelection.Builder().withNonRegistered(true)
      .withGroups(false)
      .build()
    val selection = searchSelection.where
    val args = searchSelection.args
    val orderBy = "$SYSTEM_JOINED_NAME, $E164"
    return readableDatabase.query(TABLE_NAME, SEARCH_PROJECTION, selection, args, null, null, orderBy)
  }

  fun queryNonSignalContacts(inputQuery: String): Cursor? {
    val query = SqlUtil.buildCaseInsensitiveGlobPattern(inputQuery)
    val searchSelection = ContactSearchSelection.Builder()
      .withNonRegistered(true)
      .withGroups(false)
      .withSearchQuery(query)
      .build()
    val selection = searchSelection.where
    val args = searchSelection.args
    val orderBy = "$SYSTEM_JOINED_NAME, $E164"
    return readableDatabase.query(TABLE_NAME, SEARCH_PROJECTION, selection, args, null, null, orderBy)
  }

  fun getNonGroupContacts(includeSelf: Boolean): Cursor? {
    val searchSelection = ContactSearchSelection.Builder()
      .withRegistered(true)
      .withNonRegistered(true)
      .withGroups(false)
      .excludeId(if (includeSelf) null else Recipient.self().id)
      .build()
    val orderBy = orderByPreferringAlphaOverNumeric(SORT_NAME) + ", " + E164
    return readableDatabase.query(TABLE_NAME, SEARCH_PROJECTION, searchSelection.where, searchSelection.args, null, null, orderBy)
  }

  fun queryNonGroupContacts(inputQuery: String, includeSelf: Boolean): Cursor? {
    val query = SqlUtil.buildCaseInsensitiveGlobPattern(inputQuery)

    val searchSelection = ContactSearchSelection.Builder()
      .withRegistered(true)
      .withNonRegistered(true)
      .withGroups(false)
      .excludeId(if (includeSelf) null else Recipient.self().id)
      .withSearchQuery(query)
      .build()
    val selection = searchSelection.where
    val args = searchSelection.args
    val orderBy = orderByPreferringAlphaOverNumeric(SORT_NAME) + ", " + E164

    return readableDatabase.query(TABLE_NAME, SEARCH_PROJECTION, selection, args, null, null, orderBy)
  }

  fun getGroupMemberContacts(): Cursor? {
    val searchSelection = ContactSearchSelection.Builder()
      .withGroupMembers(true)
      .excludeId(Recipient.self().id)
      .build()

    val orderBy = orderByPreferringAlphaOverNumeric(SORT_NAME) + ", " + E164
    return readableDatabase.query(TABLE_NAME, SEARCH_PROJECTION, searchSelection.where, searchSelection.args, null, null, orderBy)
  }

  fun queryGroupMemberContacts(inputQuery: String): Cursor? {
    val query = SqlUtil.buildCaseInsensitiveGlobPattern(inputQuery)
    val searchSelection = ContactSearchSelection.Builder()
      .withGroupMembers(true)
      .excludeId(Recipient.self().id)
      .withSearchQuery(query)
      .build()

    val selection = searchSelection.where
    val args = searchSelection.args
    val orderBy = orderByPreferringAlphaOverNumeric(SORT_NAME) + ", " + E164

    return readableDatabase.query(TABLE_NAME, SEARCH_PROJECTION, selection, args, null, null, orderBy)
  }

  fun queryAllContacts(inputQuery: String): Cursor? {
    val query = SqlUtil.buildCaseInsensitiveGlobPattern(inputQuery)
    val selection =
      """
        $BLOCKED = ? AND
        (
          $SORT_NAME GLOB ? OR 
          $USERNAME GLOB ? OR 
          ${ContactSearchSelection.E164_SEARCH} OR 
          $EMAIL GLOB ?
        )
      """
    val args = SqlUtil.buildArgs(0, query, query, query, query)
    return readableDatabase.query(TABLE_NAME, SEARCH_PROJECTION, selection, args, null, null, null)
  }

  /**
   * Gets the query used for performing the all contacts search so that it can be injected as a subquery.
   */
  fun getAllContactsSubquery(inputQuery: String): SqlUtil.Query {
    val query = SqlUtil.buildCaseInsensitiveGlobPattern(inputQuery)

    //language=sql
    val subquery = """SELECT $ID FROM (
      SELECT ${SEARCH_PROJECTION.joinToString(",")} FROM $TABLE_NAME
      WHERE $BLOCKED = ? AND $HIDDEN = ? AND
      (
          $SORT_NAME GLOB ? OR 
          $USERNAME GLOB ? OR 
          ${ContactSearchSelection.E164_SEARCH} OR 
          $EMAIL GLOB ?
      ))
    """

    return SqlUtil.Query(subquery, SqlUtil.buildArgs(0, 0, query, query, query, query))
  }

  /**
   * Queries all contacts without an active thread.
   */
  fun getAllContactsWithoutThreads(inputQuery: String): Cursor {
    val query = SqlUtil.buildCaseInsensitiveGlobPattern(inputQuery)

    //language=sql
    val subquery = """
      SELECT ${SEARCH_PROJECTION.joinToString(", ")} FROM $TABLE_NAME
      WHERE $BLOCKED = ? AND $HIDDEN = ? AND $REGISTERED != ? AND NOT EXISTS (SELECT 1 FROM ${ThreadTable.TABLE_NAME} WHERE ${ThreadTable.TABLE_NAME}.${ThreadTable.ACTIVE} = 1 AND ${ThreadTable.TABLE_NAME}.${ThreadTable.RECIPIENT_ID} = $TABLE_NAME.$ID LIMIT 1)
      AND (
          $SORT_NAME GLOB ? OR 
          $USERNAME GLOB ? OR 
          ${ContactSearchSelection.E164_SEARCH} OR 
          $EMAIL GLOB ?
      )
    """

    return readableDatabase.query(subquery, SqlUtil.buildArgs(0, 0, RegisteredState.NOT_REGISTERED.id, query, query, query, query))
  }

  @JvmOverloads
  fun queryRecipientsForMentions(inputQuery: String, recipientIds: List<RecipientId>? = null): List<Recipient> {
    val query = SqlUtil.buildCaseInsensitiveGlobPattern(inputQuery)
    var ids: String? = null

    if (Util.hasItems(recipientIds)) {
      ids = TextUtils.join(",", recipientIds?.map { it.serialize() }?.toList() ?: emptyList<String>())
    }

    val selection = "$BLOCKED = 0 AND ${if (ids != null) "$ID IN ($ids) AND " else ""}$SORT_NAME GLOB ?"
    val recipients: MutableList<Recipient> = ArrayList()

    RecipientReader(readableDatabase.query(TABLE_NAME, MENTION_SEARCH_PROJECTION, selection, SqlUtil.buildArgs(query), null, null, SORT_NAME)).use { reader ->
      var recipient: Recipient? = reader.getNext()
      while (recipient != null) {
        if (!recipient.isSelf) {
          recipients.add(recipient)
        }
        recipient = reader.getNext()
      }
    }

    return recipients
  }

  fun getRecipientsForMultiDeviceSync(): List<Recipient> {
    val subquery = "SELECT ${ThreadTable.TABLE_NAME}.${ThreadTable.RECIPIENT_ID} FROM ${ThreadTable.TABLE_NAME}"
    val selection = "$REGISTERED = ? AND $GROUP_ID IS NULL AND $ID != ? AND ($ACI_COLUMN NOT NULL OR $E164 NOT NULL) AND ($SYSTEM_CONTACT_URI NOT NULL OR $ID IN ($subquery))"
    val args = arrayOf(RegisteredState.REGISTERED.id.toString(), Recipient.self().id.serialize())
    val recipients: MutableList<Recipient> = ArrayList()

    readableDatabase.query(TABLE_NAME, ID_PROJECTION, selection, args, null, null, null).use { cursor ->
      while (cursor != null && cursor.moveToNext()) {
        recipients.add(Recipient.resolved(RecipientId.from(cursor.getLong(cursor.getColumnIndexOrThrow(ID)))))
      }
    }
    return recipients
  }

  /**
   * @param lastInteractionThreshold Only include contacts that have been interacted with since this time.
   * @param lastProfileFetchThreshold Only include contacts that haven't their profile fetched after this time.
   * @param limit Only return at most this many contact.
   */
  fun getRecipientsForRoutineProfileFetch(lastInteractionThreshold: Long, lastProfileFetchThreshold: Long, limit: Int): List<RecipientId> {
    val threadDatabase = threads
    val recipientsWithinInteractionThreshold: MutableSet<RecipientId> = LinkedHashSet()

    threadDatabase.readerFor(threadDatabase.getRecentPushConversationList(-1, false)).use { reader ->
      var record: ThreadRecord? = reader.getNext()

      while (record != null && record.date > lastInteractionThreshold) {
        val recipient = Recipient.resolved(record.recipient.id)
        if (recipient.isGroup) {
          recipientsWithinInteractionThreshold.addAll(recipient.participantIds)
        } else {
          recipientsWithinInteractionThreshold.add(recipient.id)
        }
        record = reader.getNext()
      }
    }

    return Recipient.resolvedList(recipientsWithinInteractionThreshold)
      .asSequence()
      .filterNot { it.isSelf }
      .filter { it.lastProfileFetchTime < lastProfileFetchThreshold }
      .take(limit)
      .map { it.id }
      .toMutableList()
  }

  fun markProfilesFetched(ids: Collection<RecipientId>, time: Long) {
    writableDatabase.withinTransaction { db ->
      val values = contentValuesOf(LAST_PROFILE_FETCH to time)

      SqlUtil.buildCollectionQuery(ID, ids).forEach { query ->
        db.update(TABLE_NAME, values, query.where, query.whereArgs)
      }
    }
  }

  fun applyBlockedUpdate(blocked: List<SignalServiceAddress>, groupIds: List<ByteArray?>) {
    val blockedE164 = blocked
      .filter { b: SignalServiceAddress -> b.number.isPresent }
      .map { b: SignalServiceAddress -> b.number.get() }
      .toList()

    val blockedUuid = blocked
      .map { b: SignalServiceAddress -> b.serviceId.toString().lowercase() }
      .toList()

    val db = writableDatabase
    db.beginTransaction()
    try {
      val resetBlocked = ContentValues().apply {
        put(BLOCKED, 0)
      }
      db.update(TABLE_NAME, resetBlocked, null, null)

      val setBlocked = ContentValues().apply {
        put(BLOCKED, 1)
        put(PROFILE_SHARING, 0)
      }

      for (e164 in blockedE164) {
        db.update(TABLE_NAME, setBlocked, "$E164 = ?", arrayOf(e164))
      }

      for (uuid in blockedUuid) {
        db.update(TABLE_NAME, setBlocked, "$ACI_COLUMN = ?", arrayOf(uuid))
      }

      val groupIdStrings: MutableList<V1> = ArrayList(groupIds.size)
      for (raw in groupIds) {
        try {
          groupIdStrings.add(GroupId.v1(raw))
        } catch (e: BadGroupIdException) {
          Log.w(TAG, "[applyBlockedUpdate] Bad GV1 ID!")
        }
      }

      for (groupId in groupIdStrings) {
        db.update(TABLE_NAME, setBlocked, "$GROUP_ID = ?", arrayOf(groupId.toString()))
      }

      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }

    AppDependencies.recipientCache.clear()
  }

  fun updateStorageId(recipientId: RecipientId, id: ByteArray?) {
    updateStorageIds(Collections.singletonMap(recipientId, id))
  }

  private fun updateStorageIds(ids: Map<RecipientId, ByteArray?>) {
    val db = writableDatabase
    db.beginTransaction()
    try {
      for ((key, value) in ids) {
        val values = ContentValues().apply {
          put(STORAGE_SERVICE_ID, Base64.encodeWithPadding(value!!))
        }
        db.update(TABLE_NAME, values, ID_WHERE, arrayOf(key.serialize()))
      }
      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }

    for (id in ids.keys) {
      AppDependencies.databaseObserver.notifyRecipientChanged(id)
    }
  }

  fun markPreMessageRequestRecipientsAsProfileSharingEnabled(messageRequestEnableTime: Long) {
    val whereArgs = SqlUtil.buildArgs(messageRequestEnableTime)
    val select =
      """
        SELECT r.$ID FROM $TABLE_NAME AS r 
        INNER JOIN ${ThreadTable.TABLE_NAME} AS t ON t.${ThreadTable.RECIPIENT_ID} = r.$ID
        WHERE
          r.$PROFILE_SHARING = 0 AND (
            EXISTS(SELECT 1 FROM ${MessageTable.TABLE_NAME} WHERE ${MessageTable.THREAD_ID} = t.${ThreadTable.ID} AND ${MessageTable.DATE_RECEIVED} < ?)
          )
      """

    val idsToUpdate: MutableList<Long> = ArrayList()
    readableDatabase.rawQuery(select, whereArgs).use { cursor ->
      while (cursor.moveToNext()) {
        idsToUpdate.add(cursor.requireLong(ID))
      }
    }

    if (Util.hasItems(idsToUpdate)) {
      val query = SqlUtil.buildSingleCollectionQuery(ID, idsToUpdate)

      val values = contentValuesOf(
        PROFILE_SHARING to 1,
        HIDDEN to 0
      )

      writableDatabase.update(TABLE_NAME, values, query.where, query.whereArgs)

      for (id in idsToUpdate) {
        AppDependencies.databaseObserver.notifyRecipientChanged(RecipientId.from(id))
      }
    }
  }

  /**
   * Indicates that the recipient knows our PNI, and therefore needs to be sent PNI signature messages until we know that they have our PNI-ACI association.
   */
  fun markNeedsPniSignature(recipientId: RecipientId) {
    if (update(recipientId, contentValuesOf(NEEDS_PNI_SIGNATURE to 1))) {
      Log.i(TAG, "Marked $recipientId as needing a PNI signature message.")
      Recipient.live(recipientId).refresh()
    }
  }

  /**
   * Indicates that we successfully told all of this recipient's devices our PNI-ACI association, and therefore no longer needs us to send it to them.
   */
  fun clearNeedsPniSignature(recipientId: RecipientId) {
    if (update(recipientId, contentValuesOf(NEEDS_PNI_SIGNATURE to 0))) {
      Recipient.live(recipientId).refresh()
    }
  }

  fun setHasGroupsInCommon(recipientIds: List<RecipientId?>) {
    if (recipientIds.isEmpty()) {
      return
    }

    var query = SqlUtil.buildSingleCollectionQuery(ID, recipientIds)
    val db = writableDatabase

    db.query(TABLE_NAME, arrayOf(ID), "${query.where} AND $GROUPS_IN_COMMON = 0", query.whereArgs, null, null, null).use { cursor ->
      val idsToUpdate: MutableList<Long> = ArrayList(cursor.count)

      while (cursor.moveToNext()) {
        idsToUpdate.add(cursor.requireLong(ID))
      }

      if (Util.hasItems(idsToUpdate)) {
        query = SqlUtil.buildSingleCollectionQuery(ID, idsToUpdate)
        val values = ContentValues().apply {
          put(GROUPS_IN_COMMON, 1)
        }

        val count = db.update(TABLE_NAME, values, query.where, query.whereArgs)
        if (count > 0) {
          for (id in idsToUpdate) {
            AppDependencies.databaseObserver.notifyRecipientChanged(RecipientId.from(id))
          }
        }
      }
    }
  }

  fun manuallyShowAvatar(recipientId: RecipientId) {
    updateExtras(recipientId) { b: RecipientExtras.Builder -> b.manuallyShownAvatar(true) }
  }

  fun getCapabilities(id: RecipientId): RecipientRecord.Capabilities? {
    readableDatabase
      .select(CAPABILITIES)
      .from(TABLE_NAME)
      .where("$ID = ?", id)
      .run()
      .use { cursor ->
        return if (cursor.moveToFirst()) {
          RecipientTableCursorUtil.readCapabilities(cursor)
        } else {
          null
        }
      }
  }

  fun updatePhoneNumberDiscoverability(presentInCds: Set<RecipientId>, missingFromCds: Set<RecipientId>) {
    SqlUtil.buildCollectionQuery(ID, presentInCds).forEach { query ->
      writableDatabase
        .update(TABLE_NAME)
        .values(PHONE_NUMBER_DISCOVERABLE to PhoneNumberDiscoverableState.DISCOVERABLE.id)
        .where(query.where, query.whereArgs)
        .run()
    }

    SqlUtil.buildCollectionQuery(ID, missingFromCds).forEach { query ->
      writableDatabase
        .update(TABLE_NAME)
        .values(PHONE_NUMBER_DISCOVERABLE to PhoneNumberDiscoverableState.NOT_DISCOVERABLE.id)
        .where(query.where, query.whereArgs)
        .run()
    }
  }

  private fun updateExtras(recipientId: RecipientId, updater: java.util.function.Function<RecipientExtras.Builder, RecipientExtras.Builder>) {
    val db = writableDatabase
    db.beginTransaction()
    try {
      db.query(TABLE_NAME, arrayOf(ID, EXTRAS), ID_WHERE, SqlUtil.buildArgs(recipientId), null, null, null).use { cursor ->
        if (cursor.moveToNext()) {
          val state = getRecipientExtras(cursor)
          val builder = state?.newBuilder() ?: RecipientExtras.Builder()
          val updatedState = updater.apply(builder).build().encode()
          val values = ContentValues(1).apply {
            put(EXTRAS, updatedState)
          }
          db.update(TABLE_NAME, values, ID_WHERE, SqlUtil.buildArgs(cursor.requireLong(ID)))
        }
      }
      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }
    AppDependencies.databaseObserver.notifyRecipientChanged(recipientId)
  }

  /**
   * Does not trigger any recipient refreshes -- it is assumed the caller handles this.
   * Will *not* give storageIds to those that shouldn't get them (e.g. MMS groups, unregistered
   * users).
   */
  fun rotateStorageId(recipientId: RecipientId) {
    val selfId = Recipient.self().id

    val values = ContentValues(1).apply {
      put(STORAGE_SERVICE_ID, Base64.encodeWithPadding(StorageSyncHelper.generateKey()))
    }

    val query = "$ID = ? AND ($TYPE IN (?, ?, ?) OR $REGISTERED = ? OR $ID = ?)"
    val args = SqlUtil.buildArgs(recipientId, RecipientType.GV1.id, RecipientType.GV2.id, RecipientType.DISTRIBUTION_LIST.id, RegisteredState.REGISTERED.id, selfId.toLong())

    writableDatabase.update(TABLE_NAME, values, query, args).also { updateCount ->
      Log.d(TAG, "[rotateStorageId] updateCount: $updateCount")
    }
  }

  /**
   * Does not trigger any recipient refreshes -- it is assumed the caller handles this.
   */
  fun setStorageIdIfNotSet(recipientId: RecipientId) {
    val values = ContentValues(1).apply {
      put(STORAGE_SERVICE_ID, Base64.encodeWithPadding(StorageSyncHelper.generateKey()))
    }

    val query = "$ID = ? AND $STORAGE_SERVICE_ID IS NULL"
    val args = SqlUtil.buildArgs(recipientId)
    writableDatabase.update(TABLE_NAME, values, query, args)
  }

  /**
   * Updates a group recipient with a new V2 group ID. Should only be done as a part of GV1->GV2
   * migration.
   */
  fun updateGroupId(v1Id: V1, v2Id: V2) {
    val values = ContentValues().apply {
      put(GROUP_ID, v2Id.toString())
      put(TYPE, RecipientType.GV2.id)
    }

    val query = SqlUtil.buildTrueUpdateQuery("$GROUP_ID = ?", SqlUtil.buildArgs(v1Id), values)
    if (update(query, values)) {
      val id = getByGroupId(v2Id).get()
      rotateStorageId(id)
      AppDependencies.databaseObserver.notifyRecipientChanged(id)
    }
  }

  fun getExpiresInSeconds(id: RecipientId): Long {
    return readableDatabase
      .select(MESSAGE_EXPIRATION_TIME)
      .from(TABLE_NAME)
      .where(ID_WHERE, id)
      .run()
      .readToSingleLong(0L)
  }

  /**
   * Will update the database with the content values you specified. It will make an intelligent
   * query such that this will only return true if a row was *actually* updated.
   */
  private fun update(id: RecipientId, contentValues: ContentValues): Boolean {
    val updateQuery = SqlUtil.buildTrueUpdateQuery(ID_WHERE, SqlUtil.buildArgs(id), contentValues)
    return update(updateQuery, contentValues)
  }

  /**
   * Will update the database with the {@param contentValues} you specified.
   *
   *
   * This will only return true if a row was *actually* updated with respect to the where clause of the {@param updateQuery}.
   */
  private fun update(updateQuery: SqlUtil.Query, contentValues: ContentValues): Boolean {
    return writableDatabase.update(TABLE_NAME, contentValues, updateQuery.where, updateQuery.whereArgs) > 0
  }

  private fun getByColumn(column: String, value: String): Optional<RecipientId> {
    val query = "$column = ?"
    val args = arrayOf(value)

    readableDatabase.query(TABLE_NAME, ID_PROJECTION, query, args, null, null, null).use { cursor ->
      return if (cursor != null && cursor.moveToFirst()) {
        Optional.of(RecipientId.from(cursor.getLong(cursor.getColumnIndexOrThrow(ID))))
      } else {
        Optional.empty()
      }
    }
  }

  private fun getOrInsertByColumn(column: String, value: String, contentValues: ContentValues = contentValuesOf(column to value)): GetOrInsertResult {
    if (TextUtils.isEmpty(value)) {
      throw AssertionError("$column cannot be empty.")
    }

    var existing = getByColumn(column, value)

    if (existing.isPresent) {
      return GetOrInsertResult(existing.get(), false)
    } else {
      val id = writableDatabase.insert(TABLE_NAME, null, contentValues)
      if (id < 0) {
        existing = getByColumn(column, value)
        if (existing.isPresent) {
          return GetOrInsertResult(existing.get(), false)
        } else {
          throw AssertionError("Failed to insert recipient!")
        }
      } else {
        return GetOrInsertResult(RecipientId.from(id), true)
      }
    }
  }

  /**
   * Merges one ACI recipient with an E164 recipient. It is assumed that the E164 recipient does
   * *not* have an ACI.
   */
  private fun merge(primaryId: RecipientId, secondaryId: RecipientId, newPni: PNI? = null, pniVerified: Boolean): MergeResult {
    ensureInTransaction()
    val db = writableDatabase
    val primaryRecord = getRecord(primaryId)
    val secondaryRecord = getRecord(secondaryId)

    // Clean up any E164-based identities (legacy stuff)
    if (secondaryRecord.e164 != null) {
      AppDependencies.protocolStore.aci().identities().delete(secondaryRecord.e164)
    }

    // Threads
    val threadMerge: ThreadTable.MergeResult = threads.merge(primaryId, secondaryId)
    threads.setLastScrolled(threadMerge.threadId, 0)
    threads.update(threadMerge.threadId, false, false)

    // Recipient remaps
    for (table in recipientIdDatabaseTables) {
      table.remapRecipient(secondaryId, primaryId)
    }

    // Thread Merge Event (remaps happen inside ThreadTable#merge)
    if (threadMerge.neededMerge) {
      val mergeEvent: ThreadMergeEvent.Builder = ThreadMergeEvent.Builder()

      if (secondaryRecord.e164 != null) {
        mergeEvent.previousE164 = secondaryRecord.e164
      }

      SignalDatabase.messages.insertThreadMergeEvent(primaryRecord.id, threadMerge.threadId, mergeEvent.build())
    }

    // Recipient
    Log.w(TAG, "Deleting recipient $secondaryId", true)
    db.delete(TABLE_NAME, ID_WHERE, SqlUtil.buildArgs(secondaryId))
    RemappedRecords.getInstance().addRecipient(secondaryId, primaryId)

    val uuidValues = contentValuesOf(
      E164 to (secondaryRecord.e164 ?: primaryRecord.e164),
      ACI_COLUMN to (primaryRecord.aci ?: secondaryRecord.aci)?.toString(),
      PNI_COLUMN to (newPni ?: secondaryRecord.pni ?: primaryRecord.pni)?.toString(),
      BLOCKED to (secondaryRecord.isBlocked || primaryRecord.isBlocked),
      MESSAGE_RINGTONE to Optional.ofNullable(primaryRecord.messageRingtone).or(Optional.ofNullable(secondaryRecord.messageRingtone)).map { obj: Uri? -> obj.toString() }.orElse(null),
      MESSAGE_VIBRATE to if (primaryRecord.messageVibrateState != VibrateState.DEFAULT) primaryRecord.messageVibrateState.id else secondaryRecord.messageVibrateState.id,
      CALL_RINGTONE to Optional.ofNullable(primaryRecord.callRingtone).or(Optional.ofNullable(secondaryRecord.callRingtone)).map { obj: Uri? -> obj.toString() }.orElse(null),
      CALL_VIBRATE to if (primaryRecord.callVibrateState != VibrateState.DEFAULT) primaryRecord.callVibrateState.id else secondaryRecord.callVibrateState.id,
      NOTIFICATION_CHANNEL to (primaryRecord.notificationChannel ?: secondaryRecord.notificationChannel),
      MUTE_UNTIL to if (primaryRecord.muteUntil > 0) primaryRecord.muteUntil else secondaryRecord.muteUntil,
      CHAT_COLORS to Optional.ofNullable(primaryRecord.chatColors).or(Optional.ofNullable(secondaryRecord.chatColors)).map { colors: ChatColors? -> colors!!.serialize().encode() }.orElse(null),
      AVATAR_COLOR to primaryRecord.avatarColor.serialize(),
      CUSTOM_CHAT_COLORS_ID to Optional.ofNullable(primaryRecord.chatColors).or(Optional.ofNullable(secondaryRecord.chatColors)).map { colors: ChatColors? -> colors!!.id.longValue }.orElse(null),
      MESSAGE_EXPIRATION_TIME to if (primaryRecord.expireMessages > 0) primaryRecord.expireMessages else secondaryRecord.expireMessages,
      REGISTERED to RegisteredState.REGISTERED.id,
      SYSTEM_GIVEN_NAME to secondaryRecord.systemProfileName.givenName,
      SYSTEM_FAMILY_NAME to secondaryRecord.systemProfileName.familyName,
      SYSTEM_JOINED_NAME to secondaryRecord.systemProfileName.toString(),
      SYSTEM_PHOTO_URI to secondaryRecord.systemContactPhotoUri,
      SYSTEM_PHONE_LABEL to secondaryRecord.systemPhoneLabel,
      SYSTEM_CONTACT_URI to secondaryRecord.systemContactUri,
      PROFILE_SHARING to (primaryRecord.profileSharing || secondaryRecord.profileSharing),
      CAPABILITIES to max(primaryRecord.capabilities.rawBits, secondaryRecord.capabilities.rawBits),
      MENTION_SETTING to if (primaryRecord.mentionSetting != MentionSetting.ALWAYS_NOTIFY) primaryRecord.mentionSetting.id else secondaryRecord.mentionSetting.id,
      PNI_SIGNATURE_VERIFIED to pniVerified.toInt()
    )

    if (primaryRecord.profileSharing || secondaryRecord.profileSharing) {
      uuidValues.put(HIDDEN, 0)
    }

    if (primaryRecord.profileKey != null) {
      updateProfileValuesForMerge(uuidValues, primaryRecord)
    } else if (secondaryRecord.profileKey != null) {
      updateProfileValuesForMerge(uuidValues, secondaryRecord)
    }

    db.update(TABLE_NAME, uuidValues, ID_WHERE, SqlUtil.buildArgs(primaryId))

    return MergeResult(
      finalId = primaryId,
      neededThreadMerge = threadMerge.neededMerge
    )
  }

  private fun ensureInTransaction() {
    check(writableDatabase.inTransaction()) { "Must be in a transaction!" }
  }

  private fun buildContentValuesForNewUser(e164: String?, pni: PNI?, aci: ACI?, pniVerified: Boolean): ContentValues {
    check(e164 != null || pni != null || aci != null) { "Must provide some sort of identifier!" }

    val values = contentValuesOf(
      E164 to e164,
      ACI_COLUMN to aci?.toString(),
      PNI_COLUMN to pni?.toString(),
      PNI_SIGNATURE_VERIFIED to pniVerified.toInt(),
      STORAGE_SERVICE_ID to Base64.encodeWithPadding(StorageSyncHelper.generateKey()),
      AVATAR_COLOR to AvatarColorHash.forAddress((aci ?: pni)?.toString(), e164).serialize()
    )

    if (pni != null || aci != null) {
      values.put(REGISTERED, RegisteredState.REGISTERED.id)
      values.put(UNREGISTERED_TIMESTAMP, 0)
    }

    return values
  }

  private fun getValuesForStorageContact(contact: SignalContactRecord, isInsert: Boolean): ContentValues {
    return ContentValues().apply {
      val profileName = ProfileName.fromParts(contact.profileGivenName.orElse(null), contact.profileFamilyName.orElse(null))
      val systemName = ProfileName.fromParts(contact.systemGivenName.orElse(null), contact.systemFamilyName.orElse(null))
      val username = contact.username.orElse(null)
      val nickname = ProfileName.fromParts(contact.nicknameGivenName.orNull(), contact.nicknameFamilyName.orNull())

      put(ACI_COLUMN, contact.aci.orElse(null)?.toString())
      put(PNI_COLUMN, contact.pni.orElse(null)?.toString())
      put(E164, contact.number.orElse(null))
      put(PROFILE_GIVEN_NAME, profileName.givenName)
      put(PROFILE_FAMILY_NAME, profileName.familyName)
      put(PROFILE_JOINED_NAME, profileName.toString())
      put(SYSTEM_GIVEN_NAME, systemName.givenName)
      put(SYSTEM_FAMILY_NAME, systemName.familyName)
      put(SYSTEM_JOINED_NAME, systemName.toString())
      put(SYSTEM_NICKNAME, contact.systemNickname.orElse(null))
      put(PROFILE_KEY, contact.profileKey.map { source -> Base64.encodeWithPadding(source) }.orElse(null))
      put(USERNAME, if (TextUtils.isEmpty(username)) null else username)
      put(PROFILE_SHARING, if (contact.isProfileSharingEnabled) "1" else "0")
      put(BLOCKED, if (contact.isBlocked) "1" else "0")
      put(MUTE_UNTIL, contact.muteUntil)
      put(STORAGE_SERVICE_ID, Base64.encodeWithPadding(contact.id.raw))
      put(HIDDEN, contact.isHidden)
      put(PNI_SIGNATURE_VERIFIED, contact.isPniSignatureVerified.toInt())
      put(NICKNAME_GIVEN_NAME, nickname.givenName.nullIfBlank())
      put(NICKNAME_FAMILY_NAME, nickname.familyName.nullIfBlank())
      put(NICKNAME_JOINED_NAME, nickname.toString().nullIfBlank())
      put(NOTE, contact.note.orNull().nullIfBlank())

      if (contact.hasUnknownFields()) {
        put(STORAGE_SERVICE_PROTO, Base64.encodeWithPadding(Objects.requireNonNull(contact.serializeUnknownFields())))
      } else {
        putNull(STORAGE_SERVICE_PROTO)
      }

      put(UNREGISTERED_TIMESTAMP, contact.unregisteredTimestamp)
      if (contact.unregisteredTimestamp > 0L) {
        put(REGISTERED, RegisteredState.NOT_REGISTERED.id)
      } else if (contact.aci.isPresent) {
        put(REGISTERED, RegisteredState.REGISTERED.id)
      } else {
        Log.w(TAG, "Contact is marked as registered, but has no serviceId! Can't locally mark registered. (Phone: ${contact.number.orElse("null")}, Username: ${username?.isNotEmpty()})")
      }

      if (isInsert) {
        put(AVATAR_COLOR, AvatarColorHash.forAddress(contact.aci.map { it.toString() }.or(contact.pni.map { it.toString() }).orNull(), contact.number.orNull()).serialize())
      }
    }
  }

  private fun getValuesForStorageGroupV1(groupV1: SignalGroupV1Record, isInsert: Boolean): ContentValues {
    return ContentValues().apply {
      val groupId = GroupId.v1orThrow(groupV1.groupId)

      put(GROUP_ID, groupId.toString())
      put(TYPE, RecipientType.GV1.id)
      put(PROFILE_SHARING, if (groupV1.isProfileSharingEnabled) "1" else "0")
      put(BLOCKED, if (groupV1.isBlocked) "1" else "0")
      put(MUTE_UNTIL, groupV1.muteUntil)
      put(STORAGE_SERVICE_ID, Base64.encodeWithPadding(groupV1.id.raw))

      if (groupV1.hasUnknownFields()) {
        put(STORAGE_SERVICE_PROTO, Base64.encodeWithPadding(groupV1.serializeUnknownFields()))
      } else {
        putNull(STORAGE_SERVICE_PROTO)
      }

      if (isInsert) {
        put(AVATAR_COLOR, AvatarColorHash.forGroupId(groupId).serialize())
      }
    }
  }

  private fun getValuesForStorageGroupV2(groupV2: SignalGroupV2Record, isInsert: Boolean): ContentValues {
    return ContentValues().apply {
      val groupId = GroupId.v2(groupV2.masterKeyOrThrow)

      put(GROUP_ID, groupId.toString())
      put(TYPE, RecipientType.GV2.id)
      put(PROFILE_SHARING, if (groupV2.isProfileSharingEnabled) "1" else "0")
      put(BLOCKED, if (groupV2.isBlocked) "1" else "0")
      put(MUTE_UNTIL, groupV2.muteUntil)
      put(STORAGE_SERVICE_ID, Base64.encodeWithPadding(groupV2.id.raw))
      put(MENTION_SETTING, if (groupV2.notifyForMentionsWhenMuted()) MentionSetting.ALWAYS_NOTIFY.id else MentionSetting.DO_NOT_NOTIFY.id)

      if (groupV2.hasUnknownFields()) {
        put(STORAGE_SERVICE_PROTO, Base64.encodeWithPadding(groupV2.serializeUnknownFields()))
      } else {
        putNull(STORAGE_SERVICE_PROTO)
      }

      if (isInsert) {
        put(AVATAR_COLOR, AvatarColorHash.forGroupId(groupId).serialize())
      }
    }
  }

  /**
   * Should be called immediately after we create a recipient for self.
   * This clears up any placeholders we put in the database for the local user, which is typically only done in database migrations.
   */
  fun updatePendingSelfData(selfId: RecipientId) {
    SignalDatabase.messages.updatePendingSelfData(RecipientId.from(PLACEHOLDER_SELF_ID), selfId)

    val deletes = writableDatabase
      .delete(TABLE_NAME)
      .where("$ID = ?", PLACEHOLDER_SELF_ID)
      .run()

    if (deletes > 0) {
      Log.w(TAG, "Deleted a PLACEHOLDER_SELF from the table.")
    } else {
      Log.i(TAG, "No PLACEHOLDER_SELF in the table.")
    }
  }

  /**
   * Should only be used for debugging! A very destructive action that clears all known serviceIds from people with phone numbers (so that we could eventually
   * get them back through CDS).
   */
  fun debugClearServiceIds(recipientId: RecipientId? = null) {
    check(RemoteConfig.internalUser)

    writableDatabase
      .update(TABLE_NAME)
      .values(
        ACI_COLUMN to null,
        PNI_COLUMN to null
      )
      .run {
        if (recipientId == null) {
          where("$ID != ? AND $E164 NOT NULL", Recipient.self().id)
        } else {
          where("$ID = ? AND $E164 NOT NULL", recipientId)
        }
      }
      .run()

    AppDependencies.recipientCache.clear()
    RecipientId.clearCache()
  }

  /**
   * Should only be used for debugging! A very destructive action that clears all known profile keys and credentials.
   */
  fun debugClearProfileData(recipientId: RecipientId? = null) {
    check(RemoteConfig.internalUser)

    writableDatabase
      .update(TABLE_NAME)
      .values(
        PROFILE_KEY to null,
        EXPIRING_PROFILE_KEY_CREDENTIAL to null,
        PROFILE_GIVEN_NAME to null,
        PROFILE_FAMILY_NAME to null,
        PROFILE_JOINED_NAME to null,
        LAST_PROFILE_FETCH to 0,
        PROFILE_AVATAR to null,
        PROFILE_SHARING to 0
      )
      .run {
        if (recipientId == null) {
          where("$ID != ?", Recipient.self().id)
        } else {
          where("$ID = ?", recipientId)
        }
      }
      .run()

    AppDependencies.recipientCache.clear()
    RecipientId.clearCache()
  }

  /**
   * Should only be used for debugging! Clears the E164 and PNI from a recipient.
   */
  fun debugClearE164AndPni(recipientId: RecipientId) {
    check(RemoteConfig.internalUser)

    writableDatabase
      .update(TABLE_NAME)
      .values(
        E164 to null,
        PNI_COLUMN to null
      )
      .where(ID_WHERE, recipientId)
      .run()

    AppDependencies.recipientCache.clear()
    RecipientId.clearCache()
  }

  /**
   * Should only be used for debugging! Clears the ACI from a contact.
   * Only works if the recipient has a PNI.
   */
  fun debugRemoveAci(recipientId: RecipientId) {
    check(RemoteConfig.internalUser)

    writableDatabase.execSQL(
      """
        UPDATE $TABLE_NAME
        SET $ACI_COLUMN = $PNI_COLUMN
        WHERE $ID = ? AND $PNI_COLUMN NOT NULL
      """,
      SqlUtil.buildArgs(recipientId)
    )

    AppDependencies.recipientCache.clear()
    RecipientId.clearCache()
  }

  private fun updateProfileValuesForMerge(values: ContentValues, record: RecipientRecord) {
    values.apply {
      put(PROFILE_KEY, if (record.profileKey != null) Base64.encodeWithPadding(record.profileKey) else null)
      putNull(EXPIRING_PROFILE_KEY_CREDENTIAL)
      put(PROFILE_AVATAR, record.signalProfileAvatar)
      put(PROFILE_GIVEN_NAME, record.signalProfileName.givenName)
      put(PROFILE_FAMILY_NAME, record.signalProfileName.familyName)
      put(PROFILE_JOINED_NAME, record.signalProfileName.toString())
    }
  }

  /**
   * By default, SQLite will prefer numbers over letters when sorting. e.g. (b, a, 1) is sorted as (1, a, b).
   * This order by will using a GLOB pattern to instead sort it as (a, b, 1).
   *
   * @param column The name of the column to sort by
   */
  private fun orderByPreferringAlphaOverNumeric(column: String): String {
    return "CASE WHEN $column GLOB '[0-9]*' THEN 1 ELSE 0 END, $column"
  }

  private fun <T> Optional<T>.isAbsent(): Boolean {
    return !this.isPresent
  }

  private data class MergeResult(
    val finalId: RecipientId,
    val neededThreadMerge: Boolean
  )

  inner class BulkOperationsHandle internal constructor(private val database: SQLiteDatabase) {
    private val pendingRecipients: MutableSet<RecipientId> = mutableSetOf()

    fun setSystemContactInfo(
      id: RecipientId,
      systemProfileName: ProfileName,
      systemDisplayName: String?,
      photoUri: String?,
      systemPhoneLabel: String?,
      systemPhoneType: Int,
      systemContactUri: String?
    ) {
      val joinedName = Util.firstNonNull(systemDisplayName, systemProfileName.toString())
      val refreshQualifyingValues = ContentValues().apply {
        put(SYSTEM_GIVEN_NAME, systemProfileName.givenName)
        put(SYSTEM_FAMILY_NAME, systemProfileName.familyName)
        put(SYSTEM_JOINED_NAME, joinedName)
        put(SYSTEM_PHOTO_URI, photoUri)
        put(SYSTEM_PHONE_LABEL, systemPhoneLabel)
        put(SYSTEM_PHONE_TYPE, systemPhoneType)
        put(SYSTEM_CONTACT_URI, systemContactUri)
      }

      val updateQuery = SqlUtil.buildTrueUpdateQuery("$ID = ? AND $PHONE_NUMBER_DISCOVERABLE != ?", SqlUtil.buildArgs(id, PhoneNumberDiscoverableState.NOT_DISCOVERABLE.id), refreshQualifyingValues)
      if (update(updateQuery, refreshQualifyingValues)) {
        pendingRecipients.add(id)
      }

      writableDatabase
        .update(TABLE_NAME)
        .values(SYSTEM_INFO_PENDING to 0)
        .where("$ID = ? AND $PHONE_NUMBER_DISCOVERABLE != ?", id, PhoneNumberDiscoverableState.NOT_DISCOVERABLE.id)
        .run()
    }

    fun finish() {
      markAllRelevantEntriesDirty()
      clearSystemDataForPendingInfo()
      database.setTransactionSuccessful()
      database.endTransaction()
      pendingRecipients.forEach { id -> AppDependencies.databaseObserver.notifyRecipientChanged(id) }
    }

    private fun markAllRelevantEntriesDirty() {
      val query = "$SYSTEM_INFO_PENDING = ? AND $STORAGE_SERVICE_ID NOT NULL"
      val args = SqlUtil.buildArgs("1")

      database.query(TABLE_NAME, ID_PROJECTION, query, args, null, null, null).use { cursor ->
        while (cursor.moveToNext()) {
          val id = RecipientId.from(cursor.requireNonNullString(ID))
          rotateStorageId(id)
        }
      }

      pendingRecipients.forEach { id -> rotateStorageId(id) }
    }

    private fun clearSystemDataForPendingInfo() {
      writableDatabase.rawQuery(
        """
        UPDATE $TABLE_NAME
        SET
          $SYSTEM_INFO_PENDING = 0,
          $SYSTEM_GIVEN_NAME = NULL,
          $SYSTEM_FAMILY_NAME = NULL,
          $SYSTEM_JOINED_NAME = NULL,
          $SYSTEM_PHOTO_URI = NULL,
          $SYSTEM_PHONE_LABEL = NULL,
          $SYSTEM_CONTACT_URI = NULL
        WHERE $SYSTEM_INFO_PENDING = 1
        RETURNING $ID
        """,
        null
      ).forEach { cursor ->
        val id = RecipientId.from(cursor.requireLong(ID))
        AppDependencies.databaseObserver.notifyRecipientChanged(id)
      }
    }
  }

  interface ColorUpdater {
    fun update(name: String, materialColor: MaterialColor?): ChatColors?
  }

  class RecipientReader internal constructor(private val cursor: Cursor) : Closeable {

    fun getCurrent(): Recipient {
      val id = RecipientId.from(cursor.requireLong(ID))
      return Recipient.resolved(id)
    }

    fun getNext(): Recipient? {
      return if (cursor.moveToNext()) {
        getCurrent()
      } else {
        null
      }
    }

    val count: Int
      get() = cursor.count

    override fun close() {
      cursor.close()
    }
  }

  class RecipientIterator(
    private val context: Context,
    private val cursor: Cursor
  ) : Iterator<RecipientRecord>, Closeable {

    override fun hasNext(): Boolean {
      return cursor.count != 0 && !cursor.isLast
    }

    override fun next(): RecipientRecord {
      if (!cursor.moveToNext()) {
        throw NoSuchElementException()
      }

      return RecipientTableCursorUtil.getRecord(context, cursor)
    }

    override fun close() {
      cursor.close()
    }
  }

  class MissingRecipientException(id: RecipientId?) : IllegalStateException("Failed to find recipient with ID: $id")

  private class GetOrInsertResult(val recipientId: RecipientId, val neededInsert: Boolean)

  data class ContactSearchQuery(
    val query: String,
    val includeSelf: Boolean,
    val contactSearchSortOrder: ContactSearchSortOrder = ContactSearchSortOrder.NATURAL
  )

  @VisibleForTesting
  internal class ContactSearchSelection private constructor(val where: String, val args: Array<String>) {

    @VisibleForTesting
    internal class Builder {
      private var includeRegistered = false
      private var includeNonRegistered = false
      private var includeGroupMembers = false
      private var excludeId: RecipientId? = null
      private var excludeGroups = false
      private var searchQuery: String? = null

      fun withRegistered(includeRegistered: Boolean): Builder {
        this.includeRegistered = includeRegistered
        return this
      }

      fun withNonRegistered(includeNonRegistered: Boolean): Builder {
        this.includeNonRegistered = includeNonRegistered
        return this
      }

      fun withGroupMembers(includeGroupMembers: Boolean): Builder {
        this.includeGroupMembers = includeGroupMembers
        return this
      }

      fun excludeId(recipientId: RecipientId?): Builder {
        excludeId = recipientId
        return this
      }

      fun withGroups(includeGroups: Boolean): Builder {
        excludeGroups = !includeGroups
        return this
      }

      fun withSearchQuery(searchQuery: String): Builder {
        this.searchQuery = searchQuery
        return this
      }

      fun build(): ContactSearchSelection {
        check(!(!includeRegistered && !includeNonRegistered && !includeGroupMembers)) { "Must include either registered, non-registered, or group member recipients in search" }
        val stringBuilder = StringBuilder("(")
        val args: MutableList<Any?> = LinkedList()
        var hasPreceedingSection = false

        if (includeRegistered) {
          hasPreceedingSection = true
          stringBuilder.append("(")
          args.add(RegisteredState.REGISTERED.id)
          args.add(1)
          if (Util.isEmpty(searchQuery)) {
            stringBuilder.append(SIGNAL_CONTACT)
          } else {
            stringBuilder.append(QUERY_SIGNAL_CONTACT)
            args.add(searchQuery)
            args.add(searchQuery)
            args.add(searchQuery)
          }
          stringBuilder.append(")")
        }

        if (hasPreceedingSection && includeNonRegistered) {
          stringBuilder.append(" OR ")
        }

        if (includeNonRegistered) {
          hasPreceedingSection = true
          stringBuilder.append("(")
          args.add(RegisteredState.REGISTERED.id)

          if (Util.isEmpty(searchQuery)) {
            stringBuilder.append(NON_SIGNAL_CONTACT)
          } else {
            stringBuilder.append(QUERY_NON_SIGNAL_CONTACT)
            args.add(searchQuery)
            args.add(searchQuery)
            args.add(searchQuery)
          }

          stringBuilder.append(")")
        }

        if (hasPreceedingSection && includeGroupMembers) {
          stringBuilder.append(" OR ")
        }

        if (includeGroupMembers) {
          stringBuilder.append("(")
          args.add(RegisteredState.REGISTERED.id)
          args.add(1)
          if (Util.isEmpty(searchQuery)) {
            stringBuilder.append(GROUP_MEMBER_CONTACT)
          } else {
            stringBuilder.append(QUERY_GROUP_MEMBER_CONTACT)
            args.add(searchQuery)
            args.add(searchQuery)
            args.add(searchQuery)
          }

          stringBuilder.append(")")
        }

        stringBuilder.append(")")
        stringBuilder.append(FILTER_BLOCKED)
        args.add(0)

        stringBuilder.append(FILTER_HIDDEN)
        args.add(0)

        if (excludeGroups) {
          stringBuilder.append(FILTER_GROUPS)
        }

        if (excludeId != null) {
          stringBuilder.append(FILTER_ID)
          args.add(excludeId!!.serialize())
        }

        return ContactSearchSelection(stringBuilder.toString(), args.map { obj: Any? -> obj.toString() }.toTypedArray())
      }
    }

    companion object {
      //language=sql
      private val HAS_GROUP_IN_COMMON = """
        EXISTS (
            SELECT 1 
            FROM ${GroupTable.MembershipTable.TABLE_NAME}
            INNER JOIN ${GroupTable.TABLE_NAME} ON ${GroupTable.TABLE_NAME}.${GroupTable.GROUP_ID} = ${GroupTable.MembershipTable.TABLE_NAME}.${GroupTable.MembershipTable.GROUP_ID}
            WHERE ${GroupTable.MembershipTable.TABLE_NAME}.${GroupTable.MembershipTable.RECIPIENT_ID} = $TABLE_NAME.$ID AND ${GroupTable.TABLE_NAME}.${GroupTable.ACTIVE} = 1 AND ${GroupTable.TABLE_NAME}.${GroupTable.MMS} = 0
        )
      """
      val E164_SEARCH = "(($PHONE_NUMBER_SHARING != ${PhoneNumberSharingState.DISABLED.id} OR $SYSTEM_CONTACT_URI NOT NULL) AND $E164 GLOB ?)"
      const val FILTER_GROUPS = " AND $GROUP_ID IS NULL"
      const val FILTER_ID = " AND $ID != ?"
      const val FILTER_BLOCKED = " AND $BLOCKED = ?"
      const val FILTER_HIDDEN = " AND $HIDDEN = ?"
      const val NON_SIGNAL_CONTACT = "$REGISTERED != ? AND $SYSTEM_CONTACT_URI NOT NULL AND ($E164 NOT NULL OR $EMAIL NOT NULL)"
      val QUERY_NON_SIGNAL_CONTACT = "$NON_SIGNAL_CONTACT AND ($E164_SEARCH OR $EMAIL GLOB ? OR $SYSTEM_JOINED_NAME GLOB ?)"
      const val SIGNAL_CONTACT = "$REGISTERED = ? AND (NULLIF($SYSTEM_JOINED_NAME, '') NOT NULL OR $PROFILE_SHARING = ?) AND ($SORT_NAME NOT NULL OR $USERNAME NOT NULL)"
      val QUERY_SIGNAL_CONTACT = "$SIGNAL_CONTACT AND ($E164_SEARCH OR $SORT_NAME GLOB ? OR $USERNAME GLOB ?)"
      val GROUP_MEMBER_CONTACT = "$REGISTERED = ? AND $HAS_GROUP_IN_COMMON AND NOT (NULLIF($SYSTEM_JOINED_NAME, '') NOT NULL OR $PROFILE_SHARING = ?) AND ($SORT_NAME NOT NULL OR $USERNAME NOT NULL)"
      val QUERY_GROUP_MEMBER_CONTACT = "$GROUP_MEMBER_CONTACT AND ($E164_SEARCH OR $SORT_NAME GLOB ? OR $USERNAME GLOB ?)"
    }
  }

  /**
   * Values that represent the index in the capabilities bitmask. Each index can store a 2-bit
   * value, which in this case is the value of [Recipient.Capability].
   */
  internal object Capabilities {
    const val BIT_LENGTH = 2

//    const val GROUPS_V2 = 0
//    const val GROUPS_V1_MIGRATION = 1
//    const val SENDER_KEY = 2
//    const val ANNOUNCEMENT_GROUPS = 3
//    const val CHANGE_NUMBER = 4
//    const val STORIES = 5
//    const val GIFT_BADGES = 6
//    const val PNP = 7
//    const val PAYMENT_ACTIVATION = 8
    const val DELETE_SYNC = 9

    // IMPORTANT: We cannot sore more than 32 capabilities in the bitmask.
  }

  enum class VibrateState(val id: Int) {
    DEFAULT(0),
    ENABLED(1),
    DISABLED(2);

    companion object {
      fun fromId(id: Int): VibrateState {
        return values()[id]
      }

      fun fromBoolean(enabled: Boolean): VibrateState {
        return if (enabled) ENABLED else DISABLED
      }
    }
  }

  enum class RegisteredState(val id: Int) {
    UNKNOWN(0),
    REGISTERED(1),
    NOT_REGISTERED(2);

    companion object {
      fun fromId(id: Int): RegisteredState {
        return values()[id]
      }
    }
  }

  enum class SealedSenderAccessMode(val mode: Int) {
    UNKNOWN(0),
    DISABLED(1),
    ENABLED(2),
    UNRESTRICTED(3);

    companion object {
      fun fromMode(mode: Int): SealedSenderAccessMode {
        return values()[mode]
      }
    }
  }

  enum class InsightsBannerTier(val id: Int) {
    NO_TIER(0),
    TIER_ONE(1),
    TIER_TWO(2);

    fun seen(tier: InsightsBannerTier): Boolean {
      return tier.id <= id
    }

    companion object {
      fun fromId(id: Int): InsightsBannerTier {
        return values()[id]
      }
    }
  }

  enum class RecipientType(val id: Int) {
    INDIVIDUAL(0),
    MMS(1),
    GV1(2),
    GV2(3),
    DISTRIBUTION_LIST(4),
    CALL_LINK(5);

    companion object {
      fun fromId(id: Int): RecipientType {
        return values()[id]
      }
    }
  }

  enum class MentionSetting(val id: Int) {
    ALWAYS_NOTIFY(0),
    DO_NOT_NOTIFY(1);

    companion object {
      fun fromId(id: Int): MentionSetting {
        return values()[id]
      }
    }
  }

  enum class PhoneNumberSharingState(val id: Int) {
    UNKNOWN(0),
    ENABLED(1),
    DISABLED(2);

    val enabled
      get() = this == ENABLED || this == UNKNOWN

    companion object {
      fun fromId(id: Int): PhoneNumberSharingState {
        return values()[id]
      }
    }
  }

  enum class PhoneNumberDiscoverableState(val id: Int) {
    UNKNOWN(0),
    DISCOVERABLE(1),
    NOT_DISCOVERABLE(2);

    companion object {
      fun fromId(id: Int): PhoneNumberDiscoverableState {
        return PhoneNumberDiscoverableState.values()[id]
      }
    }
  }

  data class CdsV2Result(
    val pni: PNI,
    val aci: ACI?
  )

  data class ProcessPnpTupleResult(
    val finalId: RecipientId,
    val requiredInsert: Boolean,
    val affectedIds: Set<RecipientId>,
    val oldIds: Set<RecipientId>,
    val changedNumberId: RecipientId?,
    val operations: List<PnpOperation>,
    val breadCrumbs: List<String>
  )

  class SseWithSelfAci(cause: Exception) : IllegalStateException(cause)
  class SseWithSelfAciNoSession(cause: Exception) : IllegalStateException(cause)
  class SseWithSelfPni(cause: Exception) : IllegalStateException(cause)
  class SseWithSelfPniNoSession(cause: Exception) : IllegalStateException(cause)
  class SseWithSelfE164(cause: Exception) : IllegalStateException(cause)
  class SseWithSelfE164NoSession(cause: Exception) : IllegalStateException(cause)
  class SseWithNoPniSessionsException(cause: Exception) : IllegalStateException(cause)
  class SseWithASinglePniSessionForSelfException(cause: Exception) : IllegalStateException(cause)
  class SseWithASinglePniSessionException(cause: Exception) : IllegalStateException(cause)
  class SseWithMultiplePniSessionsException(cause: Exception) : IllegalStateException(cause)
}
