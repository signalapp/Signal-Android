package org.thoughtcrime.securesms.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import androidx.core.content.contentValuesOf
import androidx.core.net.toUri
import org.json.JSONException
import org.json.JSONObject
import org.signal.core.util.delete
import org.signal.core.util.logging.Log
import org.signal.core.util.readToList
import org.signal.core.util.requireInt
import org.signal.core.util.requireLong
import org.signal.core.util.requireNonNullString
import org.signal.core.util.requireString
import org.signal.core.util.select
import org.signal.core.util.update
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.database.model.RemoteMegaphoneRecord
import java.util.concurrent.TimeUnit

/**
 * Stores remotely configured megaphones.
 */
class RemoteMegaphoneTable(context: Context, databaseHelper: SignalDatabase) : DatabaseTable(context, databaseHelper) {

  companion object {
    private val TAG = Log.tag(RemoteMegaphoneTable::class.java)

    private const val TABLE_NAME = "remote_megaphone"
    private const val ID = "_id"
    private const val UUID = "uuid"
    private const val COUNTRIES = "countries"
    private const val PRIORITY = "priority"
    private const val MINIMUM_VERSION = "minimum_version"
    private const val DONT_SHOW_BEFORE = "dont_show_before"
    private const val DONT_SHOW_AFTER = "dont_show_after"
    private const val SHOW_FOR_DAYS = "show_for_days"
    private const val CONDITIONAL_ID = "conditional_id"
    private const val PRIMARY_ACTION_ID = "primary_action_id"
    private const val SECONDARY_ACTION_ID = "secondary_action_id"
    private const val IMAGE_URL = "image_url"
    private const val IMAGE_BLOB_URI = "image_uri"
    private const val TITLE = "title"
    private const val BODY = "body"
    private const val PRIMARY_ACTION_TEXT = "primary_action_text"
    private const val SECONDARY_ACTION_TEXT = "secondary_action_text"
    private const val SHOWN_AT = "shown_at"
    private const val FINISHED_AT = "finished_at"
    private const val PRIMARY_ACTION_DATA = "primary_action_data"
    private const val SECONDARY_ACTION_DATA = "secondary_action_data"
    private const val SNOOZED_AT = "snoozed_at"
    private const val SEEN_COUNT = "seen_count"

    val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY,
        $UUID TEXT UNIQUE NOT NULL,
        $PRIORITY INTEGER NOT NULL,
        $COUNTRIES TEXT,
        $MINIMUM_VERSION INTEGER NOT NULL,
        $DONT_SHOW_BEFORE INTEGER NOT NULL,
        $DONT_SHOW_AFTER INTEGER NOT NULL,
        $SHOW_FOR_DAYS INTEGER NOT NULL,
        $CONDITIONAL_ID TEXT,
        $PRIMARY_ACTION_ID TEXT,
        $SECONDARY_ACTION_ID TEXT,
        $IMAGE_URL TEXT,
        $IMAGE_BLOB_URI TEXT DEFAULT NULL,
        $TITLE TEXT NOT NULL,
        $BODY TEXT NOT NULL,
        $PRIMARY_ACTION_TEXT TEXT,
        $SECONDARY_ACTION_TEXT TEXT,
        $SHOWN_AT INTEGER DEFAULT 0,
        $FINISHED_AT INTEGER DEFAULT 0,
        $PRIMARY_ACTION_DATA TEXT DEFAULT NULL,
        $SECONDARY_ACTION_DATA TEXT DEFAULT NULL,
        $SNOOZED_AT INTEGER DEFAULT 0,
        $SEEN_COUNT INTEGER DEFAULT 0
      )
    """.trimIndent()

    const val VERSION_FINISHED = Int.MAX_VALUE
  }

  fun insert(record: RemoteMegaphoneRecord) {
    writableDatabase.insert(TABLE_NAME, SQLiteDatabase.CONFLICT_REPLACE, record.toContentValues())
  }

  fun update(uuid: String, priority: Long, countries: String?, title: String, body: String, primaryActionText: String?, secondaryActionText: String?) {
    writableDatabase
      .update(TABLE_NAME)
      .values(
        PRIORITY to priority,
        COUNTRIES to countries,
        TITLE to title,
        BODY to body,
        PRIMARY_ACTION_TEXT to primaryActionText,
        SECONDARY_ACTION_TEXT to secondaryActionText
      )
      .where("$UUID = ?", uuid)
      .run()
  }

  fun getAll(): List<RemoteMegaphoneRecord> {
    return readableDatabase
      .select()
      .from(TABLE_NAME)
      .run()
      .readToList { it.toRemoteMegaphoneRecord() }
  }

  fun getPotentialMegaphonesAndClearOld(now: Long): List<RemoteMegaphoneRecord> {
    val records: List<RemoteMegaphoneRecord> = readableDatabase
      .select()
      .from(TABLE_NAME)
      .where("$FINISHED_AT = ? AND $MINIMUM_VERSION <= ? AND ($DONT_SHOW_AFTER > ? AND $DONT_SHOW_BEFORE < ?)", 0, BuildConfig.CANONICAL_VERSION_CODE, now, now)
      .orderBy("$PRIORITY DESC")
      .run()
      .readToList { it.toRemoteMegaphoneRecord() }

    val oldRecords: Set<RemoteMegaphoneRecord> = records
      .filter { it.shownAt > 0 && it.showForNumberOfDays > 0 }
      .filter { it.shownAt + TimeUnit.DAYS.toMillis(it.showForNumberOfDays) < now }
      .toSet()

    for (oldRecord in oldRecords) {
      clear(oldRecord.uuid)
    }

    return records - oldRecords
  }

  fun setImageUri(uuid: String, uri: Uri?) {
    writableDatabase
      .update(TABLE_NAME)
      .values(IMAGE_BLOB_URI to uri?.toString())
      .where("$UUID = ?", uuid)
      .run()
  }

  fun markShown(uuid: String) {
    writableDatabase
      .update(TABLE_NAME)
      .values(SHOWN_AT to System.currentTimeMillis())
      .where("$UUID = ?", uuid)
      .run()
  }

  fun markFinished(uuid: String) {
    writableDatabase
      .update(TABLE_NAME)
      .values(
        IMAGE_URL to null,
        IMAGE_BLOB_URI to null,
        FINISHED_AT to System.currentTimeMillis()
      )
      .where("$UUID = ?", uuid)
      .run()
  }

  fun snooze(remote: RemoteMegaphoneRecord) {
    writableDatabase
      .update(TABLE_NAME)
      .values(
        SEEN_COUNT to remote.seenCount + 1,
        SNOOZED_AT to System.currentTimeMillis()
      )
      .where("$UUID = ?", remote.uuid)
      .run()
  }

  fun clearImageUrl(uuid: String) {
    writableDatabase
      .update(TABLE_NAME)
      .values(IMAGE_URL to null)
      .where("$UUID = ?", uuid)
      .run()
  }

  fun clear(uuid: String) {
    writableDatabase
      .update(TABLE_NAME)
      .values(
        MINIMUM_VERSION to VERSION_FINISHED,
        IMAGE_URL to null,
        IMAGE_BLOB_URI to null
      )
      .where("$UUID = ?", uuid)
      .run()
  }

  /** Only call from internal settings */
  fun debugRemoveAll() {
    writableDatabase
      .delete(TABLE_NAME)
      .run()
  }

  private fun RemoteMegaphoneRecord.toContentValues(): ContentValues {
    return contentValuesOf(
      UUID to uuid,
      PRIORITY to priority,
      COUNTRIES to countries,
      MINIMUM_VERSION to minimumVersion,
      DONT_SHOW_BEFORE to doNotShowBefore,
      DONT_SHOW_AFTER to doNotShowAfter,
      SHOW_FOR_DAYS to showForNumberOfDays,
      CONDITIONAL_ID to conditionalId,
      PRIMARY_ACTION_ID to primaryActionId?.id,
      SECONDARY_ACTION_ID to secondaryActionId?.id,
      IMAGE_URL to imageUrl,
      TITLE to title,
      BODY to body,
      PRIMARY_ACTION_TEXT to primaryActionText,
      SECONDARY_ACTION_TEXT to secondaryActionText,
      FINISHED_AT to finishedAt,
      PRIMARY_ACTION_DATA to primaryActionData?.toString(),
      SECONDARY_ACTION_DATA to secondaryActionData?.toString(),
      SNOOZED_AT to snoozedAt,
      SEEN_COUNT to seenCount
    )
  }

  private fun Cursor.toRemoteMegaphoneRecord(): RemoteMegaphoneRecord {
    return RemoteMegaphoneRecord(
      id = requireLong(ID),
      uuid = requireNonNullString(UUID),
      priority = requireLong(PRIORITY),
      countries = requireString(COUNTRIES),
      minimumVersion = requireInt(MINIMUM_VERSION),
      doNotShowBefore = requireLong(DONT_SHOW_BEFORE),
      doNotShowAfter = requireLong(DONT_SHOW_AFTER),
      showForNumberOfDays = requireLong(SHOW_FOR_DAYS),
      conditionalId = requireString(CONDITIONAL_ID),
      primaryActionId = RemoteMegaphoneRecord.ActionId.from(requireString(PRIMARY_ACTION_ID)),
      secondaryActionId = RemoteMegaphoneRecord.ActionId.from(requireString(SECONDARY_ACTION_ID)),
      imageUrl = requireString(IMAGE_URL),
      imageUri = requireString(IMAGE_BLOB_URI)?.toUri(),
      title = requireNonNullString(TITLE),
      body = requireNonNullString(BODY),
      primaryActionText = requireString(PRIMARY_ACTION_TEXT),
      secondaryActionText = requireString(SECONDARY_ACTION_TEXT),
      shownAt = requireLong(SHOWN_AT),
      finishedAt = requireLong(FINISHED_AT),
      primaryActionData = requireString(PRIMARY_ACTION_DATA).parseJsonObject(),
      secondaryActionData = requireString(SECONDARY_ACTION_DATA).parseJsonObject(),
      snoozedAt = requireLong(SNOOZED_AT),
      seenCount = requireInt(SEEN_COUNT)
    )
  }

  private fun String?.parseJsonObject(): JSONObject? {
    if (this == null) {
      return null
    }

    return try {
      JSONObject(this)
    } catch (e: JSONException) {
      Log.w(TAG, "Unable to parse data", e)
      null
    }
  }
}
