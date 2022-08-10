package org.thoughtcrime.securesms.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import androidx.core.database.getStringOrNull
import org.session.libsession.messaging.BlindedIdMapping
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper

class BlindedIdMappingDatabase(context: Context, helper: SQLCipherOpenHelper) : Database(context, helper) {

    companion object {
        const val TABLE_NAME = "blinded_id_mapping"
        const val ROW_ID = "_id"
        const val BLINDED_PK = "blinded_pk"
        const val SESSION_PK = "session_pk"
        const val SERVER_URL = "server_url"
        const val SERVER_PK = "server_pk"

        @JvmField
        val CREATE_BLINDED_ID_MAPPING_TABLE_COMMAND = """
      CREATE TABLE $TABLE_NAME (
        $ROW_ID INTEGER PRIMARY KEY,
        $BLINDED_PK TEXT NOT NULL,
        $SESSION_PK TEXT DEFAULT NULL,
        $SERVER_URL TEXT NOT NULL,
        $SERVER_PK TEXT NOT NULL
      )
    """.trimIndent()

        private fun readBlindedIdMapping(cursor: Cursor): BlindedIdMapping {
            return BlindedIdMapping(
                blindedId = cursor.getString(cursor.getColumnIndexOrThrow(BLINDED_PK)),
                sessionId = cursor.getStringOrNull(cursor.getColumnIndexOrThrow(SESSION_PK)),
                serverUrl = cursor.getString(cursor.getColumnIndexOrThrow(SERVER_URL)),
                serverId = cursor.getString(cursor.getColumnIndexOrThrow(SERVER_PK)),
            )
        }
    }

    fun getBlindedIdMapping(blindedId: String): List<BlindedIdMapping> {
        val query = "$BLINDED_PK = ?"
        val args = arrayOf(blindedId)

        val mappings: MutableList<BlindedIdMapping> = mutableListOf()

        readableDatabase.query(TABLE_NAME, null, query, args, null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                mappings += readBlindedIdMapping(cursor)
            }
        }

        return mappings
    }

    fun addBlindedIdMapping(blindedIdMapping: BlindedIdMapping) {
        writableDatabase.beginTransaction()
        try {
            val values = ContentValues().apply {
                put(BLINDED_PK, blindedIdMapping.blindedId)
                put(SERVER_PK, blindedIdMapping.sessionId)
                put(SERVER_URL, blindedIdMapping.serverUrl)
                put(SERVER_PK, blindedIdMapping.serverId)
            }

            writableDatabase.insert(TABLE_NAME, null, values)
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun getBlindedIdMappingsExceptFor(server: String): List<BlindedIdMapping> {
        val query = "$SESSION_PK IS NOT NULL AND $SERVER_URL <> ?"
        val args = arrayOf(server)

        val mappings: MutableList<BlindedIdMapping> = mutableListOf()

        readableDatabase.query(TABLE_NAME, null, query, args, null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                mappings += readBlindedIdMapping(cursor)
            }
        }

        return mappings
    }

}