package org.thoughtcrime.securesms.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import androidx.core.content.contentValuesOf
import org.signal.core.util.Base64
import org.signal.core.util.CursorUtil
import org.signal.core.util.SqlUtil
import org.signal.core.util.delete
import org.signal.core.util.logging.Log
import org.signal.core.util.readToList
import org.signal.core.util.requireLong
import org.signal.core.util.requireNonNullString
import org.signal.core.util.requireObject
import org.signal.core.util.requireString
import org.signal.core.util.select
import org.signal.core.util.update
import org.signal.core.util.withinTransaction
import org.thoughtcrime.securesms.database.model.DistributionListId
import org.thoughtcrime.securesms.database.model.DistributionListPrivacyData
import org.thoughtcrime.securesms.database.model.DistributionListPrivacyMode
import org.thoughtcrime.securesms.database.model.DistributionListRecord
import org.thoughtcrime.securesms.database.model.StoryType
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.storage.StorageRecordUpdate
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.whispersystems.signalservice.api.push.DistributionId
import org.whispersystems.signalservice.api.storage.SignalStoryDistributionListRecord
import org.whispersystems.signalservice.api.util.UuidUtil
import java.util.UUID

/**
 * Stores distribution lists, which represent different sets of people you may want to share a story with.
 */
class DistributionListTables constructor(context: Context?, databaseHelper: SignalDatabase?) : DatabaseTable(context, databaseHelper), RecipientIdDatabaseReference {

  companion object {
    private val TAG = Log.tag(DistributionListTables::class.java)

    @JvmField
    val CREATE_TABLE: Array<String> = arrayOf(ListTable.CREATE_TABLE, MembershipTable.CREATE_TABLE)

    @JvmField
    val CREATE_INDEXES: Array<String> = MembershipTable.CREATE_INDEXES

    const val RECIPIENT_ID = ListTable.RECIPIENT_ID
    const val DISTRIBUTION_ID = ListTable.DISTRIBUTION_ID
    const val LIST_TABLE_NAME = ListTable.TABLE_NAME
    const val PRIVACY_MODE = ListTable.PRIVACY_MODE

    fun insertInitialDistributionListAtCreationTime(db: net.zetetic.database.sqlcipher.SQLiteDatabase) {
      val recipientId = db.insert(
        RecipientTable.TABLE_NAME,
        null,
        contentValuesOf(
          RecipientTable.TYPE to RecipientTable.RecipientType.DISTRIBUTION_LIST.id,
          RecipientTable.DISTRIBUTION_LIST_ID to DistributionListId.MY_STORY_ID,
          RecipientTable.STORAGE_SERVICE_ID to Base64.encodeWithPadding(StorageSyncHelper.generateKey()),
          RecipientTable.PROFILE_SHARING to 1
        )
      )
      db.insert(
        ListTable.TABLE_NAME,
        null,
        contentValuesOf(
          ListTable.ID to DistributionListId.MY_STORY_ID,
          ListTable.NAME to DistributionId.MY_STORY.toString(),
          ListTable.DISTRIBUTION_ID to DistributionId.MY_STORY.toString(),
          ListTable.RECIPIENT_ID to recipientId,
          ListTable.PRIVACY_MODE to DistributionListPrivacyMode.ALL.serialize()
        )
      )
    }
  }

  object ListTable {
    const val TABLE_NAME = "distribution_list"

    const val ID = "_id"
    const val NAME = "name"
    const val DISTRIBUTION_ID = "distribution_id"
    const val RECIPIENT_ID = "recipient_id"
    const val ALLOWS_REPLIES = "allows_replies"
    const val DELETION_TIMESTAMP = "deletion_timestamp"
    const val IS_UNKNOWN = "is_unknown"
    const val PRIVACY_MODE = "privacy_mode"

