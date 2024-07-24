package org.thoughtcrime.securesms.database

import android.content.Context
import org.signal.core.util.CursorUtil
import org.signal.core.util.SqlUtil
import org.signal.core.util.logging.Log
import org.signal.core.util.readToSet
import org.signal.core.util.requireInt
import org.signal.core.util.requireNonNullBlob
import org.signal.core.util.requireNonNullString
import org.signal.core.util.requireString
import org.signal.core.util.select
import org.signal.libsignal.protocol.InvalidSessionException
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.state.SessionRecord
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.api.push.ServiceId.PNI
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import java.io.IOException
import java.util.LinkedList

class SessionTable(context: Context, databaseHelper: SignalDatabase) : DatabaseTable(context, databaseHelper) {
  companion object {
    private val TAG = Log.tag(SessionTable::class.java)

    const val TABLE_NAME = "sessions"
    const val ID = "_id"
    const val ACCOUNT_ID = "account_id"
    const val ADDRESS = "address"
    const val DEVICE = "device"
    const val RECORD = "record"
    const val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY AUTOINCREMENT,
        $ACCOUNT_ID TEXT NOT NULL,
        $ADDRESS TEXT NOT NULL,
        $DEVICE INTEGER NOT NULL,
        $RECORD BLOB NOT NULL,
        UNIQUE($ACCOUNT_ID, $ADDRESS, $DEVICE)
      )
    """
  }

  fun store(serviceId: ServiceId, address: SignalProtocolAddress, record: SessionRecord) {
    require(address.name[0] != '+') { "Cannot insert an e164 into this table!" }

    writableDatabase.compileStatement("INSERT INTO $TABLE_NAME ($ACCOUNT_ID, $ADDRESS, $DEVICE, $RECORD) VALUES (?, ?, ?, ?) ON CONFLICT ($ACCOUNT_ID, $ADDRESS, $DEVICE) DO UPDATE SET $RECORD = excluded.$RECORD").use { statement ->
      statement.apply {
        bindString(1, serviceId.toString())
        bindString(2, address.name)
        bindLong(3, address.deviceId.toLong())
        bindBlob(4, record.serialize())
        execute()
      }
    }
  }

  fun load(serviceId: ServiceId, address: SignalProtocolAddress): SessionRecord? {
    val projection = arrayOf(RECORD)
    val selection = "$ACCOUNT_ID = ? AND $ADDRESS = ? AND $DEVICE = ?"
    val args = SqlUtil.buildArgs(serviceId, address.name, address.deviceId)

    readableDatabase.query(TABLE_NAME, projection, selection, args, null, null, null).use { cursor ->
      if (cursor.moveToFirst()) {
        try {
          return SessionRecord(cursor.requireNonNullBlob(RECORD))
        } catch (e: IOException) {
          Log.w(TAG, e)
        } catch (e: InvalidSessionException) {
          Log.w(TAG, e)
        }
      }
    }

    return null
  }

  fun load(serviceId: ServiceId, addresses: List<SignalProtocolAddress>): List<SessionRecord?> {
    val projection = arrayOf(ADDRESS, DEVICE, RECORD)
    val query = "$ACCOUNT_ID = ? AND $ADDRESS = ? AND $DEVICE = ?"
    val args: MutableList<Array<String>> = ArrayList(addresses.size)
    val sessions: HashMap<SignalProtocolAddress, SessionRecord?> = LinkedHashMap(addresses.size)

    for (address in addresses) {
      args.add(SqlUtil.buildArgs(serviceId, address.name, address.deviceId))
      sessions[address] = null
    }

    for (combinedQuery in SqlUtil.buildCustomCollectionQuery(query, args)) {
      readableDatabase.query(TABLE_NAME, projection, combinedQuery.where, combinedQuery.whereArgs, null, null, null).use { cursor ->
        while (cursor.moveToNext()) {
          val address = cursor.requireNonNullString(ADDRESS)
          val device = cursor.requireInt(DEVICE)
          try {
            val record = SessionRecord(cursor.requireNonNullBlob(RECORD))
            sessions[SignalProtocolAddress(address, device)] = record
          } catch (e: IOException) {
            Log.w(TAG, e)
          }
        }
      }
    }

    return sessions.values.toList()
  }

  fun getAllFor(serviceId: ServiceId, addressName: String): List<SessionRow> {
    val results: MutableList<SessionRow> = mutableListOf()

    readableDatabase.query(TABLE_NAME, null, "$ACCOUNT_ID = ? AND $ADDRESS = ?", SqlUtil.buildArgs(serviceId, addressName), null, null, null).use { cursor ->
      while (cursor.moveToNext()) {
        try {
          results.add(
            SessionRow(
              CursorUtil.requireString(cursor, ADDRESS),
              CursorUtil.requireInt(cursor, DEVICE),
              SessionRecord(CursorUtil.requireBlob(cursor, RECORD))
            )
          )
        } catch (e: IOException) {
          Log.w(TAG, e)
        }
      }
    }
    return results
  }

  fun getAllFor(serviceId: ServiceId, addressNames: List<String?>): List<SessionRow> {
    val query: SqlUtil.Query = SqlUtil.buildSingleCollectionQuery(ADDRESS, addressNames)
    val results: MutableList<SessionRow> = LinkedList()

    val queryString = "$ACCOUNT_ID = ? AND (${query.where})"
    val queryArgs: Array<String> = arrayOf(serviceId.toString()) + query.whereArgs

    readableDatabase.query(TABLE_NAME, null, queryString, queryArgs, null, null, null).use { cursor ->
      while (cursor.moveToNext()) {
        try {
          results.add(
            SessionRow(
              address = CursorUtil.requireString(cursor, ADDRESS),
              deviceId = CursorUtil.requireInt(cursor, DEVICE),
              record = SessionRecord(cursor.requireNonNullBlob(RECORD))
            )
          )
        } catch (e: IOException) {
          Log.w(TAG, e)
        }
      }
    }
    return results
  }

  fun getAll(serviceId: ServiceId): List<SessionRow> {
    val results: MutableList<SessionRow> = mutableListOf()

    readableDatabase.query(TABLE_NAME, null, "$ACCOUNT_ID = ?", SqlUtil.buildArgs(serviceId), null, null, null).use { cursor ->
      while (cursor.moveToNext()) {
        try {
          results.add(
            SessionRow(
              address = cursor.requireNonNullString(ADDRESS),
              deviceId = cursor.requireInt(DEVICE),
              record = SessionRecord(cursor.requireNonNullBlob(RECORD))
            )
          )
        } catch (e: IOException) {
          Log.w(TAG, e)
        }
      }
    }
    return results
  }

  fun getSubDevices(serviceId: ServiceId, addressName: String): List<Int> {
    val projection = arrayOf(DEVICE)
    val selection = "$ACCOUNT_ID = ? AND $ADDRESS = ? AND $DEVICE != ?"
    val args = SqlUtil.buildArgs(serviceId, addressName, SignalServiceAddress.DEFAULT_DEVICE_ID)

    val results: MutableList<Int> = mutableListOf()

    readableDatabase.query(TABLE_NAME, projection, selection, args, null, null, null).use { cursor ->
      while (cursor.moveToNext()) {
        results.add(cursor.requireInt(DEVICE))
      }
    }
    return results
  }

  fun delete(serviceId: ServiceId, address: SignalProtocolAddress) {
    writableDatabase.delete(TABLE_NAME, "$ACCOUNT_ID = ? AND $ADDRESS = ? AND $DEVICE = ?", SqlUtil.buildArgs(serviceId, address.name, address.deviceId))
  }

  fun deleteAllFor(serviceId: ServiceId, addressName: String) {
    writableDatabase.delete(TABLE_NAME, "$ACCOUNT_ID = ? AND $ADDRESS = ?", SqlUtil.buildArgs(serviceId, addressName))
  }

  fun hasSessionFor(serviceId: ServiceId, addressName: String): Boolean {
    val query = "$ACCOUNT_ID = ? AND $ADDRESS = ?"
    val args = SqlUtil.buildArgs(serviceId, addressName)
    readableDatabase.query(TABLE_NAME, arrayOf("1"), query, args, null, null, null, "1").use { cursor ->
      return cursor.moveToFirst()
    }
  }

  /**
   * @return True if a session exists with this address for _any_ of your identities.
   */
  fun hasAnySessionFor(addressName: String): Boolean {
    readableDatabase
      .select("1")
      .from(TABLE_NAME)
      .where("$ADDRESS = ?", addressName)
      .run()
      .use { cursor ->
        return cursor.moveToFirst()
      }
  }

  /**
   * Given a set of serviceIds, this will give you back a filtered set of those ids that have any session with any of your identities.
   *
   * This was created for getting more debug info for a specific issue.
   */
  fun findAllThatHaveAnySession(serviceIds: Set<PNI>): Set<PNI> {
    val output: MutableSet<PNI> = mutableSetOf()

    for (query in SqlUtil.buildCollectionQuery(ADDRESS, serviceIds.map { it.toString() })) {
      output += readableDatabase
        .select(ADDRESS)
        .from(TABLE_NAME)
        .where(query.where, query.whereArgs)
        .run()
        .readToSet { PNI.parseOrThrow(it.requireString(ADDRESS)) }
    }

    return output
  }

  class SessionRow(val address: String, val deviceId: Int, val record: SessionRecord)
}
