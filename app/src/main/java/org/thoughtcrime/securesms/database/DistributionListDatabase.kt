package org.thoughtcrime.securesms.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import androidx.core.content.contentValuesOf
import org.thoughtcrime.securesms.database.model.DistributionListId
import org.thoughtcrime.securesms.database.model.DistributionListPartialRecord
import org.thoughtcrime.securesms.database.model.DistributionListRecord
import org.thoughtcrime.securesms.database.model.StoryType
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.util.Base64
import org.thoughtcrime.securesms.util.CursorUtil
import org.thoughtcrime.securesms.util.SqlUtil
import org.whispersystems.signalservice.api.push.DistributionId
import java.util.UUID

/**
 * Stores distribution lists, which represent different sets of people you may want to share a story with.
 */
class DistributionListDatabase constructor(context: Context?, databaseHelper: SignalDatabase?) : Database(context, databaseHelper) {

  companion object {
    @JvmField
    val CREATE_TABLE: Array<String> = arrayOf(ListTable.CREATE_TABLE, MembershipTable.CREATE_TABLE)

    const val RECIPIENT_ID = ListTable.RECIPIENT_ID

    fun insertInitialDistributionListAtCreationTime(db: net.zetetic.database.sqlcipher.SQLiteDatabase) {
      val recipientId = db.insert(
        RecipientDatabase.TABLE_NAME, null,
        contentValuesOf(
          RecipientDatabase.DISTRIBUTION_LIST_ID to DistributionListId.MY_STORY_ID,
          RecipientDatabase.STORAGE_SERVICE_ID to Base64.encodeBytes(StorageSyncHelper.generateKey()),
          RecipientDatabase.PROFILE_SHARING to 1
        )
      )
      val listUUID = UUID.randomUUID().toString()
      db.insert(
        ListTable.TABLE_NAME, null,
        contentValuesOf(
          ListTable.ID to DistributionListId.MY_STORY_ID,
          ListTable.NAME to listUUID,
          ListTable.DISTRIBUTION_ID to listUUID,
          ListTable.RECIPIENT_ID to recipientId
        )
      )
    }
  }

  private object ListTable {
    const val TABLE_NAME = "distribution_list"

    const val ID = "_id"
    const val NAME = "name"
    const val DISTRIBUTION_ID = "distribution_id"
    const val RECIPIENT_ID = "recipient_id"
    const val ALLOWS_REPLIES = "allows_replies"