    val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY AUTOINCREMENT,
        $NAME TEXT NOT NULL,
        $DISTRIBUTION_ID TEXT UNIQUE NOT NULL,
        $RECIPIENT_ID INTEGER UNIQUE REFERENCES ${RecipientTable.TABLE_NAME} (${RecipientTable.ID}) ON DELETE CASCADE,
        $ALLOWS_REPLIES INTEGER DEFAULT 1,
        $DELETION_TIMESTAMP INTEGER DEFAULT 0,
        $IS_UNKNOWN INTEGER DEFAULT 0,
        $PRIVACY_MODE INTEGER DEFAULT ${DistributionListPrivacyMode.ONLY_WITH.serialize()}
      )
    """

    const val IS_NOT_DELETED = "$DELETION_TIMESTAMP == 0"

    val SEARCH_NAME_COLUMN = "search_name"
    private val SEARCH_NAME = "LOWER($NAME) AS $SEARCH_NAME_COLUMN"

    val LIST_UI_PROJECTION = arrayOf(ID, NAME, RECIPIENT_ID, ALLOWS_REPLIES, IS_UNKNOWN, PRIVACY_MODE, SEARCH_NAME)
  }

  object MembershipTable {
    const val TABLE_NAME = "distribution_list_member"

    const val ID = "_id"
    const val LIST_ID = "list_id"
    const val RECIPIENT_ID = "recipient_id"
    const val PRIVACY_MODE = "privacy_mode"

    const val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY AUTOINCREMENT,
        $LIST_ID INTEGER NOT NULL REFERENCES ${ListTable.TABLE_NAME} (${ListTable.ID}) ON DELETE CASCADE,
        $RECIPIENT_ID INTEGER NOT NULL REFERENCES ${RecipientTable.TABLE_NAME} (${RecipientTable.ID}) ON DELETE CASCADE,
        $PRIVACY_MODE INTEGER DEFAULT 0
      )
    """

    val CREATE_INDEXES = arrayOf(
      "CREATE UNIQUE INDEX distribution_list_member_list_id_recipient_id_privacy_mode_index ON $TABLE_NAME ($LIST_ID, $RECIPIENT_ID, $PRIVACY_MODE)",
      "CREATE INDEX distribution_list_member_recipient_id ON $TABLE_NAME ($RECIPIENT_ID)"
    )
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

  fun setPrivacyMode(distributionListId: DistributionListId, privacyMode: DistributionListPrivacyMode) {
    val values = contentValuesOf(ListTable.PRIVACY_MODE to privacyMode.serialize())
    writableDatabase.update(ListTable.TABLE_NAME, values, "${ListTable.ID} = ?", SqlUtil.buildArgs(distributionListId))
  }

  fun getAllListsForContactSelectionUiCursor(query: String?, includeMyStory: Boolean): Cursor? {
    val db = readableDatabase

    val where = when {
      query.isNullOrEmpty() && includeMyStory -> ListTable.IS_NOT_DELETED
      query.isNullOrEmpty() -> "${ListTable.ID} != ? AND ${ListTable.IS_NOT_DELETED}"
      includeMyStory -> "(${ListTable.SEARCH_NAME_COLUMN} GLOB ? OR ${ListTable.ID} == ?) AND ${ListTable.IS_NOT_DELETED} AND NOT ${ListTable.IS_UNKNOWN}"
      else -> "${ListTable.SEARCH_NAME_COLUMN} GLOB ? AND ${ListTable.ID} != ? AND ${ListTable.IS_NOT_DELETED} AND NOT ${ListTable.IS_UNKNOWN}"
    }

    val whereArgs = when {
      query.isNullOrEmpty() && includeMyStory -> null
      query.isNullOrEmpty() -> SqlUtil.buildArgs(DistributionListId.MY_STORY_ID)
      else -> SqlUtil.buildArgs(SqlUtil.buildCaseInsensitiveGlobPattern(query), DistributionListId.MY_STORY_ID)
    }

    return db.query(ListTable.TABLE_NAME, ListTable.LIST_UI_PROJECTION, where, whereArgs, null, null, null)
  }

  fun getAllListRecipients(): List<RecipientId> {
    return readableDatabase
      .select(ListTable.RECIPIENT_ID)
      .from(ListTable.TABLE_NAME)
      .run()
      .readToList { cursor -> RecipientId.from(cursor.requireLong(ListTable.RECIPIENT_ID)) }
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
    isUnknown: Boolean = false,
    privacyMode: DistributionListPrivacyMode = DistributionListPrivacyMode.ONLY_WITH
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
        put(ListTable.PRIVACY_MODE, privacyMode.serialize())
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

      members.forEach { addMemberToList(DistributionListId.from(id), privacyMode, it) }

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
    return getListByQuery("${ListTable.ID} = ? AND ${ListTable.IS_NOT_DELETED}", SqlUtil.buildArgs(listId))
  }

  fun getList(recipientId: RecipientId): DistributionListRecord? {
    return getListByQuery("${ListTable.RECIPIENT_ID} = ? AND ${ListTable.IS_NOT_DELETED}", SqlUtil.buildArgs(recipientId))
  }

