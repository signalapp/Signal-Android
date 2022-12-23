package org.thoughtcrime.securesms.database

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MergeCursor
import android.net.Uri
import androidx.core.content.contentValuesOf
import com.fasterxml.jackson.annotation.JsonProperty
import org.jsoup.helper.StringUtil
import org.signal.core.util.CursorUtil
import org.signal.core.util.SqlUtil
import org.signal.core.util.delete
import org.signal.core.util.logging.Log
import org.signal.core.util.or
import org.signal.core.util.readToList
import org.signal.core.util.readToSingleLong
import org.signal.core.util.requireBoolean
import org.signal.core.util.requireInt
import org.signal.core.util.requireLong
import org.signal.core.util.requireString
import org.signal.core.util.select
import org.signal.core.util.toSingleLine
import org.signal.core.util.update
import org.signal.core.util.withinTransaction
import org.signal.libsignal.zkgroup.InvalidInputException
import org.signal.libsignal.zkgroup.groups.GroupMasterKey
import org.thoughtcrime.securesms.conversationlist.model.ConversationFilter
import org.thoughtcrime.securesms.database.MessageTable.MarkedMessageInfo
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.attachments
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.drafts
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.groupReceipts
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.mentions
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.messageLog
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.mms
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.mmsSms
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.recipients
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.sms
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.database.model.ThreadRecord
import org.thoughtcrime.securesms.groups.BadGroupIdException
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.mms.SlideDeck
import org.thoughtcrime.securesms.mms.StickerSlide
import org.thoughtcrime.securesms.notifications.v2.ConversationId
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientDetails
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.recipients.RecipientUtil
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.util.ConversationUtil
import org.thoughtcrime.securesms.util.JsonUtils
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.api.push.ServiceId
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
    const val ARCHIVED = "archived"
    const val STATUS = "status"
    const val DELIVERY_RECEIPT_COUNT = "delivery_receipt_count"
    const val READ_RECEIPT_COUNT = "read_receipt_count"
    const val EXPIRES_IN = "expires_in"
    const val LAST_SEEN = "last_seen"
    const val HAS_SENT = "has_sent"
    const val LAST_SCROLLED = "last_scrolled"
    const val PINNED = "pinned"
    const val UNREAD_SELF_MENTION_COUNT = "unread_self_mention_count"

    @JvmField
    val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY AUTOINCREMENT, 
        $DATE INTEGER DEFAULT 0, 
        $MEANINGFUL_MESSAGES INTEGER DEFAULT 0,
        $RECIPIENT_ID INTEGER,
        $READ INTEGER DEFAULT ${ReadStatus.READ.serialize()}, 
        $TYPE INTEGER DEFAULT 0, 
        $ERROR INTEGER DEFAULT 0, 
        $SNIPPET TEXT, 
        $SNIPPET_TYPE INTEGER DEFAULT 0, 
        $SNIPPET_URI TEXT DEFAULT NULL, 
        $SNIPPET_CONTENT_TYPE TEXT DEFAULT NULL, 
        $SNIPPET_EXTRAS TEXT DEFAULT NULL, 
        $ARCHIVED INTEGER DEFAULT 0, 
        $STATUS INTEGER DEFAULT 0, 
        $DELIVERY_RECEIPT_COUNT INTEGER DEFAULT 0, 
        $EXPIRES_IN INTEGER DEFAULT 0, 
        $LAST_SEEN INTEGER DEFAULT 0, 
        $HAS_SENT INTEGER DEFAULT 0, 
        $READ_RECEIPT_COUNT INTEGER DEFAULT 0, 
        $UNREAD_COUNT INTEGER DEFAULT 0, 
        $LAST_SCROLLED INTEGER DEFAULT 0, 
        $PINNED INTEGER DEFAULT 0, 
        $UNREAD_SELF_MENTION_COUNT INTEGER DEFAULT 0
      )
    """.trimIndent()

    @JvmField
    val CREATE_INDEXS = arrayOf(
      "CREATE INDEX IF NOT EXISTS thread_recipient_id_index ON $TABLE_NAME ($RECIPIENT_ID);",
      "CREATE INDEX IF NOT EXISTS archived_count_index ON $TABLE_NAME ($ARCHIVED, $MEANINGFUL_MESSAGES);",
      "CREATE INDEX IF NOT EXISTS thread_pinned_index ON $TABLE_NAME ($PINNED);",
      "CREATE INDEX IF NOT EXISTS thread_read ON $TABLE_NAME ($READ);"
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
      ARCHIVED,
      STATUS,
      DELIVERY_RECEIPT_COUNT,
      EXPIRES_IN,
      LAST_SEEN,
      READ_RECEIPT_COUNT,
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
    readReceiptCount: Int
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
      DELIVERY_RECEIPT_COUNT to deliveryReceiptCount,
      READ_RECEIPT_COUNT to readReceiptCount,
      EXPIRES_IN to expiresIn
    )

    writableDatabase
      .update(TABLE_NAME)
      .values(contentValues)
      .where("$ID = ?", threadId)
      .run()

    if (unarchive) {
      val archiveValues = contentValuesOf(ARCHIVED to 0)
      val query = SqlUtil.buildTrueUpdateQuery(ID_WHERE, SqlUtil.buildArgs(threadId), archiveValues)
      if (writableDatabase.update(TABLE_NAME, archiveValues, query.where, query.whereArgs) > 0) {
        StorageSyncHelper.scheduleSyncForDataChange()
      }
    }
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
      SNIPPET_TYPE to type,
      SNIPPET_URI to attachment?.toString()
    )

    if (unarchive) {
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

    readableDatabase
      .select(ID)
      .from(TABLE_NAME)
      .run()
      .use { cursor ->
        while (cursor.moveToNext()) {
          trimThreadInternal(cursor.requireLong(ID), length, trimBeforeDate)
        }
      }

    val deletes = writableDatabase.withinTransaction {
      mmsSms.deleteAbandonedMessages()
      attachments.trimAllAbandonedAttachments()
      groupReceipts.deleteAbandonedRows()
      mentions.deleteAbandonedMentions()
      return@withinTransaction attachments.deleteAbandonedAttachmentFiles()
    }

    if (deletes > 0) {
      Log.i(TAG, "Trim all threads caused $deletes attachments to be deleted.")
    }

    notifyAttachmentListeners()
    notifyStickerPackListeners()
  }

  fun trimThread(threadId: Long, length: Int, trimBeforeDate: Long) {
    if (length == NO_TRIM_MESSAGE_COUNT_SET && trimBeforeDate == NO_TRIM_BEFORE_DATE_SET) {
      return
    }

    val deletes = writableDatabase.withinTransaction {
      trimThreadInternal(threadId, length, trimBeforeDate)
      mmsSms.deleteAbandonedMessages()
      attachments.trimAllAbandonedAttachments()
      groupReceipts.deleteAbandonedRows()
      mentions.deleteAbandonedMentions()
      return@withinTransaction attachments.deleteAbandonedAttachmentFiles()
    }

    if (deletes > 0) {
      Log.i(TAG, "Trim thread $threadId caused $deletes attachments to be deleted.")
    }

    notifyAttachmentListeners()
    notifyStickerPackListeners()
  }

  private fun trimThreadInternal(threadId: Long, length: Int, trimBeforeDate: Long) {
    if (length == NO_TRIM_MESSAGE_COUNT_SET && trimBeforeDate == NO_TRIM_BEFORE_DATE_SET) {
      return
    }

    val finalTrimBeforeDate = if (length != NO_TRIM_MESSAGE_COUNT_SET && length > 0) {
      mmsSms.getConversation(threadId).use { cursor ->
        if (cursor.count > length) {
          cursor.moveToPosition(length - 1)
          max(trimBeforeDate, cursor.requireLong(MmsSmsColumns.DATE_RECEIVED))
        } else {
          trimBeforeDate
        }
      }
    } else {
      trimBeforeDate
    }

    if (finalTrimBeforeDate != NO_TRIM_BEFORE_DATE_SET) {
      Log.i(TAG, "Trimming thread: $threadId before: $finalTrimBeforeDate")

      val deletes = mmsSms.deleteMessagesInThreadBeforeDate(threadId, finalTrimBeforeDate)
      if (deletes > 0) {
        Log.i(TAG, "Trimming deleted $deletes messages thread: $threadId")
        setLastScrolled(threadId, 0)
        update(threadId, false)
        notifyConversationListeners(threadId)
      } else {
        Log.i(TAG, "Trimming deleted no messages thread: $threadId")
      }
    }
  }

  fun setAllThreadsRead(): List<MarkedMessageInfo> {
    writableDatabase
      .update(TABLE_NAME)
      .values(
        READ to ReadStatus.READ.serialize(),
        UNREAD_COUNT to 0,
        UNREAD_SELF_MENTION_COUNT to 0
      )
      .run()

    val smsRecords = sms.setAllMessagesRead()
    val mmsRecords = mms.setAllMessagesRead()

    sms.setAllReactionsSeen()
    mms.setAllReactionsSeen()
    notifyConversationListListeners()

    return smsRecords + mmsRecords
  }

  fun hasCalledSince(recipient: Recipient, timestamp: Long): Boolean {
    return hasReceivedAnyCallsSince(getOrCreateThreadIdFor(recipient), timestamp)
  }

  fun hasReceivedAnyCallsSince(threadId: Long, timestamp: Long): Boolean {
    return mmsSms.hasReceivedAnyCallsSince(threadId, timestamp)
  }

  fun setEntireThreadRead(threadId: Long): List<MarkedMessageInfo> {
    setRead(threadId, false)
    return sms.setEntireThreadRead(threadId) + mms.setEntireThreadRead(threadId)
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
    return mms.setGroupStoryMessagesReadSince(threadId, groupStoryId, sinceTimestamp)
  }

  fun setReadSince(threadIdToSinceTimestamp: Map<Long, Long>, lastSeen: Boolean): List<MarkedMessageInfo> {
    val smsRecords: MutableList<MarkedMessageInfo> = LinkedList()
    val mmsRecords: MutableList<MarkedMessageInfo> = LinkedList()
    var needsSync = false

    writableDatabase.withinTransaction { db ->
      for ((threadId, sinceTimestamp) in threadIdToSinceTimestamp) {
        val previous = getThreadRecord(threadId)

        smsRecords += sms.setMessagesReadSince(threadId, sinceTimestamp)
        mmsRecords += mms.setMessagesReadSince(threadId, sinceTimestamp)

        sms.setReactionsSeen(threadId, sinceTimestamp)
        mms.setReactionsSeen(threadId, sinceTimestamp)

        val unreadCount = mmsSms.getUnreadCount(threadId)
        val unreadMentionsCount = mms.getUnreadMentionCount(threadId)

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

    return smsRecords + mmsRecords
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
      """.toSingleLine(),
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

  fun getFilteredConversationList(filter: List<RecipientId>): Cursor? {
    if (filter.isEmpty()) {
      return null
    }

    val db = databaseHelper.signalReadableDatabase
    val splitRecipientIds: List<List<RecipientId>> = filter.chunked(900)
    val cursors: MutableList<Cursor> = LinkedList()

    for (recipientIds in splitRecipientIds) {
      var selection = "$TABLE_NAME.$RECIPIENT_ID = ?"
      val selectionArgs = arrayOfNulls<String>(recipientIds.size)

      for (i in 0 until recipientIds.size - 1) {
        selection += " OR $TABLE_NAME.$RECIPIENT_ID = ?"
      }

      var i = 0
      for (recipientId in recipientIds) {
        selectionArgs[i] = recipientId.serialize()
        i++
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
      where += " AND ${RecipientTable.TABLE_NAME}.${RecipientTable.GROUP_TYPE} != ${RecipientTable.GroupType.SIGNAL_V1.id}"
    }

    if (hideSms) {
      where += """ AND (
        ${RecipientTable.TABLE_NAME}.${RecipientTable.REGISTERED} = ${RecipientTable.RegisteredState.REGISTERED.id}
        OR 
        (
          ${RecipientTable.TABLE_NAME}.${RecipientTable.GROUP_ID} NOT NULL 
          AND ${RecipientTable.TABLE_NAME}.${RecipientTable.GROUP_TYPE} != ${RecipientTable.GroupType.MMS.id}
        ) 
      )"""
      where += " AND ${RecipientTable.TABLE_NAME}.${RecipientTable.FORCE_SMS_SELECTION} = 0"
    }

    if (hideSelf) {
      where += " AND $TABLE_NAME.$RECIPIENT_ID != ${Recipient.self().id.toLong()}"
    }

    where += " AND $ARCHIVED = 0"
    where += " AND ${RecipientTable.TABLE_NAME}.${RecipientTable.BLOCKED} = 0"

    if (SignalStore.releaseChannelValues().releaseChannelRecipientId != null) {
      where += " AND $TABLE_NAME.$RECIPIENT_ID != ${SignalStore.releaseChannelValues().releaseChannelRecipientId!!.toLong()}"
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
      .where("$ARCHIVED = 1 AND $MEANINGFUL_MESSAGES != 0 $filterQuery")
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
      .where("$ARCHIVED = 0 AND $PINNED != 0 $filterQuery")
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
      .where("$ARCHIVED = 0 AND ($MEANINGFUL_MESSAGES != 0 OR $PINNED != 0) $filterQuery")
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
          .values(PINNED to pinnedCount)
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
    setLastSeenSilently(threadId)
    notifyConversationListListeners()
  }

  fun setLastSeenSilently(threadId: Long) {
    writableDatabase
      .update(TABLE_NAME)
      .values(LAST_SEEN to System.currentTimeMillis())
      .where("$ID = ?", threadId)
      .run()
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
      .select(LAST_SEEN, HAS_SENT, LAST_SCROLLED)
      .from(TABLE_NAME)
      .where("$ID = ?", threadId)
      .run()
      .use { cursor ->
        if (cursor.moveToFirst()) {
          ConversationMetadata(
            lastSeen = cursor.requireLong(LAST_SEEN),
            hasSent = cursor.requireBoolean(HAS_SENT),
            lastScrolled = cursor.requireLong(LAST_SCROLLED)
          )
        } else {
          ConversationMetadata(
            lastSeen = -1L,
            hasSent = false,
            lastScrolled = -1
          )
        }
      }
  }

  fun deleteConversation(threadId: Long) {
    val recipientIdForThreadId = getRecipientIdForThreadId(threadId)

    writableDatabase.withinTransaction { db ->
      sms.deleteThread(threadId)
      mms.deleteThread(threadId)
      drafts.clearDrafts(threadId)
      db.delete(TABLE_NAME)
        .where("$ID = ?", threadId)
        .run()
    }

    notifyConversationListListeners()
    notifyConversationListeners(threadId)
    ConversationUtil.clearShortcuts(context, setOf(recipientIdForThreadId))
  }

  fun deleteConversations(selectedConversations: Set<Long>) {
    val recipientIdsForThreadIds = getRecipientIdsForThreadIds(selectedConversations)

    writableDatabase.withinTransaction { db ->
      sms.deleteThreads(selectedConversations)
      mms.deleteThreads(selectedConversations)
      drafts.clearDrafts(selectedConversations)

      SqlUtil.buildCollectionQuery(ID, selectedConversations)
        .forEach { db.delete(TABLE_NAME, it.where, it.whereArgs) }
    }

    notifyConversationListListeners()
    notifyConversationListeners(selectedConversations)
    ConversationUtil.clearShortcuts(context, recipientIdsForThreadIds)
  }

  fun deleteAllConversations() {
    writableDatabase.withinTransaction { db ->
      messageLog.deleteAll()
      sms.deleteAllThreads()
      mms.deleteAllThreads()
      drafts.clearAllDrafts()
      db.delete(TABLE_NAME, null, null)
    }

    notifyConversationListListeners()
    ConversationUtil.clearAllShortcuts(context)
  }

  fun getThreadIdIfExistsFor(recipientId: RecipientId): Long {
    return readableDatabase
      .select(ID)
      .from(TABLE_NAME)
      .where("$RECIPIENT_ID = ?", recipientId)
      .run()
      .readToSingleLong(-1)
  }

  fun getOrCreateValidThreadId(recipient: Recipient, candidateId: Long): Long {
    return getOrCreateValidThreadId(recipient, candidateId, DistributionTypes.DEFAULT)
  }

  fun getOrCreateValidThreadId(recipient: Recipient, candidateId: Long, distributionType: Int): Long {
    return if (candidateId != -1L) {
      val remapped = RemappedRecords.getInstance().getThread(candidateId)
      if (remapped.isPresent) {
        Log.i(TAG, "Using remapped threadId: " + candidateId + " -> " + remapped.get())
        remapped.get()
      } else {
        candidateId
      }
    } else {
      getOrCreateThreadIdFor(recipient, distributionType)
    }
  }

  fun getOrCreateThreadIdFor(recipient: Recipient): Long {
    return getOrCreateThreadIdFor(recipient, DistributionTypes.DEFAULT)
  }

  fun getOrCreateThreadIdFor(recipient: Recipient, distributionType: Int): Long {
    val threadId = getThreadIdFor(recipient.id)
    return threadId ?: createThreadForRecipient(recipient.id, recipient.isGroup, distributionType)
  }

  fun getThreadIdFor(recipientId: RecipientId): Long? {
    return readableDatabase
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
  }

  private fun getRecipientIdForThreadId(threadId: Long): RecipientId? {
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

  fun setHasSentSilently(threadId: Long, hasSent: Boolean) {
    writableDatabase
      .update(TABLE_NAME)
      .values(HAS_SENT to if (hasSent) 1 else 0)
      .where("$ID = ?", threadId)
      .run()
  }

  fun updateReadState(threadId: Long) {
    val previous = getThreadRecord(threadId)
    val unreadCount = mmsSms.getUnreadCount(threadId)
    val unreadMentionsCount = mms.getUnreadMentionCount(threadId)

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

      db.update(TABLE_NAME)
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
            .values(PINNED to pinnedPosition)
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
      val unreadCount = mmsSms.getUnreadCount(threadId)
      val unreadMentionsCount = mms.getUnreadMentionCount(threadId)

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

  fun update(threadId: Long, unarchive: Boolean): Boolean {
    return update(
      threadId = threadId,
      unarchive = unarchive,
      allowDeletion = true,
      notifyListeners = true
    )
  }

  fun updateSilently(threadId: Long, unarchive: Boolean): Boolean {
    return update(
      threadId = threadId,
      unarchive = unarchive,
      allowDeletion = true,
      notifyListeners = false
    )
  }

  fun update(threadId: Long, unarchive: Boolean, allowDeletion: Boolean): Boolean {
    return update(
      threadId = threadId,
      unarchive = unarchive,
      allowDeletion = allowDeletion,
      notifyListeners = true
    )
  }

  private fun update(threadId: Long, unarchive: Boolean, allowDeletion: Boolean, notifyListeners: Boolean): Boolean {
    val meaningfulMessages = mmsSms.hasMeaningfulMessage(threadId)

    val isPinned = getPinnedThreadIds().contains(threadId)
    val shouldDelete = allowDeletion && !isPinned && !mms.containsStories(threadId)

    if (!meaningfulMessages) {
      if (shouldDelete) {
        Log.d(TAG, "Deleting thread $threadId because it has no meaningful messages.")
        deleteConversation(threadId)
        return true
      } else if (!isPinned) {
        return false
      }
    }

    val record: MessageRecord = try {
      mmsSms.getConversationSnippet(threadId)
    } catch (e: NoSuchMessageException) {
      Log.w(TAG, "Failed to get a conversation snippet for thread $threadId")

      if (shouldDelete) {
        deleteConversation(threadId)
      }

      if (isPinned) {
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
          readReceiptCount = 0
        )
      }

      return true
    }

    val drafts: DraftTable.Drafts = SignalDatabase.drafts.getDrafts(threadId)
    if (drafts.isNotEmpty()) {
      val threadRecord: ThreadRecord? = getThreadRecord(threadId)
      if (threadRecord != null &&
        threadRecord.type == MmsSmsColumns.Types.BASE_DRAFT_TYPE &&
        threadRecord.date > record.timestamp
      ) {
        return false
      }
    }

    updateThread(
      threadId = threadId,
      meaningfulMessages = meaningfulMessages,
      body = ThreadBodyUtil.getFormattedBodyFor(context, record),
      attachment = getAttachmentUriFor(record),
      contentType = getContentTypeFor(record),
      extra = getExtrasFor(record),
      date = record.timestamp,
      status = record.deliveryStatus,
      deliveryReceiptCount = record.deliveryReceiptCount,
      type = record.type,
      unarchive = unarchive,
      expiresIn = record.expiresIn,
      readReceiptCount = record.readReceiptCount
    )

    if (notifyListeners) {
      notifyConversationListListeners()
    }

    return false
  }

  fun updateSnippetTypeSilently(threadId: Long) {
    if (threadId == -1L) {
      return
    }

    val type: Long = try {
      mmsSms.getConversationSnippetType(threadId)
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

    val primary: ThreadRecord? = getThreadRecord(getThreadIdFor(primaryRecipientId))
    val secondary: ThreadRecord? = getThreadRecord(getThreadIdFor(secondaryRecipientId))

    return if (primary != null && secondary == null) {
      Log.w(TAG, "[merge] Only had a thread for primary. Returning that.", true)
      MergeResult(threadId = primary.threadId, previousThreadId = -1, neededMerge = false)
    } else if (primary == null && secondary != null) {
      Log.w(TAG, "[merge] Only had a thread for secondary. Updating it to have the recipientId of the primary.", true)
      writableDatabase
        .update(TABLE_NAME)
        .values(RECIPIENT_ID to primaryRecipientId.serialize())
        .where("$ID = ?", secondary.threadId)
        .run()
      MergeResult(threadId = secondary.threadId, previousThreadId = -1, neededMerge = false)
    } else if (primary == null && secondary == null) {
      Log.w(TAG, "[merge] No thread for either.")
      MergeResult(threadId = -1, previousThreadId = -1, neededMerge = false)
    } else {
      Log.w(TAG, "[merge] Had a thread for both. Deleting the secondary and merging the attributes together.", true)
      check(primary != null)
      check(secondary != null)

      for (table in threadIdDatabaseTables) {
        table.remapThread(secondary.threadId, primary.threadId)
      }

      writableDatabase
        .delete(TABLE_NAME)
        .where("$ID = ?", secondary.threadId)
        .run()

      if (primary.expiresIn != secondary.expiresIn) {
        val values = ContentValues()
        if (primary.expiresIn == 0L) {
          values.put(EXPIRES_IN, secondary.expiresIn)
        } else if (secondary.expiresIn == 0L) {
          values.put(EXPIRES_IN, primary.expiresIn)
        } else {
          values.put(EXPIRES_IN, min(primary.expiresIn, secondary.expiresIn))
        }

        writableDatabase
          .update(TABLE_NAME)
          .values(values)
          .where("$ID = ?", primary.threadId)
          .run()
      }

      RemappedRecords.getInstance().addThread(secondary.threadId, primary.threadId)

      MergeResult(threadId = primary.threadId, previousThreadId = secondary.threadId, neededMerge = true)
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

  private fun getAttachmentUriFor(record: MessageRecord): Uri? {
    if (!record.isMms || record.isMmsNotification || record.isGroupAction) {
      return null
    }

    val slideDeck: SlideDeck = (record as MediaMmsMessageRecord).slideDeck
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

  private fun getExtrasFor(record: MessageRecord): Extra? {
    val threadRecipient = if (record.isOutgoing) record.recipient else getRecipientForThreadId(record.threadId)
    val messageRequestAccepted = RecipientUtil.isMessageRequestAccepted(record.threadId, threadRecipient)
    val individualRecipientId = record.individualRecipient.id

    if (!messageRequestAccepted && threadRecipient != null) {
      if (threadRecipient.isPushGroup) {
        if (threadRecipient.isPushV2Group) {
          val inviteAddState = record.gv2AddInviteState
          if (inviteAddState != null) {
            val from = RecipientId.from(ServiceId.from(inviteAddState.addedOrInvitedBy))
            return if (inviteAddState.isInvited) {
              Log.i(TAG, "GV2 invite message request from $from")
              Extra.forGroupV2invite(from, individualRecipientId)
            } else {
              Log.i(TAG, "GV2 message request from $from")
              Extra.forGroupMessageRequest(from, individualRecipientId)
            }
          }

          Log.w(TAG, "Falling back to unknown message request state for GV2 message")
          return Extra.forMessageRequest(individualRecipientId)
        } else {
          val recipientId = mmsSms.getGroupAddedBy(record.threadId)
          if (recipientId != null) {
            return Extra.forGroupMessageRequest(recipientId, individualRecipientId)
          }
        }
      } else {
        return Extra.forMessageRequest(individualRecipientId)
      }
    }

    return if (record.isRemoteDelete) {
      Extra.forRemoteDelete(individualRecipientId)
    } else if (record.isViewOnce) {
      Extra.forViewOnce(individualRecipientId)
    } else if (record.isMms && (record as MmsMessageRecord).slideDeck.stickerSlide != null) {
      val slide: StickerSlide = record.slideDeck.stickerSlide!!
      Extra.forSticker(slide.emoji, individualRecipientId)
    } else if (record.isMms && (record as MmsMessageRecord).slideDeck.slides.size > 1) {
      Extra.forAlbum(individualRecipientId)
    } else if (threadRecipient != null && threadRecipient.isGroup) {
      Extra.forDefault(individualRecipientId)
    } else {
      null
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

  private fun createQuery(where: String, orderBy: String, offset: Long, limit: Long): String {
    val projection = COMBINED_THREAD_RECIPIENT_GROUP_PROJECTION.joinToString(separator = ",")

    var query = """
      SELECT $projection 
      FROM $TABLE_NAME 
        LEFT OUTER JOIN ${RecipientTable.TABLE_NAME} ON $TABLE_NAME.$RECIPIENT_ID = ${RecipientTable.TABLE_NAME}.${RecipientTable.ID} 
        LEFT OUTER JOIN ${GroupTable.TABLE_NAME} ON $TABLE_NAME.$RECIPIENT_ID = ${GroupTable.TABLE_NAME}.${GroupTable.RECIPIENT_ID} 
      WHERE $where 
      ORDER BY $orderBy
    """.trimIndent()

    if (limit > 0) {
      query += " LIMIT $limit"
    }

    if (offset > 0) {
      query += " OFFSET $offset"
    }

    return query.toSingleLine()
  }

  private fun isSilentType(type: Long): Boolean {
    return MmsSmsColumns.Types.isProfileChange(type) ||
      MmsSmsColumns.Types.isGroupV1MigrationEvent(type) ||
      MmsSmsColumns.Types.isChangeNumber(type) ||
      MmsSmsColumns.Types.isBoostRequest(type) ||
      MmsSmsColumns.Types.isGroupV2LeaveOnly(type) ||
      MmsSmsColumns.Types.isThreadMergeType(type)
  }

  fun readerFor(cursor: Cursor): Reader {
    return Reader(cursor)
  }

  private fun ConversationFilter.toQuery(): String {
    return when (this) {
      ConversationFilter.OFF -> ""
      ConversationFilter.UNREAD -> " AND $READ != ${ReadStatus.READ.serialize()}"
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
      val recipientSettings = recipients.getRecord(context, cursor, RECIPIENT_ID)

      val recipient: Recipient = if (recipientSettings.groupId != null) {
        GroupTable.Reader(cursor).current?.let { group ->
          val details = RecipientDetails(
            group.title,
            null,
            if (group.hasAvatar()) Optional.of(group.avatarId) else Optional.empty(),
            false,
            false,
            recipientSettings.registered,
            recipientSettings,
            null,
            false
          )
          Recipient(recipientId, details, false)
        } ?: Recipient.live(recipientId).get()
      } else {
        val details = RecipientDetails.forIndividual(context, recipientSettings)
        Recipient(recipientId, details, true)
      }

      val readReceiptCount = if (TextSecurePreferences.isReadReceiptsEnabled(context)) cursor.requireInt(READ_RECEIPT_COUNT) else 0
      val extraString = cursor.getString(cursor.getColumnIndexOrThrow(SNIPPET_EXTRAS))
      val extra: Extra? = if (extraString != null) {
        try {
          JsonUtils.fromJson(extraString, Extra::class.java)
        } catch (e: IOException) {
          Log.w(TAG, "Failed to decode extras!")
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
        .setDeliveryReceiptCount(cursor.requireInt(DELIVERY_RECEIPT_COUNT))
        .setReadReceiptCount(readReceiptCount)
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
        .build()
    }

    private fun getSnippetUri(cursor: Cursor?): Uri? {
      return if (cursor!!.isNull(cursor.getColumnIndexOrThrow(SNIPPET_URI))) {
        null
      } else try {
        Uri.parse(cursor.getString(cursor.getColumnIndexOrThrow(SNIPPET_URI)))
      } catch (e: IllegalArgumentException) {
        Log.w(TAG, e)
        null
      }
    }

    override fun close() {
      cursor.close()
    }
  }

  data class Extra(
    @field:JsonProperty @param:JsonProperty("isRevealable") val isViewOnce: Boolean,
    @field:JsonProperty @param:JsonProperty("isSticker") val isSticker: Boolean,
    @field:JsonProperty @param:JsonProperty("stickerEmoji") val stickerEmoji: String?,
    @field:JsonProperty @param:JsonProperty("isAlbum") val isAlbum: Boolean,
    @field:JsonProperty @param:JsonProperty("isRemoteDelete") val isRemoteDelete: Boolean,
    @field:JsonProperty @param:JsonProperty("isMessageRequestAccepted") val isMessageRequestAccepted: Boolean,
    @field:JsonProperty @param:JsonProperty("isGv2Invite") val isGv2Invite: Boolean,
    @field:JsonProperty @param:JsonProperty("groupAddedBy") val groupAddedBy: String?,
    @field:JsonProperty @param:JsonProperty("individualRecipientId") private val individualRecipientId: String
  ) {

    fun getIndividualRecipientId(): String {
      return individualRecipientId
    }

    companion object {
      fun forViewOnce(individualRecipient: RecipientId): Extra {
        return Extra(isViewOnce = true, isSticker = false, stickerEmoji = null, isAlbum = false, isRemoteDelete = false, isMessageRequestAccepted = true, isGv2Invite = false, groupAddedBy = null, individualRecipientId = individualRecipient.serialize())
      }

      fun forSticker(emoji: String?, individualRecipient: RecipientId): Extra {
        return Extra(isViewOnce = false, isSticker = true, stickerEmoji = emoji, isAlbum = false, isRemoteDelete = false, isMessageRequestAccepted = true, isGv2Invite = false, groupAddedBy = null, individualRecipientId = individualRecipient.serialize())
      }

      fun forAlbum(individualRecipient: RecipientId): Extra {
        return Extra(isViewOnce = false, isSticker = false, stickerEmoji = null, isAlbum = true, isRemoteDelete = false, isMessageRequestAccepted = true, isGv2Invite = false, groupAddedBy = null, individualRecipientId = individualRecipient.serialize())
      }

      fun forRemoteDelete(individualRecipient: RecipientId): Extra {
        return Extra(isViewOnce = false, isSticker = false, stickerEmoji = null, isAlbum = false, isRemoteDelete = true, isMessageRequestAccepted = true, isGv2Invite = false, groupAddedBy = null, individualRecipientId = individualRecipient.serialize())
      }

      fun forMessageRequest(individualRecipient: RecipientId): Extra {
        return Extra(isViewOnce = false, isSticker = false, stickerEmoji = null, isAlbum = false, isRemoteDelete = false, isMessageRequestAccepted = false, isGv2Invite = false, groupAddedBy = null, individualRecipientId = individualRecipient.serialize())
      }

      fun forGroupMessageRequest(recipientId: RecipientId, individualRecipient: RecipientId): Extra {
        return Extra(isViewOnce = false, isSticker = false, stickerEmoji = null, isAlbum = false, isRemoteDelete = false, isMessageRequestAccepted = false, isGv2Invite = false, groupAddedBy = recipientId.serialize(), individualRecipientId = individualRecipient.serialize())
      }

      fun forGroupV2invite(recipientId: RecipientId, individualRecipient: RecipientId): Extra {
        return Extra(isViewOnce = false, isSticker = false, stickerEmoji = null, isAlbum = false, isRemoteDelete = false, isMessageRequestAccepted = false, isGv2Invite = true, groupAddedBy = recipientId.serialize(), individualRecipientId = individualRecipient.serialize())
      }

      fun forDefault(individualRecipient: RecipientId): Extra {
        return Extra(isViewOnce = false, isSticker = false, stickerEmoji = null, isAlbum = false, isRemoteDelete = false, isMessageRequestAccepted = true, isGv2Invite = false, groupAddedBy = null, individualRecipientId = individualRecipient.serialize())
      }
    }
  }

  internal enum class ReadStatus(private val value: Int) {
    READ(1), UNREAD(0), FORCED_UNREAD(2);

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
    val lastScrolled: Long
  )

  data class MergeResult(val threadId: Long, val previousThreadId: Long, val neededMerge: Boolean)
}
