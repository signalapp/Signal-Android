/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.storage.keyvalue

import android.app.Application
import android.content.ContentValues
import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteOpenHelper
import org.signal.core.database.delete
import org.signal.core.database.deleteAll
import org.signal.core.database.insertInto
import org.signal.core.database.select
import org.signal.core.database.withinTransaction
import org.signal.core.util.forEach
import org.signal.core.util.logging.Log
import org.signal.core.util.requireBlob
import org.signal.core.util.requireFloat
import org.signal.core.util.requireInt
import org.signal.core.util.requireLong
import org.signal.core.util.requireNonNullString
import org.signal.core.util.requireString
import org.signal.storage.database.DatabaseSecret
import org.signal.storage.database.DatabaseSecretProvider
import org.signal.storage.database.SignalDatabaseOpenHelper
import org.signal.storage.database.SqlCipherDatabaseHook
import org.signal.storage.database.SqlCipherErrorHandler
import org.signal.storage.database.SqlCipherLibraryLoader

/**
 * Persists data for the [KeyValueStore].
 *
 * This is its own separate physical database, so it cannot do joins or queries with any other
 * tables.
 */
open class KeyValueDatabase @VisibleForTesting internal constructor(
  application: Application,
  databaseSecret: DatabaseSecret,
  name: String
) : SQLiteOpenHelper(
  application,
  name,
  databaseSecret.asHex(),
  null,
  DATABASE_VERSION,
  0,
  SqlCipherErrorHandler(application, name),
  SqlCipherDatabaseHook(),
  true
),
  SignalDatabaseOpenHelper,
  KeyValuePersistentStorage {

  companion object {
    private val TAG = Log.tag(KeyValueDatabase::class.java)

    private const val DATABASE_VERSION = 1
    const val DATABASE_NAME = "signal-key-value.db"

    private const val TABLE_NAME = "key_value"
    private const val ID = "_id"
    const val KEY = "key"
    const val VALUE = "value"
    private const val TYPE = "type"

    @VisibleForTesting
    internal const val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY AUTOINCREMENT,
        $KEY TEXT UNIQUE,
        $VALUE TEXT,
        $TYPE INTEGER
      )
    """

    @Volatile
    private var instance: KeyValueDatabase? = null

    @JvmStatic
    fun getInstance(context: Application): KeyValueDatabase {
      if (instance == null) {
        synchronized(KeyValueDatabase::class.java) {
          if (instance == null) {
            SqlCipherLibraryLoader.load()
            instance = KeyValueDatabase(context, DatabaseSecretProvider.getOrCreateDatabaseSecret(context), DATABASE_NAME)
          }
        }
      }
      return instance!!
    }

    @JvmStatic
    fun exists(context: Context): Boolean {
      return context.getDatabasePath(DATABASE_NAME).exists()
    }

    @JvmStatic
    fun createWithName(application: Application, name: String): KeyValueDatabase {
      return KeyValueDatabase(application, DatabaseSecretProvider.getOrCreateDatabaseSecret(application), name)
    }
  }

  @get:VisibleForTesting
  internal open val database: SupportSQLiteDatabase
    get() = writableDatabase

  override fun onCreate(db: SQLiteDatabase) {
    Log.i(TAG, "onCreate()")
    db.execSQL(CREATE_TABLE)
  }

  override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    Log.i(TAG, "onUpgrade($oldVersion, $newVersion)")
  }

  override fun onOpen(db: SQLiteDatabase) {
    Log.i(TAG, "onOpen()")
    db.setForeignKeyConstraintsEnabled(true)
  }

  override fun getDataSet(): KeyValueDataSet {
    val dataSet = KeyValueDataSet()

    database
      .select()
      .from(TABLE_NAME)
      .run()
      .forEach { cursor ->
        val key = cursor.requireNonNullString(KEY)

        when (Type.fromId(cursor.requireInt(TYPE))) {
          Type.BLOB -> dataSet.putBlob(key, cursor.requireBlob(VALUE))
          Type.BOOLEAN -> dataSet.putBoolean(key, cursor.requireInt(VALUE) == 1)
          Type.FLOAT -> dataSet.putFloat(key, cursor.requireFloat(VALUE))
          Type.INTEGER -> dataSet.putInteger(key, cursor.requireInt(VALUE))
          Type.LONG -> dataSet.putLong(key, cursor.requireLong(VALUE))
          Type.STRING -> dataSet.putString(key, cursor.requireString(VALUE))
        }
      }

    return dataSet
  }

  override fun writeDataSet(dataSet: KeyValueDataSet, removes: Collection<String>) {
    database.withinTransaction { db ->
      for ((key, value) in dataSet.values) {
        val contentValues = ContentValues(3)
        contentValues.put(KEY, key)

        when (val type = dataSet.getType(key)) {
          ByteArray::class.java -> {
            contentValues.put(VALUE, value as ByteArray?)
            contentValues.put(TYPE, Type.BLOB.id)
          }
          Boolean::class.javaObjectType -> {
            contentValues.put(VALUE, value as Boolean)
            contentValues.put(TYPE, Type.BOOLEAN.id)
          }
          Float::class.javaObjectType -> {
            contentValues.put(VALUE, value as Float)
            contentValues.put(TYPE, Type.FLOAT.id)
          }
          Int::class.javaObjectType -> {
            contentValues.put(VALUE, value as Int)
            contentValues.put(TYPE, Type.INTEGER.id)
          }
          Long::class.javaObjectType -> {
            contentValues.put(VALUE, value as Long)
            contentValues.put(TYPE, Type.LONG.id)
          }
          String::class.java -> {
            contentValues.put(VALUE, value as String?)
            contentValues.put(TYPE, Type.STRING.id)
          }
          else -> throw AssertionError("Unknown type: $type")
        }

        db.insertInto(TABLE_NAME)
          .values(contentValues)
          .run(SQLiteDatabase.CONFLICT_REPLACE)
      }

      for (remove in removes) {
        db.delete(TABLE_NAME)
          .where("$KEY = ?", remove).run()
      }
    }
  }

  override val sqlCipherDatabase: SQLiteDatabase
    get() = writableDatabase

  fun clear() {
    database.deleteAll(TABLE_NAME)
  }

  private enum class Type(val id: Int) {
    BLOB(0),
    BOOLEAN(1),
    FLOAT(2),
    INTEGER(3),
    LONG(4),
    STRING(5);

    companion object {
      fun fromId(id: Int): Type = entries[id]
    }
  }
}