  fun getListByDistributionId(distributionId: DistributionId): DistributionListRecord? {
    return getListByQuery("${ListTable.DISTRIBUTION_ID} = ? AND ${ListTable.IS_NOT_DELETED}", SqlUtil.buildArgs(distributionId))
  }

  private fun getListByQuery(query: String, args: Array<String>): DistributionListRecord? {
    readableDatabase.query(ListTable.TABLE_NAME, null, query, args, null, null, null).use { cursor ->
      return if (cursor.moveToFirst()) {
        val id: DistributionListId = DistributionListId.from(cursor.requireLong(ListTable.ID))
        val privacyMode: DistributionListPrivacyMode = cursor.requireObject(ListTable.PRIVACY_MODE, DistributionListPrivacyMode.Serializer)

        DistributionListRecord(
          id = id,
          name = cursor.requireNonNullString(ListTable.NAME),
          distributionId = DistributionId.from(cursor.requireNonNullString(ListTable.DISTRIBUTION_ID)),
          allowsReplies = CursorUtil.requireBoolean(cursor, ListTable.ALLOWS_REPLIES),
          rawMembers = getRawMembers(id, privacyMode),
          members = getMembers(id),
          deletedAtTimestamp = 0L,
          isUnknown = CursorUtil.requireBoolean(cursor, ListTable.IS_UNKNOWN),
          privacyMode = privacyMode
        )
      } else {
        null
      }
    }
  }

  /**
   * Gets the raw string value of distribution ID of the desired row. Added for additional logging around the UUID issues we've seen.
   */
  fun getRawDistributionId(listId: DistributionListId): String? {
    return readableDatabase
      .select(ListTable.DISTRIBUTION_ID)
      .from(ListTable.TABLE_NAME)
      .where("${ListTable.ID} = ?", listId)
      .run()
      .use { cursor ->
        if (cursor.moveToFirst()) {
          cursor.requireString(ListTable.DISTRIBUTION_ID)
        } else {
          null
        }
      }
  }

