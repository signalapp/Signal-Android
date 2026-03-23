/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.testing

import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.database.sqlite.SQLiteTransactionListener
import android.os.CancellationSignal
import android.util.Pair
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteProgram
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.sqlite.db.SupportSQLiteStatement
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.Types
import java.util.Locale

/**
 * A [SupportSQLiteDatabase] backed by JDBC (sqlite-jdbc / org.xerial). Provides a modern SQLite
 * engine with FTS5 and JSON1 support for unit tests, replacing Robolectric's limited native SQLite.
 */
class JdbcSqliteDatabase private constructor(private val connection: Connection) : SupportSQLiteDatabase {

  private var transactionSuccessful = false
  private var transactionNesting = 0

  companion object {
    private val CONFLICT_VALUES = arrayOf("", " OR ROLLBACK", " OR ABORT", " OR FAIL", " OR IGNORE", " OR REPLACE")

    fun createInMemory(): JdbcSqliteDatabase {
      Class.forName("org.sqlite.JDBC")
      val connection = DriverManager.getConnection("jdbc:sqlite::memory:")
      connection.autoCommit = true
      return JdbcSqliteDatabase(connection)
    }
  }

  // region Transaction Management

  override fun beginTransaction() {
    if (transactionNesting == 0) {
      connection.createStatement().use { it.execute("BEGIN IMMEDIATE") }
    } else {
      connection.createStatement().use { it.execute("SAVEPOINT sp_$transactionNesting") }
    }
    transactionNesting++
    transactionSuccessful = false
  }

  override fun beginTransactionNonExclusive() {
    if (transactionNesting == 0) {
      connection.createStatement().use { it.execute("BEGIN DEFERRED") }
    } else {
      connection.createStatement().use { it.execute("SAVEPOINT sp_$transactionNesting") }
    }
    transactionNesting++
    transactionSuccessful = false
  }

  override fun beginTransactionWithListener(transactionListener: SQLiteTransactionListener) {
    beginTransaction()
    transactionListener.onBegin()
  }

  override fun beginTransactionWithListenerNonExclusive(transactionListener: SQLiteTransactionListener) {
    beginTransactionNonExclusive()
    transactionListener.onBegin()
  }

  override fun endTransaction() {
    transactionNesting--
    if (transactionNesting == 0) {
      if (transactionSuccessful) {
        connection.createStatement().use { it.execute("COMMIT") }
      } else {
        connection.createStatement().use { it.execute("ROLLBACK") }
      }
    } else {
      if (!transactionSuccessful) {
        connection.createStatement().use { it.execute("ROLLBACK TO SAVEPOINT sp_$transactionNesting") }
      }
      connection.createStatement().use { it.execute("RELEASE SAVEPOINT sp_$transactionNesting") }
    }
    transactionSuccessful = false
  }

  override fun setTransactionSuccessful() {
    transactionSuccessful = true
  }

  override fun inTransaction(): Boolean = transactionNesting > 0

  override val isDbLockedByCurrentThread: Boolean
    get() = true

  override fun yieldIfContendedSafely(): Boolean = false

  override fun yieldIfContendedSafely(sleepAfterYieldDelay: Long): Boolean = false

  // endregion

  // region Query

  override fun query(query: String): Cursor = query(query, emptyArray())

  override fun query(query: String, bindArgs: Array<out Any?>): Cursor {
    return executeQuery(query, bindArgs)
  }

  override fun query(query: SupportSQLiteQuery): Cursor {
    val capture = BindingCapture(query.argCount)
    query.bindTo(capture)
    return executeQuery(query.sql, capture.getArgs())
  }

  override fun query(query: SupportSQLiteQuery, cancellationSignal: CancellationSignal?): Cursor {
    return query(query)
  }

  private fun executeQuery(sql: String, bindArgs: Array<out Any?>): Cursor {
    val stmt = connection.prepareStatement(sql)
    bindArgs(stmt, bindArgs)

    // sqlite-jdbc throws if you call executeQuery() on a non-SELECT statement.
    // Some callers (e.g. migrations) pass UPDATE/INSERT through rawQuery, so we
    // use execute() and check whether there's a result set.
    val hasResultSet = stmt.execute()
    if (!hasResultSet) {
      stmt.close()
      return MatrixCursor(emptyArray())
    }

    val rs = stmt.resultSet
    val metaData = rs.metaData
    val columnCount = metaData.columnCount
    val columnNames = Array(columnCount) { metaData.getColumnLabel(it + 1) }
    val cursor = MatrixCursor(columnNames)
    while (rs.next()) {
      val row = Array<Any?>(columnCount) { rs.getObject(it + 1) }
      cursor.addRow(row)
    }
    rs.close()
    stmt.close()
    return cursor
  }

