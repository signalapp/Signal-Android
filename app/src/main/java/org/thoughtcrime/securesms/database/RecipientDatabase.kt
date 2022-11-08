package org.thoughtcrime.securesms.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.text.TextUtils
import androidx.annotation.VisibleForTesting
import androidx.core.content.contentValuesOf
import app.cash.exhaustive.Exhaustive
import com.google.protobuf.ByteString
import com.google.protobuf.InvalidProtocolBufferException
import net.zetetic.database.sqlcipher.SQLiteConstraintException
import org.signal.core.util.Bitmask
import org.signal.core.util.CursorUtil
import org.signal.core.util.SqlUtil
import org.signal.core.util.exists
import org.signal.core.util.logging.Log
import org.signal.core.util.optionalBlob
import org.signal.core.util.optionalBoolean
import org.signal.core.util.optionalInt
import org.signal.core.util.optionalLong
import org.signal.core.util.optionalString
import org.signal.core.util.or
import org.signal.core.util.readToSet
import org.signal.core.util.requireBlob
import org.signal.core.util.requireBoolean
import org.signal.core.util.requireInt
import org.signal.core.util.requireLong
import org.signal.core.util.requireNonNullString
import org.signal.core.util.requireString
import org.signal.core.util.select
import org.signal.core.util.update
import org.signal.core.util.withinTransaction
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.InvalidKeyException
import org.signal.libsignal.zkgroup.InvalidInputException
import org.signal.libsignal.zkgroup.profiles.ExpiringProfileKeyCredential
import org.signal.libsignal.zkgroup.profiles.ProfileKey
import org.signal.storageservice.protos.groups.local.DecryptedGroup
import org.thoughtcrime.securesms.badges.Badges
import org.thoughtcrime.securesms.badges.Badges.toDatabaseBadge
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.color.MaterialColor
import org.thoughtcrime.securesms.color.MaterialColor.UnknownColorException
import org.thoughtcrime.securesms.conversation.colors.AvatarColor
import org.thoughtcrime.securesms.conversation.colors.ChatColors
import org.thoughtcrime.securesms.conversation.colors.ChatColors.Companion.forChatColor
import org.thoughtcrime.securesms.conversation.colors.ChatColors.Id.Companion.forLongValue
import org.thoughtcrime.securesms.conversation.colors.ChatColorsMapper.getChatColors
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil
import org.thoughtcrime.securesms.database.GroupDatabase.LegacyGroupInsertException
import org.thoughtcrime.securesms.database.GroupDatabase.MissedGroupMigrationInsertException
import org.thoughtcrime.securesms.database.GroupDatabase.ShowAsStoryState
import org.thoughtcrime.securesms.database.IdentityDatabase.VerifiedStatus
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
import org.thoughtcrime.securesms.database.model.databaseprotos.ThreadMergeEvent
import org.thoughtcrime.securesms.database.model.databaseprotos.Wallpaper
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.groups.BadGroupIdException
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.groups.GroupId.V1
import org.thoughtcrime.securesms.groups.GroupId.V2
import org.thoughtcrime.securesms.groups.v2.ProfileKeySet
import org.thoughtcrime.securesms.groups.v2.processing.GroupsV2StateProcessor
import org.thoughtcrime.securesms.jobs.RecipientChangedNumberJob
import org.thoughtcrime.securesms.jobs.RequestGroupV2InfoJob
import org.thoughtcrime.securesms.jobs.RetrieveProfileJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.profiles.AvatarHelper
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.storage.StorageRecordUpdate
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.storage.StorageSyncModels
import org.thoughtcrime.securesms.util.Base64
import org.thoughtcrime.securesms.util.FeatureFlags
import org.thoughtcrime.securesms.util.GroupUtil
import org.thoughtcrime.securesms.util.IdentityUtil
import org.thoughtcrime.securesms.util.ProfileUtil
import org.thoughtcrime.securesms.util.Util
import org.thoughtcrime.securesms.wallpaper.ChatWallpaper
import org.thoughtcrime.securesms.wallpaper.ChatWallpaperFactory
import org.thoughtcrime.securesms.wallpaper.WallpaperStorage
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile
import org.whispersystems.signalservice.api.push.ACI
import org.whispersystems.signalservice.api.push.PNI
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.api.storage.SignalAccountRecord
import org.whispersystems.signalservice.api.storage.SignalContactRecord
import org.whispersystems.signalservice.api.storage.SignalGroupV1Record
import org.whispersystems.signalservice.api.storage.SignalGroupV2Record
import org.whispersystems.signalservice.api.storage.StorageId
import org.whispersystems.signalservice.internal.storage.protos.GroupV2Record
import java.io.Closeable
import java.io.IOException
import java.util.Arrays
import java.util.Collections
import java.util.LinkedList
import java.util.Objects
import java.util.Optional
import java.util.concurrent.TimeUnit
import kotlin.math.max

open class RecipientDatabase(context: Context, databaseHelper: SignalDatabase) : Database(context, databaseHelper) {

