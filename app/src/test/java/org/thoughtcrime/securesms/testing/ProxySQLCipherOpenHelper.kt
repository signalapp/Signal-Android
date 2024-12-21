package org.thoughtcrime.securesms.testing

import android.app.Application
import org.thoughtcrime.securesms.crypto.AttachmentSecret
import org.thoughtcrime.securesms.crypto.DatabaseSecret
import org.thoughtcrime.securesms.database.SignalDatabase
import java.security.SecureRandom
import android.database.sqlite.SQLiteDatabase as AndroidSQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteDatabase as SQLCipherSQLiteDatabase

/**
 * Proxy [SignalDatabase] to the [TestSQLiteOpenHelper] interface.
 */
class ProxySQLCipherOpenHelper(
  context: Application,
  val myReadableDatabase: AndroidSQLiteDatabase,
  val myWritableDatabase: AndroidSQLiteDatabase
) : SignalDatabase(context, DatabaseSecret(ByteArray(32).apply { SecureRandom().nextBytes(this) }), AttachmentSecret()) {

  constructor(context: Application, testOpenHelper: TestSQLiteOpenHelper) : this(context, testOpenHelper.readableDatabase, testOpenHelper.writableDatabase)

  override fun close() {
    throw UnsupportedOperationException()
  }

  override val databaseName: String
    get() = throw UnsupportedOperationException()

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

  override fun onOpen(db: net.zetetic.database.sqlcipher.SQLiteDatabase) {
    throw UnsupportedOperationException()
  }

  override fun onCreate(db: net.zetetic.database.sqlcipher.SQLiteDatabase) {
    throw UnsupportedOperationException()
  }

  override fun onUpgrade(db: net.zetetic.database.sqlcipher.SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    throw UnsupportedOperationException()
  }

  override val readableDatabase: SQLCipherSQLiteDatabase
    get() = throw UnsupportedOperationException()

  override val writableDatabase: SQLCipherSQLiteDatabase
    get() = throw UnsupportedOperationException()

  override val rawReadableDatabase: net.zetetic.database.sqlcipher.SQLiteDatabase
    get() = throw UnsupportedOperationException()

  override val rawWritableDatabase: net.zetetic.database.sqlcipher.SQLiteDatabase
    get() = throw UnsupportedOperationException()

  override val signalReadableDatabase: org.thoughtcrime.securesms.database.SQLiteDatabase
    get() = ProxySignalSQLiteDatabase(myReadableDatabase)

  override val signalWritableDatabase: org.thoughtcrime.securesms.database.SQLiteDatabase
    get() = ProxySignalSQLiteDatabase(myWritableDatabase)

  override fun getSqlCipherDatabase(): SQLCipherSQLiteDatabase {
    throw UnsupportedOperationException()
  }

  override fun markCurrent(db: net.zetetic.database.sqlcipher.SQLiteDatabase) {
    throw UnsupportedOperationException()
  }
}