  // endregion

  // region Insert / Update / Delete

  override fun insert(table: String, conflictAlgorithm: Int, values: ContentValues): Long {
    val keys = values.keySet().toList()
    if (keys.isEmpty()) {
      val sql = "INSERT${CONFLICT_VALUES[conflictAlgorithm]} INTO $table DEFAULT VALUES"
      connection.createStatement().use { it.executeUpdate(sql) }
    } else {
      val columns = keys.joinToString(", ")
      val placeholders = keys.joinToString(", ") { "?" }
      val sql = "INSERT${CONFLICT_VALUES[conflictAlgorithm]} INTO $table ($columns) VALUES ($placeholders)"
      val stmt = connection.prepareStatement(sql)
      keys.forEachIndexed { index, key -> bindArg(stmt, index + 1, values.get(key)) }
      stmt.executeUpdate()
      stmt.close()
    }
    return connection.createStatement().use { s ->
      s.executeQuery("SELECT last_insert_rowid()").use { rs ->
        if (rs.next()) rs.getLong(1) else -1L
      }
    }
  }

  override fun update(table: String, conflictAlgorithm: Int, values: ContentValues, whereClause: String?, whereArgs: Array<out Any?>?): Int {
    val keys = values.keySet().toList()
    val setClause = keys.joinToString(", ") { "$it = ?" }
    val sql = buildString {
      append("UPDATE${CONFLICT_VALUES[conflictAlgorithm]} $table SET $setClause")
      if (!whereClause.isNullOrEmpty()) {
        append(" WHERE $whereClause")
      }
    }
    val stmt = connection.prepareStatement(sql)
    var paramIndex = 1
    keys.forEach { key -> bindArg(stmt, paramIndex++, values.get(key)) }
    whereArgs?.forEach { arg -> bindArg(stmt, paramIndex++, arg) }
    val count = stmt.executeUpdate()
    stmt.close()
    return count
  }

  override fun delete(table: String, whereClause: String?, whereArgs: Array<out Any?>?): Int {
    val sql = buildString {
      append("DELETE FROM $table")
      if (!whereClause.isNullOrEmpty()) {
        append(" WHERE $whereClause")
      }
    }
    val stmt = connection.prepareStatement(sql)
    whereArgs?.forEachIndexed { index, arg -> bindArg(stmt, index + 1, arg) }
    val count = stmt.executeUpdate()
    stmt.close()
    return count
  }

  // endregion

  // region ExecSQL

  override fun execSQL(sql: String) {
    connection.createStatement().use { it.execute(sql) }
  }

  override fun execSQL(sql: String, bindArgs: Array<out Any?>) {
    val stmt = connection.prepareStatement(sql)
    bindArgs(stmt, bindArgs)
    stmt.execute()
    stmt.close()
  }

  // endregion

  // region CompileStatement

  override fun compileStatement(sql: String): SupportSQLiteStatement {
    return JdbcSqliteStatement(connection.prepareStatement(sql), connection)
  }

  // endregion

  // region Properties

  override var version: Int
    get() {
      return connection.createStatement().use { s ->
        s.executeQuery("PRAGMA user_version").use { rs ->
          if (rs.next()) rs.getInt(1) else 0
        }
      }
    }
    set(value) {
      connection.createStatement().use { it.execute("PRAGMA user_version = $value") }
    }

  override val maximumSize: Long
    get() {
      val pageCount = connection.createStatement().use { s ->
        s.executeQuery("PRAGMA max_page_count").use { rs ->
          if (rs.next()) rs.getLong(1) else 0L
        }
      }
      return pageSize * pageCount
    }

  override fun setMaximumSize(numBytes: Long): Long {
    var numPages = numBytes / pageSize
    if (numBytes % pageSize != 0L) numPages++
    connection.createStatement().use { it.execute("PRAGMA max_page_count = $numPages") }
    return maximumSize
  }

  override var pageSize: Long
    get() {
      return connection.createStatement().use { s ->
        s.executeQuery("PRAGMA page_size").use { rs ->
          if (rs.next()) rs.getLong(1) else 4096L
        }
      }
    }
    set(value) {
      connection.createStatement().use { it.execute("PRAGMA page_size = $value") }
    }

  override val isReadOnly: Boolean
    get() = false

