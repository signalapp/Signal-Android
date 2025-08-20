@file:Suppress("ktlint:standard:filename")

package org.thoughtcrime.securesms.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import androidx.core.content.contentValuesOf
import org.signal.core.util.Base64
import org.signal.core.util.SqlUtil
import org.signal.core.util.exists
import org.signal.core.util.hasUnknownFields
import org.signal.core.util.insertInto
import org.signal.core.util.logging.Log
import org.signal.core.util.readToList
import org.signal.core.util.readToMap
import org.signal.core.util.readToSingleLong
import org.signal.core.util.readToSingleObject
import org.signal.core.util.requireBoolean
import org.signal.core.util.requireInt
import org.signal.core.util.requireLong
import org.signal.core.util.requireNonNullString
import org.signal.core.util.requireString
import org.signal.core.util.select
import org.signal.core.util.toInt
import org.signal.core.util.update
import org.signal.core.util.withinTransaction
import org.thoughtcrime.securesms.conversation.colors.AvatarColor
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfile
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfileId
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfileSchedule
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.storage.StorageSyncModels
import org.thoughtcrime.securesms.storage.StorageSyncModels.toLocal
import org.thoughtcrime.securesms.util.RemoteConfig
import org.whispersystems.signalservice.api.storage.SignalNotificationProfileRecord
import org.whispersystems.signalservice.api.storage.StorageId
import org.whispersystems.signalservice.api.util.UuidUtil
import java.time.DayOfWeek

/**
 * Database for maintaining Notification Profiles, Notification Profile Schedules, and Notification Profile allowed memebers.
 */
class NotificationProfileTables(context: Context, databaseHelper: SignalDatabase) : DatabaseTable(context, databaseHelper), RecipientIdDatabaseReference {

  companion object {
    private val TAG = Log.tag(NotificationProfileTable::class)

    @JvmField
    val CREATE_TABLE: Array<String> = arrayOf(NotificationProfileTable.CREATE_TABLE, NotificationProfileScheduleTable.CREATE_TABLE, NotificationProfileAllowedMembersTable.CREATE_TABLE)

    @JvmField
    val CREATE_INDEXES: Array<String> = arrayOf(NotificationProfileScheduleTable.CREATE_INDEX, NotificationProfileAllowedMembersTable.CREATE_NOTIFICATION_PROFILE_INDEX, NotificationProfileAllowedMembersTable.CREATE_RECIPIENT_ID_INDEX)
  }

  object NotificationProfileTable {
    const val TABLE_NAME = "notification_profile"

    const val ID = "_id"
    const val NAME = "name"
    const val EMOJI = "emoji"
    const val COLOR = "color"
    const val CREATED_AT = "created_at"
    const val ALLOW_ALL_CALLS = "allow_all_calls"
    const val ALLOW_ALL_MENTIONS = "allow_all_mentions"
    const val NOTIFICATION_PROFILE_ID = "notification_profile_id"
    const val DELETED_TIMESTAMP_MS = "deleted_timestamp_ms"
    const val STORAGE_SERVICE_ID = "storage_service_id"
    const val STORAGE_SERVICE_PROTO = "storage_service_proto"

