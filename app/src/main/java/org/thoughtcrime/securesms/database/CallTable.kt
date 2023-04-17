package org.thoughtcrime.securesms.database

import android.content.Context
import android.database.Cursor
import androidx.annotation.Discouraged
import androidx.core.content.contentValuesOf
import org.signal.core.util.IntSerializer
import org.signal.core.util.Serializer
import org.signal.core.util.SqlUtil
import org.signal.core.util.delete
import org.signal.core.util.flatten
import org.signal.core.util.insertInto
import org.signal.core.util.logging.Log
import org.signal.core.util.readToList
import org.signal.core.util.readToMap
import org.signal.core.util.readToSingleLong
import org.signal.core.util.readToSingleObject
import org.signal.core.util.requireLong
import org.signal.core.util.requireNonNullString
import org.signal.core.util.requireObject
import org.signal.core.util.requireString
import org.signal.core.util.select
import org.signal.core.util.update
import org.signal.core.util.withinTransaction
import org.signal.ringrtc.CallId
import org.signal.ringrtc.CallManager.RingUpdate
import org.thoughtcrime.securesms.calls.log.CallLogFilter
import org.thoughtcrime.securesms.calls.log.CallLogRow
import org.thoughtcrime.securesms.database.model.GroupCallUpdateDetailsUtil
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobs.CallSyncEventJob
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.SyncMessage.CallEvent
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Contains details for each 1:1 call.
 */
class CallTable(context: Context, databaseHelper: SignalDatabase) : DatabaseTable(context, databaseHelper), RecipientIdDatabaseReference {