  override val isOpen: Boolean
    get() = !connection.isClosed

  override fun needUpgrade(newVersion: Int): Boolean = version < newVersion

  override val path: String?
    get() = null

  override fun setLocale(locale: Locale) = Unit

  override fun setMaxSqlCacheSize(cacheSize: Int) = Unit

  override fun setForeignKeyConstraintsEnabled(enable: Boolean) {
    connection.createStatement().use { it.execute("PRAGMA foreign_keys = ${if (enable) "ON" else "OFF"}") }
  }

  override fun enableWriteAheadLogging(): Boolean = false

  override fun disableWriteAheadLogging() = Unit

  override val isWriteAheadLoggingEnabled: Boolean
    get() = false

  override val attachedDbs: List<Pair<String, String>>?
    get() = null

  override val isDatabaseIntegrityOk: Boolean
    get() = true

  override fun close() {
    if (!connection.isClosed) {
      connection.close()
    }
  }

  // endregion

  // region Helpers

  private fun bindArgs(stmt: PreparedStatement, args: Array<out Any?>) {
    args.forEachIndexed { index, arg -> bindArg(stmt, index + 1, arg) }
  }

  private fun bindArg(stmt: PreparedStatement, index: Int, arg: Any?) {
    when (arg) {
      null -> stmt.setNull(index, Types.NULL)
      is String -> stmt.setString(index, arg)
      is Long -> stmt.setLong(index, arg)
      is Int -> stmt.setInt(index, arg)
      is Short -> stmt.setShort(index, arg)
      is Byte -> stmt.setByte(index, arg)
      is Double -> stmt.setDouble(index, arg)
      is Float -> stmt.setFloat(index, arg)
      is ByteArray -> stmt.setBytes(index, arg)
      is Boolean -> stmt.setInt(index, if (arg) 1 else 0)
      else -> stmt.setString(index, arg.toString())
    }
  }

  /**
   * Captures binding operations from a [SupportSQLiteQuery] so they can be replayed onto a JDBC
   * [PreparedStatement].
   */
  private class BindingCapture(argCount: Int) : SupportSQLiteProgram {
    private val bindings = arrayOfNulls<Any>(argCount)

    override fun bindNull(index: Int) { bindings[index - 1] = NULL_SENTINEL }
    override fun bindLong(index: Int, value: Long) { bindings[index - 1] = value }
    override fun bindDouble(index: Int, value: Double) { bindings[index - 1] = value }
    override fun bindString(index: Int, value: String) { bindings[index - 1] = value }
    override fun bindBlob(index: Int, value: ByteArray) { bindings[index - 1] = value }
    override fun clearBindings() { bindings.fill(null) }
    override fun close() = Unit

    fun getArgs(): Array<out Any?> = bindings.map { if (it === NULL_SENTINEL) null else it }.toTypedArray()

    companion object {
      private val NULL_SENTINEL = Object()
    }
  }

  // endregion
}

/**
 * A [SupportSQLiteStatement] backed by a JDBC [PreparedStatement].
 */
class JdbcSqliteStatement(
  private val statement: PreparedStatement,
  private val connection: Connection
) : SupportSQLiteStatement {

  override fun execute() {
    statement.execute()
  }

  override fun executeUpdateDelete(): Int {
    return statement.executeUpdate()
  }

  override fun executeInsert(): Long {
    statement.executeUpdate()
    return connection.createStatement().use { s ->
      s.executeQuery("SELECT last_insert_rowid()").use { rs ->
        if (rs.next()) rs.getLong(1) else -1L
      }
    }
  }

  override fun simpleQueryForLong(): Long {
    val rs = statement.executeQuery()
    return if (rs.next()) rs.getLong(1) else throw android.database.sqlite.SQLiteDoneException()
  }

  override fun simpleQueryForString(): String? {
    val rs = statement.executeQuery()
    return if (rs.next()) rs.getString(1) else throw android.database.sqlite.SQLiteDoneException()
  }

  override fun bindNull(index: Int) {
    statement.setNull(index, Types.NULL)
  }

  override fun bindLong(index: Int, value: Long) {
    statement.setLong(index, value)
  }

  override fun bindDouble(index: Int, value: Double) {
    statement.setDouble(index, value)
  }

  override fun bindString(index: Int, value: String) {
    statement.setString(index, value)
  }

  override fun bindBlob(index: Int, value: ByteArray) {
    statement.setBytes(index, value)
  }

  override fun clearBindings() {
    statement.clearParameters()
  }

  override fun close() {
    statement.close()
  }
}
