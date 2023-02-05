package org.thoughtcrime.securesms.database

import android.content.ContentValues
import android.content.Context
import org.signal.core.util.CursorUtil
import org.signal.core.util.SqlUtil
import org.signal.ringrtc.CallManager
import java.util.concurrent.TimeUnit

/**
 * Track state of Group Call ring cancellations.
 */
class GroupCallRingTable(context: Context, databaseHelper: SignalDatabase) : DatabaseTable(context, databaseHelper) {

  companion object {
    private val VALID_RING_DURATION = TimeUnit.MINUTES.toMillis(30)

    private const val TABLE_NAME = "group_call_ring"

    private const val ID = "_id"
    private const val RING_ID = "ring_id"
    private const val DATE_RECEIVED = "date_received"
    private const val RING_STATE = "ring_state"

    @JvmField
    val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY,
        $RING_ID INTEGER UNIQUE,
        $DATE_RECEIVED INTEGER,
        $RING_STATE INTEGER
      )
    """.trimIndent()

    @JvmField
    val CREATE_INDEXES = arrayOf(
      "CREATE INDEX date_received_index on $TABLE_NAME ($DATE_RECEIVED)"
    )
  }

  fun isCancelled(ringId: Long): Boolean {
    val db = databaseHelper.signalReadableDatabase

    db.query(TABLE_NAME, null, "$RING_ID = ?", SqlUtil.buildArgs(ringId), null, null, null).use { cursor ->
      if (cursor.moveToFirst()) {
        return CursorUtil.requireInt(cursor, RING_STATE) != 0
      }
    }

    return false
  }

  fun insertGroupRing(ringId: Long, dateReceived: Long, ringState: CallManager.RingUpdate) {
    val db = databaseHelper.signalWritableDatabase
    val values = ContentValues().apply {
      put(RING_ID, ringId)
      put(DATE_RECEIVED, dateReceived)
      put(RING_STATE, ringState.toCode())
    }
    db.insert(TABLE_NAME, null, values)

    removeOldRings()
  }

  fun insertOrUpdateGroupRing(ringId: Long, dateReceived: Long, ringState: CallManager.RingUpdate) {
    val db = databaseHelper.signalWritableDatabase
    val values = ContentValues().apply {
      put(RING_ID, ringId)
      put(DATE_RECEIVED, dateReceived)
      put(RING_STATE, ringState.toCode())
    }
    db.insertWithOnConflict(TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE)

    removeOldRings()
  }

  fun removeOldRings() {
    val db = databaseHelper.signalWritableDatabase

    db.delete(TABLE_NAME, "$DATE_RECEIVED < ?", SqlUtil.buildArgs(System.currentTimeMillis() - VALID_RING_DURATION))
  }
}

private fun CallManager.RingUpdate.toCode(): Int {
  return when (this) {
    CallManager.RingUpdate.REQUESTED -> 0
    CallManager.RingUpdate.EXPIRED_REQUEST -> 1
    CallManager.RingUpdate.ACCEPTED_ON_ANOTHER_DEVICE -> 2
    CallManager.RingUpdate.DECLINED_ON_ANOTHER_DEVICE -> 3
    CallManager.RingUpdate.BUSY_LOCALLY -> 4
    CallManager.RingUpdate.BUSY_ON_ANOTHER_DEVICE -> 5
    CallManager.RingUpdate.CANCELLED_BY_RINGER -> 6
  }
}
