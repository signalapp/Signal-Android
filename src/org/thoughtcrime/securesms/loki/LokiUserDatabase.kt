package org.thoughtcrime.securesms.loki

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import org.thoughtcrime.securesms.database.Address
import org.thoughtcrime.securesms.database.Database
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.loki.messaging.LokiUserDatabaseProtocol

class LokiUserDatabase(context: Context, helper: SQLCipherOpenHelper) : Database(context, helper), LokiUserDatabaseProtocol {

    companion object {
        // Shared
        private val displayName = "display_name"
        private val profileAvatarUrl = "profile_avatar_url"
        // Display name cache
        private val displayNameTable = "loki_user_display_name_database"
        private val hexEncodedPublicKey = "hex_encoded_public_key"
        @JvmStatic val createDisplayNameTableCommand = "CREATE TABLE $displayNameTable ($hexEncodedPublicKey TEXT PRIMARY KEY, $displayName TEXT);"
        // Server display name cache
        private val serverDisplayNameTable = "loki_user_server_display_name_database"
        private val serverID = "server_id"
        @JvmStatic val createServerDisplayNameTableCommand = "CREATE TABLE $serverDisplayNameTable ($hexEncodedPublicKey TEXT, $serverID TEXT, $displayName TEXT, PRIMARY KEY ($hexEncodedPublicKey, $serverID));"
        // Profile Avatar URL cache
        private val profileAvatarUrlTable = "loki_user_profile_avatar_url_database"
        @JvmStatic val createProfileAvatarUrlTableCommand = "CREATE TABLE $profileAvatarUrlTable ($hexEncodedPublicKey TEXT PRIMARY KEY, $profileAvatarUrl TEXT);"
    }

    override fun getDisplayName(hexEncodedPublicKey: String): String? {
        if (hexEncodedPublicKey == TextSecurePreferences.getLocalNumber(context)) {
            return TextSecurePreferences.getProfileName(context)
        } else {
            val database = databaseHelper.readableDatabase
            return database.get(displayNameTable, "${Companion.hexEncodedPublicKey} = ?", arrayOf( hexEncodedPublicKey )) { cursor ->
                cursor.getString(cursor.getColumnIndexOrThrow(displayName))
            }
        }
    }

    fun setDisplayName(hexEncodedPublicKey: String, displayName: String) {
        val database = databaseHelper.writableDatabase
        val row = ContentValues(2)
        row.put(Companion.hexEncodedPublicKey, hexEncodedPublicKey)
        row.put(Companion.displayName, displayName)
        database.insertOrUpdate(displayNameTable, row, "${Companion.hexEncodedPublicKey} = ?", arrayOf( hexEncodedPublicKey ))
        Recipient.from(context, Address.fromSerialized(hexEncodedPublicKey), false).notifyListeners()
    }

    override fun getServerDisplayName(serverID: String, hexEncodedPublicKey: String): String? {
        val database = databaseHelper.readableDatabase
        return database.get(serverDisplayNameTable, "${Companion.hexEncodedPublicKey} = ? AND ${Companion.serverID} = ?", arrayOf( hexEncodedPublicKey, serverID )) { cursor ->
            cursor.getString(cursor.getColumnIndexOrThrow(displayName))
        }
    }

    fun setServerDisplayName(serverID: String, hexEncodedPublicKey: String, displayName: String) {
        val database = databaseHelper.writableDatabase
        val values = ContentValues(3)
        values.put(Companion.serverID, serverID)
        values.put(Companion.hexEncodedPublicKey, hexEncodedPublicKey)
        values.put(Companion.displayName, displayName)
        try {
            database.insertWithOnConflict(serverDisplayNameTable, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            Recipient.from(context, Address.fromSerialized(hexEncodedPublicKey), false).notifyListeners()
        } catch (e: Exception) {
            Log.d("Loki", "Couldn't save server display name due to exception: $e.")
        }
    }

    override fun getProfileAvatarUrl(hexEncodedPublicKey: String): String? {
        if (hexEncodedPublicKey == TextSecurePreferences.getLocalNumber(context)) {
            return TextSecurePreferences.getProfileAvatarUrl(context)
        } else {
            val database = databaseHelper.readableDatabase
            return database.get(profileAvatarUrlTable, "${Companion.hexEncodedPublicKey} = ?", arrayOf( hexEncodedPublicKey )) { cursor ->
                cursor.getString(cursor.getColumnIndexOrThrow(profileAvatarUrl))
            }
        }
    }

    //TODO figure out what to do with Recipient
    /*
    fun setProfileAvatarUrl(hexEncodedPublicKey: String, profileAvatarUrl: String) {
        val database = databaseHelper.writableDatabase
        val row = ContentValues(2)
        row.put(Companion.hexEncodedPublicKey, hexEncodedPublicKey)
        row.put(Companion.profileAvatarUrl, profileAvatarUrl)
        database.insertOrUpdate(profileAvatarUrlTable, row, "${Companion.hexEncodedPublicKey} = ?", arrayOf( hexEncodedPublicKey ))
        Recipient.from(context, Address.fromSerialized(hexEncodedPublicKey), false).notifyListeners()
    }
     */
}