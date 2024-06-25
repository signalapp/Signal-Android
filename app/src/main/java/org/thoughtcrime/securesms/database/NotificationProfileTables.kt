@file:Suppress("ktlint:standard:filename")

package org.thoughtcrime.securesms.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import org.signal.core.util.SqlUtil
import org.signal.core.util.requireBoolean
import org.signal.core.util.requireInt
import org.signal.core.util.requireLong
import org.signal.core.util.requireString
import org.signal.core.util.toInt
import org.thoughtcrime.securesms.conversation.colors.AvatarColor
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfile
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfileSchedule
import org.thoughtcrime.securesms.recipients.RecipientId
import java.time.DayOfWeek

/**
 * Database for maintaining Notification Profiles, Notification Profile Schedules, and Notification Profile allowed memebers.
 */
class NotificationProfileDatabase(context: Context, databaseHelper: SignalDatabase) : DatabaseTable(context, databaseHelper), RecipientIdDatabaseReference {

  companion object {
    @JvmField
    val CREATE_TABLE: Array<String> = arrayOf(NotificationProfileTable.CREATE_TABLE, NotificationProfileScheduleTable.CREATE_TABLE, NotificationProfileAllowedMembersTable.CREATE_TABLE)

    @JvmField
    val CREATE_INDEXES: Array<String> = arrayOf(NotificationProfileScheduleTable.CREATE_INDEX, NotificationProfileAllowedMembersTable.CREATE_INDEX)
  }

  private object NotificationProfileTable {
    const val TABLE_NAME = "notification_profile"

    const val ID = "_id"
    const val NAME = "name"
    const val EMOJI = "emoji"
    const val COLOR = "color"
    const val CREATED_AT = "created_at"
    const val ALLOW_ALL_CALLS = "allow_all_calls"
    const val ALLOW_ALL_MENTIONS = "allow_all_mentions"

    val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY AUTOINCREMENT,
        $NAME TEXT NOT NULL UNIQUE,
        $EMOJI TEXT NOT NULL,
        $COLOR TEXT NOT NULL,
        $CREATED_AT INTEGER NOT NULL,
        $ALLOW_ALL_CALLS INTEGER NOT NULL DEFAULT 0,
        $ALLOW_ALL_MENTIONS INTEGER NOT NULL DEFAULT 0
      )
    """
  }

  private object NotificationProfileScheduleTable {
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

  private object NotificationProfileAllowedMembersTable {
    const val TABLE_NAME = "notification_profile_allowed_members"

    const val ID = "_id"
    const val NOTIFICATION_PROFILE_ID = "notification_profile_id"
    const val RECIPIENT_ID = "recipient_id"

    val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY AUTOINCREMENT,
        $NOTIFICATION_PROFILE_ID INTEGER NOT NULL REFERENCES ${NotificationProfileTable.TABLE_NAME} (${NotificationProfileTable.ID}) ON DELETE CASCADE,
        $RECIPIENT_ID INTEGER NOT NULL,
        UNIQUE($NOTIFICATION_PROFILE_ID, $RECIPIENT_ID) ON CONFLICT REPLACE
      )
    """

    const val CREATE_INDEX = "CREATE INDEX notification_profile_allowed_members_profile_index ON $TABLE_NAME ($NOTIFICATION_PROFILE_ID)"
  }

  fun createProfile(name: String, emoji: String, color: AvatarColor, createdAt: Long): NotificationProfileChangeResult {
    val db = writableDatabase

    db.beginTransaction()
    try {
      val profileValues = ContentValues().apply {
        put(NotificationProfileTable.NAME, name)
        put(NotificationProfileTable.EMOJI, emoji)
        put(NotificationProfileTable.COLOR, color.serialize())
        put(NotificationProfileTable.CREATED_AT, createdAt)
        put(NotificationProfileTable.ALLOW_ALL_CALLS, 1)
      }

      val profileId = db.insert(NotificationProfileTable.TABLE_NAME, null, profileValues)
      if (profileId < 0) {
        return NotificationProfileChangeResult.DuplicateName
      }

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
          allowAllCalls = true
        )
      )
    } finally {
      db.endTransaction()
      AppDependencies.databaseObserver.notifyNotificationProfileObservers()
    }
  }

  fun updateProfile(profileId: Long, name: String, emoji: String): NotificationProfileChangeResult {
    val profileValues = ContentValues().apply {
      put(NotificationProfileTable.NAME, name)
      put(NotificationProfileTable.EMOJI, emoji)
    }

    val updateQuery = SqlUtil.buildTrueUpdateQuery(ID_WHERE, SqlUtil.buildArgs(profileId), profileValues)

    return try {
      val count = writableDatabase.update(NotificationProfileTable.TABLE_NAME, profileValues, updateQuery.where, updateQuery.whereArgs)
      if (count > 0) {
        AppDependencies.databaseObserver.notifyNotificationProfileObservers()
      }

      NotificationProfileChangeResult.Success(getProfile(profileId)!!)
    } catch (e: SQLiteConstraintException) {
      NotificationProfileChangeResult.DuplicateName
    }
  }

  fun updateProfile(profile: NotificationProfile): NotificationProfileChangeResult {
    val db = writableDatabase

    db.beginTransaction()
    try {
      val profileValues = ContentValues().apply {
        put(NotificationProfileTable.NAME, profile.name)
        put(NotificationProfileTable.EMOJI, profile.emoji)
        put(NotificationProfileTable.ALLOW_ALL_CALLS, profile.allowAllCalls.toInt())
        put(NotificationProfileTable.ALLOW_ALL_MENTIONS, profile.allowAllMentions.toInt())
      }

      val updateQuery = SqlUtil.buildTrueUpdateQuery(ID_WHERE, SqlUtil.buildArgs(profile.id), profileValues)

      try {
        db.update(NotificationProfileTable.TABLE_NAME, profileValues, updateQuery.where, updateQuery.whereArgs)
      } catch (e: SQLiteConstraintException) {
        return NotificationProfileChangeResult.DuplicateName
      }

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

  fun getProfiles(): List<NotificationProfile> {
    val profiles: MutableList<NotificationProfile> = mutableListOf()

    readableDatabase.query(NotificationProfileTable.TABLE_NAME, null, null, null, null, null, null).use { cursor ->
      while (cursor.moveToNext()) {
        profiles += getProfile(cursor)
      }
    }

    return profiles
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

  fun deleteProfile(profileId: Long) {
    writableDatabase.delete(NotificationProfileTable.TABLE_NAME, ID_WHERE, SqlUtil.buildArgs(profileId))
    AppDependencies.databaseObserver.notifyNotificationProfileObservers()
  }

  override fun remapRecipient(oldId: RecipientId, newId: RecipientId) {
    val query = "${NotificationProfileAllowedMembersTable.RECIPIENT_ID} = ?"
    val args = SqlUtil.buildArgs(oldId)
    val values = ContentValues().apply {
      put(NotificationProfileAllowedMembersTable.RECIPIENT_ID, newId.serialize())
    }

    databaseHelper.signalWritableDatabase.update(NotificationProfileAllowedMembersTable.TABLE_NAME, values, query, args)

    AppDependencies.databaseObserver.notifyNotificationProfileObservers()
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
      allowedMembers = getProfileAllowedMembers(profileId)
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

  sealed class NotificationProfileChangeResult {
    data class Success(val notificationProfile: NotificationProfile) : NotificationProfileChangeResult()
    object DuplicateName : NotificationProfileChangeResult()
  }
}

private fun Iterable<DayOfWeek>.serialize(): String {
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