  fun getListForStorageSync(listId: DistributionListId): DistributionListRecord? {
    readableDatabase.query(ListTable.TABLE_NAME, null, "${ListTable.ID} = ?", SqlUtil.buildArgs(listId), null, null, null).use { cursor ->
      return if (cursor.moveToFirst()) {
        val id: DistributionListId = DistributionListId.from(cursor.requireLong(ListTable.ID))
        val privacyMode = cursor.requireObject(ListTable.PRIVACY_MODE, DistributionListPrivacyMode.Serializer)

        DistributionListRecord(
          id = id,
          name = cursor.requireNonNullString(ListTable.NAME),
          distributionId = DistributionId.from(cursor.requireNonNullString(ListTable.DISTRIBUTION_ID)),
          allowsReplies = CursorUtil.requireBoolean(cursor, ListTable.ALLOWS_REPLIES),
          rawMembers = getRawMembers(id, privacyMode),
          members = emptyList(),
          deletedAtTimestamp = cursor.requireLong(ListTable.DELETION_TIMESTAMP),
          isUnknown = CursorUtil.requireBoolean(cursor, ListTable.IS_UNKNOWN),
          privacyMode = privacyMode
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

  fun getDistributionId(recipientId: RecipientId): DistributionId? {
    readableDatabase.query(ListTable.TABLE_NAME, arrayOf(ListTable.DISTRIBUTION_ID), "${ListTable.RECIPIENT_ID} = ? AND ${ListTable.IS_NOT_DELETED}", SqlUtil.buildArgs(recipientId), null, null, null).use { cursor ->
      return if (cursor.moveToFirst()) {
        DistributionId.from(cursor.requireString(ListTable.DISTRIBUTION_ID))
      } else {
        null
      }
    }
  }

  fun getMembers(listId: DistributionListId): List<RecipientId> {
    lateinit var privacyMode: DistributionListPrivacyMode
    lateinit var rawMembers: List<RecipientId>

    readableDatabase.withinTransaction {
      privacyMode = getPrivacyMode(listId)
      rawMembers = getRawMembers(listId, privacyMode)
    }

    return when (privacyMode) {
      DistributionListPrivacyMode.ALL -> emptyList()
      DistributionListPrivacyMode.ONLY_WITH -> rawMembers
      DistributionListPrivacyMode.ALL_EXCEPT -> {
        SignalDatabase.recipients
          .getSignalContacts(false)!!
          .readToList(
            predicate = { !rawMembers.contains(it) },
            mapper = { it.requireObject(RecipientTable.ID, RecipientId.SERIALIZER) }
          )
      }
    }
  }

  fun getRawMembers(listId: DistributionListId, privacyMode: DistributionListPrivacyMode): List<RecipientId> {
    val members = mutableListOf<RecipientId>()

    readableDatabase.query(MembershipTable.TABLE_NAME, null, "${MembershipTable.LIST_ID} = ? AND ${MembershipTable.PRIVACY_MODE} = ?", SqlUtil.buildArgs(listId, privacyMode.serialize()), null, null, null).use { cursor ->
      while (cursor.moveToNext()) {
        members.add(RecipientId.from(cursor.requireLong(MembershipTable.RECIPIENT_ID)))
      }
    }

    return members
  }

  fun getMemberCount(listId: DistributionListId): Int {
    return getPrivacyData(listId).memberCount
  }

  fun getPrivacyData(listId: DistributionListId): DistributionListPrivacyData {
    lateinit var privacyMode: DistributionListPrivacyMode
    var rawMemberCount = 0
    var totalContactCount = 0

    readableDatabase.withinTransaction {
      privacyMode = getPrivacyMode(listId)
      rawMemberCount = getRawMemberCount(listId, privacyMode)
      totalContactCount = SignalDatabase.recipients.getSignalContactsCount(false)
    }

    val memberCount = when (privacyMode) {
      DistributionListPrivacyMode.ALL -> totalContactCount
      DistributionListPrivacyMode.ALL_EXCEPT -> rawMemberCount
      DistributionListPrivacyMode.ONLY_WITH -> rawMemberCount
    }

    return DistributionListPrivacyData(
      privacyMode = privacyMode,
      memberCount = memberCount
    )
  }

  private fun getRawMemberCount(listId: DistributionListId, privacyMode: DistributionListPrivacyMode): Int {
    readableDatabase.query(MembershipTable.TABLE_NAME, SqlUtil.buildArgs("COUNT(*)"), "${MembershipTable.LIST_ID} = ? AND ${MembershipTable.PRIVACY_MODE} = ?", SqlUtil.buildArgs(listId, privacyMode.serialize()), null, null, null).use { cursor ->
      return if (cursor.moveToFirst()) {
        cursor.getInt(0)
      } else {
        0
      }
    }
  }

  fun getPrivacyMode(listId: DistributionListId): DistributionListPrivacyMode {
    return readableDatabase
      .select(ListTable.PRIVACY_MODE)
      .from(ListTable.TABLE_NAME)
      .where("${ListTable.ID} = ?", listId.serialize())
      .run()
      .use {
        if (it.moveToFirst()) {
          it.requireObject(ListTable.PRIVACY_MODE, DistributionListPrivacyMode.Serializer)
        } else {
          DistributionListPrivacyMode.ONLY_WITH
        }
      }
  }

  fun removeMemberFromAllLists(member: RecipientId) {
    writableDatabase.delete(MembershipTable.TABLE_NAME, "${MembershipTable.RECIPIENT_ID} = ?", SqlUtil.buildArgs(member))
  }

  fun removeMemberFromList(listId: DistributionListId, privacyMode: DistributionListPrivacyMode, member: RecipientId) {
    writableDatabase.delete(MembershipTable.TABLE_NAME, "${MembershipTable.LIST_ID} = ? AND  ${MembershipTable.RECIPIENT_ID} = ? AND ${MembershipTable.PRIVACY_MODE} = ?", SqlUtil.buildArgs(listId, member, privacyMode.serialize()))
  }

  fun addMemberToList(listId: DistributionListId, privacyMode: DistributionListPrivacyMode, member: RecipientId) {
    val values = ContentValues().apply {
      put(MembershipTable.LIST_ID, listId.serialize())
      put(MembershipTable.RECIPIENT_ID, member.serialize())
      put(MembershipTable.PRIVACY_MODE, privacyMode.serialize())
    }

    writableDatabase.insert(MembershipTable.TABLE_NAME, null, values)
  }

  fun removeAllMembers(listId: DistributionListId) {
    writableDatabase
      .delete(MembershipTable.TABLE_NAME)
      .where("${MembershipTable.LIST_ID} = ?", listId.serialize())
      .run()
  }

  fun removeAllMembers(listId: DistributionListId, privacyMode: DistributionListPrivacyMode) {
    writableDatabase
      .delete(MembershipTable.TABLE_NAME)
      .where("${MembershipTable.LIST_ID} = ? AND ${MembershipTable.PRIVACY_MODE} = ?", listId.serialize(), privacyMode.serialize())
      .run()
  }

  override fun remapRecipient(oldId: RecipientId, newId: RecipientId) {
    writableDatabase
      .update(MembershipTable.TABLE_NAME)
      .values(MembershipTable.RECIPIENT_ID to newId.serialize())
      .where("${MembershipTable.RECIPIENT_ID} = ?", oldId)
      .run(SQLiteDatabase.CONFLICT_REPLACE)
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
    val uuid: UUID = requireNotNull(UuidUtil.parseOrNull(record.identifier)) { "Incoming record did not have a valid identifier." }
    val distributionId = DistributionId.from(uuid)

    return readableDatabase.query(
      ListTable.TABLE_NAME,
      arrayOf(ListTable.RECIPIENT_ID),
      "${ListTable.DISTRIBUTION_ID} = ?",
      SqlUtil.buildArgs(distributionId.toString()),
      null,
      null,
      null
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
      null,
      null,
      null
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

    val privacyMode: DistributionListPrivacyMode = when {
      insert.isBlockList && insert.recipients.isEmpty() -> DistributionListPrivacyMode.ALL
      insert.isBlockList -> DistributionListPrivacyMode.ALL_EXCEPT
      else -> DistributionListPrivacyMode.ONLY_WITH
    }

    createList(
      name = insert.name,
      members = insert.recipients.map(RecipientId::from),
      distributionId = distributionId,
      allowsReplies = insert.allowsReplies(),
      deletionTimestamp = insert.deletedAtTimestamp,
      privacyMode = privacyMode,
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
      if (distributionId == DistributionId.MY_STORY) {
        Log.w(TAG, "Refusing to delete My Story.")
        return
      }

      deleteList(distributionListId, update.new.deletedAtTimestamp)
      return
    }

    val privacyMode: DistributionListPrivacyMode = when {
      update.new.isBlockList && update.new.recipients.isEmpty() -> DistributionListPrivacyMode.ALL
      update.new.isBlockList -> DistributionListPrivacyMode.ALL_EXCEPT
      else -> DistributionListPrivacyMode.ONLY_WITH
    }

    writableDatabase.withinTransaction {
      val listTableValues = contentValuesOf(
        ListTable.ALLOWS_REPLIES to update.new.allowsReplies(),
        ListTable.NAME to update.new.name,
        ListTable.IS_UNKNOWN to false,
        ListTable.PRIVACY_MODE to privacyMode.serialize()
      )

      writableDatabase.update(
        ListTable.TABLE_NAME,
        listTableValues,
        "${ListTable.DISTRIBUTION_ID} = ?",
        SqlUtil.buildArgs(distributionId.toString())
      )

      val currentlyInDistributionList = getRawMembers(distributionListId, privacyMode).toSet()
      val shouldBeInDistributionList = update.new.recipients.map(RecipientId::from).toSet()
      val toRemove = currentlyInDistributionList - shouldBeInDistributionList
      val toAdd = shouldBeInDistributionList - currentlyInDistributionList

      toRemove.forEach {
        removeMemberFromList(distributionListId, privacyMode, it)
      }

      toAdd.forEach {
        addMemberToList(distributionListId, privacyMode, it)
      }
    }
  }

  private fun createUniqueNameForDeletedStory(): String {
    return "DELETED-${UUID.randomUUID()}"
  }

  private fun createUniqueNameForUnknownDistributionId(): String {
    return "DELETED-${UUID.randomUUID()}"
  }

  fun excludeFromStory(recipientId: RecipientId, record: DistributionListRecord) {
    excludeAllFromStory(listOf(recipientId), record)
  }

  fun excludeAllFromStory(recipientIds: List<RecipientId>, record: DistributionListRecord) {
    writableDatabase.withinTransaction {
      when (record.privacyMode) {
        DistributionListPrivacyMode.ONLY_WITH -> {
          recipientIds.forEach {
            removeMemberFromList(record.id, record.privacyMode, it)
          }
        }
        DistributionListPrivacyMode.ALL_EXCEPT -> {
          recipientIds.forEach {
            addMemberToList(record.id, record.privacyMode, it)
          }
        }
        DistributionListPrivacyMode.ALL -> {
          removeAllMembers(record.id, DistributionListPrivacyMode.ALL_EXCEPT)
          setPrivacyMode(record.id, DistributionListPrivacyMode.ALL_EXCEPT)

          recipientIds.forEach {
            addMemberToList(record.id, DistributionListPrivacyMode.ALL_EXCEPT, it)
          }
        }
      }
    }
  }
}
