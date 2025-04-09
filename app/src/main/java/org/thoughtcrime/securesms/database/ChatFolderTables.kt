package org.thoughtcrime.securesms.database

import android.content.ContentValues
import android.content.Context
import androidx.core.content.contentValuesOf
import org.signal.core.util.Base64
import org.signal.core.util.SqlUtil
import org.signal.core.util.count
import org.signal.core.util.delete
import org.signal.core.util.groupBy
import org.signal.core.util.hasUnknownFields
import org.signal.core.util.insertInto
import org.signal.core.util.logging.Log
import org.signal.core.util.readToList
import org.signal.core.util.readToMap
import org.signal.core.util.readToSingleInt
import org.signal.core.util.readToSingleLongOrNull
import org.signal.core.util.readToSingleObject
import org.signal.core.util.requireBoolean
import org.signal.core.util.requireInt
import org.signal.core.util.requireLong
import org.signal.core.util.requireNonNullString
import org.signal.core.util.requireString
import org.signal.core.util.select
import org.signal.core.util.update
import org.signal.core.util.withinTransaction
import org.signal.libsignal.zkgroup.InvalidInputException
import org.signal.libsignal.zkgroup.groups.GroupMasterKey
import org.thoughtcrime.securesms.components.settings.app.chats.folders.ChatFolderId
import org.thoughtcrime.securesms.components.settings.app.chats.folders.ChatFolderRecord
import org.thoughtcrime.securesms.database.ThreadTable.Companion.ID
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.groups.BadGroupIdException
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.api.storage.SignalChatFolderRecord
import org.whispersystems.signalservice.api.storage.StorageId
import org.whispersystems.signalservice.api.util.UuidUtil
import java.util.concurrent.TimeUnit
import org.whispersystems.signalservice.internal.storage.protos.ChatFolderRecord as RemoteChatFolderRecord

/**
 * Stores chat folders and the chats that belong in each chat folder
 */
class ChatFolderTables(context: Context?, databaseHelper: SignalDatabase?) : DatabaseTable(context, databaseHelper), ThreadIdDatabaseReference {

  companion object {
    private val TAG = Log.tag(ChatFolderTable::class.java)
    private val DELETED_LIFESPAN: Long = TimeUnit.DAYS.toMillis(30)

    @JvmField
    val CREATE_TABLE: Array<String> = arrayOf(ChatFolderTable.CREATE_TABLE, ChatFolderMembershipTable.CREATE_TABLE)

    @JvmField
    val CREATE_INDEXES: Array<String> = ChatFolderTable.CREATE_INDEX + ChatFolderMembershipTable.CREATE_INDEXES

    fun insertInitialChatFoldersAtCreationTime(db: SQLiteDatabase) {
      db.insert(ChatFolderTable.TABLE_NAME, null, getAllChatsFolderContentValues())
    }

    private fun getAllChatsFolderContentValues(): ContentValues {
      return contentValuesOf(
        ChatFolderTable.POSITION to 0,
        ChatFolderTable.FOLDER_TYPE to ChatFolderRecord.FolderType.ALL.value,
        ChatFolderTable.SHOW_INDIVIDUAL to 1,
        ChatFolderTable.SHOW_GROUPS to 1,
        ChatFolderTable.SHOW_MUTED to 1,
        ChatFolderTable.CHAT_FOLDER_ID to ChatFolderId.generate().toString(),
        ChatFolderTable.STORAGE_SERVICE_ID to Base64.encodeWithPadding(StorageSyncHelper.generateKey())
      )
    }
  }

  /**
   * Represents the components of a chat folder and any chat types it contains
   */
  object ChatFolderTable {
    const val TABLE_NAME = "chat_folder"

    const val ID = "_id"
    const val NAME = "name"
    const val POSITION = "position"
    const val SHOW_UNREAD = "show_unread"
    const val SHOW_MUTED = "show_muted"
    const val SHOW_INDIVIDUAL = "show_individual"
    const val SHOW_GROUPS = "show_groups"
    const val FOLDER_TYPE = "folder_type"
    const val CHAT_FOLDER_ID = "chat_folder_id"
    const val STORAGE_SERVICE_ID = "storage_service_id"
    const val STORAGE_SERVICE_PROTO = "storage_service_proto"
    const val DELETED_TIMESTAMP_MS = "deleted_timestamp_ms"

