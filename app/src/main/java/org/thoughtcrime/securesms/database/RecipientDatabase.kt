package org.thoughtcrime.securesms.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.text.TextUtils
import androidx.annotation.VisibleForTesting
import com.google.protobuf.ByteString
import com.google.protobuf.InvalidProtocolBufferException
import net.zetetic.database.sqlcipher.SQLiteConstraintException
import org.signal.core.util.logging.Log
import org.signal.storageservice.protos.groups.local.DecryptedGroup
import org.signal.zkgroup.InvalidInputException
import org.signal.zkgroup.profiles.ProfileKey
import org.signal.zkgroup.profiles.ProfileKeyCredential
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
import org.thoughtcrime.securesms.database.IdentityDatabase.VerifiedStatus
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.groups
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.identities
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.messageLog
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.notificationProfiles
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.reactions
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.sessions
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.threads
import org.thoughtcrime.securesms.database.model.RecipientRecord
import org.thoughtcrime.securesms.database.model.ThreadRecord
import org.thoughtcrime.securesms.database.model.databaseprotos.BadgeList
import org.thoughtcrime.securesms.database.model.databaseprotos.ChatColor
import org.thoughtcrime.securesms.database.model.databaseprotos.DeviceLastResetTime
import org.thoughtcrime.securesms.database.model.databaseprotos.ProfileKeyCredentialColumnData
import org.thoughtcrime.securesms.database.model.databaseprotos.RecipientExtras
import org.thoughtcrime.securesms.database.model.databaseprotos.Wallpaper
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.groups.BadGroupIdException
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.groups.GroupId.V1
import org.thoughtcrime.securesms.groups.GroupId.V2
import org.thoughtcrime.securesms.groups.v2.ProfileKeySet
import org.thoughtcrime.securesms.groups.v2.processing.GroupsV2StateProcessor
import org.thoughtcrime.securesms.jobs.RecipientChangedNumberJob
import org.thoughtcrime.securesms.jobs.RefreshAttributesJob
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
import org.thoughtcrime.securesms.util.Bitmask
import org.thoughtcrime.securesms.util.GroupUtil
import org.thoughtcrime.securesms.util.IdentityUtil
import org.thoughtcrime.securesms.util.SqlUtil
import org.thoughtcrime.securesms.util.StringUtil
import org.thoughtcrime.securesms.util.Util
import org.thoughtcrime.securesms.wallpaper.ChatWallpaper
import org.thoughtcrime.securesms.wallpaper.ChatWallpaperFactory
import org.thoughtcrime.securesms.wallpaper.WallpaperStorage
import org.whispersystems.libsignal.IdentityKey
import org.whispersystems.libsignal.InvalidKeyException
import org.whispersystems.libsignal.util.Pair
import org.whispersystems.libsignal.util.guava.Optional
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile
import org.whispersystems.signalservice.api.push.ACI
import org.whispersystems.signalservice.api.push.PNI
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.api.storage.SignalAccountRecord
import org.whispersystems.signalservice.api.storage.SignalContactRecord
import org.whispersystems.signalservice.api.storage.SignalGroupV1Record
import org.whispersystems.signalservice.api.storage.SignalGroupV2Record
import org.whispersystems.signalservice.api.storage.StorageId
import java.io.Closeable
import java.io.IOException
import java.lang.AssertionError
import java.lang.IllegalStateException
import java.lang.StringBuilder
import java.util.ArrayList
import java.util.Arrays
import java.util.Collections
import java.util.HashMap
import java.util.HashSet
import java.util.LinkedHashSet
import java.util.LinkedList
import java.util.Objects
import java.util.concurrent.TimeUnit
import kotlin.jvm.Throws
import kotlin.math.max

open class RecipientDatabase(context: Context, databaseHelper: SignalDatabase) : Database(context, databaseHelper) {

