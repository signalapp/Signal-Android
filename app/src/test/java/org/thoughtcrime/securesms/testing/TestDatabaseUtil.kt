package org.thoughtcrime.securesms.testing

import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import java.io.File

/**
 * Helper for creating/reading a database for unit tests.
 */
object TestDatabaseUtil {

  /**
   * Create an in-memory only database that is empty. Can pass [onCreate] to do similar operations
   * one would do in a open helper's onCreate.
   */
  fun inMemoryDatabase(onCreate: OnCreate): ProxySQLCipherOpenHelper {
    val testSQLiteOpenHelper = TestSQLiteOpenHelper(ApplicationProvider.getApplicationContext(), onCreate)
    return ProxySQLCipherOpenHelper(ApplicationProvider.getApplicationContext(), testSQLiteOpenHelper)
  }

  /**
   * Open a database file located in app/src/test/resources/db. Currently only reads
   * are allowed due to weird caching of the file resulting in non-deterministic tests.
   */
  fun fromFileDatabase(name: String): ProxySQLCipherOpenHelper {
    val databaseFile = File(javaClass.getResource("/db/$name")!!.file)
    val sqliteDatabase = SQLiteDatabase.openDatabase(databaseFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
    return ProxySQLCipherOpenHelper(ApplicationProvider.getApplicationContext(), sqliteDatabase, sqliteDatabase)
  }
}
