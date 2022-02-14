package org.signal.spinnertest

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteTransactionListener
import android.os.CancellationSignal
import android.util.Pair
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.sqlite.db.SupportSQLiteStatement
import java.util.Locale

class SpinnerTestSqliteOpenHelper(context: Context?) :
  SQLiteOpenHelper(context, "test", null, 2), SupportSQLiteDatabase {
  override fun onCreate(db: SQLiteDatabase) {
    db.execSQL("CREATE TABLE test (id INTEGER PRIMARY KEY, col1 TEXT, col2 TEXT)")
  }

  override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    if (oldVersion < 2) {
      db.execSQL("CREATE INDEX test_col1_index ON test (col1)")
    }
  }

  override fun compileStatement(sql: String?): SupportSQLiteStatement {
    TODO("Not yet implemented")
  }

  override fun beginTransaction() {
    writableDatabase.beginTransaction()
  }

  override fun beginTransactionNonExclusive() {
    writableDatabase.beginTransactionNonExclusive()
  }

  override fun beginTransactionWithListener(transactionListener: SQLiteTransactionListener?) {
    writableDatabase.beginTransactionWithListener(transactionListener)
  }

  override fun beginTransactionWithListenerNonExclusive(transactionListener: SQLiteTransactionListener?) {
    writableDatabase.beginTransactionWithListenerNonExclusive(transactionListener)
  }

  override fun endTransaction() {
    writableDatabase.endTransaction()
  }

  override fun setTransactionSuccessful() {
    writableDatabase.setTransactionSuccessful()
  }

  override fun inTransaction(): Boolean {
    return writableDatabase.inTransaction()
  }

  override fun isDbLockedByCurrentThread(): Boolean {
    return writableDatabase.isDbLockedByCurrentThread
  }

  override fun yieldIfContendedSafely(): Boolean {
    return writableDatabase.yieldIfContendedSafely()
  }

  override fun yieldIfContendedSafely(sleepAfterYieldDelay: Long): Boolean {
    return writableDatabase.yieldIfContendedSafely(sleepAfterYieldDelay)
  }

  override fun getVersion(): Int {
    return writableDatabase.version
  }

  override fun setVersion(version: Int) {
    writableDatabase.version = version
  }

  override fun getMaximumSize(): Long {
    return writableDatabase.maximumSize
  }

  override fun setMaximumSize(numBytes: Long): Long {
    writableDatabase.maximumSize = numBytes
    return writableDatabase.maximumSize
  }

  override fun getPageSize(): Long {
    return writableDatabase.pageSize
  }

  override fun setPageSize(numBytes: Long) {
    writableDatabase.pageSize = numBytes
  }

  override fun query(query: String?): Cursor {
    return readableDatabase.rawQuery(query, null)
  }

  override fun query(query: String?, bindArgs: Array<out Any>?): Cursor {
    return readableDatabase.rawQuery(query, bindArgs?.map { it.toString() }?.toTypedArray())
  }

  override fun query(query: SupportSQLiteQuery?): Cursor {
    TODO("Not yet implemented")
  }

  override fun query(query: SupportSQLiteQuery?, cancellationSignal: CancellationSignal?): Cursor {
    TODO("Not yet implemented")
  }

  override fun insert(table: String?, conflictAlgorithm: Int, values: ContentValues?): Long {
    return writableDatabase.insertWithOnConflict(table, null, values, conflictAlgorithm)
  }

  override fun delete(table: String?, whereClause: String?, whereArgs: Array<out Any>?): Int {
    return writableDatabase.delete(table, whereClause, whereArgs?.map { it.toString() }?.toTypedArray())
  }

  override fun update(table: String?, conflictAlgorithm: Int, values: ContentValues?, whereClause: String?, whereArgs: Array<out Any>?): Int {
    return writableDatabase.updateWithOnConflict(table, values, whereClause, whereArgs?.map { it.toString() }?.toTypedArray(), conflictAlgorithm)
  }

  override fun execSQL(sql: String?) {
    writableDatabase.execSQL(sql)
  }

  override fun execSQL(sql: String?, bindArgs: Array<out Any>?) {
    writableDatabase.execSQL(sql, bindArgs?.map { it.toString() }?.toTypedArray())
  }

  override fun isReadOnly(): Boolean {
    return readableDatabase.isReadOnly
  }

  override fun isOpen(): Boolean {
    return readableDatabase.isOpen
  }

  override fun needUpgrade(newVersion: Int): Boolean {
    return readableDatabase.needUpgrade(newVersion)
  }

  override fun getPath(): String {
    return readableDatabase.path
  }

  override fun setLocale(locale: Locale?) {
    writableDatabase.setLocale(locale)
  }

  override fun setMaxSqlCacheSize(cacheSize: Int) {
    writableDatabase.setMaxSqlCacheSize(cacheSize)
  }

  override fun setForeignKeyConstraintsEnabled(enable: Boolean) {
    writableDatabase.setForeignKeyConstraintsEnabled(enable)
  }

  override fun enableWriteAheadLogging(): Boolean {
    return writableDatabase.enableWriteAheadLogging()
  }

  override fun disableWriteAheadLogging() {
    writableDatabase.disableWriteAheadLogging()
  }

  override fun isWriteAheadLoggingEnabled(): Boolean {
    return readableDatabase.isWriteAheadLoggingEnabled
  }

  override fun getAttachedDbs(): MutableList<Pair<String, String>> {
    return readableDatabase.attachedDbs
  }

  override fun isDatabaseIntegrityOk(): Boolean {
    return readableDatabase.isDatabaseIntegrityOk
  }
}
