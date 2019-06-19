package org.thoughtcrime.securesms.loki

import android.content.ContentValues
import net.sqlcipher.Cursor
import net.sqlcipher.database.SQLiteDatabase
import org.thoughtcrime.securesms.util.Base64

fun <T> SQLiteDatabase.get(table: String, query: String, arguments: Array<String>, get: (Cursor) -> T): T? {
    var cursor: Cursor? = null
    try {
        cursor = query(table, null, query, arguments, null, null, null)
        if (cursor != null && cursor.moveToFirst()) { return get(cursor) }
    } catch (e: Exception) {
        // Do nothing
    } finally {
        cursor?.close()
    }
    return null
}

fun SQLiteDatabase.insertOrUpdate(table: String, values: ContentValues, whereClause: String, whereArgs: Array<String>) {
    val id = insertWithOnConflict(table, null, values, SQLiteDatabase.CONFLICT_IGNORE).toInt()
    if (id == -1) {
        update(table, values, whereClause, whereArgs)
    }
}

fun Cursor.getInt(columnName: String): Int {
    return getInt(getColumnIndexOrThrow(columnName))
}

fun Cursor.getString(columnName: String): String {
    return getString(getColumnIndexOrThrow(columnName))
}

fun Cursor.getBase64EncodedData(columnName: String): ByteArray {
    return Base64.decode(getString(columnName))
}