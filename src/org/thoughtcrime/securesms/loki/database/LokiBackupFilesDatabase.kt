package org.thoughtcrime.securesms.loki.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import org.thoughtcrime.securesms.database.Database
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper
import java.lang.IllegalArgumentException
import java.util.*
import kotlin.collections.ArrayList

/**
 * Keeps track of the backup files saved by the app.
 * Uses [BackupFileRecord] as an entry data projection.
 */
class LokiBackupFilesDatabase(context: Context, databaseHelper: SQLCipherOpenHelper)
    : Database(context, databaseHelper) {

    companion object {
        public  const val TABLE_NAME = "backup_files"
        private const val COLUMN_ID = "_id"
        private const val COLUMN_URI = "uri"
        private const val COLUMN_FILE_SIZE = "file_size"
        private const val COLUMN_TIMESTAMP = "timestamp"

        private val allColumns = arrayOf(COLUMN_ID, COLUMN_URI, COLUMN_FILE_SIZE, COLUMN_TIMESTAMP)

        @JvmStatic
        val createTableCommand = """
                    CREATE TABLE $TABLE_NAME (
                    $COLUMN_ID INTEGER PRIMARY KEY, 
                    $COLUMN_URI TEXT NOT NULL, 
                    $COLUMN_FILE_SIZE INTEGER NOT NULL, 
                    $COLUMN_TIMESTAMP INTEGER NOT NULL
                    );
                """.trimIndent()

        private fun mapCursorToRecord(cursor: Cursor): BackupFileRecord {
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID))
            val uriRaw = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_URI))
            val fileSize = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_FILE_SIZE))
            val timestampRaw = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP))
            return BackupFileRecord(id, Uri.parse(uriRaw), fileSize, Date(timestampRaw))
        }

        private fun mapRecordToValues(record: BackupFileRecord): ContentValues {
            val contentValues = ContentValues()
            if (record.id >= 0) { contentValues.put(COLUMN_ID, record.id) }
            contentValues.put(COLUMN_URI, record.uri.toString())
            contentValues.put(COLUMN_FILE_SIZE, record.fileSize)
            contentValues.put(COLUMN_TIMESTAMP, record.timestamp.time)
            return contentValues
        }
    }

    fun getBackupFiles(): List<BackupFileRecord> {
        databaseHelper.readableDatabase.query(TABLE_NAME, allColumns, null, null, null, null, null).use {
            val records = ArrayList<BackupFileRecord>()
            while (it != null && it.moveToNext()) {
                val record = mapCursorToRecord(it)
                records.add(record)
            }
            return records
        }
    }

    fun insertBackupFile(record: BackupFileRecord): BackupFileRecord {
        val contentValues = mapRecordToValues(record)
        val id = databaseHelper.writableDatabase.insertOrThrow(TABLE_NAME, null, contentValues)
        return BackupFileRecord(id, record.uri, record.fileSize, record.timestamp)
    }

    fun getLastBackupFileTime(): Date? {
        // SELECT $COLUMN_TIMESTAMP FROM $TABLE_NAME ORDER BY $COLUMN_TIMESTAMP DESC LIMIT 1
        databaseHelper.readableDatabase.query(
                TABLE_NAME,
                arrayOf(COLUMN_TIMESTAMP),
                null, null, null, null,
                "$COLUMN_TIMESTAMP DESC",
                "1"
        ).use {
            if (it !== null && it.moveToFirst()) {
                return Date(it.getLong(0))
            } else {
                return null
            }
        }
    }

    fun getLastBackupFile(): BackupFileRecord? {
        // SELECT * FROM $TABLE_NAME ORDER BY $COLUMN_TIMESTAMP DESC LIMIT 1
        databaseHelper.readableDatabase.query(
                TABLE_NAME,
                allColumns,
                null, null, null, null,
                "$COLUMN_TIMESTAMP DESC",
                "1"
        ).use {
            if (it != null && it.moveToFirst()) {
                return mapCursorToRecord(it)
            } else {
                return null
            }
        }
    }

    fun deleteBackupFile(record: BackupFileRecord): Boolean {
        return deleteBackupFile(record.id)
    }

    fun deleteBackupFile(id: Long): Boolean {
        if (id < 0) {
            throw IllegalArgumentException("ID must be zero or a positive number.")
        }
        return databaseHelper.writableDatabase.delete(TABLE_NAME, "$COLUMN_ID = $id", null) > 0
    }
}