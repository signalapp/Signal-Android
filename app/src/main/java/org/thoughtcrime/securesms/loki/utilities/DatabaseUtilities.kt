package org.thoughtcrime.securesms.loki.utilities

import android.content.ContentValues
import androidx.core.database.getStringOrNull
import net.sqlcipher.Cursor
import net.sqlcipher.database.SQLiteDatabase
import org.session.libsignal.utilities.Base64

fun <T> SQLiteDatabase.get(table: String, query: String?, arguments: Array<String>?, get: (Cursor) -> T): T? {
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

fun <T> SQLiteDatabase.getAll(table: String, query: String?, arguments: Array<String>?, get: (Cursor) -> T): List<T> {
    val result = mutableListOf<T>()
    var cursor: Cursor? = null
    try {
        cursor = query(table, null, query, arguments, null, null, null)
        while (cursor != null && cursor.moveToNext()) {
            result.add(get(cursor))
        }
        return result
    } catch (e: Exception) {
        // Do nothing
    } finally {
        cursor?.close()
    }
    return listOf()
}

fun SQLiteDatabase.insertOrUpdate(table: String, values: ContentValues, query: String, arguments: Array<String>) {
    val id = insertWithOnConflict(table, null, values, SQLiteDatabase.CONFLICT_IGNORE).toInt()
    if (id == -1) {
        update(table, values, query, arguments)
    }
}

fun Cursor.getInt(columnName: String): Int {
    return getInt(getColumnIndexOrThrow(columnName))
}

fun Cursor.getString(columnName: String): String {
    return getString(getColumnIndexOrThrow(columnName))
}

fun Cursor.getLong(columnName: String): Long {
    return getLong(getColumnIndexOrThrow(columnName))
}

fun Cursor.getBase64EncodedData(columnName: String): ByteArray {
    return Base64.decode(getString(columnName))
}

fun Cursor.getStringOrNull(columnName: String): String? {
    return getStringOrNull(getColumnIndexOrThrow(columnName))
}