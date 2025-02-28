package org.thoughtcrime.securesms.testing

import android.content.ContentValues
import android.database.Cursor
import android.database.SQLException
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteQuery
import net.zetetic.database.sqlcipher.SQLiteQueryBuilder
import java.util.Locale
import android.database.sqlite.SQLiteDatabase as AndroidSQLiteDatabase
import android.database.sqlite.SQLiteTransactionListener as AndroidSQLiteTransactionListener
import net.zetetic.database.sqlcipher.SQLiteStatement as SQLCipherSQLiteStatement
import net.zetetic.database.sqlcipher.SQLiteTransactionListener as SQLCipherSQLiteTransactionListener
import org.thoughtcrime.securesms.database.SQLiteDatabase as SignalSQLiteDatabase

/**
 * Partial implementation of [SignalSQLiteDatabase] using an instance of [AndroidSQLiteDatabase] instead
 * of SQLCipher.
 */
class TestSignalSQLiteDatabase(private val database: SupportSQLiteDatabase) : SignalSQLiteDatabase(null) {
  override fun getSqlCipherDatabase(): net.zetetic.database.sqlcipher.SQLiteDatabase {
    throw UnsupportedOperationException()
  }

  override fun beginTransaction() {
    database.beginTransaction()
  }

  override fun endTransaction() {
    database.endTransaction()
  }

  override fun setTransactionSuccessful() {
    database.setTransactionSuccessful()
  }

  override fun query(distinct: Boolean, table: String, columns: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, groupBy: String?, having: String?, orderBy: String?, limit: String?): Cursor {
    throw UnsupportedOperationException()
  }

  override fun queryWithFactory(
    cursorFactory: net.zetetic.database.sqlcipher.SQLiteDatabase.CursorFactory?,
    distinct: Boolean,
    table: String,
    columns: Array<out String>?,
    selection: String?,
    selectionArgs: Array<out String>?,
    groupBy: String?,
    having: String?,
    orderBy: String?,
    limit: String?
  ): Cursor {
    throw UnsupportedOperationException()
  }

  override fun query(query: SupportSQLiteQuery): Cursor {
    return database.query(query)
  }

  override fun query(table: String, columns: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, groupBy: String?, having: String?, orderBy: String?, limit: String?): Cursor {
    val query: String = SQLiteQueryBuilder.buildQueryString(false, table, columns, selection, groupBy, having, orderBy, limit)
    return database.query(query, selectionArgs ?: emptyArray())
  }

  override fun query(table: String, columns: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, groupBy: String?, having: String?, orderBy: String?): Cursor {
    return query(table, columns, selection, selectionArgs, groupBy, having, orderBy, null)
  }

  override fun rawQuery(sql: String, selectionArgs: Array<out String>?): Cursor {
    return database.query(sql, selectionArgs ?: emptyArray())
  }

  override fun rawQuery(sql: String, args: Array<out Any>?): Cursor {
    return database.query(sql, args ?: emptyArray())
  }

  override fun rawQueryWithFactory(cursorFactory: net.zetetic.database.sqlcipher.SQLiteDatabase.CursorFactory?, sql: String, selectionArgs: Array<out String>?, editTable: String): Cursor {
    throw UnsupportedOperationException()
  }

  override fun rawQuery(sql: String, selectionArgs: Array<out String>?, initialRead: Int, maxRead: Int): Cursor {
    throw UnsupportedOperationException()
  }

  override fun insert(table: String, nullColumnHack: String?, values: ContentValues): Long {
    return database.insert(table, 0, values)
  }

  override fun insertOrThrow(table: String, nullColumnHack: String?, values: ContentValues): Long {
    val result = database.insert(table, 0, values)
    if (result < 0) {
      throw SQLException()
    }
    return result
  }

  override fun replace(table: String, nullColumnHack: String?, initialValues: ContentValues): Long {
    return database.insert(table, 5, initialValues)
  }

