package org.thoughtcrime.securesms.loki

import android.content.ContentValues
import android.content.Context
import org.thoughtcrime.securesms.database.Address
import org.thoughtcrime.securesms.database.Database
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.loki.messaging.LokiUserDatabaseProtocol

class LokiUserDatabase(context: Context, helper: SQLCipherOpenHelper) : Database(context, helper), LokiUserDatabaseProtocol {

    companion object {
        private val tableName = "loki_user_display_name_database"
        private val hexEncodedPublicKey = "hex_encoded_public_key"
        private val displayName = "display_name"
        @JvmStatic val createTableCommand = "CREATE TABLE $tableName ($hexEncodedPublicKey TEXT PRIMARY KEY, $displayName TEXT);"
    }

    override fun getDisplayName(hexEncodedPublicKey: String): String? {
        if (hexEncodedPublicKey == TextSecurePreferences.getLocalNumber(context)) {
            return TextSecurePreferences.getProfileName(context)
        } else {
            val database = databaseHelper.readableDatabase
            return database.get(tableName, "${Companion.hexEncodedPublicKey} = ?", arrayOf(hexEncodedPublicKey)) { cursor ->
                cursor.getString(cursor.getColumnIndexOrThrow(displayName))
            }
        }
    }

    fun setDisplayName(hexEncodedPublicKey: String, displayName: String) {
        val database = databaseHelper.writableDatabase
        val row = ContentValues(2)
        row.put(Companion.hexEncodedPublicKey, hexEncodedPublicKey)
        row.put(Companion.displayName, displayName)
        database.insertOrUpdate(tableName, row, "${Companion.hexEncodedPublicKey} = ?", arrayOf( hexEncodedPublicKey ))
        Recipient.from(context, Address.fromSerialized(hexEncodedPublicKey), false).notifyListeners()
    }
}