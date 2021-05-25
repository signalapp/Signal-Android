package org.thoughtcrime.securesms.loki.database

import android.content.Context
import org.thoughtcrime.securesms.database.Database
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper
import org.thoughtcrime.securesms.loki.utilities.get
import org.session.libsession.utilities.TextSecurePreferences

class LokiUserDatabase(context: Context, helper: SQLCipherOpenHelper) : Database(context, helper) {

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

    fun getDisplayName(publicKey: String): String? {
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
}