  override fun replaceOrThrow(table: String, nullColumnHack: String?, initialValues: ContentValues): Long {
    val result = replace(table, nullColumnHack, initialValues)
    if (result < 0) {
      throw SQLException()
    }
    return result
  }

  override fun insertWithOnConflict(table: String, nullColumnHack: String?, initialValues: ContentValues, conflictAlgorithm: Int): Long {
    return database.insert(table, conflictAlgorithm, initialValues)
  }

  override fun delete(table: String, whereClause: String?, whereArgs: Array<out String>?): Int {
    return database.delete(table, whereClause, whereArgs)
  }

  override fun update(table: String, values: ContentValues, whereClause: String?, whereArgs: Array<out String>?): Int {
    return database.update(table, 0, values, whereClause, whereArgs)
  }

  override fun updateWithOnConflict(table: String, values: ContentValues, whereClause: String?, whereArgs: Array<out String>?, conflictAlgorithm: Int): Int {
    return database.update(table, conflictAlgorithm, values, whereClause, whereArgs)
  }

  override fun execSQL(sql: String) {
    database.execSQL(sql)
  }

  override fun rawExecSQL(sql: String) {
    database.execSQL(sql)
  }

  override fun execSQL(sql: String, bindArgs: Array<out Any?>) {
    database.execSQL(sql, bindArgs)
  }

  override fun enableWriteAheadLogging(): Boolean {
    throw UnsupportedOperationException()
  }

  override fun disableWriteAheadLogging() {
    throw UnsupportedOperationException()
  }

  override val isWriteAheadLoggingEnabled: Boolean
    get() = throw UnsupportedOperationException()

  override fun setForeignKeyConstraintsEnabled(enabled: Boolean) {
    database.setForeignKeyConstraintsEnabled(enabled)
  }

  override fun beginTransactionWithListener(transactionListener: SQLCipherSQLiteTransactionListener?) {
    database.beginTransactionWithListener(object : AndroidSQLiteTransactionListener {
      override fun onBegin() {
        transactionListener?.onBegin()
      }

      override fun onCommit() {
        transactionListener?.onCommit()
      }

      override fun onRollback() {
        transactionListener?.onRollback()
      }
    })
  }

  override fun beginTransactionNonExclusive() {
    database.beginTransactionNonExclusive()
  }

  override fun beginTransactionWithListenerNonExclusive(transactionListener: SQLCipherSQLiteTransactionListener?) {
    database.beginTransactionWithListenerNonExclusive(object : AndroidSQLiteTransactionListener {
      override fun onBegin() {
        transactionListener?.onBegin()
      }

      override fun onCommit() {
        transactionListener?.onCommit()
      }

      override fun onRollback() {
        transactionListener?.onRollback()
      }
    })
  }

  override fun inTransaction(): Boolean {
    return database.inTransaction()
  }

  override val isDbLockedByCurrentThread: Boolean
    get() = database.isDbLockedByCurrentThread

  override fun isDbLockedByOtherThreads(): Boolean {
    return false
  }

  override fun yieldIfContendedSafely(): Boolean {
    return database.yieldIfContendedSafely()
  }

  override fun yieldIfContendedSafely(sleepAfterYieldDelayMillis: Long): Boolean {
    return database.yieldIfContendedSafely(sleepAfterYieldDelayMillis)
  }

  override var version: Int
    get() = database.version
    set(value) {
      database.version = value
    }

  override val maximumSize: Long
    get() = database.maximumSize

  override fun setMaximumSize(numBytes: Long): Long {
    return database.setMaximumSize(numBytes)
  }

  override var pageSize: Long
    get() = database.pageSize
    set(value) {
      database.pageSize = value
    }

  override fun compileStatement(sql: String): SQLCipherSQLiteStatement {
    throw UnsupportedOperationException()
  }

  override val isReadOnly: Boolean
    get() = database.isReadOnly

  override val isOpen: Boolean
    get() = database.isOpen

  override fun needUpgrade(newVersion: Int): Boolean {
    return database.needUpgrade(newVersion)
  }

  override fun setLocale(locale: Locale) {
    database.setLocale(locale)
  }
}
