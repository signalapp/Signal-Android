package org.signal.smsexporter

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.provider.Telephony
import androidx.test.core.app.ApplicationProvider

/**
 * Provides a content provider which reads and writes to an in-memory database.
 */
class InMemoryContentProvider : ContentProvider() {

  private val database: InMemoryDatabase = InMemoryDatabase()

  override fun onCreate(): Boolean {
    return false
  }

  override fun query(p0: Uri, p1: Array<out String>?, p2: String?, p3: Array<out String>?, p4: String?): Cursor? {
    val tableName = if (p0.pathSegments.isNotEmpty()) p0.lastPathSegment else p0.authority
    return database.readableDatabase.query(tableName, p1, p2, p3, p4, null, null)
  }

  override fun getType(p0: Uri): String? {
    return null
  }

  override fun insert(p0: Uri, p1: ContentValues?): Uri? {
    val tableName = if (p0.pathSegments.isNotEmpty()) p0.lastPathSegment else p0.authority
    val id = database.writableDatabase.insert(tableName, null, p1)
    return if (id == -1L) {
      null
    } else {
      p0.buildUpon().appendPath("$id").build()
    }
  }

  override fun delete(p0: Uri, p1: String?, p2: Array<out String>?): Int {
    return -1
  }

  override fun update(p0: Uri, p1: ContentValues?, p2: String?, p3: Array<out String>?): Int {
    return -1
  }

  private class InMemoryDatabase : SQLiteOpenHelper(ApplicationProvider.getApplicationContext(), null, null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
      db.execSQL(
        """
        CREATE TABLE sms (
            ${Telephony.Sms._ID} INTEGER PRIMARY KEY AUTOINCREMENT,
            ${Telephony.Sms.ADDRESS} TEXT,
            ${Telephony.Sms.DATE_SENT} INTEGER,
            ${Telephony.Sms.DATE} INTEGER,
            ${Telephony.Sms.BODY} TEXT,
            ${Telephony.Sms.READ} INTEGER,
            ${Telephony.Sms.TYPE} INTEGER
        );
       
        """.trimIndent()
      )

      db.execSQL(
        """
        CREATE TABLE mms (
          ${Telephony.Mms._ID} INTEGER PRIMARY KEY AUTOINCREMENT,
          ${Telephony.Mms.THREAD_ID} INTEGER,
          ${Telephony.Mms.DATE} INTEGER,
          ${Telephony.Mms.DATE_SENT} INTEGER,
          ${Telephony.Mms.MESSAGE_BOX} INTEGER,
          ${Telephony.Mms.READ} INTEGER,
          ${Telephony.Mms.CONTENT_TYPE} TEXT,
          ${Telephony.Mms.MESSAGE_TYPE} INTEGER,
          ${Telephony.Mms.MMS_VERSION} INTEGER,
          ${Telephony.Mms.MESSAGE_CLASS} TEXT,
          ${Telephony.Mms.PRIORITY} INTEGER,
          ${Telephony.Mms.TRANSACTION_ID} TEXT,
          ${Telephony.Mms.RESPONSE_STATUS} INTEGER,
          ${Telephony.Mms.SEEN} INTEGER,
          ${Telephony.Mms.TEXT_ONLY} INTEGER
        );
        """.trimIndent()
      )

      db.execSQL(
        """
        CREATE TABLE part (
          ${Telephony.Mms.Part._ID} INTEGER PRIMARY KEY AUTOINCREMENT,
          ${Telephony.Mms.Part.MSG_ID} INTEGER,
          ${Telephony.Mms.Part.CONTENT_TYPE} TEXT,
          ${Telephony.Mms.Part.CONTENT_ID} INTEGER,
          ${Telephony.Mms.Part.TEXT} TEXT
        )
        """.trimIndent()
      )

      db.execSQL(
        """
        CREATE TABLE addr (
          ${Telephony.Mms.Addr._ID} INTEGER PRIMARY KEY AUTOINCREMENT,
          ${Telephony.Mms.Addr.ADDRESS} TEXT,
          ${Telephony.Mms.Addr.CHARSET} INTEGER,
          ${Telephony.Mms.Addr.TYPE} INTEGER
        )
        """.trimIndent()
      )
    }

    override fun onUpgrade(db: SQLiteDatabase, p1: Int, p2: Int) = Unit
  }
}
