package org.thoughtcrime.securesms.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import androidx.core.content.contentValuesOf
import org.signal.core.util.Serializer
import org.signal.core.util.insertInto
import org.signal.core.util.logging.Log
import org.signal.core.util.readToSingleInt
import org.signal.core.util.readToSingleObject
import org.signal.core.util.requireBlob
import org.signal.core.util.requireBoolean
import org.signal.core.util.requireInt
import org.signal.core.util.requireLong
import org.signal.core.util.requireNonNullBlob
import org.signal.core.util.requireNonNullString
import org.signal.core.util.requireString
import org.signal.core.util.select
import org.signal.core.util.update
import org.signal.core.util.withinTransaction
import org.signal.ringrtc.CallLinkState.Restrictions
import org.thoughtcrime.securesms.conversation.colors.AvatarColor
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.service.webrtc.links.CallLinkCredentials
import org.thoughtcrime.securesms.service.webrtc.links.CallLinkRoomId
import org.thoughtcrime.securesms.service.webrtc.links.SignalCallLinkState
import org.thoughtcrime.securesms.util.Base64
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Table containing ad-hoc call link details
 */
class CallLinkTable(context: Context, databaseHelper: SignalDatabase) : DatabaseTable(context, databaseHelper), RecipientIdDatabaseReference {