    val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY AUTOINCREMENT,
        $NAME TEXT NOT NULL,
        $EMOJI TEXT NOT NULL,
        $COLOR TEXT NOT NULL,
        $CREATED_AT INTEGER NOT NULL,
        $ALLOW_ALL_CALLS INTEGER NOT NULL DEFAULT 0,
        $ALLOW_ALL_MENTIONS INTEGER NOT NULL DEFAULT 0,
        $NOTIFICATION_PROFILE_ID TEXT DEFAULT NULL,
        $DELETED_TIMESTAMP_MS INTEGER DEFAULT 0,
        $STORAGE_SERVICE_ID TEXT DEFAULT NULL,
        $STORAGE_SERVICE_PROTO TEXT DEFAULT NULL
      )
    """
  }

  object NotificationProfileScheduleTable {
    const val TABLE_NAME = "notification_profile_schedule"

    const val ID = "_id"
    const val NOTIFICATION_PROFILE_ID = "notification_profile_id"
    const val ENABLED = "enabled"
    const val START = "start"
    const val END = "end"
    const val DAYS_ENABLED = "days_enabled"

    val DEFAULT_DAYS = listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY).serialize()

    val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY AUTOINCREMENT,
        $NOTIFICATION_PROFILE_ID INTEGER NOT NULL REFERENCES ${NotificationProfileTable.TABLE_NAME} (${NotificationProfileTable.ID}) ON DELETE CASCADE,
        $ENABLED INTEGER NOT NULL DEFAULT 0,
        $START INTEGER NOT NULL,
        $END INTEGER NOT NULL,
        $DAYS_ENABLED TEXT NOT NULL
      )
    """

