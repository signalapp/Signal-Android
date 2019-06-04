package org.thoughtcrime.securesms.loki

import android.database.Cursor
import net.sqlcipher.database.SQLiteDatabase

fun <T> SQLiteDatabase.get(table: String, query: String, arguments: Array<String>, get: (Cursor) -> T): T? {
    var cursor: Cursor? = null
    try {
        cursor = this.query(table, null, query, arguments, null, null, null)
        if (cursor != null && cursor.moveToFirst()) { return get(cursor) }
    } catch (e: Exception) {
        // Do nothing
    } finally {
        cursor?.close()
    }
    return null
}