  companion object {
    private val TAG = Log.tag(CallTable::class.java)
    private val TIME_WINDOW = TimeUnit.HOURS.toMillis(4)

    private const val TABLE_NAME = "call"
    private const val ID = "_id"
    private const val CALL_ID = "call_id"
    private const val MESSAGE_ID = "message_id"
    private const val PEER = "peer"
    private const val CALL_LINK = "call_link"
    private const val TYPE = "type"
    private const val DIRECTION = "direction"
    private const val EVENT = "event"
    private const val TIMESTAMP = "timestamp"
    private const val RINGER = "ringer"
    private const val DELETION_TIMESTAMP = "deletion_timestamp"

    //language=sql
    val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY,
        $CALL_ID INTEGER NOT NULL,
        $MESSAGE_ID INTEGER DEFAULT NULL REFERENCES ${MessageTable.TABLE_NAME} (${MessageTable.ID}) ON DELETE SET NULL,
        $PEER INTEGER DEFAULT NULL REFERENCES ${RecipientTable.TABLE_NAME} (${RecipientTable.ID}) ON DELETE CASCADE,
        $CALL_LINK INTEGER DEFAULT NULL REFERENCES ${CallLinkTable.TABLE_NAME} (${CallLinkTable.ID}) ON DELETE CASCADE,
        $TYPE INTEGER NOT NULL,
        $DIRECTION INTEGER NOT NULL,
        $EVENT INTEGER NOT NULL,
        $TIMESTAMP INTEGER NOT NULL,
        $RINGER INTEGER DEFAULT NULL,
        $DELETION_TIMESTAMP INTEGER DEFAULT 0,
        UNIQUE ($CALL_ID, $PEER, $CALL_LINK) ON CONFLICT FAIL,
        CHECK (($PEER IS NULL AND $CALL_LINK IS NOT NULL) OR ($PEER IS NOT NULL AND $CALL_LINK IS NULL))
      )
    """.trimIndent()

    val CREATE_INDEXES = arrayOf(
      "CREATE INDEX call_call_id_index ON $TABLE_NAME ($CALL_ID)",
      "CREATE INDEX call_message_id_index ON $TABLE_NAME ($MESSAGE_ID)"
    )
  }

  fun insertOneToOneCall(callId: Long, timestamp: Long, peer: RecipientId, type: Type, direction: Direction, event: Event) {
    val messageType: Long = Call.getMessageType(type, direction, event)

    writableDatabase.withinTransaction {
      val result = SignalDatabase.messages.insertCallLog(peer, messageType, timestamp)

      val values = contentValuesOf(
        CALL_ID to callId,
        MESSAGE_ID to result.messageId,
        PEER to peer.serialize(),
        TYPE to Type.serialize(type),
        DIRECTION to Direction.serialize(direction),
        EVENT to Event.serialize(event),
        TIMESTAMP to timestamp
      )

      writableDatabase.insert(TABLE_NAME, null, values)
    }

    ApplicationDependencies.getMessageNotifier().updateNotification(context)
    ApplicationDependencies.getDatabaseObserver().notifyCallUpdateObservers()

    Log.i(TAG, "Inserted call: $callId type: $type direction: $direction event:$event")
  }

  fun updateOneToOneCall(callId: Long, event: Event): Call? {
    return writableDatabase.withinTransaction {
      writableDatabase
        .update(TABLE_NAME)
        .values(EVENT to Event.serialize(event))
        .where("$CALL_ID = ?", callId)
        .run()

      val call = readableDatabase
        .select()
        .from(TABLE_NAME)
        .where("$CALL_ID = ?", callId)
        .run()
        .readToSingleObject(Call.Deserializer)

      if (call != null) {
        Log.i(TAG, "Updated call: $callId event: $event")

        SignalDatabase.messages.updateCallLog(call.messageId!!, call.messageType)
        ApplicationDependencies.getMessageNotifier().updateNotification(context)
        ApplicationDependencies.getDatabaseObserver().notifyCallUpdateObservers()
      }

      call
    }
  }

  fun getCallById(callId: Long, conversationId: CallConversationId): Call? {
    val query = getCallSelectionQuery(callId, conversationId)

    return readableDatabase
      .select()
      .from(TABLE_NAME)
      .where(query.where, query.whereArgs)
      .run()
      .readToSingleObject(Call.Deserializer)
  }

  fun getCallByMessageId(messageId: Long): Call? {
    return readableDatabase
      .select()
      .from(TABLE_NAME)
      .where("$MESSAGE_ID = ?", messageId)
      .run()
      .readToSingleObject(Call.Deserializer)
  }

  fun getCalls(messageIds: Collection<Long>): Map<Long, Call> {
    val queries = SqlUtil.buildCollectionQuery(MESSAGE_ID, messageIds)
    val maps = queries.map { query ->
      readableDatabase
        .select()
        .from(TABLE_NAME)
        .where("$EVENT != ${Event.serialize(Event.DELETE)} AND ${query.where}", query.whereArgs)
        .run()
        .readToMap { c -> c.requireLong(MESSAGE_ID) to Call.deserialize(c) }
    }

    return maps.flatten()
  }

  /**
   * @param callRowIds The CallTable.ID collection to query
   *
   * @return a map of raw MessageId -> Call
   */
  fun getCallsByRowIds(callRowIds: Collection<Long>): Map<Long, Call> {
    val queries = SqlUtil.buildCollectionQuery(ID, callRowIds)

    val maps = queries.map { query ->
      readableDatabase
        .select()
        .from(TABLE_NAME)
        .where("$EVENT != ${Event.serialize(Event.DELETE)} AND ${query.where}", query.whereArgs)
        .run()
        .readToMap { c -> c.requireLong(MESSAGE_ID) to Call.deserialize(c) }
    }

    return maps.flatten()
  }

  fun getOldestDeletionTimestamp(): Long {
    return writableDatabase
      .select(DELETION_TIMESTAMP)
      .from(TABLE_NAME)
      .where("$DELETION_TIMESTAMP > 0")
      .orderBy("$DELETION_TIMESTAMP DESC")
      .limit(1)
      .run()
      .readToSingleLong(0L)
  }

  fun deleteCallEventsDeletedBefore(threshold: Long): Int {
    return writableDatabase
      .delete(TABLE_NAME)
      .where("$DELETION_TIMESTAMP > 0 AND $DELETION_TIMESTAMP <= ?", threshold)
      .run()
  }

  /**
   * If a non-ad-hoc call has been deleted from the message database, then we need to
   * set its deletion_timestamp to now.
   */
  fun updateCallEventDeletionTimestamps() {
    val where = "$TYPE != ? AND $DELETION_TIMESTAMP = 0 AND $MESSAGE_ID IS NULL"
    val type = Type.serialize(Type.AD_HOC_CALL)

    val toSync = writableDatabase.withinTransaction { db ->
      val result = db
        .select()
        .from(TABLE_NAME)
        .where(where, type)
        .run()
        .readToList {
          Call.deserialize(it)
        }
        .toSet()

      db
        .update(TABLE_NAME)
        .values(
          EVENT to Event.serialize(Event.DELETE),
          DELETION_TIMESTAMP to System.currentTimeMillis()
        )
        .where(where, type)
        .run()

      result
    }

    CallSyncEventJob.enqueueDeleteSyncEvents(toSync)
    ApplicationDependencies.getDeletedCallEventManager().scheduleIfNecessary()
    ApplicationDependencies.getDatabaseObserver().notifyCallUpdateObservers()
  }

  // region Group / Ad-Hoc Calling

  fun deleteGroupCall(call: Call) {
    checkIsGroupOrAdHocCall(call)

    writableDatabase.withinTransaction { db ->
      db
        .update(TABLE_NAME)
        .values(
          EVENT to Event.serialize(Event.DELETE),
          DELETION_TIMESTAMP to System.currentTimeMillis()
        )
        .where("$CALL_ID = ? AND $PEER = ?", call.callId, call.peer)
        .run()

      if (call.messageId != null) {
        SignalDatabase.messages.deleteMessage(call.messageId)
      }
    }

    ApplicationDependencies.getMessageNotifier().updateNotification(context)
    ApplicationDependencies.getDatabaseObserver().notifyCallUpdateObservers()
    Log.d(TAG, "Marked group call event for deletion: ${call.callId}")
  }

  fun insertDeletedGroupCallFromSyncEvent(
    callId: Long,
    recipientId: RecipientId?,
    direction: Direction,
    timestamp: Long
  ) {
    val type = if (recipientId != null) Type.GROUP_CALL else Type.AD_HOC_CALL

    writableDatabase
      .insertInto(TABLE_NAME)
      .values(
        CALL_ID to callId,
        MESSAGE_ID to null,
        PEER to recipientId?.toLong(),
        EVENT to Event.serialize(Event.DELETE),
        TYPE to Type.serialize(type),
        DIRECTION to Direction.serialize(direction),
        TIMESTAMP to timestamp,
        DELETION_TIMESTAMP to System.currentTimeMillis()
      )
      .run()

    ApplicationDependencies.getDeletedCallEventManager().scheduleIfNecessary()
  }

  fun acceptIncomingGroupCall(call: Call) {
    checkIsGroupOrAdHocCall(call)
    check(call.direction == Direction.INCOMING)

    val newEvent = when (call.event) {
      Event.RINGING, Event.MISSED, Event.DECLINED -> Event.ACCEPTED
      Event.GENERIC_GROUP_CALL -> Event.JOINED
      else -> {
        Log.d(TAG, "Call in state ${call.event} cannot be transitioned by ACCEPTED")
        return
      }
    }

    writableDatabase
      .update(TABLE_NAME)
      .values(EVENT to Event.serialize(newEvent))
      .run()

    ApplicationDependencies.getMessageNotifier().updateNotification(context)
    ApplicationDependencies.getDatabaseObserver().notifyCallUpdateObservers()
    Log.d(TAG, "Transitioned group call ${call.callId} from ${call.event} to $newEvent")
  }

  fun insertAcceptedGroupCall(
    callId: Long,
    recipientId: RecipientId?,
    direction: Direction,
    timestamp: Long
  ) {
    val type = if (recipientId != null) Type.GROUP_CALL else Type.AD_HOC_CALL
    val event = if (direction == Direction.OUTGOING) Event.OUTGOING_RING else Event.JOINED
    val ringer = if (direction == Direction.OUTGOING) Recipient.self().id.toLong() else null

    writableDatabase.withinTransaction { db ->
      val messageId: MessageId? = if (recipientId != null) {
        SignalDatabase.messages.insertGroupCall(
          groupRecipientId = recipientId,
          sender = Recipient.self().id,
          timestamp,
          "",
          emptyList(),
          false
        )
      } else {
        null
      }

      db
        .insertInto(TABLE_NAME)
        .values(
          CALL_ID to callId,
          MESSAGE_ID to messageId?.id,
          PEER to recipientId?.toLong(),
          EVENT to Event.serialize(event),
          TYPE to Type.serialize(type),
          DIRECTION to Direction.serialize(direction),
          TIMESTAMP to timestamp,
          RINGER to ringer
        )
        .run()
    }

    ApplicationDependencies.getDatabaseObserver().notifyCallUpdateObservers()
  }

  fun insertOrUpdateGroupCallFromExternalEvent(
    groupRecipientId: RecipientId,
    sender: RecipientId,
    timestamp: Long,
    messageGroupCallEraId: String?
  ) {
    insertOrUpdateGroupCallFromLocalEvent(
      groupRecipientId,
      sender,
      timestamp,
      messageGroupCallEraId,
      emptyList(),
      false
    )
  }

  fun insertOrUpdateGroupCallFromLocalEvent(
    groupRecipientId: RecipientId,
    sender: RecipientId,
    timestamp: Long,
    peekGroupCallEraId: String?,
    peekJoinedUuids: Collection<UUID>,
    isCallFull: Boolean
  ) {
    writableDatabase.withinTransaction {
      if (peekGroupCallEraId.isNullOrEmpty()) {
        Log.w(TAG, "Dropping local call event with null era id.")
        return@withinTransaction
      }

      val callId = CallId.fromEra(peekGroupCallEraId).longValue()
      val call = getCallById(callId, CallConversationId.Peer(groupRecipientId))
      val messageId: MessageId = if (call != null) {
        if (call.event == Event.DELETE) {
          Log.d(TAG, "Dropping group call update for deleted call.")
          return@withinTransaction
        }

        if (call.type != Type.GROUP_CALL) {
          Log.d(TAG, "Dropping unsupported update message for non-group-call call.")
          return@withinTransaction
        }

        if (call.messageId == null) {
          Log.d(TAG, "Dropping group call update for call without an attached message.")
          return@withinTransaction
        }

        SignalDatabase.messages.updateGroupCall(
          call.messageId,
          peekGroupCallEraId,
          peekJoinedUuids,
          isCallFull
        )
      } else {
        SignalDatabase.messages.insertGroupCall(
          groupRecipientId,
          sender,
          timestamp,
          peekGroupCallEraId,
          peekJoinedUuids,
          isCallFull
        )
      }

      insertCallEventFromGroupUpdate(
        callId,
        messageId,
        sender,
        groupRecipientId,
        timestamp
      )
    }
  }

  private fun insertCallEventFromGroupUpdate(
    callId: Long,
    messageId: MessageId?,
    sender: RecipientId,
    groupRecipientId: RecipientId,
    timestamp: Long
  ) {
    val conversationId = CallConversationId.Peer(groupRecipientId)
    if (messageId != null) {
      val call = getCallById(callId, conversationId)
      if (call == null) {
        val direction = if (sender == Recipient.self().id) Direction.OUTGOING else Direction.INCOMING

        writableDatabase
          .insertInto(TABLE_NAME)
          .values(
            CALL_ID to callId,
            MESSAGE_ID to messageId.id,
            PEER to groupRecipientId.toLong(),
            EVENT to Event.serialize(Event.GENERIC_GROUP_CALL),
            TYPE to Type.serialize(Type.GROUP_CALL),
            DIRECTION to Direction.serialize(direction),
            TIMESTAMP to timestamp,
            RINGER to null
          )
          .run()

        Log.d(TAG, "Inserted new call event from group call update message. Call Id: $callId")
      } else {
        if (timestamp < call.timestamp) {
          setTimestamp(callId, conversationId, timestamp)
          Log.d(TAG, "Updated call event timestamp for call id $callId")
        }

        if (call.messageId == null) {
          setMessageId(callId, messageId)
          Log.d(TAG, "Updated call event message id for newly inserted group call state: $callId")
        }
      }
    } else {
      Log.d(TAG, "Skipping call event processing for null era id.")
    }

    ApplicationDependencies.getDatabaseObserver().notifyCallUpdateObservers()
  }

  /**
   * Since this does not alter the call table, we can simply pass this directly through to the old handler.
   */
  fun updateGroupCallFromPeek(
    threadId: Long,
    peekGroupCallEraId: String?,
    peekJoinedUuids: Collection<UUID>,
    isCallFull: Boolean
  ): Boolean {
    val sameEraId = SignalDatabase.messages.updatePreviousGroupCall(threadId, peekGroupCallEraId, peekJoinedUuids, isCallFull)
    ApplicationDependencies.getDatabaseObserver().notifyCallUpdateObservers()
    return sameEraId
  }

  fun insertOrUpdateGroupCallFromRingState(
    ringId: Long,
    groupRecipientId: RecipientId,
    ringerRecipient: RecipientId,
    dateReceived: Long,
    ringState: RingUpdate
  ) {
    handleGroupRingState(ringId, groupRecipientId, ringerRecipient, dateReceived, ringState)
  }

  fun insertOrUpdateGroupCallFromRingState(
    ringId: Long,
    groupRecipientId: RecipientId,
    ringerUUID: UUID,
    dateReceived: Long,
    ringState: RingUpdate
  ) {
    val ringerRecipient = Recipient.externalPush(ServiceId.from(ringerUUID))
    handleGroupRingState(ringId, groupRecipientId, ringerRecipient.id, dateReceived, ringState)
  }

  fun isRingCancelled(ringId: Long, groupRecipientId: RecipientId): Boolean {
    val call = getCallById(ringId, CallConversationId.Peer(groupRecipientId)) ?: return false
    return call.event != Event.RINGING
  }

  private fun handleGroupRingState(
    ringId: Long,
    groupRecipientId: RecipientId,
    ringerRecipient: RecipientId,
    dateReceived: Long,
    ringState: RingUpdate
  ) {
    Log.d(TAG, "Processing group ring state update for $ringId in state $ringState")

    val call = getCallById(ringId, CallConversationId.Peer(groupRecipientId))
    if (call != null) {
      if (call.event == Event.DELETE) {
        Log.d(TAG, "Ignoring ring request for $ringId since its event has been deleted.")
        return
      }

      when (ringState) {
        RingUpdate.REQUESTED -> {
          when (call.event) {
            Event.GENERIC_GROUP_CALL -> updateEventFromRingState(ringId, Event.RINGING, ringerRecipient)
            Event.JOINED -> updateEventFromRingState(ringId, Event.ACCEPTED, ringerRecipient)
            else -> Log.w(TAG, "Received a REQUESTED ring event while in ${call.event}. Ignoring.")
          }
        }
        RingUpdate.EXPIRED_REQUEST, RingUpdate.CANCELLED_BY_RINGER -> {
          when (call.event) {
            Event.GENERIC_GROUP_CALL, Event.RINGING -> updateEventFromRingState(ringId, Event.MISSED, ringerRecipient)
            Event.OUTGOING_RING -> Log.w(TAG, "Received an expiration or cancellation while in OUTGOING_RING state. Ignoring.")
            else -> Unit
          }
        }
        RingUpdate.BUSY_LOCALLY, RingUpdate.BUSY_ON_ANOTHER_DEVICE -> {
          when (call.event) {
            Event.JOINED -> updateEventFromRingState(ringId, Event.ACCEPTED)
            Event.GENERIC_GROUP_CALL, Event.RINGING -> updateEventFromRingState(ringId, Event.MISSED)
            else -> Log.w(TAG, "Received a busy event we can't process. Ignoring.")
          }
        }
        RingUpdate.ACCEPTED_ON_ANOTHER_DEVICE -> {
          updateEventFromRingState(ringId, Event.ACCEPTED)
        }
        RingUpdate.DECLINED_ON_ANOTHER_DEVICE -> {
          when (call.event) {
            Event.RINGING, Event.MISSED -> updateEventFromRingState(ringId, Event.DECLINED)
            Event.OUTGOING_RING -> Log.w(TAG, "Received DECLINED_ON_ANOTHER_DEVICE while in OUTGOING_RING state.")
            else -> Unit
          }
        }
      }
    } else {
      val event: Event = when (ringState) {
        RingUpdate.REQUESTED -> Event.RINGING
        RingUpdate.EXPIRED_REQUEST -> Event.MISSED
        RingUpdate.ACCEPTED_ON_ANOTHER_DEVICE -> {
          Log.w(TAG, "Missed original ring request for $ringId")
          Event.ACCEPTED
        }
        RingUpdate.DECLINED_ON_ANOTHER_DEVICE -> {
          Log.w(TAG, "Missed original ring request for $ringId")
          Event.DECLINED
        }
        RingUpdate.BUSY_LOCALLY, RingUpdate.BUSY_ON_ANOTHER_DEVICE -> {
          Log.w(TAG, "Missed original ring request for $ringId")
          Event.MISSED
        }
        RingUpdate.CANCELLED_BY_RINGER -> {
          Log.w(TAG, "Missed original ring request for $ringId")
          Event.MISSED
        }
      }

      createEventFromRingState(ringId, groupRecipientId, ringerRecipient, event, dateReceived)
    }

    ApplicationDependencies.getDatabaseObserver().notifyCallUpdateObservers()
  }

  private fun updateEventFromRingState(
    callId: Long,
    event: Event,
    ringerRecipient: RecipientId
  ) {
    writableDatabase
      .update(TABLE_NAME)
      .values(
        EVENT to Event.serialize(event),
        RINGER to ringerRecipient.serialize()
      )
      .where("$CALL_ID = ?", callId)
      .run()

    Log.d(TAG, "Updated ring state to $event")
  }

  private fun updateEventFromRingState(
    callId: Long,
    event: Event
  ) {
    writableDatabase
      .update(TABLE_NAME)
      .values(
        EVENT to Event.serialize(event)
      )
      .where("$CALL_ID = ?", callId)
      .run()

    Log.d(TAG, "Updated ring state to $event")
  }

  private fun createEventFromRingState(
    callId: Long,
    groupRecipientId: RecipientId,
    ringerRecipient: RecipientId,
    event: Event,
    timestamp: Long
  ) {
    val direction = if (ringerRecipient == Recipient.self().id) Direction.OUTGOING else Direction.INCOMING

    writableDatabase.withinTransaction { db ->
      val messageId = SignalDatabase.messages.insertGroupCall(
        groupRecipientId = groupRecipientId,
        sender = ringerRecipient,
        timestamp = timestamp,
        eraId = "",
        joinedUuids = emptyList(),
        isCallFull = false
      )

      db
        .insertInto(TABLE_NAME)
        .values(
          CALL_ID to callId,
          MESSAGE_ID to messageId.id,
          PEER to groupRecipientId.toLong(),
          EVENT to Event.serialize(event),
          TYPE to Type.serialize(Type.GROUP_CALL),
          DIRECTION to Direction.serialize(direction),
          TIMESTAMP to timestamp,
          RINGER to ringerRecipient.toLong()
        )
        .run()
    }

    Log.d(TAG, "Inserted a new group ring event for $callId with event $event")
  }

  fun setTimestamp(callId: Long, conversationId: CallConversationId, timestamp: Long) {
    writableDatabase.withinTransaction { db ->
      val call = getCallById(callId, conversationId)
      if (call == null || call.event == Event.DELETE) {
        Log.d(TAG, "Refusing to update deleted call event.")
        return@withinTransaction
      }

      db
        .update(TABLE_NAME)
        .values(TIMESTAMP to timestamp)
        .where("$CALL_ID = ?", callId)
        .run()

      if (call.messageId != null) {
        SignalDatabase.messages.updateCallTimestamps(call.messageId, timestamp)
      }
    }

    ApplicationDependencies.getDatabaseObserver().notifyCallUpdateObservers()
  }

  private fun setMessageId(callId: Long, messageId: MessageId) {
    writableDatabase
      .update(TABLE_NAME)
      .values(MESSAGE_ID to messageId.id)
      .where("$CALL_ID = ?", callId)
      .run()
  }

  fun deleteCallEvents(callRowIds: Set<Long>) {
    val messageIds = getMessageIds(callRowIds)
    SignalDatabase.messages.deleteCallUpdates(messageIds)
    updateCallEventDeletionTimestamps()
  }

  fun deleteAllCallEventsExcept(callRowIds: Set<Long>) {
    val messageIds = getMessageIds(callRowIds)
    SignalDatabase.messages.deleteAllCallUpdatesExcept(messageIds)
    updateCallEventDeletionTimestamps()
  }

  @Discouraged("Using this method is generally considered an error. Utilize other deletion methods instead of this.")
  fun deleteAllCalls() {
    Log.w(TAG, "Deleting all calls from the local database.")
    writableDatabase
      .delete(TABLE_NAME)
      .run()
  }

  private fun getCallSelectionQuery(callId: Long, conversationId: CallConversationId): SqlUtil.Query {
    return when (conversationId) {
      is CallConversationId.CallLink -> SqlUtil.Query("$CALL_ID = ? AND $CALL_LINK = ?", SqlUtil.buildArgs(callId, conversationId.callLinkId))
      is CallConversationId.Peer -> SqlUtil.Query("$CALL_ID = ? AND $PEER = ?", SqlUtil.buildArgs(callId, conversationId.recipientId))
    }
  }

  private fun getMessageIds(callRowIds: Set<Long>): Set<Long> {
    val queries = SqlUtil.buildCollectionQuery(
      ID,
      callRowIds,
      "$MESSAGE_ID NOT NULL AND"
    )

    return queries.map { query ->
      readableDatabase.select(MESSAGE_ID).from(TABLE_NAME).where(query.where, query.whereArgs).run().readToList {
        it.requireLong(MESSAGE_ID)
      }
    }.flatten().toSet()
  }

  private fun checkIsGroupOrAdHocCall(call: Call) {
    check(call.type == Type.GROUP_CALL || call.type == Type.AD_HOC_CALL)
  }

  // endregion

  private fun getCallsCursor(isCount: Boolean, offset: Int, limit: Int, searchTerm: String?, filter: CallLogFilter): Cursor {
    val filterClause: SqlUtil.Query = when (filter) {
      CallLogFilter.ALL -> SqlUtil.buildQuery("$DELETION_TIMESTAMP = 0")
      CallLogFilter.MISSED -> SqlUtil.buildQuery("$EVENT = ${Event.serialize(Event.MISSED)} AND $DELETION_TIMESTAMP = 0")
    }

    val queryClause: SqlUtil.Query = if (!searchTerm.isNullOrEmpty()) {
      val glob = SqlUtil.buildCaseInsensitiveGlobPattern(searchTerm)
      val selection =
        """
        ${RecipientTable.TABLE_NAME}.${RecipientTable.BLOCKED} = ? AND ${RecipientTable.TABLE_NAME}.${RecipientTable.HIDDEN} = ? AND
        (
          sort_name GLOB ? OR 
          ${RecipientTable.TABLE_NAME}.${RecipientTable.USERNAME} GLOB ? OR 
          ${RecipientTable.TABLE_NAME}.${RecipientTable.PHONE} GLOB ? OR 
          ${RecipientTable.TABLE_NAME}.${RecipientTable.EMAIL} GLOB ?
        )
        """.trimIndent()
      SqlUtil.buildQuery(selection, 0, 0, glob, glob, glob, glob)
    } else {
      SqlUtil.buildQuery("")
    }

    val offsetLimit = if (limit > 0) {
      "LIMIT $offset,$limit"
    } else {
      ""
    }

    val projection = if (isCount) {
      "COUNT(*),"
    } else {
      "p.$ID, $TIMESTAMP, $EVENT, $DIRECTION, $PEER, p.$TYPE, $CALL_ID, $MESSAGE_ID, $RINGER, children, in_period, ${MessageTable.DATE_RECEIVED}, ${MessageTable.BODY},"
    }

    //language=sql
    val statement = """
      SELECT $projection
        LOWER(
          COALESCE(
            NULLIF(${RecipientTable.TABLE_NAME}.${RecipientTable.SYSTEM_JOINED_NAME}, ''),
            NULLIF(${RecipientTable.TABLE_NAME}.${RecipientTable.SYSTEM_GIVEN_NAME}, ''),
            NULLIF(${RecipientTable.TABLE_NAME}.${RecipientTable.PROFILE_JOINED_NAME}, ''),
            NULLIF(${RecipientTable.TABLE_NAME}.${RecipientTable.PROFILE_GIVEN_NAME}, ''),
            NULLIF(${RecipientTable.TABLE_NAME}.${RecipientTable.USERNAME}, '')
          )
        ) AS sort_name
      FROM (
        WITH cte AS (
          SELECT
            $ID, $TIMESTAMP, $EVENT, $DIRECTION, $PEER, $TYPE, $CALL_ID, $MESSAGE_ID, $RINGER,
            (
              SELECT
                $ID
              FROM
                $TABLE_NAME
              WHERE
                $TABLE_NAME.$DIRECTION = c.$DIRECTION
                AND $TABLE_NAME.$PEER = c.$PEER
                AND $TABLE_NAME.$TIMESTAMP - $TIME_WINDOW <= c.$TIMESTAMP
                AND $TABLE_NAME.$TIMESTAMP >= c.$TIMESTAMP
                AND ${filterClause.where}
              ORDER BY
                $TIMESTAMP DESC
            ) as parent,
            (
              SELECT
                group_concat($ID)
              FROM
                $TABLE_NAME
              WHERE
                $TABLE_NAME.$DIRECTION = c.$DIRECTION
                AND $TABLE_NAME.$PEER = c.$PEER
                AND c.$TIMESTAMP - $TIME_WINDOW <= $TABLE_NAME.$TIMESTAMP
                AND c.$TIMESTAMP >= $TABLE_NAME.$TIMESTAMP
                AND ${filterClause.where}
            ) as children,
            (
              SELECT
                group_concat($ID)
              FROM
                $TABLE_NAME
              WHERE
                c.$TIMESTAMP - $TIME_WINDOW <= $TABLE_NAME.$TIMESTAMP
                AND c.$TIMESTAMP >= $TABLE_NAME.$TIMESTAMP
                AND ${filterClause.where}
            ) as in_period
          FROM
            $TABLE_NAME c
          WHERE ${filterClause.where}
          ORDER BY
            $TIMESTAMP DESC
        )
        SELECT
          *,
          CASE
            WHEN LAG (parent, 1, 0) OVER (
              ORDER BY
                $TIMESTAMP DESC
            ) != parent THEN $ID
            ELSE parent
          END true_parent
        FROM
          cte
      ) p
      INNER JOIN ${RecipientTable.TABLE_NAME} ON ${RecipientTable.TABLE_NAME}.${RecipientTable.ID} = $PEER
      INNER JOIN ${MessageTable.TABLE_NAME} ON ${MessageTable.TABLE_NAME}.${MessageTable.ID} = $MESSAGE_ID
      WHERE true_parent = p.$ID ${if (queryClause.where.isNotEmpty()) "AND ${queryClause.where}" else ""}
      $offsetLimit
    """.trimIndent()

    return readableDatabase.query(
      statement,
      queryClause.whereArgs
    )
  }

  fun getCallsCount(searchTerm: String?, filter: CallLogFilter): Int {
    return getCallsCursor(true, 0, 0, searchTerm, filter).use {
      it.moveToFirst()
      it.getInt(0)
    }
  }

  fun getCalls(offset: Int, limit: Int, searchTerm: String?, filter: CallLogFilter): List<CallLogRow.Call> {
    return getCallsCursor(false, offset, limit, searchTerm, filter).readToList { cursor ->
      val call = Call.deserialize(cursor)
      val recipient = Recipient.resolved(call.peer)
      val date = cursor.requireLong(MessageTable.DATE_RECEIVED)
      val groupCallDetails = GroupCallUpdateDetailsUtil.parse(cursor.requireString(MessageTable.BODY))

      Log.d(TAG, "${cursor.requireNonNullString("in_period")}")

      val children = cursor.requireNonNullString("children")
        .split(',')
        .map { it.toLong() }
        .toSet()

      val inPeriod = cursor.requireNonNullString("in_period")
        .split(',')
        .map { it.toLong() }
        .sortedDescending()
        .toSet()

      val actualChildren = inPeriod.takeWhile { children.contains(it) }

      CallLogRow.Call(
        record = call,
        peer = recipient,
        date = date,
        groupCallState = CallLogRow.GroupCallState.fromDetails(groupCallDetails),
        children = actualChildren.toSet()
      )
    }
  }

  override fun remapRecipient(fromId: RecipientId, toId: RecipientId) {
    writableDatabase
      .update(TABLE_NAME)
      .values(PEER to toId.serialize())
      .where("$PEER = ?", fromId)
      .run()
  }

  data class Call(
    val callId: Long,
    val peer: RecipientId,
    val type: Type,
    val direction: Direction,
    val event: Event,
    val messageId: Long?,
    val timestamp: Long,
    val ringerRecipient: RecipientId?
  ) {
    val messageType: Long = getMessageType(type, direction, event)

    companion object Deserializer : Serializer<Call, Cursor> {
      fun getMessageType(type: Type, direction: Direction, event: Event): Long {
        if (type == Type.GROUP_CALL) {
          return MessageTypes.GROUP_CALL_TYPE
        }

        if (type == Type.AD_HOC_CALL) {
          error("Ad-Hoc calls are not linked to messages.")
        }

        return if (direction == Direction.INCOMING && event == Event.MISSED) {
          if (type == Type.VIDEO_CALL) MessageTypes.MISSED_VIDEO_CALL_TYPE else MessageTypes.MISSED_AUDIO_CALL_TYPE
        } else if (direction == Direction.INCOMING) {
          if (type == Type.VIDEO_CALL) MessageTypes.INCOMING_VIDEO_CALL_TYPE else MessageTypes.INCOMING_AUDIO_CALL_TYPE
        } else {
          if (type == Type.VIDEO_CALL) MessageTypes.OUTGOING_VIDEO_CALL_TYPE else MessageTypes.OUTGOING_AUDIO_CALL_TYPE
        }
      }

      override fun serialize(data: Call): Cursor {
        throw UnsupportedOperationException()
      }

      override fun deserialize(data: Cursor): Call {
        return Call(
          callId = data.requireLong(CALL_ID),
          peer = RecipientId.from(data.requireLong(PEER)),
          type = data.requireObject(TYPE, Type.Serializer),
          direction = data.requireObject(DIRECTION, Direction.Serializer),
          event = data.requireObject(EVENT, Event.Serializer),
          messageId = data.requireLong(MESSAGE_ID).takeIf { it > 0L },
          timestamp = data.requireLong(TIMESTAMP),
          ringerRecipient = data.requireLong(RINGER).let {
            if (it > 0) {
              RecipientId.from(it)
            } else {
              null
            }
          }
        )
      }
    }
  }

  enum class Type(private val code: Int) {
    AUDIO_CALL(0),
    VIDEO_CALL(1),
    GROUP_CALL(3),
    AD_HOC_CALL(4);

    companion object Serializer : IntSerializer<Type> {
      override fun serialize(data: Type): Int = data.code

      override fun deserialize(data: Int): Type {
        return when (data) {
          AUDIO_CALL.code -> AUDIO_CALL
          VIDEO_CALL.code -> VIDEO_CALL
          GROUP_CALL.code -> GROUP_CALL
          AD_HOC_CALL.code -> AD_HOC_CALL
          else -> throw IllegalArgumentException("Unknown type $data")
        }
      }

      @JvmStatic
      fun from(type: CallEvent.Type): Type? {
        return when (type) {
          CallEvent.Type.UNKNOWN_TYPE -> null
          CallEvent.Type.AUDIO_CALL -> AUDIO_CALL
          CallEvent.Type.VIDEO_CALL -> VIDEO_CALL
          CallEvent.Type.GROUP_CALL -> GROUP_CALL
          CallEvent.Type.AD_HOC_CALL -> AD_HOC_CALL
        }
      }
    }
  }

  enum class Direction(private val code: Int) {
    INCOMING(0),
    OUTGOING(1);

    companion object Serializer : IntSerializer<Direction> {
      override fun serialize(data: Direction): Int = data.code

      override fun deserialize(data: Int): Direction {
        return when (data) {
          INCOMING.code -> INCOMING
          OUTGOING.code -> OUTGOING
          else -> throw IllegalArgumentException("Unknown type $data")
        }
      }

      @JvmStatic
      fun from(direction: CallEvent.Direction): Direction? {
        return when (direction) {
          CallEvent.Direction.UNKNOWN_DIRECTION -> null
          CallEvent.Direction.INCOMING -> INCOMING
          CallEvent.Direction.OUTGOING -> OUTGOING
        }
      }
    }
  }

  sealed interface CallConversationId {
    data class Peer(val recipientId: RecipientId) : CallConversationId
    data class CallLink(val callLinkId: Int) : CallConversationId
  }

  enum class Event(private val code: Int) {
    /**
     * 1:1 Calls only.
     */
    ONGOING(0),

    /**
     * 1:1 and Group Calls.
     *
     * Group calls: You accepted a ring.
     */
    ACCEPTED(1),

    /**
     * 1:1 Calls only.
     */
    NOT_ACCEPTED(2),

    /**
     * 1:1 and Group/Ad-Hoc Calls.
     *
     * Group calls: The remote ring has expired or was cancelled by the ringer.
     */
    MISSED(3),

    /**
     * 1:1 and Group/Ad-Hoc Calls.
     */
    DELETE(4),

    /**
     * Group/Ad-Hoc Calls only.
     *
     * Initial state.
     */
    GENERIC_GROUP_CALL(5),

    /**
     * Group Calls: User has joined the group call.
     */
    JOINED(6),

    /**
     * Group Calls: If a ring was requested by another user.
     */
    RINGING(7),

    /**
     * Group Calls: If you declined a ring.
     */
    DECLINED(8),

    /**
     * Group Calls: If you are ringing a group.
     */
    OUTGOING_RING(9);

    companion object Serializer : IntSerializer<Event> {
      override fun serialize(data: Event): Int = data.code

      override fun deserialize(data: Int): Event {
        return values().firstOrNull {
          it.code == data
        } ?: throw IllegalArgumentException("Unknown event $data")
      }

      @JvmStatic
      fun from(event: CallEvent.Event): Event? {
        return when (event) {
          CallEvent.Event.UNKNOWN_ACTION -> null
          CallEvent.Event.ACCEPTED -> ACCEPTED
          CallEvent.Event.NOT_ACCEPTED -> NOT_ACCEPTED
          CallEvent.Event.DELETE -> DELETE
        }
      }
    }
  }
}
