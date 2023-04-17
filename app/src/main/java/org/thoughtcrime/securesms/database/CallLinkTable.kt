package org.thoughtcrime.securesms.database

import android.content.Context
import org.signal.core.util.logging.Log

/**
 * Table containing ad-hoc call link details
 */
class CallLinkTable(context: Context, databaseHelper: SignalDatabase) : DatabaseTable(context, databaseHelper) {

  companion object {
    private val TAG = Log.tag(CallLinkTable::class.java)

    const val TABLE_NAME = "call_link"
    const val ID = "_id"

    //language=sql
    val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY
      )
    """.trimIndent()
  }
}
