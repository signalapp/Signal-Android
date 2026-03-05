/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.sample.storage

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.signal.core.util.deleteAll
import org.signal.core.util.insertInto
import org.signal.core.util.withinTransaction
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord

/**
 * SQLite database for storing prekey data in the sample app.
 * Only stores signed prekeys and kyber prekeys, which benefit from
 * database storage due to their structure.
 */
class RegistrationDatabase(context: Context) {

  companion object {
    private const val DATABASE_NAME = "registration.db"
    private const val DATABASE_VERSION = 2

    const val ACCOUNT_TYPE_ACI = "aci"
    const val ACCOUNT_TYPE_PNI = "pni"
  }

  private val openHelper: SupportSQLiteOpenHelper = FrameworkSQLiteOpenHelperFactory().create(
    SupportSQLiteOpenHelper.Configuration(
      context = context,
      name = DATABASE_NAME,
      callback = Callback()
    )
  )

  val writableDatabase: SupportSQLiteDatabase get() = openHelper.writableDatabase
  val readableDatabase: SupportSQLiteDatabase get() = openHelper.readableDatabase

  val signedPreKeys = SampleSignedPreKeyTable(this)
  val kyberPreKeys = SampleKyberPreKeyTable(this)

  fun clearAllPreKeys() {
    writableDatabase.withinTransaction { db ->
      db.deleteAll(SampleSignedPreKeyTable.TABLE_NAME)
      db.deleteAll(SampleKyberPreKeyTable.TABLE_NAME)
    }
  }

  private class Callback : SupportSQLiteOpenHelper.Callback(DATABASE_VERSION) {
    override fun onCreate(db: SupportSQLiteDatabase) {
      db.execSQL(SampleSignedPreKeyTable.CREATE_TABLE)
      db.execSQL(SampleKyberPreKeyTable.CREATE_TABLE)
    }

    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
  }

  /**
   * Table for storing signed pre-keys.
   */
  class SampleSignedPreKeyTable(private val db: RegistrationDatabase) {

    companion object {
      const val TABLE_NAME = "signed_prekeys"

      private const val ID = "_id"
      private const val ACCOUNT_TYPE = "account_type"
      private const val KEY_ID = "key_id"
      private const val KEY_DATA = "key_data"

      const val CREATE_TABLE = """
        CREATE TABLE $TABLE_NAME (
          $ID INTEGER PRIMARY KEY AUTOINCREMENT,
          $ACCOUNT_TYPE TEXT NOT NULL,
          $KEY_ID INTEGER NOT NULL,
          $KEY_DATA BLOB NOT NULL
        )
      """
    }

    fun insert(accountType: String, signedPreKey: SignedPreKeyRecord) {
      db.writableDatabase
        .insertInto(TABLE_NAME)
        .values(
          ACCOUNT_TYPE to accountType,
          KEY_ID to signedPreKey.id,
          KEY_DATA to signedPreKey.serialize()
        )
        .run()
    }
  }

  /**
   * Table for storing Kyber pre-keys.
   */
  class SampleKyberPreKeyTable(private val db: RegistrationDatabase) {

    companion object {
      const val TABLE_NAME = "kyber_prekeys"

      private const val ID = "_id"
      private const val ACCOUNT_TYPE = "account_type"
      private const val KEY_ID = "key_id"
      private const val KEY_DATA = "key_data"

      const val CREATE_TABLE = """
        CREATE TABLE $TABLE_NAME (
          $ID INTEGER PRIMARY KEY AUTOINCREMENT,
          $ACCOUNT_TYPE TEXT NOT NULL,
          $KEY_ID INTEGER NOT NULL,
          $KEY_DATA BLOB NOT NULL
        )
      """
    }

    fun insert(accountType: String, kyberPreKey: KyberPreKeyRecord) {
      db.writableDatabase
        .insertInto(TABLE_NAME)
        .values(
          ACCOUNT_TYPE to accountType,
          KEY_ID to kyberPreKey.id,
          KEY_DATA to kyberPreKey.serialize()
        )
        .run()
    }
  }
}
