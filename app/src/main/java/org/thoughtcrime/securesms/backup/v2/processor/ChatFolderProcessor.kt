/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.processor

import androidx.core.content.contentValuesOf
import org.signal.core.util.SqlUtil
import org.signal.core.util.insertInto
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.backup.v2.ExportState
import org.thoughtcrime.securesms.backup.v2.ImportState
import org.thoughtcrime.securesms.backup.v2.proto.ChatFolder
import org.thoughtcrime.securesms.backup.v2.proto.Frame
import org.thoughtcrime.securesms.backup.v2.stream.BackupFrameEmitter
import org.thoughtcrime.securesms.components.settings.app.chats.folders.ChatFolderRecord
import org.thoughtcrime.securesms.database.ChatFolderTables.ChatFolderMembershipTable
import org.thoughtcrime.securesms.database.ChatFolderTables.ChatFolderTable
import org.thoughtcrime.securesms.database.ChatFolderTables.MembershipType
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.backup.v2.proto.ChatFolder as ChatFolderProto

/**
 * Handles exporting and importing [ChatFolderRecord]s.
 */
object ChatFolderProcessor {

  private val TAG = Log.tag(ChatFolderProcessor::class)

  fun export(db: SignalDatabase, exportState: ExportState, emitter: BackupFrameEmitter) {
    val folders = db
      .chatFoldersTable
      .getChatFolders()
      .sortedBy { it.position }

    if (folders.isEmpty()) {
      Log.d(TAG, "No chat folders, nothing to export")
      return
    }

    if (folders.size == 1 && folders[0].folderType == ChatFolderRecord.FolderType.ALL) {
      Log.d(TAG, "Only ALL chat folder present, skipping chat folder export")
      return
    }

    if (folders.none { it.folderType == ChatFolderRecord.FolderType.ALL }) {
      Log.w(TAG, "Missing ALL chat folder, exporting as first position")
      emitter.emit(ChatFolderRecord.getAllChatsFolderForBackup().toBackupFrame(emptyList(), emptyList()))
    }

    folders.forEach { folder ->
      val includedRecipientIds = folder
        .includedChats
        .map { db.threadTable.getRecipientIdForThreadId(it)!!.toLong() }
        .filter { exportState.recipientIds.contains(it) }

      val excludedRecipientIds = folder
        .excludedChats
        .map { db.threadTable.getRecipientIdForThreadId(it)!!.toLong() }
        .filter { exportState.recipientIds.contains(it) }

      val frame = folder.toBackupFrame(includedRecipientIds, excludedRecipientIds)
      emitter.emit(frame)
    }
  }

  fun import(chatFolder: ChatFolderProto, importState: ImportState) {
    val chatFolderId = SignalDatabase
      .writableDatabase
      .insertInto(ChatFolderTable.TABLE_NAME)
      .values(
        ChatFolderTable.NAME to chatFolder.name,
        ChatFolderTable.POSITION to importState.getNextChatFolderPosition(),
        ChatFolderTable.SHOW_UNREAD to chatFolder.showOnlyUnread,
        ChatFolderTable.SHOW_MUTED to chatFolder.showMutedChats,
        ChatFolderTable.SHOW_INDIVIDUAL to chatFolder.includeAllIndividualChats,
        ChatFolderTable.SHOW_GROUPS to chatFolder.includeAllGroupChats,
        ChatFolderTable.FOLDER_TYPE to chatFolder.folderType.toLocal().value
      )
      .run()

    if (chatFolderId < 0) {
      Log.w(TAG, "Chat folder already exists")
      return
    }

    val includedChatsQueries = chatFolder.includedRecipientIds.toMembershipInsertQueries(chatFolderId, importState, MembershipType.INCLUDED)
    includedChatsQueries.forEach {
      SignalDatabase.writableDatabase.execSQL(it.where, it.whereArgs)
    }

    val excludedChatsQueries = chatFolder.excludedRecipientIds.toMembershipInsertQueries(chatFolderId, importState, MembershipType.EXCLUDED)
    excludedChatsQueries.forEach {
      SignalDatabase.writableDatabase.execSQL(it.where, it.whereArgs)
    }
  }
}

private fun ChatFolderRecord.toBackupFrame(includedRecipientIds: List<Long>, excludedRecipientIds: List<Long>): Frame {
  val chatFolder = ChatFolderProto(
    name = this.name,
    showOnlyUnread = this.showUnread,
    showMutedChats = this.showMutedChats,
    includeAllIndividualChats = this.showIndividualChats,
    includeAllGroupChats = this.showGroupChats,
    folderType = when (this.folderType) {
      ChatFolderRecord.FolderType.ALL -> ChatFolderProto.FolderType.ALL
      ChatFolderRecord.FolderType.CUSTOM -> ChatFolderProto.FolderType.CUSTOM
      else -> throw IllegalStateException("Only ALL or CUSTOM should be in the db")
    },
    includedRecipientIds = includedRecipientIds,
    excludedRecipientIds = excludedRecipientIds
  )

  return Frame(chatFolder = chatFolder)
}

private fun ChatFolderProto.FolderType.toLocal(): ChatFolderRecord.FolderType {
  return when (this) {
    ChatFolder.FolderType.UNKNOWN -> throw IllegalStateException()
    ChatFolder.FolderType.ALL -> ChatFolderRecord.FolderType.ALL
    ChatFolder.FolderType.CUSTOM -> ChatFolderRecord.FolderType.CUSTOM
  }
}

private fun List<Long>.toMembershipInsertQueries(chatFolderId: Long, importState: ImportState, membershipType: MembershipType): List<SqlUtil.Query> {
  val values = this
    .mapNotNull { importState.remoteToLocalRecipientId[it] }
    .map { recipientId -> importState.recipientIdToLocalThreadId[recipientId] ?: SignalDatabase.threads.getOrCreateThreadIdFor(recipientId, importState.recipientIdToIsGroup[recipientId] == true) }
    .map { threadId ->
      contentValuesOf(
        ChatFolderMembershipTable.CHAT_FOLDER_ID to chatFolderId,
        ChatFolderMembershipTable.THREAD_ID to threadId,
        ChatFolderMembershipTable.MEMBERSHIP_TYPE to membershipType.value
      )
    }

  return SqlUtil.buildBulkInsert(
    ChatFolderMembershipTable.TABLE_NAME,
    arrayOf(ChatFolderMembershipTable.CHAT_FOLDER_ID, ChatFolderMembershipTable.THREAD_ID, ChatFolderMembershipTable.MEMBERSHIP_TYPE),
    values
  )
}