  companion object {
    private val TAG = Log.tag(CallLinkTable::class.java)

    const val TABLE_NAME = "call_link"
    const val ID = "_id"
    const val ROOT_KEY = "root_key"
    const val ROOM_ID = "room_id"
    const val ADMIN_KEY = "admin_key"
    const val NAME = "name"
    const val RESTRICTIONS = "restrictions"
    const val REVOKED = "revoked"
    const val EXPIRATION = "expiration"
    const val AVATAR_COLOR = "avatar_color"
    const val RECIPIENT_ID = "recipient_id"

    //language=sql
    const val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY,
        $ROOT_KEY BLOB,
        $ROOM_ID TEXT NOT NULL UNIQUE,
        $ADMIN_KEY BLOB,
        $NAME TEXT NOT NULL,
        $RESTRICTIONS INTEGER NOT NULL,
        $REVOKED INTEGER NOT NULL,
        $EXPIRATION INTEGER NOT NULL,
        $AVATAR_COLOR TEXT NOT NULL,
        $RECIPIENT_ID INTEGER UNIQUE REFERENCES ${RecipientTable.TABLE_NAME} (${RecipientTable.ID}) ON DELETE CASCADE
      )
    """

    private fun SignalCallLinkState.serialize(): ContentValues {
      return contentValuesOf(
        NAME to name,
        RESTRICTIONS to restrictions.mapToInt(),
        EXPIRATION to expiration.toEpochMilli(),
        REVOKED to revoked
      )
    }

    private fun Restrictions.mapToInt(): Int {
      return when (this) {
        Restrictions.NONE -> 0
        Restrictions.ADMIN_APPROVAL -> 1
        Restrictions.UNKNOWN -> 2
      }
    }
  }

  fun insertCallLink(
    callLink: CallLink
  ) {
    writableDatabase.withinTransaction { db ->
      val recipientId = SignalDatabase.recipients.getOrInsertFromCallLinkRoomId(callLink.roomId)

      db
        .insertInto(TABLE_NAME)
        .values(CallLinkSerializer.serialize(callLink.copy(recipientId = recipientId)))
        .run()
    }

    ApplicationDependencies.getDatabaseObserver().notifyCallLinkObservers(callLink.roomId)
  }

  fun updateCallLinkCredentials(
    roomId: CallLinkRoomId,
    credentials: CallLinkCredentials
  ) {
    writableDatabase
      .update(TABLE_NAME)
      .values(
        contentValuesOf(
          ROOT_KEY to credentials.linkKeyBytes,
          ADMIN_KEY to credentials.adminPassBytes
        )
      )
      .where("$ROOM_ID = ?", roomId.serialize())
      .run()

    ApplicationDependencies.getDatabaseObserver().notifyCallLinkObservers(roomId)
  }

  fun updateCallLinkState(
    roomId: CallLinkRoomId,
    state: SignalCallLinkState
  ) {
    writableDatabase
      .update(TABLE_NAME)
      .values(state.serialize())
      .where("$ROOM_ID = ?", roomId.serialize())
      .run()

    ApplicationDependencies.getDatabaseObserver().notifyCallLinkObservers(roomId)
  }

  fun callLinkExists(
    callLinkRoomId: CallLinkRoomId
  ): Boolean {
    return writableDatabase
      .select("COUNT(*)")
      .from(TABLE_NAME)
      .where("$ROOM_ID = ?", callLinkRoomId.serialize())
      .run()
      .readToSingleInt() > 0
  }

  fun getCallLinkByRoomId(
    callLinkRoomId: CallLinkRoomId
  ): CallLink? {
    return writableDatabase
      .select()
      .from(TABLE_NAME)
      .where("$ROOM_ID = ?", callLinkRoomId.serialize())
      .run()
      .readToSingleObject { CallLinkDeserializer.deserialize(it) }
  }

  fun getOrCreateCallLinkByRoomId(
    callLinkRoomId: CallLinkRoomId
  ): CallLink {
    val callLink = getCallLinkByRoomId(callLinkRoomId)
    return if (callLink == null) {
      val link = CallLink(
        recipientId = RecipientId.UNKNOWN,
        roomId = callLinkRoomId,
        credentials = null,
        state = SignalCallLinkState(),
        avatarColor = AvatarColor.random()
      )
      insertCallLink(link)
      return getCallLinkByRoomId(callLinkRoomId)!!
    } else {
      callLink
    }
  }

  private object CallLinkSerializer : Serializer<CallLink, ContentValues> {
    override fun serialize(data: CallLink): ContentValues {
      return contentValuesOf(
        RECIPIENT_ID to data.recipientId.takeIf { it != RecipientId.UNKNOWN }?.toLong(),
        ROOM_ID to data.roomId.serialize(),
        ROOT_KEY to data.credentials?.linkKeyBytes,
        ADMIN_KEY to data.credentials?.adminPassBytes,
        AVATAR_COLOR to data.avatarColor.serialize()
      ).apply {
        putAll(data.state.serialize())
      }
    }

    override fun deserialize(data: ContentValues): CallLink {
      throw UnsupportedOperationException()
    }
  }

  private object CallLinkDeserializer : Serializer<CallLink, Cursor> {
    override fun serialize(data: CallLink): Cursor {
      throw UnsupportedOperationException()
    }

    override fun deserialize(data: Cursor): CallLink {
      return CallLink(
        recipientId = data.requireLong(RECIPIENT_ID).let { if (it > 0) RecipientId.from(it) else RecipientId.UNKNOWN },
        roomId = CallLinkRoomId.fromBytes(Base64.decode(data.requireNonNullString(ROOM_ID))),
        credentials = CallLinkCredentials(
          linkKeyBytes = data.requireNonNullBlob(ROOT_KEY),
          adminPassBytes = data.requireBlob(ADMIN_KEY)
        ),
        state = SignalCallLinkState(
          name = data.requireNonNullString(NAME),
          restrictions = data.requireInt(RESTRICTIONS).mapToRestrictions(),
          revoked = data.requireBoolean(REVOKED),
          expiration = Instant.ofEpochMilli(data.requireLong(EXPIRATION)).truncatedTo(ChronoUnit.DAYS)
        ),
        avatarColor = AvatarColor.deserialize(data.requireString(AVATAR_COLOR))
      )
    }

    private fun Int.mapToRestrictions(): Restrictions {
      return when (this) {
        0 -> Restrictions.NONE
        1 -> Restrictions.ADMIN_APPROVAL
        else -> Restrictions.UNKNOWN
      }
    }
  }

  data class CallLink(
    val recipientId: RecipientId,
    val roomId: CallLinkRoomId,
    val credentials: CallLinkCredentials?,
    val state: SignalCallLinkState,
    val avatarColor: AvatarColor
  )

  override fun remapRecipient(fromId: RecipientId, toId: RecipientId) {
    writableDatabase.update(TABLE_NAME)
      .values(
        contentValuesOf(
          RECIPIENT_ID to toId.toLong()
        )
      )
      .where("$RECIPIENT_ID = ?", fromId.toLong())
      .run()
  }
}
