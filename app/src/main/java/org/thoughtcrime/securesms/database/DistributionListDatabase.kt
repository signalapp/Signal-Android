package org.thoughtcrime.securesms.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import androidx.core.content.contentValuesOf
import org.signal.core.util.CursorUtil
import org.signal.core.util.SqlUtil
import org.signal.core.util.logging.Log
import org.signal.core.util.requireLong
import org.signal.core.util.requireNonNullString
import org.signal.core.util.requireString
import org.signal.core.util.select
import org.thoughtcrime.securesms.database.model.DistributionListId
import org.thoughtcrime.securesms.database.model.DistributionListPartialRecord
import org.thoughtcrime.securesms.database.model.DistributionListRecord
import org.thoughtcrime.securesms.database.model.StoryType
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.storage.StorageRecordUpdate
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.util.Base64
import org.whispersystems.signalservice.api.push.DistributionId
import org.whispersystems.signalservice.api.storage.SignalStoryDistributionListRecord
import org.whispersystems.signalservice.api.util.UuidUtil
import java.util.UUID

/**
 * Stores distribution lists, which represent different sets of people you may want to share a story with.
 */
class DistributionListDatabase constructor(context: Context?, databaseHelper: SignalDatabase?) : Database(context, databaseHelper) {

  companion object {
    private val TAG = Log.tag(DistributionListDatabase::class.java)

    @JvmField
    val CREATE_TABLE: Array<String> = arrayOf(ListTable.CREATE_TABLE, MembershipTable.CREATE_TABLE)

    const val RECIPIENT_ID = ListTable.RECIPIENT_ID
    const val DISTRIBUTION_ID = ListTable.DISTRIBUTION_ID
    const val LIST_TABLE_NAME = ListTable.TABLE_NAME

    fun insertInitialDistributionListAtCreationTime(db: net.zetetic.database.sqlcipher.SQLiteDatabase) {
      val recipientId = db.insert(
        RecipientDatabase.TABLE_NAME, null,
        contentValuesOf(
          RecipientDatabase.GROUP_TYPE to RecipientDatabase.GroupType.DISTRIBUTION_LIST.id,
          RecipientDatabase.DISTRIBUTION_LIST_ID to DistributionListId.MY_STORY_ID,
          RecipientDatabase.STORAGE_SERVICE_ID to Base64.encodeBytes(StorageSyncHelper.generateKey()),
          RecipientDatabase.PROFILE_SHARING to 1
        )
      )
      db.insert(
        ListTable.TABLE_NAME, null,
        contentValuesOf(
          ListTable.ID to DistributionListId.MY_STORY_ID,
          ListTable.NAME to DistributionId.MY_STORY.toString(),
          ListTable.DISTRIBUTION_ID to DistributionId.MY_STORY.toString(),
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
    const val DELETION_TIMESTAMP = "deletion_timestamp"
    const val IS_UNKNOWN = "is_unknown"

    const val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY AUTOINCREMENT,
        $NAME TEXT UNIQUE NOT NULL,
        $DISTRIBUTION_ID TEXT UNIQUE NOT NULL,
        $RECIPIENT_ID INTEGER UNIQUE REFERENCES ${RecipientDatabase.TABLE_NAME} (${RecipientDatabase.ID}),
        $ALLOWS_REPLIES INTEGER DEFAULT 1,
        $DELETION_TIMESTAMP INTEGER DEFAULT 0,
        $IS_UNKNOWN INTEGER DEFAULT 0
      )
    """

    const val IS_NOT_DELETED = "$DELETION_TIMESTAMP == 0"
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
            recipientId = RecipientId.from(CursorUtil.requireLong(it, ListTable.RECIPIENT_ID)),
            isUnknown = CursorUtil.requireBoolean(it, ListTable.IS_UNKNOWN)
          )
        )
      }

      results
    } ?: emptyList()
  }

  fun getAllListsForContactSelectionUiCursor(query: String?, includeMyStory: Boolean): Cursor? {
    val db = readableDatabase
    val projection = arrayOf(ListTable.ID, ListTable.NAME, ListTable.RECIPIENT_ID, ListTable.ALLOWS_REPLIES, ListTable.IS_UNKNOWN)

    val where = when {
      query.isNullOrEmpty() && includeMyStory -> ListTable.IS_NOT_DELETED
      query.isNullOrEmpty() -> "${ListTable.ID} != ? AND ${ListTable.IS_NOT_DELETED}"
      includeMyStory -> "(${ListTable.NAME} GLOB ? OR ${ListTable.ID} == ?) AND ${ListTable.IS_NOT_DELETED} AND NOT ${ListTable.IS_UNKNOWN}"
      else -> "${ListTable.NAME} GLOB ? AND ${ListTable.ID} != ? AND ${ListTable.IS_NOT_DELETED} AND NOT ${ListTable.IS_UNKNOWN}"
    }

    val whereArgs = when {
      query.isNullOrEmpty() && includeMyStory -> null
      query.isNullOrEmpty() -> SqlUtil.buildArgs(DistributionListId.MY_STORY_ID)
      else -> SqlUtil.buildArgs(SqlUtil.buildCaseInsensitiveGlobPattern(query), DistributionListId.MY_STORY_ID)
    }

    return db.query(ListTable.TABLE_NAME, projection, where, whereArgs, null, null, null)
  }

  fun getCustomListsForUi(): List<DistributionListPartialRecord> {
    val db = readableDatabase
    val projection = SqlUtil.buildArgs(ListTable.ID, ListTable.NAME, ListTable.RECIPIENT_ID, ListTable.ALLOWS_REPLIES, ListTable.IS_UNKNOWN)
    val selection = "${ListTable.ID} != ${DistributionListId.MY_STORY_ID} AND ${ListTable.IS_NOT_DELETED}"

    return db.query(ListTable.TABLE_NAME, projection, selection, null, null, null, null)?.use {
      val results = mutableListOf<DistributionListPartialRecord>()
      while (it.moveToNext()) {
        results.add(
          DistributionListPartialRecord(
            id = DistributionListId.from(CursorUtil.requireLong(it, ListTable.ID)),
            name = CursorUtil.requireString(it, ListTable.NAME),
            allowsReplies = CursorUtil.requireBoolean(it, ListTable.ALLOWS_REPLIES),
            recipientId = RecipientId.from(CursorUtil.requireLong(it, ListTable.RECIPIENT_ID)),
            isUnknown = CursorUtil.requireBoolean(it, ListTable.IS_UNKNOWN)
          )
        )
      }

      results
    } ?: emptyList()
  }

  /**
   * Gets or creates a distribution list for the given id.
   *
   * If the list does not exist, then a new list is created with a randomized name and populated with the members
   * in the manifest.
   *
   * @return the recipient id of the list
   */
  fun getOrCreateByDistributionId(distributionId: DistributionId, manifest: SentStorySyncManifest): RecipientId {
    writableDatabase.beginTransaction()
    try {
      val distributionRecipientId = getRecipientIdByDistributionId(distributionId)
      if (distributionRecipientId == null) {
        val members: List<RecipientId> = manifest.entries
          .filter { it.distributionLists.contains(distributionId) }
          .map { it.recipientId }

        val distributionListId = createList(
          name = createUniqueNameForUnknownDistributionId(),
          members = members,
          distributionId = distributionId,
          isUnknown = true
        )

        if (distributionListId == null) {
          throw AssertionError("Failed to create distribution list for unknown id.")
        } else {
          val recipient = getRecipientId(distributionListId)
          if (recipient == null) {
            throw AssertionError("Failed to retrieve recipient for newly created list")
          } else {
            writableDatabase.setTransactionSuccessful()
            return recipient
          }
        }
      }

      writableDatabase.setTransactionSuccessful()
      return distributionRecipientId
    } finally {
      writableDatabase.endTransaction()
    }
  }

  /**
   * @return The id of the list if successful, otherwise null. If not successful, you can assume it was a name conflict.
   */
  fun createList(
    name: String,
    members: List<RecipientId>,
    distributionId: DistributionId = DistributionId.from(UUID.randomUUID()),
    allowsReplies: Boolean = true,
    deletionTimestamp: Long = 0L,
    storageId: ByteArray? = null,
    isUnknown: Boolean = false
  ): DistributionListId? {
    val db = writableDatabase

    db.beginTransaction()
    try {
      val values = ContentValues().apply {
        put(ListTable.NAME, if (deletionTimestamp == 0L) name else createUniqueNameForDeletedStory())
        put(ListTable.DISTRIBUTION_ID, distributionId.toString())
        put(ListTable.ALLOWS_REPLIES, if (deletionTimestamp == 0L) allowsReplies else false)
        putNull(ListTable.RECIPIENT_ID)
        put(ListTable.DELETION_TIMESTAMP, deletionTimestamp)
        put(ListTable.IS_UNKNOWN, isUnknown)
      }

      val id = writableDatabase.insert(ListTable.TABLE_NAME, null, values)

      if (id < 0) {
        return null
      }

      val recipientId = SignalDatabase.recipients.getOrInsertFromDistributionListId(DistributionListId.from(id), storageId)
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

  fun getRecipientIdByDistributionId(distributionId: DistributionId): RecipientId? {
    return readableDatabase
      .select(ListTable.RECIPIENT_ID)
      .from(ListTable.TABLE_NAME)
      .where("${ListTable.DISTRIBUTION_ID} = ?", distributionId.toString())
      .run()
      .use { cursor ->
        if (cursor.moveToFirst()) {
          RecipientId.from(CursorUtil.requireLong(cursor, ListTable.RECIPIENT_ID))
        } else {
          null
        }
      }
  }

  fun getStoryType(listId: DistributionListId): StoryType {
    readableDatabase.query(ListTable.TABLE_NAME, arrayOf(ListTable.ALLOWS_REPLIES), "${ListTable.ID} = ? AND ${ListTable.IS_NOT_DELETED}", SqlUtil.buildArgs(listId), null, null, null).use { cursor ->
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
    writableDatabase.update(ListTable.TABLE_NAME, contentValuesOf(ListTable.ALLOWS_REPLIES to allowsReplies), "${ListTable.ID} = ? AND ${ListTable.IS_NOT_DELETED}", SqlUtil.buildArgs(listId))
  }

  fun getList(listId: DistributionListId): DistributionListRecord? {
    readableDatabase.query(ListTable.TABLE_NAME, null, "${ListTable.ID} = ? AND ${ListTable.IS_NOT_DELETED}", SqlUtil.buildArgs(listId), null, null, null).use { cursor ->
      return if (cursor.moveToFirst()) {
        val id: DistributionListId = DistributionListId.from(cursor.requireLong(ListTable.ID))

        DistributionListRecord(
          id = id,
          name = cursor.requireNonNullString(ListTable.NAME),
          distributionId = DistributionId.from(cursor.requireNonNullString(ListTable.DISTRIBUTION_ID)),
          allowsReplies = CursorUtil.requireBoolean(cursor, ListTable.ALLOWS_REPLIES),
          members = getMembers(id),
          deletedAtTimestamp = 0L,
          isUnknown = CursorUtil.requireBoolean(cursor, ListTable.IS_UNKNOWN)
        )
      } else {
        null
      }
    }
  }

  fun getListForStorageSync(listId: DistributionListId): DistributionListRecord? {
    readableDatabase.query(ListTable.TABLE_NAME, null, "${ListTable.ID} = ?", SqlUtil.buildArgs(listId), null, null, null).use { cursor ->
      return if (cursor.moveToFirst()) {
        val id: DistributionListId = DistributionListId.from(cursor.requireLong(ListTable.ID))

        DistributionListRecord(
          id = id,
          name = cursor.requireNonNullString(ListTable.NAME),
          distributionId = DistributionId.from(cursor.requireNonNullString(ListTable.DISTRIBUTION_ID)),
          allowsReplies = CursorUtil.requireBoolean(cursor, ListTable.ALLOWS_REPLIES),
          members = getRawMembers(id),
          deletedAtTimestamp = cursor.requireLong(ListTable.DELETION_TIMESTAMP),
          isUnknown = CursorUtil.requireBoolean(cursor, ListTable.IS_UNKNOWN)
        )
      } else {
        null
      }
    }
  }

  fun getDistributionId(listId: DistributionListId): DistributionId? {
    readableDatabase.query(ListTable.TABLE_NAME, arrayOf(ListTable.DISTRIBUTION_ID), "${ListTable.ID} = ? AND ${ListTable.IS_NOT_DELETED}", SqlUtil.buildArgs(listId), null, null, null).use { cursor ->
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

  fun deleteList(distributionListId: DistributionListId, deletionTimestamp: Long = System.currentTimeMillis()) {
    writableDatabase.update(
      ListTable.TABLE_NAME,
      contentValuesOf(
        ListTable.NAME to createUniqueNameForDeletedStory(),
        ListTable.ALLOWS_REPLIES to false,
        ListTable.DELETION_TIMESTAMP to deletionTimestamp
      ),
      ID_WHERE,
      SqlUtil.buildArgs(distributionListId)
    )

    writableDatabase.delete(
      MembershipTable.TABLE_NAME,
      "${MembershipTable.LIST_ID} = ?",
      SqlUtil.buildArgs(distributionListId)
    )
  }

  fun getRecipientIdForSyncRecord(record: SignalStoryDistributionListRecord): RecipientId? {
    val uuid: UUID = UuidUtil.parseOrNull(record.identifier) ?: return null
    val distributionId = DistributionId.from(uuid)

    return readableDatabase.query(
      ListTable.TABLE_NAME,
      arrayOf(ListTable.RECIPIENT_ID),
      "${ListTable.DISTRIBUTION_ID} = ?",
      SqlUtil.buildArgs(distributionId.toString()),
      null, null, null
    )?.use { cursor ->
      if (cursor.moveToFirst()) {
        RecipientId.from(CursorUtil.requireLong(cursor, ListTable.RECIPIENT_ID))
      } else {
        null
      }
    }
  }

  fun getRecipientId(distributionListId: DistributionListId): RecipientId? {
    return readableDatabase.query(
      ListTable.TABLE_NAME,
      arrayOf(ListTable.RECIPIENT_ID),
      "${ListTable.ID} = ?",
      SqlUtil.buildArgs(distributionListId),
      null, null, null
    )?.use { cursor ->
      if (cursor.moveToFirst()) {
        RecipientId.from(CursorUtil.requireLong(cursor, ListTable.RECIPIENT_ID))
      } else {
        null
      }
    }
  }

  fun applyStorageSyncStoryDistributionListInsert(insert: SignalStoryDistributionListRecord) {
    val distributionId = DistributionId.from(UuidUtil.parseOrThrow(insert.identifier))
    if (distributionId == DistributionId.MY_STORY) {
      throw AssertionError("Should never try to insert My Story")
    }

    createList(
      name = insert.name,
      members = insert.recipients.map(RecipientId::from),
      distributionId = distributionId,
      allowsReplies = insert.allowsReplies(),
      deletionTimestamp = insert.deletedAtTimestamp,
      storageId = insert.id.raw
    )
  }

  fun applyStorageSyncStoryDistributionListUpdate(update: StorageRecordUpdate<SignalStoryDistributionListRecord>) {
    val distributionId = DistributionId.from(UuidUtil.parseOrThrow(update.new.identifier))

    val distributionListId: DistributionListId? = readableDatabase.query(ListTable.TABLE_NAME, arrayOf(ListTable.ID), "${ListTable.DISTRIBUTION_ID} = ?", SqlUtil.buildArgs(distributionId.toString()), null, null, null).use { cursor ->
      if (cursor == null || !cursor.moveToFirst()) {
        null
      } else {
        DistributionListId.from(CursorUtil.requireLong(cursor, ListTable.ID))
      }
    }

    if (distributionListId == null) {
      Log.w(TAG, "Cannot find required distribution list.")
      return
    }

    val recipientId = getRecipientId(distributionListId)!!
    SignalDatabase.recipients.updateStorageId(recipientId, update.new.id.raw)

    if (update.new.deletedAtTimestamp > 0L) {
      if (distributionId.asUuid().equals(DistributionId.MY_STORY.asUuid())) {
        Log.w(TAG, "Refusing to delete My Story.")
        return
      }

      deleteList(distributionListId, update.new.deletedAtTimestamp)
      return
    }

    writableDatabase.beginTransaction()
    try {
      val listTableValues = contentValuesOf(
        ListTable.ALLOWS_REPLIES to update.new.allowsReplies(),
        ListTable.NAME to update.new.name,
        ListTable.IS_UNKNOWN to false
      )

      writableDatabase.update(
        ListTable.TABLE_NAME,
        listTableValues,
        "${ListTable.DISTRIBUTION_ID} = ?",
        SqlUtil.buildArgs(distributionId.toString())
      )

      val currentlyInDistributionList = getRawMembers(distributionListId).toSet()
      val shouldBeInDistributionList = update.new.recipients.map(RecipientId::from).toSet()
      val toRemove = currentlyInDistributionList - shouldBeInDistributionList
      val toAdd = shouldBeInDistributionList - currentlyInDistributionList

      toRemove.forEach {
        removeMemberFromList(distributionListId, it)
      }

      toAdd.forEach {
        addMemberToList(distributionListId, it)
      }

      writableDatabase.setTransactionSuccessful()
    } finally {
      writableDatabase.endTransaction()
    }
  }

  private fun createUniqueNameForDeletedStory(): String {
    return "DELETED-${UUID.randomUUID()}"
  }

  private fun createUniqueNameForUnknownDistributionId(): String {
    return "DELETED-${UUID.randomUUID()}"
  }
}
