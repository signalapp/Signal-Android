package org.thoughtcrime.securesms.loki

import android.content.ContentValues
import net.sqlcipher.Cursor
import net.sqlcipher.database.SQLiteDatabase
import org.thoughtcrime.securesms.util.Base64

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

fun SQLiteDatabase.insertOrUpdate(table: String, values: ContentValues, whereClause: String, whereArgs: Array<String>) {
    val id = this.insertWithOnConflict(table, null, values, SQLiteDatabase.CONFLICT_IGNORE).toInt()
    if (id == -1) {
        this.update(table, values, whereClause, whereArgs)
    }
}

fun Cursor.getInt(columnName: String): Int {
    return this.getInt(this.getColumnIndexOrThrow(columnName))
}

fun Cursor.getString(columnName: String): String {
    return this.getString(this.getColumnIndexOrThrow(columnName))
}

fun Cursor.getBase64EncodedData(columnName: String): ByteArray {
    return Base64.decode(this.getString(columnName))
}