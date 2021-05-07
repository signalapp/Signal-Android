package org.thoughtcrime.securesms.loki.database

import android.content.ContentValues
import android.content.Context
import net.sqlcipher.Cursor
import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.messaging.jobs.Job
import org.session.libsignal.utilities.Base64
import org.thoughtcrime.securesms.database.Database
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper
import org.thoughtcrime.securesms.loki.utilities.*

class SessionContactDatabase(context: Context, helper: SQLCipherOpenHelper) : Database(context, helper) {
    companion object {
        private const val sessionContactTable = "session_contact_database"
        const val sessionID = "session_id"
        const val name = "name"
        const val nickname = "nickname"
        const val profilePictureURL = "profile_picture_url"
        const val profilePictureFileName = "profile_picture_file_name"
        const val profilePictureEncryptionKey = "profile_picture_encryption_key"
        const val threadID = "thread_id"
        const val isTrusted = "is_trusted"
        @JvmStatic val createSessionContactTableCommand =
            "CREATE TABLE $sessionContactTable " +
                    "($sessionID STRING PRIMARY KEY, " +
                    "$name TEXT DEFAULT NULL, " +
                    "$nickname TEXT DEFAULT NULL, " +
                    "$profilePictureURL TEXT DEFAULT NULL, " +
                    "$profilePictureFileName TEXT DEFAULT NULL, " +
                    "$profilePictureEncryptionKey BLOB DEFAULT NULL, " +
                    "$threadID INTEGER DEFAULT -1, " +
                    "$isTrusted INTEGER DEFAULT 0);"
    }

    fun getContactWithSessionID(sessionID: String): Contact? {
        val database = databaseHelper.readableDatabase
        return database.get(sessionContactTable, "$sessionID = ?", arrayOf(sessionID)) { cursor ->
            contactFromCursor(cursor)
        }
    }

    fun getAllContacts(): Set<Contact> {
        val database = databaseHelper.readableDatabase
        return database.getAll(sessionContactTable, null, null) { cursor ->
            contactFromCursor(cursor)
        }.toSet()
    }

    fun setContact(contact: Contact) {
        val database = databaseHelper.writableDatabase
        val contentValues = ContentValues(8)
        contentValues.put(sessionID, contact.sessionID)
        contentValues.put(name, contact.name)
        contentValues.put(nickname, contact.nickname)
        contentValues.put(profilePictureURL, contact.profilePictureURL)
        contentValues.put(profilePictureFileName, contact.profilePictureFileName)
        contentValues.put(profilePictureEncryptionKey, Base64.encodeBytes(contact.profilePictureEncryptionKey))
        contentValues.put(threadID, threadID)
        contentValues.put(isTrusted, if (contact.isTrusted) 1 else 0)
        database.insertOrUpdate(sessionContactTable, contentValues, "$sessionID = ?", arrayOf(contact.sessionID))
    }

    private fun contactFromCursor(cursor: Cursor): Contact {
        val sessionID = cursor.getString(sessionID)
        val contact = Contact(sessionID)
        contact.name = cursor.getString(name)
        contact.nickname = cursor.getString(nickname)
        contact.profilePictureURL = cursor.getString(profilePictureURL)
        contact.profilePictureFileName = cursor.getString(profilePictureFileName)
        contact.profilePictureEncryptionKey = Base64.decode(cursor.getString(profilePictureEncryptionKey))
        contact.threadID = cursor.getInt(threadID)
        contact.isTrusted = cursor.getInt(isTrusted) != 0
        return contact
    }
}