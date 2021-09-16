package org.thoughtcrime.securesms.testing

import android.content.Context
import org.thoughtcrime.securesms.crypto.DatabaseSecret
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper
import java.security.SecureRandom
import android.database.sqlite.SQLiteDatabase as AndroidSQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteDatabase as SQLCipherSQLiteDatabase
import org.thoughtcrime.securesms.database.SQLiteDatabase as SignalSQLiteDatabase

/**
 * Proxy [SQLCipherOpenHelper] to the [TestSQLiteOpenHelper] interface.
 */
class ProxySQLCipherOpenHelper(
  context: Context,
  val readableDatabase: AndroidSQLiteDatabase,
  val writableDatabase: AndroidSQLiteDatabase,
) : SQLCipherOpenHelper(context, DatabaseSecret(ByteArray(32).apply { SecureRandom().nextBytes(this) })) {

  constructor(context: Context, testOpenHelper: TestSQLiteOpenHelper) : this(context, testOpenHelper.readableDatabase, testOpenHelper.writableDatabase)

  override fun close() {
    throw UnsupportedOperationException()
  }

  override fun getDatabaseName(): String {
    throw UnsupportedOperationException()
  }

  override fun setWriteAheadLoggingEnabled(enabled: Boolean) {
    throw UnsupportedOperationException()
  }

  override fun onConfigure(db: SQLCipherSQLiteDatabase) {
    throw UnsupportedOperationException()
  }

  override fun onBeforeDelete(db: SQLCipherSQLiteDatabase?) {
    throw UnsupportedOperationException()
  }

  override fun onDowngrade(db: SQLCipherSQLiteDatabase?, oldVersion: Int, newVersion: Int) {
    throw UnsupportedOperationException()
  }

  override fun onOpen(db: SQLCipherSQLiteDatabase?) {
    throw UnsupportedOperationException()
  }

  override fun onCreate(db: SQLCipherSQLiteDatabase?) {
    throw UnsupportedOperationException()
  }

  override fun onUpgrade(db: SQLCipherSQLiteDatabase?, oldVersion: Int, newVersion: Int) {
    throw UnsupportedOperationException()
  }

  override fun getReadableDatabase(): SQLCipherSQLiteDatabase {
    throw UnsupportedOperationException()
  }

  override fun getWritableDatabase(): SQLCipherSQLiteDatabase {
    throw UnsupportedOperationException()
  }

  override fun getRawReadableDatabase(): SQLCipherSQLiteDatabase {
    throw UnsupportedOperationException()
  }

  override fun getRawWritableDatabase(): SQLCipherSQLiteDatabase {
    throw UnsupportedOperationException()
  }

  override fun getSignalReadableDatabase(): SignalSQLiteDatabase {
    return ProxySignalSQLiteDatabase(readableDatabase)
  }

  override fun getSignalWritableDatabase(): SignalSQLiteDatabase {
    return ProxySignalSQLiteDatabase(writableDatabase)
  }

  override fun getSqlCipherDatabase(): SQLCipherSQLiteDatabase {
    throw UnsupportedOperationException()
  }

  override fun markCurrent(db: SQLCipherSQLiteDatabase?) {
    throw UnsupportedOperationException()
  }
}