    const val CREATE_INDEX = "CREATE INDEX notification_profile_schedule_profile_index ON $TABLE_NAME ($NOTIFICATION_PROFILE_ID)"
  }

  object NotificationProfileAllowedMembersTable {
    const val TABLE_NAME = "notification_profile_allowed_members"

    const val ID = "_id"
    const val NOTIFICATION_PROFILE_ID = "notification_profile_id"
    const val RECIPIENT_ID = "recipient_id"

    val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY AUTOINCREMENT,
        $NOTIFICATION_PROFILE_ID INTEGER NOT NULL REFERENCES ${NotificationProfileTable.TABLE_NAME} (${NotificationProfileTable.ID}) ON DELETE CASCADE,
        $RECIPIENT_ID INTEGER NOT NULL REFERENCES ${RecipientTable.TABLE_NAME} (${RecipientTable.ID}) ON DELETE CASCADE,
        UNIQUE($NOTIFICATION_PROFILE_ID, $RECIPIENT_ID) ON CONFLICT REPLACE
      )
    """

    const val CREATE_NOTIFICATION_PROFILE_INDEX = "CREATE INDEX notification_profile_allowed_members_profile_index ON $TABLE_NAME ($NOTIFICATION_PROFILE_ID)"
    const val CREATE_RECIPIENT_ID_INDEX = "CREATE INDEX notification_profile_allowed_members_recipient_index ON $TABLE_NAME ($RECIPIENT_ID)"
  }

  fun createProfile(name: String, emoji: String, color: AvatarColor, createdAt: Long): NotificationProfileChangeResult {
    val db = writableDatabase

    db.beginTransaction()
    try {
      if (isDuplicateName(name)) {
        return NotificationProfileChangeResult.DuplicateName
      }

      val notificationProfileId = NotificationProfileId.generate()
      val storageServiceId = StorageSyncHelper.generateKey()
      val profileValues = ContentValues().apply {
        put(NotificationProfileTable.NAME, name)
        put(NotificationProfileTable.EMOJI, emoji)
        put(NotificationProfileTable.COLOR, color.serialize())
        put(NotificationProfileTable.CREATED_AT, createdAt)
        put(NotificationProfileTable.ALLOW_ALL_CALLS, 1)
        put(NotificationProfileTable.NOTIFICATION_PROFILE_ID, notificationProfileId.serialize())
        put(NotificationProfileTable.STORAGE_SERVICE_ID, Base64.encodeWithPadding(storageServiceId))
      }

      val profileId = db.insert(NotificationProfileTable.TABLE_NAME, null, profileValues)

      val scheduleValues = ContentValues().apply {
        put(NotificationProfileScheduleTable.NOTIFICATION_PROFILE_ID, profileId)
        put(NotificationProfileScheduleTable.START, 900)
        put(NotificationProfileScheduleTable.END, 1700)
        put(NotificationProfileScheduleTable.DAYS_ENABLED, NotificationProfileScheduleTable.DEFAULT_DAYS)
      }
      db.insert(NotificationProfileScheduleTable.TABLE_NAME, null, scheduleValues)

      db.setTransactionSuccessful()

      return NotificationProfileChangeResult.Success(
        NotificationProfile(
          id = profileId,
          name = name,
          emoji = emoji,
          createdAt = createdAt,
          schedule = getProfileSchedule(profileId),
          allowAllCalls = true,
          notificationProfileId = notificationProfileId,
          storageServiceId = StorageId.forNotificationProfile(storageServiceId)
        )
      )
    } finally {
      db.endTransaction()
      AppDependencies.databaseObserver.notifyNotificationProfileObservers()
    }
  }

  fun updateProfile(profileId: Long, name: String, emoji: String): NotificationProfileChangeResult {
    if (isDuplicateName(name, profileId)) {
      return NotificationProfileChangeResult.DuplicateName
    }

    val profileValues = ContentValues().apply {
      put(NotificationProfileTable.NAME, name)
      put(NotificationProfileTable.EMOJI, emoji)
    }

    val updateQuery = SqlUtil.buildTrueUpdateQuery(ID_WHERE, SqlUtil.buildArgs(profileId), profileValues)

    val count = writableDatabase.update(NotificationProfileTable.TABLE_NAME, profileValues, updateQuery.where, updateQuery.whereArgs)
    if (count > 0) {
      AppDependencies.databaseObserver.notifyNotificationProfileObservers()
    }

    return NotificationProfileChangeResult.Success(getProfile(profileId)!!)
  }

  fun updateProfile(profile: NotificationProfile): NotificationProfileChangeResult {
    if (isDuplicateName(profile.name, profile.id)) {
      return NotificationProfileChangeResult.DuplicateName
    }

    val db = writableDatabase

    db.beginTransaction()
    try {
      val storageServiceId = profile.storageServiceId?.raw ?: StorageSyncHelper.generateKey()
      val storageServiceProto = if (profile.storageServiceProto != null) Base64.encodeWithPadding(profile.storageServiceProto) else null

      val profileValues = ContentValues().apply {
        put(NotificationProfileTable.NAME, profile.name)
        put(NotificationProfileTable.EMOJI, profile.emoji)
        put(NotificationProfileTable.ALLOW_ALL_CALLS, profile.allowAllCalls.toInt())
        put(NotificationProfileTable.ALLOW_ALL_MENTIONS, profile.allowAllMentions.toInt())
        put(NotificationProfileTable.STORAGE_SERVICE_ID, Base64.encodeWithPadding(storageServiceId))
        put(NotificationProfileTable.STORAGE_SERVICE_PROTO, storageServiceProto)
        put(NotificationProfileTable.DELETED_TIMESTAMP_MS, profile.deletedTimestampMs)
        put(NotificationProfileTable.CREATED_AT, profile.createdAt)
      }

      val updateQuery = SqlUtil.buildTrueUpdateQuery(ID_WHERE, SqlUtil.buildArgs(profile.id), profileValues)

      db.update(NotificationProfileTable.TABLE_NAME, profileValues, updateQuery.where, updateQuery.whereArgs)

      updateSchedule(profile.schedule, true)

      db.delete(NotificationProfileAllowedMembersTable.TABLE_NAME, "${NotificationProfileAllowedMembersTable.NOTIFICATION_PROFILE_ID} = ?", SqlUtil.buildArgs(profile.id))

      profile.allowedMembers.forEach { recipientId ->
        val allowedMembersValues = ContentValues().apply {
          put(NotificationProfileAllowedMembersTable.NOTIFICATION_PROFILE_ID, profile.id)
          put(NotificationProfileAllowedMembersTable.RECIPIENT_ID, recipientId.serialize())
        }
        db.insert(NotificationProfileAllowedMembersTable.TABLE_NAME, null, allowedMembersValues)
      }

      db.setTransactionSuccessful()

      return NotificationProfileChangeResult.Success(getProfile(profile.id)!!)
    } finally {
      db.endTransaction()
      AppDependencies.databaseObserver.notifyNotificationProfileObservers()
    }
  }

  fun updateSchedule(schedule: NotificationProfileSchedule, silent: Boolean = false) {
    val scheduleValues = ContentValues().apply {
      put(NotificationProfileScheduleTable.ENABLED, schedule.enabled.toInt())
      put(NotificationProfileScheduleTable.START, schedule.start)
      put(NotificationProfileScheduleTable.END, schedule.end)
      put(NotificationProfileScheduleTable.DAYS_ENABLED, schedule.daysEnabled.serialize())
    }

    val updateQuery = SqlUtil.buildTrueUpdateQuery(ID_WHERE, SqlUtil.buildArgs(schedule.id), scheduleValues)
    writableDatabase.update(NotificationProfileScheduleTable.TABLE_NAME, scheduleValues, updateQuery.where, updateQuery.whereArgs)

    if (!silent) {
      AppDependencies.databaseObserver.notifyNotificationProfileObservers()
    }
  }

  fun setAllowedRecipients(profileId: Long, recipients: Set<RecipientId>): NotificationProfile {
    val db = writableDatabase

    db.beginTransaction()
    try {
      db.delete(NotificationProfileAllowedMembersTable.TABLE_NAME, "${NotificationProfileAllowedMembersTable.NOTIFICATION_PROFILE_ID} = ?", SqlUtil.buildArgs(profileId))

      recipients.forEach { recipientId ->
        val allowedMembersValues = ContentValues().apply {
          put(NotificationProfileAllowedMembersTable.NOTIFICATION_PROFILE_ID, profileId)
          put(NotificationProfileAllowedMembersTable.RECIPIENT_ID, recipientId.serialize())
        }
        db.insert(NotificationProfileAllowedMembersTable.TABLE_NAME, null, allowedMembersValues)
      }

      db.setTransactionSuccessful()

      return getProfile(profileId)!!
    } finally {
      db.endTransaction()

      AppDependencies.databaseObserver.notifyNotificationProfileObservers()
    }
  }

  fun addAllowedRecipient(profileId: Long, recipientId: RecipientId): NotificationProfile {
    val allowedValues = ContentValues().apply {
      put(NotificationProfileAllowedMembersTable.NOTIFICATION_PROFILE_ID, profileId)
      put(NotificationProfileAllowedMembersTable.RECIPIENT_ID, recipientId.serialize())
    }
    writableDatabase.insert(NotificationProfileAllowedMembersTable.TABLE_NAME, null, allowedValues)

    AppDependencies.databaseObserver.notifyNotificationProfileObservers()
    return getProfile(profileId)!!
  }

  fun removeAllowedRecipient(profileId: Long, recipientId: RecipientId): NotificationProfile {
    writableDatabase.delete(
      NotificationProfileAllowedMembersTable.TABLE_NAME,
      "${NotificationProfileAllowedMembersTable.NOTIFICATION_PROFILE_ID} = ? AND ${NotificationProfileAllowedMembersTable.RECIPIENT_ID} = ?",
      SqlUtil.buildArgs(profileId, recipientId)
    )

    AppDependencies.databaseObserver.notifyNotificationProfileObservers()
    return getProfile(profileId)!!
  }

  /**
   * Returns all undeleted notification profiles
   */
  fun getProfiles(): List<NotificationProfile> {
    return readableDatabase
      .select()
      .from(NotificationProfileTable.TABLE_NAME)
      .where("${NotificationProfileTable.DELETED_TIMESTAMP_MS} = 0")
      .orderBy("${NotificationProfileTable.CREATED_AT} DESC")
      .run()
      .readToList { cursor -> getProfile(cursor) }
  }

  fun getProfile(profileId: Long): NotificationProfile? {
    return readableDatabase.query(NotificationProfileTable.TABLE_NAME, null, ID_WHERE, SqlUtil.buildArgs(profileId), null, null, null).use { cursor ->
      if (cursor.moveToFirst()) {
        getProfile(cursor)
      } else {
        null
      }
    }
  }

  fun getProfile(query: SqlUtil.Query): NotificationProfile? {
    return readableDatabase
      .select()
      .from(NotificationProfileTable.TABLE_NAME)
      .where(query.where, query.whereArgs)
      .run()
      .readToSingleObject { cursor -> getProfile(cursor) }
  }

  fun deleteProfile(profileId: Long) {
    writableDatabase.withinTransaction { db ->
      db.update(NotificationProfileTable.TABLE_NAME)
        .values(NotificationProfileTable.DELETED_TIMESTAMP_MS to System.currentTimeMillis())
        .where("${NotificationProfileTable.ID} = ?", profileId)
        .run()
    }

    AppDependencies.databaseObserver.notifyNotificationProfileObservers()
  }

  fun markNeedsSync(profileId: Long) {
    writableDatabase.withinTransaction {
      rotateStorageId(profileId)
    }
  }

  fun applyStorageIdUpdate(id: NotificationProfileId, storageId: StorageId) {
    applyStorageIdUpdates(hashMapOf(id to storageId))
  }

  fun applyStorageIdUpdates(storageIds: Map<NotificationProfileId, StorageId>) {
    writableDatabase.withinTransaction { db ->
      storageIds.forEach { (notificationProfileId, storageId) ->
        db.update(NotificationProfileTable.TABLE_NAME)
          .values(NotificationProfileTable.STORAGE_SERVICE_ID to Base64.encodeWithPadding(storageId.raw))
          .where("${NotificationProfileTable.NOTIFICATION_PROFILE_ID} = ?", notificationProfileId.serialize())
          .run()
      }
    }
  }

  fun insertNotificationProfileFromStorageSync(notificationProfileRecord: SignalNotificationProfileRecord) {
    val profile = notificationProfileRecord.proto
    writableDatabase.withinTransaction { db ->
      val storageServiceProto = if (notificationProfileRecord.proto.hasUnknownFields()) Base64.encodeWithPadding(notificationProfileRecord.serializedUnknowns!!) else null

      val id = db.insertInto(NotificationProfileTable.TABLE_NAME)
        .values(
          contentValuesOf(
            NotificationProfileTable.NAME to profile.name,
            NotificationProfileTable.EMOJI to profile.emoji.orEmpty(),
            NotificationProfileTable.COLOR to (AvatarColor.fromColor(profile.color)?.serialize() ?: NotificationProfile.DEFAULT_NOTIFICATION_PROFILE_COLOR.serialize()),
            NotificationProfileTable.CREATED_AT to profile.createdAtMs,
            NotificationProfileTable.ALLOW_ALL_CALLS to profile.allowAllCalls,
            NotificationProfileTable.ALLOW_ALL_MENTIONS to profile.allowAllMentions,
            NotificationProfileTable.NOTIFICATION_PROFILE_ID to NotificationProfileId(UuidUtil.parseOrThrow(profile.id)).serialize(),
            NotificationProfileTable.DELETED_TIMESTAMP_MS to profile.deletedAtTimestampMs,
            NotificationProfileTable.STORAGE_SERVICE_ID to Base64.encodeWithPadding(notificationProfileRecord.id.raw),
            NotificationProfileTable.STORAGE_SERVICE_PROTO to storageServiceProto
          )
        )
        .run()

      db.insertInto(NotificationProfileScheduleTable.TABLE_NAME)
        .values(
          contentValuesOf(
            NotificationProfileScheduleTable.NOTIFICATION_PROFILE_ID to id,
            NotificationProfileScheduleTable.ENABLED to profile.scheduleEnabled.toInt(),
            NotificationProfileScheduleTable.START to profile.scheduleStartTime,
            NotificationProfileScheduleTable.END to profile.scheduleEndTime,
            NotificationProfileScheduleTable.DAYS_ENABLED to profile.scheduleDaysEnabled.map { it.toLocal() }.toSet().serialize()
          )
        )
        .run()

      profile.allowedMembers
        .mapNotNull { remoteRecipient -> StorageSyncModels.remoteToLocalRecipient(remoteRecipient) }
        .forEach {
          db.insertInto(NotificationProfileAllowedMembersTable.TABLE_NAME)
            .values(
              NotificationProfileAllowedMembersTable.NOTIFICATION_PROFILE_ID to id,
              NotificationProfileAllowedMembersTable.RECIPIENT_ID to it.id.serialize()
            )
            .run()
        }
    }

    AppDependencies.databaseObserver.notifyNotificationProfileObservers()
  }

  fun updateNotificationProfileFromStorageSync(notificationProfileRecord: SignalNotificationProfileRecord) {
    val profile = notificationProfileRecord.proto
    val notificationProfileId = NotificationProfileId(UuidUtil.parseOrThrow(profile.id))

    val profileId = readableDatabase
      .select(NotificationProfileTable.ID)
      .from(NotificationProfileTable.TABLE_NAME)
      .where("${NotificationProfileTable.NOTIFICATION_PROFILE_ID} = ?", notificationProfileId.serialize())
      .run()
      .readToSingleLong()

    val scheduleId = readableDatabase
      .select(NotificationProfileScheduleTable.ID)
      .from(NotificationProfileScheduleTable.TABLE_NAME)
      .where("${NotificationProfileScheduleTable.NOTIFICATION_PROFILE_ID} = ?", profileId)
      .run()
      .readToSingleLong()

    updateProfile(
      NotificationProfile(
        id = profileId,
        name = profile.name,
        emoji = profile.emoji.orEmpty(),
        color = AvatarColor.fromColor(profile.color) ?: NotificationProfile.DEFAULT_NOTIFICATION_PROFILE_COLOR,
        createdAt = profile.createdAtMs,
        allowAllCalls = profile.allowAllCalls,
        allowAllMentions = profile.allowAllMentions,
        schedule = NotificationProfileSchedule(
          scheduleId,
          profile.scheduleEnabled,
          profile.scheduleStartTime,
          profile.scheduleEndTime,
          profile.scheduleDaysEnabled.map { it.toLocal() }.toSet()
        ),
        allowedMembers = profile.allowedMembers.mapNotNull { remoteRecipient -> StorageSyncModels.remoteToLocalRecipient(remoteRecipient)?.id }.toSet(),
        notificationProfileId = notificationProfileId,
        deletedTimestampMs = profile.deletedAtTimestampMs,
        storageServiceId = StorageId.forNotificationProfile(notificationProfileRecord.id.raw),
        storageServiceProto = notificationProfileRecord.serializedUnknowns
      )
    )
  }

  fun getStorageSyncIdsMap(): Map<NotificationProfileId, StorageId> {
    return readableDatabase
      .select(NotificationProfileTable.NOTIFICATION_PROFILE_ID, NotificationProfileTable.STORAGE_SERVICE_ID)
      .from(NotificationProfileTable.TABLE_NAME)
      .where("${NotificationProfileTable.STORAGE_SERVICE_ID} IS NOT NULL")
      .run()
      .readToMap { cursor ->
        val id = NotificationProfileId.from(cursor.requireNonNullString(NotificationProfileTable.NOTIFICATION_PROFILE_ID))
        val encodedKey = cursor.requireNonNullString(NotificationProfileTable.STORAGE_SERVICE_ID)
        val key = Base64.decodeOrThrow(encodedKey)
        id to StorageId.forNotificationProfile(key)
      }
  }

  fun getStorageSyncIds(): List<StorageId> {
    return readableDatabase
      .select(NotificationProfileTable.STORAGE_SERVICE_ID)
      .from(NotificationProfileTable.TABLE_NAME)
      .where("${NotificationProfileTable.STORAGE_SERVICE_ID} IS NOT NULL")
      .run()
      .readToList { cursor ->
        val encodedKey = cursor.requireNonNullString(NotificationProfileTable.STORAGE_SERVICE_ID)
        val key = Base64.decodeOrThrow(encodedKey)
        StorageId.forNotificationProfile(key)
      }.also { Log.i(TAG, "${it.size} profiles have storage ids.") }
  }

  /**
   * Removes storageIds from notification profiles that have been deleted for [RemoteConfig.messageQueueTime].
   */
  fun removeStorageIdsFromOldDeletedProfiles(now: Long): Int {
    return writableDatabase
      .update(NotificationProfileTable.TABLE_NAME)
      .values(NotificationProfileTable.STORAGE_SERVICE_ID to null)
      .where("${NotificationProfileTable.STORAGE_SERVICE_ID} NOT NULL AND ${NotificationProfileTable.DELETED_TIMESTAMP_MS} > 0 AND ${NotificationProfileTable.DELETED_TIMESTAMP_MS} < ?", now - RemoteConfig.messageQueueTime)
      .run()
  }

  /**
   * Removes storageIds of profiles that are local only and deleted
   */
  fun removeStorageIdsFromLocalOnlyDeletedProfiles(storageIds: Collection<StorageId>): Int {
    var updated = 0

    SqlUtil.buildCollectionQuery(NotificationProfileTable.STORAGE_SERVICE_ID, storageIds.map { Base64.encodeWithPadding(it.raw) }, "${NotificationProfileTable.DELETED_TIMESTAMP_MS} > 0 AND")
      .forEach {
        updated += writableDatabase.update(
          NotificationProfileTable.TABLE_NAME,
          contentValuesOf(NotificationProfileTable.STORAGE_SERVICE_ID to null),
          it.where,
          it.whereArgs
        )
      }

    return updated
  }

  override fun remapRecipient(fromId: RecipientId, toId: RecipientId) {
    val count = writableDatabase
      .update(NotificationProfileAllowedMembersTable.TABLE_NAME)
      .values(NotificationProfileAllowedMembersTable.RECIPIENT_ID to toId.serialize())
      .where("${NotificationProfileAllowedMembersTable.RECIPIENT_ID} = ?", fromId)
      .run()
    AppDependencies.databaseObserver.notifyNotificationProfileObservers()

    Log.d(TAG, "Remapped $fromId to $toId. count: $count")
  }

  private fun getProfile(cursor: Cursor): NotificationProfile {
    val profileId: Long = cursor.requireLong(NotificationProfileTable.ID)

    return NotificationProfile(
      id = profileId,
      name = cursor.requireString(NotificationProfileTable.NAME)!!,
      emoji = cursor.requireString(NotificationProfileTable.EMOJI)!!,
      color = AvatarColor.deserialize(cursor.requireString(NotificationProfileTable.COLOR)),
      createdAt = cursor.requireLong(NotificationProfileTable.CREATED_AT),
      allowAllCalls = cursor.requireBoolean(NotificationProfileTable.ALLOW_ALL_CALLS),
      allowAllMentions = cursor.requireBoolean(NotificationProfileTable.ALLOW_ALL_MENTIONS),
      schedule = getProfileSchedule(profileId),
      allowedMembers = getProfileAllowedMembers(profileId),
      notificationProfileId = NotificationProfileId.from(cursor.requireNonNullString(NotificationProfileTable.NOTIFICATION_PROFILE_ID)),
      deletedTimestampMs = cursor.requireLong(NotificationProfileTable.DELETED_TIMESTAMP_MS),
      storageServiceId = cursor.requireString(NotificationProfileTable.STORAGE_SERVICE_ID)?.let { StorageId.forNotificationProfile(Base64.decodeNullableOrThrow(it)) },
      storageServiceProto = Base64.decodeOrNull(cursor.requireString(NotificationProfileTable.STORAGE_SERVICE_PROTO))
    )
  }

  private fun getProfileSchedule(profileId: Long): NotificationProfileSchedule {
    val query = SqlUtil.buildQuery("${NotificationProfileScheduleTable.NOTIFICATION_PROFILE_ID} = ?", profileId)

    return readableDatabase.query(NotificationProfileScheduleTable.TABLE_NAME, null, query.where, query.whereArgs, null, null, null).use { cursor ->
      if (cursor.moveToFirst()) {
        val daysEnabledString = cursor.requireString(NotificationProfileScheduleTable.DAYS_ENABLED) ?: ""
        val daysEnabled: Set<DayOfWeek> = daysEnabledString.split(",")
          .filter { it.isNotBlank() }
          .map { it.toDayOfWeek() }
          .toSet()

        NotificationProfileSchedule(
          id = cursor.requireLong(NotificationProfileScheduleTable.ID),
          enabled = cursor.requireBoolean(NotificationProfileScheduleTable.ENABLED),
          start = cursor.requireInt(NotificationProfileScheduleTable.START),
          end = cursor.requireInt(NotificationProfileScheduleTable.END),
          daysEnabled = daysEnabled
        )
      } else {
        throw AssertionError("No schedule for $profileId")
      }
    }
  }

  private fun getProfileAllowedMembers(profileId: Long): Set<RecipientId> {
    val allowed = mutableSetOf<RecipientId>()
    val query = SqlUtil.buildQuery("${NotificationProfileAllowedMembersTable.NOTIFICATION_PROFILE_ID} = ?", profileId)

    readableDatabase.query(NotificationProfileAllowedMembersTable.TABLE_NAME, null, query.where, query.whereArgs, null, null, null).use { cursor ->
      while (cursor.moveToNext()) {
        allowed += RecipientId.from(cursor.requireLong(NotificationProfileAllowedMembersTable.RECIPIENT_ID))
      }
    }

    return allowed
  }

  private fun rotateStorageId(id: Long) {
    writableDatabase
      .update(NotificationProfileTable.TABLE_NAME)
      .values(NotificationProfileTable.STORAGE_SERVICE_ID to Base64.encodeWithPadding(StorageSyncHelper.generateKey()))
      .where("${NotificationProfileTable.ID} = ?", id)
      .run()
  }

  /**
   * Checks that there is no other notification profile with the same [name]
   */
  private fun isDuplicateName(name: String, id: Long = -1): Boolean {
    return readableDatabase
      .exists(NotificationProfileTable.TABLE_NAME)
      .where("${NotificationProfileTable.NAME} = ? AND ${NotificationProfileTable.DELETED_TIMESTAMP_MS} = 0 AND ${NotificationProfileTable.ID} != ?", name, id)
      .run()
  }

  sealed class NotificationProfileChangeResult {
    data class Success(val notificationProfile: NotificationProfile) : NotificationProfileChangeResult()
    object DuplicateName : NotificationProfileChangeResult()
  }
}

fun Iterable<DayOfWeek>.serialize(): String {
  return joinToString(separator = ",", transform = { it.serialize() })
}

private fun String.toDayOfWeek(): DayOfWeek {
  return when (this) {
    "1" -> DayOfWeek.MONDAY
    "2" -> DayOfWeek.TUESDAY
    "3" -> DayOfWeek.WEDNESDAY
    "4" -> DayOfWeek.THURSDAY
    "5" -> DayOfWeek.FRIDAY
    "6" -> DayOfWeek.SATURDAY
    "7" -> DayOfWeek.SUNDAY
    else -> throw AssertionError("Value ($this) does not map to a day")
  }
}

private fun DayOfWeek.serialize(): String {
  return when (this) {
    DayOfWeek.MONDAY -> "1"
    DayOfWeek.TUESDAY -> "2"
    DayOfWeek.WEDNESDAY -> "3"
    DayOfWeek.THURSDAY -> "4"
    DayOfWeek.FRIDAY -> "5"
    DayOfWeek.SATURDAY -> "6"
    DayOfWeek.SUNDAY -> "7"
  }
}
