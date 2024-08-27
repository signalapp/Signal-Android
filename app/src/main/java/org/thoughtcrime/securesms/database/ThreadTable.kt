package org.thoughtcrime.securesms.database

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MergeCursor
import android.net.Uri
import androidx.core.content.contentValuesOf
import com.fasterxml.jackson.annotation.JsonProperty
import org.json.JSONObject
import org.jsoup.helper.StringUtil
import org.signal.core.util.CursorUtil
import org.signal.core.util.SqlUtil
import org.signal.core.util.Stopwatch
import org.signal.core.util.delete
import org.signal.core.util.exists
import org.signal.core.util.logging.Log
import org.signal.core.util.or
import org.signal.core.util.readToList
import org.signal.core.util.readToSingleLong
import org.signal.core.util.requireBoolean
import org.signal.core.util.requireInt
import org.signal.core.util.requireLong
import org.signal.core.util.requireString
import org.signal.core.util.select
import org.signal.core.util.toInt
import org.signal.core.util.update
import org.signal.core.util.updateAll
import org.signal.core.util.withinTransaction
import org.signal.libsignal.zkgroup.InvalidInputException
import org.signal.libsignal.zkgroup.groups.GroupMasterKey
import org.thoughtcrime.securesms.conversationlist.model.ConversationFilter
import org.thoughtcrime.securesms.database.MessageTable.MarkedMessageInfo
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.attachments
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.calls
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.drafts
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.groupReceipts
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.mentions
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.messageLog
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.messages
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.recipients
import org.thoughtcrime.securesms.database.ThreadBodyUtil.ThreadBody
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.database.model.ThreadRecord
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList
import org.thoughtcrime.securesms.database.model.databaseprotos.MessageExtras
import org.thoughtcrime.securesms.database.model.serialize
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.groups.BadGroupIdException
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.jobs.MultiDeviceDeleteSyncJob
import org.thoughtcrime.securesms.jobs.OptimizeMessageSearchIndexJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.mms.SlideDeck
import org.thoughtcrime.securesms.mms.StickerSlide
import org.thoughtcrime.securesms.notifications.v2.ConversationId
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientCreator
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.recipients.RecipientUtil
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.util.ConversationUtil
import org.thoughtcrime.securesms.util.JsonUtils
import org.thoughtcrime.securesms.util.JsonUtils.SaneJSONObject
import org.thoughtcrime.securesms.util.LRUCache
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.isScheduled
import org.whispersystems.signalservice.api.storage.SignalAccountRecord
import org.whispersystems.signalservice.api.storage.SignalAccountRecord.PinnedConversation
import org.whispersystems.signalservice.api.storage.SignalContactRecord
import org.whispersystems.signalservice.api.storage.SignalGroupV1Record
import org.whispersystems.signalservice.api.storage.SignalGroupV2Record
import java.io.Closeable
import java.io.IOException
import java.util.Collections
import java.util.LinkedList
import java.util.Optional
import kotlin.math.max
import kotlin.math.min

@SuppressLint("RecipientIdDatabaseReferenceUsage", "ThreadIdDatabaseReferenceUsage") // Handles remapping in a unique way
class ThreadTable(context: Context, databaseHelper: SignalDatabase) : DatabaseTable(context, databaseHelper) {