    const val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY AUTOINCREMENT,
        $NAME TEXT UNIQUE NOT NULL,
        $DISTRIBUTION_ID TEXT UNIQUE NOT NULL,
        $RECIPIENT_ID INTEGER UNIQUE REFERENCES ${RecipientDatabase.TABLE_NAME} (${RecipientDatabase.ID}),
        $ALLOWS_REPLIES INTEGER DEFAULT 1
      )
    """
  }

  private object MembershipTable {
    const val TABLE_NAME = "distribution_list_member"

    const val ID = "_id"
    const val LIST_ID = "list_id"
    const val RECIPIENT_ID = "recipient_id"

    const val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY AUTOINCREMENT,
        $LIST_ID INTEGER NOT NULL REFERENCES ${ListTable.TABLE_NAME} (${ListTable.ID}) ON DELETE CASCADE,
        $RECIPIENT_ID INTEGER NOT NULL REFERENCES ${RecipientDatabase.TABLE_NAME} (${RecipientDatabase.ID}),
        UNIQUE($LIST_ID, $RECIPIENT_ID) ON CONFLICT IGNORE
      )
    """
  }

  /**
   * @return true if the name change happened, false otherwise.
   */
  fun setName(distributionListId: DistributionListId, name: String): Boolean {
    val db = writableDatabase

    return db.updateWithOnConflict(
      ListTable.TABLE_NAME,
      contentValuesOf(ListTable.NAME to name),
      ID_WHERE,
      SqlUtil.buildArgs(distributionListId),
      SQLiteDatabase.CONFLICT_IGNORE
    ) == 1
  }

  fun getAllListsForContactSelectionUi(query: String?, includeMyStory: Boolean): List<DistributionListPartialRecord> {
    return getAllListsForContactSelectionUiCursor(query, includeMyStory)?.use {
      val results = mutableListOf<DistributionListPartialRecord>()
      while (it.moveToNext()) {
        results.add(
          DistributionListPartialRecord(
            id = DistributionListId.from(CursorUtil.requireLong(it, ListTable.ID)),
            name = CursorUtil.requireString(it, ListTable.NAME),
            allowsReplies = CursorUtil.requireBoolean(it, ListTable.ALLOWS_REPLIES),
            recipientId = RecipientId.from(CursorUtil.requireLong(it, ListTable.RECIPIENT_ID))
          )
        )
      }

      results
    } ?: emptyList()
  }

  fun getAllListsForContactSelectionUiCursor(query: String?, includeMyStory: Boolean): Cursor? {
    val db = readableDatabase
    val projection = arrayOf(ListTable.ID, ListTable.NAME, ListTable.RECIPIENT_ID, ListTable.ALLOWS_REPLIES)

    val where = when {
      query.isNullOrEmpty() && includeMyStory -> null
      query.isNullOrEmpty() -> "${ListTable.ID} != ?"
      includeMyStory -> "${ListTable.NAME} LIKE ? OR ${ListTable.ID} == ?"
      else -> "${ListTable.NAME} LIKE ? AND ${ListTable.ID} != ?"
    }

    val whereArgs = when {
      query.isNullOrEmpty() && includeMyStory -> null
      query.isNullOrEmpty() -> SqlUtil.buildArgs(DistributionListId.MY_STORY_ID)
      else -> SqlUtil.buildArgs("%$query%", DistributionListId.MY_STORY_ID)
    }

    return db.query(ListTable.TABLE_NAME, projection, where, whereArgs, null, null, null)
  }

  fun getCustomListsForUi(): List<DistributionListPartialRecord> {
    val db = readableDatabase
    val projection = SqlUtil.buildArgs(ListTable.ID, ListTable.NAME, ListTable.RECIPIENT_ID, ListTable.ALLOWS_REPLIES)
    val selection = "${ListTable.ID} != ${DistributionListId.MY_STORY_ID}"

    return db.query(ListTable.TABLE_NAME, projection, selection, null, null, null, null)?.use {
      val results = mutableListOf<DistributionListPartialRecord>()
      while (it.moveToNext()) {
        results.add(
          DistributionListPartialRecord(
            id = DistributionListId.from(CursorUtil.requireLong(it, ListTable.ID)),
            name = CursorUtil.requireString(it, ListTable.NAME),
            allowsReplies = CursorUtil.requireBoolean(it, ListTable.ALLOWS_REPLIES),
            recipientId = RecipientId.from(CursorUtil.requireLong(it, ListTable.RECIPIENT_ID))
          )
        )
      }

      results
    } ?: emptyList()
  }

  /**
   * @return The id of the list if successful, otherwise null. If not successful, you can assume it was a name conflict.
   */
  fun createList(name: String, members: List<RecipientId>): DistributionListId? {
    val db = writableDatabase

    db.beginTransaction()
    try {
      val values = ContentValues().apply {
        put(ListTable.NAME, name)
        put(ListTable.DISTRIBUTION_ID, UUID.randomUUID().toString())
        putNull(ListTable.RECIPIENT_ID)
      }

      val id = writableDatabase.insert(ListTable.TABLE_NAME, null, values)

      if (id < 0) {
        return null
      }

      val recipientId = SignalDatabase.recipients.getOrInsertFromDistributionListId(DistributionListId.from(id))
      writableDatabase.update(
        ListTable.TABLE_NAME,
        ContentValues().apply { put(ListTable.RECIPIENT_ID, recipientId.serialize()) },
        "${ListTable.ID} = ?",
        SqlUtil.buildArgs(id)
      )

      members.forEach { addMemberToList(DistributionListId.from(id), it) }

      db.setTransactionSuccessful()

      return DistributionListId.from(id)
    } finally {
      db.endTransaction()
    }
  }

  fun getStoryType(listId: DistributionListId): StoryType {
    readableDatabase.query(ListTable.TABLE_NAME, arrayOf(ListTable.ALLOWS_REPLIES), "${ListTable.ID} = ?", SqlUtil.buildArgs(listId), null, null, null).use { cursor ->
      return if (cursor.moveToFirst()) {
        if (CursorUtil.requireBoolean(cursor, ListTable.ALLOWS_REPLIES)) {
          StoryType.STORY_WITH_REPLIES
        } else {
          StoryType.STORY_WITHOUT_REPLIES
        }
      } else {
        error("Distribution list not in database.")
      }
    }
  }

  fun setAllowsReplies(listId: DistributionListId, allowsReplies: Boolean) {
    writableDatabase.update(ListTable.TABLE_NAME, contentValuesOf(ListTable.ALLOWS_REPLIES to allowsReplies), "${ListTable.ID} = ?", SqlUtil.buildArgs(listId))
  }

  fun getList(listId: DistributionListId): DistributionListRecord? {
    readableDatabase.query(ListTable.TABLE_NAME, null, "${ListTable.ID} = ?", SqlUtil.buildArgs(listId), null, null, null).use { cursor ->
      return if (cursor.moveToFirst()) {
        val id: DistributionListId = DistributionListId.from(cursor.requireLong(ListTable.ID))

        DistributionListRecord(
          id = id,
          name = cursor.requireNonNullString(ListTable.NAME),
          distributionId = DistributionId.from(cursor.requireNonNullString(ListTable.DISTRIBUTION_ID)),
          allowsReplies = CursorUtil.requireBoolean(cursor, ListTable.ALLOWS_REPLIES),
          members = getMembers(id)
        )
      } else {
        null
      }
    }
  }

  fun getDistributionId(listId: DistributionListId): DistributionId? {
    readableDatabase.query(ListTable.TABLE_NAME, arrayOf(ListTable.DISTRIBUTION_ID), "${ListTable.ID} = ?", SqlUtil.buildArgs(listId), null, null, null).use { cursor ->
      return if (cursor.moveToFirst()) {
        DistributionId.from(cursor.requireString(ListTable.DISTRIBUTION_ID))
      } else {
        null
      }
    }
  }

  fun getMembers(listId: DistributionListId): List<RecipientId> {
    if (listId == DistributionListId.MY_STORY) {
      val blockedMembers = getRawMembers(listId).toSet()

      return SignalDatabase.recipients.getSignalContacts(false)?.use {
        val result = mutableListOf<RecipientId>()
        while (it.moveToNext()) {
          val id = RecipientId.from(CursorUtil.requireLong(it, RecipientDatabase.ID))
          if (!blockedMembers.contains(id)) {
            result.add(id)
          }
        }
        result
      } ?: emptyList()
    } else {
      return getRawMembers(listId)
    }
  }

  fun getRawMembers(listId: DistributionListId): List<RecipientId> {
    val members = mutableListOf<RecipientId>()

    readableDatabase.query(MembershipTable.TABLE_NAME, null, "${MembershipTable.LIST_ID} = ?", SqlUtil.buildArgs(listId), null, null, null).use { cursor ->
      while (cursor.moveToNext()) {
        members.add(RecipientId.from(cursor.requireLong(MembershipTable.RECIPIENT_ID)))
      }
    }

    return members
  }

  fun getMemberCount(listId: DistributionListId): Int {
    return if (listId == DistributionListId.MY_STORY) {
      SignalDatabase.recipients.getSignalContacts(false)?.count?.let { it - getRawMemberCount(listId) } ?: 0
    } else {
      getRawMemberCount(listId)
    }
  }

  fun getRawMemberCount(listId: DistributionListId): Int {
    readableDatabase.query(MembershipTable.TABLE_NAME, SqlUtil.buildArgs("COUNT(*)"), "${MembershipTable.LIST_ID} = ?", SqlUtil.buildArgs(listId), null, null, null).use { cursor ->
      return if (cursor.moveToFirst()) {
        cursor.getInt(0)
      } else {
        0
      }
    }
  }

  fun removeMemberFromList(listId: DistributionListId, member: RecipientId) {
    writableDatabase.delete(MembershipTable.TABLE_NAME, "${MembershipTable.LIST_ID} = ? AND  ${MembershipTable.RECIPIENT_ID} = ?", SqlUtil.buildArgs(listId, member))
  }

  fun addMemberToList(listId: DistributionListId, member: RecipientId) {
    val values = ContentValues().apply {
      put(MembershipTable.LIST_ID, listId.serialize())
      put(MembershipTable.RECIPIENT_ID, member.serialize())
    }

    writableDatabase.insert(MembershipTable.TABLE_NAME, null, values)
  }

  fun remapRecipient(oldId: RecipientId, newId: RecipientId) {
    val values = ContentValues().apply {
      put(MembershipTable.RECIPIENT_ID, newId.serialize())
    }

    writableDatabase.update(MembershipTable.TABLE_NAME, values, "${MembershipTable.RECIPIENT_ID} = ?", SqlUtil.buildArgs(oldId))
  }

  fun deleteList(distributionListId: DistributionListId) {
    writableDatabase.delete(ListTable.TABLE_NAME, ID_WHERE, SqlUtil.buildArgs(distributionListId))
  }
}