    val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY AUTOINCREMENT,
        $NAME TEXT DEFAULT NULL,
        $POSITION INTEGER DEFAULT 0,
        $SHOW_UNREAD INTEGER DEFAULT 0,
        $SHOW_MUTED INTEGER DEFAULT 0,
        $SHOW_INDIVIDUAL INTEGER DEFAULT 0,
        $SHOW_GROUPS INTEGER DEFAULT 0,
        $FOLDER_TYPE INTEGER DEFAULT ${ChatFolderRecord.FolderType.CUSTOM.value},
        $CHAT_FOLDER_ID TEXT DEFAULT NULL,
        $STORAGE_SERVICE_ID TEXT DEFAULT NULL,
        $STORAGE_SERVICE_PROTO TEXT DEFAULT NULL,
        $DELETED_TIMESTAMP_MS INTEGER DEFAULT 0
      )
    """

    val CREATE_INDEX = arrayOf(
      "CREATE INDEX chat_folder_position_index ON $TABLE_NAME ($POSITION)"
    )
  }

  /**
   * Represents a thread that is associated with this chat folder. They are
   * either included in the chat folder or explicitly excluded.
   */
  object ChatFolderMembershipTable {
    const val TABLE_NAME = "chat_folder_membership"

    const val ID = "_id"
    const val CHAT_FOLDER_ID = "chat_folder_id"
    const val THREAD_ID = "thread_id"
    const val MEMBERSHIP_TYPE = "membership_type"

    const val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY AUTOINCREMENT,
        $CHAT_FOLDER_ID INTEGER NOT NULL REFERENCES ${ChatFolderTable.TABLE_NAME} (${ChatFolderTable.ID}) ON DELETE CASCADE,
        $THREAD_ID INTEGER NOT NULL REFERENCES ${ThreadTable.TABLE_NAME} (${ThreadTable.ID}) ON DELETE CASCADE,
        $MEMBERSHIP_TYPE INTEGER DEFAULT 1,
        UNIQUE(${CHAT_FOLDER_ID}, ${THREAD_ID}) ON CONFLICT REPLACE
      )
    """