  companion object {
    private val TAG = Log.tag(RecipientDatabase::class.java)

    const val TABLE_NAME = "recipient"

    const val ID = "_id"
    private const val ACI_COLUMN = "uuid"
    private const val PNI_COLUMN = "pni"
    private const val USERNAME = "username"
    const val PHONE = "phone"
    const val EMAIL = "email"
    const val GROUP_ID = "group_id"
    const val GROUP_TYPE = "group_type"
    private const val BLOCKED = "blocked"
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
    private const val PROFILE_KEY_CREDENTIAL = "profile_key_credential"
    private const val SIGNAL_PROFILE_AVATAR = "signal_profile_avatar"
    private const val PROFILE_SHARING = "profile_sharing"
    private const val LAST_PROFILE_FETCH = "last_profile_fetch"
    private const val UNIDENTIFIED_ACCESS_MODE = "unidentified_access_mode"
    const val FORCE_SMS_SELECTION = "force_sms_selection"
    private const val CAPABILITIES = "capabilities"
    private const val STORAGE_SERVICE_ID = "storage_service_key"
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

    @JvmField
    val CREATE_TABLE =
      """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY AUTOINCREMENT,
        $ACI_COLUMN TEXT UNIQUE DEFAULT NULL,
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
        $PROFILE_KEY_CREDENTIAL TEXT DEFAULT NULL, 
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
        $PNI_COLUMN TEXT DEFAULT NULL
      )
      """.trimIndent()

    val CREATE_INDEXS = arrayOf(
      "CREATE INDEX IF NOT EXISTS recipient_group_type_index ON $TABLE_NAME ($GROUP_TYPE);",
      "CREATE UNIQUE INDEX IF NOT EXISTS recipient_pni_index ON $TABLE_NAME ($PNI_COLUMN)"
    )

    private val RECIPIENT_PROJECTION: Array<String> = arrayOf(
      ID,
      ACI_COLUMN,
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
      PROFILE_KEY_CREDENTIAL,
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
      BADGES
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
        ${ThreadDatabase.TABLE_NAME}.${ThreadDatabase.DATE} > ?
      ORDER BY ${ThreadDatabase.TABLE_NAME}.${ThreadDatabase.DATE} DESC LIMIT 50
      """
  }

  fun containsPhoneOrUuid(id: String): Boolean {
    val query = "$ACI_COLUMN = ? OR $PHONE = ?"
    val args = arrayOf(id, id)
    readableDatabase.query(TABLE_NAME, arrayOf(ID), query, args, null, null, null).use { cursor -> return cursor != null && cursor.moveToFirst() }
  }

  fun getByE164(e164: String): Optional<RecipientId> {
    return getByColumn(PHONE, e164)
  }

  fun getByEmail(email: String): Optional<RecipientId> {
    return getByColumn(EMAIL, email)
  }

  fun getByGroupId(groupId: GroupId): Optional<RecipientId> {
    return getByColumn(GROUP_ID, groupId.toString())
  }

  fun getByAci(uuid: ACI): Optional<RecipientId> {
    return getByColumn(ACI_COLUMN, uuid.toString())
  }

  fun getByUsername(username: String): Optional<RecipientId> {
    return getByColumn(USERNAME, username)
  }

  fun getAndPossiblyMerge(aci: ACI?, e164: String?, highTrust: Boolean): RecipientId {
    return getAndPossiblyMerge(aci, e164, highTrust, false)
  }

  fun getAndPossiblyMerge(aci: ACI?, e164: String?, highTrust: Boolean, changeSelf: Boolean): RecipientId {
    require(!(aci == null && e164 == null)) { "Must provide an ACI or E164!" }

    val db = writableDatabase

    var transactionSuccessful = false
    var remapped: Pair<RecipientId, RecipientId>? = null
    var recipientsNeedingRefresh: List<RecipientId> = listOf()
    var recipientChangedNumber: RecipientId? = null

    db.beginTransaction()
    try {
      val fetch: RecipientFetch = fetchRecipient(aci, e164, highTrust, changeSelf)

      if (fetch.logBundle != null) {
        Log.w(TAG, fetch.toString())
      }

      val resolvedId: RecipientId = when (fetch) {
        is RecipientFetch.Match -> {
          fetch.id
        }
        is RecipientFetch.MatchAndUpdateE164 -> {
          setPhoneNumberOrThrowSilent(fetch.id, fetch.e164)
          recipientsNeedingRefresh = listOf(fetch.id)
          recipientChangedNumber = fetch.changedNumber
          fetch.id
        }
        is RecipientFetch.MatchAndReassignE164 -> {
          removePhoneNumber(fetch.e164Id, db)
          setPhoneNumberOrThrowSilent(fetch.id, fetch.e164)
          recipientsNeedingRefresh = listOf(fetch.id, fetch.e164Id)
          recipientChangedNumber = fetch.changedNumber
          fetch.id
        }
        is RecipientFetch.MatchAndUpdateAci -> {
          markRegistered(fetch.id, fetch.aci)
          recipientsNeedingRefresh = listOf(fetch.id)
          fetch.id
        }
        is RecipientFetch.MatchAndInsertAci -> {
          val id = db.insert(TABLE_NAME, null, buildContentValuesForNewUser(null, fetch.aci))
          RecipientId.from(id)
        }
        is RecipientFetch.MatchAndMerge -> {
          remapped = Pair(fetch.e164Id, fetch.aciId)
          val mergedId: RecipientId = merge(fetch.aciId, fetch.e164Id)
          recipientsNeedingRefresh = listOf(mergedId)
          recipientChangedNumber = fetch.changedNumber
          mergedId
        }
        is RecipientFetch.Insert -> {
          val id = db.insert(TABLE_NAME, null, buildContentValuesForNewUser(fetch.e164, fetch.aci))
          RecipientId.from(id)
        }
        is RecipientFetch.InsertAndReassignE164 -> {
          removePhoneNumber(fetch.e164Id, db)
          recipientsNeedingRefresh = listOf(fetch.e164Id)
          val id = db.insert(TABLE_NAME, null, buildContentValuesForNewUser(fetch.e164, fetch.aci))
          RecipientId.from(id)
        }
      }

      transactionSuccessful = true
      db.setTransactionSuccessful()
      return resolvedId
    } finally {
      db.endTransaction()

      if (transactionSuccessful) {
        if (recipientsNeedingRefresh.isNotEmpty()) {
          recipientsNeedingRefresh.forEach { ApplicationDependencies.getDatabaseObserver().notifyRecipientChanged(it) }
          RetrieveProfileJob.enqueue(recipientsNeedingRefresh.toSet())
        }

        if (remapped != null) {
          Recipient.live(remapped.first()).refresh(remapped.second())
          ApplicationDependencies.getRecipientCache().remap(remapped.first(), remapped.second())
        }

        if (recipientsNeedingRefresh.isNotEmpty() || remapped != null) {
          StorageSyncHelper.scheduleSyncForDataChange()
          RecipientId.clearCache()
        }

        if (recipientChangedNumber != null) {
          ApplicationDependencies.getJobManager().add(RecipientChangedNumberJob(recipientChangedNumber))
        }
      }
    }
  }

  private fun fetchRecipient(aci: ACI?, e164: String?, highTrust: Boolean, changeSelf: Boolean): RecipientFetch {
    val byE164 = e164?.let { getByE164(it) } ?: Optional.absent()
    val byAci = aci?.let { getByAci(it) } ?: Optional.absent()

    var logs = LogBundle(
      byAci = byAci.transform { id -> RecipientLogDetails(id = id) }.orNull(),
      byE164 = byE164.transform { id -> RecipientLogDetails(id = id) }.orNull(),
      label = "L0"
    )

    if (byAci.isPresent && byE164.isPresent && byAci.get() == byE164.get()) {
      return RecipientFetch.Match(byAci.get(), null)
    }

    if (byAci.isPresent && byE164.isAbsent()) {
      val aciRecord: RecipientRecord = getRecord(byAci.get())
      logs = logs.copy(byAci = aciRecord.toLogDetails())

      if (highTrust && e164 != null && (changeSelf || aci != SignalStore.account().aci)) {
        val changedNumber: RecipientId? = if (aciRecord.e164 != null && aciRecord.e164 != e164) aciRecord.id else null
        return RecipientFetch.MatchAndUpdateE164(byAci.get(), e164, changedNumber, logs.label("L1"))
      } else if (e164 == null) {
        return RecipientFetch.Match(byAci.get(), null)
      } else {
        return RecipientFetch.Match(byAci.get(), logs.label("L2"))
      }
    }

    if (byAci.isAbsent() && byE164.isPresent) {
      val e164Record: RecipientRecord = getRecord(byE164.get())
      logs = logs.copy(byE164 = e164Record.toLogDetails())

      if (highTrust && aci != null && e164Record.aci == null) {
        return RecipientFetch.MatchAndUpdateAci(byE164.get(), aci, logs.label("L3"))
      } else if (highTrust && aci != null && e164Record.aci != SignalStore.account().aci) {
        return RecipientFetch.InsertAndReassignE164(aci, e164, byE164.get(), logs.label("L4"))
      } else if (aci != null) {
        return RecipientFetch.Insert(aci, null, logs.label("L5"))
      } else {
        return RecipientFetch.Match(byE164.get(), null)
      }
    }

    if (byAci.isAbsent() && byE164.isAbsent()) {
      if (highTrust) {
        return RecipientFetch.Insert(aci, e164, logs.label("L6"))
      } else if (aci != null) {
        return RecipientFetch.Insert(aci, null, logs.label("L7"))
      } else {
        return RecipientFetch.Insert(null, e164, logs.label("L8"))
      }
    }

    require(byAci.isPresent && byE164.isPresent && byAci.get() != byE164.get()) { "Assumed conditions at this point." }

    val aciRecord: RecipientRecord = getRecord(byAci.get())
    val e164Record: RecipientRecord = getRecord(byE164.get())

    logs = logs.copy(byAci = aciRecord.toLogDetails(), byE164 = e164Record.toLogDetails())

    if (e164Record.aci == null) {
      if (highTrust) {
        val changedNumber: RecipientId? = if (aciRecord.e164 != null) aciRecord.id else null
        return RecipientFetch.MatchAndMerge(aciId = byAci.get(), e164Id = byE164.get(), changedNumber = changedNumber, logs.label("L9"))
      } else {
        return RecipientFetch.Match(byAci.get(), logs.label("L10"))
      }
    } else {
      if (highTrust && e164Record.aci != SignalStore.account().aci) {
        val changedNumber: RecipientId? = if (aciRecord.e164 != null) aciRecord.id else null
        return RecipientFetch.MatchAndReassignE164(id = byAci.get(), e164Id = byE164.get(), e164 = e164!!, changedNumber = changedNumber, logs.label("L11"))
      } else {
        return RecipientFetch.Match(byAci.get(), logs.label("L12"))
      }
    }
  }

  fun getOrInsertFromAci(aci: ACI): RecipientId {
    return getOrInsertByColumn(ACI_COLUMN, aci.toString()).recipientId
  }

  fun getOrInsertFromE164(e164: String): RecipientId {
    return getOrInsertByColumn(PHONE, e164).recipientId
  }

  fun getOrInsertFromEmail(email: String): RecipientId {
    return getOrInsertByColumn(EMAIL, email).recipientId
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

  fun markNeedsSync(recipientId: RecipientId) {
    rotateStorageId(recipientId)
    ApplicationDependencies.getDatabaseObserver().notifyRecipientChanged(recipientId)
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

    val recipientId: RecipientId?
    if (id < 0) {
      Log.w(TAG, "[applyStorageSyncContactInsert] Failed to insert. Possibly merging.")
      recipientId = getAndPossiblyMerge(if (insert.address.hasValidAci()) insert.address.aci else null, insert.address.number.orNull(), true)
      db.update(TABLE_NAME, values, ID_WHERE, SqlUtil.buildArgs(recipientId))
    } else {
      recipientId = RecipientId.from(id)
    }

    if (insert.identityKey.isPresent && insert.address.hasValidAci()) {
      try {
        val identityKey = IdentityKey(insert.identityKey.get(), 0)
        identities.updateIdentityAfterSync(insert.address.identifier, recipientId!!, identityKey, StorageSyncModels.remoteToLocalIdentityStatus(insert.identityState))
      } catch (e: InvalidKeyException) {
        Log.w(TAG, "Failed to process identity key during insert! Skipping.", e)
      }
    }

    threadDatabase.applyStorageSyncUpdate(recipientId!!, insert)
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
      recipientId = getAndPossiblyMerge(if (update.new.address.hasValidAci()) update.new.address.aci else null, update.new.address.number.orNull(), true)

      Log.w(TAG, "[applyStorageSyncContactUpdate] Merged into $recipientId")
      db.update(TABLE_NAME, values, ID_WHERE, SqlUtil.buildArgs(recipientId))
    }

    val recipientId = getByStorageKeyOrThrow(update.new.id.raw)
    if (StorageSyncHelper.profileKeyChanged(update)) {
      val clearValues = ContentValues(1).apply {
        putNull(PROFILE_KEY_CREDENTIAL)
      }
      db.update(TABLE_NAME, clearValues, ID_WHERE, SqlUtil.buildArgs(recipientId))
    }

    try {
      val oldIdentityRecord = identityStore.getIdentityRecord(recipientId)
      if (update.new.identityKey.isPresent && update.new.address.hasValidAci()) {
        val identityKey = IdentityKey(update.new.identityKey.get(), 0)
        identities.updateIdentityAfterSync(update.new.address.identifier, recipientId, identityKey, StorageSyncModels.remoteToLocalIdentityStatus(update.new.identityState))
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

    val recipient = Recipient.externalGroupExact(context, GroupId.v1orThrow(update.old.groupId))
    threads.applyStorageSyncUpdate(recipient.id, update.new)
    recipient.live().refresh()
  }

  fun applyStorageSyncGroupV2Insert(insert: SignalGroupV2Record) {
    val masterKey = insert.masterKeyOrThrow
    val groupId = GroupId.v2(masterKey)
    val values = getValuesForStorageGroupV2(insert, true)

    writableDatabase.insertOrThrow(TABLE_NAME, null, values)
    val recipient = Recipient.externalGroupExact(context, groupId)

    Log.i(TAG, "Creating restore placeholder for $groupId")
    groups.create(
      masterKey,
      DecryptedGroup.newBuilder()
        .setRevision(GroupsV2StateProcessor.RESTORE_PLACEHOLDER_REVISION)
        .build()
    )

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
    val recipient = Recipient.externalGroupExact(context, GroupId.v2(masterKey))

    threads.applyStorageSyncUpdate(recipient.id, update.new)
    recipient.live().refresh()
  }

  fun applyStorageSyncAccountUpdate(update: StorageRecordUpdate<SignalAccountRecord>) {
    val profileName = ProfileName.fromParts(update.new.givenName.orNull(), update.new.familyName.orNull())
    val localKey = ProfileKeyUtil.profileKeyOptional(update.old.profileKey.orNull())
    val remoteKey = ProfileKeyUtil.profileKeyOptional(update.new.profileKey.orNull())
    val profileKey = remoteKey.or(localKey).transform { obj: ProfileKey -> obj.serialize() }.transform { source: ByteArray? -> Base64.encodeBytes(source!!) }.orNull()
    if (!remoteKey.isPresent) {
      Log.w(TAG, "Got an empty profile key while applying an account record update!")
    }

    val values = ContentValues().apply {
      put(PROFILE_GIVEN_NAME, profileName.givenName)
      put(PROFILE_FAMILY_NAME, profileName.familyName)
      put(PROFILE_JOINED_NAME, profileName.toString())
      put(PROFILE_KEY, profileKey)
      put(STORAGE_SERVICE_ID, Base64.encodeBytes(update.new.id.raw))
      if (update.new.hasUnknownFields()) {
        put(STORAGE_PROTO, Base64.encodeBytes(Objects.requireNonNull(update.new.serializeUnknownFields())))
      } else {
        putNull(STORAGE_PROTO)
      }
    }

    val updateCount = writableDatabase.update(TABLE_NAME, values, STORAGE_SERVICE_ID + " = ?", arrayOf(Base64.encodeBytes(update.old.id.raw)))
    if (updateCount < 1) {
      throw AssertionError("Account update didn't match any rows!")
    }

    if (remoteKey != localKey) {
      ApplicationDependencies.getJobManager().add(RefreshAttributesJob())
    }

    threads.applyStorageSyncUpdate(Recipient.self().id, update.new)
    Recipient.self().live().refresh()
  }

  fun updatePhoneNumbers(mapping: Map<String?, String?>) {
    if (mapping.isEmpty()) return
    val db = writableDatabase

    db.beginTransaction()
    try {
      val query = "$PHONE = ?"
      for ((key, value) in mapping) {
        val values = ContentValues().apply {
          put(PHONE, value)
        }
        db.updateWithOnConflict(TABLE_NAME, values, query, arrayOf(key), SQLiteDatabase.CONFLICT_IGNORE)
      }
      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
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

  private fun getRecordForSync(query: String?, args: Array<String>?): List<RecipientRecord> {
    val table =
      """
      $TABLE_NAME LEFT OUTER JOIN ${IdentityDatabase.TABLE_NAME} ON $TABLE_NAME.$ACI_COLUMN = ${IdentityDatabase.TABLE_NAME}.${IdentityDatabase.ADDRESS} 
                  LEFT OUTER JOIN ${GroupDatabase.TABLE_NAME} ON $TABLE_NAME.$GROUP_ID = ${GroupDatabase.TABLE_NAME}.${GroupDatabase.GROUP_ID} 
                  LEFT OUTER JOIN ${ThreadDatabase.TABLE_NAME} ON $TABLE_NAME.$ID = ${ThreadDatabase.TABLE_NAME}.${ThreadDatabase.RECIPIENT_ID}
      """.trimIndent()
    val out: MutableList<RecipientRecord> = ArrayList()
    val columns: Array<String> = TYPED_RECIPIENT_PROJECTION + arrayOf(
      "$TABLE_NAME.$STORAGE_PROTO",
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
   * @return All storage IDs for ContactRecords, excluding the ones that need to be deleted.
   */
  fun getContactStorageSyncIdsMap(): Map<RecipientId, StorageId> {
    val query = """
      $STORAGE_SERVICE_ID NOT NULL AND (
        ($GROUP_TYPE = ? AND $ACI_COLUMN NOT NULL AND $ID != ?)
        OR
        $GROUP_TYPE IN (?)
      )
    """.trimIndent()
    val args = SqlUtil.buildArgs(GroupType.NONE.id, Recipient.self().id, GroupType.SIGNAL_V1.id)
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
          else -> throw AssertionError()
        }
      }
    }

    for (id in groups.allGroupV2Ids) {
      val recipient = Recipient.externalGroupExact(context, id!!)
      val recipientId = recipient.id
      val existing: RecipientRecord = getRecordForSync(recipientId) ?: throw AssertionError()
      val key = existing.storageId ?: throw AssertionError()
      out[recipientId] = StorageId.forGroupV2(key)
    }

    return out
  }

  fun beginBulkSystemContactUpdate(): BulkOperationsHandle {
    val db = writableDatabase
    val contentValues = ContentValues(1).apply {
      put(SYSTEM_INFO_PENDING, 1)
    }

    db.beginTransaction()
    db.update(TABLE_NAME, contentValues, "$SYSTEM_CONTACT_URI NOT NULL", null)
    return BulkOperationsHandle(db)
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
      val query = SqlUtil.buildCollectionQuery(ID, ids)
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

  fun setBadges(id: RecipientId, badges: List<Badge?>) {
    val badgeListBuilder = BadgeList.newBuilder()
    for (badge in badges) {
      badgeListBuilder.addBadges(toDatabaseBadge(badge!!))
    }

    val values = ContentValues(1).apply {
      put(BADGES, badgeListBuilder.build().toByteArray())
    }

    if (update(id, values)) {
      ApplicationDependencies.getDatabaseObserver().notifyNotificationProfileObservers()
      ApplicationDependencies.getDatabaseObserver().notifyRecipientChanged(id)
    }
  }

  fun setCapabilities(id: RecipientId, capabilities: SignalServiceProfile.Capabilities) {
    var value: Long = 0
    value = Bitmask.update(value, Capabilities.GROUPS_V2, Capabilities.BIT_LENGTH, Recipient.Capability.fromBoolean(capabilities.isGv2).serialize().toLong())
    value = Bitmask.update(value, Capabilities.GROUPS_V1_MIGRATION, Capabilities.BIT_LENGTH, Recipient.Capability.fromBoolean(capabilities.isGv1Migration).serialize().toLong())
    value = Bitmask.update(value, Capabilities.SENDER_KEY, Capabilities.BIT_LENGTH, Recipient.Capability.fromBoolean(capabilities.isSenderKey).serialize().toLong())
    value = Bitmask.update(value, Capabilities.ANNOUNCEMENT_GROUPS, Capabilities.BIT_LENGTH, Recipient.Capability.fromBoolean(capabilities.isAnnouncementGroup).serialize().toLong())
    value = Bitmask.update(value, Capabilities.CHANGE_NUMBER, Capabilities.BIT_LENGTH, Recipient.Capability.fromBoolean(capabilities.isChangeNumber).serialize().toLong())

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
      putNull(PROFILE_KEY_CREDENTIAL)
      put(UNIDENTIFIED_ACCESS_MODE, UnidentifiedAccessMode.UNKNOWN.mode)
    }

    val updateQuery = SqlUtil.buildTrueUpdateQuery(selection, args, valuesToCompare)

    if (update(updateQuery, valuesToSet)) {
      rotateStorageId(id)
      ApplicationDependencies.getDatabaseObserver().notifyRecipientChanged(id)
      StorageSyncHelper.scheduleSyncForDataChange()
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
      putNull(PROFILE_KEY_CREDENTIAL)
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
    profileKeyCredential: ProfileKeyCredential
  ): Boolean {
    val selection = "$ID = ? AND $PROFILE_KEY = ?"
    val args = arrayOf(id.serialize(), Base64.encodeBytes(profileKey.serialize()))
    val columnData = ProfileKeyCredentialColumnData.newBuilder()
      .setProfileKey(ByteString.copyFrom(profileKey.serialize()))
      .setProfileKeyCredential(ByteString.copyFrom(profileKeyCredential.serialize()))
      .build()
    val values = ContentValues(1).apply {
      put(PROFILE_KEY_CREDENTIAL, Base64.encodeBytes(columnData.toByteArray()))
    }
    val updateQuery = SqlUtil.buildTrueUpdateQuery(selection, args, values)

    val updated = update(updateQuery, values)
    if (updated) {
      ApplicationDependencies.getDatabaseObserver().notifyRecipientChanged(id)
    }

    return updated
  }

  private fun clearProfileKeyCredential(id: RecipientId) {
    val values = ContentValues(1)
    values.putNull(PROFILE_KEY_CREDENTIAL)
    if (update(id, values)) {
      rotateStorageId(id)
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
      val recipientId = getOrInsertFromAci(key)
      if (setProfileKeyIfAbsent(recipientId, value)) {
        Log.i(TAG, "Learned new profile key")
        updated.add(recipientId)
      }
    }

    for ((key, value) in authoritativeProfileKeys) {
      val recipientId = getOrInsertFromAci(key)

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
    val where = "checked_name = ?"
    val arguments = SqlUtil.buildArgs(recipient.profileName.toString())

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

  fun setProfileSharing(id: RecipientId, enabled: Boolean) {
    val contentValues = ContentValues(1).apply {
      put(PROFILE_SHARING, if (enabled) 1 else 0)
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
              cursor.optionalString(WALLPAPER_URI).orNull()
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
          ApplicationDependencies.getDatabaseObserver().notifyRecipientChanged(pair.first())
          if (pair.second() != null) {
            WallpaperStorage.onWallpaperDeselected(context, Uri.parse(pair.second()))
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
      val newId = getAndPossiblyMerge(existing.aci, e164, true)
      Log.w(TAG, "[setPhoneNumber] Resulting id: $newId")

      db.setTransactionSuccessful()
      newId != existing.id
    } finally {
      db.endTransaction()
    }
  }

  private fun removePhoneNumber(recipientId: RecipientId, db: SQLiteDatabase) {
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

  fun updateSelfPhone(e164: String) {
    val db = writableDatabase

    db.beginTransaction()
    try {
      val id = Recipient.self().id
      val newId = getAndPossiblyMerge(Recipient.self().requireAci(), e164, highTrust = true, changeSelf = true)

      if (id == newId) {
        Log.i(TAG, "[updateSelfPhone] Phone updated for self")
      } else {
        throw AssertionError("[updateSelfPhone] Self recipient id changed when updating phone. old: $id new: $newId")
      }

      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }
  }

  fun setUsername(id: RecipientId, username: String?) {
    if (username != null) {
      val existingUsername = getByUsername(username)
      if (existingUsername.isPresent && id != existingUsername.get()) {
        Log.i(TAG, "Username was previously thought to be owned by " + existingUsername.get() + ". Clearing their username.")
        setUsername(existingUsername.get(), null)
      }
    }

    val contentValues = ContentValues(1).apply {
      put(USERNAME, username)
    }
    if (update(id, contentValues)) {
      ApplicationDependencies.getDatabaseObserver().notifyRecipientChanged(id)
      StorageSyncHelper.scheduleSyncForDataChange()
    }
  }

  fun clearUsernameIfExists(username: String) {
    val existingUsername = getByUsername(username)
    if (existingUsername.isPresent) {
      setUsername(existingUsername.get(), null)
    }
  }

  fun getAllPhoneNumbers(): Set<String> {
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

  fun setPni(id: RecipientId, pni: PNI) {
    val values = ContentValues().apply {
      put(PNI_COLUMN, pni.toString())
    }
    writableDatabase.update(TABLE_NAME, values, ID_WHERE, SqlUtil.buildArgs(id))
  }

  /**
   * @return True if setting the UUID resulted in changed recipientId, otherwise false.
   */
  fun markRegistered(id: RecipientId, aci: ACI): Boolean {
    val db = writableDatabase

    db.beginTransaction()
    try {
      markRegisteredOrThrow(id, aci)
      db.setTransactionSuccessful()
      return false
    } catch (e: SQLiteConstraintException) {
      Log.w(TAG, "[markRegistered] Hit a conflict when trying to update $id. Possibly merging.")

      val existing = getRecord(id)
      val newId = getAndPossiblyMerge(aci, existing.e164, true)
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
  fun markRegisteredOrThrow(id: RecipientId, aci: ACI) {
    val contentValues = ContentValues(2).apply {
      put(REGISTERED, RegisteredState.REGISTERED.id)
      put(ACI_COLUMN, aci.toString().toLowerCase())
    }
    if (update(id, contentValues)) {
      setStorageIdIfNotSet(id)
      ApplicationDependencies.getDatabaseObserver().notifyRecipientChanged(id)
    }
  }

  fun markUnregistered(id: RecipientId) {
    val contentValues = ContentValues(2).apply {
      put(REGISTERED, RegisteredState.NOT_REGISTERED.id)
      putNull(STORAGE_SERVICE_ID)
    }
    if (update(id, contentValues)) {
      ApplicationDependencies.getDatabaseObserver().notifyRecipientChanged(id)
    }
  }

  fun bulkUpdatedRegisteredStatus(registered: Map<RecipientId, ACI?>, unregistered: Collection<RecipientId>) {
    val db = writableDatabase

    db.beginTransaction()
    try {
      for ((recipientId, aci) in registered) {
        val values = ContentValues(2).apply {
          put(REGISTERED, RegisteredState.REGISTERED.id)
          if (aci != null) {
            put(ACI_COLUMN, aci.toString().toLowerCase())
          }
        }

        try {
          if (update(recipientId, values)) {
            setStorageIdIfNotSet(recipientId)
          }
        } catch (e: SQLiteConstraintException) {
          Log.w(TAG, "[bulkUpdateRegisteredStatus] Hit a conflict when trying to update $recipientId. Possibly merging.")
          val e164 = getRecord(recipientId).e164
          val newId = getAndPossiblyMerge(aci, e164, true)
          Log.w(TAG, "[bulkUpdateRegisteredStatus] Merged into $newId")
        }
      }

      for (id in unregistered) {
        val values = ContentValues(2).apply {
          put(REGISTERED, RegisteredState.NOT_REGISTERED.id)
          putNull(STORAGE_SERVICE_ID)
        }
        update(id, values)
      }

      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
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
        var aciEntry = if (aci != null) getByAci(aci) else Optional.absent()

        if (aciEntry.isPresent) {
          val idChanged = setPhoneNumber(aciEntry.get(), e164)
          if (idChanged) {
            aciEntry = getByAci(aci!!)
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

    readableDatabase.query(TABLE_NAME, ID_PROJECTION, "$REGISTERED = ?", arrayOf("1"), null, null, null).use { cursor ->
      while (cursor != null && cursor.moveToNext()) {
        results.add(RecipientId.from(cursor.getLong(cursor.getColumnIndexOrThrow(ID))))
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
    val searchSelection = ContactSearchSelection.Builder()
      .withRegistered(true)
      .withGroups(false)
      .excludeId(if (includeSelf) null else Recipient.self().id)
      .build()
    val selection = searchSelection.where
    val args = searchSelection.args
    val orderBy = "$SORT_NAME, $SYSTEM_JOINED_NAME, $SEARCH_PROFILE_NAME, $USERNAME, $PHONE"
    return readableDatabase.query(TABLE_NAME, SEARCH_PROJECTION, selection, args, null, null, orderBy)
  }

  fun querySignalContacts(inputQuery: String, includeSelf: Boolean): Cursor? {
    val query = buildCaseInsensitiveGlobPattern(inputQuery)

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
    val query = buildCaseInsensitiveGlobPattern(inputQuery)
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
    val query = buildCaseInsensitiveGlobPattern(inputQuery)

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
    val query = buildCaseInsensitiveGlobPattern(inputQuery)
    val selection =
      """
        $BLOCKED = ? AND 
        (
          $SORT_NAME GLOB ? OR 
          $USERNAME GLOB ? OR 
          $PHONE GLOB ? OR 
          $EMAIL GLOB ?
        )
      """.trimIndent()
    val args = SqlUtil.buildArgs("0", query, query, query, query)
    return readableDatabase.query(TABLE_NAME, SEARCH_PROJECTION, selection, args, null, null, null)
  }

  @JvmOverloads
  fun queryRecipientsForMentions(inputQuery: String, recipientIds: List<RecipientId>? = null): List<Recipient> {
    val query = buildCaseInsensitiveGlobPattern(inputQuery)
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
    val recipientsWithinInteractionThreshold: MutableSet<Recipient> = LinkedHashSet()

    threadDatabase.readerFor(threadDatabase.getRecentPushConversationList(-1, false)).use { reader ->
      var record: ThreadRecord? = reader.next

      while (record != null && record.date > lastInteractionThreshold) {
        val recipient = Recipient.resolved(record.recipient.id)
        if (recipient.isGroup) {
          recipientsWithinInteractionThreshold.addAll(recipient.participants)
        } else {
          recipientsWithinInteractionThreshold.add(recipient)
        }
        record = reader.next
      }
    }

    return recipientsWithinInteractionThreshold
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
      .map { b: SignalServiceAddress -> b.aci.toString().toLowerCase() }
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
      val query = SqlUtil.buildCollectionQuery(ID, idsToUpdate)
      val values = ContentValues(1).apply {
        put(PROFILE_SHARING, 1)
      }

      writableDatabase.update(TABLE_NAME, values, query.where, query.whereArgs)

      for (id in idsToUpdate) {
        ApplicationDependencies.getDatabaseObserver().notifyRecipientChanged(RecipientId.from(id))
      }
    }
  }

  fun setHasGroupsInCommon(recipientIds: List<RecipientId?>) {
    if (recipientIds.isEmpty()) {
      return
    }

    var query = SqlUtil.buildCollectionQuery(ID, recipientIds)
    val db = writableDatabase

    db.query(TABLE_NAME, arrayOf(ID), "${query.where} AND $GROUPS_IN_COMMON = 0", query.whereArgs, null, null, null).use { cursor ->
      val idsToUpdate: MutableList<Long> = ArrayList(cursor.count)

      while (cursor.moveToNext()) {
        idsToUpdate.add(cursor.requireLong(ID))
      }

      if (Util.hasItems(idsToUpdate)) {
        query = SqlUtil.buildCollectionQuery(ID, idsToUpdate)
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

    val query = "$ID = ? AND ($GROUP_TYPE IN (?, ?) OR $REGISTERED = ?)"
    val args = SqlUtil.buildArgs(recipientId, GroupType.SIGNAL_V1.id, GroupType.SIGNAL_V2.id, RegisteredState.REGISTERED.id)
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
        Optional.absent()
      }
    }
  }

  private fun getOrInsertByColumn(column: String, value: String): GetOrInsertResult {
    if (TextUtils.isEmpty(value)) {
      throw AssertionError("$column cannot be empty.")
    }

    var existing = getByColumn(column, value)

    if (existing.isPresent) {
      return GetOrInsertResult(existing.get(), false)
    } else {
      val values = ContentValues().apply {
        put(column, value)
        put(AVATAR_COLOR, AvatarColor.random().serialize())
      }

      val id = writableDatabase.insert(TABLE_NAME, null, values)
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
  private fun merge(byAci: RecipientId, byE164: RecipientId): RecipientId {
    ensureInTransaction()
    val db = writableDatabase
    val aciRecord = getRecord(byAci)
    val e164Record = getRecord(byE164)

    // Identities
    ApplicationDependencies.getProtocolStore().aci().identities().delete(e164Record.e164!!)

    // Group Receipts
    val groupReceiptValues = ContentValues()
    groupReceiptValues.put(GroupReceiptDatabase.RECIPIENT_ID, byAci.serialize())
    db.update(GroupReceiptDatabase.TABLE_NAME, groupReceiptValues, GroupReceiptDatabase.RECIPIENT_ID + " = ?", SqlUtil.buildArgs(byE164))

    // Groups
    val groupDatabase = groups
    for (group in groupDatabase.getGroupsContainingMember(byE164, false, true)) {
      val newMembers = LinkedHashSet(group.members).apply {
        remove(byE164)
        add(byAci)
      }

      val groupValues = ContentValues().apply {
        put(GroupDatabase.MEMBERS, RecipientId.toSerializedList(newMembers))
      }
      db.update(GroupDatabase.TABLE_NAME, groupValues, GroupDatabase.RECIPIENT_ID + " = ?", SqlUtil.buildArgs(group.recipientId))

      if (group.isV2Group) {
        groupDatabase.removeUnmigratedV1Members(group.id.requireV2(), listOf(byE164))
      }
    }

    // Threads
    val threadMerge = threads.merge(byAci, byE164)

    // SMS Messages
    val smsValues = ContentValues().apply {
      put(SmsDatabase.RECIPIENT_ID, byAci.serialize())
    }
    db.update(SmsDatabase.TABLE_NAME, smsValues, SmsDatabase.RECIPIENT_ID + " = ?", SqlUtil.buildArgs(byE164))

    if (threadMerge.neededMerge) {
      val values = ContentValues().apply {
        put(SmsDatabase.THREAD_ID, threadMerge.threadId)
      }
      db.update(SmsDatabase.TABLE_NAME, values, SmsDatabase.THREAD_ID + " = ?", SqlUtil.buildArgs(threadMerge.previousThreadId))
    }

    // MMS Messages
    val mmsValues = ContentValues().apply {
      put(MmsDatabase.RECIPIENT_ID, byAci.serialize())
    }
    db.update(MmsDatabase.TABLE_NAME, mmsValues, MmsDatabase.RECIPIENT_ID + " = ?", SqlUtil.buildArgs(byE164))

    if (threadMerge.neededMerge) {
      val values = ContentValues()
      values.put(MmsDatabase.THREAD_ID, threadMerge.threadId)
      db.update(MmsDatabase.TABLE_NAME, values, MmsDatabase.THREAD_ID + " = ?", SqlUtil.buildArgs(threadMerge.previousThreadId))
    }

    // Sessions
    val sessionDatabase = sessions
    val hasE164Session = sessionDatabase.getAllFor(e164Record.e164).size > 0
    val hasAciSession = sessionDatabase.getAllFor(aciRecord.aci.toString()).size > 0

    if (hasE164Session && hasAciSession) {
      Log.w(TAG, "Had a session for both users. Deleting the E164.", true)
      sessionDatabase.deleteAllFor(e164Record.e164)
    } else if (hasE164Session && !hasAciSession) {
      Log.w(TAG, "Had a session for E164, but not ACI. Re-assigning to the ACI.", true)
      val values = ContentValues().apply {
        put(SessionDatabase.ADDRESS, aciRecord.aci.toString())
      }
      db.update(SessionDatabase.TABLE_NAME, values, SessionDatabase.ADDRESS + " = ?", SqlUtil.buildArgs(e164Record.e164))
    } else if (!hasE164Session && hasAciSession) {
      Log.w(TAG, "Had a session for ACI, but not E164. No action necessary.", true)
    } else {
      Log.w(TAG, "Had no sessions. No action necessary.", true)
    }

    // MSL
    messageLog.remapRecipient(byE164, byAci)

    // Mentions
    val mentionRecipientValues = ContentValues().apply {
      put(MentionDatabase.RECIPIENT_ID, byAci.serialize())
    }
    db.update(MentionDatabase.TABLE_NAME, mentionRecipientValues, MentionDatabase.RECIPIENT_ID + " = ?", SqlUtil.buildArgs(byE164))

    if (threadMerge.neededMerge) {
      val mentionThreadValues = ContentValues().apply {
        put(MentionDatabase.THREAD_ID, threadMerge.threadId)
      }
      db.update(MentionDatabase.TABLE_NAME, mentionThreadValues, MentionDatabase.THREAD_ID + " = ?", SqlUtil.buildArgs(threadMerge.previousThreadId))
    }
    threads.setLastScrolled(threadMerge.threadId, 0)
    threads.update(threadMerge.threadId, false, false)

    // Reactions
    reactions.remapRecipient(byE164, byAci)

    // Notification Profiles
    notificationProfiles.remapRecipient(byE164, byAci)

    // Recipient
    Log.w(TAG, "Deleting recipient $byE164", true)
    db.delete(TABLE_NAME, ID_WHERE, SqlUtil.buildArgs(byE164))
    RemappedRecords.getInstance().addRecipient(byE164, byAci)

    val uuidValues = ContentValues().apply {
      put(PHONE, e164Record.e164)
      put(BLOCKED, e164Record.isBlocked || aciRecord.isBlocked)
      put(MESSAGE_RINGTONE, Optional.fromNullable(aciRecord.messageRingtone).or(Optional.fromNullable(e164Record.messageRingtone)).transform { obj: Uri? -> obj.toString() }.orNull())
      put(MESSAGE_VIBRATE, if (aciRecord.messageVibrateState != VibrateState.DEFAULT) aciRecord.messageVibrateState.id else e164Record.messageVibrateState.id)
      put(CALL_RINGTONE, Optional.fromNullable(aciRecord.callRingtone).or(Optional.fromNullable(e164Record.callRingtone)).transform { obj: Uri? -> obj.toString() }.orNull())
      put(CALL_VIBRATE, if (aciRecord.callVibrateState != VibrateState.DEFAULT) aciRecord.callVibrateState.id else e164Record.callVibrateState.id)
      put(NOTIFICATION_CHANNEL, aciRecord.notificationChannel ?: e164Record.notificationChannel)
      put(MUTE_UNTIL, if (aciRecord.muteUntil > 0) aciRecord.muteUntil else e164Record.muteUntil)
      put(CHAT_COLORS, Optional.fromNullable(aciRecord.chatColors).or(Optional.fromNullable(e164Record.chatColors)).transform { colors: ChatColors? -> colors!!.serialize().toByteArray() }.orNull())
      put(AVATAR_COLOR, aciRecord.avatarColor.serialize())
      put(CUSTOM_CHAT_COLORS_ID, Optional.fromNullable(aciRecord.chatColors).or(Optional.fromNullable(e164Record.chatColors)).transform { colors: ChatColors? -> colors!!.id.longValue }.orNull())
      put(SEEN_INVITE_REMINDER, e164Record.insightsBannerTier.id)
      put(DEFAULT_SUBSCRIPTION_ID, e164Record.getDefaultSubscriptionId().or(-1))
      put(MESSAGE_EXPIRATION_TIME, if (aciRecord.expireMessages > 0) aciRecord.expireMessages else e164Record.expireMessages)
      put(REGISTERED, RegisteredState.REGISTERED.id)
      put(SYSTEM_GIVEN_NAME, e164Record.systemProfileName.givenName)
      put(SYSTEM_FAMILY_NAME, e164Record.systemProfileName.familyName)
      put(SYSTEM_JOINED_NAME, e164Record.systemProfileName.toString())
      put(SYSTEM_PHOTO_URI, e164Record.systemContactPhotoUri)
      put(SYSTEM_PHONE_LABEL, e164Record.systemPhoneLabel)
      put(SYSTEM_CONTACT_URI, e164Record.systemContactUri)
      put(PROFILE_SHARING, aciRecord.profileSharing || e164Record.profileSharing)
      put(CAPABILITIES, max(aciRecord.rawCapabilities, e164Record.rawCapabilities))
      put(MENTION_SETTING, if (aciRecord.mentionSetting != MentionSetting.ALWAYS_NOTIFY) aciRecord.mentionSetting.id else e164Record.mentionSetting.id)
    }

    if (aciRecord.profileKey != null) {
      updateProfileValuesForMerge(uuidValues, aciRecord)
    } else if (e164Record.profileKey != null) {
      updateProfileValuesForMerge(uuidValues, e164Record)
    }

    db.update(TABLE_NAME, uuidValues, ID_WHERE, SqlUtil.buildArgs(byAci))
    return byAci
  }

  private fun ensureInTransaction() {
    check(writableDatabase.inTransaction()) { "Must be in a transaction!" }
  }

  private fun buildContentValuesForNewUser(e164: String?, aci: ACI?): ContentValues {
    val values = ContentValues()
    values.put(PHONE, e164)
    if (aci != null) {
      values.put(ACI_COLUMN, aci.toString().toLowerCase())
      values.put(REGISTERED, RegisteredState.REGISTERED.id)
      values.put(STORAGE_SERVICE_ID, Base64.encodeBytes(StorageSyncHelper.generateKey()))
      values.put(AVATAR_COLOR, AvatarColor.random().serialize())
    }
    return values
  }

  private fun getValuesForStorageContact(contact: SignalContactRecord, isInsert: Boolean): ContentValues {
    return ContentValues().apply {
      val profileName = ProfileName.fromParts(contact.givenName.orNull(), contact.familyName.orNull())
      val username = contact.username.orNull()

      if (contact.address.hasValidAci()) {
        put(ACI_COLUMN, contact.address.aci.toString())
      }

      put(PHONE, contact.address.number.orNull())
      put(PROFILE_GIVEN_NAME, profileName.givenName)
      put(PROFILE_FAMILY_NAME, profileName.familyName)
      put(PROFILE_JOINED_NAME, profileName.toString())
      put(PROFILE_KEY, contact.profileKey.transform { source -> Base64.encodeBytes(source) }.orNull())
      put(USERNAME, if (TextUtils.isEmpty(username)) null else username)
      put(PROFILE_SHARING, if (contact.isProfileSharingEnabled) "1" else "0")
      put(BLOCKED, if (contact.isBlocked) "1" else "0")
      put(MUTE_UNTIL, contact.muteUntil)
      put(STORAGE_SERVICE_ID, Base64.encodeBytes(contact.id.raw))

      if (contact.hasUnknownFields()) {
        put(STORAGE_PROTO, Base64.encodeBytes(Objects.requireNonNull(contact.serializeUnknownFields())))
      } else {
        putNull(STORAGE_PROTO)
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

  fun getRecord(context: Context, cursor: Cursor): RecipientRecord {
    return getRecord(context, cursor, ID)
  }

  fun getRecord(context: Context, cursor: Cursor, idColumnName: String): RecipientRecord {
    val profileKeyString = cursor.requireString(PROFILE_KEY)
    val profileKeyCredentialString = cursor.requireString(PROFILE_KEY_CREDENTIAL)
    var profileKey: ByteArray? = null
    var profileKeyCredential: ProfileKeyCredential? = null

    if (profileKeyString != null) {
      try {
        profileKey = Base64.decode(profileKeyString)
      } catch (e: IOException) {
        Log.w(TAG, e)
      }

      if (profileKeyCredentialString != null) {
        try {
          val columnDataBytes = Base64.decode(profileKeyCredentialString)
          val columnData = ProfileKeyCredentialColumnData.parseFrom(columnDataBytes)
          if (Arrays.equals(columnData.profileKey.toByteArray(), profileKey)) {
            profileKeyCredential = ProfileKeyCredential(columnData.profileKeyCredential.toByteArray())
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
    val capabilities = cursor.requireLong(CAPABILITIES)

    return RecipientRecord(
      id = recipientId,
      aci = ACI.parseOrNull(cursor.requireString(ACI_COLUMN)),
      pni = PNI.parseOrNull(cursor.requireString(PNI_COLUMN)),
      username = cursor.requireString(USERNAME),
      e164 = cursor.requireString(PHONE),
      email = cursor.requireString(EMAIL),
      groupId = GroupId.parseNullableOrThrow(cursor.requireString(GROUP_ID)),
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
      profileKeyCredential = profileKeyCredential,
      systemProfileName = ProfileName.fromParts(cursor.requireString(SYSTEM_GIVEN_NAME), cursor.requireString(SYSTEM_FAMILY_NAME)),
      systemDisplayName = cursor.requireString(SYSTEM_JOINED_NAME),
      systemContactPhotoUri = cursor.requireString(SYSTEM_PHOTO_URI),
      systemPhoneLabel = cursor.requireString(SYSTEM_PHONE_LABEL),
      systemContactUri = cursor.requireString(SYSTEM_CONTACT_URI),
      signalProfileName = ProfileName.fromParts(cursor.requireString(PROFILE_GIVEN_NAME), cursor.requireString(PROFILE_FAMILY_NAME)),
      signalProfileAvatar = cursor.requireString(SIGNAL_PROFILE_AVATAR),
      hasProfileImage = AvatarHelper.hasAvatar(context, recipientId),
      profileSharing = cursor.requireBoolean(PROFILE_SHARING),
      lastProfileFetch = cursor.requireLong(LAST_PROFILE_FETCH),
      notificationChannel = cursor.requireString(NOTIFICATION_CHANNEL),
      unidentifiedAccessMode = UnidentifiedAccessMode.fromMode(cursor.requireInt(UNIDENTIFIED_ACCESS_MODE)),
      forceSmsSelection = cursor.requireBoolean(FORCE_SMS_SELECTION),
      rawCapabilities = capabilities,
      groupsV2Capability = Recipient.Capability.deserialize(Bitmask.read(capabilities, Capabilities.GROUPS_V2, Capabilities.BIT_LENGTH).toInt()),
      groupsV1MigrationCapability = Recipient.Capability.deserialize(Bitmask.read(capabilities, Capabilities.GROUPS_V1_MIGRATION, Capabilities.BIT_LENGTH).toInt()),
      senderKeyCapability = Recipient.Capability.deserialize(Bitmask.read(capabilities, Capabilities.SENDER_KEY, Capabilities.BIT_LENGTH).toInt()),
      announcementGroupCapability = Recipient.Capability.deserialize(Bitmask.read(capabilities, Capabilities.ANNOUNCEMENT_GROUPS, Capabilities.BIT_LENGTH).toInt()),
      changeNumberCapability = Recipient.Capability.deserialize(Bitmask.read(capabilities, Capabilities.CHANGE_NUMBER, Capabilities.BIT_LENGTH).toInt()),
      insightsBannerTier = InsightsBannerTier.fromId(cursor.requireInt(SEEN_INVITE_REMINDER)),
      storageId = Base64.decodeNullableOrThrow(cursor.requireString(STORAGE_SERVICE_ID)),
      mentionSetting = MentionSetting.fromId(cursor.requireInt(MENTION_SETTING)),
      wallpaper = chatWallpaper,
      chatColors = chatColors,
      avatarColor = AvatarColor.deserialize(cursor.requireString(AVATAR_COLOR)),
      about = cursor.requireString(ABOUT),
      aboutEmoji = cursor.requireString(ABOUT_EMOJI),
      syncExtras = getSyncExtras(cursor),
      extras = getExtras(cursor),
      hasGroupsInCommon = cursor.requireBoolean(GROUPS_IN_COMMON),
      badges = parseBadgeList(cursor.requireBlob(BADGES))
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
    val storageProtoRaw = cursor.optionalString(STORAGE_PROTO).orNull()
    val storageProto = if (storageProtoRaw != null) Base64.decodeOrThrow(storageProtoRaw) else null
    val archived = cursor.optionalBoolean(ThreadDatabase.ARCHIVED).or(false)
    val forcedUnread = cursor.optionalInt(ThreadDatabase.READ).transform { status: Int -> status == ThreadDatabase.ReadStatus.FORCED_UNREAD.serialize() }.or(false)
    val groupMasterKey = cursor.optionalBlob(GroupDatabase.V2_MASTER_KEY).transform { GroupUtil.requireMasterKey(it) }.orNull()
    val identityKey = cursor.optionalString(IDENTITY_KEY).transform { Base64.decodeOrThrow(it) }.orNull()
    val identityStatus = cursor.optionalInt(IDENTITY_STATUS).transform { VerifiedStatus.forState(it) }.or(VerifiedStatus.DEFAULT)

    return RecipientRecord.SyncExtras(storageProto, groupMasterKey, identityKey, identityStatus, archived, forcedUnread)
  }

  private fun getExtras(cursor: Cursor): Recipient.Extras? {
    return Recipient.Extras.from(getRecipientExtras(cursor))
  }

  private fun getRecipientExtras(cursor: Cursor): RecipientExtras? {
    return cursor.optionalBlob(EXTRAS).transform { b: ByteArray? ->
      try {
        RecipientExtras.parseFrom(b)
      } catch (e: InvalidProtocolBufferException) {
        Log.w(TAG, e)
        throw AssertionError(e)
      }
    }.orNull()
  }

  /**
   * Builds a case-insensitive GLOB pattern for fuzzy text queries. Works with all unicode
   * characters.
   *
   * Ex:
   * cat -> [cC][aA][tT]
   */
  private fun buildCaseInsensitiveGlobPattern(query: String): String {
    if (TextUtils.isEmpty(query)) {
      return "*"
    }

    val pattern = StringBuilder()
    var i = 0
    val len = query.codePointCount(0, query.length)
    while (i < len) {
      val point = StringUtil.codePointToString(query.codePointAt(i))
      pattern.append("[")
      pattern.append(point.toLowerCase())
      pattern.append(point.toUpperCase())
      pattern.append(getAccentuatedCharRegex(point.toLowerCase()))
      pattern.append("]")
      i++
    }

    return "*$pattern*"
  }

  private fun getAccentuatedCharRegex(query: String): String {
    return when (query) {
      "a" -> "À-Åà-åĀ-ąǍǎǞ-ǡǺ-ǻȀ-ȃȦȧȺɐ-ɒḀḁẚẠ-ặ"
      "b" -> "ßƀ-ƅɃɓḂ-ḇ"
      "c" -> "çÇĆ-čƆ-ƈȻȼɔḈḉ"
      "d" -> "ÐðĎ-đƉ-ƍȡɖɗḊ-ḓ"
      "e" -> "È-Ëè-ëĒ-ěƎ-ƐǝȄ-ȇȨȩɆɇɘ-ɞḔ-ḝẸ-ệ"
      "f" -> "ƑƒḞḟ"
      "g" -> "Ĝ-ģƓǤ-ǧǴǵḠḡ"
      "h" -> "Ĥ-ħƕǶȞȟḢ-ḫẖ"
      "i" -> "Ì-Ïì-ïĨ-ıƖƗǏǐȈ-ȋɨɪḬ-ḯỈ-ị"
      "j" -> "ĴĵǰȷɈɉɟ"
      "k" -> "Ķ-ĸƘƙǨǩḰ-ḵ"
      "l" -> "Ĺ-łƚȴȽɫ-ɭḶ-ḽ"
      "m" -> "Ɯɯ-ɱḾ-ṃ"
      "n" -> "ÑñŃ-ŋƝƞǸǹȠȵɲ-ɴṄ-ṋ"
      "o" -> "Ò-ÖØò-öøŌ-őƟ-ơǑǒǪ-ǭǾǿȌ-ȏȪ-ȱṌ-ṓỌ-ợ"
      "p" -> "ƤƥṔ-ṗ"
      "q" -> ""
      "r" -> "Ŕ-řƦȐ-ȓɌɍṘ-ṟ"
      "s" -> "Ś-šƧƨȘșȿṠ-ṩ"
      "t" -> "Ţ-ŧƫ-ƮȚțȾṪ-ṱẗ"
      "u" -> "Ù-Üù-üŨ-ųƯ-ƱǓ-ǜȔ-ȗɄṲ-ṻỤ-ự"
      "v" -> "ƲɅṼ-ṿ"
      "w" -> "ŴŵẀ-ẉẘ"
      "x" -> "Ẋ-ẍ"
      "y" -> "ÝýÿŶ-ŸƔƳƴȲȳɎɏẎẏỲ-ỹỾỿẙ"
      "z" -> "Ź-žƵƶɀẐ-ẕ"
      "α" -> "\u0386\u0391\u03AC\u03B1\u1F00-\u1F0F\u1F70\u1F71\u1F80-\u1F8F\u1FB0-\u1FB4\u1FB6-\u1FBC"
      "ε" -> "\u0388\u0395\u03AD\u03B5\u1F10-\u1F15\u1F18-\u1F1D\u1F72\u1F73\u1FC8\u1FC9"
      "η" -> "\u0389\u0397\u03AE\u03B7\u1F20-\u1F2F\u1F74\u1F75\u1F90-\u1F9F\u1F20-\u1F2F\u1F74\u1F75\u1F90-\u1F9F\u1fc2\u1fc3\u1fc4\u1fc6\u1FC7\u1FCA\u1FCB\u1FCC"
      "ι" -> "\u038A\u0390\u0399\u03AA\u03AF\u03B9\u03CA\u1F30-\u1F3F\u1F76\u1F77\u1FD0-\u1FD3\u1FD6-\u1FDB"
      "ο" -> "\u038C\u039F\u03BF\u03CC\u1F40-\u1F45\u1F48-\u1F4D\u1F78\u1F79\u1FF8\u1FF9"
      "σ" -> "\u03A3\u03C2\u03C3"
      "ς" -> "\u03A3\u03C2\u03C3"
      "υ" -> "\u038E\u03A5\u03AB\u03C5\u03CB\u03CD\u1F50-\u1F57\u1F59\u1F5B\u1F5D\u1F5F\u1F7A\u1F7B\u1FE0-\u1FE3\u1FE6-\u1FEB"
      "ω" -> "\u038F\u03A9\u03C9\u03CE\u1F60-\u1F6F\u1F7C\u1F7D\u1FA0-\u1FAF\u1FF2-\u1FF4\u1FF6\u1FF7\u1FFA-\u1FFC"
      else -> ""
    }
  }

  private fun updateProfileValuesForMerge(values: ContentValues, record: RecipientRecord) {
    values.apply {
      put(PROFILE_KEY, if (record.profileKey != null) Base64.encodeBytes(record.profileKey) else null)
      putNull(PROFILE_KEY_CREDENTIAL)
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
      aci = this.aci,
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
      val query = "$SYSTEM_INFO_PENDING = ?"
      val args = arrayOf("1")
      val values = ContentValues(5).apply {
        put(SYSTEM_INFO_PENDING, 0)
        put(SYSTEM_GIVEN_NAME, null as String?)
        put(SYSTEM_FAMILY_NAME, null as String?)
        put(SYSTEM_JOINED_NAME, null as String?)
        put(SYSTEM_PHOTO_URI, null as String?)
        put(SYSTEM_PHONE_LABEL, null as String?)
        put(SYSTEM_CONTACT_URI, null as String?)
      }

      database.update(TABLE_NAME, values, query, args)
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
    const val GROUPS_V2 = 0
    const val GROUPS_V1_MIGRATION = 1
    const val SENDER_KEY = 2
    const val ANNOUNCEMENT_GROUPS = 3
    const val CHANGE_NUMBER = 4
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
    NONE(0), MMS(1), SIGNAL_V1(2), SIGNAL_V2(3);

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
    data class MatchAndUpdateAci(val id: RecipientId, val aci: ACI, val bundle: LogBundle) : RecipientFetch(bundle)

    /**
     * We found a matching recipient and can insert an ACI as a *new user*.
     */
    data class MatchAndInsertAci(val id: RecipientId, val aci: ACI, val bundle: LogBundle) : RecipientFetch(bundle)

    /**
     * The ACI maps to ACI-only recipient, and the E164 maps to a different E164-only recipient. We need to merge the two together.
     */
    data class MatchAndMerge(val aciId: RecipientId, val e164Id: RecipientId, val changedNumber: RecipientId?, val bundle: LogBundle) : RecipientFetch(bundle)

    /**
     * We don't have a matching recipient, so we need to insert one.
     */
    data class Insert(val aci: ACI?, val e164: String?, val bundle: LogBundle) : RecipientFetch(bundle)

    /**
     * We need to create a new recipient and give it the E164 of an existing recipient.
     */
    data class InsertAndReassignE164(val aci: ACI?, val e164: String?, val e164Id: RecipientId, val bundle: LogBundle) : RecipientFetch(bundle)
  }

  /**
   * Simple class for [fetchRecipient] to pass back info that can be logged.
   */
  private data class LogBundle(
    val label: String,
    val aci: ACI? = null,
    val e164: String? = null,
    val byAci: RecipientLogDetails? = null,
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
    val aci: ACI? = null,
    val e164: String? = null
  )
}
