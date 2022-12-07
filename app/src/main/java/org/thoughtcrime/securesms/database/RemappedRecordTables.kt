package org.thoughtcrime.securesms.database

import android.content.Context
import android.database.Cursor
import androidx.core.content.contentValuesOf
import org.signal.core.util.readToList
import org.signal.core.util.requireLong
import org.signal.core.util.select
import org.signal.core.util.withinTransaction
import org.thoughtcrime.securesms.database.RemappedRecordTables.SharedColumns.ID
import org.thoughtcrime.securesms.database.RemappedRecordTables.SharedColumns.NEW_ID
import org.thoughtcrime.securesms.database.RemappedRecordTables.SharedColumns.OLD_ID
import org.thoughtcrime.securesms.recipients.RecipientId
import java.util.HashMap

/**
 * The backing datastore for [RemappedRecords]. See that class for more details.
 */
class RemappedRecordTables internal constructor(context: Context?, databaseHelper: SignalDatabase?) : DatabaseTable(context, databaseHelper) {

  companion object {
    val CREATE_TABLE = arrayOf(Recipients.CREATE_TABLE, Threads.CREATE_TABLE)
  }

  private object SharedColumns {
    const val ID = "_id"
    const val OLD_ID = "old_id"
    const val NEW_ID = "new_id"
  }

  private object Recipients {
    const val TABLE_NAME = "remapped_recipients"
    const val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY AUTOINCREMENT, 
        $OLD_ID INTEGER UNIQUE, 
        $NEW_ID INTEGER
      )
    """
  }

  private object Threads {
    const val TABLE_NAME = "remapped_threads"
    const val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY AUTOINCREMENT, 
        $OLD_ID INTEGER UNIQUE, 
        $NEW_ID INTEGER
      )
    """
  }

  fun getAllRecipientMappings(): Map<RecipientId, RecipientId> {
    val recipientMap: MutableMap<RecipientId, RecipientId> = HashMap()

    readableDatabase.withinTransaction { db ->
      val mappings = getAllMappings(db, Recipients.TABLE_NAME)
      for (mapping in mappings) {
        val oldId = RecipientId.from(mapping.oldId)
        val newId = RecipientId.from(mapping.newId)
        recipientMap[oldId] = newId
      }
    }

    return recipientMap
  }

  fun getAllThreadMappings(): Map<Long, Long> {
    val threadMap: MutableMap<Long, Long> = HashMap()

    readableDatabase.withinTransaction { db ->
      val mappings = getAllMappings(db, Threads.TABLE_NAME)
      for (mapping in mappings) {
        threadMap[mapping.oldId] = mapping.newId
      }
    }

    return threadMap
  }

  fun addRecipientMapping(oldId: RecipientId, newId: RecipientId) {
    addMapping(Recipients.TABLE_NAME, Mapping(oldId.toLong(), newId.toLong()))
  }

  fun addThreadMapping(oldId: Long, newId: Long) {
    addMapping(Threads.TABLE_NAME, Mapping(oldId, newId))
  }

  fun getAllRecipients(): Cursor {
    return readableDatabase
      .select()
      .from(Recipients.TABLE_NAME)
      .run()
  }

  fun getAllThreads(): Cursor {
    return readableDatabase
      .select()
      .from(Threads.TABLE_NAME)
      .run()
  }

  private fun getAllMappings(db: SQLiteDatabase, table: String): List<Mapping> {
    return db.select()
      .from(table)
      .run()
      .readToList { cursor ->
        Mapping(
          oldId = cursor.requireLong(OLD_ID),
          newId = cursor.requireLong(NEW_ID)
        )
      }
  }

  private fun addMapping(table: String, mapping: Mapping) {
    val values = contentValuesOf(
      OLD_ID to mapping.oldId,
      NEW_ID to mapping.newId
    )
    databaseHelper.signalWritableDatabase.insert(table, null, values)
  }

  private class Mapping(val oldId: Long, val newId: Long)
}
