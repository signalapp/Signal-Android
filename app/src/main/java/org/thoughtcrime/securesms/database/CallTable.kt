package org.thoughtcrime.securesms.database

import android.content.Context
import android.database.Cursor
import androidx.annotation.Discouraged
import androidx.core.content.contentValuesOf
import org.signal.core.util.IntSerializer
import org.signal.core.util.Serializer
import org.signal.core.util.SqlUtil
import org.signal.core.util.count
import org.signal.core.util.delete
import org.signal.core.util.deleteAll
import org.signal.core.util.exists
import org.signal.core.util.flatten
import org.signal.core.util.insertInto
import org.signal.core.util.isAbsent
import org.signal.core.util.logging.Log
import org.signal.core.util.readToList
import org.signal.core.util.readToMap
import org.signal.core.util.readToSingleLong
import org.signal.core.util.readToSingleObject
import org.signal.core.util.requireBoolean
import org.signal.core.util.requireLong
import org.signal.core.util.requireNonNullString
import org.signal.core.util.requireObject
import org.signal.core.util.requireString
import org.signal.core.util.select
import org.signal.core.util.toInt
import org.signal.core.util.toSingleLine
import org.signal.core.util.update
import org.signal.core.util.withinTransaction
import org.signal.ringrtc.CallId
import org.signal.ringrtc.CallManager.RingUpdate
import org.thoughtcrime.securesms.calls.log.CallLogFilter
import org.thoughtcrime.securesms.calls.log.CallLogRow
import org.thoughtcrime.securesms.database.model.GroupCallUpdateDetailsUtil
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.CallLinkUpdateSendJob
import org.thoughtcrime.securesms.jobs.CallSyncEventJob
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.service.webrtc.links.CallLinkRoomId
import org.whispersystems.signalservice.api.push.ServiceId.ACI
import org.whispersystems.signalservice.internal.push.SyncMessage.CallEvent
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Contains details for each 1:1 call.
 */
class CallTable(context: Context, databaseHelper: SignalDatabase) : DatabaseTable(context, databaseHelper), RecipientIdDatabaseReference {