  companion object {
    private val TAG = Log.tag(RecipientDatabase::class.java)

    private val UNREGISTERED_LIFESPAN: Long = TimeUnit.DAYS.toMillis(30)

    const val TABLE_NAME = "recipient"

    const val ID = "_id"
    const val SERVICE_ID = "uuid"
    const val PNI_COLUMN = "pni"
    private const val USERNAME = "username"
    const val PHONE = "phone"
    const val EMAIL = "email"
    const val GROUP_ID = "group_id"
    const val DISTRIBUTION_LIST_ID = "distribution_list_id"
    const val GROUP_TYPE = "group_type"
    const val BLOCKED = "blocked"
    private const val MESSAGE_RINGTONE = "message_ringtone"
    private const val MESSAGE_VIBRATE = "message_vibrate"
    private const val CALL_RINGTONE = "call_ringtone"
    private const val CALL_VIBRATE = "call_vibrate"
    private const val NOTIFICATION_CHANNEL = "notification_channel"
    private const val MUTE_UNTIL = "mute_until"
    private const val AVATAR_COLOR = "color"
    private const val SEEN_INVITE_REMINDER = "seen_invite_reminder"
    private const val DEFAULT_SUBSCRIPTION_ID = "default_subscription_id"
    private const val MESSAGE_EXPIRATION_TIME = "message_expiration_time"
    const val REGISTERED = "registered"
    const val SYSTEM_JOINED_NAME = "system_display_name"
    const val SYSTEM_FAMILY_NAME = "system_family_name"
    const val SYSTEM_GIVEN_NAME = "system_given_name"
    private const val SYSTEM_PHOTO_URI = "system_photo_uri"
    const val SYSTEM_PHONE_TYPE = "system_phone_type"
    const val SYSTEM_PHONE_LABEL = "system_phone_label"
    private const val SYSTEM_CONTACT_URI = "system_contact_uri"
    private const val SYSTEM_INFO_PENDING = "system_info_pending"
    private const val PROFILE_KEY = "profile_key"
    const val EXPIRING_PROFILE_KEY_CREDENTIAL = "profile_key_credential"
    private const val SIGNAL_PROFILE_AVATAR = "signal_profile_avatar"
    const val PROFILE_SHARING = "profile_sharing"
    private const val LAST_PROFILE_FETCH = "last_profile_fetch"
    private const val UNIDENTIFIED_ACCESS_MODE = "unidentified_access_mode"
    const val FORCE_SMS_SELECTION = "force_sms_selection"
    private const val CAPABILITIES = "capabilities"
    const val STORAGE_SERVICE_ID = "storage_service_key"
    private const val PROFILE_GIVEN_NAME = "signal_profile_name"
    private const val PROFILE_FAMILY_NAME = "profile_family_name"
    private const val PROFILE_JOINED_NAME = "profile_joined_name"
    private const val MENTION_SETTING = "mention_setting"
    private const val STORAGE_PROTO = "storage_proto"
    private const val LAST_SESSION_RESET = "last_session_reset"
    private const val WALLPAPER = "wallpaper"
    private const val WALLPAPER_URI = "wallpaper_file"
    const val ABOUT = "about"
    const val ABOUT_EMOJI = "about_emoji"
    private const val EXTRAS = "extras"
    private const val GROUPS_IN_COMMON = "groups_in_common"
    private const val CHAT_COLORS = "chat_colors"
    private const val CUSTOM_CHAT_COLORS_ID = "custom_chat_colors_id"
    private const val BADGES = "badges"
    const val SEARCH_PROFILE_NAME = "search_signal_profile"
    private const val SORT_NAME = "sort_name"
    private const val IDENTITY_STATUS = "identity_status"
    private const val IDENTITY_KEY = "identity_key"
    private const val NEEDS_PNI_SIGNATURE = "needs_pni_signature"
    private const val UNREGISTERED_TIMESTAMP = "unregistered_timestamp"
    private const val HIDDEN = "hidden"

    @JvmField
    val CREATE_TABLE =
      """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY AUTOINCREMENT,
        $SERVICE_ID TEXT UNIQUE DEFAULT NULL,
        $USERNAME TEXT UNIQUE DEFAULT NULL,
        $PHONE TEXT UNIQUE DEFAULT NULL,
        $EMAIL TEXT UNIQUE DEFAULT NULL,
        $GROUP_ID TEXT UNIQUE DEFAULT NULL,
        $GROUP_TYPE INTEGER DEFAULT ${GroupType.NONE.id},
        $BLOCKED INTEGER DEFAULT 0,
        $MESSAGE_RINGTONE TEXT DEFAULT NULL, 
        $MESSAGE_VIBRATE INTEGER DEFAULT ${VibrateState.DEFAULT.id}, 
        $CALL_RINGTONE TEXT DEFAULT NULL, 
        $CALL_VIBRATE INTEGER DEFAULT ${VibrateState.DEFAULT.id}, 
        $NOTIFICATION_CHANNEL TEXT DEFAULT NULL, 
        $MUTE_UNTIL INTEGER DEFAULT 0, 
        $AVATAR_COLOR TEXT DEFAULT NULL, 
        $SEEN_INVITE_REMINDER INTEGER DEFAULT ${InsightsBannerTier.NO_TIER.id},
        $DEFAULT_SUBSCRIPTION_ID INTEGER DEFAULT -1,
        $MESSAGE_EXPIRATION_TIME INTEGER DEFAULT 0,
        $REGISTERED INTEGER DEFAULT ${RegisteredState.UNKNOWN.id},
        $SYSTEM_GIVEN_NAME TEXT DEFAULT NULL, 
        $SYSTEM_FAMILY_NAME TEXT DEFAULT NULL, 
        $SYSTEM_JOINED_NAME TEXT DEFAULT NULL, 
        $SYSTEM_PHOTO_URI TEXT DEFAULT NULL, 
        $SYSTEM_PHONE_LABEL TEXT DEFAULT NULL, 
        $SYSTEM_PHONE_TYPE INTEGER DEFAULT -1, 
        $SYSTEM_CONTACT_URI TEXT DEFAULT NULL, 
        $SYSTEM_INFO_PENDING INTEGER DEFAULT 0, 
        $PROFILE_KEY TEXT DEFAULT NULL, 
        $EXPIRING_PROFILE_KEY_CREDENTIAL TEXT DEFAULT NULL, 
        $PROFILE_GIVEN_NAME TEXT DEFAULT NULL, 
        $PROFILE_FAMILY_NAME TEXT DEFAULT NULL, 
        $PROFILE_JOINED_NAME TEXT DEFAULT NULL, 
        $SIGNAL_PROFILE_AVATAR TEXT DEFAULT NULL, 
        $PROFILE_SHARING INTEGER DEFAULT 0, 
        $LAST_PROFILE_FETCH INTEGER DEFAULT 0, 
        $UNIDENTIFIED_ACCESS_MODE INTEGER DEFAULT 0, 
        $FORCE_SMS_SELECTION INTEGER DEFAULT 0, 
        $STORAGE_SERVICE_ID TEXT UNIQUE DEFAULT NULL, 
        $MENTION_SETTING INTEGER DEFAULT ${MentionSetting.ALWAYS_NOTIFY.id}, 
        $STORAGE_PROTO TEXT DEFAULT NULL,
        $CAPABILITIES INTEGER DEFAULT 0,
        $LAST_SESSION_RESET BLOB DEFAULT NULL,
        $WALLPAPER BLOB DEFAULT NULL,
        $WALLPAPER_URI TEXT DEFAULT NULL,
        $ABOUT TEXT DEFAULT NULL,
        $ABOUT_EMOJI TEXT DEFAULT NULL,
        $EXTRAS BLOB DEFAULT NULL,
        $GROUPS_IN_COMMON INTEGER DEFAULT 0,
        $CHAT_COLORS BLOB DEFAULT NULL,
        $CUSTOM_CHAT_COLORS_ID INTEGER DEFAULT 0,
        $BADGES BLOB DEFAULT NULL,
        $PNI_COLUMN TEXT DEFAULT NULL,
        $DISTRIBUTION_LIST_ID INTEGER DEFAULT NULL,
        $NEEDS_PNI_SIGNATURE INTEGER DEFAULT 0,
        $UNREGISTERED_TIMESTAMP INTEGER DEFAULT 0,
        $HIDDEN INTEGER DEFAULT 0
      )
      """.trimIndent()

    val CREATE_INDEXS = arrayOf(
      "CREATE INDEX IF NOT EXISTS recipient_group_type_index ON $TABLE_NAME ($GROUP_TYPE);",
      "CREATE UNIQUE INDEX IF NOT EXISTS recipient_pni_index ON $TABLE_NAME ($PNI_COLUMN)",
      "CREATE INDEX IF NOT EXISTS recipient_service_id_profile_key ON $TABLE_NAME ($SERVICE_ID, $PROFILE_KEY) WHERE $SERVICE_ID NOT NULL AND $PROFILE_KEY NOT NULL"
    )

    private val RECIPIENT_PROJECTION: Array<String> = arrayOf(
      ID,
      SERVICE_ID,
      PNI_COLUMN,
      USERNAME,
      PHONE,
      EMAIL,
      GROUP_ID,
      GROUP_TYPE,
      BLOCKED,
      MESSAGE_RINGTONE,
      CALL_RINGTONE,
      MESSAGE_VIBRATE,
      CALL_VIBRATE,
      MUTE_UNTIL,
      AVATAR_COLOR,
      SEEN_INVITE_REMINDER,
      DEFAULT_SUBSCRIPTION_ID,
      MESSAGE_EXPIRATION_TIME,
      REGISTERED,
      PROFILE_KEY,
      EXPIRING_PROFILE_KEY_CREDENTIAL,
      SYSTEM_JOINED_NAME,
      SYSTEM_GIVEN_NAME,
      SYSTEM_FAMILY_NAME,
      SYSTEM_PHOTO_URI,
      SYSTEM_PHONE_LABEL,
      SYSTEM_PHONE_TYPE,
      SYSTEM_CONTACT_URI,
      PROFILE_GIVEN_NAME,
      PROFILE_FAMILY_NAME,
      SIGNAL_PROFILE_AVATAR,
      PROFILE_SHARING,
      LAST_PROFILE_FETCH,
      NOTIFICATION_CHANNEL,
      UNIDENTIFIED_ACCESS_MODE,
      FORCE_SMS_SELECTION,
      CAPABILITIES,
      STORAGE_SERVICE_ID,
      MENTION_SETTING,
      WALLPAPER,
      WALLPAPER_URI,
      MENTION_SETTING,
      ABOUT,
      ABOUT_EMOJI,
      EXTRAS,
      GROUPS_IN_COMMON,
      CHAT_COLORS,
      CUSTOM_CHAT_COLORS_ID,
      BADGES,
      DISTRIBUTION_LIST_ID,
      NEEDS_PNI_SIGNATURE,
      HIDDEN
    )

    private val ID_PROJECTION = arrayOf(ID)

    private val SEARCH_PROJECTION = arrayOf(
      ID,
      SYSTEM_JOINED_NAME,
      PHONE,
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
          NULLIF($SYSTEM_JOINED_NAME, ''),
          NULLIF($SYSTEM_GIVEN_NAME, ''),
          NULLIF($PROFILE_JOINED_NAME, ''),
          NULLIF($PROFILE_GIVEN_NAME, ''),
          NULLIF($USERNAME, '')
        )
      ) AS $SORT_NAME
      """.trimIndent()
    )

    @JvmField
    val SEARCH_PROJECTION_NAMES = arrayOf(
      ID,
      SYSTEM_JOINED_NAME,
      PHONE,
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
          NULLIF($SYSTEM_JOINED_NAME, ''), 
          NULLIF($SYSTEM_GIVEN_NAME, ''), 
          NULLIF($PROFILE_JOINED_NAME, ''), 
          NULLIF($PROFILE_GIVEN_NAME, ''),
          NULLIF($USERNAME, ''),
          NULLIF($PHONE, '')
        ),
        ' ',
        ''
      ) AS $SORT_NAME
      """.trimIndent()
    )

    private val INSIGHTS_INVITEE_LIST =
      """
      SELECT $TABLE_NAME.$ID
      FROM $TABLE_NAME INNER JOIN ${ThreadDatabase.TABLE_NAME} ON $TABLE_NAME.$ID = ${ThreadDatabase.TABLE_NAME}.${ThreadDatabase.RECIPIENT_ID}
      WHERE 
        $TABLE_NAME.$GROUP_ID IS NULL AND
        $TABLE_NAME.$REGISTERED = ${RegisteredState.NOT_REGISTERED.id} AND
        $TABLE_NAME.$SEEN_INVITE_REMINDER < ${InsightsBannerTier.TIER_TWO.id} AND
        ${ThreadDatabase.TABLE_NAME}.${ThreadDatabase.HAS_SENT} AND
        ${ThreadDatabase.TABLE_NAME}.${ThreadDatabase.DATE} > ? AND
        $TABLE_NAME.$HIDDEN = 0
      ORDER BY ${ThreadDatabase.TABLE_NAME}.${ThreadDatabase.DATE} DESC LIMIT 50
      """
  }

  fun getByE164(e164: String): Optional<RecipientId> {
    return getByColumn(PHONE, e164)
  }

  fun getByGroupId(groupId: GroupId): Optional<RecipientId> {
    return getByColumn(GROUP_ID, groupId.toString())
  }

  fun getByServiceId(serviceId: ServiceId): Optional<RecipientId> {
    return getByColumn(SERVICE_ID, serviceId.toString())
  }

  /**
   * Will return a recipient matching the PNI, but only in the explicit [PNI_COLUMN]. This should only be checked in conjunction with [getByServiceId] as a way
   * to avoid creating a recipient we already merged.
   */
  fun getByPni(pni: PNI): Optional<RecipientId> {
    return getByColumn(PNI_COLUMN, pni.toString())
  }

  fun getByUsername(username: String): Optional<RecipientId> {
    return getByColumn(USERNAME, username)
  }

  fun isAssociated(serviceId: ServiceId, pni: PNI): Boolean {
    return readableDatabase.exists(TABLE_NAME, "$SERVICE_ID = ? AND $PNI_COLUMN = ?", serviceId.toString(), pni.toString())
  }

  @JvmOverloads
  fun getAndPossiblyMerge(serviceId: ServiceId?, e164: String?, changeSelf: Boolean = false): RecipientId {
    require(!(serviceId == null && e164 == null)) { "Must provide an ACI or E164!" }
    return getAndPossiblyMerge(serviceId = serviceId, pni = null, e164 = e164, pniVerified = false, changeSelf = changeSelf)
  }

  /**
   * Gets and merges a (serviceId, pni, e164) tuple, doing merges/updates as needed, and giving you back the final RecipientId.
   * It is assumed that the tuple is verified. Do not give this method an untrusted association.
   */
  fun getAndPossiblyMergePnpVerified(serviceId: ServiceId?, pni: PNI?, e164: String?): RecipientId {
    if (!FeatureFlags.phoneNumberPrivacy()) {
      throw AssertionError()
    }

    return getAndPossiblyMerge(serviceId = serviceId, pni = pni, e164 = e164, pniVerified = true, changeSelf = false)
  }

  private fun getAndPossiblyMerge(serviceId: ServiceId?, pni: PNI?, e164: String?, pniVerified: Boolean = false, changeSelf: Boolean = false): RecipientId {
    require(!(serviceId == null && e164 == null)) { "Must provide an ACI or E164!" }

    if ((serviceId is PNI) && pni != null && serviceId != pni) {
      throw AssertionError("Provided two non-matching PNIs! serviceId: $serviceId, pni: $pni")
    }

    val db = writableDatabase
    var transactionSuccessful = false
    lateinit var result: ProcessPnpTupleResult

    db.beginTransaction()
    try {
      result = when {
        serviceId is ACI -> processPnpTuple(e164 = e164, pni = pni, aci = serviceId, pniVerified = pniVerified, changeSelf = changeSelf)
        serviceId is PNI -> processPnpTuple(e164 = e164, pni = serviceId, aci = null, pniVerified = pniVerified, changeSelf = changeSelf)
        serviceId == null -> processPnpTuple(e164 = e164, pni = pni, aci = null, pniVerified = pniVerified, changeSelf = changeSelf)
        serviceId == pni -> processPnpTuple(e164 = e164, pni = pni, aci = null, pniVerified = pniVerified, changeSelf = changeSelf)
        pni != null -> processPnpTuple(e164 = e164, pni = pni, aci = ACI.from(serviceId.uuid()), pniVerified = pniVerified, changeSelf = changeSelf)
        getByPni(PNI.from(serviceId.uuid())).isPresent -> processPnpTuple(e164 = e164, pni = PNI.from(serviceId.uuid()), aci = null, pniVerified = pniVerified, changeSelf = changeSelf)
        else -> processPnpTuple(e164 = e164, pni = pni, aci = ACI.fromNullable(serviceId), pniVerified = pniVerified, changeSelf = changeSelf)
      }

      if (result.operations.isNotEmpty() || result.requiredInsert) {
        Log.i(TAG, "[getAndPossiblyMerge] ($serviceId, $pni, $e164) BreadCrumbs: ${result.breadCrumbs}, Operations: ${result.operations}, RequiredInsert: ${result.requiredInsert}, FinalId: ${result.finalId}")
      }

      db.setTransactionSuccessful()
      transactionSuccessful = true
    } finally {
      db.endTransaction()

      if (transactionSuccessful) {
        if (result.affectedIds.isNotEmpty()) {
          result.affectedIds.forEach { ApplicationDependencies.getDatabaseObserver().notifyRecipientChanged(it) }
          RetrieveProfileJob.enqueue(result.affectedIds)
        }

        if (result.oldIds.isNotEmpty()) {
          result.oldIds.forEach { oldId ->
            Recipient.live(oldId).refresh(result.finalId)
            ApplicationDependencies.getRecipientCache().remap(oldId, result.finalId)
          }
        }

        if (result.affectedIds.isNotEmpty() || result.oldIds.isNotEmpty()) {
          StorageSyncHelper.scheduleSyncForDataChange()
          RecipientId.clearCache()
        }

        if (result.changedNumberId != null) {
          ApplicationDependencies.getJobManager().add(RecipientChangedNumberJob(result.changedNumberId!!))
        }
      }
    }

    return result.finalId
  }

  fun getAllServiceIdProfileKeyPairs(): Map<ServiceId, ProfileKey> {
    val serviceIdToProfileKey: MutableMap<ServiceId, ProfileKey> = mutableMapOf()

    readableDatabase
      .select(SERVICE_ID, PROFILE_KEY)
      .from(TABLE_NAME)
      .where("$SERVICE_ID NOT NULL AND $PROFILE_KEY NOT NULL")
      .run()
      .use { cursor ->
        while (cursor.moveToNext()) {
          val serviceId: ServiceId? = ServiceId.parseOrNull(cursor.requireString(SERVICE_ID))
          val profileKey: ProfileKey? = ProfileKeyUtil.profileKeyOrNull(cursor.requireString(PROFILE_KEY))

          if (serviceId != null && profileKey != null) {
            serviceIdToProfileKey[serviceId] = profileKey
          }
        }
      }

    return serviceIdToProfileKey
  }

  private fun fetchRecipient(serviceId: ServiceId?, e164: String?, changeSelf: Boolean): RecipientFetch {
    val byE164 = e164?.let { getByE164(it) } ?: Optional.empty()
    val byAci = serviceId?.let { getByServiceId(it) } ?: Optional.empty()

    var logs = LogBundle(
      bySid = byAci.map { id -> RecipientLogDetails(id = id) }.orElse(null),
      byE164 = byE164.map { id -> RecipientLogDetails(id = id) }.orElse(null),
      label = "L0"
    )

    if (byAci.isPresent && byE164.isPresent && byAci.get() == byE164.get()) {
      return RecipientFetch.Match(byAci.get(), logs.label("L0"))
    }

    if (byAci.isPresent && byE164.isAbsent()) {
      val aciRecord: RecipientRecord = getRecord(byAci.get())
      logs = logs.copy(bySid = aciRecord.toLogDetails())

      if (e164 != null && (changeSelf || serviceId != SignalStore.account().aci)) {
        val changedNumber: RecipientId? = if (aciRecord.e164 != null && aciRecord.e164 != e164) aciRecord.id else null
        return RecipientFetch.MatchAndUpdateE164(byAci.get(), e164, changedNumber, logs.label("L1"))
      } else if (e164 == null) {
        return RecipientFetch.Match(byAci.get(), logs.label("L2"))
      } else {
        return RecipientFetch.Match(byAci.get(), logs.label("L3"))
      }
    }

    if (byAci.isAbsent() && byE164.isPresent) {
      val e164Record: RecipientRecord = getRecord(byE164.get())
      logs = logs.copy(byE164 = e164Record.toLogDetails())

      if (serviceId != null && e164Record.serviceId == null) {
        return RecipientFetch.MatchAndUpdateAci(byE164.get(), serviceId, logs.label("L4"))
      } else if (serviceId != null && e164Record.serviceId != SignalStore.account().aci) {
        return RecipientFetch.InsertAndReassignE164(serviceId, e164, byE164.get(), logs.label("L5"))
      } else if (serviceId != null) {
        return RecipientFetch.Insert(serviceId, null, logs.label("L6"))
      } else {
        return RecipientFetch.Match(byE164.get(), logs.label("L7"))
      }
    }

    if (byAci.isAbsent() && byE164.isAbsent()) {
      return RecipientFetch.Insert(serviceId, e164, logs.label("L8"))
    }

    require(byAci.isPresent && byE164.isPresent && byAci.get() != byE164.get()) { "Assumed conditions at this point." }

    val aciRecord: RecipientRecord = getRecord(byAci.get())
    val e164Record: RecipientRecord = getRecord(byE164.get())

    logs = logs.copy(bySid = aciRecord.toLogDetails(), byE164 = e164Record.toLogDetails())

    if (e164Record.serviceId == null) {
      val changedNumber: RecipientId? = if (aciRecord.e164 != null) aciRecord.id else null
      return RecipientFetch.MatchAndMerge(sidId = byAci.get(), e164Id = byE164.get(), changedNumber = changedNumber, logs.label("L9"))
    } else {
      if (e164Record.serviceId != SignalStore.account().aci) {
        val changedNumber: RecipientId? = if (aciRecord.e164 != null) aciRecord.id else null
        return RecipientFetch.MatchAndReassignE164(id = byAci.get(), e164Id = byE164.get(), e164 = e164!!, changedNumber = changedNumber, logs.label("L10"))
      } else {
        return RecipientFetch.Match(byAci.get(), logs.label("L11"))
      }
    }
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
        put(GROUP_TYPE, GroupType.DISTRIBUTION_LIST.id)
        put(DISTRIBUTION_LIST_ID, distributionListId.serialize())
        put(STORAGE_SERVICE_ID, Base64.encodeBytes(storageId ?: StorageSyncHelper.generateKey()))
        put(PROFILE_SHARING, 1)
      }
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
    } else if (groupId.isV2 && groups.getGroupV1ByExpectedV2(groupId.requireV2()).isPresent) {
      throw MissedGroupMigrationInsertException(groupId)
    } else {
      val values = ContentValues().apply {
        put(GROUP_ID, groupId.toString())
        put(AVATAR_COLOR, AvatarColor.random().serialize())
      }

      val id = writableDatabase.insert(TABLE_NAME, null, values)
      if (id < 0) {
        existing = getByColumn(GROUP_ID, groupId.toString())
        if (existing.isPresent) {
          return existing.get()
        } else if (groupId.isV1 && groups.groupExists(groupId.requireV1().deriveV2MigrationGroupId())) {
          throw LegacyGroupInsertException(groupId)
        } else if (groupId.isV2 && groups.getGroupV1ByExpectedV2(groupId.requireV2()).isPresent) {
          throw MissedGroupMigrationInsertException(groupId)
        } else {
          throw AssertionError("Failed to insert recipient!")
        }
      } else {
        val groupUpdates = ContentValues().apply {
          if (groupId.isMms) {
            put(GROUP_TYPE, GroupType.MMS.id)
          } else {
            if (groupId.isV2) {
              put(GROUP_TYPE, GroupType.SIGNAL_V2.id)
            } else {
              put(GROUP_TYPE, GroupType.SIGNAL_V1.id)
            }
            put(STORAGE_SERVICE_ID, Base64.encodeBytes(StorageSyncHelper.generateKey()))
          }
        }

        val recipientId = RecipientId.from(id)
        update(recipientId, groupUpdates)

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

      if (groupId.isV2) {
        val v1 = groups.getGroupV1ByExpectedV2(groupId.requireV2())
        if (v1.isPresent) {
          db.setTransactionSuccessful()
          return v1.get().recipientId
        }
      }

      val id = getOrInsertFromGroupId(groupId)
      db.setTransactionSuccessful()
      return id
    } finally {
      db.endTransaction()
    }
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

  fun getRecord(id: RecipientId): RecipientRecord {
    val query = "$ID = ?"
    val args = arrayOf(id.serialize())

    readableDatabase.query(TABLE_NAME, RECIPIENT_PROJECTION, query, args, null, null, null).use { cursor ->
      return if (cursor != null && cursor.moveToNext()) {
        getRecord(context, cursor)
      } else {
        val remapped = RemappedRecords.getInstance().getRecipient(id)

        if (remapped.isPresent) {
          Log.w(TAG, "Missing recipient for $id, but found it in the remapped records as ${remapped.get()}")
          getRecord(remapped.get())
        } else {
          throw MissingRecipientException(id)
        }
      }
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
    val result = getRecordForSync("$TABLE_NAME.$STORAGE_SERVICE_ID = ?", arrayOf(Base64.encodeBytes(storageId)))

    return if (result.isNotEmpty()) {
      result[0]
    } else null
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
    ApplicationDependencies.getDatabaseObserver().notifyRecipientChanged(recipientId)
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
          put(STORAGE_SERVICE_ID, Base64.encodeBytes(value.raw))
        }
        db.update(TABLE_NAME, values, query, arrayOf(key.serialize()))
      }
      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }

    for (id in storageIds.keys) {
      ApplicationDependencies.getDatabaseObserver().notifyRecipientChanged(id)
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
      if (FeatureFlags.phoneNumberPrivacy()) {
        recipientId = getAndPossiblyMergePnpVerified(if (insert.serviceId.isValid) insert.serviceId else null, insert.pni.orElse(null), insert.number.orElse(null))
      } else {
        recipientId = getAndPossiblyMerge(if (insert.serviceId.isValid) insert.serviceId else null, insert.number.orElse(null))
      }
      db.update(TABLE_NAME, values, ID_WHERE, SqlUtil.buildArgs(recipientId))
    } else {
      recipientId = RecipientId.from(id)
    }

    if (insert.identityKey.isPresent && insert.serviceId.isValid) {
      try {
        val identityKey = IdentityKey(insert.identityKey.get(), 0)
        identities.updateIdentityAfterSync(insert.serviceId.toString(), recipientId, identityKey, StorageSyncModels.remoteToLocalIdentityStatus(insert.identityState))
      } catch (e: InvalidKeyException) {
        Log.w(TAG, "Failed to process identity key during insert! Skipping.", e)
      }
    }

    updateExtras(recipientId) {
      it.setHideStory(insert.shouldHideStory())
    }

    threadDatabase.applyStorageSyncUpdate(recipientId, insert)
  }

  fun applyStorageSyncContactUpdate(update: StorageRecordUpdate<SignalContactRecord>) {
    val db = writableDatabase
    val identityStore = ApplicationDependencies.getProtocolStore().aci().identities()
    val values = getValuesForStorageContact(update.new, false)

    try {
      val updateCount = db.update(TABLE_NAME, values, "$STORAGE_SERVICE_ID = ?", arrayOf(Base64.encodeBytes(update.old.id.raw)))
      if (updateCount < 1) {
        throw AssertionError("Had an update, but it didn't match any rows!")
      }
    } catch (e: SQLiteConstraintException) {
      Log.w(TAG, "[applyStorageSyncContactUpdate] Failed to update a user by storageId.")
      var recipientId = getByColumn(STORAGE_SERVICE_ID, Base64.encodeBytes(update.old.id.raw)).get()

      Log.w(TAG, "[applyStorageSyncContactUpdate] Found user $recipientId. Possibly merging.")
      if (FeatureFlags.phoneNumberPrivacy()) {
        recipientId = getAndPossiblyMergePnpVerified(if (update.new.serviceId.isValid) update.new.serviceId else null, update.new.pni.orElse(null), update.new.number.orElse(null))
      } else {
        recipientId = getAndPossiblyMerge(if (update.new.serviceId.isValid) update.new.serviceId else null, update.new.number.orElse(null))
      }

      Log.w(TAG, "[applyStorageSyncContactUpdate] Merged into $recipientId")
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
      if (update.new.identityKey.isPresent && update.new.serviceId.isValid) {
        val identityKey = IdentityKey(update.new.identityKey.get(), 0)
        identities.updateIdentityAfterSync(update.new.serviceId.toString(), recipientId, identityKey, StorageSyncModels.remoteToLocalIdentityStatus(update.new.identityState))
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
      it.setHideStory(update.new.shouldHideStory())
    }

    threads.applyStorageSyncUpdate(recipientId, update.new)
    ApplicationDependencies.getDatabaseObserver().notifyRecipientChanged(recipientId)
  }

  fun applyStorageSyncGroupV1Insert(insert: SignalGroupV1Record) {
    val id = writableDatabase.insertOrThrow(TABLE_NAME, null, getValuesForStorageGroupV1(insert, true))

    val recipientId = RecipientId.from(id)
    threads.applyStorageSyncUpdate(recipientId, insert)
    ApplicationDependencies.getDatabaseObserver().notifyRecipientChanged(recipientId)
  }

  fun applyStorageSyncGroupV1Update(update: StorageRecordUpdate<SignalGroupV1Record>) {
    val values = getValuesForStorageGroupV1(update.new, false)

    val updateCount = writableDatabase.update(TABLE_NAME, values, STORAGE_SERVICE_ID + " = ?", arrayOf(Base64.encodeBytes(update.old.id.raw)))
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
    val recipient = Recipient.externalGroupExact(groupId)

    Log.i(TAG, "Creating restore placeholder for $groupId")
    groups.create(
      masterKey,
      DecryptedGroup.newBuilder()
        .setRevision(GroupsV2StateProcessor.RESTORE_PLACEHOLDER_REVISION)
        .build()
    )

    groups.setShowAsStoryState(groupId, insert.storySendMode.toShowAsStoryState())
    updateExtras(recipient.id) {
      it.setHideStory(insert.shouldHideStory())
    }

    Log.i(TAG, "Scheduling request for latest group info for $groupId")
    ApplicationDependencies.getJobManager().add(RequestGroupV2InfoJob(groupId))
    threads.applyStorageSyncUpdate(recipient.id, insert)
    recipient.live().refresh()
  }

  fun applyStorageSyncGroupV2Update(update: StorageRecordUpdate<SignalGroupV2Record>) {
    val values = getValuesForStorageGroupV2(update.new, false)

    val updateCount = writableDatabase.update(TABLE_NAME, values, "$STORAGE_SERVICE_ID = ?", arrayOf(Base64.encodeBytes(update.old.id.raw)))
    if (updateCount < 1) {
      throw AssertionError("Had an update, but it didn't match any rows!")
    }

    val masterKey = update.old.masterKeyOrThrow
    val groupId = GroupId.v2(masterKey)
    val recipient = Recipient.externalGroupExact(groupId)

    updateExtras(recipient.id) {
      it.setHideStory(update.new.shouldHideStory())
    }

    groups.setShowAsStoryState(groupId, update.new.storySendMode.toShowAsStoryState())
    threads.applyStorageSyncUpdate(recipient.id, update.new)
    recipient.live().refresh()
  }

  fun applyStorageSyncAccountUpdate(update: StorageRecordUpdate<SignalAccountRecord>) {
    val profileName = ProfileName.fromParts(update.new.givenName.orElse(null), update.new.familyName.orElse(null))
    val localKey = ProfileKeyUtil.profileKeyOptional(update.old.profileKey.orElse(null))
    val remoteKey = ProfileKeyUtil.profileKeyOptional(update.new.profileKey.orElse(null))
    val profileKey: String? = remoteKey.or(localKey).map { obj: ProfileKey -> obj.serialize() }.map { source: ByteArray? -> Base64.encodeBytes(source!!) }.orElse(null)
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

      put(STORAGE_SERVICE_ID, Base64.encodeBytes(update.new.id.raw))

      if (update.new.hasUnknownFields()) {
        put(STORAGE_PROTO, Base64.encodeBytes(Objects.requireNonNull(update.new.serializeUnknownFields())))
      } else {
        putNull(STORAGE_PROTO)
      }
    }

    val updateCount = writableDatabase.update(TABLE_NAME, values, "$STORAGE_SERVICE_ID = ?", arrayOf(Base64.encodeBytes(update.old.id.raw)))
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

    SqlUtil.buildCollectionQuery(STORAGE_SERVICE_ID, storageIds.map { Base64.encodeBytes(it.raw) }, "$UNREGISTERED_TIMESTAMP > 0 AND")
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
          .values(PHONE to updatedE164)
          .where("$PHONE = ?", originalE164)
          .run(SQLiteDatabase.CONFLICT_IGNORE)
      }
    }
  }

  private fun getByStorageKeyOrThrow(storageKey: ByteArray): RecipientId {
    val query = "$STORAGE_SERVICE_ID = ?"
    val args = arrayOf(Base64.encodeBytes(storageKey))

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
      GroupV2Record.StorySendMode.UNRECOGNIZED -> ShowAsStoryState.IF_ACTIVE
    }
  }

  private fun getRecordForSync(query: String?, args: Array<String>?): List<RecipientRecord> {
    val table =
      """
      $TABLE_NAME LEFT OUTER JOIN ${IdentityDatabase.TABLE_NAME} ON $TABLE_NAME.$SERVICE_ID = ${IdentityDatabase.TABLE_NAME}.${IdentityDatabase.ADDRESS} 
                  LEFT OUTER JOIN ${GroupDatabase.TABLE_NAME} ON $TABLE_NAME.$GROUP_ID = ${GroupDatabase.TABLE_NAME}.${GroupDatabase.GROUP_ID} 
                  LEFT OUTER JOIN ${ThreadDatabase.TABLE_NAME} ON $TABLE_NAME.$ID = ${ThreadDatabase.TABLE_NAME}.${ThreadDatabase.RECIPIENT_ID}
      """.trimIndent()
    val out: MutableList<RecipientRecord> = ArrayList()
    val columns: Array<String> = TYPED_RECIPIENT_PROJECTION + arrayOf(
      "$TABLE_NAME.$STORAGE_PROTO",
      "$TABLE_NAME.$UNREGISTERED_TIMESTAMP",
      "${GroupDatabase.TABLE_NAME}.${GroupDatabase.V2_MASTER_KEY}",
      "${ThreadDatabase.TABLE_NAME}.${ThreadDatabase.ARCHIVED}",
      "${ThreadDatabase.TABLE_NAME}.${ThreadDatabase.READ}",
      "${IdentityDatabase.TABLE_NAME}.${IdentityDatabase.VERIFIED} AS $IDENTITY_STATUS",
      "${IdentityDatabase.TABLE_NAME}.${IdentityDatabase.IDENTITY_KEY} AS $IDENTITY_KEY"
    )

    readableDatabase.query(table, columns, query, args, "$TABLE_NAME.$ID", null, null).use { cursor ->
      while (cursor != null && cursor.moveToNext()) {
        out.add(getRecord(context, cursor))
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
    val inPart = "(?, ?)"
    val args = SqlUtil.buildArgs(GroupType.NONE.id, Recipient.self().id, GroupType.SIGNAL_V1.id, GroupType.DISTRIBUTION_LIST.id)

    val query = """
      $STORAGE_SERVICE_ID NOT NULL AND (
        ($GROUP_TYPE = ? AND $SERVICE_ID NOT NULL AND $ID != ?)
        OR
        $GROUP_TYPE IN $inPart
      )
    """.trimIndent()
    val out: MutableMap<RecipientId, StorageId> = HashMap()

    readableDatabase.query(TABLE_NAME, arrayOf(ID, STORAGE_SERVICE_ID, GROUP_TYPE), query, args, null, null, null).use { cursor ->
      while (cursor != null && cursor.moveToNext()) {
        val id = RecipientId.from(cursor.requireLong(ID))
        val encodedKey = cursor.requireNonNullString(STORAGE_SERVICE_ID)
        val groupType = GroupType.fromId(cursor.requireInt(GROUP_TYPE))
        val key = Base64.decodeOrThrow(encodedKey)

        when (groupType) {
          GroupType.NONE -> out[id] = StorageId.forContact(key)
          GroupType.SIGNAL_V1 -> out[id] = StorageId.forGroupV1(key)
          GroupType.DISTRIBUTION_LIST -> out[id] = StorageId.forStoryDistributionList(key)
          else -> throw AssertionError()
        }
      }
    }

    for (id in groups.allGroupV2Ids) {
      val recipient = Recipient.externalGroupExact(id!!)
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
      readableDatabase.query(TABLE_NAME, arrayOf(PHONE), query.where, query.whereArgs, null, null, null).use { cursor ->
        while (cursor.moveToNext()) {
          val e164: String? = cursor.requireString(PHONE)
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
        put(CHAT_COLORS, chatColors.serialize().toByteArray())
        put(CUSTOM_CHAT_COLORS_ID, chatColors.id.longValue)
      }

      writableDatabase.update(TABLE_NAME, values, where, args)

      for (recipientId in updated) {
        ApplicationDependencies.getDatabaseObserver().notifyRecipientChanged(recipientId)
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
        ApplicationDependencies.getDatabaseObserver().notifyRecipientChanged(recipientId)
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
      ApplicationDependencies.getDatabaseObserver().notifyRecipientChanged(id)
    }
  }

  fun clearColor(id: RecipientId) {
    val values = ContentValues().apply {
      put(CHAT_COLORS, null as ByteArray?)
      put(CUSTOM_CHAT_COLORS_ID, ChatColors.Id.NotSet.longValue)
    }
    if (update(id, values)) {
      ApplicationDependencies.getDatabaseObserver().notifyRecipientChanged(id)
    }
  }

  fun setColor(id: RecipientId, color: ChatColors) {
    val values = ContentValues().apply {
      put(CHAT_COLORS, color.serialize().toByteArray())
      put(CUSTOM_CHAT_COLORS_ID, color.id.longValue)
    }
    if (update(id, values)) {
      ApplicationDependencies.getDatabaseObserver().notifyRecipientChanged(id)
    }
  }

  fun setDefaultSubscriptionId(id: RecipientId, defaultSubscriptionId: Int) {
    val values = ContentValues().apply {
      put(DEFAULT_SUBSCRIPTION_ID, defaultSubscriptionId)
    }
    if (update(id, values)) {
      ApplicationDependencies.getDatabaseObserver().notifyRecipientChanged(id)
    }
  }

  fun setForceSmsSelection(id: RecipientId, forceSmsSelection: Boolean) {
    val contentValues = ContentValues(1).apply {
      put(FORCE_SMS_SELECTION, if (forceSmsSelection) 1 else 0)
    }
    if (update(id, contentValues)) {
      ApplicationDependencies.getDatabaseObserver().notifyRecipientChanged(id)
    }
  }

  fun setBlocked(id: RecipientId, blocked: Boolean) {
    val values = ContentValues().apply {
      put(BLOCKED, if (blocked) 1 else 0)
    }
    if (update(id, values)) {
      rotateStorageId(id)
      ApplicationDependencies.getDatabaseObserver().notifyRecipientChanged(id)
    }
  }

  fun setMessageRingtone(id: RecipientId, notification: Uri?) {
    val values = ContentValues().apply {
      put(MESSAGE_RINGTONE, notification?.toString())
    }
    if (update(id, values)) {
      ApplicationDependencies.getDatabaseObserver().notifyRecipientChanged(id)
    }
  }

  fun setCallRingtone(id: RecipientId, ringtone: Uri?) {
    val values = ContentValues().apply {
      put(CALL_RINGTONE, ringtone?.toString())
    }
    if (update(id, values)) {
      ApplicationDependencies.getDatabaseObserver().notifyRecipientChanged(id)
    }
  }

  fun setMessageVibrate(id: RecipientId, enabled: VibrateState) {
    val values = ContentValues().apply {
      put(MESSAGE_VIBRATE, enabled.id)
    }
    if (update(id, values)) {
      ApplicationDependencies.getDatabaseObserver().notifyRecipientChanged(id)
    }
  }

  fun setCallVibrate(id: RecipientId, enabled: VibrateState) {
    val values = ContentValues().apply {
      put(CALL_VIBRATE, enabled.id)
    }
    if (update(id, values)) {
      ApplicationDependencies.getDatabaseObserver().notifyRecipientChanged(id)
    }
  }

  fun setMuted(id: RecipientId, until: Long) {
    val values = ContentValues().apply {
      put(MUTE_UNTIL, until)
    }

    if (update(id, values)) {
      rotateStorageId(id)
      ApplicationDependencies.getDatabaseObserver().notifyRecipientChanged(id)
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
      ApplicationDependencies.getDatabaseObserver().notifyRecipientChanged(id)
    }

    StorageSyncHelper.scheduleSyncForDataChange()
  }

  fun setSeenFirstInviteReminder(id: RecipientId) {
    setInsightsBannerTier(id, InsightsBannerTier.TIER_ONE)
  }

  fun setSeenSecondInviteReminder(id: RecipientId) {
    setInsightsBannerTier(id, InsightsBannerTier.TIER_TWO)
  }

  fun setHasSentInvite(id: RecipientId) {
    setSeenSecondInviteReminder(id)
  }

  private fun setInsightsBannerTier(id: RecipientId, insightsBannerTier: InsightsBannerTier) {
    val query = "$ID = ? AND $SEEN_INVITE_REMINDER < ?"
    val args = arrayOf(id.serialize(), insightsBannerTier.toString())
    val values = ContentValues(1).apply {
      put(SEEN_INVITE_REMINDER, insightsBannerTier.id)
    }

    writableDatabase.update(TABLE_NAME, values, query, args)
    ApplicationDependencies.getDatabaseObserver().notifyRecipientChanged(id)
  }

  fun setExpireMessages(id: RecipientId, expiration: Int) {
    val values = ContentValues(1).apply {
      put(MESSAGE_EXPIRATION_TIME, expiration)
    }
    if (update(id, values)) {
      ApplicationDependencies.getDatabaseObserver().notifyRecipientChanged(id)
    }
  }

  fun setUnidentifiedAccessMode(id: RecipientId, unidentifiedAccessMode: UnidentifiedAccessMode) {
    val values = ContentValues(1).apply {
      put(UNIDENTIFIED_ACCESS_MODE, unidentifiedAccessMode.mode)
    }
    if (update(id, values)) {
      ApplicationDependencies.getDatabaseObserver().notifyRecipientChanged(id)
    }
  }

  fun setLastSessionResetTime(id: RecipientId, lastResetTime: DeviceLastResetTime) {
    val values = ContentValues(1).apply {
      put(LAST_SESSION_RESET, lastResetTime.toByteArray())
    }
    update(id, values)
  }

  fun getLastSessionResetTimes(id: RecipientId): DeviceLastResetTime {
    readableDatabase.query(TABLE_NAME, arrayOf(LAST_SESSION_RESET), ID_WHERE, SqlUtil.buildArgs(id), null, null, null).use { cursor ->
      if (cursor.moveToFirst()) {
        return try {
          val serialized = cursor.requireBlob(LAST_SESSION_RESET)
          if (serialized != null) {
            DeviceLastResetTime.parseFrom(serialized)
          } else {
            DeviceLastResetTime.newBuilder().build()
          }
        } catch (e: InvalidProtocolBufferException) {
          Log.w(TAG, e)
          DeviceLastResetTime.newBuilder().build()
        }
      }
    }

    return DeviceLastResetTime.newBuilder().build()
  }

  fun setBadges(id: RecipientId, badges: List<Badge>) {
    val badgeListBuilder = BadgeList.newBuilder()
    for (badge in badges) {
      badgeListBuilder.addBadges(toDatabaseBadge(badge))
    }

    val values = ContentValues(1).apply {
      put(BADGES, badgeListBuilder.build().toByteArray())
    }

    if (update(id, values)) {
      ApplicationDependencies.getDatabaseObserver().notifyRecipientChanged(id)
    }
  }

  fun setCapabilities(id: RecipientId, capabilities: SignalServiceProfile.Capabilities) {
    var value: Long = 0
    value = Bitmask.update(value, Capabilities.GROUPS_V1_MIGRATION, Capabilities.BIT_LENGTH, Recipient.Capability.fromBoolean(capabilities.isGv1Migration).serialize().toLong())
    value = Bitmask.update(value, Capabilities.SENDER_KEY, Capabilities.BIT_LENGTH, Recipient.Capability.fromBoolean(capabilities.isSenderKey).serialize().toLong())
    value = Bitmask.update(value, Capabilities.ANNOUNCEMENT_GROUPS, Capabilities.BIT_LENGTH, Recipient.Capability.fromBoolean(capabilities.isAnnouncementGroup).serialize().toLong())
    value = Bitmask.update(value, Capabilities.CHANGE_NUMBER, Capabilities.BIT_LENGTH, Recipient.Capability.fromBoolean(capabilities.isChangeNumber).serialize().toLong())
    value = Bitmask.update(value, Capabilities.STORIES, Capabilities.BIT_LENGTH, Recipient.Capability.fromBoolean(capabilities.isStories).serialize().toLong())
    value = Bitmask.update(value, Capabilities.GIFT_BADGES, Capabilities.BIT_LENGTH, Recipient.Capability.fromBoolean(capabilities.isGiftBadges).serialize().toLong())
    value = Bitmask.update(value, Capabilities.PNP, Capabilities.BIT_LENGTH, Recipient.Capability.fromBoolean(capabilities.isPnp).serialize().toLong())

    val values = ContentValues(1).apply {
      put(CAPABILITIES, value)
    }

    if (update(id, values)) {
      ApplicationDependencies.getDatabaseObserver().notifyRecipientChanged(id)
    }
  }

  fun setMentionSetting(id: RecipientId, mentionSetting: MentionSetting) {
    val values = ContentValues().apply {
      put(MENTION_SETTING, mentionSetting.id)
    }
    if (update(id, values)) {
      rotateStorageId(id)
      ApplicationDependencies.getDatabaseObserver().notifyRecipientChanged(id)
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
    val encodedProfileKey = Base64.encodeBytes(profileKey.serialize())
    val valuesToCompare = ContentValues(1).apply {
      put(PROFILE_KEY, encodedProfileKey)
    }
    val valuesToSet = ContentValues(3).apply {
      put(PROFILE_KEY, encodedProfileKey)
      putNull(EXPIRING_PROFILE_KEY_CREDENTIAL)
      put(UNIDENTIFIED_ACCESS_MODE, UnidentifiedAccessMode.UNKNOWN.mode)
    }

    val updateQuery = SqlUtil.buildTrueUpdateQuery(selection, args, valuesToCompare)

    if (update(updateQuery, valuesToSet)) {
      rotateStorageId(id)
      ApplicationDependencies.getDatabaseObserver().notifyRecipientChanged(id)
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
      put(PROFILE_KEY, Base64.encodeBytes(profileKey.serialize()))
      putNull(EXPIRING_PROFILE_KEY_CREDENTIAL)
      put(UNIDENTIFIED_ACCESS_MODE, UnidentifiedAccessMode.UNKNOWN.mode)
    }

    if (writableDatabase.update(TABLE_NAME, valuesToSet, selection, args) > 0) {
      rotateStorageId(id)
      ApplicationDependencies.getDatabaseObserver().notifyRecipientChanged(id)
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
    val args = arrayOf(id.serialize(), Base64.encodeBytes(profileKey.serialize()))
    val columnData = ExpiringProfileKeyCredentialColumnData.newBuilder()
      .setProfileKey(ByteString.copyFrom(profileKey.serialize()))
      .setExpiringProfileKeyCredential(ByteString.copyFrom(expiringProfileKeyCredential.serialize()))
      .build()
    val values = ContentValues(1).apply {
      put(EXPIRING_PROFILE_KEY_CREDENTIAL, Base64.encodeBytes(columnData.toByteArray()))
    }
    val updateQuery = SqlUtil.buildTrueUpdateQuery(selection, args, values)

    val updated = update(updateQuery, values)
    if (updated) {
      ApplicationDependencies.getDatabaseObserver().notifyRecipientChanged(id)
    }

    return updated
  }

  fun clearProfileKeyCredential(id: RecipientId) {
    val values = ContentValues(1)
    values.putNull(EXPIRING_PROFILE_KEY_CREDENTIAL)
    if (update(id, values)) {
      ApplicationDependencies.getDatabaseObserver().notifyRecipientChanged(id)
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

  fun getSimilarRecipientIds(recipient: Recipient): List<RecipientId> {
    val projection = SqlUtil.buildArgs(ID, "COALESCE(NULLIF($SYSTEM_JOINED_NAME, ''), NULLIF($PROFILE_JOINED_NAME, '')) AS checked_name")
    val where = "checked_name = ? AND $HIDDEN = ?"
    val arguments = SqlUtil.buildArgs(recipient.profileName.toString(), 0)

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
      ApplicationDependencies.getDatabaseObserver().notifyRecipientChanged(id)
    }
  }

  fun setProfileName(id: RecipientId, profileName: ProfileName) {
    val contentValues = ContentValues(1).apply {
      put(PROFILE_GIVEN_NAME, profileName.givenName)
      put(PROFILE_FAMILY_NAME, profileName.familyName)
      put(PROFILE_JOINED_NAME, profileName.toString())
    }
    if (update(id, contentValues)) {
      rotateStorageId(id)
      ApplicationDependencies.getDatabaseObserver().notifyRecipientChanged(id)
      StorageSyncHelper.scheduleSyncForDataChange()
    }
  }

  fun setProfileAvatar(id: RecipientId, profileAvatar: String?) {
    val contentValues = ContentValues(1).apply {
      put(SIGNAL_PROFILE_AVATAR, profileAvatar)
    }
    if (update(id, contentValues)) {
      ApplicationDependencies.getDatabaseObserver().notifyRecipientChanged(id)
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
      ApplicationDependencies.getDatabaseObserver().notifyRecipientChanged(id)
    }
  }

  fun markHidden(id: RecipientId) {
    val contentValues = contentValuesOf(
      HIDDEN to 1,
      PROFILE_SHARING to 0
    )

    val updated = writableDatabase.update(TABLE_NAME, contentValues, "$ID_WHERE AND $GROUP_TYPE = ?", SqlUtil.buildArgs(id, GroupType.NONE.id)) > 0
    if (updated) {
      rotateStorageId(id)
      ApplicationDependencies.getDatabaseObserver().notifyRecipientChanged(id)
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
      ApplicationDependencies.getDatabaseObserver().notifyRecipientChanged(id)
      StorageSyncHelper.scheduleSyncForDataChange()
    }
  }

  fun setNotificationChannel(id: RecipientId, notificationChannel: String?) {
    val contentValues = ContentValues(1).apply {
      put(NOTIFICATION_CHANNEL, notificationChannel)
    }
    if (update(id, contentValues)) {
      ApplicationDependencies.getDatabaseObserver().notifyRecipientChanged(id)
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
          ApplicationDependencies.getDatabaseObserver().notifyRecipientChanged(pair.first)
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
      put(WALLPAPER, wallpaper?.toByteArray())
      if (wallpaper != null && wallpaper.hasFile()) {
        put(WALLPAPER_URI, wallpaper.file.uri)
      } else {
        putNull(WALLPAPER_URI)
      }
    }

    if (update(id, values)) {
      ApplicationDependencies.getDatabaseObserver().notifyRecipientChanged(id)
    }

    if (existingWallpaperUri != null) {
      WallpaperStorage.onWallpaperDeselected(context, existingWallpaperUri)
    }
  }

  fun setDimWallpaperInDarkTheme(id: RecipientId, enabled: Boolean) {
    val wallpaper = getWallpaper(id) ?: throw IllegalStateException("No wallpaper set for $id")
    val updated = wallpaper.toBuilder()
      .setDimLevelInDarkTheme(if (enabled) ChatWallpaper.FIXED_DIM_LEVEL_FOR_DARK_THEME else 0f)
      .build()

    setWallpaper(id, updated)
  }

  private fun getWallpaper(id: RecipientId): Wallpaper? {
    readableDatabase.query(TABLE_NAME, arrayOf(WALLPAPER), ID_WHERE, SqlUtil.buildArgs(id), null, null, null).use { cursor ->
      if (cursor.moveToFirst()) {
        val raw = cursor.requireBlob(WALLPAPER)
        return if (raw != null) {
          try {
            Wallpaper.parseFrom(raw)
          } catch (e: InvalidProtocolBufferException) {
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

    return if (wallpaper != null && wallpaper.hasFile()) {
      Uri.parse(wallpaper.file.uri)
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
      val newId = getAndPossiblyMerge(existing.serviceId, e164)
      Log.w(TAG, "[setPhoneNumber] Resulting id: $newId")

      db.setTransactionSuccessful()
      newId != existing.id
    } finally {
      db.endTransaction()
    }
  }

  private fun removePhoneNumber(recipientId: RecipientId) {
    val values = ContentValues().apply {
      putNull(PHONE)
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
      put(PHONE, e164)
    }
    if (update(id, contentValues)) {
      rotateStorageId(id)
      ApplicationDependencies.getDatabaseObserver().notifyRecipientChanged(id)
      StorageSyncHelper.scheduleSyncForDataChange()
    }
  }

  @Throws(SQLiteConstraintException::class)
  fun setPhoneNumberOrThrowSilent(id: RecipientId, e164: String) {
    val contentValues = ContentValues(1).apply {
      put(PHONE, e164)
    }
    if (update(id, contentValues)) {
      rotateStorageId(id)
    }
  }

  /**
   * Associates the provided IDs together. The assumption here is that all of the IDs correspond to the local user and have been verified.
   */
  fun linkIdsForSelf(aci: ACI, pni: PNI, e164: String) {
    getAndPossiblyMerge(serviceId = aci, pni = pni, e164 = e164, changeSelf = true, pniVerified = true)
  }

  /**
   * Does *not* handle clearing the recipient cache. It is assumed the caller handles this.
   */
  fun updateSelfPhone(e164: String, pni: PNI) {
    val db = writableDatabase

    db.beginTransaction()
    try {
      val id = Recipient.self().id
      val newId = getAndPossiblyMerge(serviceId = SignalStore.account().requireAci(), pni = pni, e164 = e164, pniVerified = true, changeSelf = true)

      if (id == newId) {
        Log.i(TAG, "[updateSelfPhone] Phone updated for self")
      } else {
        throw AssertionError("[updateSelfPhone] Self recipient id changed when updating phone. old: $id new: $newId")
      }

      db
        .update(TABLE_NAME)
        .values(NEEDS_PNI_SIGNATURE to 0)
        .run()

      SignalDatabase.pendingPniSignatureMessages.deleteAll()

      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
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
        ApplicationDependencies.getDatabaseObserver().notifyRecipientChanged(id)
        StorageSyncHelper.scheduleSyncForDataChange()
      }
    }
  }

  fun setHideStory(id: RecipientId, hideStory: Boolean) {
    updateExtras(id) { it.setHideStory(hideStory) }
    StorageSyncHelper.scheduleSyncForDataChange()
  }

  fun updateLastStoryViewTimestamp(id: RecipientId) {
    updateExtras(id) { it.setLastStoryView(System.currentTimeMillis()) }
  }

  fun clearUsernameIfExists(username: String) {
    val existingUsername = getByUsername(username)
    if (existingUsername.isPresent) {
      setUsername(existingUsername.get(), null)
    }
  }

  fun getAllE164s(): Set<String> {
    val results: MutableSet<String> = HashSet()
    readableDatabase.query(TABLE_NAME, arrayOf(PHONE), null, null, null, null, null).use { cursor ->
      while (cursor != null && cursor.moveToNext()) {
        val number = cursor.getString(cursor.getColumnIndexOrThrow(PHONE))
        if (!TextUtils.isEmpty(number)) {
          results.add(number)
        }
      }
    }
    return results
  }

  /**
   * Gives you all of the recipientIds of possibly-registered users (i.e. REGISTERED or UNKNOWN) that can be found by the set of
   * provided E164s.
   */
  fun getAllPossiblyRegisteredByE164(e164s: Set<String>): Set<RecipientId> {
    val results: MutableSet<RecipientId> = mutableSetOf()
    val queries: List<SqlUtil.Query> = SqlUtil.buildCollectionQuery(PHONE, e164s)

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
      .values(SERVICE_ID to pni.toString())
      .where("$ID = ? AND ($SERVICE_ID IS NULL OR $SERVICE_ID = $PNI_COLUMN)", id)
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
      SERVICE_ID to serviceId.toString().lowercase(),
      UNREGISTERED_TIMESTAMP to 0
    )
    if (update(id, contentValues)) {
      Log.i(TAG, "Newly marked $id as registered.")
      setStorageIdIfNotSet(id)
      ApplicationDependencies.getDatabaseObserver().notifyRecipientChanged(id)
    }
  }

  fun markUnregistered(id: RecipientId) {
    val contentValues = contentValuesOf(
      REGISTERED to RegisteredState.NOT_REGISTERED.id,
      UNREGISTERED_TIMESTAMP to System.currentTimeMillis()
    )

    if (update(id, contentValues)) {
      Log.i(TAG, "Newly marked $id as unregistered.")
      ApplicationDependencies.getDatabaseObserver().notifyRecipientChanged(id)
    }
  }

  fun bulkUpdatedRegisteredStatus(registered: Map<RecipientId, ServiceId?>, unregistered: Collection<RecipientId>) {
    writableDatabase.withinTransaction { db ->
      val registeredWithServiceId: Set<RecipientId> = getRegisteredWithServiceIds()
      val needsMarkRegistered: Map<RecipientId, ServiceId?> = registered - registeredWithServiceId

      for ((recipientId, serviceId) in needsMarkRegistered) {
        val values = ContentValues().apply {
          put(REGISTERED, RegisteredState.REGISTERED.id)
          put(UNREGISTERED_TIMESTAMP, 0)
          if (serviceId != null) {
            put(SERVICE_ID, serviceId.toString().lowercase())
          }
        }

        try {
          if (update(recipientId, values)) {
            setStorageIdIfNotSet(recipientId)
            ApplicationDependencies.getDatabaseObserver().notifyRecipientChanged(recipientId)
          }
        } catch (e: SQLiteConstraintException) {
          Log.w(TAG, "[bulkUpdateRegisteredStatus] Hit a conflict when trying to update $recipientId. Possibly merging.")
          val e164 = getRecord(recipientId).e164
          val newId = getAndPossiblyMerge(serviceId, e164)
          Log.w(TAG, "[bulkUpdateRegisteredStatus] Merged into $newId")
        }
      }

      for (id in unregistered) {
        val values = contentValuesOf(
          REGISTERED to RegisteredState.NOT_REGISTERED.id,
          UNREGISTERED_TIMESTAMP to System.currentTimeMillis()
        )
        if (update(id, values)) {
          ApplicationDependencies.getDatabaseObserver().notifyRecipientChanged(id)
        }
      }
    }
  }

  /**
   * Handles inserts the (e164, UUID) pairs, which could result in merges. Does not mark users as
   * registered.
   *
   * @return A mapping of (RecipientId, UUID)
   */
  fun bulkProcessCdsResult(mapping: Map<String, ACI?>): Map<RecipientId, ACI?> {
    val db = writableDatabase
    val aciMap: MutableMap<RecipientId, ACI?> = mutableMapOf()

    db.beginTransaction()
    try {
      for ((e164, aci) in mapping) {
        var aciEntry = if (aci != null) getByServiceId(aci) else Optional.empty()

        if (aciEntry.isPresent) {
          val idChanged = setPhoneNumber(aciEntry.get(), e164)
          if (idChanged) {
            aciEntry = getByServiceId(aci!!)
          }
        }

        val id = if (aciEntry.isPresent) aciEntry.get() else getOrInsertFromE164(e164)
        aciMap[id] = aci
      }

      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }

    return aciMap
  }

  /**
   * Processes CDSv2 results, merging recipients as necessary. Does not mark users as
   * registered.
   *
   * Important: This is under active development and is not suitable for actual use.
   *
   * @return A set of [RecipientId]s that were updated/inserted.
   */
  fun bulkProcessCdsV2Result(mapping: Map<String, CdsV2Result>): Set<RecipientId> {
    val ids: MutableSet<RecipientId> = mutableSetOf()
    val db = writableDatabase

    db.beginTransaction()
    try {
      for ((e164, result) in mapping) {
        ids += getAndPossiblyMerge(serviceId = result.aci, pni = result.pni, e164 = e164, pniVerified = false, changeSelf = false)
      }

      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }

    return ids
  }

  fun bulkUpdatedRegisteredStatusV2(registered: Set<RecipientId>, unregistered: Collection<RecipientId>) {
    writableDatabase.withinTransaction {
      val registeredValues = contentValuesOf(
        REGISTERED to RegisteredState.REGISTERED.id,
        UNREGISTERED_TIMESTAMP to 0
      )

      val newlyRegistered: MutableSet<RecipientId> = mutableSetOf()

      for (id in registered) {
        if (update(id, registeredValues)) {
          newlyRegistered += id
          setStorageIdIfNotSet(id)
          ApplicationDependencies.getDatabaseObserver().notifyRecipientChanged(id)
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
          ApplicationDependencies.getDatabaseObserver().notifyRecipientChanged(id)
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

    val finalId: RecipientId = writePnpChangeSetToDisk(changeSet, pni)

    return ProcessPnpTupleResult(
      finalId = finalId,
      requiredInsert = changeSet.id is PnpIdResolver.PnpInsert,
      affectedIds = affectedIds,
      oldIds = oldIds,
      changedNumberId = changedNumberId,
      operations = changeSet.operations,
      breadCrumbs = changeSet.breadCrumbs
    )
  }

  @VisibleForTesting
  fun writePnpChangeSetToDisk(changeSet: PnpChangeSet, inputPni: PNI?): RecipientId {
    for (operation in changeSet.operations) {
      @Exhaustive
      when (operation) {
        is PnpOperation.RemoveE164 -> {
          writableDatabase
            .update(TABLE_NAME)
            .values(PHONE to null)
            .where("$ID = ?", operation.recipientId)
            .run()
        }
        is PnpOperation.RemovePni -> {
          writableDatabase
            .update(TABLE_NAME)
            .values(SERVICE_ID to null)
            .where("$ID = ? AND $SERVICE_ID NOT NULL AND $SERVICE_ID = $PNI_COLUMN", operation.recipientId)
            .run()

          writableDatabase
            .update(TABLE_NAME)
            .values(PNI_COLUMN to null)
            .where("$ID = ?", operation.recipientId)
            .run()
        }
        is PnpOperation.SetAci -> {
          writableDatabase
            .update(TABLE_NAME)
            .values(
              SERVICE_ID to operation.aci.toString(),
              REGISTERED to RegisteredState.REGISTERED.id,
              UNREGISTERED_TIMESTAMP to 0
            )
            .where("$ID = ?", operation.recipientId)
            .run()
        }
        is PnpOperation.SetE164 -> {
          writableDatabase
            .update(TABLE_NAME)
            .values(PHONE to operation.e164)
            .where("$ID = ?", operation.recipientId)
            .run()
        }
        is PnpOperation.SetPni -> {
          writableDatabase
            .update(TABLE_NAME)
            .values(SERVICE_ID to operation.pni.toString())
            .where("$ID = ? AND ($SERVICE_ID IS NULL OR $SERVICE_ID = $PNI_COLUMN)", operation.recipientId)
            .run()

          writableDatabase
            .update(TABLE_NAME)
            .values(
              PNI_COLUMN to operation.pni.toString(),
              REGISTERED to RegisteredState.REGISTERED.id,
              UNREGISTERED_TIMESTAMP to 0
            )
            .where("$ID = ?", operation.recipientId)
            .run()
        }
        is PnpOperation.Merge -> {
          merge(operation.primaryId, operation.secondaryId, inputPni)
        }
        is PnpOperation.SessionSwitchoverInsert -> {
          // TODO [pnp]
          Log.w(TAG, "Session switchover events aren't implemented yet!")
        }
        is PnpOperation.ChangeNumberInsert -> {
          if (changeSet.id is PnpIdResolver.PnpNoopId) {
            SignalDatabase.sms.insertNumberChangeMessages(changeSet.id.recipientId)
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
        val id: Long = writableDatabase.insert(TABLE_NAME, null, buildContentValuesForNewUser(changeSet.id.e164, changeSet.id.pni, changeSet.id.aci))
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
      byPniSid = pni?.let { getByServiceId(it).orElse(null) },
      byPniOnly = pni?.let { getByPni(it).orElse(null) },
      byAciSid = aci?.let { getByServiceId(it).orElse(null) }
    )

    val allRequiredDbFields: MutableList<RecipientId?> = mutableListOf()
    if (e164 != null) {
      allRequiredDbFields += partialData.byE164
    }
    if (aci != null) {
      allRequiredDbFields += partialData.byAciSid
    }
    if (pni != null) {
      allRequiredDbFields += partialData.byPniOnly
    }
    if (pni != null && aci == null) {
      allRequiredDbFields += partialData.byPniSid
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
    if (partialData.byE164 == null && partialData.byPniSid == null && partialData.byAciSid == null) {
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

    // TODO pni only record?

    // At this point, we know that records have been found for at least two of the fields,
    // and that there are at least two unique IDs among the records.
    //
    // In other words, *some* sort of merging of data must now occur.
    // It may be that some data just gets shuffled around, or it may be that
    // two or more records get merged into one record, with the others being deleted.

    breadCrumbs += "NeedsMerge"

    val fullData = partialData.copy(
      e164Record = partialData.byE164?.let { getRecord(it) },
      pniSidRecord = partialData.byPniSid?.let { getRecord(it) },
      aciSidRecord = partialData.byAciSid?.let { getRecord(it) },
    )

    check(fullData.commonId == null)
    check(listOfNotNull(fullData.byE164, fullData.byPniSid, fullData.byPniOnly, fullData.byAciSid).size >= 2)

    val operations: MutableList<PnpOperation> = mutableListOf()

    operations += processPossibleE164PniSidMerge(pni, pniVerified, fullData, breadCrumbs)
    operations += processPossiblePniSidAciSidMerge(e164, pni, aci, fullData.perform(operations), changeSelf, breadCrumbs)
    operations += processPossibleE164AciSidMerge(e164, pni, aci, fullData.perform(operations), changeSelf, breadCrumbs)

    val finalData: PnpDataSet = fullData.perform(operations)
    val primaryId: RecipientId = listOfNotNull(finalData.byAciSid, finalData.byE164, finalData.byPniSid).first()

    if (finalData.byAciSid == null && aci != null) {
      breadCrumbs += "FinalUpdateAci"
      operations += PnpOperation.SetAci(
        recipientId = primaryId,
        aci = aci
      )
    }

    if (finalData.byE164 == null && e164 != null && (changeSelf || notSelf(e164, pni, aci))) {
      breadCrumbs += "FinalUpdateE164"
      operations += PnpOperation.SetE164(
        recipientId = primaryId,
        e164 = e164
      )
    }

    if (finalData.byPniSid == null && finalData.byPniOnly == null && pni != null) {
      breadCrumbs += "FinalUpdatePni"
      operations += PnpOperation.SetPni(
        recipientId = primaryId,
        pni = pni
      )
    }

    return PnpChangeSet(
      id = PnpIdResolver.PnpNoopId(primaryId),
      operations = operations,
      breadCrumbs = breadCrumbs
    )
  }

  private fun notSelf(e164: String?, pni: PNI?, aci: ACI?): Boolean {
    return (e164 == null || e164 != SignalStore.account().e164) &&
      (pni == null || pni != SignalStore.account().pni) &&
      (aci == null || aci != SignalStore.account().aci)
  }

  private fun isSelf(e164: String?, pni: PNI?, aci: ACI?): Boolean {
    return (e164 != null && e164 == SignalStore.account().e164) ||
      (pni != null && pni == SignalStore.account().pni) ||
      (aci != null && aci == SignalStore.account().aci)
  }

  private fun processNonMergePnpUpdate(e164: String?, pni: PNI?, aci: ACI?, pniVerified: Boolean, changeSelf: Boolean, commonId: RecipientId, breadCrumbs: MutableList<String>): PnpChangeSet {
    val record: RecipientRecord = getRecord(commonId)

    val operations: MutableList<PnpOperation> = mutableListOf()

    // This is a special case. The ACI passed in doesn't match the common record. We can't change ACIs, so we need to make a new record.
    if (aci != null && aci != record.serviceId && record.serviceId != null && !record.sidIsPni()) {
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

    if (aci != null && record.serviceId != aci) {
      operations += PnpOperation.SetAci(
        recipientId = commonId,
        aci = aci
      )
    }

    if (record.e164 != null && updatedNumber && notSelf(e164, pni, aci) && !record.isBlocked) {
      operations += PnpOperation.ChangeNumberInsert(
        recipientId = commonId,
        oldE164 = record.e164,
        newE164 = e164!!
      )
    }

    val newServiceId: ServiceId? = aci ?: pni ?: record.serviceId

    if (!pniVerified && record.serviceId != null && record.serviceId != newServiceId && sessions.hasAnySessionFor(record.serviceId.toString())) {
      operations += PnpOperation.SessionSwitchoverInsert(commonId)
    }

    return PnpChangeSet(
      id = PnpIdResolver.PnpNoopId(commonId),
      operations = operations,
      breadCrumbs = breadCrumbs
    )
  }

  private fun processPossibleE164PniSidMerge(pni: PNI?, pniVerified: Boolean, data: PnpDataSet, breadCrumbs: MutableList<String>): List<PnpOperation> {
    if (pni == null || data.byE164 == null || data.byPniSid == null || data.e164Record == null || data.pniSidRecord == null || data.e164Record.id == data.pniSidRecord.id) {
      return emptyList()
    }

    // We have found records for both the E164 and PNI, and they're different
    breadCrumbs += "E164PniSidMerge"

    val operations: MutableList<PnpOperation> = mutableListOf()

    // The PNI record only has a single identifier. We know we must merge.
    if (data.pniSidRecord.sidOnly(pni)) {
      breadCrumbs += "PniOnly"

      if (data.e164Record.pni != null) {
        operations += PnpOperation.RemovePni(data.byE164)
      }

      operations += PnpOperation.Merge(
        primaryId = data.byE164,
        secondaryId = data.byPniSid
      )

      // TODO: Possible session switchover?
    } else {
      check(!data.pniSidRecord.pniAndAci() && data.pniSidRecord.e164 != null)

      breadCrumbs += "PniSidRecordHasE164"

      operations += PnpOperation.RemovePni(data.byPniSid)
      operations += PnpOperation.SetPni(
        recipientId = data.byE164,
        pni = pni
      )

      if (!pniVerified && sessions.hasAnySessionFor(data.pniSidRecord.serviceId.toString())) {
        operations += PnpOperation.SessionSwitchoverInsert(data.byPniSid)
      }

      if (!pniVerified && data.e164Record.serviceId != null && data.e164Record.sidIsPni() && sessions.hasAnySessionFor(data.e164Record.serviceId.toString())) {
        operations += PnpOperation.SessionSwitchoverInsert(data.byE164)
      }
    }

    return operations
  }

  private fun processPossiblePniSidAciSidMerge(e164: String?, pni: PNI?, aci: ACI?, data: PnpDataSet, changeSelf: Boolean, breadCrumbs: MutableList<String>): List<PnpOperation> {
    if (pni == null || aci == null || data.byPniSid == null || data.byAciSid == null || data.pniSidRecord == null || data.aciSidRecord == null || data.pniSidRecord.id == data.aciSidRecord.id) {
      return emptyList()
    }

    if (!changeSelf && isSelf(e164, pni, aci)) {
      breadCrumbs += "ChangeSelfPreventsPniSidAciSidMerge"
      return emptyList()
    }

    // We have found records for both the PNI and ACI, and they're different
    breadCrumbs += "PniSidAciSidMerge"

    val operations: MutableList<PnpOperation> = mutableListOf()

    // The PNI record only has a single identifier. We know we must merge.
    if (data.pniSidRecord.sidOnly(pni)) {
      breadCrumbs += "PniOnly"

      if (data.aciSidRecord.pni != null) {
        operations += PnpOperation.RemovePni(data.byAciSid)
      }

      operations += PnpOperation.Merge(
        primaryId = data.byAciSid,
        secondaryId = data.byPniSid
      )
    } else if (data.pniSidRecord.e164 == e164) {
      // The PNI record also has the E164 on it. We're going to be stealing both fields,
      // so this is basically a merge with a little bit of extra prep.
      breadCrumbs += "PniSidRecordHasMatchingE164"

      if (data.aciSidRecord.pni != null) {
        operations += PnpOperation.RemovePni(data.byAciSid)
      }

      if (data.aciSidRecord.e164 != null && data.aciSidRecord.e164 != e164) {
        operations += PnpOperation.RemoveE164(data.byAciSid)
      }

      operations += PnpOperation.Merge(
        primaryId = data.byAciSid,
        secondaryId = data.byPniSid
      )

      if (data.aciSidRecord.e164 != null && data.aciSidRecord.e164 != e164 && notSelf(e164, pni, aci) && !data.aciSidRecord.isBlocked) {
        operations += PnpOperation.ChangeNumberInsert(
          recipientId = data.byAciSid,
          oldE164 = data.aciSidRecord.e164,
          newE164 = e164!!
        )
      }
    } else {
      check(data.pniSidRecord.e164 != null && data.pniSidRecord.e164 != e164)
      breadCrumbs += "PniSidRecordHasNonMatchingE164"

      operations += PnpOperation.RemovePni(data.byPniSid)

      if (data.aciSidRecord.pni != pni) {
        operations += PnpOperation.SetPni(
          recipientId = data.byAciSid,
          pni = pni
        )
      }

      if (e164 != null && data.aciSidRecord.e164 != e164) {
        operations += PnpOperation.SetE164(
          recipientId = data.byAciSid,
          e164 = e164
        )

        if (data.aciSidRecord.e164 != null && notSelf(e164, pni, aci) && !data.aciSidRecord.isBlocked) {
          operations += PnpOperation.ChangeNumberInsert(
            recipientId = data.byAciSid,
            oldE164 = data.aciSidRecord.e164,
            newE164 = e164
          )
        }
      }
    }

    return operations
  }

  private fun processPossibleE164AciSidMerge(e164: String?, pni: PNI?, aci: ACI?, data: PnpDataSet, changeSelf: Boolean, breadCrumbs: MutableList<String>): List<PnpOperation> {
    if (e164 == null || aci == null || data.byE164 == null || data.byAciSid == null || data.e164Record == null || data.aciSidRecord == null || data.e164Record.id == data.aciSidRecord.id) {
      return emptyList()
    }

    if (!changeSelf && isSelf(e164, pni, aci)) {
      breadCrumbs += "ChangeSelfPreventsE164AciSidMerge"
      return emptyList()
    }

    // We have found records for both the E164 and ACI, and they're different
    breadCrumbs += "E164AciSidMerge"

    val operations: MutableList<PnpOperation> = mutableListOf()

    // The E164 record only has a single identifier. We know we must merge.
    if (data.e164Record.e164Only()) {
      breadCrumbs += "E164Only"

      if (data.aciSidRecord.e164 != null && data.aciSidRecord.e164 != e164) {
        operations += PnpOperation.RemoveE164(data.byAciSid)
      }

      operations += PnpOperation.Merge(
        primaryId = data.byAciSid,
        secondaryId = data.byE164
      )

      if (data.aciSidRecord.e164 != null && data.aciSidRecord.e164 != e164 && notSelf(e164, pni, aci) && !data.aciSidRecord.isBlocked) {
        operations += PnpOperation.ChangeNumberInsert(
          recipientId = data.byAciSid,
          oldE164 = data.aciSidRecord.e164,
          newE164 = e164
        )
      }
    } else if (data.e164Record.pni != null && data.e164Record.pni == pni) {
      // The E164 record also has the PNI on it. We're going to be stealing both fields,
      // so this is basically a merge with a little bit of extra prep.
      breadCrumbs += "E164RecordHasMatchingPni"

      if (data.aciSidRecord.pni != null) {
        operations += PnpOperation.RemovePni(data.byAciSid)
      }

      if (data.aciSidRecord.e164 != null && data.aciSidRecord.e164 != e164) {
        operations += PnpOperation.RemoveE164(data.byAciSid)
      }

      operations += PnpOperation.Merge(
        primaryId = data.byAciSid,
        secondaryId = data.byE164
      )

      if (data.aciSidRecord.e164 != null && data.aciSidRecord.e164 != e164 && notSelf(e164, pni, aci) && !data.aciSidRecord.isBlocked) {
        operations += PnpOperation.ChangeNumberInsert(
          recipientId = data.byAciSid,
          oldE164 = data.aciSidRecord.e164,
          newE164 = e164!!
        )
      }
    } else {
      check(data.e164Record.pni == null || data.e164Record.pni != pni)
      breadCrumbs += "E164RecordHasNonMatchingPni"

      operations += PnpOperation.RemoveE164(data.byE164)

      operations += PnpOperation.SetE164(
        recipientId = data.byAciSid,
        e164 = e164
      )

      if (data.aciSidRecord.e164 != null && data.aciSidRecord.e164 != e164 && notSelf(e164, pni, aci) && !data.aciSidRecord.isBlocked) {
        operations += PnpOperation.ChangeNumberInsert(
          recipientId = data.byAciSid,
          oldE164 = data.aciSidRecord.e164,
          newE164 = e164
        )
      }
    }

    return operations
  }

  fun getUninvitedRecipientsForInsights(): List<RecipientId> {
    val results: MutableList<RecipientId> = LinkedList()
    val args = arrayOf((System.currentTimeMillis() - TimeUnit.DAYS.toMillis(31)).toString())

    readableDatabase.rawQuery(INSIGHTS_INVITEE_LIST, args).use { cursor ->
      while (cursor != null && cursor.moveToNext()) {
        results.add(RecipientId.from(cursor.getLong(cursor.getColumnIndexOrThrow(ID))))
      }
    }

    return results
  }

  fun getRegistered(): List<RecipientId> {
    val results: MutableList<RecipientId> = LinkedList()

    readableDatabase.query(TABLE_NAME, ID_PROJECTION, "$REGISTERED = ? and $HIDDEN = ?", arrayOf("1", "0"), null, null, null).use { cursor ->
      while (cursor != null && cursor.moveToNext()) {
        results.add(RecipientId.from(cursor.getLong(cursor.getColumnIndexOrThrow(ID))))
      }
    }
    return results
  }

  fun getRegisteredWithServiceIds(): Set<RecipientId> {
    return readableDatabase
      .select(ID)
      .from(TABLE_NAME)
      .where("$REGISTERED = ? and $HIDDEN = ? AND $SERVICE_ID NOT NULL", 1, 0)
      .run()
      .readToSet { cursor ->
        RecipientId.from(cursor.requireLong(ID))
      }
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

  fun getRegisteredE164s(): Set<String> {
    return readableDatabase
      .select(PHONE)
      .from(TABLE_NAME)
      .where("$REGISTERED = ? and $HIDDEN = ? AND $PHONE NOT NULL", 1, 0)
      .run()
      .readToSet { cursor ->
        cursor.requireNonNullString(PHONE)
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
              forChatColor(forLongValue(customChatColorsId), ChatColor.parseFrom(serializedChatColors))
            } catch (e: InvalidProtocolBufferException) {
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
            put(CHAT_COLORS, chatColors.serialize().toByteArray())
            put(CUSTOM_CHAT_COLORS_ID, chatColors.id.longValue)
          }
          db.update(TABLE_NAME, contentValues, "$ID = ?", arrayOf(id.toString()))
          updates[RecipientId.from(id)] = chatColors
        }
      }
    } finally {
      db.setTransactionSuccessful()
      db.endTransaction()
      updates.entries.forEach { ApplicationDependencies.getDatabaseObserver().notifyRecipientChanged(it.key) }
    }
  }

  fun getSignalContacts(includeSelf: Boolean): Cursor? {
    return getSignalContacts(includeSelf, "$SORT_NAME, $SYSTEM_JOINED_NAME, $SEARCH_PROFILE_NAME, $USERNAME, $PHONE")
  }

  fun getSignalContactsCount(includeSelf: Boolean): Int {
    return getSignalContacts(includeSelf)?.count ?: 0
  }

  fun getSignalContacts(includeSelf: Boolean, orderBy: String? = null): Cursor? {
    val searchSelection = ContactSearchSelection.Builder()
      .withRegistered(true)
      .withGroups(false)
      .excludeId(if (includeSelf) null else Recipient.self().id)
      .build()
    val selection = searchSelection.where
    val args = searchSelection.args
    return readableDatabase.query(TABLE_NAME, SEARCH_PROJECTION, selection, args, null, null, orderBy)
  }

  fun querySignalContacts(inputQuery: String, includeSelf: Boolean): Cursor? {
    val query = SqlUtil.buildCaseInsensitiveGlobPattern(inputQuery)

    val searchSelection = ContactSearchSelection.Builder()
      .withRegistered(true)
      .withGroups(false)
      .excludeId(if (includeSelf) null else Recipient.self().id)
      .withSearchQuery(query)
      .build()
    val selection = searchSelection.where
    val args = searchSelection.args
    val orderBy = "$SORT_NAME, $SYSTEM_JOINED_NAME, $SEARCH_PROFILE_NAME, $PHONE"

    return readableDatabase.query(TABLE_NAME, SEARCH_PROJECTION, selection, args, null, null, orderBy)
  }

  fun querySignalContactLetterHeaders(inputQuery: String, includeSelf: Boolean): Map<RecipientId, String> {
    val searchSelection = ContactSearchSelection.Builder()
      .withRegistered(true)
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
          ORDER BY $SORT_NAME, $SYSTEM_JOINED_NAME, $SEARCH_PROFILE_NAME, $PHONE
        )
        GROUP BY letter_header
      """.trimIndent(),
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
    val orderBy = "$SYSTEM_JOINED_NAME, $PHONE"
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
    val orderBy = "$SYSTEM_JOINED_NAME, $PHONE"
    return readableDatabase.query(TABLE_NAME, SEARCH_PROJECTION, selection, args, null, null, orderBy)
  }

  fun getNonGroupContacts(includeSelf: Boolean): Cursor? {
    val searchSelection = ContactSearchSelection.Builder()
      .withRegistered(true)
      .withNonRegistered(true)
      .withGroups(false)
      .excludeId(if (includeSelf) null else Recipient.self().id)
      .build()
    val orderBy = orderByPreferringAlphaOverNumeric(SORT_NAME) + ", " + PHONE
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
    val orderBy = orderByPreferringAlphaOverNumeric(SORT_NAME) + ", " + PHONE

    return readableDatabase.query(TABLE_NAME, SEARCH_PROJECTION, selection, args, null, null, orderBy)
  }

  fun queryAllContacts(inputQuery: String): Cursor? {
    val query = SqlUtil.buildCaseInsensitiveGlobPattern(inputQuery)
    val selection =
      """
        $BLOCKED = ? AND $HIDDEN = ? AND
        (
          $SORT_NAME GLOB ? OR 
          $USERNAME GLOB ? OR 
          $PHONE GLOB ? OR 
          $EMAIL GLOB ?
        )
      """.trimIndent()
    val args = SqlUtil.buildArgs(0, 0, query, query, query, query)
    return readableDatabase.query(TABLE_NAME, SEARCH_PROJECTION, selection, args, null, null, null)
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
        recipients.add(recipient)
        recipient = reader.getNext()
      }
    }

    return recipients
  }

  fun getRecipientsForMultiDeviceSync(): List<Recipient> {
    val subquery = "SELECT ${ThreadDatabase.TABLE_NAME}.${ThreadDatabase.RECIPIENT_ID} FROM ${ThreadDatabase.TABLE_NAME}"
    val selection = "$REGISTERED = ? AND $GROUP_ID IS NULL AND $ID != ? AND ($SYSTEM_CONTACT_URI NOT NULL OR $ID IN ($subquery))"
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
    val db = writableDatabase
    db.beginTransaction()
    try {
      val values = ContentValues(1).apply {
        put(LAST_PROFILE_FETCH, time)
      }

      for (id in ids) {
        db.update(TABLE_NAME, values, ID_WHERE, arrayOf(id.serialize()))
      }
      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
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
        db.update(TABLE_NAME, setBlocked, "$PHONE = ?", arrayOf(e164))
      }

      for (uuid in blockedUuid) {
        db.update(TABLE_NAME, setBlocked, "$SERVICE_ID = ?", arrayOf(uuid))
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

    ApplicationDependencies.getRecipientCache().clear()
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
          put(STORAGE_SERVICE_ID, Base64.encodeBytes(value!!))
        }
        db.update(TABLE_NAME, values, ID_WHERE, arrayOf(key.serialize()))
      }
      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }

    for (id in ids.keys) {
      ApplicationDependencies.getDatabaseObserver().notifyRecipientChanged(id)
    }
  }

  fun markPreMessageRequestRecipientsAsProfileSharingEnabled(messageRequestEnableTime: Long) {
    val whereArgs = SqlUtil.buildArgs(messageRequestEnableTime, messageRequestEnableTime)
    val select =
      """
        SELECT r.$ID FROM $TABLE_NAME AS r 
        INNER JOIN ${ThreadDatabase.TABLE_NAME} AS t ON t.${ThreadDatabase.RECIPIENT_ID} = r.$ID
        WHERE
          r.$PROFILE_SHARING = 0 AND (
            EXISTS(SELECT 1 FROM ${SmsDatabase.TABLE_NAME} WHERE ${SmsDatabase.THREAD_ID} = t.${ThreadDatabase.ID} AND ${SmsDatabase.DATE_RECEIVED} < ?) OR
            EXISTS(SELECT 1 FROM ${MmsDatabase.TABLE_NAME} WHERE ${MmsDatabase.THREAD_ID} = t.${ThreadDatabase.ID} AND ${MmsDatabase.DATE_RECEIVED} < ?)
          )
      """.trimIndent()

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
        ApplicationDependencies.getDatabaseObserver().notifyRecipientChanged(RecipientId.from(id))
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
            ApplicationDependencies.getDatabaseObserver().notifyRecipientChanged(RecipientId.from(id))
          }
        }
      }
    }
  }

  fun manuallyShowAvatar(recipientId: RecipientId) {
    updateExtras(recipientId) { b: RecipientExtras.Builder -> b.setManuallyShownAvatar(true) }
  }

  fun getCapabilities(id: RecipientId): RecipientRecord.Capabilities? {
    readableDatabase
      .select(CAPABILITIES)
      .from(TABLE_NAME)
      .where("$ID = ?", id)
      .run()
      .use { cursor ->
        return if (cursor.moveToFirst()) {
          readCapabilities(cursor)
        } else {
          null
        }
      }
  }

  private fun updateExtras(recipientId: RecipientId, updater: java.util.function.Function<RecipientExtras.Builder, RecipientExtras.Builder>) {
    val db = writableDatabase
    db.beginTransaction()
    try {
      db.query(TABLE_NAME, arrayOf(ID, EXTRAS), ID_WHERE, SqlUtil.buildArgs(recipientId), null, null, null).use { cursor ->
        if (cursor.moveToNext()) {
          val state = getRecipientExtras(cursor)
          val builder = if (state != null) state.toBuilder() else RecipientExtras.newBuilder()
          val updatedState = updater.apply(builder).build().toByteArray()
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
    ApplicationDependencies.getDatabaseObserver().notifyRecipientChanged(recipientId)
  }

  /**
   * Does not trigger any recipient refreshes -- it is assumed the caller handles this.
   * Will *not* give storageIds to those that shouldn't get them (e.g. MMS groups, unregistered
   * users).
   */
  fun rotateStorageId(recipientId: RecipientId) {
    val values = ContentValues(1).apply {
      put(STORAGE_SERVICE_ID, Base64.encodeBytes(StorageSyncHelper.generateKey()))
    }

    val query = "$ID = ? AND ($GROUP_TYPE IN (?, ?, ?) OR $REGISTERED = ?)"
    val args = SqlUtil.buildArgs(recipientId, GroupType.SIGNAL_V1.id, GroupType.SIGNAL_V2.id, GroupType.DISTRIBUTION_LIST.id, RegisteredState.REGISTERED.id)
    writableDatabase.update(TABLE_NAME, values, query, args)
  }

  /**
   * Does not trigger any recipient refreshes -- it is assumed the caller handles this.
   */
  fun setStorageIdIfNotSet(recipientId: RecipientId) {
    val values = ContentValues(1).apply {
      put(STORAGE_SERVICE_ID, Base64.encodeBytes(StorageSyncHelper.generateKey()))
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
      put(GROUP_TYPE, GroupType.SIGNAL_V2.id)
    }

    val query = SqlUtil.buildTrueUpdateQuery("$GROUP_ID = ?", SqlUtil.buildArgs(v1Id), values)
    if (update(query, values)) {
      val id = getByGroupId(v2Id).get()
      rotateStorageId(id)
      ApplicationDependencies.getDatabaseObserver().notifyRecipientChanged(id)
    }
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
  private fun merge(primaryId: RecipientId, secondaryId: RecipientId, newPni: PNI? = null): RecipientId {
    ensureInTransaction()
    val db = writableDatabase
    val primaryRecord = getRecord(primaryId)
    val secondaryRecord = getRecord(secondaryId)

    // Clean up any E164-based identities (legacy stuff)
    if (secondaryRecord.e164 != null) {
      ApplicationDependencies.getProtocolStore().aci().identities().delete(secondaryRecord.e164)
    }

    // Threads
    val threadMerge = threads.merge(primaryId, secondaryId)
    threads.setLastScrolled(threadMerge.threadId, 0)
    threads.update(threadMerge.threadId, false, false)

    // Recipient remaps
    for (table in recipientIdDatabaseTables) {
      table.remapRecipient(secondaryId, primaryId)
    }

    // Thread remaps
    if (threadMerge.neededMerge) {
      for (table in threadIdDatabaseTables) {
        table.remapThread(threadMerge.previousThreadId, threadMerge.threadId)
      }

      // Thread Merge Event
      val mergeEvent: ThreadMergeEvent.Builder = ThreadMergeEvent.newBuilder()

      if (secondaryRecord.e164 != null) {
        mergeEvent.previousE164 = secondaryRecord.e164
      }

      SignalDatabase.sms.insertThreadMergeEvent(primaryRecord.id, threadMerge.threadId, mergeEvent.build())
    }

    // Recipient
    Log.w(TAG, "Deleting recipient $secondaryId", true)
    db.delete(TABLE_NAME, ID_WHERE, SqlUtil.buildArgs(secondaryId))
    RemappedRecords.getInstance().addRecipient(secondaryId, primaryId)

    val uuidValues = contentValuesOf(
      PHONE to (secondaryRecord.e164 ?: primaryRecord.e164),
      SERVICE_ID to (primaryRecord.serviceId ?: secondaryRecord.serviceId)?.toString(),
      PNI_COLUMN to (newPni ?: secondaryRecord.pni ?: primaryRecord.pni)?.toString(),
      BLOCKED to (secondaryRecord.isBlocked || primaryRecord.isBlocked),
      MESSAGE_RINGTONE to Optional.ofNullable(primaryRecord.messageRingtone).or(Optional.ofNullable(secondaryRecord.messageRingtone)).map { obj: Uri? -> obj.toString() }.orElse(null),
      MESSAGE_VIBRATE to if (primaryRecord.messageVibrateState != VibrateState.DEFAULT) primaryRecord.messageVibrateState.id else secondaryRecord.messageVibrateState.id,
      CALL_RINGTONE to Optional.ofNullable(primaryRecord.callRingtone).or(Optional.ofNullable(secondaryRecord.callRingtone)).map { obj: Uri? -> obj.toString() }.orElse(null),
      CALL_VIBRATE to if (primaryRecord.callVibrateState != VibrateState.DEFAULT) primaryRecord.callVibrateState.id else secondaryRecord.callVibrateState.id,
      NOTIFICATION_CHANNEL to (primaryRecord.notificationChannel ?: secondaryRecord.notificationChannel),
      MUTE_UNTIL to if (primaryRecord.muteUntil > 0) primaryRecord.muteUntil else secondaryRecord.muteUntil,
      CHAT_COLORS to Optional.ofNullable(primaryRecord.chatColors).or(Optional.ofNullable(secondaryRecord.chatColors)).map { colors: ChatColors? -> colors!!.serialize().toByteArray() }.orElse(null),
      AVATAR_COLOR to primaryRecord.avatarColor.serialize(),
      CUSTOM_CHAT_COLORS_ID to Optional.ofNullable(primaryRecord.chatColors).or(Optional.ofNullable(secondaryRecord.chatColors)).map { colors: ChatColors? -> colors!!.id.longValue }.orElse(null),
      SEEN_INVITE_REMINDER to secondaryRecord.insightsBannerTier.id,
      DEFAULT_SUBSCRIPTION_ID to secondaryRecord.getDefaultSubscriptionId().orElse(-1),
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
      MENTION_SETTING to if (primaryRecord.mentionSetting != MentionSetting.ALWAYS_NOTIFY) primaryRecord.mentionSetting.id else secondaryRecord.mentionSetting.id
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
    return primaryId
  }

  private fun ensureInTransaction() {
    check(writableDatabase.inTransaction()) { "Must be in a transaction!" }
  }

  private fun buildContentValuesForNewUser(e164: String?, pni: PNI?, aci: ACI?): ContentValues {
    check(e164 != null || pni != null || aci != null) { "Must provide some sort of identifier!" }

    val values = contentValuesOf(
      PHONE to e164,
      SERVICE_ID to (aci ?: pni)?.toString(),
      PNI_COLUMN to pni?.toString(),
      STORAGE_SERVICE_ID to Base64.encodeBytes(StorageSyncHelper.generateKey()),
      AVATAR_COLOR to AvatarColor.random().serialize()
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

      if (contact.serviceId.isValid) {
        put(SERVICE_ID, contact.serviceId.toString())
      }

      if (FeatureFlags.phoneNumberPrivacy()) {
        put(PNI_COLUMN, contact.pni.orElse(null)?.toString())
      }

      put(PHONE, contact.number.orElse(null))
      put(PROFILE_GIVEN_NAME, profileName.givenName)
      put(PROFILE_FAMILY_NAME, profileName.familyName)
      put(PROFILE_JOINED_NAME, profileName.toString())
      put(SYSTEM_GIVEN_NAME, systemName.givenName)
      put(SYSTEM_FAMILY_NAME, systemName.familyName)
      put(SYSTEM_JOINED_NAME, systemName.toString())
      put(PROFILE_KEY, contact.profileKey.map { source -> Base64.encodeBytes(source) }.orElse(null))
      put(USERNAME, if (TextUtils.isEmpty(username)) null else username)
      put(PROFILE_SHARING, if (contact.isProfileSharingEnabled) "1" else "0")
      put(BLOCKED, if (contact.isBlocked) "1" else "0")
      put(MUTE_UNTIL, contact.muteUntil)
      put(STORAGE_SERVICE_ID, Base64.encodeBytes(contact.id.raw))
      put(HIDDEN, contact.isHidden)

      if (contact.hasUnknownFields()) {
        put(STORAGE_PROTO, Base64.encodeBytes(Objects.requireNonNull(contact.serializeUnknownFields())))
      } else {
        putNull(STORAGE_PROTO)
      }

      put(UNREGISTERED_TIMESTAMP, contact.unregisteredTimestamp)
      if (contact.unregisteredTimestamp > 0L) {
        put(REGISTERED, RegisteredState.NOT_REGISTERED.id)
      } else if (contact.serviceId.isValid) {
        put(REGISTERED, RegisteredState.REGISTERED.id)
      } else {
        Log.w(TAG, "Contact is marked as registered, but has no serviceId! Can't locally mark registered. (Phone: ${contact.number.orElse("null")}, Username: ${username?.isNotEmpty()})")
      }

      if (isInsert) {
        put(AVATAR_COLOR, AvatarColor.random().serialize())
      }
    }
  }

  private fun getValuesForStorageGroupV1(groupV1: SignalGroupV1Record, isInsert: Boolean): ContentValues {
    return ContentValues().apply {
      put(GROUP_ID, GroupId.v1orThrow(groupV1.groupId).toString())
      put(GROUP_TYPE, GroupType.SIGNAL_V1.id)
      put(PROFILE_SHARING, if (groupV1.isProfileSharingEnabled) "1" else "0")
      put(BLOCKED, if (groupV1.isBlocked) "1" else "0")
      put(MUTE_UNTIL, groupV1.muteUntil)
      put(STORAGE_SERVICE_ID, Base64.encodeBytes(groupV1.id.raw))

      if (groupV1.hasUnknownFields()) {
        put(STORAGE_PROTO, Base64.encodeBytes(groupV1.serializeUnknownFields()))
      } else {
        putNull(STORAGE_PROTO)
      }

      if (isInsert) {
        put(AVATAR_COLOR, AvatarColor.random().serialize())
      }
    }
  }

  private fun getValuesForStorageGroupV2(groupV2: SignalGroupV2Record, isInsert: Boolean): ContentValues {
    return ContentValues().apply {
      put(GROUP_ID, GroupId.v2(groupV2.masterKeyOrThrow).toString())
      put(GROUP_TYPE, GroupType.SIGNAL_V2.id)
      put(PROFILE_SHARING, if (groupV2.isProfileSharingEnabled) "1" else "0")
      put(BLOCKED, if (groupV2.isBlocked) "1" else "0")
      put(MUTE_UNTIL, groupV2.muteUntil)
      put(STORAGE_SERVICE_ID, Base64.encodeBytes(groupV2.id.raw))
      put(MENTION_SETTING, if (groupV2.notifyForMentionsWhenMuted()) MentionSetting.ALWAYS_NOTIFY.id else MentionSetting.DO_NOT_NOTIFY.id)

      if (groupV2.hasUnknownFields()) {
        put(STORAGE_PROTO, Base64.encodeBytes(groupV2.serializeUnknownFields()))
      } else {
        putNull(STORAGE_PROTO)
      }

      if (isInsert) {
        put(AVATAR_COLOR, AvatarColor.random().serialize())
      }
    }
  }

  /**
   * Should only be used for debugging! A very destructive action that clears all known serviceIds from people with phone numbers (so that we could eventually
   * get them back through CDS).
   */
  fun debugClearServiceIds(recipientId: RecipientId? = null) {
    writableDatabase
      .update(TABLE_NAME)
      .values(
        SERVICE_ID to null,
        PNI_COLUMN to null
      )
      .run {
        if (recipientId == null) {
          where("$ID != ? AND $PHONE NOT NULL", Recipient.self().id)
        } else {
          where("$ID = ? AND $PHONE NOT NULL", recipientId)
        }
      }
      .run()

    ApplicationDependencies.getRecipientCache().clear()
    RecipientId.clearCache()
  }

  /**
   * Should only be used for debugging! A very destructive action that clears all known profile keys and credentials.
   */
  fun debugClearProfileData(recipientId: RecipientId? = null) {
    writableDatabase
      .update(TABLE_NAME)
      .values(
        PROFILE_KEY to null,
        EXPIRING_PROFILE_KEY_CREDENTIAL to null,
        PROFILE_GIVEN_NAME to null,
        PROFILE_FAMILY_NAME to null,
        PROFILE_JOINED_NAME to null,
        LAST_PROFILE_FETCH to 0,
        SIGNAL_PROFILE_AVATAR to null
      )
      .run {
        if (recipientId == null) {
          where("$ID != ?", Recipient.self().id)
        } else {
          where("$ID = ?", recipientId)
        }
      }
      .run()

    ApplicationDependencies.getRecipientCache().clear()
    RecipientId.clearCache()
  }

  /**
   * Should only be used for debugging! Clears the E164 and PNI from a recipient.
   */
  fun debugClearE164AndPni(recipientId: RecipientId) {
    writableDatabase
      .update(TABLE_NAME)
      .values(
        PHONE to null,
        PNI_COLUMN to null
      )
      .where(ID_WHERE, recipientId)
      .run()

    Recipient.live(recipientId).refresh()
  }

  fun getRecord(context: Context, cursor: Cursor): RecipientRecord {
    return getRecord(context, cursor, ID)
  }

  fun getRecord(context: Context, cursor: Cursor, idColumnName: String): RecipientRecord {
    val profileKeyString = cursor.requireString(PROFILE_KEY)
    val expiringProfileKeyCredentialString = cursor.requireString(EXPIRING_PROFILE_KEY_CREDENTIAL)
    var profileKey: ByteArray? = null
    var expiringProfileKeyCredential: ExpiringProfileKeyCredential? = null

    if (profileKeyString != null) {
      try {
        profileKey = Base64.decode(profileKeyString)
      } catch (e: IOException) {
        Log.w(TAG, e)
      }

      if (expiringProfileKeyCredentialString != null) {
        try {
          val columnDataBytes = Base64.decode(expiringProfileKeyCredentialString)
          val columnData = ExpiringProfileKeyCredentialColumnData.parseFrom(columnDataBytes)
          if (Arrays.equals(columnData.profileKey.toByteArray(), profileKey)) {
            expiringProfileKeyCredential = ExpiringProfileKeyCredential(columnData.expiringProfileKeyCredential.toByteArray())
          } else {
            Log.i(TAG, "Out of date profile key credential data ignored on read")
          }
        } catch (e: InvalidInputException) {
          Log.w(TAG, "Profile key credential column data could not be read", e)
        } catch (e: IOException) {
          Log.w(TAG, "Profile key credential column data could not be read", e)
        }
      }
    }

    val serializedWallpaper = cursor.requireBlob(WALLPAPER)
    val chatWallpaper: ChatWallpaper? = if (serializedWallpaper != null) {
      try {
        ChatWallpaperFactory.create(Wallpaper.parseFrom(serializedWallpaper))
      } catch (e: InvalidProtocolBufferException) {
        Log.w(TAG, "Failed to parse wallpaper.", e)
        null
      }
    } else {
      null
    }

    val customChatColorsId = cursor.requireLong(CUSTOM_CHAT_COLORS_ID)
    val serializedChatColors = cursor.requireBlob(CHAT_COLORS)
    val chatColors: ChatColors? = if (serializedChatColors != null) {
      try {
        forChatColor(forLongValue(customChatColorsId), ChatColor.parseFrom(serializedChatColors))
      } catch (e: InvalidProtocolBufferException) {
        Log.w(TAG, "Failed to parse chat colors.", e)
        null
      }
    } else {
      null
    }

    val recipientId = RecipientId.from(cursor.requireLong(idColumnName))
    val distributionListId: DistributionListId? = DistributionListId.fromNullable(cursor.requireLong(DISTRIBUTION_LIST_ID))
    val avatarColor: AvatarColor = if (distributionListId != null) AvatarColor.UNKNOWN else AvatarColor.deserialize(cursor.requireString(AVATAR_COLOR))

    return RecipientRecord(
      id = recipientId,
      serviceId = ServiceId.parseOrNull(cursor.requireString(SERVICE_ID)),
      pni = PNI.parseOrNull(cursor.requireString(PNI_COLUMN)),
      username = cursor.requireString(USERNAME),
      e164 = cursor.requireString(PHONE),
      email = cursor.requireString(EMAIL),
      groupId = GroupId.parseNullableOrThrow(cursor.requireString(GROUP_ID)),
      distributionListId = distributionListId,
      groupType = GroupType.fromId(cursor.requireInt(GROUP_TYPE)),
      isBlocked = cursor.requireBoolean(BLOCKED),
      muteUntil = cursor.requireLong(MUTE_UNTIL),
      messageVibrateState = VibrateState.fromId(cursor.requireInt(MESSAGE_VIBRATE)),
      callVibrateState = VibrateState.fromId(cursor.requireInt(CALL_VIBRATE)),
      messageRingtone = Util.uri(cursor.requireString(MESSAGE_RINGTONE)),
      callRingtone = Util.uri(cursor.requireString(CALL_RINGTONE)),
      defaultSubscriptionId = cursor.requireInt(DEFAULT_SUBSCRIPTION_ID),
      expireMessages = cursor.requireInt(MESSAGE_EXPIRATION_TIME),
      registered = RegisteredState.fromId(cursor.requireInt(REGISTERED)),
      profileKey = profileKey,
      expiringProfileKeyCredential = expiringProfileKeyCredential,
      systemProfileName = ProfileName.fromParts(cursor.requireString(SYSTEM_GIVEN_NAME), cursor.requireString(SYSTEM_FAMILY_NAME)),
      systemDisplayName = cursor.requireString(SYSTEM_JOINED_NAME),
      systemContactPhotoUri = cursor.requireString(SYSTEM_PHOTO_URI),
      systemPhoneLabel = cursor.requireString(SYSTEM_PHONE_LABEL),
      systemContactUri = cursor.requireString(SYSTEM_CONTACT_URI),
      signalProfileName = ProfileName.fromParts(cursor.requireString(PROFILE_GIVEN_NAME), cursor.requireString(PROFILE_FAMILY_NAME)),
      signalProfileAvatar = cursor.requireString(SIGNAL_PROFILE_AVATAR),
      profileAvatarFileDetails = AvatarHelper.getAvatarFileDetails(context, recipientId),
      profileSharing = cursor.requireBoolean(PROFILE_SHARING),
      lastProfileFetch = cursor.requireLong(LAST_PROFILE_FETCH),
      notificationChannel = cursor.requireString(NOTIFICATION_CHANNEL),
      unidentifiedAccessMode = UnidentifiedAccessMode.fromMode(cursor.requireInt(UNIDENTIFIED_ACCESS_MODE)),
      forceSmsSelection = cursor.requireBoolean(FORCE_SMS_SELECTION),
      capabilities = readCapabilities(cursor),
      insightsBannerTier = InsightsBannerTier.fromId(cursor.requireInt(SEEN_INVITE_REMINDER)),
      storageId = Base64.decodeNullableOrThrow(cursor.requireString(STORAGE_SERVICE_ID)),
      mentionSetting = MentionSetting.fromId(cursor.requireInt(MENTION_SETTING)),
      wallpaper = chatWallpaper,
      chatColors = chatColors,
      avatarColor = avatarColor,
      about = cursor.requireString(ABOUT),
      aboutEmoji = cursor.requireString(ABOUT_EMOJI),
      syncExtras = getSyncExtras(cursor),
      extras = getExtras(cursor),
      hasGroupsInCommon = cursor.requireBoolean(GROUPS_IN_COMMON),
      badges = parseBadgeList(cursor.requireBlob(BADGES)),
      needsPniSignature = cursor.requireBoolean(NEEDS_PNI_SIGNATURE),
      isHidden = cursor.requireBoolean(HIDDEN)
    )
  }

  private fun readCapabilities(cursor: Cursor): RecipientRecord.Capabilities {
    val capabilities = cursor.requireLong(CAPABILITIES)
    return RecipientRecord.Capabilities(
      rawBits = capabilities,
      groupsV1MigrationCapability = Recipient.Capability.deserialize(Bitmask.read(capabilities, Capabilities.GROUPS_V1_MIGRATION, Capabilities.BIT_LENGTH).toInt()),
      senderKeyCapability = Recipient.Capability.deserialize(Bitmask.read(capabilities, Capabilities.SENDER_KEY, Capabilities.BIT_LENGTH).toInt()),
      announcementGroupCapability = Recipient.Capability.deserialize(Bitmask.read(capabilities, Capabilities.ANNOUNCEMENT_GROUPS, Capabilities.BIT_LENGTH).toInt()),
      changeNumberCapability = Recipient.Capability.deserialize(Bitmask.read(capabilities, Capabilities.CHANGE_NUMBER, Capabilities.BIT_LENGTH).toInt()),
      storiesCapability = Recipient.Capability.deserialize(Bitmask.read(capabilities, Capabilities.STORIES, Capabilities.BIT_LENGTH).toInt()),
      giftBadgesCapability = Recipient.Capability.deserialize(Bitmask.read(capabilities, Capabilities.GIFT_BADGES, Capabilities.BIT_LENGTH).toInt()),
      pnpCapability = Recipient.Capability.deserialize(Bitmask.read(capabilities, Capabilities.PNP, Capabilities.BIT_LENGTH).toInt()),
    )
  }

  private fun parseBadgeList(serializedBadgeList: ByteArray?): List<Badge> {
    var badgeList: BadgeList? = null
    if (serializedBadgeList != null) {
      try {
        badgeList = BadgeList.parseFrom(serializedBadgeList)
      } catch (e: InvalidProtocolBufferException) {
        Log.w(TAG, e)
      }
    }

    val badges: List<Badge>
    if (badgeList != null) {
      val protoBadges = badgeList.badgesList
      badges = ArrayList(protoBadges.size)
      for (protoBadge in protoBadges) {
        badges.add(Badges.fromDatabaseBadge(protoBadge))
      }
    } else {
      badges = emptyList()
    }

    return badges
  }

  private fun getSyncExtras(cursor: Cursor): RecipientRecord.SyncExtras {
    val storageProtoRaw = cursor.optionalString(STORAGE_PROTO).orElse(null)
    val storageProto = if (storageProtoRaw != null) Base64.decodeOrThrow(storageProtoRaw) else null
    val archived = cursor.optionalBoolean(ThreadDatabase.ARCHIVED).orElse(false)
    val forcedUnread = cursor.optionalInt(ThreadDatabase.READ).map { status: Int -> status == ThreadDatabase.ReadStatus.FORCED_UNREAD.serialize() }.orElse(false)
    val groupMasterKey = cursor.optionalBlob(GroupDatabase.V2_MASTER_KEY).map { GroupUtil.requireMasterKey(it) }.orElse(null)
    val identityKey = cursor.optionalString(IDENTITY_KEY).map { Base64.decodeOrThrow(it) }.orElse(null)
    val identityStatus = cursor.optionalInt(IDENTITY_STATUS).map { VerifiedStatus.forState(it) }.orElse(VerifiedStatus.DEFAULT)
    val unregisteredTimestamp = cursor.optionalLong(UNREGISTERED_TIMESTAMP).orElse(0)

    return RecipientRecord.SyncExtras(
      storageProto = storageProto,
      groupMasterKey = groupMasterKey,
      identityKey = identityKey,
      identityStatus = identityStatus,
      isArchived = archived,
      isForcedUnread = forcedUnread,
      unregisteredTimestamp = unregisteredTimestamp
    )
  }

  private fun getExtras(cursor: Cursor): Recipient.Extras? {
    return Recipient.Extras.from(getRecipientExtras(cursor))
  }

  private fun getRecipientExtras(cursor: Cursor): RecipientExtras? {
    return cursor.optionalBlob(EXTRAS).map { b: ByteArray? ->
      try {
        RecipientExtras.parseFrom(b)
      } catch (e: InvalidProtocolBufferException) {
        Log.w(TAG, e)
        throw AssertionError(e)
      }
    }.orElse(null)
  }

  private fun updateProfileValuesForMerge(values: ContentValues, record: RecipientRecord) {
    values.apply {
      put(PROFILE_KEY, if (record.profileKey != null) Base64.encodeBytes(record.profileKey) else null)
      putNull(EXPIRING_PROFILE_KEY_CREDENTIAL)
      put(SIGNAL_PROFILE_AVATAR, record.signalProfileAvatar)
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

  private fun RecipientRecord.toLogDetails(): RecipientLogDetails {
    return RecipientLogDetails(
      id = this.id,
      serviceId = this.serviceId,
      e164 = this.e164
    )
  }

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

      val updatedValues = update(id, refreshQualifyingValues)
      if (updatedValues) {
        pendingRecipients.add(id)
      }

      val otherValues = ContentValues().apply {
        put(SYSTEM_INFO_PENDING, 0)
      }

      update(id, otherValues)
    }

    fun finish() {
      markAllRelevantEntriesDirty()
      clearSystemDataForPendingInfo()
      database.setTransactionSuccessful()
      database.endTransaction()
      pendingRecipients.forEach { id -> ApplicationDependencies.getDatabaseObserver().notifyRecipientChanged(id) }
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
    }

    private fun clearSystemDataForPendingInfo() {
      database.update(TABLE_NAME)
        .values(
          SYSTEM_INFO_PENDING to 0,
          SYSTEM_GIVEN_NAME to null,
          SYSTEM_FAMILY_NAME to null,
          SYSTEM_JOINED_NAME to null,
          SYSTEM_PHOTO_URI to null,
          SYSTEM_PHONE_LABEL to null,
          SYSTEM_CONTACT_URI to null
        )
        .where("$SYSTEM_INFO_PENDING = ?", 1)
        .run()
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

  class MissingRecipientException(id: RecipientId?) : IllegalStateException("Failed to find recipient with ID: $id")

  private class GetOrInsertResult(val recipientId: RecipientId, val neededInsert: Boolean)

  @VisibleForTesting
  internal class ContactSearchSelection private constructor(val where: String, val args: Array<String>) {

    @VisibleForTesting
    internal class Builder {
      private var includeRegistered = false
      private var includeNonRegistered = false
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
        check(!(!includeRegistered && !includeNonRegistered)) { "Must include either registered or non-registered recipients in search" }
        val stringBuilder = StringBuilder("(")
        val args: MutableList<Any?> = LinkedList()

        if (includeRegistered) {
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

        if (includeRegistered && includeNonRegistered) {
          stringBuilder.append(" OR ")
        }

        if (includeNonRegistered) {
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
      const val FILTER_GROUPS = " AND $GROUP_ID IS NULL"
      const val FILTER_ID = " AND $ID != ?"
      const val FILTER_BLOCKED = " AND $BLOCKED = ?"
      const val FILTER_HIDDEN = " AND $HIDDEN = ?"
      const val NON_SIGNAL_CONTACT = "$REGISTERED != ? AND $SYSTEM_CONTACT_URI NOT NULL AND ($PHONE NOT NULL OR $EMAIL NOT NULL)"
      const val QUERY_NON_SIGNAL_CONTACT = "$NON_SIGNAL_CONTACT AND ($PHONE GLOB ? OR $EMAIL GLOB ? OR $SYSTEM_JOINED_NAME GLOB ?)"
      const val SIGNAL_CONTACT = "$REGISTERED = ? AND (NULLIF($SYSTEM_JOINED_NAME, '') NOT NULL OR $PROFILE_SHARING = ?) AND ($SORT_NAME NOT NULL OR $USERNAME NOT NULL)"
      const val QUERY_SIGNAL_CONTACT = "$SIGNAL_CONTACT AND ($PHONE GLOB ? OR $SORT_NAME GLOB ? OR $USERNAME GLOB ?)"
    }
  }

  /**
   * Values that represent the index in the capabilities bitmask. Each index can store a 2-bit
   * value, which in this case is the value of [Recipient.Capability].
   */
  internal object Capabilities {
    const val BIT_LENGTH = 2

    //    const val GROUPS_V2 = 0
    const val GROUPS_V1_MIGRATION = 1
    const val SENDER_KEY = 2
    const val ANNOUNCEMENT_GROUPS = 3
    const val CHANGE_NUMBER = 4
    const val STORIES = 5
    const val GIFT_BADGES = 6
    const val PNP = 7
  }

  enum class VibrateState(val id: Int) {
    DEFAULT(0), ENABLED(1), DISABLED(2);

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
    UNKNOWN(0), REGISTERED(1), NOT_REGISTERED(2);

    companion object {
      fun fromId(id: Int): RegisteredState {
        return values()[id]
      }
    }
  }

  enum class UnidentifiedAccessMode(val mode: Int) {
    UNKNOWN(0), DISABLED(1), ENABLED(2), UNRESTRICTED(3);

    companion object {
      fun fromMode(mode: Int): UnidentifiedAccessMode {
        return values()[mode]
      }
    }
  }

  enum class InsightsBannerTier(val id: Int) {
    NO_TIER(0), TIER_ONE(1), TIER_TWO(2);

    fun seen(tier: InsightsBannerTier): Boolean {
      return tier.id <= id
    }

    companion object {
      fun fromId(id: Int): InsightsBannerTier {
        return values()[id]
      }
    }
  }

  enum class GroupType(val id: Int) {
    NONE(0), MMS(1), SIGNAL_V1(2), SIGNAL_V2(3), DISTRIBUTION_LIST(4);

    companion object {
      fun fromId(id: Int): GroupType {
        return values()[id]
      }
    }
  }

  enum class MentionSetting(val id: Int) {
    ALWAYS_NOTIFY(0), DO_NOT_NOTIFY(1);

    companion object {
      fun fromId(id: Int): MentionSetting {
        return values()[id]
      }
    }
  }

  private sealed class RecipientFetch(val logBundle: LogBundle?) {
    /**
     * We have a matching recipient, and no writes need to occur.
     */
    data class Match(val id: RecipientId, val bundle: LogBundle?) : RecipientFetch(bundle)

    /**
     * We found a matching recipient and can update them with a new E164.
     */
    data class MatchAndUpdateE164(val id: RecipientId, val e164: String, val changedNumber: RecipientId?, val bundle: LogBundle) : RecipientFetch(bundle)

    /**
     * We found a matching recipient and can give them an E164 that used to belong to someone else.
     */
    data class MatchAndReassignE164(val id: RecipientId, val e164Id: RecipientId, val e164: String, val changedNumber: RecipientId?, val bundle: LogBundle) : RecipientFetch(bundle)

    /**
     * We found a matching recipient and can update them with a new ACI.
     */
    data class MatchAndUpdateAci(val id: RecipientId, val serviceId: ServiceId, val bundle: LogBundle) : RecipientFetch(bundle)

    /**
     * We found a matching recipient and can insert an ACI as a *new user*.
     */
    data class MatchAndInsertAci(val id: RecipientId, val serviceId: ServiceId, val bundle: LogBundle) : RecipientFetch(bundle)

    /**
     * The ACI maps to ACI-only recipient, and the E164 maps to a different E164-only recipient. We need to merge the two together.
     */
    data class MatchAndMerge(val sidId: RecipientId, val e164Id: RecipientId, val changedNumber: RecipientId?, val bundle: LogBundle) : RecipientFetch(bundle)

    /**
     * We don't have a matching recipient, so we need to insert one.
     */
    data class Insert(val serviceId: ServiceId?, val e164: String?, val bundle: LogBundle) : RecipientFetch(bundle)

    /**
     * We need to create a new recipient and give it the E164 of an existing recipient.
     */
    data class InsertAndReassignE164(val serviceId: ServiceId?, val e164: String?, val e164Id: RecipientId, val bundle: LogBundle) : RecipientFetch(bundle)
  }

  /**
   * Simple class for [fetchRecipient] to pass back info that can be logged.
   */
  private data class LogBundle(
    val label: String,
    val serviceId: ServiceId? = null,
    val e164: String? = null,
    val bySid: RecipientLogDetails? = null,
    val byE164: RecipientLogDetails? = null
  ) {
    fun label(label: String): LogBundle {
      return this.copy(label = label)
    }
  }

  /**
   * Minimal info about a recipient that we'd want to log. Used in [fetchRecipient].
   */
  private data class RecipientLogDetails(
    val id: RecipientId,
    val serviceId: ServiceId? = null,
    val e164: String? = null
  )

  data class CdsV2Result(
    val pni: PNI,
    val aci: ACI?
  ) {
    fun bestServiceId(): ServiceId {
      return if (aci != null) {
        aci
      } else {
        pni
      }
    }
  }

  data class ProcessPnpTupleResult(
    val finalId: RecipientId,
    val requiredInsert: Boolean,
    val affectedIds: Set<RecipientId>,
    val oldIds: Set<RecipientId>,
    val changedNumberId: RecipientId?,
    val operations: List<PnpOperation>,
    val breadCrumbs: List<String>,
  )
}
