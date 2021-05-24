package org.thoughtcrime.securesms.loki.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.session.libsignal.utilities.Log
import org.session.libsession.utilities.Address
import org.thoughtcrime.securesms.database.Database
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper
import org.thoughtcrime.securesms.loki.utilities.get
import org.thoughtcrime.securesms.loki.utilities.insertOrUpdate
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.database.LokiUserDatabaseProtocol

class LokiUserDatabase(context: Context, helper: SQLCipherOpenHelper) : Database(context, helper), LokiUserDatabaseProtocol {

    companion object {
        // Shared
        private val displayName = "display_name"
        // Display name cache
        private val displayNameTable = "loki_user_display_name_database"
        private val publicKey = "hex_encoded_public_key"
        @JvmStatic val createDisplayNameTableCommand = "CREATE TABLE $displayNameTable ($publicKey TEXT PRIMARY KEY, $displayName TEXT);"
        // Server display name cache
        private val serverDisplayNameTable = "loki_user_server_display_name_database"
        private val serverID = "server_id"
        @JvmStatic val createServerDisplayNameTableCommand = "CREATE TABLE $serverDisplayNameTable ($publicKey TEXT, $serverID TEXT, $displayName TEXT, PRIMARY KEY ($publicKey, $serverID));"
    }

    override fun getDisplayName(publicKey: String): String? {
        if (publicKey == TextSecurePreferences.getLocalNumber(context)) {
            return TextSecurePreferences.getProfileName(context)
        } else {
            val database = databaseHelper.readableDatabase
            val result = database.get(displayNameTable, "${Companion.publicKey} = ?", arrayOf( publicKey )) { cursor ->
                cursor.getString(cursor.getColumnIndexOrThrow(displayName))
            } ?: return null
            val suffix = " (...${publicKey.substring(publicKey.count() - 8)})"
            if (result.endsWith(suffix)) {
                return result.substring(0..(result.count() - suffix.count()))
            } else {
                return result
            }
        }
    }

    fun setDisplayName(publicKey: String, displayName: String) {
        val database = databaseHelper.writableDatabase
        val row = ContentValues(2)
        row.put(Companion.publicKey, publicKey)
        row.put(Companion.displayName, displayName)
        database.insertOrUpdate(displayNameTable, row, "${Companion.publicKey} = ?", arrayOf( publicKey ))
        Recipient.from(context, Address.fromSerialized(publicKey), false).notifyListeners()
    }

    override fun getProfilePictureURL(publicKey: String): String? {
        return if (publicKey == TextSecurePreferences.getLocalNumber(context)) {
            TextSecurePreferences.getProfilePictureURL(context)
        } else {
            Recipient.from(context, Address.fromSerialized(publicKey), false).resolve().profileAvatar
        }
    }
}