  companion object {
    private val TAG = Log.tag(CallTable::class.java)
    private val TIME_WINDOW = TimeUnit.HOURS.toMillis(4)

    const val TABLE_NAME = "call"
    const val ID = "_id"
    const val CALL_ID = "call_id"
    const val MESSAGE_ID = "message_id"
    const val PEER = "peer"
    const val TYPE = "type"
    const val DIRECTION = "direction"
    const val EVENT = "event"
    const val TIMESTAMP = "timestamp"
    const val RINGER = "ringer"
    const val DELETION_TIMESTAMP = "deletion_timestamp"
    const val READ = "read"

    /**
     * Whether a given call event was joined by the local user
     *
     * Used to determine if a group call in the "GENERIC_GROUP_CALL" state is to be
     * displayed as a missed call in the ui
     */
    const val LOCAL_JOINED = "local_joined"

    /**
     * Whether a given call event is currently considered active.
     *
     * Used to determine if a group call in the "GENERIC_GROUP_CALL" state is to be
     * displayed as a missed call in the ui
     */
    const val GROUP_CALL_ACTIVE = "group_call_active"

    //language=sql
    const val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY,
        $CALL_ID INTEGER NOT NULL,
        $MESSAGE_ID INTEGER DEFAULT NULL REFERENCES ${MessageTable.TABLE_NAME} (${MessageTable.ID}) ON DELETE SET NULL,
        $PEER INTEGER NOT NULL REFERENCES ${RecipientTable.TABLE_NAME} (${RecipientTable.ID}) ON DELETE CASCADE,
        $TYPE INTEGER NOT NULL,
        $DIRECTION INTEGER NOT NULL,
        $EVENT INTEGER NOT NULL,
        $TIMESTAMP INTEGER NOT NULL,
        $RINGER INTEGER DEFAULT NULL,
        $DELETION_TIMESTAMP INTEGER DEFAULT 0,
        $READ INTEGER DEFAULT 1,
        $LOCAL_JOINED INTEGER DEFAULT 0,
        $GROUP_CALL_ACTIVE INTEGER DEFAULT 0,
        UNIQUE ($CALL_ID, $PEER) ON CONFLICT FAIL
      )
    """

    const val CALL_LOG_INDEX = "call_log_index"

    val CREATE_INDEXES = arrayOf(
      "CREATE INDEX call_call_id_index ON $TABLE_NAME ($CALL_ID)",
      "CREATE INDEX call_message_id_index ON $TABLE_NAME ($MESSAGE_ID)",
      "CREATE INDEX call_peer_index ON $TABLE_NAME ($PEER)",
      "CREATE INDEX $CALL_LOG_INDEX ON $TABLE_NAME ($TIMESTAMP, $PEER, $EVENT, $TYPE, $DELETION_TIMESTAMP)"
    )
  }
  fun markAllCallEventsRead(timestamp: Long = Long.MAX_VALUE) {
    val updateCount = writableDatabase
      .update(TABLE_NAME)
      .values(READ to ReadState.serialize(ReadState.READ))
      .where("$TIMESTAMP <= ? AND $READ != ?", timestamp, ReadState.serialize(ReadState.READ))
      .run()

    if (updateCount > 0) {
      notifyConversationListListeners()
    }
  }

  fun markAllCallEventsWithPeerBeforeTimestampRead(peer: RecipientId, timestamp: Long): Call? {
    val latestCallAsOfTimestamp = writableDatabase.withinTransaction { db ->
      val updated = db.update(TABLE_NAME)
        .values(READ to ReadState.serialize(ReadState.READ))
        .where("$PEER = ? AND $TIMESTAMP <= ?", peer.toLong(), timestamp)
        .run()

      if (updated == 0) {
        null
      } else {
        db.select()
          .from(TABLE_NAME)
          .where("$PEER = ? AND $TIMESTAMP <= ?", peer.toLong(), timestamp)
          .orderBy("$TIMESTAMP DESC")
          .limit(1)
          .run()
          .readToSingleObject(Call.Deserializer)
      }
    }

    notifyConversationListListeners()
    return latestCallAsOfTimestamp
  }

  fun getUnreadMissedCallCount(): Long {
    return readableDatabase
      .count()
      .from(TABLE_NAME)
      .where("$EVENT = ? AND $READ = ?", Event.serialize(Event.MISSED), ReadState.serialize(ReadState.UNREAD))
      .run()
      .readToSingleLong()
  }

  fun insertOneToOneCall(callId: Long, timestamp: Long, peer: RecipientId, type: Type, direction: Direction, event: Event) {
    val messageType: Long = Call.getMessageType(type, direction, event)

    writableDatabase.withinTransaction {
      val result = SignalDatabase.messages.insertCallLog(peer, messageType, timestamp, direction == Direction.OUTGOING)
      val values = contentValuesOf(
        CALL_ID to callId,
        MESSAGE_ID to result.messageId,
        PEER to peer.serialize(),
        TYPE to Type.serialize(type),
        DIRECTION to Direction.serialize(direction),
        EVENT to Event.serialize(event),
        TIMESTAMP to timestamp,
        READ to ReadState.serialize(ReadState.UNREAD)
      )

      writableDatabase.insert(TABLE_NAME, null, values)
    }

    AppDependencies.messageNotifier.updateNotification(context)
    AppDependencies.databaseObserver.notifyCallUpdateObservers()

    Log.i(TAG, "Inserted call: $callId type: $type direction: $direction event:$event")
  }

  fun updateOneToOneCall(callId: Long, event: Event): Call? {
    return writableDatabase.withinTransaction {
      writableDatabase
        .update(TABLE_NAME)
        .values(
          EVENT to Event.serialize(event),
          READ to ReadState.serialize(ReadState.UNREAD)
        )
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

        if (call.messageId == null) {
          Log.w(TAG, "Call does not have an associated message id! No message to update.")
        } else {
          SignalDatabase.messages.updateCallLog(call.messageId, call.messageType)
        }

        AppDependencies.messageNotifier.updateNotification(context)
        AppDependencies.databaseObserver.notifyCallUpdateObservers()
      }

      call
    }
  }

  fun getCallById(callId: Long, recipientId: RecipientId): Call? {
    val query = getCallSelectionQuery(callId, recipientId)

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

  fun getCallLinkRoomIdsFromCallRowIds(callRowIds: Set<Long>): Set<CallLinkRoomId> {
    return SqlUtil.buildCollectionQuery("$TABLE_NAME.$ID", callRowIds).map { query ->
      //language=sql
      val statement = """
        SELECT ${CallLinkTable.ROOM_ID} FROM $TABLE_NAME
        INNER JOIN ${CallLinkTable.TABLE_NAME} ON ${CallLinkTable.TABLE_NAME}.${CallLinkTable.RECIPIENT_ID} = $PEER
        WHERE $TYPE = ${Type.serialize(Type.AD_HOC_CALL)} AND ${query.where}
      """.toSingleLine()

      readableDatabase.query(statement, query.whereArgs).readToList {
        CallLinkRoomId.DatabaseSerializer.deserialize(it.requireNonNullString(CallLinkTable.ROOM_ID))
      }
    }.flatten().toSet()
  }

  /**
   * If a call link has been revoked, or if we do not have a CallLink table entry for an AD_HOC_CALL type
   * event, we mark it deleted.
   */
  fun updateAdHocCallEventDeletionTimestamps(skipSync: Boolean = false) {
    //language=sql
    val statement = """
      UPDATE $TABLE_NAME
      SET $DELETION_TIMESTAMP = ${System.currentTimeMillis()}, $EVENT = ${Event.serialize(Event.DELETE)}
      WHERE $TYPE = ${Type.serialize(Type.AD_HOC_CALL)}
      AND (
        (NOT EXISTS (SELECT 1 FROM ${CallLinkTable.TABLE_NAME} WHERE ${CallLinkTable.RECIPIENT_ID} = $PEER))
        OR
        (SELECT ${CallLinkTable.REVOKED} FROM ${CallLinkTable.TABLE_NAME} WHERE ${CallLinkTable.RECIPIENT_ID} = $PEER)
      )
      RETURNING *
    """.toSingleLine()

    val toSync = writableDatabase.query(statement).readToList {
      Call.deserialize(it)
    }.toSet()

    if (!skipSync) {
      CallSyncEventJob.enqueueDeleteSyncEvents(toSync)
    }

    AppDependencies.deletedCallEventManager.scheduleIfNecessary()
    AppDependencies.databaseObserver.notifyCallUpdateObservers()
  }

  /**
   * If a non-ad-hoc call has been deleted from the message database, then we need to
   * set its deletion_timestamp to now.
   */
  fun updateCallEventDeletionTimestamps(skipSync: Boolean = false) {
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

    if (!skipSync) {
      CallSyncEventJob.enqueueDeleteSyncEvents(toSync)
    }

    AppDependencies.deletedCallEventManager.scheduleIfNecessary()
    AppDependencies.databaseObserver.notifyCallUpdateObservers()
  }

  /**
   * Marks the given call event DELETED. This deletes the associated message, but
   * keeps the call event around for several hours to ensure out of order messages
   * do not bring it back.
   */
  fun markCallDeletedFromSyncEvent(call: Call) {
    val filter: SqlUtil.Query = getCallSelectionQuery(call.callId, call.peer)

    writableDatabase.withinTransaction { db ->
      db
        .update(TABLE_NAME)
        .values(
          EVENT to Event.serialize(Event.DELETE),
          DELETION_TIMESTAMP to System.currentTimeMillis()
        )
        .where(filter.where, filter.whereArgs)
        .run()

      if (call.messageId != null) {
        SignalDatabase.messages.deleteMessage(call.messageId)
      }
    }

    AppDependencies.messageNotifier.updateNotification(context)
    AppDependencies.databaseObserver.notifyCallUpdateObservers()
    Log.d(TAG, "Marked call event for deletion: ${call.callId}")
  }

  /**
   * Inserts a call event in the DELETED state with the corresponding data.
   * Deleted calls are kept around for several hours to ensure they don't reappear
   * due to out of order messages.
   */
  fun insertDeletedCallFromSyncEvent(
    callId: Long,
    recipientId: RecipientId,
    type: Type,
    direction: Direction,
    timestamp: Long
  ) {
    writableDatabase
      .insertInto(TABLE_NAME)
      .values(
        CALL_ID to callId,
        MESSAGE_ID to null,
        PEER to recipientId.toLong(),
        EVENT to Event.serialize(Event.DELETE),
        TYPE to Type.serialize(type),
        DIRECTION to Direction.serialize(direction),
        TIMESTAMP to timestamp,
        DELETION_TIMESTAMP to System.currentTimeMillis()
      )
      .run(SQLiteDatabase.CONFLICT_ABORT)

    AppDependencies.deletedCallEventManager.scheduleIfNecessary()
    Log.d(TAG, "Inserted deleted call event: $callId, $type, $direction, $timestamp")
  }

  // region Group / Ad-Hoc Calling

  fun acceptIncomingGroupCall(call: Call) {
    checkIsGroupOrAdHocCall(call)

    val newEvent = when (call.event) {
      Event.RINGING, Event.MISSED, Event.MISSED_NOTIFICATION_PROFILE, Event.DECLINED -> Event.ACCEPTED
      Event.GENERIC_GROUP_CALL -> Event.JOINED
      else -> {
        Log.d(TAG, "[acceptIncomingGroupCall] Call in state ${call.event} cannot be transitioned by ACCEPTED")
        return
      }
    }

    writableDatabase
      .update(TABLE_NAME)
      .values(EVENT to Event.serialize(newEvent))
      .where("$CALL_ID = ?", call.callId)
      .run()

    AppDependencies.messageNotifier.updateNotification(context)
    AppDependencies.databaseObserver.notifyCallUpdateObservers()
    Log.d(TAG, "[acceptIncomingGroupCall] Transitioned group call ${call.callId} from ${call.event} to $newEvent")
  }

  fun acceptOutgoingGroupCall(call: Call) {
    checkIsGroupOrAdHocCall(call)

    val newEvent = when (call.event) {
      Event.GENERIC_GROUP_CALL, Event.JOINED -> Event.OUTGOING_RING
      Event.RINGING, Event.MISSED, Event.MISSED_NOTIFICATION_PROFILE, Event.DECLINED, Event.ACCEPTED -> {
        Log.w(TAG, "[acceptOutgoingGroupCall] This shouldn't have been an outgoing ring because the call already existed!")
        Event.ACCEPTED
      }

      else -> {
        Log.d(TAG, "[acceptOutgoingGroupCall] Call in state ${call.event} cannot be transitioned by ACCEPTED")
        return
      }
    }

    writableDatabase
      .update(TABLE_NAME)
      .values(EVENT to Event.serialize(newEvent), DIRECTION to Direction.serialize(Direction.OUTGOING))
      .where("$CALL_ID = ?", call.callId)
      .run()

    AppDependencies.messageNotifier.updateNotification(context)
    AppDependencies.databaseObserver.notifyCallUpdateObservers()
    Log.d(TAG, "[acceptOutgoingGroupCall] Transitioned group call ${call.callId} from ${call.event} to $newEvent")
  }

  fun declineIncomingGroupCall(call: Call) {
    checkIsGroupOrAdHocCall(call)
    check(call.direction == Direction.INCOMING)

    val newEvent = when (call.event) {
      Event.GENERIC_GROUP_CALL, Event.RINGING, Event.MISSED, Event.MISSED_NOTIFICATION_PROFILE -> Event.DECLINED
      Event.JOINED -> Event.ACCEPTED
      else -> {
        Log.d(TAG, "Call in state ${call.event} cannot be transitioned by DECLINED")
        return
      }
    }

    writableDatabase
      .update(TABLE_NAME)
      .values(EVENT to Event.serialize(newEvent))
      .where("$CALL_ID = ?", call.callId)
      .run()

    AppDependencies.messageNotifier.updateNotification(context)
    AppDependencies.databaseObserver.notifyCallUpdateObservers()
    Log.d(TAG, "Transitioned group call ${call.callId} from ${call.event} to $newEvent")
  }

  fun insertAcceptedGroupCall(
    callId: Long,
    recipientId: RecipientId,
    direction: Direction,
    timestamp: Long
  ) {
    val recipient = Recipient.resolved(recipientId)
    val type = if (recipient.isCallLink) Type.AD_HOC_CALL else Type.GROUP_CALL
    val event = if (direction == Direction.OUTGOING) Event.OUTGOING_RING else Event.JOINED
    val ringer = if (direction == Direction.OUTGOING) Recipient.self().id.toLong() else null

    writableDatabase.withinTransaction { db ->
      val messageId: MessageId? = if (type == Type.GROUP_CALL) {
        SignalDatabase.messages.insertGroupCall(
          groupRecipientId = recipientId,
          sender = Recipient.self().id,
          timestamp,
          "",
          emptyList(),
          false,
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
          PEER to recipientId.toLong(),
          EVENT to Event.serialize(event),
          TYPE to Type.serialize(type),
          DIRECTION to Direction.serialize(direction),
          TIMESTAMP to timestamp,
          RINGER to ringer,
          LOCAL_JOINED to true
        )
        .run(SQLiteDatabase.CONFLICT_ABORT)
    }

    AppDependencies.databaseObserver.notifyCallUpdateObservers()
  }

  fun insertDeclinedGroupCall(
    callId: Long,
    recipientId: RecipientId,
    timestamp: Long
  ) {
    val recipient = Recipient.resolved(recipientId)
    val type = if (recipient.isCallLink) Type.AD_HOC_CALL else Type.GROUP_CALL

    writableDatabase.withinTransaction { db ->
      val messageId: MessageId? = if (type == Type.GROUP_CALL) {
        SignalDatabase.messages.insertGroupCall(
          groupRecipientId = recipientId,
          sender = Recipient.self().id,
          timestamp,
          "",
          emptyList(),
          false,
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
          PEER to recipientId.toLong(),
          EVENT to Event.serialize(Event.DECLINED),
          TYPE to Type.serialize(type),
          DIRECTION to Direction.serialize(Direction.INCOMING),
          TIMESTAMP to timestamp,
          RINGER to null,
          LOCAL_JOINED to false
        )
        .run(SQLiteDatabase.CONFLICT_ABORT)
    }

    AppDependencies.databaseObserver.notifyCallUpdateObservers()
  }

  fun insertOrUpdateAdHocCallFromRemoteObserveEvent(
    callRecipient: Recipient,
    timestamp: Long,
    callId: Long
  ) {
    handleCallLinkUpdate(callRecipient, timestamp, CallId(callId), Direction.INCOMING, skipSyncOnInsert = true)
  }

  fun insertAdHocCallFromLocalObserveEvent(
    callRecipient: Recipient,
    timestamp: Long,
    eraId: String
  ): Boolean {
    return handleCallLinkUpdate(callRecipient, timestamp, CallId.fromEra(eraId), Direction.INCOMING, skipTimestampUpdate = true)
  }

  fun insertOrUpdateGroupCallFromLocalEvent(
    groupRecipientId: RecipientId,
    sender: RecipientId,
    timestamp: Long,
    peekGroupCallEraId: String?,
    peekJoinedUuids: Collection<UUID>,
    isCallFull: Boolean
  ) {
    val recipient = Recipient.resolved(groupRecipientId)
    if (recipient.isCallLink) {
      handleCallLinkUpdate(recipient, timestamp, peekGroupCallEraId?.let { CallId.fromEra(it) })
    } else {
      handleGroupUpdate(recipient, sender, timestamp, peekGroupCallEraId, peekJoinedUuids, isCallFull)
    }
  }

  private fun handleGroupUpdate(
    groupRecipient: Recipient,
    sender: RecipientId,
    timestamp: Long,
    peekGroupCallEraId: String?,
    peekJoinedUuids: Collection<UUID>,
    isCallFull: Boolean
  ) {
    check(groupRecipient.isPushV2Group)
    writableDatabase.withinTransaction {
      if (peekGroupCallEraId.isNullOrEmpty()) {
        Log.w(TAG, "Dropping local call event with null era id.")
        return@withinTransaction
      }

      val callId = CallId.fromEra(peekGroupCallEraId).longValue()
      val call = getCallById(callId, groupRecipient.id)
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
          isCallFull,
          call.event == Event.RINGING
        )
      } else {
        SignalDatabase.messages.insertGroupCall(
          groupRecipient.id,
          sender,
          timestamp,
          peekGroupCallEraId,
          peekJoinedUuids,
          isCallFull,
          false
        )
      }

      insertCallEventFromGroupUpdate(
        callId = callId,
        messageId = messageId,
        sender = sender,
        groupRecipientId = groupRecipient.id,
        timestamp = timestamp,
        didLocalUserJoin = peekJoinedUuids.contains(Recipient.self().requireServiceId().rawUuid),
        isGroupCallActive = peekJoinedUuids.isNotEmpty()
      )
    }
  }

  /**
   * @return Whether or not a new row was inserted.
   */
  private fun handleCallLinkUpdate(
    callLinkRecipient: Recipient,
    timestamp: Long,
    callId: CallId?,
    direction: Direction = Direction.OUTGOING,
    skipTimestampUpdate: Boolean = false,
    skipSyncOnInsert: Boolean = false
  ): Boolean {
    check(callLinkRecipient.isCallLink)

    if (callId == null) {
      return false
    }

    val didInsert = writableDatabase.withinTransaction { db ->
      val exists = db.exists(TABLE_NAME)
        .where("$PEER = ? AND $CALL_ID = ?", callLinkRecipient.id.serialize(), callId.longValue())
        .run()

      if (exists && !skipTimestampUpdate) {
        val updated = db.update(TABLE_NAME)
          .values(TIMESTAMP to timestamp)
          .where("$PEER = ? AND $CALL_ID = ? AND $TIMESTAMP < ?", callLinkRecipient.id.serialize(), callId.longValue(), timestamp)
          .run() > 0

        if (updated) {
          Log.d(TAG, "Updated call event for call link. Call Id: $callId")
          AppDependencies.databaseObserver.notifyCallUpdateObservers()
        }

        false
      } else if (!exists) {
        db.insertInto(TABLE_NAME)
          .values(
            CALL_ID to callId.longValue(),
            MESSAGE_ID to null,
            PEER to callLinkRecipient.id.toLong(),
            EVENT to Event.serialize(Event.GENERIC_GROUP_CALL),
            TYPE to Type.serialize(Type.AD_HOC_CALL),
            DIRECTION to Direction.serialize(direction),
            TIMESTAMP to timestamp,
            RINGER to null
          ).run(SQLiteDatabase.CONFLICT_ABORT)

        Log.d(TAG, "Inserted new call event for call link. Call Id: $callId")
        AppDependencies.databaseObserver.notifyCallUpdateObservers()

        if (!skipSyncOnInsert) {
          AppDependencies.jobManager.add(CallLinkUpdateSendJob(callLinkRecipient.requireCallLinkRoomId()))
        }

        true
      } else false
    }

    return didInsert
  }

  private fun insertCallEventFromGroupUpdate(
    callId: Long,
    messageId: MessageId?,
    sender: RecipientId,
    groupRecipientId: RecipientId,
    timestamp: Long,
    didLocalUserJoin: Boolean,
    isGroupCallActive: Boolean
  ) {
    if (messageId != null) {
      val call = getCallById(callId, groupRecipientId)
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
            RINGER to null,
            LOCAL_JOINED to didLocalUserJoin,
            GROUP_CALL_ACTIVE to isGroupCallActive
          )
          .run(SQLiteDatabase.CONFLICT_ABORT)

        Log.d(TAG, "Inserted new call event from group call update message. Call Id: $callId")
      } else {
        if (timestamp < call.timestamp) {
          setTimestamp(callId, groupRecipientId, timestamp)
          Log.d(TAG, "Updated call event timestamp for call id $callId")
        }

        if (call.messageId == null) {
          setMessageId(callId, messageId)
          Log.d(TAG, "Updated call event message id for newly inserted group call state: $callId")
        }

        updateGroupCallState(call, didLocalUserJoin, isGroupCallActive)
      }
    } else {
      Log.d(TAG, "Skipping call event processing for null era id.")
    }

    AppDependencies.databaseObserver.notifyCallUpdateObservers()
  }

  /**
   * Update necessary call info from peek
   */
  fun updateGroupCallFromPeek(
    threadId: Long,
    peekGroupCallEraId: String?,
    peekJoinedUuids: Collection<UUID>,
    isCallFull: Boolean
  ) {
    val callId = peekGroupCallEraId?.let { CallId.fromEra(it) }
    val recipientId = SignalDatabase.threads.getRecipientIdForThreadId(threadId)
    val call = if (callId != null && recipientId != null) {
      getCallById(callId.longValue(), recipientId)
    } else {
      null
    }

    SignalDatabase.messages.updatePreviousGroupCall(
      threadId = threadId,
      peekGroupCallEraId = peekGroupCallEraId,
      peekJoinedUuids = peekJoinedUuids,
      isCallFull = isCallFull,
      isRingingOnLocalDevice = call?.event == Event.RINGING
    )

    if (call != null) {
      updateGroupCallState(call, peekJoinedUuids)
      AppDependencies.databaseObserver.notifyCallUpdateObservers()
    }
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

  @JvmOverloads
  fun insertOrUpdateGroupCallFromRingState(
    ringId: Long,
    groupRecipientId: RecipientId,
    ringerAci: ACI,
    dateReceived: Long,
    ringState: RingUpdate,
    dueToNotificationProfile: Boolean = false
  ) {
    val ringerRecipient = Recipient.externalPush(ringerAci)
    handleGroupRingState(ringId, groupRecipientId, ringerRecipient.id, dateReceived, ringState, dueToNotificationProfile)
  }

  fun isRingCancelled(ringId: Long, groupRecipientId: RecipientId): Boolean {
    val call = getCallById(ringId, groupRecipientId) ?: return false
    return call.event != Event.RINGING && call.event != Event.GENERIC_GROUP_CALL
  }

  /**
   * @return whether or not a change is detected.
   */
  private fun updateGroupCallState(
    call: Call,
    peekJoinedUuids: Collection<UUID>
  ): Boolean {
    return updateGroupCallState(
      call,
      peekJoinedUuids.contains(Recipient.self().requireServiceId().rawUuid),
      peekJoinedUuids.isNotEmpty()
    )
  }

  /**
   * @return Whether or not a change was detected
   */
  private fun updateGroupCallState(
    call: Call,
    hasLocalUserJoined: Boolean,
    isGroupCallActive: Boolean
  ): Boolean {
    val localJoined = call.didLocalUserJoin || hasLocalUserJoined

    return writableDatabase.update(TABLE_NAME)
      .values(
        LOCAL_JOINED to localJoined,
        GROUP_CALL_ACTIVE to isGroupCallActive
      )
      .where(
        "$CALL_ID = ? AND $PEER = ? AND ($LOCAL_JOINED != ? OR $GROUP_CALL_ACTIVE != ?)",
        call.callId,
        call.peer.toLong(),
        localJoined.toInt(),
        isGroupCallActive.toInt()
      )
      .run() > 0
  }

  private fun handleGroupRingState(
    ringId: Long,
    groupRecipientId: RecipientId,
    ringerRecipient: RecipientId,
    dateReceived: Long,
    ringState: RingUpdate,
    dueToNotificationProfile: Boolean = false
  ) {
    writableDatabase.withinTransaction {
      Log.d(TAG, "Processing group ring state update for $ringId in state $ringState")

      val call = getCallById(ringId, groupRecipientId)
      if (call != null) {
        if (call.event == Event.DELETE) {
          Log.d(TAG, "Ignoring ring request for $ringId since its event has been deleted.")
          return@withinTransaction
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
              Event.GENERIC_GROUP_CALL, Event.RINGING -> updateEventFromRingState(ringId, if (dueToNotificationProfile) Event.MISSED_NOTIFICATION_PROFILE else Event.MISSED, ringerRecipient)
              Event.JOINED -> updateEventFromRingState(ringId, Event.ACCEPTED, ringerRecipient)
              Event.OUTGOING_RING -> Log.w(TAG, "Received an expiration or cancellation while in OUTGOING_RING state. Ignoring.")
              else -> Unit
            }
          }

          RingUpdate.BUSY_LOCALLY -> {
            when (call.event) {
              Event.JOINED -> updateEventFromRingState(ringId, Event.ACCEPTED)
              Event.GENERIC_GROUP_CALL, Event.RINGING -> updateEventFromRingState(ringId, Event.MISSED)
              else -> {
                updateEventFromRingState(ringId, call.event, ringerRecipient)
                Log.w(TAG, "Received a busy event we can't process. Updating ringer only.")
              }
            }
          }

          RingUpdate.BUSY_ON_ANOTHER_DEVICE -> {
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
              Event.RINGING, Event.MISSED, Event.MISSED_NOTIFICATION_PROFILE, Event.GENERIC_GROUP_CALL -> updateEventFromRingState(ringId, Event.DECLINED)
              Event.JOINED -> updateEventFromRingState(ringId, Event.ACCEPTED)
              Event.OUTGOING_RING -> Log.w(TAG, "Received DECLINED_ON_ANOTHER_DEVICE while in OUTGOING_RING state.")
              else -> Unit
            }
          }
        }
      } else {
        val event: Event = when (ringState) {
          RingUpdate.REQUESTED -> Event.RINGING
          RingUpdate.EXPIRED_REQUEST -> if (dueToNotificationProfile) Event.MISSED_NOTIFICATION_PROFILE else Event.MISSED
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
    }

    AppDependencies.databaseObserver.notifyCallUpdateObservers()
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

    val recipient = Recipient.resolved(groupRecipientId)
    check(recipient.isPushV2Group)

    writableDatabase.withinTransaction { db ->
      val messageId = SignalDatabase.messages.insertGroupCall(
        groupRecipientId = groupRecipientId,
        sender = ringerRecipient,
        timestamp = timestamp,
        eraId = "",
        joinedUuids = emptyList(),
        isCallFull = false,
        isIncomingGroupCallRingingOnLocalDevice = event == Event.RINGING
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
        .run(SQLiteDatabase.CONFLICT_ABORT)
    }

    Log.d(TAG, "Inserted a new group ring event for $callId with event $event")
  }

  fun setTimestamp(callId: Long, recipientId: RecipientId, timestamp: Long) {
    writableDatabase.withinTransaction { db ->
      val call = getCallById(callId, recipientId)
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

    AppDependencies.databaseObserver.notifyCallUpdateObservers()
  }

  private fun setMessageId(callId: Long, messageId: MessageId) {
    writableDatabase
      .update(TABLE_NAME)
      .values(MESSAGE_ID to messageId.id)
      .where("$CALL_ID = ?", callId)
      .run()
  }

  /**
   * Gets the most recent timestamp from the [TIMESTAMP] column
   */
  fun getLatestCall(): Call? {
    val statement = """
      SELECT * FROM $TABLE_NAME ORDER BY $TIMESTAMP DESC LIMIT 1
    """.trimIndent()

    return readableDatabase.query(statement).readToSingleObject { Call.deserialize(it) }
  }

  fun deleteNonAdHocCallEventsOnOrBefore(timestamp: Long) {
    val messageIdsOnOrBeforeTimestamp = """
      SELECT $MESSAGE_ID FROM $TABLE_NAME WHERE $TIMESTAMP <= $timestamp AND $MESSAGE_ID IS NOT NULL
    """.trimIndent()

    writableDatabase.withinTransaction { db ->
      db.delete(MessageTable.TABLE_NAME)
        .where("${MessageTable.ID} IN ($messageIdsOnOrBeforeTimestamp)")
        .run()

      updateCallEventDeletionTimestamps(skipSync = true)
    }
  }

  fun deleteNonAdHocCallEvents(callRowIds: Set<Long>) {
    val messageIds = getMessageIds(callRowIds)
    SignalDatabase.messages.deleteCallUpdates(messageIds)
    updateCallEventDeletionTimestamps()
  }

  fun deleteAllNonAdHocCallEventsExcept(callRowIds: Set<Long>, missedOnly: Boolean) {
    val callFilter = if (missedOnly) {
      "($EVENT = ${Event.serialize(Event.MISSED)} OR $EVENT = ${Event.serialize(Event.MISSED_NOTIFICATION_PROFILE)}) AND $DELETION_TIMESTAMP = 0"
    } else {
      "$DELETION_TIMESTAMP = 0"
    }

    if (callRowIds.isEmpty()) {
      val threadIds = writableDatabase.withinTransaction { db ->
        val ids = db.select(MessageTable.THREAD_ID)
          .from(MessageTable.TABLE_NAME)
          .where(
            """
            ${MessageTable.ID} IN (
              SELECT $MESSAGE_ID FROM $TABLE_NAME
              WHERE $callFilter
            )
          """.toSingleLine()
          )
          .run()
          .readToList { it.requireLong(MessageTable.THREAD_ID) }

        db.delete(MessageTable.TABLE_NAME)
          .where(
            """
            ${MessageTable.ID} IN (
              SELECT $MESSAGE_ID FROM $TABLE_NAME
              WHERE $callFilter
            )
          """.toSingleLine()
          )
          .run()

        ids.toSet()
      }

      threadIds.forEach {
        SignalDatabase.threads.update(
          threadId = it,
          unarchive = false,
          allowDeletion = true
        )
      }

      notifyConversationListeners(threadIds)
      notifyConversationListListeners()
      updateCallEventDeletionTimestamps()
    } else {
      writableDatabase.withinTransaction { db ->
        SqlUtil.buildCollectionQuery(
          column = ID,
          values = callRowIds,
          prefix = "$callFilter AND",
          collectionOperator = SqlUtil.CollectionOperator.NOT_IN
        ).forEach { query ->
          val messageIds = db.select(MESSAGE_ID)
            .from(TABLE_NAME)
            .where(query.where, query.whereArgs)
            .run()
            .readToList { it.requireLong(MESSAGE_ID) }
            .toSet()
          SignalDatabase.messages.deleteCallUpdates(messageIds)
          updateCallEventDeletionTimestamps()
        }
      }
    }
  }

  @Discouraged("Using this method is generally considered an error. Utilize other deletion methods instead of this.")
  fun deleteAllCalls() {
    Log.w(TAG, "Deleting all calls from the local database.")
    writableDatabase.deleteAll(TABLE_NAME)
  }

  private fun getCallSelectionQuery(callId: Long, recipientId: RecipientId): SqlUtil.Query {
    return SqlUtil.Query("$CALL_ID = ? AND $PEER = ?", SqlUtil.buildArgs(callId, recipientId))
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
    val isMissedGenericGroupCall = "$EVENT = ${Event.serialize(Event.GENERIC_GROUP_CALL)} AND $LOCAL_JOINED = ${false.toInt()} AND $GROUP_CALL_ACTIVE = ${false.toInt()}"
    val filterClause: SqlUtil.Query = when (filter) {
      CallLogFilter.ALL -> SqlUtil.buildQuery("$DELETION_TIMESTAMP = 0")
      CallLogFilter.MISSED -> SqlUtil.buildQuery("$TYPE != ${Type.serialize(Type.AD_HOC_CALL)} AND $DIRECTION == ${Direction.serialize(Direction.INCOMING)} AND ($EVENT = ${Event.serialize(Event.MISSED)} OR $EVENT = ${Event.serialize(Event.MISSED_NOTIFICATION_PROFILE)} OR $EVENT = ${Event.serialize(Event.NOT_ACCEPTED)} OR $EVENT = ${Event.serialize(Event.DECLINED)} OR ($isMissedGenericGroupCall)) AND $DELETION_TIMESTAMP = 0")
      CallLogFilter.AD_HOC -> SqlUtil.buildQuery("$TYPE = ${Type.serialize(Type.AD_HOC_CALL)} AND $DELETION_TIMESTAMP = 0")
    }

    val queryClause: SqlUtil.Query = if (!searchTerm.isNullOrEmpty()) {
      val glob = SqlUtil.buildCaseInsensitiveGlobPattern(searchTerm)
      val selection =
        """
        ${RecipientTable.TABLE_NAME}.${RecipientTable.BLOCKED} = ? AND ${RecipientTable.TABLE_NAME}.${RecipientTable.HIDDEN} = ? AND
        (
          sort_name GLOB ? OR 
          ${RecipientTable.TABLE_NAME}.${RecipientTable.USERNAME} GLOB ? OR 
          ${RecipientTable.TABLE_NAME}.${RecipientTable.E164} GLOB ? OR 
          ${RecipientTable.TABLE_NAME}.${RecipientTable.EMAIL} GLOB ?
        )
        """
      SqlUtil.buildQuery(selection, 0, 0, glob, glob, glob, glob)
    } else {
      SqlUtil.buildQuery(
        """
        ${RecipientTable.TABLE_NAME}.${RecipientTable.BLOCKED} = ? AND ${RecipientTable.TABLE_NAME}.${RecipientTable.HIDDEN} = ?
      """,
        0,
        0
      )
    }

    val offsetLimit = if (limit > 0) {
      "LIMIT $offset,$limit"
    } else {
      ""
    }

    val projection = if (isCount) {
      "COUNT(*) OVER() as count"
    } else {
      "p.$ID, p.$TIMESTAMP, $EVENT, $DIRECTION, $PEER, p.$TYPE, $CALL_ID, $MESSAGE_ID, $RINGER, $LOCAL_JOINED, $GROUP_CALL_ACTIVE, children, in_period, ${MessageTable.BODY}"
    }

    val recipientSearchProjection = if (searchTerm.isNullOrEmpty()) {
      ""
    } else {
      """
        ,LOWER(
          COALESCE(
            NULLIF(${GroupTable.TABLE_NAME}.${GroupTable.TITLE}, ''),
            NULLIF(${RecipientTable.TABLE_NAME}.${RecipientTable.NICKNAME_JOINED_NAME}, ''),
            NULLIF(${RecipientTable.TABLE_NAME}.${RecipientTable.NICKNAME_GIVEN_NAME}, ''),
            NULLIF(${RecipientTable.TABLE_NAME}.${RecipientTable.SYSTEM_JOINED_NAME}, ''),
            NULLIF(${RecipientTable.TABLE_NAME}.${RecipientTable.SYSTEM_GIVEN_NAME}, ''),
            NULLIF(${RecipientTable.TABLE_NAME}.${RecipientTable.PROFILE_JOINED_NAME}, ''),
            NULLIF(${RecipientTable.TABLE_NAME}.${RecipientTable.PROFILE_GIVEN_NAME}, ''),
            NULLIF(${RecipientTable.TABLE_NAME}.${RecipientTable.USERNAME}, '')
          )
        ) AS sort_name
      """.trimIndent()
    }

    val join = if (isCount) {
      ""
    } else {
      "LEFT JOIN ${MessageTable.TABLE_NAME} ON ${MessageTable.TABLE_NAME}.${MessageTable.ID} = $MESSAGE_ID"
    }

    // Group call events by those we consider missed or not missed to build out our call log aggregation.
    val eventTypeSubQuery = """
      ($TABLE_NAME.$EVENT = c.$EVENT AND (
        $TABLE_NAME.$EVENT = ${Event.serialize(Event.MISSED)} OR 
        $TABLE_NAME.$EVENT = ${Event.serialize(Event.MISSED_NOTIFICATION_PROFILE)} OR
        $TABLE_NAME.$EVENT = ${Event.serialize(Event.NOT_ACCEPTED)} OR
        $TABLE_NAME.$EVENT = ${Event.serialize(Event.DECLINED)} OR
        ($TABLE_NAME.$isMissedGenericGroupCall)
      )) OR (
        $TABLE_NAME.$EVENT != ${Event.serialize(Event.MISSED)} AND 
        c.$EVENT != ${Event.serialize(Event.MISSED)} AND 
        $TABLE_NAME.$EVENT != ${Event.serialize(Event.MISSED_NOTIFICATION_PROFILE)} AND 
        c.$EVENT != ${Event.serialize(Event.MISSED_NOTIFICATION_PROFILE)} AND
        $TABLE_NAME.$EVENT != ${Event.serialize(Event.NOT_ACCEPTED)} AND
        c.$EVENT != ${Event.serialize(Event.NOT_ACCEPTED)} AND
        $TABLE_NAME.$EVENT != ${Event.serialize(Event.DECLINED)} AND
        c.$EVENT != ${Event.serialize(Event.DECLINED)} AND
        (NOT ($TABLE_NAME.$isMissedGenericGroupCall)) AND
        (NOT (c.$isMissedGenericGroupCall))
      )
      """

    //language=sql
    val statement = """
      SELECT $projection
        $recipientSearchProjection
      FROM (
        WITH cte AS (
          SELECT
            $ID, $TIMESTAMP, $EVENT, $DIRECTION, $PEER, $TYPE, $CALL_ID, $MESSAGE_ID, $RINGER, $LOCAL_JOINED, $GROUP_CALL_ACTIVE,
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
                AND ($eventTypeSubQuery)
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
                AND ($eventTypeSubQuery)
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
            $TABLE_NAME c INDEXED BY $CALL_LOG_INDEX
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
      $join
      LEFT JOIN ${GroupTable.TABLE_NAME} ON ${GroupTable.TABLE_NAME}.${GroupTable.RECIPIENT_ID} = ${RecipientTable.TABLE_NAME}.${RecipientTable.ID}
      WHERE true_parent = p.$ID 
        AND CASE 
          WHEN p.$TYPE = ${Type.serialize(Type.AD_HOC_CALL)} THEN EXISTS (SELECT * FROM ${CallLinkTable.TABLE_NAME} WHERE ${CallLinkTable.RECIPIENT_ID} = $PEER AND ${CallLinkTable.ROOT_KEY} NOT NULL) 
          ELSE 1
        END 
        ${if (queryClause.where.isNotEmpty()) "AND ${queryClause.where}" else ""}
      GROUP BY CASE WHEN p.type = 4 THEN p.peer ELSE p._id END
      ORDER BY p.$TIMESTAMP DESC
      $offsetLimit
    """

    return readableDatabase.query(
      statement,
      queryClause.whereArgs
    )
  }

  fun getLatestRingingCalls(): List<Call> {
    return readableDatabase.select()
      .from(TABLE_NAME)
      .where("$EVENT = ?", Event.serialize(Event.RINGING))
      .limit(10)
      .orderBy(TIMESTAMP)
      .run()
      .readToList {
        Call.deserialize(it)
      }
  }

  fun markRingingCallsAsMissed() {
    writableDatabase.withinTransaction { db ->
      val messageIds: List<Long> = db.select(MESSAGE_ID)
        .from(TABLE_NAME)
        .where("$EVENT = ? AND $MESSAGE_ID != NULL", Event.serialize(Event.RINGING))
        .run()
        .readToList { it.requireLong(MESSAGE_ID) }

      db.update(TABLE_NAME)
        .values(EVENT to Event.serialize(Event.MISSED))
        .where("$EVENT = ?", Event.serialize(Event.RINGING))
        .run()

      SignalDatabase.messages.clearIsRingingOnLocalDeviceFlag(messageIds)
    }
  }

  fun getCallsCount(searchTerm: String?, filter: CallLogFilter): Int {
    return getCallsCursor(true, 0, 1, searchTerm, filter).use {
      if (it.moveToFirst()) {
        it.getInt(0)
      } else {
        0
      }
    }
  }

  fun getCalls(offset: Int, limit: Int, searchTerm: String?, filter: CallLogFilter): List<CallLogRow.Call> {
    return getCallsCursor(false, offset, limit, searchTerm, filter).readToList { cursor ->
      val call = Call.deserialize(cursor)
      val groupCallDetails = GroupCallUpdateDetailsUtil.parse(cursor.requireString(MessageTable.BODY))

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
      val peer = Recipient.resolved(call.peer)

      val canUserBeginCall = if (peer.isGroup) {
        val record = SignalDatabase.groups.getGroup(peer.id)

        !record.isAbsent() &&
          record.get().isActive &&
          (!record.get().isAnnouncementGroup || record.get().memberLevel(Recipient.self()) == GroupTable.MemberLevel.ADMINISTRATOR)
      } else {
        true
      }

      CallLogRow.Call(
        record = call,
        date = call.timestamp,
        peer = peer,
        groupCallState = CallLogRow.GroupCallState.fromDetails(groupCallDetails),
        children = actualChildren.toSet(),
        searchQuery = searchTerm,
        callLinkPeekInfo = AppDependencies.signalCallManager.peekInfoSnapshot[peer.id],
        canUserBeginCall = canUserBeginCall
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

  /**
   * @param isGroupCallActive - Whether the group call currently contains users. Only valid for group calls.
   * @param didLocalUserJoin   - Determines whether the local user joined this call. Only valid for group calls.
   */
  data class Call(
    val callId: Long,
    val peer: RecipientId,
    val type: Type,
    val direction: Direction,
    val event: Event,
    val messageId: Long?,
    val timestamp: Long,
    val ringerRecipient: RecipientId?,
    val isGroupCallActive: Boolean,
    val didLocalUserJoin: Boolean
  ) {
    val messageType: Long = getMessageType(type, direction, event)

    val isDisplayedAsMissedCallInUi = isDisplayedAsMissedCallInUi(this)

    companion object Deserializer : Serializer<Call, Cursor> {

      private fun isDisplayedAsMissedCallInUi(call: Call): Boolean {
        return call.direction == Direction.INCOMING && (call.event in Event.DISPLAY_AS_MISSED_CALL || (call.event == Event.GENERIC_GROUP_CALL && !call.didLocalUserJoin && !call.isGroupCallActive))
      }

      fun getMessageType(type: Type, direction: Direction, event: Event): Long {
        if (type == Type.GROUP_CALL || type == Type.AD_HOC_CALL) {
          return MessageTypes.GROUP_CALL_TYPE
        }

        return if (direction == Direction.INCOMING && event.isMissedCall()) {
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
          },
          isGroupCallActive = data.requireBoolean(GROUP_CALL_ACTIVE),
          didLocalUserJoin = data.requireBoolean(LOCAL_JOINED)
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
      fun from(type: CallEvent.Type?): Type? {
        return when (type) {
          null, CallEvent.Type.UNKNOWN_TYPE -> null
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
      fun from(direction: CallEvent.Direction?): Direction? {
        return when (direction) {
          null, CallEvent.Direction.UNKNOWN_DIRECTION -> null
          CallEvent.Direction.INCOMING -> INCOMING
          CallEvent.Direction.OUTGOING -> OUTGOING
        }
      }
    }
  }

  enum class ReadState(private val code: Int) {
    UNREAD(0),
    READ(1);

    companion object Serializer : IntSerializer<ReadState> {
      override fun serialize(data: ReadState): Int {
        return data.code
      }

      override fun deserialize(data: Int): ReadState {
        return ReadState.values().first { it.code == data }
      }
    }
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
     *
     * Call was auto-declined due to a notification profile.
     */
    MISSED_NOTIFICATION_PROFILE(10),

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
    OUTGOING_RING(9); // Next is 11

    fun isMissedCall(): Boolean {
      return this == MISSED || this == MISSED_NOTIFICATION_PROFILE
    }

    companion object Serializer : IntSerializer<Event> {

      val DISPLAY_AS_MISSED_CALL = listOf(
        MISSED,
        MISSED_NOTIFICATION_PROFILE,
        DECLINED,
        NOT_ACCEPTED
      )

      override fun serialize(data: Event): Int = data.code

      override fun deserialize(data: Int): Event {
        return values().firstOrNull {
          it.code == data
        } ?: throw IllegalArgumentException("Unknown event $data")
      }

      @JvmStatic
      fun from(event: CallEvent.Event?): Event? {
        return when (event) {
          null, CallEvent.Event.UNKNOWN_ACTION, CallEvent.Event.OBSERVED -> null
          CallEvent.Event.ACCEPTED -> ACCEPTED
          CallEvent.Event.NOT_ACCEPTED -> NOT_ACCEPTED
          CallEvent.Event.DELETE -> DELETE
        }
      }
    }
  }
}
