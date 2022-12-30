package org.thoughtcrime.securesms.database

import android.content.Context
import android.database.Cursor
import androidx.core.content.contentValuesOf
import org.signal.core.util.IntSerializer
import org.signal.core.util.Serializer
import org.signal.core.util.SqlUtil
import org.signal.core.util.logging.Log
import org.signal.core.util.readToList
import org.signal.core.util.readToSingleObject
import org.signal.core.util.requireLong
import org.signal.core.util.requireObject
import org.signal.core.util.select
import org.signal.core.util.update
import org.signal.core.util.withinTransaction
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.recipients.RecipientId
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.SyncMessage.CallEvent

/**
 * Contains details for each 1:1 call.
 */
class CallTable(context: Context, databaseHelper: SignalDatabase) : DatabaseTable(context, databaseHelper), RecipientIdDatabaseReference {

  companion object {
    private val TAG = Log.tag(CallTable::class.java)

    private const val TABLE_NAME = "call"
    private const val ID = "_id"
    private const val CALL_ID = "call_id"
    private const val MESSAGE_ID = "message_id"
    private const val PEER = "peer"
    private const val TYPE = "type"
    private const val DIRECTION = "direction"
    private const val EVENT = "event"

    val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY,
        $CALL_ID INTEGER NOT NULL UNIQUE,
        $MESSAGE_ID INTEGER NOT NULL REFERENCES ${MessageTable.TABLE_NAME} (${MessageTable.ID}) ON DELETE CASCADE,
        $PEER INTEGER NOT NULL REFERENCES ${RecipientTable.TABLE_NAME} (${RecipientTable.ID}) ON DELETE CASCADE,
        $TYPE INTEGER NOT NULL,
        $DIRECTION INTEGER NOT NULL,
        $EVENT INTEGER NOT NULL
      )
    """.trimIndent()

    val CREATE_INDEXES = arrayOf(
      "CREATE INDEX call_call_id_index ON $TABLE_NAME ($CALL_ID)",
      "CREATE INDEX call_message_id_index ON $TABLE_NAME ($MESSAGE_ID)"
    )
  }

  fun insertCall(callId: Long, timestamp: Long, peer: RecipientId, type: Type, direction: Direction, event: Event) {
    val messageType: Long = Call.getMessageType(type, direction, event)

    writableDatabase.withinTransaction {
      val result = SignalDatabase.messages.insertCallLog(peer, messageType, timestamp)

      val values = contentValuesOf(
        CALL_ID to callId,
        MESSAGE_ID to result.messageId,
        PEER to peer.serialize(),
        TYPE to Type.serialize(type),
        DIRECTION to Direction.serialize(direction),
        EVENT to Event.serialize(event)
      )

      writableDatabase.insert(TABLE_NAME, null, values)
    }

    ApplicationDependencies.getMessageNotifier().updateNotification(context)

    Log.i(TAG, "Inserted call: $callId type: $type direction: $direction event:$event")
  }

  fun updateCall(callId: Long, event: Event): Call? {
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

        SignalDatabase.messages.updateCallLog(call.messageId, call.messageType)
        ApplicationDependencies.getMessageNotifier().updateNotification(context)
      }

      call
    }
  }

  fun getCallById(callId: Long): Call? {
    return readableDatabase
      .select()
      .from(TABLE_NAME)
      .where("$CALL_ID = ?", callId)
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
    val calls = mutableMapOf<Long, Call>()
    val queries = SqlUtil.buildCollectionQuery(MESSAGE_ID, messageIds)

    queries.forEach { query ->
      val cursor = readableDatabase
        .select()
        .from(TABLE_NAME)
        .where(query.where, query.whereArgs)
        .run()

      calls.putAll(cursor.readToList { c -> c.requireLong(MESSAGE_ID) to Call.deserialize(c) })
    }
    return calls
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
    val messageId: Long
  ) {
    val messageType: Long = getMessageType(type, direction, event)

    companion object Deserializer : Serializer<Call, Cursor> {
      fun getMessageType(type: Type, direction: Direction, event: Event): Long {
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
          messageId = data.requireLong(MESSAGE_ID)
        )
      }
    }
  }

  enum class Type(private val code: Int) {
    AUDIO_CALL(0),
    VIDEO_CALL(1);

    companion object Serializer : IntSerializer<Type> {
      override fun serialize(data: Type): Int = data.code

      override fun deserialize(data: Int): Type {
        return when (data) {
          AUDIO_CALL.code -> AUDIO_CALL
          VIDEO_CALL.code -> VIDEO_CALL
          else -> throw IllegalArgumentException("Unknown type $data")
        }
      }

      @JvmStatic
      fun from(type: CallEvent.Type): Type? {
        return when (type) {
          CallEvent.Type.UNKNOWN_TYPE -> null
          CallEvent.Type.AUDIO_CALL -> AUDIO_CALL
          CallEvent.Type.VIDEO_CALL -> VIDEO_CALL
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

  enum class Event(private val code: Int) {
    ONGOING(0),
    ACCEPTED(1),
    NOT_ACCEPTED(2),
    MISSED(3);

    companion object Serializer : IntSerializer<Event> {
      override fun serialize(data: Event): Int = data.code

      override fun deserialize(data: Int): Event {
        return when (data) {
          ONGOING.code -> ONGOING
          ACCEPTED.code -> ACCEPTED
          NOT_ACCEPTED.code -> NOT_ACCEPTED
          MISSED.code -> MISSED
          else -> throw IllegalArgumentException("Unknown type $data")
        }
      }

      @JvmStatic
      fun from(event: CallEvent.Event): Event? {
        return when (event) {
          CallEvent.Event.UNKNOWN_ACTION -> null
          CallEvent.Event.ACCEPTED -> ACCEPTED
          CallEvent.Event.NOT_ACCEPTED -> NOT_ACCEPTED
        }
      }
    }
  }
}