  companion object {
    private val TAG = Log.tag(ThreadTable::class.java)

    const val TABLE_NAME = "thread"
    const val ID = "_id"
    const val DATE = "date"
    const val MEANINGFUL_MESSAGES = "meaningful_messages"
    const val RECIPIENT_ID = "recipient_id"
    const val READ = "read"
    const val UNREAD_COUNT = "unread_count"
    const val TYPE = "type"
    const val ERROR = "error"
    const val SNIPPET = "snippet"
    const val SNIPPET_TYPE = "snippet_type"
    const val SNIPPET_URI = "snippet_uri"
    const val SNIPPET_CONTENT_TYPE = "snippet_content_type"
    const val SNIPPET_EXTRAS = "snippet_extras"
    const val SNIPPET_MESSAGE_EXTRAS = "snippet_message_extras"
    const val ARCHIVED = "archived"
    const val STATUS = "status"
    const val HAS_DELIVERY_RECEIPT = "has_delivery_receipt"
    const val HAS_READ_RECEIPT = "has_read_receipt"
    const val EXPIRES_IN = "expires_in"
    const val LAST_SEEN = "last_seen"
    const val HAS_SENT = "has_sent"
    const val LAST_SCROLLED = "last_scrolled"
    const val PINNED = "pinned"
    const val UNREAD_SELF_MENTION_COUNT = "unread_self_mention_count"
    const val ACTIVE = "active"

    const val MAX_CACHE_SIZE = 1000

    @JvmField
    val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY AUTOINCREMENT, 
        $DATE INTEGER DEFAULT 0, 
        $MEANINGFUL_MESSAGES INTEGER DEFAULT 0,
        $RECIPIENT_ID INTEGER NOT NULL UNIQUE REFERENCES ${RecipientTable.TABLE_NAME} (${RecipientTable.ID}) ON DELETE CASCADE,
        $READ INTEGER DEFAULT ${ReadStatus.READ.serialize()}, 
        $TYPE INTEGER DEFAULT 0, 
        $ERROR INTEGER DEFAULT 0, 
        $SNIPPET TEXT, 
        $SNIPPET_TYPE INTEGER DEFAULT 0, 
        $SNIPPET_URI TEXT DEFAULT NULL, 
        $SNIPPET_CONTENT_TYPE TEXT DEFAULT NULL, 
        $SNIPPET_EXTRAS TEXT DEFAULT NULL, 
        $UNREAD_COUNT INTEGER DEFAULT 0, 
        $ARCHIVED INTEGER DEFAULT 0, 
        $STATUS INTEGER DEFAULT 0, 
        $HAS_DELIVERY_RECEIPT INTEGER DEFAULT 0, 
        $HAS_READ_RECEIPT INTEGER DEFAULT 0, 
        $EXPIRES_IN INTEGER DEFAULT 0, 
        $LAST_SEEN INTEGER DEFAULT 0, 
        $HAS_SENT INTEGER DEFAULT 0, 
        $LAST_SCROLLED INTEGER DEFAULT 0, 
        $PINNED INTEGER DEFAULT 0, 
        $UNREAD_SELF_MENTION_COUNT INTEGER DEFAULT 0,
        $ACTIVE INTEGER DEFAULT 0,
        $SNIPPET_MESSAGE_EXTRAS BLOB DEFAULT NULL
      )
    """

    @JvmField
    val CREATE_INDEXS = arrayOf(
      "CREATE INDEX IF NOT EXISTS thread_recipient_id_index ON $TABLE_NAME ($RECIPIENT_ID, $ACTIVE);",
      "CREATE INDEX IF NOT EXISTS archived_count_index ON $TABLE_NAME ($ACTIVE, $ARCHIVED, $MEANINGFUL_MESSAGES, $PINNED);",
      "CREATE INDEX IF NOT EXISTS thread_pinned_index ON $TABLE_NAME ($PINNED);",
      "CREATE INDEX IF NOT EXISTS thread_read ON $TABLE_NAME ($READ);",
      "CREATE INDEX IF NOT EXISTS thread_active ON $TABLE_NAME ($ACTIVE);"
    )

    private val THREAD_PROJECTION = arrayOf(
      ID,
      DATE,
      MEANINGFUL_MESSAGES,
      RECIPIENT_ID,
      SNIPPET,
      READ,
      UNREAD_COUNT,
      TYPE,
      ERROR,
      SNIPPET_TYPE,
      SNIPPET_URI,
      SNIPPET_CONTENT_TYPE,
      SNIPPET_EXTRAS,
      SNIPPET_MESSAGE_EXTRAS,
      ARCHIVED,
      STATUS,
      HAS_DELIVERY_RECEIPT,
      EXPIRES_IN,
      LAST_SEEN,
      HAS_READ_RECEIPT,
      LAST_SCROLLED,
      PINNED,
      UNREAD_SELF_MENTION_COUNT
    )

    private val TYPED_THREAD_PROJECTION: List<String> = THREAD_PROJECTION
      .map { columnName: String -> "$TABLE_NAME.$columnName" }
      .toList()

    private val COMBINED_THREAD_RECIPIENT_GROUP_PROJECTION: List<String> = TYPED_THREAD_PROJECTION + RecipientTable.TYPED_RECIPIENT_PROJECTION_NO_ID + GroupTable.TYPED_GROUP_PROJECTION

    const val NO_TRIM_BEFORE_DATE_SET: Long = 0
    const val NO_TRIM_MESSAGE_COUNT_SET = Int.MAX_VALUE
  }

  private val threadIdCache = LRUCache<RecipientId, Long>(MAX_CACHE_SIZE)

  private fun createThreadForRecipient(recipientId: RecipientId, group: Boolean, distributionType: Int): Long {
    if (recipientId.isUnknown) {
      throw AssertionError("Cannot create a thread for an unknown recipient!")
    }

    val date = System.currentTimeMillis()
    val contentValues = contentValuesOf(
      DATE to date - date % 1000,
      RECIPIENT_ID to recipientId.serialize(),
      MEANINGFUL_MESSAGES to 0
    )

    if (group) {
      contentValues.put(TYPE, distributionType)
    }

    val result = writableDatabase.insert(TABLE_NAME, null, contentValues)
    Recipient.live(recipientId).refresh()
    return result
  }

  private fun updateThread(
    threadId: Long,
    meaningfulMessages: Boolean,
    body: String?,
    attachment: Uri?,
    contentType: String?,
    extra: Extra?,
    date: Long,
    status: Int,
    deliveryReceiptCount: Int,
    type: Long,
    unarchive: Boolean,
    expiresIn: Long,
    readReceiptCount: Int,
    unreadCount: Int,
    unreadMentionCount: Int,
    messageExtras: MessageExtras?
  ) {
    var extraSerialized: String? = null

    if (extra != null) {
      extraSerialized = try {
        JsonUtils.toJson(extra)
      } catch (e: IOException) {
        throw AssertionError(e)
      }
    }

    val contentValues = contentValuesOf(
      DATE to date - date % 1000,
      SNIPPET to body,
      SNIPPET_URI to attachment?.toString(),
      SNIPPET_TYPE to type,
      SNIPPET_CONTENT_TYPE to contentType,
      SNIPPET_EXTRAS to extraSerialized,
      MEANINGFUL_MESSAGES to if (meaningfulMessages) 1 else 0,
      STATUS to status,
      HAS_DELIVERY_RECEIPT to deliveryReceiptCount,
      HAS_READ_RECEIPT to readReceiptCount,
      EXPIRES_IN to expiresIn,
      ACTIVE to 1,
      UNREAD_COUNT to unreadCount,
      UNREAD_SELF_MENTION_COUNT to unreadMentionCount,
      SNIPPET_MESSAGE_EXTRAS to messageExtras?.encode()
    )

    writableDatabase
      .update(TABLE_NAME)
      .values(contentValues)
      .where("$ID = ?", threadId)
      .run()

    if (unarchive && allowedToUnarchive(threadId)) {
      val archiveValues = contentValuesOf(ARCHIVED to 0)
      val query = SqlUtil.buildTrueUpdateQuery(ID_WHERE, SqlUtil.buildArgs(threadId), archiveValues)
      if (writableDatabase.update(TABLE_NAME, archiveValues, query.where, query.whereArgs) > 0) {
        StorageSyncHelper.scheduleSyncForDataChange()
      }
    }
  }

  private fun allowedToUnarchive(threadId: Long): Boolean {
    if (!SignalStore.settings.shouldKeepMutedChatsArchived()) {
      return true
    }

    val threadRecipientId: RecipientId? = getRecipientIdForThreadId(threadId)

    return threadRecipientId == null || !recipients.isMuted(threadRecipientId)
  }

  fun updateSnippetUriSilently(threadId: Long, attachment: Uri?) {
    writableDatabase
      .update(TABLE_NAME)
      .values(SNIPPET_URI to attachment?.toString())
      .where("$ID = ?", threadId)
      .run()
  }

  fun updateSnippet(threadId: Long, snippet: String?, attachment: Uri?, date: Long, type: Long, unarchive: Boolean) {
    if (isSilentType(type)) {
      return
    }

    val contentValues = contentValuesOf(
      DATE to date - date % 1000,
      SNIPPET to snippet,
      SNIPPET_URI to attachment?.toString(),
      SNIPPET_TYPE to type,
      SNIPPET_CONTENT_TYPE to null,
      SNIPPET_EXTRAS to null
    )

    if (unarchive && allowedToUnarchive(threadId)) {
      contentValues.put(ARCHIVED, 0)
    }

    writableDatabase
      .update(TABLE_NAME)
      .values(contentValues)
      .where("$ID = ?", threadId)
      .run()

    notifyConversationListListeners()
  }

  fun trimAllThreads(length: Int, trimBeforeDate: Long) {
    if (length == NO_TRIM_MESSAGE_COUNT_SET && trimBeforeDate == NO_TRIM_BEFORE_DATE_SET) {
      return
    }

    val syncThreadTrimDeletes = SignalStore.settings.shouldSyncThreadTrimDeletes() && Recipient.self().deleteSyncCapability.isSupported
    val threadTrimsToSync = mutableListOf<ThreadDeleteSyncInfo>()

    readableDatabase
      .select(ID)
      .from(TABLE_NAME)
      .run()
      .use { cursor ->
        while (cursor.moveToNext()) {
          trimThreadInternal(
            threadId = cursor.requireLong(ID),
            syncThreadTrimDeletes = syncThreadTrimDeletes,
            length = length,
            trimBeforeDate = trimBeforeDate
          )?.also {
            threadTrimsToSync += it
          }
        }
      }

    val deletes = writableDatabase.withinTransaction {
      messages.deleteAbandonedMessages()
      attachments.trimAllAbandonedAttachments()
      groupReceipts.deleteAbandonedRows()
      mentions.deleteAbandonedMentions()
      return@withinTransaction attachments.deleteAbandonedAttachmentFiles()
    }

    if (deletes > 0) {
      Log.i(TAG, "Trim all threads caused $deletes attachments to be deleted.")
    }

    if (syncThreadTrimDeletes && threadTrimsToSync.isNotEmpty()) {
      MultiDeviceDeleteSyncJob.enqueueThreadDeletes(threadTrimsToSync, isFullDelete = false)
    }

    notifyAttachmentListeners()
    notifyStickerPackListeners()
    OptimizeMessageSearchIndexJob.enqueue()
  }

  fun trimThread(
    threadId: Long,
    syncThreadTrimDeletes: Boolean,
    length: Int = NO_TRIM_MESSAGE_COUNT_SET,
    trimBeforeDate: Long = NO_TRIM_BEFORE_DATE_SET,
    inclusive: Boolean = false
  ) {
    if (length == NO_TRIM_MESSAGE_COUNT_SET && trimBeforeDate == NO_TRIM_BEFORE_DATE_SET) {
      return
    }

    var threadTrimToSync: ThreadDeleteSyncInfo? = null
    val deletes = writableDatabase.withinTransaction {
      threadTrimToSync = trimThreadInternal(threadId, syncThreadTrimDeletes, length, trimBeforeDate, inclusive)
      messages.deleteAbandonedMessages()
      attachments.trimAllAbandonedAttachments()
      groupReceipts.deleteAbandonedRows()
      mentions.deleteAbandonedMentions()
      return@withinTransaction attachments.deleteAbandonedAttachmentFiles()
    }

    if (deletes > 0) {
      Log.i(TAG, "Trim thread $threadId caused $deletes attachments to be deleted.")
    }

    if (syncThreadTrimDeletes && threadTrimToSync != null) {
      MultiDeviceDeleteSyncJob.enqueueThreadDeletes(listOf(threadTrimToSync!!), isFullDelete = false)
    }

    notifyAttachmentListeners()
    notifyStickerPackListeners()
    OptimizeMessageSearchIndexJob.enqueue()
  }

  private fun trimThreadInternal(
    threadId: Long,
    syncThreadTrimDeletes: Boolean,
    length: Int,
    trimBeforeDate: Long,
    inclusive: Boolean = false
  ): ThreadDeleteSyncInfo? {
    if (length == NO_TRIM_MESSAGE_COUNT_SET && trimBeforeDate == NO_TRIM_BEFORE_DATE_SET) {
      return null
    }

    val finalTrimBeforeDate = if (length != NO_TRIM_MESSAGE_COUNT_SET && length > 0) {
      messages.getConversation(threadId).use { cursor ->
        if (cursor.count > length) {
          cursor.moveToPosition(length - 1)
          max(trimBeforeDate, cursor.requireLong(MessageTable.DATE_RECEIVED))
        } else {
          trimBeforeDate
        }
      }
    } else {
      trimBeforeDate
    }

    if (finalTrimBeforeDate != NO_TRIM_BEFORE_DATE_SET) {
      Log.i(TAG, "Trimming thread: $threadId before: $finalTrimBeforeDate inclusive: $inclusive")

      val addressableMessages: Set<MessageRecord> = if (syncThreadTrimDeletes) {
        messages.getAddressableMessagesBefore(threadId, finalTrimBeforeDate, excludeExpiring = false)
      } else {
        emptySet()
      }

      val nonExpiringAddressableMessages: Set<MessageRecord> = if (syncThreadTrimDeletes && addressableMessages.size == MessageTable.ADDRESSABLE_MESSAGE_LIMIT && addressableMessages.any { it.expiresIn > 0 }) {
        messages.getAddressableMessagesBefore(threadId, finalTrimBeforeDate, excludeExpiring = true)
      } else {
        emptySet()
      }

      val deletes = messages.deleteMessagesInThreadBeforeDate(threadId, finalTrimBeforeDate, inclusive)

      if (deletes > 0) {
        Log.i(TAG, "Trimming deleted $deletes messages thread: $threadId")
        setLastScrolled(threadId, 0)
        val threadDeleted = update(threadId = threadId, unarchive = false, syncThreadDelete = syncThreadTrimDeletes)
        notifyConversationListeners(threadId)
        SignalDatabase.calls.updateCallEventDeletionTimestamps()

        return if (syncThreadTrimDeletes && (threadDeleted || addressableMessages.isNotEmpty())) {
          ThreadDeleteSyncInfo(threadId, addressableMessages, nonExpiringAddressableMessages)
        } else {
          null
        }
      } else {
        Log.i(TAG, "Trimming deleted no messages thread: $threadId")
      }
    }

    return null
  }

  fun setAllThreadsRead(): List<MarkedMessageInfo> {
    writableDatabase
      .updateAll(TABLE_NAME)
      .values(
        READ to ReadStatus.READ.serialize(),
        UNREAD_COUNT to 0,
        UNREAD_SELF_MENTION_COUNT to 0
      )
      .run()

    val messageRecords: List<MarkedMessageInfo> = messages.setAllMessagesRead()

    messages.setAllReactionsSeen()
    notifyConversationListListeners()

    return messageRecords
  }

  fun hasCalledSince(recipient: Recipient, timestamp: Long): Boolean {
    return hasReceivedAnyCallsSince(getOrCreateThreadIdFor(recipient), timestamp)
  }

  fun hasReceivedAnyCallsSince(threadId: Long, timestamp: Long): Boolean {
    return messages.hasReceivedAnyCallsSince(threadId, timestamp)
  }

  fun setEntireThreadRead(threadId: Long): List<MarkedMessageInfo> {
    setRead(threadId, false)
    return messages.setEntireThreadRead(threadId)
  }

  fun setRead(threadId: Long, lastSeen: Boolean): List<MarkedMessageInfo> {
    return setReadSince(Collections.singletonMap(threadId, -1L), lastSeen)
  }

  fun setRead(conversationId: ConversationId, lastSeen: Boolean): List<MarkedMessageInfo> {
    return if (conversationId.groupStoryId == null) {
      setRead(conversationId.threadId, lastSeen)
    } else {
      setGroupStoryReadSince(conversationId.threadId, conversationId.groupStoryId, System.currentTimeMillis())
    }
  }

  fun setReadSince(conversationId: ConversationId, lastSeen: Boolean, sinceTimestamp: Long): List<MarkedMessageInfo> {
    return if (conversationId.groupStoryId != null) {
      setGroupStoryReadSince(conversationId.threadId, conversationId.groupStoryId, sinceTimestamp)
    } else {
      setReadSince(conversationId.threadId, lastSeen, sinceTimestamp)
    }
  }

  fun setReadSince(threadId: Long, lastSeen: Boolean, sinceTimestamp: Long): List<MarkedMessageInfo> {
    return setReadSince(Collections.singletonMap(threadId, sinceTimestamp), lastSeen)
  }

  fun setRead(threadIds: Collection<Long>, lastSeen: Boolean): List<MarkedMessageInfo> {
    return setReadSince(threadIds.associateWith { -1L }, lastSeen)
  }

  private fun setGroupStoryReadSince(threadId: Long, groupStoryId: Long, sinceTimestamp: Long): List<MarkedMessageInfo> {
    return messages.setGroupStoryMessagesReadSince(threadId, groupStoryId, sinceTimestamp)
  }

  fun setReadSince(threadIdToSinceTimestamp: Map<Long, Long>, lastSeen: Boolean): List<MarkedMessageInfo> {
    val messageRecords: MutableList<MarkedMessageInfo> = LinkedList()
    var needsSync = false

    writableDatabase.withinTransaction { db ->
      for ((threadId, sinceTimestamp) in threadIdToSinceTimestamp) {
        val previous = getThreadRecord(threadId)

        messageRecords += messages.setMessagesReadSince(threadId, sinceTimestamp)

        messages.setReactionsSeen(threadId, sinceTimestamp)

        val unreadCount = messages.getUnreadCount(threadId)
        val unreadMentionsCount = messages.getUnreadMentionCount(threadId)

        val contentValues = contentValuesOf(
          READ to ReadStatus.READ.serialize(),
          UNREAD_COUNT to unreadCount,
          UNREAD_SELF_MENTION_COUNT to unreadMentionsCount
        )

        if (lastSeen) {
          contentValues.put(LAST_SEEN, if (sinceTimestamp == -1L) System.currentTimeMillis() else sinceTimestamp)
        }

        db.update(TABLE_NAME)
          .values(contentValues)
          .where("$ID = ?", threadId)
          .run()

        if (previous != null && previous.isForcedUnread) {
          recipients.markNeedsSync(previous.recipient.id)
          needsSync = true
        }
      }
    }

    notifyVerboseConversationListeners(threadIdToSinceTimestamp.keys)
    notifyConversationListListeners()

    if (needsSync) {
      StorageSyncHelper.scheduleSyncForDataChange()
    }

    return messageRecords
  }

  fun setForcedUnread(threadIds: Collection<Long>) {
    var recipientIds: List<RecipientId> = emptyList()

    writableDatabase.withinTransaction { db ->
      val query = SqlUtil.buildSingleCollectionQuery(ID, threadIds)
      val contentValues = contentValuesOf(READ to ReadStatus.FORCED_UNREAD.serialize())
      db.update(TABLE_NAME, contentValues, query.where, query.whereArgs)

      recipientIds = getRecipientIdsForThreadIds(threadIds)
      recipients.markNeedsSyncWithoutRefresh(recipientIds)
    }

    for (id in recipientIds) {
      Recipient.live(id).refresh()
    }

    StorageSyncHelper.scheduleSyncForDataChange()
    notifyConversationListListeners()
  }

  fun getUnreadThreadCount(): Long {
    return getUnreadThreadIdAggregate(SqlUtil.COUNT) { cursor ->
      if (cursor.moveToFirst()) {
        cursor.getLong(0)
      } else {
        0L
      }
    }
  }

  /**
   * Returns the number of unread messages across all threads.
   * Threads that are forced-unread count as 1.
   */
  fun getUnreadMessageCount(): Long {
    val allCount: Long = readableDatabase
      .select("SUM($UNREAD_COUNT)")
      .from(TABLE_NAME)
      .where("$ARCHIVED = ?", 0)
      .run()
      .use { cursor ->
        if (cursor.moveToFirst()) {
          cursor.getLong(0)
        } else {
          0
        }
      }

    val forcedUnreadCount: Long = readableDatabase
      .select("COUNT(*)")
      .from(TABLE_NAME)
      .where("$READ = ? AND $ARCHIVED = ?", ReadStatus.FORCED_UNREAD.serialize(), 0)
      .run()
      .use { cursor ->
        if (cursor.moveToFirst()) {
          cursor.getLong(0)
        } else {
          0
        }
      }

    return allCount + forcedUnreadCount
  }

  /**
   * Returns the number of unread messages in a given thread.
   */
  fun getUnreadMessageCount(threadId: Long): Long {
    return readableDatabase
      .select(UNREAD_COUNT)
      .from(TABLE_NAME)
      .where("$ID = ?", threadId)
      .run()
      .use { cursor ->
        if (cursor.moveToFirst()) {
          CursorUtil.requireLong(cursor, UNREAD_COUNT)
        } else {
          0L
        }
      }
  }

  fun getUnreadThreadIdList(): String? {
    return getUnreadThreadIdAggregate(arrayOf("GROUP_CONCAT($ID)")) { cursor ->
      if (cursor.moveToFirst()) {
        cursor.getString(0)
      } else {
        null
      }
    }
  }

  private fun <T> getUnreadThreadIdAggregate(aggregator: Array<String>, mapCursorToType: (Cursor) -> T): T {
    return readableDatabase
      .select(*aggregator)
      .from(TABLE_NAME)
      .where("$READ != ${ReadStatus.READ.serialize()} AND $ARCHIVED = 0 AND $MEANINGFUL_MESSAGES != 0")
      .run()
      .use(mapCursorToType)
  }

  fun incrementUnread(threadId: Long, unreadAmount: Int, unreadSelfMentionAmount: Int) {
    writableDatabase.execSQL(
      """
      UPDATE $TABLE_NAME 
      SET $READ = ${ReadStatus.UNREAD.serialize()}, 
          $UNREAD_COUNT = $UNREAD_COUNT + ?, 
          $UNREAD_SELF_MENTION_COUNT = $UNREAD_SELF_MENTION_COUNT + ?, 
          $LAST_SCROLLED = ? 
      WHERE $ID = ?
      """,
      SqlUtil.buildArgs(unreadAmount, unreadSelfMentionAmount, 0, threadId)
    )
  }

  fun setDistributionType(threadId: Long, distributionType: Int) {
    writableDatabase
      .update(TABLE_NAME)
      .values(TYPE to distributionType)
      .where("$ID = ?", threadId)
      .run()

    notifyConversationListListeners()
  }

  fun getDistributionType(threadId: Long): Int {
    return readableDatabase
      .select(TYPE)
      .from(TABLE_NAME)
      .where("$ID = ?", threadId)
      .run()
      .use { cursor ->
        if (cursor.moveToFirst()) {
          cursor.requireInt(TYPE)
        } else {
          DistributionTypes.DEFAULT
        }
      }
  }

  fun containsId(threadId: Long): Boolean {
    return readableDatabase
      .exists(TABLE_NAME)
      .where("$ID = ?", threadId)
      .run()
  }

  fun getFilteredConversationList(filter: List<RecipientId>, unreadOnly: Boolean): Cursor? {
    if (filter.isEmpty()) {
      return null
    }

    val db = databaseHelper.signalReadableDatabase
    val splitRecipientIds: List<List<RecipientId>> = filter.chunked(900)
    val cursors: MutableList<Cursor> = LinkedList()

    for (recipientIds in splitRecipientIds) {
      var selection = "($TABLE_NAME.$RECIPIENT_ID = ?"
      val selectionArgs = arrayOfNulls<String>(recipientIds.size)

      for (i in 0 until recipientIds.size - 1) {
        selection += " OR $TABLE_NAME.$RECIPIENT_ID = ?"
      }

      var i = 0
      for (recipientId in recipientIds) {
        selectionArgs[i] = recipientId.serialize()
        i++
      }

      selection += if (unreadOnly) {
        ") AND $TABLE_NAME.$READ != ${ReadStatus.READ.serialize()}"
      } else {
        ")"
      }

      val query = createQuery(selection, "$DATE DESC", 0, 0)
      cursors.add(db.rawQuery(query, selectionArgs))
    }

    return if (cursors.size > 1) {
      MergeCursor(cursors.toTypedArray())
    } else {
      cursors[0]
    }
  }

  fun getRecentConversationList(limit: Int, includeInactiveGroups: Boolean, hideV1Groups: Boolean): Cursor {
    return getRecentConversationList(
      limit = limit,
      includeInactiveGroups = includeInactiveGroups,
      individualsOnly = false,
      groupsOnly = false,
      hideV1Groups = hideV1Groups,
      hideSms = false,
      hideSelf = false
    )
  }

  fun getRecentConversationList(limit: Int, includeInactiveGroups: Boolean, individualsOnly: Boolean, groupsOnly: Boolean, hideV1Groups: Boolean, hideSms: Boolean, hideSelf: Boolean): Cursor {
    var where = ""

    if (!includeInactiveGroups) {
      where += "$MEANINGFUL_MESSAGES != 0 AND (${GroupTable.TABLE_NAME}.${GroupTable.ACTIVE} IS NULL OR ${GroupTable.TABLE_NAME}.${GroupTable.ACTIVE} = 1)"
    } else {
      where += "$MEANINGFUL_MESSAGES != 0"
    }

    if (groupsOnly) {
      where += " AND ${RecipientTable.TABLE_NAME}.${RecipientTable.GROUP_ID} NOT NULL"
    }

    if (individualsOnly) {
      where += " AND ${RecipientTable.TABLE_NAME}.${RecipientTable.GROUP_ID} IS NULL"
    }

    if (hideV1Groups) {
      where += " AND ${RecipientTable.TABLE_NAME}.${RecipientTable.TYPE} != ${RecipientTable.RecipientType.GV1.id}"
    }

    if (hideSms) {
      where += """ AND (
        ${RecipientTable.TABLE_NAME}.${RecipientTable.REGISTERED} = ${RecipientTable.RegisteredState.REGISTERED.id}
        OR 
        (
          ${RecipientTable.TABLE_NAME}.${RecipientTable.GROUP_ID} NOT NULL 
          AND ${RecipientTable.TABLE_NAME}.${RecipientTable.TYPE} != ${RecipientTable.RecipientType.MMS.id}
        ) 
      )"""
    }

    if (hideSelf) {
      where += " AND $TABLE_NAME.$RECIPIENT_ID != ${Recipient.self().id.toLong()}"
    }

    where += " AND $ARCHIVED = 0"
    where += " AND ${RecipientTable.TABLE_NAME}.${RecipientTable.BLOCKED} = 0"

    if (SignalStore.releaseChannel.releaseChannelRecipientId != null) {
      where += " AND $TABLE_NAME.$RECIPIENT_ID != ${SignalStore.releaseChannel.releaseChannelRecipientId!!.toLong()}"
    }

    val query = createQuery(
      where = where,
      offset = 0,
      limit = limit.toLong(),
      preferPinned = true
    )

    return readableDatabase.rawQuery(query, null)
  }

  fun getRecentPushConversationList(limit: Int, includeInactiveGroups: Boolean): Cursor {
    val activeGroupQuery = if (!includeInactiveGroups) " AND " + GroupTable.TABLE_NAME + "." + GroupTable.ACTIVE + " = 1" else ""
    val where = """
      $MEANINGFUL_MESSAGES != 0 
      AND (
        ${RecipientTable.REGISTERED} = ${RecipientTable.RegisteredState.REGISTERED.id} 
        OR (
          ${GroupTable.TABLE_NAME}.${GroupTable.GROUP_ID} NOT NULL 
          AND ${GroupTable.TABLE_NAME}.${GroupTable.MMS} = 0
          $activeGroupQuery
        )
      )
    """

    val query = createQuery(
      where = where,
      offset = 0,
      limit = limit.toLong(),
      preferPinned = true
    )

    return readableDatabase.rawQuery(query, null)
  }

  fun isArchived(recipientId: RecipientId): Boolean {
    return readableDatabase
      .select(ARCHIVED)
      .from(TABLE_NAME)
      .where("$RECIPIENT_ID = ?", recipientId)
      .run()
      .use { cursor ->
        if (cursor.moveToFirst()) {
          cursor.requireBoolean(ARCHIVED)
        } else {
          false
        }
      }
  }

  fun setArchived(threadIds: Set<Long>, archive: Boolean) {
    var recipientIds: List<RecipientId> = emptyList()

    writableDatabase.withinTransaction { db ->
      for (threadId in threadIds) {
        val values = ContentValues().apply {
          if (archive) {
            put(PINNED, "0")
            put(ARCHIVED, "1")
          } else {
            put(ARCHIVED, "0")
          }
        }

        db.update(TABLE_NAME)
          .values(values)
          .where("$ID = ?", threadId)
          .run()
      }

      recipientIds = getRecipientIdsForThreadIds(threadIds)
      recipients.markNeedsSyncWithoutRefresh(recipientIds)
    }

    for (id in recipientIds) {
      Recipient.live(id).refresh()
    }
    notifyConversationListListeners()
    StorageSyncHelper.scheduleSyncForDataChange()
  }

  fun getArchivedRecipients(): Set<RecipientId> {
    return getArchivedConversationList(ConversationFilter.OFF).readToList { cursor ->
      RecipientId.from(cursor.requireLong(RECIPIENT_ID))
    }.toSet()
  }

  fun getInboxPositions(): Map<RecipientId, Int> {
    val query = createQuery("$MEANINGFUL_MESSAGES != ?", 0)
    val positions: MutableMap<RecipientId, Int> = mutableMapOf()

    readableDatabase.rawQuery(query, arrayOf("0")).use { cursor ->
      var i = 0
      while (cursor != null && cursor.moveToNext()) {
        val recipientId = RecipientId.from(cursor.requireLong(RECIPIENT_ID))
        positions[recipientId] = i
        i++
      }
    }

    return positions
  }

  fun getArchivedConversationList(conversationFilter: ConversationFilter, offset: Long = 0, limit: Long = 0): Cursor {
    val filterQuery = conversationFilter.toQuery()
    val query = createQuery("$ARCHIVED = ? AND $MEANINGFUL_MESSAGES != 0 $filterQuery", offset, limit, preferPinned = false)
    return readableDatabase.rawQuery(query, arrayOf("1"))
  }

  fun getUnarchivedConversationList(conversationFilter: ConversationFilter, pinned: Boolean, offset: Long, limit: Long): Cursor {
    val filterQuery = conversationFilter.toQuery()
    val where = if (pinned) {
      "$ARCHIVED = 0 AND $PINNED != 0 $filterQuery"
    } else {
      "$ARCHIVED = 0 AND $PINNED = 0 AND $MEANINGFUL_MESSAGES != 0 $filterQuery"
    }

    val query = if (pinned) {
      createQuery(where, PINNED + " ASC", offset, limit)
    } else {
      createQuery(where, offset, limit, preferPinned = false)
    }

    return readableDatabase.rawQuery(query, null)
  }

  fun getArchivedConversationListCount(conversationFilter: ConversationFilter): Int {
    val filterQuery = conversationFilter.toQuery()
    return readableDatabase
      .select("COUNT(*)")
      .from(TABLE_NAME)
      .where("$ACTIVE = 1 AND $ARCHIVED = 1 AND $MEANINGFUL_MESSAGES != 0 $filterQuery")
      .run()
      .use { cursor ->
        if (cursor.moveToFirst()) {
          cursor.getInt(0)
        } else {
          0
        }
      }
  }

  fun getPinnedConversationListCount(conversationFilter: ConversationFilter): Int {
    val filterQuery = conversationFilter.toQuery()
    return readableDatabase
      .select("COUNT(*)")
      .from(TABLE_NAME)
      .where("$ACTIVE = 1 AND $ARCHIVED = 0 AND $PINNED != 0 $filterQuery")
      .run()
      .use { cursor ->
        if (cursor.moveToFirst()) {
          cursor.getInt(0)
        } else {
          0
        }
      }
  }

  fun getUnarchivedConversationListCount(conversationFilter: ConversationFilter): Int {
    val filterQuery = conversationFilter.toQuery()
    return readableDatabase
      .select("COUNT(*)")
      .from(TABLE_NAME)
      .where("$ACTIVE = 1 AND $ARCHIVED = 0 AND ($MEANINGFUL_MESSAGES != 0 OR $PINNED != 0) $filterQuery")
      .run()
      .use { cursor ->
        if (cursor.moveToFirst()) {
          cursor.getInt(0)
        } else {
          0
        }
      }
  }

  /**
   * @return Pinned recipients, in order from top to bottom.
   */
  fun getPinnedRecipientIds(): List<RecipientId> {
    return readableDatabase
      .select(ID, RECIPIENT_ID)
      .from(TABLE_NAME)
      .where("$PINNED > 0")
      .run()
      .readToList { cursor ->
        RecipientId.from(cursor.requireLong(RECIPIENT_ID))
      }
  }

  /**
   * @return Pinned thread ids, in order from top to bottom.
   */
  fun getPinnedThreadIds(): List<Long> {
    return readableDatabase
      .select(ID)
      .from(TABLE_NAME)
      .where("$PINNED > 0")
      .run()
      .readToList { cursor ->
        cursor.requireLong(ID)
      }
  }

  fun restorePins(threadIds: Collection<Long>) {
    Log.d(TAG, "Restoring pinned threads " + StringUtil.join(threadIds, ","))
    pinConversations(threadIds, true)
  }

  fun pinConversations(threadIds: Collection<Long>) {
    Log.d(TAG, "Pinning threads " + StringUtil.join(threadIds, ","))
    pinConversations(threadIds, false)
  }

  private fun pinConversations(threadIds: Collection<Long>, clearFirst: Boolean) {
    writableDatabase.withinTransaction { db ->
      if (clearFirst) {
        db.update(TABLE_NAME)
          .values(PINNED to 0)
          .where("$PINNED > 0")
          .run()
      }

      var pinnedCount = getPinnedConversationListCount(ConversationFilter.OFF)

      for (threadId in threadIds) {
        pinnedCount++
        db.update(TABLE_NAME)
          .values(PINNED to pinnedCount, ACTIVE to 1)
          .where("$ID = ?", threadId)
          .run()
      }
    }

    notifyConversationListListeners()
    recipients.markNeedsSync(Recipient.self().id)
    StorageSyncHelper.scheduleSyncForDataChange()
  }

  fun unpinConversations(threadIds: Collection<Long>) {
    writableDatabase.withinTransaction { db ->
      val query: SqlUtil.Query = SqlUtil.buildSingleCollectionQuery(ID, threadIds)
      db.update(TABLE_NAME)
        .values(PINNED to 0)
        .where(query.where, *query.whereArgs)
        .run()

      getPinnedThreadIds().forEachIndexed { index: Int, threadId: Long ->
        db.update(TABLE_NAME)
          .values(PINNED to index + 1)
          .where("$ID = ?", threadId)
          .run()
      }
    }

    notifyConversationListListeners()
    recipients.markNeedsSync(Recipient.self().id)
    StorageSyncHelper.scheduleSyncForDataChange()
  }

  fun archiveConversation(threadId: Long) {
    setArchived(setOf(threadId), archive = true)
  }

  fun unarchiveConversation(threadId: Long) {
    setArchived(setOf(threadId), archive = false)
  }

  fun setLastSeen(threadId: Long) {
    writableDatabase
      .update(TABLE_NAME)
      .values(LAST_SEEN to System.currentTimeMillis())
      .where("$ID = ?", threadId)
      .run()

    notifyConversationListListeners()
  }

  fun setLastScrolled(threadId: Long, lastScrolledTimestamp: Long) {
    writableDatabase
      .update(TABLE_NAME)
      .values(LAST_SCROLLED to lastScrolledTimestamp)
      .where("$ID = ?", threadId)
      .run()
  }

  fun getConversationMetadata(threadId: Long): ConversationMetadata {
    return readableDatabase
      .select(UNREAD_COUNT, LAST_SEEN, HAS_SENT, LAST_SCROLLED)
      .from(TABLE_NAME)
      .where("$ID = ?", threadId)
      .run()
      .use { cursor ->
        if (cursor.moveToFirst()) {
          ConversationMetadata(
            lastSeen = cursor.requireLong(LAST_SEEN),
            hasSent = cursor.requireBoolean(HAS_SENT),
            lastScrolled = cursor.requireLong(LAST_SCROLLED),
            unreadCount = cursor.requireInt(UNREAD_COUNT)
          )
        } else {
          ConversationMetadata(
            lastSeen = -1L,
            hasSent = false,
            lastScrolled = -1,
            unreadCount = 0
          )
        }
      }
  }

  fun deleteConversationIfContainsOnlyLocal(threadId: Long): Boolean {
    return writableDatabase.withinTransaction {
      val containsAddressable = messages.threadContainsAddressableMessages(threadId)
      val isEmpty = messages.threadIsEmpty(threadId)

      if (containsAddressable || isEmpty) {
        false
      } else {
        deleteConversation(threadId, syncThreadDelete = false)
        true
      }
    }
  }

  @JvmOverloads
  fun deleteConversation(threadId: Long, syncThreadDelete: Boolean = true) {
    deleteConversations(setOf(threadId), syncThreadDelete)
  }

  fun deleteConversations(selectedConversations: Set<Long>, syncThreadDeletes: Boolean = true) {
    val recipientIds = getRecipientIdsForThreadIds(selectedConversations)

    val addressableMessages = mutableListOf<ThreadDeleteSyncInfo>()

    val queries: List<SqlUtil.Query> = SqlUtil.buildCollectionQuery(ID, selectedConversations)
    writableDatabase.withinTransaction { db ->
      if (syncThreadDeletes && Recipient.self().deleteSyncCapability.isSupported) {
        for (threadId in selectedConversations) {
          val mostRecentMessages = messages.getMostRecentAddressableMessages(threadId, excludeExpiring = false)
          val mostRecentNonExpiring = if (mostRecentMessages.size == MessageTable.ADDRESSABLE_MESSAGE_LIMIT && mostRecentMessages.any { it.expiresIn > 0 }) {
            messages.getMostRecentAddressableMessages(threadId, excludeExpiring = true)
          } else {
            emptySet()
          }

          addressableMessages += ThreadDeleteSyncInfo(threadId, mostRecentMessages, mostRecentNonExpiring)
        }
      }

      for (query in queries) {
        db.deactivateThread(query)
      }

      messages.deleteAbandonedMessages()
      attachments.trimAllAbandonedAttachments()
      groupReceipts.deleteAbandonedRows()
      mentions.deleteAbandonedMentions()
      drafts.clearDrafts(selectedConversations)
      attachments.deleteAbandonedAttachmentFiles()
      synchronized(threadIdCache) {
        for (recipientId in recipientIds) {
          threadIdCache.remove(recipientId)
        }
      }
    }

    if (syncThreadDeletes) {
      MultiDeviceDeleteSyncJob.enqueueThreadDeletes(addressableMessages, isFullDelete = true)
    }

    notifyConversationListListeners()
    notifyConversationListeners(selectedConversations)
    notifyStickerListeners()
    notifyStickerPackListeners()
    AppDependencies.databaseObserver.notifyConversationDeleteListeners(selectedConversations)

    ConversationUtil.clearShortcuts(context, recipientIds)

    OptimizeMessageSearchIndexJob.enqueue()
  }

  @SuppressLint("DiscouragedApi")
  fun deleteAllConversations() {
    writableDatabase.withinTransaction { db ->
      messageLog.deleteAll()
      messages.deleteAllThreads()
      drafts.clearAllDrafts()
      db.deactivateThreads()
      calls.deleteAllCalls()
      synchronized(threadIdCache) {
        threadIdCache.clear()
      }
    }

    notifyConversationListListeners()
    ConversationUtil.clearAllShortcuts(context)
  }

  fun getThreadIdIfExistsFor(recipientId: RecipientId): Long {
    return getThreadIdFor(recipientId) ?: -1
  }

  fun getOrCreateValidThreadId(recipient: Recipient, candidateId: Long): Long {
    return getOrCreateValidThreadId(recipient, candidateId, DistributionTypes.DEFAULT)
  }

  fun getOrCreateValidThreadId(recipient: Recipient, candidateId: Long, distributionType: Int): Long {
    return if (candidateId != -1L) {
      if (areThreadIdAndRecipientAssociated(candidateId, recipient)) {
        candidateId
      } else {
        val remapped = RemappedRecords.getInstance().getThread(candidateId)
        if (remapped.isPresent) {
          if (areThreadIdAndRecipientAssociated(remapped.get(), recipient)) {
            Log.i(TAG, "Using remapped threadId: $candidateId -> ${remapped.get()}")
            remapped.get()
          } else {
            Log.i(TAG, "There's a remap for $candidateId -> ${remapped.get()}, but it's not associated with $recipient. Deleting old remap and throwing.")
            writableDatabase.withinTransaction {
              RemappedRecords.getInstance().deleteThread(candidateId)
            }
            throw IllegalArgumentException("Candidate threadId ($candidateId) is not associated with recipient ($recipient)")
          }
        } else {
          throw IllegalArgumentException("Candidate threadId ($candidateId) is not associated with recipient ($recipient)")
        }
      }
    } else {
      getOrCreateThreadIdFor(recipient, distributionType)
    }
  }

  fun getOrCreateThreadIdFor(recipient: Recipient): Long {
    return getOrCreateThreadIdFor(recipient, DistributionTypes.DEFAULT)
  }

  fun getOrCreateThreadIdFor(recipient: Recipient, distributionType: Int): Long {
    return getOrCreateThreadIdFor(recipient.id, recipient.isGroup, distributionType)
  }

  fun getOrCreateThreadIdFor(recipientId: RecipientId, isGroup: Boolean, distributionType: Int = DistributionTypes.DEFAULT): Long {
    return getOrCreateThreadIdResultFor(recipientId, isGroup, distributionType).threadId
  }

  fun getOrCreateThreadIdResultFor(recipientId: RecipientId, isGroup: Boolean, distributionType: Int = DistributionTypes.DEFAULT): ThreadIdResult {
    return writableDatabase.withinTransaction {
      val threadId = getThreadIdFor(recipientId)
      if (threadId != null) {
        ThreadIdResult(
          threadId = threadId,
          newlyCreated = false
        )
      } else {
        ThreadIdResult(
          threadId = createThreadForRecipient(recipientId, isGroup, distributionType),
          newlyCreated = true
        )
      }
    }
  }

  fun areThreadIdAndRecipientAssociated(threadId: Long, recipient: Recipient): Boolean {
    return readableDatabase
      .exists(TABLE_NAME)
      .where("$ID = ? AND $RECIPIENT_ID = ?", threadId, recipient.id)
      .run()
  }

  fun getThreadIdFor(recipientId: RecipientId): Long? {
    var threadId: Long? = synchronized(threadIdCache) {
      threadIdCache[recipientId]
    }
    if (threadId == null) {
      threadId = readableDatabase
        .select(ID)
        .from(TABLE_NAME)
        .where("$RECIPIENT_ID = ?", recipientId)
        .run()
        .use { cursor ->
          if (cursor.moveToFirst()) {
            cursor.requireLong(ID)
          } else {
            null
          }
        }
      if (threadId != null) {
        synchronized(threadIdCache) {
          threadIdCache[recipientId] = threadId
        }
      }
    }
    return threadId
  }

  fun getRecipientIdForThreadId(threadId: Long): RecipientId? {
    return readableDatabase
      .select(RECIPIENT_ID)
      .from(TABLE_NAME)
      .where("$ID = ?", threadId)
      .run()
      .use { cursor ->
        if (cursor.moveToFirst()) {
          return RecipientId.from(cursor.requireLong(RECIPIENT_ID))
        } else {
          null
        }
      }
  }

  fun getRecipientForThreadId(threadId: Long): Recipient? {
    val id: RecipientId = getRecipientIdForThreadId(threadId) ?: return null
    return Recipient.resolved(id)
  }

  fun getRecipientIdsForThreadIds(threadIds: Collection<Long>): List<RecipientId> {
    if (threadIds.isEmpty()) {
      return emptyList()
    }

    val query = SqlUtil.buildSingleCollectionQuery(ID, threadIds)

    return readableDatabase
      .select(RECIPIENT_ID)
      .from(TABLE_NAME)
      .where(query.where, *query.whereArgs)
      .run()
      .readToList { cursor ->
        RecipientId.from(cursor.requireLong(RECIPIENT_ID))
      }
  }

  fun hasThread(recipientId: RecipientId): Boolean {
    return getThreadIdIfExistsFor(recipientId) > -1
  }

  fun hasActiveThread(recipientId: RecipientId): Boolean {
    return readableDatabase
      .exists(TABLE_NAME)
      .where("$RECIPIENT_ID = ? AND $ACTIVE = 1", recipientId)
      .run()
  }

  fun updateLastSeenAndMarkSentAndLastScrolledSilenty(threadId: Long) {
    writableDatabase
      .update(TABLE_NAME)
      .values(
        LAST_SEEN to System.currentTimeMillis(),
        HAS_SENT to 1,
        LAST_SCROLLED to 0
      )
      .where("$ID = ?", threadId)
      .run()
  }

  fun updateReadState(threadId: Long) {
    val previous = getThreadRecord(threadId)
    val unreadCount = messages.getUnreadCount(threadId)
    val unreadMentionsCount = messages.getUnreadMentionCount(threadId)

    writableDatabase
      .update(TABLE_NAME)
      .values(
        READ to if (unreadCount == 0) ReadStatus.READ.serialize() else ReadStatus.UNREAD.serialize(),
        UNREAD_COUNT to unreadCount,
        UNREAD_SELF_MENTION_COUNT to unreadMentionsCount
      )
      .where("$ID = ?", threadId)
      .run()

    notifyConversationListListeners()

    if (previous != null && previous.isForcedUnread) {
      recipients.markNeedsSync(previous.recipient.id)
      StorageSyncHelper.scheduleSyncForDataChange()
    }
  }

  fun applyStorageSyncUpdate(recipientId: RecipientId, record: SignalContactRecord) {
    applyStorageSyncUpdate(recipientId, record.isArchived, record.isForcedUnread)
  }

  fun applyStorageSyncUpdate(recipientId: RecipientId, record: SignalGroupV1Record) {
    applyStorageSyncUpdate(recipientId, record.isArchived, record.isForcedUnread)
  }

  fun applyStorageSyncUpdate(recipientId: RecipientId, record: SignalGroupV2Record) {
    applyStorageSyncUpdate(recipientId, record.isArchived, record.isForcedUnread)
  }

  fun applyStorageSyncUpdate(recipientId: RecipientId, record: SignalAccountRecord) {
    writableDatabase.withinTransaction { db ->
      applyStorageSyncUpdate(recipientId, record.isNoteToSelfArchived, record.isNoteToSelfForcedUnread)

      db.updateAll(TABLE_NAME)
        .values(PINNED to 0)
        .run()

      var pinnedPosition = 1

      for (pinned: PinnedConversation in record.pinnedConversations) {
        val pinnedRecipient: Recipient? = if (pinned.contact.isPresent) {
          Recipient.externalPush(pinned.contact.get())
        } else if (pinned.groupV1Id.isPresent) {
          try {
            Recipient.externalGroupExact(GroupId.v1(pinned.groupV1Id.get()))
          } catch (e: BadGroupIdException) {
            Log.w(TAG, "Failed to parse pinned groupV1 ID!", e)
            null
          }
        } else if (pinned.groupV2MasterKey.isPresent) {
          try {
            Recipient.externalGroupExact(GroupId.v2(GroupMasterKey(pinned.groupV2MasterKey.get())))
          } catch (e: InvalidInputException) {
            Log.w(TAG, "Failed to parse pinned groupV2 master key!", e)
            null
          }
        } else {
          Log.w(TAG, "Empty pinned conversation on the AccountRecord?")
          null
        }

        if (pinnedRecipient != null) {
          db.update(TABLE_NAME)
            .values(PINNED to pinnedPosition, ACTIVE to 1)
            .where("$RECIPIENT_ID = ?", pinnedRecipient.id)
            .run()
        }

        pinnedPosition++
      }
    }

    notifyConversationListListeners()
  }

  private fun applyStorageSyncUpdate(recipientId: RecipientId, archived: Boolean, forcedUnread: Boolean) {
    val values = ContentValues()
    values.put(ARCHIVED, if (archived) 1 else 0)

    val threadId: Long? = getThreadIdFor(recipientId)

    if (forcedUnread) {
      values.put(READ, ReadStatus.FORCED_UNREAD.serialize())
    } else if (threadId != null) {
      val unreadCount = messages.getUnreadCount(threadId)
      val unreadMentionsCount = messages.getUnreadMentionCount(threadId)

      values.put(READ, if (unreadCount == 0) ReadStatus.READ.serialize() else ReadStatus.UNREAD.serialize())
      values.put(UNREAD_COUNT, unreadCount)
      values.put(UNREAD_SELF_MENTION_COUNT, unreadMentionsCount)
    }

    writableDatabase
      .update(TABLE_NAME)
      .values(values)
      .where("$RECIPIENT_ID = ?", recipientId)
      .run()

    if (threadId != null) {
      notifyConversationListeners(threadId)
    }
  }

  /**
   * Set a thread as active prior to an [update] call. Useful when a thread is for sure active but
   * hasn't had the update call yet. e.g., inserting a message in a new thread.
   */
  fun markAsActiveEarly(threadId: Long) {
    writableDatabase
      .update(TABLE_NAME)
      .values(ACTIVE to 1)
      .where("$ID = ?", threadId)
      .run()
  }

  fun update(threadId: Long, unarchive: Boolean, syncThreadDelete: Boolean = true): Boolean {
    return update(
      threadId = threadId,
      unarchive = unarchive,
      allowDeletion = true,
      notifyListeners = true,
      syncThreadDelete = syncThreadDelete
    )
  }

  fun updateSilently(threadId: Long, unarchive: Boolean): Boolean {
    return update(
      threadId = threadId,
      unarchive = unarchive,
      allowDeletion = true,
      notifyListeners = false,
      syncThreadDelete = true
    )
  }

  fun update(threadId: Long, unarchive: Boolean, allowDeletion: Boolean, syncThreadDelete: Boolean = true): Boolean {
    return update(
      threadId = threadId,
      unarchive = unarchive,
      allowDeletion = allowDeletion,
      notifyListeners = true,
      syncThreadDelete = syncThreadDelete
    )
  }

  /**
   * Updates the thread with the receipt status of the message provided, but only if that message is the most recent meaningful message.
   * The idea here is that if it _is_ the most meaningful message, we can set the new status. If it's not, there's no need to update
   * the thread at all.
   */
  fun updateReceiptStatus(messageId: Long, threadId: Long, stopwatch: Stopwatch? = null) {
    val status = messages.getReceiptStatusIfItsTheMostRecentMeaningfulMessage(messageId, threadId)
    stopwatch?.split("thread-query")

    if (status != null) {
      Log.d(TAG, "Updating receipt status for thread $threadId")
      writableDatabase
        .update(TABLE_NAME)
        .values(
          HAS_DELIVERY_RECEIPT to status.hasDeliveryReceipt.toInt(),
          HAS_READ_RECEIPT to status.hasReadReceipt.toInt(),
          STATUS to when {
            MessageTypes.isFailedMessageType(status.type) -> MessageTable.Status.STATUS_FAILED
            MessageTypes.isSentType(status.type) -> MessageTable.Status.STATUS_COMPLETE
            MessageTypes.isPendingMessageType(status.type) -> MessageTable.Status.STATUS_PENDING
            else -> MessageTable.Status.STATUS_NONE
          }
        )
        .where("$ID = ?", threadId)
        .run()
    } else {
      Log.d(TAG, "Receipt was for an old message, not updating thread.")
    }
    stopwatch?.split("thread-update")
  }

  private fun update(threadId: Long, unarchive: Boolean, allowDeletion: Boolean, notifyListeners: Boolean, syncThreadDelete: Boolean): Boolean {
    if (threadId == -1L) {
      Log.d(TAG, "Skipping update for threadId -1")
      return false
    }

    return writableDatabase.withinTransaction {
      val meaningfulMessages = messages.hasMeaningfulMessage(threadId)

      val isPinned by lazy { getPinnedThreadIds().contains(threadId) }
      val shouldDelete by lazy { allowDeletion && !isPinned && !messages.containsStories(threadId) }

      if (!meaningfulMessages) {
        if (shouldDelete) {
          Log.d(TAG, "Deleting thread $threadId because it has no meaningful messages.")
          deleteConversation(threadId, syncThreadDelete = syncThreadDelete)
          return@withinTransaction true
        } else if (!isPinned) {
          return@withinTransaction false
        }
      }

      val record: MessageRecord? = try {
        messages.getConversationSnippet(threadId)
      } catch (e: NoSuchMessageException) {
        val scheduledMessage: MessageRecord? = messages.getScheduledMessagesInThread(threadId).lastOrNull()

        if (scheduledMessage != null) {
          Log.i(TAG, "Using scheduled message for conversation snippet")
        }
        scheduledMessage
      }

      if (record == null) {
        Log.w(TAG, "Failed to get a conversation snippet for thread $threadId")
        if (shouldDelete) {
          deleteConversation(threadId)
        } else if (isPinned) {
          updateThread(
            threadId = threadId,
            meaningfulMessages = meaningfulMessages,
            body = null,
            attachment = null,
            contentType = null,
            extra = null,
            date = 0,
            status = 0,
            deliveryReceiptCount = 0,
            type = 0,
            unarchive = unarchive,
            expiresIn = 0,
            readReceiptCount = 0,
            unreadCount = 0,
            unreadMentionCount = 0,
            messageExtras = null
          )
        }
        return@withinTransaction true
      }

      if (hasMoreRecentDraft(threadId, record.timestamp)) {
        return@withinTransaction false
      }

      val threadBody: ThreadBody = ThreadBodyUtil.getFormattedBodyFor(context, record)
      val unreadCount: Int = messages.getUnreadCount(threadId)
      val unreadMentionCount: Int = messages.getUnreadMentionCount(threadId)

      updateThread(
        threadId = threadId,
        meaningfulMessages = meaningfulMessages,
        body = threadBody.body.toString(),
        attachment = getAttachmentUriFor(record),
        contentType = getContentTypeFor(record),
        extra = getExtrasFor(record, threadBody),
        date = record.timestamp,
        status = record.deliveryStatus,
        deliveryReceiptCount = record.hasDeliveryReceipt().toInt(),
        type = record.type,
        unarchive = unarchive,
        expiresIn = record.expiresIn,
        readReceiptCount = record.hasReadReceipt().toInt(),
        unreadCount = unreadCount,
        unreadMentionCount = unreadMentionCount,
        messageExtras = record.messageExtras
      )

      if (notifyListeners) {
        notifyConversationListListeners()
      }
      return@withinTransaction false
    }
  }

  private fun hasMoreRecentDraft(threadId: Long, timestamp: Long): Boolean {
    val drafts: DraftTable.Drafts = SignalDatabase.drafts.getDrafts(threadId)
    if (drafts.isNotEmpty()) {
      val threadRecord: ThreadRecord? = getThreadRecord(threadId)
      if (threadRecord != null &&
        threadRecord.type == MessageTypes.BASE_DRAFT_TYPE &&
        threadRecord.date > timestamp
      ) {
        return true
      }
    }
    return false
  }

  fun updateSnippetTypeSilently(threadId: Long) {
    if (threadId == -1L) {
      return
    }

    val type: Long = try {
      messages.getConversationSnippetType(threadId)
    } catch (e: NoSuchMessageException) {
      Log.w(TAG, "Unable to find snippet message for thread $threadId")
      return
    }

    writableDatabase
      .update(TABLE_NAME)
      .values(SNIPPET_TYPE to type)
      .where("$ID = ?", threadId)
      .run()
  }

  fun getThreadRecordFor(recipient: Recipient): ThreadRecord {
    return getThreadRecord(getOrCreateThreadIdFor(recipient))!!
  }

  fun getAllThreadRecipients(): Set<RecipientId> {
    return readableDatabase
      .select(RECIPIENT_ID)
      .from(TABLE_NAME)
      .run()
      .readToList { cursor ->
        RecipientId.from(cursor.requireLong(RECIPIENT_ID))
      }
      .toSet()
  }

  fun merge(primaryRecipientId: RecipientId, secondaryRecipientId: RecipientId): MergeResult {
    check(databaseHelper.signalWritableDatabase.inTransaction()) { "Must be in a transaction!" }
    Log.w(TAG, "Merging threads. Primary: $primaryRecipientId, Secondary: $secondaryRecipientId", true)

    val primaryThreadId: Long? = getThreadIdFor(primaryRecipientId)
    val secondaryThreadId: Long? = getThreadIdFor(secondaryRecipientId)

    return if (primaryThreadId != null && secondaryThreadId == null) {
      Log.w(TAG, "[merge] Only had a thread for primary. Returning that.", true)
      MergeResult(threadId = primaryThreadId, previousThreadId = -1, neededMerge = false)
    } else if (primaryThreadId == null && secondaryThreadId != null) {
      Log.w(TAG, "[merge] Only had a thread for secondary. Updating it to have the recipientId of the primary.", true)
      writableDatabase
        .update(TABLE_NAME)
        .values(RECIPIENT_ID to primaryRecipientId.serialize())
        .where("$ID = ?", secondaryThreadId)
        .run()
      synchronized(threadIdCache) {
        threadIdCache.remove(secondaryRecipientId)
      }
      MergeResult(threadId = secondaryThreadId, previousThreadId = -1, neededMerge = false)
    } else if (primaryThreadId == null && secondaryThreadId == null) {
      Log.w(TAG, "[merge] No thread for either.")
      MergeResult(threadId = -1, previousThreadId = -1, neededMerge = false)
    } else {
      Log.w(TAG, "[merge] Had a thread for both. Deleting the secondary and merging the attributes together.", true)
      check(primaryThreadId != null)
      check(secondaryThreadId != null)

      for (table in threadIdDatabaseTables) {
        table.remapThread(secondaryThreadId, primaryThreadId)
      }

      writableDatabase
        .delete(TABLE_NAME)
        .where("$ID = ?", secondaryThreadId)
        .run()

      synchronized(threadIdCache) {
        threadIdCache.remove(secondaryRecipientId)
      }

      val primaryExpiresIn = getExpiresIn(primaryThreadId)
      val secondaryExpiresIn = getExpiresIn(secondaryThreadId)

      val values = ContentValues()
      values.put(ACTIVE, true)

      if (primaryExpiresIn != secondaryExpiresIn) {
        if (primaryExpiresIn == 0L) {
          values.put(EXPIRES_IN, secondaryExpiresIn)
        } else if (secondaryExpiresIn == 0L) {
          values.put(EXPIRES_IN, primaryExpiresIn)
        } else {
          values.put(EXPIRES_IN, min(primaryExpiresIn, secondaryExpiresIn))
        }
      }

      writableDatabase
        .update(TABLE_NAME)
        .values(values)
        .where("$ID = ?", primaryThreadId)
        .run()

      RemappedRecords.getInstance().addThread(secondaryThreadId, primaryThreadId)

      MergeResult(threadId = primaryThreadId, previousThreadId = secondaryThreadId, neededMerge = true)
    }
  }

  fun getThreadRecord(threadId: Long?): ThreadRecord? {
    if (threadId == null) {
      return null
    }

    val query = createQuery("$TABLE_NAME.$ID = ?", 1)

    return readableDatabase.rawQuery(query, SqlUtil.buildArgs(threadId)).use { cursor ->
      if (cursor.moveToFirst()) {
        readerFor(cursor).getCurrent()
      } else {
        null
      }
    }
  }

  private fun getExpiresIn(threadId: Long): Long {
    return readableDatabase
      .select(EXPIRES_IN)
      .from(TABLE_NAME)
      .where("$ID = $threadId")
      .run()
      .readToSingleLong()
  }

  private fun SQLiteDatabase.deactivateThreads() {
    deactivateThread(query = null)
  }

  private fun SQLiteDatabase.deactivateThread(query: SqlUtil.Query?) {
    val contentValues = contentValuesOf(
      DATE to 0,
      MEANINGFUL_MESSAGES to 0,
      READ to ReadStatus.READ.serialize(),
      TYPE to 0,
      ERROR to 0,
      SNIPPET to null,
      SNIPPET_TYPE to 0,
      SNIPPET_URI to null,
      SNIPPET_CONTENT_TYPE to null,
      SNIPPET_EXTRAS to null,
      SNIPPET_MESSAGE_EXTRAS to null,
      UNREAD_COUNT to 0,
      ARCHIVED to 0,
      STATUS to 0,
      HAS_DELIVERY_RECEIPT to 0,
      HAS_READ_RECEIPT to 0,
      EXPIRES_IN to 0,
      LAST_SEEN to 0,
      HAS_SENT to 0,
      LAST_SCROLLED to 0,
      PINNED to 0,
      UNREAD_SELF_MENTION_COUNT to 0,
      ACTIVE to 0
    )

    if (query != null) {
      this
        .update(TABLE_NAME)
        .values(contentValues)
        .where(query.where, query.whereArgs)
        .run()
    } else {
      this
        .updateAll(TABLE_NAME)
        .values(contentValues)
        .run()
    }
  }

  private fun getAttachmentUriFor(record: MessageRecord): Uri? {
    if (!record.isMms || record.isMmsNotification || record.isGroupAction) {
      return null
    }

    val slideDeck: SlideDeck = (record as MmsMessageRecord).slideDeck
    val thumbnail = Optional.ofNullable(slideDeck.thumbnailSlide)
      .or(Optional.ofNullable(slideDeck.stickerSlide))
      .orElse(null)

    return if (thumbnail != null && !(record as MmsMessageRecord).isViewOnce) {
      thumbnail.uri
    } else {
      null
    }
  }

  private fun getContentTypeFor(record: MessageRecord): String? {
    if (record.isMms) {
      val slideDeck = (record as MmsMessageRecord).slideDeck
      if (slideDeck.slides.isNotEmpty()) {
        return slideDeck.slides[0].contentType
      }
    }
    return null
  }

  private fun getExtrasFor(record: MessageRecord, body: ThreadBody): Extra? {
    val threadRecipient = getRecipientForThreadId(record.threadId)
    val messageRequestAccepted = RecipientUtil.isMessageRequestAccepted(record.threadId, threadRecipient)
    val isHidden = threadRecipient?.isHidden ?: false
    val authorId = record.fromRecipient.id

    if (!messageRequestAccepted && threadRecipient != null) {
      if (threadRecipient.isPushGroup) {
        if (threadRecipient.isPushV2Group) {
          val inviteAddState = record.gv2AddInviteState
          if (inviteAddState != null) {
            val from = RecipientId.from(inviteAddState.addedOrInvitedBy)
            return if (inviteAddState.isInvited) {
              Log.i(TAG, "GV2 invite message request from $from")
              Extra.forGroupV2invite(from, authorId)
            } else {
              Log.i(TAG, "GV2 message request from $from")
              Extra.forGroupMessageRequest(from, authorId)
            }
          }

          Log.w(TAG, "Falling back to unknown message request state for GV2 message")
          return Extra.forMessageRequest(authorId)
        } else {
          val recipientId = messages.getGroupAddedBy(record.threadId)
          if (recipientId != null) {
            return Extra.forGroupMessageRequest(recipientId, authorId)
          }
        }
      } else {
        return Extra.forMessageRequest(authorId, isHidden)
      }
    }

    val extras: Extra? = if (record.isScheduled()) {
      Extra.forScheduledMessage(authorId)
    } else if (record.isRemoteDelete) {
      Extra.forRemoteDelete(authorId)
    } else if (record.isViewOnce) {
      Extra.forViewOnce(authorId)
    } else if (record.isMms && (record as MmsMessageRecord).slideDeck.stickerSlide != null) {
      val slide: StickerSlide = record.slideDeck.stickerSlide!!
      Extra.forSticker(slide.emoji, authorId)
    } else if (record.isMms && (record as MmsMessageRecord).slideDeck.slides.size > 1) {
      Extra.forAlbum(authorId)
    } else if (threadRecipient != null && threadRecipient.isGroup) {
      Extra.forDefault(authorId)
    } else {
      null
    }

    return if (record.messageRanges != null) {
      val bodyRanges = record.requireMessageRanges().adjustBodyRanges(body.bodyAdjustments)!!
      extras?.copy(bodyRanges = bodyRanges.serialize()) ?: Extra.forBodyRanges(bodyRanges, authorId)
    } else {
      extras
    }
  }

  private fun createQuery(where: String, limit: Long): String {
    return createQuery(
      where = where,
      offset = 0,
      limit = limit,
      preferPinned = false
    )
  }

  private fun createQuery(where: String, offset: Long, limit: Long, preferPinned: Boolean): String {
    val orderBy = if (preferPinned) {
      "$TABLE_NAME.$PINNED DESC, $TABLE_NAME.$DATE DESC"
    } else {
      "$TABLE_NAME.$DATE DESC"
    }

    return createQuery(
      where = where,
      orderBy = orderBy,
      offset = offset,
      limit = limit
    )
  }

  fun clearCache() {
    threadIdCache.clear()
  }

  private fun createQuery(where: String, orderBy: String, offset: Long, limit: Long): String {
    val projection = COMBINED_THREAD_RECIPIENT_GROUP_PROJECTION.joinToString(separator = ",")

    //language=sql
    var query = """
      SELECT $projection, ${GroupTable.MEMBER_GROUP_CONCAT}
      FROM $TABLE_NAME 
        LEFT OUTER JOIN ${RecipientTable.TABLE_NAME} ON $TABLE_NAME.$RECIPIENT_ID = ${RecipientTable.TABLE_NAME}.${RecipientTable.ID} 
        LEFT OUTER JOIN ${GroupTable.TABLE_NAME} ON $TABLE_NAME.$RECIPIENT_ID = ${GroupTable.TABLE_NAME}.${GroupTable.RECIPIENT_ID}
        LEFT OUTER JOIN (
          SELECT group_id, GROUP_CONCAT(${GroupTable.MembershipTable.TABLE_NAME}.${GroupTable.MembershipTable.RECIPIENT_ID}) as ${GroupTable.MEMBER_GROUP_CONCAT} 
          FROM ${GroupTable.MembershipTable.TABLE_NAME}
        ) as MembershipAlias ON MembershipAlias.${GroupTable.MembershipTable.GROUP_ID} = ${GroupTable.TABLE_NAME}.${GroupTable.GROUP_ID}
      WHERE $TABLE_NAME.$ACTIVE = 1 AND $where
      ORDER BY $orderBy
    """

    if (limit > 0) {
      query += " LIMIT $limit"
    }

    if (offset > 0) {
      query += " OFFSET $offset"
    }

    return query
  }

  private fun isSilentType(type: Long): Boolean {
    return MessageTypes.isProfileChange(type) ||
      MessageTypes.isGroupV1MigrationEvent(type) ||
      MessageTypes.isChangeNumber(type) ||
      MessageTypes.isReleaseChannelDonationRequest(type) ||
      MessageTypes.isGroupV2LeaveOnly(type) ||
      MessageTypes.isThreadMergeType(type)
  }

  fun readerFor(cursor: Cursor): Reader {
    return Reader(cursor)
  }

  private fun ConversationFilter.toQuery(): String {
    return when (this) {
      ConversationFilter.OFF -> ""
      //language=sql
      ConversationFilter.UNREAD -> " AND ($UNREAD_COUNT > 0 OR $READ == ${ReadStatus.FORCED_UNREAD.serialize()})"
      ConversationFilter.MUTED -> error("This filter selection isn't supported yet.")
      ConversationFilter.GROUPS -> error("This filter selection isn't supported yet.")
    }
  }

  object DistributionTypes {
    const val DEFAULT = 2
    const val BROADCAST = 1
    const val CONVERSATION = 2
    const val ARCHIVE = 3
    const val INBOX_ZERO = 4
  }

  inner class Reader(cursor: Cursor) : StaticReader(cursor, context)

  open class StaticReader(private val cursor: Cursor, private val context: Context) : Closeable {
    fun getNext(): ThreadRecord? {
      return if (!cursor.moveToNext()) {
        null
      } else {
        getCurrent()
      }
    }

    open fun getCurrent(): ThreadRecord? {
      val recipientId = RecipientId.from(cursor.requireLong(RECIPIENT_ID))
      val recipientSettings = RecipientTableCursorUtil.getRecord(context, cursor, RECIPIENT_ID)

      val recipient: Recipient = if (recipientSettings.groupId != null) {
        GroupTable.Reader(cursor).getCurrent()?.let { group ->
          RecipientCreator.forGroup(
            groupRecord = group,
            recipientRecord = recipientSettings,
            resolved = false
          )
        } ?: Recipient.live(recipientId).get()
      } else {
        RecipientCreator.forIndividual(context, recipientSettings)
      }

      val hasReadReceipt = TextSecurePreferences.isReadReceiptsEnabled(context) && cursor.requireBoolean(HAS_READ_RECEIPT)
      val extraString = cursor.getString(cursor.getColumnIndexOrThrow(SNIPPET_EXTRAS))
      val messageExtraBytes = cursor.getBlob(cursor.getColumnIndexOrThrow(SNIPPET_MESSAGE_EXTRAS))
      val messageExtras = if (messageExtraBytes != null) MessageExtras.ADAPTER.decode(messageExtraBytes) else null
      val extra: Extra? = if (extraString != null) {
        try {
          val jsonObject = SaneJSONObject(JSONObject(extraString))
          Extra(
            isViewOnce = jsonObject.getBoolean("isRevealable"),
            isSticker = jsonObject.getBoolean("isSticker"),
            stickerEmoji = jsonObject.getString("stickerEmoji"),
            isAlbum = jsonObject.getBoolean("isAlbum"),
            isRemoteDelete = jsonObject.getBoolean("isRemoteDelete"),
            isMessageRequestAccepted = jsonObject.getBoolean("isMessageRequestAccepted"),
            isGv2Invite = jsonObject.getBoolean("isGv2Invite"),
            groupAddedBy = jsonObject.getString("groupAddedBy"),
            individualRecipientId = jsonObject.getString("individualRecipientId")!!,
            bodyRanges = jsonObject.getString("bodyRanges"),
            isScheduled = jsonObject.getBoolean("isScheduled"),
            isRecipientHidden = jsonObject.getBoolean("isRecipientHidden")
          )
        } catch (exception: Exception) {
          null
        }
      } else {
        null
      }

      return ThreadRecord.Builder(cursor.requireLong(ID))
        .setRecipient(recipient)
        .setType(cursor.requireInt(SNIPPET_TYPE).toLong())
        .setDistributionType(cursor.requireInt(TYPE))
        .setBody(cursor.requireString(SNIPPET) ?: "")
        .setDate(cursor.requireLong(DATE))
        .setArchived(cursor.requireBoolean(ARCHIVED))
        .setDeliveryStatus(cursor.requireInt(STATUS).toLong())
        .setHasDeliveryReceipt(cursor.requireBoolean(HAS_DELIVERY_RECEIPT))
        .setHasReadReceipt(hasReadReceipt)
        .setExpiresIn(cursor.requireLong(EXPIRES_IN))
        .setLastSeen(cursor.requireLong(LAST_SEEN))
        .setSnippetUri(getSnippetUri(cursor))
        .setContentType(cursor.requireString(SNIPPET_CONTENT_TYPE))
        .setMeaningfulMessages(cursor.requireLong(MEANINGFUL_MESSAGES) > 0)
        .setUnreadCount(cursor.requireInt(UNREAD_COUNT))
        .setForcedUnread(cursor.requireInt(READ) == ReadStatus.FORCED_UNREAD.serialize())
        .setPinned(cursor.requireBoolean(PINNED))
        .setUnreadSelfMentionsCount(cursor.requireInt(UNREAD_SELF_MENTION_COUNT))
        .setExtra(extra)
        .setSnippetMessageExtras(messageExtras)
        .build()
    }

    private fun getSnippetUri(cursor: Cursor?): Uri? {
      return if (cursor!!.isNull(cursor.getColumnIndexOrThrow(SNIPPET_URI))) {
        null
      } else {
        try {
          Uri.parse(cursor.getString(cursor.getColumnIndexOrThrow(SNIPPET_URI)))
        } catch (e: IllegalArgumentException) {
          Log.w(TAG, e)
          null
        }
      }
    }

    override fun close() {
      cursor.close()
    }
  }

  data class Extra(
    @field:JsonProperty
    @param:JsonProperty("isRevealable")
    val isViewOnce: Boolean = false,
    @field:JsonProperty
    @param:JsonProperty("isSticker")
    val isSticker: Boolean = false,
    @field:JsonProperty
    @param:JsonProperty("stickerEmoji")
    val stickerEmoji: String? = null,
    @field:JsonProperty
    @param:JsonProperty("isAlbum")
    val isAlbum: Boolean = false,
    @field:JsonProperty
    @param:JsonProperty("isRemoteDelete")
    val isRemoteDelete: Boolean = false,
    @field:JsonProperty
    @param:JsonProperty("isMessageRequestAccepted")
    val isMessageRequestAccepted: Boolean = true,
    @field:JsonProperty
    @param:JsonProperty("isGv2Invite")
    val isGv2Invite: Boolean = false,
    @field:JsonProperty
    @param:JsonProperty("groupAddedBy")
    val groupAddedBy: String? = null,
    @field:JsonProperty
    @param:JsonProperty("individualRecipientId")
    private val individualRecipientId: String,
    @field:JsonProperty
    @param:JsonProperty("bodyRanges")
    val bodyRanges: String? = null,
    @field:JsonProperty
    @param:JsonProperty("isScheduled")
    val isScheduled: Boolean = false,
    @field:JsonProperty
    @param:JsonProperty("isRecipientHidden")
    val isRecipientHidden: Boolean = false
  ) {

    fun getIndividualRecipientId(): String {
      return individualRecipientId
    }

    companion object {
      fun forViewOnce(individualRecipient: RecipientId): Extra {
        return Extra(isViewOnce = true, individualRecipientId = individualRecipient.serialize())
      }

      fun forSticker(emoji: String?, individualRecipient: RecipientId): Extra {
        return Extra(isSticker = true, stickerEmoji = emoji, individualRecipientId = individualRecipient.serialize())
      }

      fun forAlbum(individualRecipient: RecipientId): Extra {
        return Extra(isAlbum = true, individualRecipientId = individualRecipient.serialize())
      }

      fun forRemoteDelete(individualRecipient: RecipientId): Extra {
        return Extra(isRemoteDelete = true, individualRecipientId = individualRecipient.serialize())
      }

      fun forMessageRequest(individualRecipient: RecipientId, isHidden: Boolean = false): Extra {
        return Extra(isMessageRequestAccepted = false, individualRecipientId = individualRecipient.serialize(), isRecipientHidden = isHidden)
      }

      fun forGroupMessageRequest(recipientId: RecipientId, individualRecipient: RecipientId): Extra {
        return Extra(isMessageRequestAccepted = false, groupAddedBy = recipientId.serialize(), individualRecipientId = individualRecipient.serialize())
      }

      fun forGroupV2invite(recipientId: RecipientId, individualRecipient: RecipientId): Extra {
        return Extra(isGv2Invite = true, groupAddedBy = recipientId.serialize(), individualRecipientId = individualRecipient.serialize())
      }

      fun forDefault(individualRecipient: RecipientId): Extra {
        return Extra(individualRecipientId = individualRecipient.serialize())
      }

      fun forBodyRanges(bodyRanges: BodyRangeList, individualRecipient: RecipientId): Extra {
        return Extra(individualRecipientId = individualRecipient.serialize(), bodyRanges = bodyRanges.serialize())
      }

      fun forScheduledMessage(individualRecipient: RecipientId): Extra {
        return Extra(individualRecipientId = individualRecipient.serialize(), isScheduled = true)
      }
    }
  }

  internal enum class ReadStatus(private val value: Int) {
    READ(1),
    UNREAD(0),
    FORCED_UNREAD(2);

    fun serialize(): Int {
      return value
    }

    companion object {
      fun deserialize(value: Int): ReadStatus {
        for (status in values()) {
          if (status.value == value) {
            return status
          }
        }
        throw IllegalArgumentException("No matching status for value $value")
      }
    }
  }

  data class ConversationMetadata(
    val lastSeen: Long,
    @get:JvmName("hasSent")
    val hasSent: Boolean,
    val lastScrolled: Long,
    val unreadCount: Int
  )

  data class MergeResult(val threadId: Long, val previousThreadId: Long, val neededMerge: Boolean)

  data class ThreadIdResult(
    val threadId: Long,
    val newlyCreated: Boolean
  )

  data class ThreadDeleteSyncInfo(val threadId: Long, val addressableMessages: Set<MessageRecord>, val nonExpiringAddressableMessages: Set<MessageRecord>)
}
