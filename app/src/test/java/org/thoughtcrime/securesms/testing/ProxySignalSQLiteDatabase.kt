package org.thoughtcrime.securesms.testing

import android.content.ContentValues
import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteQuery
import org.signal.core.util.toAndroidQuery
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
class ProxySignalSQLiteDatabase(private val database: AndroidSQLiteDatabase) : SignalSQLiteDatabase(null) {
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

  override fun query(distinct: Boolean, table: String?, columns: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, groupBy: String?, having: String?, orderBy: String?, limit: String?): Cursor {
    return database.query(distinct, table, columns, selection, selectionArgs, groupBy, having, orderBy, limit)
  }

  override fun queryWithFactory(
    cursorFactory: net.zetetic.database.sqlcipher.SQLiteDatabase.CursorFactory?,
    distinct: Boolean,
    table: String?,
    columns: Array<out String>?,
    selection: String?,
    selectionArgs: Array<out String>?,
    groupBy: String?,
    having: String?,
    orderBy: String?,
    limit: String?
  ): Cursor {
    return database.queryWithFactory(null, distinct, table, columns, selection, selectionArgs, groupBy, having, orderBy, limit)
  }

  override fun query(query: SupportSQLiteQuery): Cursor? {
    val converted = query.toAndroidQuery()
    return database.rawQuery(converted.where, converted.whereArgs)
  }

  override fun query(table: String?, columns: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, groupBy: String?, having: String?, orderBy: String?): Cursor {
    return database.query(table, columns, selection, selectionArgs, groupBy, having, orderBy)
  }

  override fun query(table: String?, columns: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, groupBy: String?, having: String?, orderBy: String?, limit: String?): Cursor {
    return database.query(table, columns, selection, selectionArgs, groupBy, having, orderBy, limit)
  }

  override fun rawQuery(sql: String?, selectionArgs: Array<out String>?): Cursor {
    return database.rawQuery(sql, selectionArgs)
  }

  override fun rawQuery(sql: String?, args: Array<out Any>?): Cursor {
    return database.rawQuery(sql, args?.map(Any::toString)?.toTypedArray())
  }

  override fun rawQueryWithFactory(cursorFactory: net.zetetic.database.sqlcipher.SQLiteDatabase.CursorFactory?, sql: String?, selectionArgs: Array<out String>?, editTable: String?): Cursor {
    return database.rawQueryWithFactory(null, sql, selectionArgs, editTable)
  }

  override fun rawQuery(sql: String?, selectionArgs: Array<out String>?, initialRead: Int, maxRead: Int): Cursor {
    throw UnsupportedOperationException()
  }

  override fun insert(table: String?, nullColumnHack: String?, values: ContentValues?): Long {
    return database.insert(table, nullColumnHack, values)
  }

  override fun insertOrThrow(table: String?, nullColumnHack: String?, values: ContentValues?): Long {
    return database.insertOrThrow(table, nullColumnHack, values)
  }

  override fun replace(table: String?, nullColumnHack: String?, initialValues: ContentValues?): Long {
    return database.replace(table, nullColumnHack, initialValues)
  }

  override fun replaceOrThrow(table: String?, nullColumnHack: String?, initialValues: ContentValues?): Long {
    return database.replaceOrThrow(table, nullColumnHack, initialValues)
  }

  override fun insertWithOnConflict(table: String?, nullColumnHack: String?, initialValues: ContentValues?, conflictAlgorithm: Int): Long {
    return database.insertWithOnConflict(table, nullColumnHack, initialValues, conflictAlgorithm)
  }

  override fun delete(table: String?, whereClause: String?, whereArgs: Array<out String>?): Int {
    return database.delete(table, whereClause, whereArgs)
  }

  override fun update(table: String?, values: ContentValues?, whereClause: String?, whereArgs: Array<out String>?): Int {
    return database.update(table, values, whereClause, whereArgs)
  }

  override fun updateWithOnConflict(table: String?, values: ContentValues?, whereClause: String?, whereArgs: Array<out String>?, conflictAlgorithm: Int): Int {
    return database.updateWithOnConflict(table, values, whereClause, whereArgs, conflictAlgorithm)
  }

  override fun execSQL(sql: String?) {
    database.execSQL(sql)
  }

  override fun rawExecSQL(sql: String?) {
    database.execSQL(sql)
  }

  override fun execSQL(sql: String?, bindArgs: Array<out Any>?) {
    database.execSQL(sql, bindArgs)
  }

  override fun enableWriteAheadLogging(): Boolean {
    throw UnsupportedOperationException()
  }

  override fun disableWriteAheadLogging() {
    throw UnsupportedOperationException()
  }

  override fun isWriteAheadLoggingEnabled(): Boolean {
    throw UnsupportedOperationException()
  }

  override fun setForeignKeyConstraintsEnabled(enable: Boolean) {
    database.setForeignKeyConstraintsEnabled(enable)
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

  override fun isDbLockedByCurrentThread(): Boolean {
    return database.isDbLockedByCurrentThread
  }

  @Suppress("DEPRECATION")
  override fun isDbLockedByOtherThreads(): Boolean {
    return database.isDbLockedByOtherThreads
  }

  override fun yieldIfContendedSafely(): Boolean {
    return database.yieldIfContendedSafely()
  }

  override fun yieldIfContendedSafely(sleepAfterYieldDelay: Long): Boolean {
    return database.yieldIfContendedSafely(sleepAfterYieldDelay)
  }

  override fun getVersion(): Int {
    return database.version
  }

  override fun setVersion(version: Int) {
    database.version = version
  }

  override fun getMaximumSize(): Long {
    return database.maximumSize
  }

  override fun setMaximumSize(numBytes: Long): Long {
    return database.setMaximumSize(numBytes)
  }

  override fun getPageSize(): Long {
    return database.pageSize
  }

  override fun setPageSize(numBytes: Long) {
    database.pageSize = numBytes
  }

  override fun compileStatement(sql: String?): SQLCipherSQLiteStatement {
    throw UnsupportedOperationException()
  }

  override fun isReadOnly(): Boolean {
    return database.isReadOnly
  }

  override fun isOpen(): Boolean {
    return database.isOpen
  }

  override fun needUpgrade(newVersion: Int): Boolean {
    return database.needUpgrade(newVersion)
  }

  override fun setLocale(locale: Locale?) {
    database.setLocale(locale)
  }
}
