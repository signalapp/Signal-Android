package org.thoughtcrime.securesms.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import androidx.core.content.contentValuesOf
import org.signal.core.util.Serializer
import org.signal.core.util.SqlUtil
import org.signal.core.util.delete
import org.signal.core.util.insertInto
import org.signal.core.util.logging.Log
import org.signal.core.util.readToList
import org.signal.core.util.readToSet
import org.signal.core.util.readToSingleInt
import org.signal.core.util.readToSingleLong
import org.signal.core.util.readToSingleObject
import org.signal.core.util.requireBlob
import org.signal.core.util.requireBoolean
import org.signal.core.util.requireInt
import org.signal.core.util.requireLong
import org.signal.core.util.requireNonNullBlob
import org.signal.core.util.requireNonNullString
import org.signal.core.util.select
import org.signal.core.util.update
import org.signal.core.util.withinTransaction
import org.signal.ringrtc.CallLinkRootKey
import org.signal.ringrtc.CallLinkState.Restrictions
import org.thoughtcrime.securesms.calls.log.CallLogRow
import org.thoughtcrime.securesms.conversation.colors.AvatarColor
import org.thoughtcrime.securesms.conversation.colors.AvatarColorHash
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.service.webrtc.links.CallLinkCredentials
import org.thoughtcrime.securesms.service.webrtc.links.CallLinkRoomId
import org.thoughtcrime.securesms.service.webrtc.links.SignalCallLinkState
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
        $RECIPIENT_ID INTEGER UNIQUE REFERENCES ${RecipientTable.TABLE_NAME} (${RecipientTable.ID}) ON DELETE CASCADE
      )
    """

    private fun SignalCallLinkState.serialize(): ContentValues {
      return contentValuesOf(
        NAME to name,
        RESTRICTIONS to restrictions.mapToInt(),
        EXPIRATION to if (expiration == Instant.MAX) -1L else expiration.toEpochMilli(),
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
  ): RecipientId {
    val recipientId: RecipientId = writableDatabase.withinTransaction { db ->
      val recipientId = SignalDatabase.recipients.getOrInsertFromCallLinkRoomId(callLink.roomId)

      db
        .insertInto(TABLE_NAME)
        .values(CallLinkSerializer.serialize(callLink.copy(recipientId = recipientId)))
        .run()

      recipientId
    }

    AppDependencies.databaseObserver.notifyCallLinkObservers(callLink.roomId)
    AppDependencies.databaseObserver.notifyCallUpdateObservers()

    return recipientId!!
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

    AppDependencies.databaseObserver.notifyCallLinkObservers(roomId)
    AppDependencies.databaseObserver.notifyCallUpdateObservers()
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

    val recipientId = readableDatabase
      .select(RECIPIENT_ID)
      .from(TABLE_NAME)
      .where("$ROOM_ID = ?", roomId.serialize())
      .run()
      .readToSingleLong()
      .let { RecipientId.from(it) }

    if (state.revoked) {
      SignalDatabase.calls.updateAdHocCallEventDeletionTimestamps()
    }

    Recipient.live(recipientId).refresh()
    AppDependencies.databaseObserver.notifyCallLinkObservers(roomId)
    AppDependencies.databaseObserver.notifyCallUpdateObservers()
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

  fun getOrCreateCallLinkByRootKey(
    callLinkRootKey: CallLinkRootKey
  ): CallLink {
    val roomId = CallLinkRoomId.fromBytes(callLinkRootKey.deriveRoomId())
    val callLink = getCallLinkByRoomId(roomId)
    return if (callLink == null) {
      val link = CallLink(
        recipientId = RecipientId.UNKNOWN,
        roomId = roomId,
        credentials = CallLinkCredentials(
          linkKeyBytes = callLinkRootKey.keyBytes,
          adminPassBytes = null
        ),
        state = SignalCallLinkState()
      )

      insertCallLink(link)
      return getCallLinkByRoomId(roomId)!!
    } else {
      callLink
    }
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
        state = SignalCallLinkState()
      )
      insertCallLink(link)
      return getCallLinkByRoomId(callLinkRoomId)!!
    } else {
      callLink
    }
  }

  fun getCallLinksCount(query: String?): Int {
    return queryCallLinks(query, -1, -1, true).readToSingleInt(0)
  }

  fun getCallLinks(query: String?, offset: Int, limit: Int): List<CallLogRow.CallLink> {
    return queryCallLinks(query, offset, limit, false).readToList {
      val callLink = CallLinkDeserializer.deserialize(it)
      val peer = Recipient.resolved(callLink.recipientId)
      CallLogRow.CallLink(
        record = callLink,
        recipient = peer,
        searchQuery = query,
        callLinkPeekInfo = AppDependencies.signalCallManager.peekInfoSnapshot[peer.id]
      )
    }
  }

  /**
   * Puts the call link into the "revoked" state which will hide it from the UI and
   * delete it after a few days.
   */
  fun markRevoked(
    roomId: CallLinkRoomId
  ) {
    writableDatabase.withinTransaction { db ->
      db.update(TABLE_NAME)
        .values(REVOKED to true)
        .where("$ROOM_ID = ?", roomId.serialize())
        .run()

      SignalDatabase.calls.updateAdHocCallEventDeletionTimestamps()
    }
  }

  /**
   * Deletes the call link. This should only happen *after* we send out a sync message
   * or receive a sync message which deletes the corresponding link.
   */
  fun deleteCallLink(
    roomId: CallLinkRoomId
  ) {
    writableDatabase.withinTransaction { db ->
      db.delete(TABLE_NAME)
        .where("$ROOM_ID = ?", roomId.serialize())
        .run()
    }
  }

  fun deleteNonAdminCallLinks(roomIds: Set<CallLinkRoomId>) {
    val queries = SqlUtil.buildCollectionQuery(ROOM_ID, roomIds)

    queries.forEach {
      writableDatabase.delete(TABLE_NAME)
        .where("${it.where} AND $ADMIN_KEY IS NULL", it.whereArgs)
        .run()
    }
  }

  fun deleteNonAdminCallLinksOnOrBefore(timestamp: Long) {
    writableDatabase.withinTransaction { db ->
      db.delete(TABLE_NAME)
        .where("EXISTS (SELECT 1 FROM ${CallTable.TABLE_NAME} WHERE ${CallTable.TIMESTAMP} <= ? AND ${CallTable.PEER} = $RECIPIENT_ID)", timestamp)
        .run()

      SignalDatabase.calls.updateAdHocCallEventDeletionTimestamps(skipSync = true)
    }
  }

  fun getAdminCallLinks(roomIds: Set<CallLinkRoomId>): Set<CallLink> {
    val queries = SqlUtil.buildCollectionQuery(ROOM_ID, roomIds)

    return queries.map {
      writableDatabase
        .select()
        .from(TABLE_NAME)
        .where("${it.where} AND $ADMIN_KEY IS NOT NULL", it.whereArgs)
        .run()
        .readToList { CallLinkDeserializer.deserialize(it) }
    }.flatten().toSet()
  }

  fun deleteAllNonAdminCallLinksExcept(roomIds: Set<CallLinkRoomId>) {
    if (roomIds.isEmpty()) {
      writableDatabase.delete(TABLE_NAME)
        .where("$ADMIN_KEY IS NULL")
        .run()
    } else {
      SqlUtil.buildCollectionQuery(ROOM_ID, roomIds, collectionOperator = SqlUtil.CollectionOperator.NOT_IN).forEach {
        writableDatabase.delete(TABLE_NAME)
          .where("${it.where} AND $ADMIN_KEY IS NULL", it.whereArgs)
          .run()
      }
    }
  }

  fun getAllAdminCallLinksExcept(roomIds: Set<CallLinkRoomId>): Set<CallLink> {
    return if (roomIds.isEmpty()) {
      writableDatabase
        .select()
        .from(TABLE_NAME)
        .where("$ADMIN_KEY IS NOT NULL")
        .run()
        .readToList { CallLinkDeserializer.deserialize(it) }
        .toSet()
    } else {
      SqlUtil.buildCollectionQuery(ROOM_ID, roomIds, collectionOperator = SqlUtil.CollectionOperator.NOT_IN).map {
        writableDatabase
          .select()
          .from(TABLE_NAME)
          .where("${it.where} AND $ADMIN_KEY IS NOT NULL", it.whereArgs)
          .run()
          .readToList { CallLinkDeserializer.deserialize(it) }
      }.flatten().toSet()
    }
  }

  fun getAdminCallLinkCredentialsOnOrBefore(timestamp: Long): Set<CallLinkCredentials> {
    val query = """
      SELECT $ROOT_KEY, $ADMIN_KEY FROM $TABLE_NAME
      INNER JOIN ${CallTable.TABLE_NAME} ON ${CallTable.TABLE_NAME}.${CallTable.PEER} = $TABLE_NAME.$RECIPIENT_ID
      WHERE ${CallTable.TIMESTAMP} <= $timestamp AND $ADMIN_KEY IS NOT NULL AND $REVOKED = 0
    """.trimIndent()

    return readableDatabase.query(query).readToSet {
      CallLinkCredentials(it.requireNonNullBlob(ROOT_KEY), it.requireNonNullBlob(ADMIN_KEY))
    }
  }

  private fun queryCallLinks(query: String?, offset: Int, limit: Int, asCount: Boolean): Cursor {
    //language=sql
    val noCallEvent = """
      NOT EXISTS (
          SELECT 1 
          FROM ${CallTable.TABLE_NAME} 
          WHERE ${CallTable.PEER} = $TABLE_NAME.$RECIPIENT_ID 
            AND ${CallTable.TYPE} = ${CallTable.Type.serialize(CallTable.Type.AD_HOC_CALL)}
            AND ${CallTable.EVENT} != ${CallTable.Event.serialize(CallTable.Event.DELETE)}
      )
    """.trimIndent()

    val searchFilter = if (!query.isNullOrEmpty()) {
      SqlUtil.buildQuery("AND $NAME GLOB ?", SqlUtil.buildCaseInsensitiveGlobPattern(query))
    } else {
      null
    }

    val limitOffset = if (limit >= 0 && offset >= 0) {
      //language=sql
      "LIMIT $limit OFFSET $offset"
    } else {
      ""
    }

    val projection = if (asCount) {
      "COUNT(*)"
    } else {
      "*"
    }

    //language=sql
    val statement = """
      SELECT $projection
      FROM $TABLE_NAME
      WHERE $noCallEvent AND NOT $REVOKED ${searchFilter?.where ?: ""} AND $ROOT_KEY IS NOT NULL
      ORDER BY $ID DESC
      $limitOffset
    """.trimIndent()

    return readableDatabase.query(statement, searchFilter?.whereArgs)
  }

  private object CallLinkSerializer : Serializer<CallLink, ContentValues> {
    override fun serialize(data: CallLink): ContentValues {
      return contentValuesOf(
        RECIPIENT_ID to data.recipientId.takeIf { it != RecipientId.UNKNOWN }?.toLong(),
        ROOM_ID to data.roomId.serialize(),
        ROOT_KEY to data.credentials?.linkKeyBytes,
        ADMIN_KEY to data.credentials?.adminPassBytes
      ).apply {
        putAll(data.state.serialize())
      }
    }

    override fun deserialize(data: ContentValues): CallLink {
      throw UnsupportedOperationException()
    }
  }

  object CallLinkDeserializer : Serializer<CallLink, Cursor> {
    override fun serialize(data: CallLink): Cursor {
      throw UnsupportedOperationException()
    }

    override fun deserialize(data: Cursor): CallLink {
      return CallLink(
        recipientId = data.requireLong(RECIPIENT_ID).let { if (it > 0) RecipientId.from(it) else RecipientId.UNKNOWN },
        roomId = CallLinkRoomId.DatabaseSerializer.deserialize(data.requireNonNullString(ROOM_ID)),
        credentials = data.requireBlob(ROOT_KEY)?.let { linkKey ->
          CallLinkCredentials(
            linkKeyBytes = linkKey,
            adminPassBytes = data.requireBlob(ADMIN_KEY)
          )
        },
        state = SignalCallLinkState(
          name = data.requireNonNullString(NAME),
          restrictions = data.requireInt(RESTRICTIONS).mapToRestrictions(),
          revoked = data.requireBoolean(REVOKED),
          expiration = data.requireLong(EXPIRATION).let {
            if (it == -1L) {
              Instant.MAX
            } else {
              Instant.ofEpochMilli(it).truncatedTo(ChronoUnit.DAYS)
            }
          }
        )
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
    val state: SignalCallLinkState
  ) {
    val avatarColor: AvatarColor = credentials?.let { AvatarColorHash.forCallLink(it.linkKeyBytes) } ?: AvatarColor.UNKNOWN
  }

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
