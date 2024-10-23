package org.thoughtcrime.securesms.database

import android.content.ContentValues
import android.content.Context
import androidx.core.content.contentValuesOf
import org.signal.core.util.SqlUtil
import org.signal.core.util.count
import org.signal.core.util.delete
import org.signal.core.util.groupBy
import org.signal.core.util.insertInto
import org.signal.core.util.readToList
import org.signal.core.util.readToSingleInt
import org.signal.core.util.readToSingleObject
import org.signal.core.util.requireBoolean
import org.signal.core.util.requireInt
import org.signal.core.util.requireLong
import org.signal.core.util.requireString
import org.signal.core.util.select
import org.signal.core.util.update
import org.signal.core.util.withinTransaction
import org.thoughtcrime.securesms.components.settings.app.chats.folders.ChatFolderRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies

/**
 * Stores chat folders and the chats that belong in each chat folder
 */
class ChatFolderTables(context: Context?, databaseHelper: SignalDatabase?) : DatabaseTable(context, databaseHelper), ThreadIdDatabaseReference {

  companion object {
    @JvmField
    val CREATE_TABLE: Array<String> = arrayOf(ChatFolderTable.CREATE_TABLE, ChatFolderMembershipTable.CREATE_TABLE)

    @JvmField
    val CREATE_INDEXES: Array<String> = ChatFolderTable.CREATE_INDEX + ChatFolderMembershipTable.CREATE_INDEXES

    fun insertInitialChatFoldersAtCreationTime(db: net.zetetic.database.sqlcipher.SQLiteDatabase) {
      db.insert(ChatFolderTable.TABLE_NAME, null, getAllChatsFolderContentValues())
    }

    private fun getAllChatsFolderContentValues(): ContentValues {
      return contentValuesOf(
        ChatFolderTable.POSITION to 0,
        ChatFolderTable.FOLDER_TYPE to ChatFolderRecord.FolderType.ALL.value,
        ChatFolderTable.SHOW_INDIVIDUAL to 1,
        ChatFolderTable.SHOW_GROUPS to 1,
        ChatFolderTable.SHOW_MUTED to 1
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
    const val IS_MUTED = "is_muted"
    const val FOLDER_TYPE = "folder_type"

    val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY AUTOINCREMENT,
        $NAME TEXT DEFAULT NULL,
        $POSITION INTEGER DEFAULT 0,
        $SHOW_UNREAD INTEGER DEFAULT 0,
        $SHOW_MUTED INTEGER DEFAULT 0,
        $SHOW_INDIVIDUAL INTEGER DEFAULT 0,
        $SHOW_GROUPS INTEGER DEFAULT 0,
        $IS_MUTED INTEGER DEFAULT 0,
        $FOLDER_TYPE INTEGER DEFAULT ${ChatFolderRecord.FolderType.CUSTOM.value}
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
   * Returns a single chat folder that corresponds to that id
   */
  fun getChatFolder(id: Long): ChatFolderRecord {
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
          isMuted = cursor.requireBoolean(ChatFolderTable.IS_MUTED),
          folderType = ChatFolderRecord.FolderType.deserialize(cursor.requireInt(ChatFolderTable.FOLDER_TYPE)),
          includedChats = includedChats[id] ?: emptyList(),
          excludedChats = excludedChats[id] ?: emptyList()
        )
      }

    return folder ?: ChatFolderRecord()
  }

  /**
   * Maps the chat folder ids to its corresponding chat folder
   */
  fun getChatFolders(includeUnreadAndMutedCount: Boolean = false): List<ChatFolderRecord> {
    val includedChats: Map<Long, List<Long>> = getIncludedChats()
    val excludedChats: Map<Long, List<Long>> = getExcludedChats()

    val folders = readableDatabase
      .select()
      .from(ChatFolderTable.TABLE_NAME)
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
          isMuted = cursor.requireBoolean(ChatFolderTable.IS_MUTED),
          folderType = ChatFolderRecord.FolderType.deserialize(cursor.requireInt(ChatFolderTable.FOLDER_TYPE)),
          includedChats = includedChats[id] ?: emptyList(),
          excludedChats = excludedChats[id] ?: emptyList()
        )
      }

    if (includeUnreadAndMutedCount) {
      return folders.map { folder ->
        folder.copy(
          unreadCount = SignalDatabase.threads.getUnreadCountByChatFolder(folder),
          isMuted = !SignalDatabase.threads.hasUnmutedChatsInFolder(folder)
        )
      }
    }

    return folders
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
   * Returns the number of user-made folders
   */
  fun getFolderCount(): Int {
    return readableDatabase
      .count()
      .from(ChatFolderTable.TABLE_NAME)
      .where("${ChatFolderTable.FOLDER_TYPE} != ${ChatFolderRecord.FolderType.ALL.value}")
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

      val id = db.insertInto(ChatFolderTable.TABLE_NAME)
        .values(
          contentValuesOf(
            ChatFolderTable.NAME to chatFolder.name,
            ChatFolderTable.SHOW_UNREAD to chatFolder.showUnread,
            ChatFolderTable.SHOW_MUTED to chatFolder.showMutedChats,
            ChatFolderTable.SHOW_INDIVIDUAL to chatFolder.showIndividualChats,
            ChatFolderTable.SHOW_GROUPS to chatFolder.showGroupChats,
            ChatFolderTable.IS_MUTED to chatFolder.isMuted,
            ChatFolderTable.POSITION to position
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
    writableDatabase.withinTransaction { db ->
      db.update(ChatFolderTable.TABLE_NAME)
        .values(
          ChatFolderTable.NAME to chatFolder.name,
          ChatFolderTable.SHOW_UNREAD to chatFolder.showUnread,
          ChatFolderTable.SHOW_MUTED to chatFolder.showMutedChats,
          ChatFolderTable.SHOW_INDIVIDUAL to chatFolder.showIndividualChats,
          ChatFolderTable.SHOW_GROUPS to chatFolder.showGroupChats,
          ChatFolderTable.IS_MUTED to chatFolder.isMuted
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
   * Deletes a chat folder
   */
  fun deleteChatFolder(chatFolder: ChatFolderRecord) {
    writableDatabase.withinTransaction { db ->
      db.delete(ChatFolderTable.TABLE_NAME, "${ChatFolderTable.ID} = ?", SqlUtil.buildArgs(chatFolder.id))
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
  fun addToFolder(folderId: Long, threadId: Long) {
    writableDatabase.withinTransaction { db ->
      db.insertInto(ChatFolderMembershipTable.TABLE_NAME)
        .values(
          ChatFolderMembershipTable.CHAT_FOLDER_ID to folderId,
          ChatFolderMembershipTable.THREAD_ID to threadId,
          ChatFolderMembershipTable.MEMBERSHIP_TYPE to MembershipType.INCLUDED.value
        )
        .run(SQLiteDatabase.CONFLICT_REPLACE)

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
