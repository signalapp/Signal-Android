package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import android.database.Cursor
import androidx.core.content.contentValuesOf
import org.signal.core.util.Base64
import org.signal.core.util.CursorUtil
import org.signal.core.util.SqlUtil
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SQLiteDatabase
import org.thoughtcrime.securesms.storage.StorageSyncHelper

/**
 * Performs a check and ensures that MyStory exists at the correct distribution list id and correct distribution id.
 */
@Suppress("ClassName")
object V151_MyStoryMigration : SignalDatabaseMigration {

  private val TAG = Log.tag(V151_MyStoryMigration::class.java)

  private const val TABLE_NAME = "distribution_list"
  private const val NAME = "name"
  private const val DISTRIBUTION_LIST_ID = "_id"
  private const val DISTRIBUTION_ID = "distribution_id"
  private const val RECIPIENT_ID = "recipient_id"
  private const val PRIVACY_MODE = "privacy_mode"
  private const val MY_STORY_DISTRIBUTION_LIST_ID = 1
  private const val MY_STORY_DISTRIBUTION_ID = "00000000-0000-0000-0000-000000000000"
  private const val MY_STORY_PRIVACY_MODE = 2

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    val result: MyStoryExistsResult = getMyStoryCursor(db).use { cursor ->
      if (cursor.moveToNext()) {
        val distributionId = CursorUtil.requireString(cursor, DISTRIBUTION_ID)
        if (distributionId != MY_STORY_DISTRIBUTION_ID) {
          Log.d(TAG, "[migrate] Invalid MyStory DistributionId: $distributionId")
          MyStoryExistsResult.REQUIRES_DISTRIBUTION_ID_UPDATE
        } else {
          Log.d(TAG, "[migrate] MyStory DistributionId matches expected value.")
          MyStoryExistsResult.MATCHES_EXPECTED_VALUE
        }
      } else {
        Log.d(TAG, "[migrate] My Story does not exist.")
        MyStoryExistsResult.DOES_NOT_EXIST
      }
    }

    when (result) {
      MyStoryExistsResult.REQUIRES_DISTRIBUTION_ID_UPDATE -> updateDistributionIdToExpectedValue(db)
      MyStoryExistsResult.MATCHES_EXPECTED_VALUE -> Unit
      MyStoryExistsResult.DOES_NOT_EXIST -> createMyStory(db)
    }
  }

  private fun updateDistributionIdToExpectedValue(db: SQLiteDatabase) {
    Log.d(TAG, "[updateDistributionIdToExpectedValue] Overwriting My Story DistributionId with expected value.")
    db.update(
      TABLE_NAME,
      contentValuesOf(DISTRIBUTION_ID to MY_STORY_DISTRIBUTION_ID),
      "$DISTRIBUTION_LIST_ID = ?",
      arrayOf(MY_STORY_DISTRIBUTION_LIST_ID.toString())
    )
  }

  private fun createMyStory(db: SQLiteDatabase) {
    Log.d(TAG, "[createMyStory] Attempting to create My Story.")

    val recipientId: Long = getMyStoryRecipientId(db) ?: createMyStoryRecipientId(db)

    db.insert(
      TABLE_NAME,
      null,
      contentValuesOf(
        DISTRIBUTION_LIST_ID to MY_STORY_DISTRIBUTION_LIST_ID,
        NAME to MY_STORY_DISTRIBUTION_ID,
        DISTRIBUTION_ID to MY_STORY_DISTRIBUTION_ID,
        RECIPIENT_ID to recipientId,
        PRIVACY_MODE to MY_STORY_PRIVACY_MODE
      )
    )
  }

  private fun createMyStoryRecipientId(db: SQLiteDatabase): Long {
    return db.insert(
      "recipient",
      null,
      contentValuesOf(
        "group_type" to 4,
        "distribution_list_id" to MY_STORY_DISTRIBUTION_LIST_ID,
        "storage_service_key" to Base64.encodeWithPadding(StorageSyncHelper.generateKey()),
        "profile_sharing" to 1
      )
    )
  }

  private fun getMyStoryRecipientId(db: SQLiteDatabase): Long? {
    return db.query(
      "recipient",
      arrayOf("_id"),
      "distribution_list_id = ?",
      SqlUtil.buildArgs(MY_STORY_DISTRIBUTION_LIST_ID),
      null,
      null,
      null
    ).use {
      if (it.moveToNext()) {
        CursorUtil.requireLong(it, "_id")
      } else {
        null
      }
    }
  }

  private fun getMyStoryCursor(db: SQLiteDatabase): Cursor {
    return db.query(
      TABLE_NAME,
      arrayOf(DISTRIBUTION_ID),
      "$DISTRIBUTION_LIST_ID = ?",
      arrayOf(MY_STORY_DISTRIBUTION_LIST_ID.toString()),
      null,
      null,
      null
    )
  }

  private enum class MyStoryExistsResult {
    REQUIRES_DISTRIBUTION_ID_UPDATE,
    MATCHES_EXPECTED_VALUE,
    DOES_NOT_EXIST
  }
}
