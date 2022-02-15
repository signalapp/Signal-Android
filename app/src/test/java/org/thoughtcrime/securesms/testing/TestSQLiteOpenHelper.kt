package org.thoughtcrime.securesms.testing

import android.content.Context
import android.database.sqlite.SQLiteDatabase as AndroidSQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper as AndroidSQLiteOpenHelper

typealias OnCreate = AndroidSQLiteDatabase.() -> Unit

/**
 * [AndroidSQLiteOpenHelper] for use in unit tests.
 */
class TestSQLiteOpenHelper(context: Context, private val onCreate: OnCreate) : AndroidSQLiteOpenHelper(context, "test", null, 1) {

  fun setup() {
    onCreate(writableDatabase)
  }

  override fun onCreate(db: AndroidSQLiteDatabase) {
    onCreate.invoke(db)
  }

  override fun onUpgrade(db: AndroidSQLiteDatabase, oldVersion: Int, newVersion: Int) {
    // no upgrade
  }
}