    val CREATE_INDEXES = arrayOf(
      "CREATE INDEX chat_folder_membership_thread_id_index ON $TABLE_NAME ($THREAD_ID)",
      "CREATE INDEX chat_folder_membership_membership_type_index ON $TABLE_NAME ($MEMBERSHIP_TYPE)"
    )
  }

  override fun remapThread(fromId: Long, toId: Long) {
    writableDatabase
      .update(ChatFolderMembershipTable.TABLE_NAME)
      .values(ChatFolderMembershipTable.THREAD_ID to toId)
      .where("${ChatFolderMembershipTable.THREAD_ID} = ?", fromId)
      .run()
  }

  /**
   * Returns a single chat folder that corresponds to that query.
   * Assumes query will only match to one chat folder.
   */
  fun getChatFolder(query: SqlUtil.Query): ChatFolderRecord? {
    val id = readableDatabase
      .select(ChatFolderTable.ID)
      .from(ChatFolderTable.TABLE_NAME)
      .where(query.where, query.whereArgs)
      .run()
      .readToSingleLongOrNull()

    return getChatFolder(id)
  }

  /**
   * Returns a single chat folder that corresponds to that id
   */
  fun getChatFolder(id: Long?): ChatFolderRecord? {
    if (id == null) {
      return null
    }
    val includedChats: Map<Long, List<Long>> = getIncludedChats(id)
    val excludedChats: Map<Long, List<Long>> = getExcludedChats(id)

    val folder = readableDatabase
      .select()
      .from(ChatFolderTable.TABLE_NAME)
      .where("${ChatFolderTable.ID} = ?", id)
      .run()
      .readToSingleObject { cursor ->
        ChatFolderRecord(
          id = id,
          name = cursor.requireString(ChatFolderTable.NAME) ?: "",
          position = cursor.requireInt(ChatFolderTable.POSITION),
          showUnread = cursor.requireBoolean(ChatFolderTable.SHOW_UNREAD),
          showMutedChats = cursor.requireBoolean(ChatFolderTable.SHOW_MUTED),
          showIndividualChats = cursor.requireBoolean(ChatFolderTable.SHOW_INDIVIDUAL),
          showGroupChats = cursor.requireBoolean(ChatFolderTable.SHOW_GROUPS),
          folderType = ChatFolderRecord.FolderType.deserialize(cursor.requireInt(ChatFolderTable.FOLDER_TYPE)),
          includedChats = includedChats[id] ?: emptyList(),
          excludedChats = excludedChats[id] ?: emptyList(),
          chatFolderId = ChatFolderId.from(cursor.requireNonNullString(ChatFolderTable.CHAT_FOLDER_ID)),
          storageServiceId = cursor.requireString(ChatFolderTable.STORAGE_SERVICE_ID)?.let { StorageId.forChatFolder(Base64.decodeNullableOrThrow(it)) },
          storageServiceProto = Base64.decodeOrNull(cursor.requireString(ChatFolderTable.STORAGE_SERVICE_PROTO)),
          deletedTimestampMs = cursor.requireLong(ChatFolderTable.DELETED_TIMESTAMP_MS)
        )
      }

    return folder ?: ChatFolderRecord()
  }

  /**
   * Returns all non-deleted chat folders
   */
  fun getCurrentChatFolders(): List<ChatFolderRecord> {
    val includedChats: Map<Long, List<Long>> = getIncludedChats()
    val excludedChats: Map<Long, List<Long>> = getExcludedChats()

    val folders = readableDatabase
      .select()
      .from(ChatFolderTable.TABLE_NAME)
      .where("${ChatFolderTable.DELETED_TIMESTAMP_MS} = 0")
      .orderBy(ChatFolderTable.POSITION)
      .run()
      .readToList { cursor ->
        val id = cursor.requireLong(ChatFolderTable.ID)
        ChatFolderRecord(
          id = id,
          name = cursor.requireString(ChatFolderTable.NAME) ?: "",
          position = cursor.requireInt(ChatFolderTable.POSITION),
          showUnread = cursor.requireBoolean(ChatFolderTable.SHOW_UNREAD),
          showMutedChats = cursor.requireBoolean(ChatFolderTable.SHOW_MUTED),
          showIndividualChats = cursor.requireBoolean(ChatFolderTable.SHOW_INDIVIDUAL),
          showGroupChats = cursor.requireBoolean(ChatFolderTable.SHOW_GROUPS),
          folderType = ChatFolderRecord.FolderType.deserialize(cursor.requireInt(ChatFolderTable.FOLDER_TYPE)),
          includedChats = includedChats[id] ?: emptyList(),
          excludedChats = excludedChats[id] ?: emptyList(),
          chatFolderId = ChatFolderId.from(cursor.requireNonNullString((ChatFolderTable.CHAT_FOLDER_ID))),
          storageServiceId = cursor.requireString(ChatFolderTable.STORAGE_SERVICE_ID)?.let { StorageId.forChatFolder(Base64.decodeNullableOrThrow(it)) },
          storageServiceProto = Base64.decodeOrNull(cursor.requireString(ChatFolderTable.STORAGE_SERVICE_PROTO))
        )
      }

    return folders
  }

  /**
   * Given a list of folders, maps a folder id to the folder's unread count and whether all the chats in the folder are muted
   */
  fun getUnreadCountAndMutedStatusForFolders(folders: List<ChatFolderRecord>): HashMap<Long, Pair<Int, Boolean>> {
    val map: HashMap<Long, Pair<Int, Boolean>> = hashMapOf()
    folders.map { folder ->
      val unreadCount = SignalDatabase.threads.getUnreadCountByChatFolder(folder)
      val isMuted = !SignalDatabase.threads.hasUnmutedChatsInFolder(folder)
      map[folder.id] = Pair(unreadCount, isMuted)
    }
    return map
  }

  /**
   * Returns the number of non-deleted folders a user has, including the default 'All Chats'
   */
  fun getFolderCount(): Int {
    return readableDatabase
      .count()
      .from(ChatFolderTable.TABLE_NAME)
      .where("${ChatFolderTable.DELETED_TIMESTAMP_MS} = 0")
      .run()
      .readToSingleInt()
  }

  /**
   * Adds a chat folder and its corresponding included/excluded chats
   */
  fun createFolder(chatFolder: ChatFolderRecord) {
    writableDatabase.withinTransaction { db ->
      val position: Int = db
        .select("MAX(${ChatFolderTable.POSITION})")
        .from(ChatFolderTable.TABLE_NAME)
        .run()
        .readToSingleInt(0) + 1

      val storageId = chatFolder.storageServiceId?.raw ?: StorageSyncHelper.generateKey()
      val storageServiceProto = if (chatFolder.storageServiceProto != null) Base64.encodeWithPadding(chatFolder.storageServiceProto) else null
      val id = db.insertInto(ChatFolderTable.TABLE_NAME)
        .values(
          contentValuesOf(
            ChatFolderTable.NAME to chatFolder.name,
            ChatFolderTable.SHOW_UNREAD to chatFolder.showUnread,
            ChatFolderTable.SHOW_MUTED to chatFolder.showMutedChats,
            ChatFolderTable.SHOW_INDIVIDUAL to chatFolder.showIndividualChats,
            ChatFolderTable.SHOW_GROUPS to chatFolder.showGroupChats,
            ChatFolderTable.POSITION to if (chatFolder.position == -1 && chatFolder.deletedTimestampMs == 0L) position else chatFolder.position,
            ChatFolderTable.CHAT_FOLDER_ID to chatFolder.chatFolderId.toString(),
            ChatFolderTable.STORAGE_SERVICE_ID to Base64.encodeWithPadding(storageId),
            ChatFolderTable.STORAGE_SERVICE_PROTO to storageServiceProto,
            ChatFolderTable.DELETED_TIMESTAMP_MS to chatFolder.deletedTimestampMs
          )
        )
        .run(SQLiteDatabase.CONFLICT_IGNORE)

      val includedChatsQueries = SqlUtil.buildBulkInsert(
        ChatFolderMembershipTable.TABLE_NAME,
        arrayOf(ChatFolderMembershipTable.CHAT_FOLDER_ID, ChatFolderMembershipTable.THREAD_ID, ChatFolderMembershipTable.MEMBERSHIP_TYPE),
        chatFolder.includedChats.toContentValues(chatFolderId = id, membershipType = MembershipType.INCLUDED)
      )

      val excludedChatsQueries = SqlUtil.buildBulkInsert(
        ChatFolderMembershipTable.TABLE_NAME,
        arrayOf(ChatFolderMembershipTable.CHAT_FOLDER_ID, ChatFolderMembershipTable.THREAD_ID, ChatFolderMembershipTable.MEMBERSHIP_TYPE),
        chatFolder.excludedChats.toContentValues(chatFolderId = id, membershipType = MembershipType.EXCLUDED)
      )

      includedChatsQueries.forEach {
        db.execSQL(it.where, it.whereArgs)
      }

      excludedChatsQueries.forEach {
        db.execSQL(it.where, it.whereArgs)
      }

      AppDependencies.databaseObserver.notifyChatFolderObservers()
    }
  }

  /**
   * Updates the details for an existing folder like name, chat types, etc.
   */
  fun updateFolder(chatFolder: ChatFolderRecord) {
    val storageServiceProto = if (chatFolder.storageServiceProto != null) Base64.encodeWithPadding(chatFolder.storageServiceProto) else null
    writableDatabase.withinTransaction { db ->
      db.update(ChatFolderTable.TABLE_NAME)
        .values(
          ChatFolderTable.NAME to chatFolder.name,
          ChatFolderTable.POSITION to chatFolder.position,
          ChatFolderTable.SHOW_UNREAD to chatFolder.showUnread,
          ChatFolderTable.SHOW_MUTED to chatFolder.showMutedChats,
          ChatFolderTable.SHOW_INDIVIDUAL to chatFolder.showIndividualChats,
          ChatFolderTable.SHOW_GROUPS to chatFolder.showGroupChats,
          ChatFolderTable.STORAGE_SERVICE_PROTO to storageServiceProto,
          ChatFolderTable.DELETED_TIMESTAMP_MS to chatFolder.deletedTimestampMs
        )
        .where("${ChatFolderTable.ID} = ?", chatFolder.id)
        .run(SQLiteDatabase.CONFLICT_IGNORE)

      db
        .delete(ChatFolderMembershipTable.TABLE_NAME)
        .where("${ChatFolderMembershipTable.CHAT_FOLDER_ID} = ?", chatFolder.id)
        .run()

      val includedChats = SqlUtil.buildBulkInsert(
        ChatFolderMembershipTable.TABLE_NAME,
        arrayOf(ChatFolderMembershipTable.CHAT_FOLDER_ID, ChatFolderMembershipTable.THREAD_ID, ChatFolderMembershipTable.MEMBERSHIP_TYPE),
        chatFolder.includedChats.toContentValues(chatFolderId = chatFolder.id, membershipType = MembershipType.INCLUDED)
      )

      val excludedChats = SqlUtil.buildBulkInsert(
        ChatFolderMembershipTable.TABLE_NAME,
        arrayOf(ChatFolderMembershipTable.CHAT_FOLDER_ID, ChatFolderMembershipTable.THREAD_ID, ChatFolderMembershipTable.MEMBERSHIP_TYPE),
        chatFolder.excludedChats.toContentValues(chatFolderId = chatFolder.id, membershipType = MembershipType.EXCLUDED)
      )

      (includedChats + excludedChats).forEach {
        db.execSQL(it.where, it.whereArgs)
      }

      AppDependencies.databaseObserver.notifyChatFolderObservers()
    }
  }

  /**
   * Marks a chat folder as deleted and removes all associated chats
   */
  fun deleteChatFolder(chatFolder: ChatFolderRecord) {
    writableDatabase.withinTransaction { db ->
      db.update(ChatFolderTable.TABLE_NAME)
        .values(
          ChatFolderTable.DELETED_TIMESTAMP_MS to System.currentTimeMillis(),
          ChatFolderTable.POSITION to -1
        )
        .where("$ID = ?", chatFolder.id)
        .run()

      db.delete(ChatFolderMembershipTable.TABLE_NAME)
        .where("${ChatFolderMembershipTable.CHAT_FOLDER_ID} = ?", chatFolder.id)
        .run()

      resetPositions()
      AppDependencies.databaseObserver.notifyChatFolderObservers()
    }
  }

  /**
   * Updates the position of the chat folders
   */
  fun updatePositions(folders: List<ChatFolderRecord>) {
    writableDatabase.withinTransaction { db ->
      folders.forEach { folder ->
        db.update(ChatFolderTable.TABLE_NAME)
          .values(ChatFolderTable.POSITION to folder.position)
          .where("${ChatFolderTable.ID} = ?", folder.id)
          .run(SQLiteDatabase.CONFLICT_IGNORE)
      }
      AppDependencies.databaseObserver.notifyChatFolderObservers()
    }
  }

  /**
   * Removes a thread from a chat folder
   */
  fun removeFromFolder(folderId: Long, threadId: Long) {
    writableDatabase.withinTransaction { db ->
      db.insertInto(ChatFolderMembershipTable.TABLE_NAME)
        .values(
          ChatFolderMembershipTable.CHAT_FOLDER_ID to folderId,
          ChatFolderMembershipTable.THREAD_ID to threadId,
          ChatFolderMembershipTable.MEMBERSHIP_TYPE to MembershipType.EXCLUDED.value
        )
        .run(SQLiteDatabase.CONFLICT_REPLACE)

      AppDependencies.databaseObserver.notifyChatFolderObservers()
    }
  }

  /**
   * Adds a thread to a chat folder
   */
  fun addToFolder(folderId: Long, threadIds: List<Long>) {
    writableDatabase.withinTransaction { db ->
      threadIds.forEach { threadId ->
        db.insertInto(ChatFolderMembershipTable.TABLE_NAME)
          .values(
            ChatFolderMembershipTable.CHAT_FOLDER_ID to folderId,
            ChatFolderMembershipTable.THREAD_ID to threadId,
            ChatFolderMembershipTable.MEMBERSHIP_TYPE to MembershipType.INCLUDED.value
          )
          .run(SQLiteDatabase.CONFLICT_REPLACE)
      }

      AppDependencies.databaseObserver.notifyChatFolderObservers()
    }
  }

  /**
   * Inserts the default 'All chats' folder in cases where it could get deleted (eg backups)
   */
  fun insertAllChatFolder() {
    writableDatabase.withinTransaction { db ->
      db.insert(ChatFolderTable.TABLE_NAME, null, getAllChatsFolderContentValues())
    }
  }

  /**
   * Saves the new storage id for a chat folder
   */
  fun applyStorageIdUpdate(id: ChatFolderId, storageId: StorageId) {
    applyStorageIdUpdates(hashMapOf(id to storageId))
  }

  /**
   * Saves the new storage ids for all the chat folders in the map
   */
  fun applyStorageIdUpdates(storageIds: Map<ChatFolderId, StorageId>) {
    writableDatabase.withinTransaction { db ->
      storageIds.forEach { (chatFolderId, storageId) ->
        db.update(ChatFolderTable.TABLE_NAME)
          .values(ChatFolderTable.STORAGE_SERVICE_ID to Base64.encodeWithPadding(storageId.raw))
          .where("${ChatFolderTable.CHAT_FOLDER_ID} = ?", chatFolderId.toString())
          .run()
      }
    }
  }

  /**
   * Maps all chat folder ids to storage ids
   */
  fun getStorageSyncIdsMap(): Map<ChatFolderId, StorageId> {
    return readableDatabase
      .select(ChatFolderTable.CHAT_FOLDER_ID, ChatFolderTable.STORAGE_SERVICE_ID)
      .from(ChatFolderTable.TABLE_NAME)
      .where("${ChatFolderTable.STORAGE_SERVICE_ID} IS NOT NULL")
      .run()
      .readToMap { cursor ->
        val id = ChatFolderId.from(cursor.requireNonNullString(ChatFolderTable.CHAT_FOLDER_ID))
        val encodedKey = cursor.requireNonNullString(ChatFolderTable.STORAGE_SERVICE_ID)
        val key = Base64.decodeOrThrow(encodedKey)
        id to StorageId.forChatFolder(key)
      }
  }

  /**
   * Returns a list of all the storage ids used in chat folders
   */
  fun getStorageSyncIds(): List<StorageId> {
    return readableDatabase
      .select(ChatFolderTable.STORAGE_SERVICE_ID)
      .from(ChatFolderTable.TABLE_NAME)
      .where("${ChatFolderTable.STORAGE_SERVICE_ID} IS NOT NULL")
      .run()
      .readToList { cursor ->
        val encodedKey = cursor.requireNonNullString(ChatFolderTable.STORAGE_SERVICE_ID)
        val key = Base64.decodeOrThrow(encodedKey)
        StorageId.forChatFolder(key)
      }
  }

  /**
   * Creates a new storage id for a folder. Assumption is that StorageSyncHelper.scheduleSyncForDataChange() will be called after.
   */
  fun markNeedsSync(folderId: Long) {
    markNeedsSync(listOf(folderId))
  }

  /**
   * Creates new storage ids for multiple folders. Assumption is that StorageSyncHelper.scheduleSyncForDataChange() will be called after.
   */
  fun markNeedsSync(folderIds: List<Long>) {
    writableDatabase.withinTransaction {
      for (id in folderIds) {
        rotateStorageId(id)
      }
    }
  }

  /**
   * Inserts a remote chat folder into the database
   */
  fun insertChatFolderFromStorageSync(record: SignalChatFolderRecord) {
    if (record.proto.folderType == RemoteChatFolderRecord.FolderType.ALL) {
      Log.i(TAG, "All chats should already exists. Avoiding inserting another and only updating relevant fields")
      val storageServiceProto = if (record.proto.hasUnknownFields()) Base64.encodeWithPadding(record.serializedUnknowns!!) else null
      writableDatabase.withinTransaction { db ->
        db.update(ChatFolderTable.TABLE_NAME)
          .values(
            ChatFolderTable.POSITION to record.proto.position,
            ChatFolderTable.CHAT_FOLDER_ID to ChatFolderId.from(UuidUtil.parseOrThrow(record.proto.identifier)).toString(),
            ChatFolderTable.STORAGE_SERVICE_ID to Base64.encodeWithPadding(record.id.raw),
            ChatFolderTable.STORAGE_SERVICE_PROTO to storageServiceProto
          )
          .where("${ChatFolderTable.FOLDER_TYPE} = ?", ChatFolderRecord.FolderType.ALL.value)
          .run(SQLiteDatabase.CONFLICT_IGNORE)
      }
    } else {
      createFolder(remoteChatFolderRecordToLocal(record))
    }
  }

  /**
   * Updates an existing local chat folder with the details of the remote chat folder
   */
  fun updateChatFolderFromStorageSync(record: SignalChatFolderRecord) {
    updateFolder(remoteChatFolderRecordToLocal(record))
  }

  /**
   * Removes storageIds from folders that have been deleted for [DELETED_LIFESPAN].
   */
  fun removeStorageIdsFromOldDeletedFolders(now: Long): Int {
    return writableDatabase
      .update(ChatFolderTable.TABLE_NAME)
      .values(ChatFolderTable.STORAGE_SERVICE_ID to null)
      .where("${ChatFolderTable.STORAGE_SERVICE_ID} NOT NULL AND ${ChatFolderTable.DELETED_TIMESTAMP_MS} > 0 AND ${ChatFolderTable.DELETED_TIMESTAMP_MS} < ?", now - DELETED_LIFESPAN)
      .run()
  }

  /**
   * Removes storageIds of folders from the given collection because those folders are local only and deleted
   */
  fun removeStorageIdsFromLocalOnlyDeletedFolders(storageIds: Collection<StorageId>): Int {
    var updated = 0

    SqlUtil.buildCollectionQuery(ChatFolderTable.STORAGE_SERVICE_ID, storageIds.map { Base64.encodeWithPadding(it.raw) }, "${ChatFolderTable.DELETED_TIMESTAMP_MS} > 0 AND")
      .forEach {
        updated += writableDatabase.update(
          ChatFolderTable.TABLE_NAME,
          contentValuesOf(ChatFolderTable.STORAGE_SERVICE_ID to null),
          it.where,
          it.whereArgs
        )
      }

    return updated
  }

  /**
   * Maps a chat folder id to all of its corresponding included chats.
   * If an id is not specified, all chat folder ids will be mapped.
   */
  private fun getIncludedChats(id: Long? = null): Map<Long, List<Long>> {
    val whereQuery = if (id != null) {
      "${ChatFolderMembershipTable.MEMBERSHIP_TYPE} = ${MembershipType.INCLUDED.value} AND ${ChatFolderMembershipTable.CHAT_FOLDER_ID} = $id"
    } else {
      "${ChatFolderMembershipTable.MEMBERSHIP_TYPE} = ${MembershipType.INCLUDED.value}"
    }

    return readableDatabase
      .select()
      .from(ChatFolderMembershipTable.TABLE_NAME)
      .where(whereQuery)
      .run()
      .groupBy { cursor ->
        cursor.requireLong(ChatFolderMembershipTable.CHAT_FOLDER_ID) to cursor.requireLong(ChatFolderMembershipTable.THREAD_ID)
      }
  }

  /**
   * Maps a chat folder id to all of its corresponding excluded chats.
   * If an id is not specified, all chat folder ids will be mapped.
   */
  private fun getExcludedChats(id: Long? = null): Map<Long, List<Long>> {
    val whereQuery = if (id != null) {
      "${ChatFolderMembershipTable.MEMBERSHIP_TYPE} = ${MembershipType.EXCLUDED.value} AND ${ChatFolderMembershipTable.CHAT_FOLDER_ID} = $id"
    } else {
      "${ChatFolderMembershipTable.MEMBERSHIP_TYPE} = ${MembershipType.EXCLUDED.value}"
    }

    return readableDatabase
      .select()
      .from(ChatFolderMembershipTable.TABLE_NAME)
      .where(whereQuery)
      .run()
      .groupBy { cursor ->
        cursor.requireLong(ChatFolderMembershipTable.CHAT_FOLDER_ID) to cursor.requireLong(ChatFolderMembershipTable.THREAD_ID)
      }
  }

  /**
   * Ensures that chat folders positions are 0-indexed and consecutive
   */
  private fun resetPositions() {
    val folders = readableDatabase
      .select(ChatFolderTable.ID)
      .from(ChatFolderTable.TABLE_NAME)
      .where("${ChatFolderTable.DELETED_TIMESTAMP_MS} = 0")
      .orderBy("${ChatFolderTable.POSITION} ASC")
      .run()
      .readToList { cursor -> cursor.requireLong(ChatFolderTable.ID) }

    writableDatabase.withinTransaction { db ->
      folders.forEachIndexed { index, id ->
        db.update(ChatFolderTable.TABLE_NAME)
          .values(ChatFolderTable.POSITION to index)
          .where("${ChatFolderTable.ID} = ?", id)
          .run(SQLiteDatabase.CONFLICT_IGNORE)
      }
    }
  }

  /**
   * Rotates the storage id for a chat folder
   */
  private fun rotateStorageId(id: Long) {
    writableDatabase
      .update(ChatFolderTable.TABLE_NAME)
      .values(ChatFolderTable.STORAGE_SERVICE_ID to Base64.encodeWithPadding(StorageSyncHelper.generateKey()))
      .where("${ChatFolderTable.ID} = ?", id)
      .run()
  }

  /**
   * Given a [ChatFolderId], return the id for that folder
   */
  private fun getIdByRemoteChatFolderId(chatFolderId: ChatFolderId): Long? {
    return readableDatabase
      .select(ChatFolderTable.ID)
      .from(ChatFolderTable.TABLE_NAME)
      .where("${ChatFolderTable.CHAT_FOLDER_ID} = ?", chatFolderId.toString())
      .run()
      .readToSingleLongOrNull()
  }

  /**
   * Parses a remote chat folder [SignalChatFolderRecord] into a local folder [ChatFolderRecord]
   */
  private fun remoteChatFolderRecordToLocal(record: SignalChatFolderRecord): ChatFolderRecord {
    val chatFolderId = ChatFolderId.from(UuidUtil.parseOrThrow(record.proto.identifier))
    val id = getIdByRemoteChatFolderId(chatFolderId)
    return ChatFolderRecord(
      id = id ?: -1,
      name = record.proto.name,
      position = record.proto.position,
      showUnread = record.proto.showOnlyUnread,
      showMutedChats = record.proto.showMutedChats,
      showIndividualChats = record.proto.includeAllIndividualChats,
      showGroupChats = record.proto.includeAllGroupChats,
      folderType = when (record.proto.folderType) {
        RemoteChatFolderRecord.FolderType.ALL -> ChatFolderRecord.FolderType.ALL
        RemoteChatFolderRecord.FolderType.CUSTOM -> ChatFolderRecord.FolderType.CUSTOM
        RemoteChatFolderRecord.FolderType.UNKNOWN -> throw AssertionError("Folder type cannot be unknown")
      },
      includedChats = record.proto.includedRecipients
        .mapNotNull { remoteRecipient -> getRecipientIdFromRemoteRecipient(remoteRecipient) }
        .map { recipient -> SignalDatabase.threads.getOrCreateThreadIdFor(recipient) },
      excludedChats = record.proto.excludedRecipients
        .mapNotNull { remoteRecipient -> getRecipientIdFromRemoteRecipient(remoteRecipient) }
        .map { recipient -> SignalDatabase.threads.getOrCreateThreadIdFor(recipient) },
      chatFolderId = chatFolderId,
      storageServiceId = StorageId.forChatFolder(record.id.raw),
      storageServiceProto = record.serializedUnknowns,
      deletedTimestampMs = record.proto.deletedAtTimestampMs
    )
  }

  /**
   * Parses a remote recipient into a local one. Used when configuring the chats of a remote chat folder into a local one.
   */
  private fun getRecipientIdFromRemoteRecipient(remoteRecipient: RemoteChatFolderRecord.Recipient): Recipient? {
    return if (remoteRecipient.contact != null) {
      val serviceId = ServiceId.parseOrNull(remoteRecipient.contact!!.serviceId)
      val e164 = remoteRecipient.contact!!.e164
      Recipient.externalPush(SignalServiceAddress(serviceId, e164))
    } else if (remoteRecipient.legacyGroupId != null) {
      try {
        Recipient.externalGroupExact(GroupId.v1(remoteRecipient.legacyGroupId!!.toByteArray()))
      } catch (e: BadGroupIdException) {
        Log.w(TAG, "Failed to parse groupV1 ID!", e)
        null
      }
    } else if (remoteRecipient.groupMasterKey != null) {
      try {
        Recipient.externalGroupExact(GroupId.v2(GroupMasterKey(remoteRecipient.groupMasterKey!!.toByteArray())))
      } catch (e: InvalidInputException) {
        Log.w(TAG, "Failed to parse groupV2 master key!", e)
        null
      }
    } else {
      Log.w(TAG, "Could not find recipient")
      null
    }
  }

  private fun Collection<Long>.toContentValues(chatFolderId: Long, membershipType: MembershipType): List<ContentValues> {
    return map {
      contentValuesOf(
        ChatFolderMembershipTable.CHAT_FOLDER_ID to chatFolderId,
        ChatFolderMembershipTable.THREAD_ID to it,
        ChatFolderMembershipTable.MEMBERSHIP_TYPE to membershipType.value
      )
    }
  }

  enum class MembershipType(val value: Int) {
    /** Chat that should be included in the chat folder */
    INCLUDED(0),

    /** Chat that should be excluded from the chat folder */
    EXCLUDED(1)
  }
}
