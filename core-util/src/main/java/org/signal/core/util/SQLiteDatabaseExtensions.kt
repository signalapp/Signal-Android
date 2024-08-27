package org.signal.core.util

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.core.content.contentValuesOf
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteQueryBuilder

/**
 * Begins a transaction on the `this` database, runs the provided [block] providing the `this` value as it's argument
 * within the transaction, and then ends the transaction successfully.
 *
 * @return The value returned by [block] if any
 */
fun <T : SupportSQLiteDatabase, R> T.withinTransaction(block: (T) -> R): R {
  beginTransaction()
  try {
    val toReturn = block(this)
    setTransactionSuccessful()
    return toReturn
  } finally {
    endTransaction()
  }
}

fun SupportSQLiteDatabase.getTableRowCount(table: String): Int {
  return this.query("SELECT COUNT(*) FROM $table").use {
    if (it.moveToFirst()) {
      it.getInt(0)
    } else {
      0
    }
  }
}

fun SupportSQLiteDatabase.getAllTables(): List<String> {
  return SqlUtil.getAllTables(this)
}

/**
 * Returns a list of objects that represent the table definitions in the database. Basically the table name and then the SQL that was used to create it.
 */
fun SupportSQLiteDatabase.getAllTableDefinitions(): List<CreateStatement> {
  return this.query("SELECT name, sql FROM sqlite_schema WHERE type = 'table' AND sql NOT NULL AND name != 'sqlite_sequence'")
    .readToList { cursor ->
      CreateStatement(
        name = cursor.requireNonNullString("name"),
        statement = cursor.requireNonNullString("sql").replace("      ", "")
      )
    }
    .filterNot { it.name.startsWith("sqlite_stat") }
    .sortedBy { it.name }
}

/**
 * Returns a list of objects that represent the index definitions in the database. Basically the index name and then the SQL that was used to create it.
 */
fun SupportSQLiteDatabase.getAllIndexDefinitions(): List<CreateStatement> {
  return this.query("SELECT name, sql FROM sqlite_schema WHERE type = 'index' AND sql NOT NULL")
    .readToList { cursor ->
      CreateStatement(
        name = cursor.requireNonNullString("name"),
        statement = cursor.requireNonNullString("sql")
      )
    }
    .sortedBy { it.name }
}

fun SupportSQLiteDatabase.getForeignKeys(): List<ForeignKeyConstraint> {
  return SqlUtil.getAllTables(this)
    .map { table ->
      this.query("PRAGMA foreign_key_list($table)").readToList { cursor ->
        ForeignKeyConstraint(
          table = table,
          column = cursor.requireNonNullString("from"),
          dependsOnTable = cursor.requireNonNullString("table"),
          dependsOnColumn = cursor.requireNonNullString("to"),
          onDelete = cursor.requireString("on_delete") ?: "NOTHING"
        )
      }
    }
    .flatten()
}

fun SupportSQLiteDatabase.areForeignKeyConstraintsEnabled(): Boolean {
  return this.query("PRAGMA foreign_keys", null).use { cursor ->
    cursor.moveToFirst() && cursor.getInt(0) != 0
  }
}

/**
 * Does a full WAL checkpoint (TRUNCATE mode, where the log is for sure flushed and the log is zero'd out).
 * Will try up to [maxAttempts] times. Can technically fail if the database is too active and the checkpoint
 * can't complete in a reasonable amount of time.
 *
 * See: https://www.sqlite.org/pragma.html#pragma_wal_checkpoint
 */
fun SupportSQLiteDatabase.fullWalCheckpoint(maxAttempts: Int = 3): Boolean {
  var attempts = 0

  while (attempts < maxAttempts) {
    if (this.walCheckpoint()) {
      return true
    }

    attempts++
  }

  return false
}

private fun SupportSQLiteDatabase.walCheckpoint(): Boolean {
  return this.query("PRAGMA wal_checkpoint(TRUNCATE)").use { cursor ->
    cursor.moveToFirst() && cursor.getInt(0) == 0
  }
}

fun SupportSQLiteDatabase.getIndexes(): List<Index> {
  return this.query("SELECT name, tbl_name FROM sqlite_master WHERE type='index' ORDER BY name ASC").readToList { cursor ->
    val indexName = cursor.requireNonNullString("name")

    Index(
      name = indexName,
      table = cursor.requireNonNullString("tbl_name"),
      columns = this.query("PRAGMA index_info($indexName)").readToList { it.requireNonNullString("name") }
    )
  }
}

/**
 * Checks if a row exists that matches the query.
 */
fun SupportSQLiteDatabase.exists(table: String): ExistsBuilderPart1 {
  return ExistsBuilderPart1(this, table)
}

/**
 * Begins a SELECT statement with a helpful builder pattern.
 */
fun SupportSQLiteDatabase.select(vararg columns: String): SelectBuilderPart1 {
  return SelectBuilderPart1(this, arrayOf(*columns))
}

/**
 * Begins a COUNT statement with a helpful builder pattern.
 */
fun SupportSQLiteDatabase.count(): SelectBuilderPart1 {
  return SelectBuilderPart1(this, SqlUtil.COUNT)
}

/**
 * Begins an UPDATE statement with a helpful builder pattern.
 * Requires a WHERE clause as a way of mitigating mistakes. If you'd like to update all items in the table, use [updateAll].
 */
fun SupportSQLiteDatabase.update(tableName: String): UpdateBuilderPart1 {
  return UpdateBuilderPart1(this, tableName)
}

fun SupportSQLiteDatabase.updateAll(tableName: String): UpdateAllBuilderPart1 {
  return UpdateAllBuilderPart1(this, tableName)
}

/**
 * Begins a DELETE statement with a helpful builder pattern.
 * Requires a WHERE clause as a way of mitigating mistakes. If you'd like to delete all items in the table, use [deleteAll].
 */
fun SupportSQLiteDatabase.delete(tableName: String): DeleteBuilderPart1 {
  return DeleteBuilderPart1(this, tableName)
}

/**
 * Deletes all data in the table.
 */
fun SupportSQLiteDatabase.deleteAll(tableName: String): Int {
  return this.delete(tableName, null, arrayOfNulls<String>(0))
}

fun SupportSQLiteDatabase.insertInto(tableName: String): InsertBuilderPart1 {
  return InsertBuilderPart1(this, tableName)
}

class SelectBuilderPart1(
  private val db: SupportSQLiteDatabase,
  private val columns: Array<String>
) {
  fun from(tableName: String): SelectBuilderPart2 {
    return SelectBuilderPart2(db, columns, tableName)
  }
}

class SelectBuilderPart2(
  private val db: SupportSQLiteDatabase,
  private val columns: Array<String>,
  private val tableName: String
) {
  fun where(where: String, vararg whereArgs: Any): SelectBuilderPart3 {
    return SelectBuilderPart3(db, columns, tableName, where, SqlUtil.buildArgs(*whereArgs))
  }

  fun where(where: String, whereArgs: Array<String>): SelectBuilderPart3 {
    return SelectBuilderPart3(db, columns, tableName, where, whereArgs)
  }

  fun orderBy(orderBy: String): SelectBuilderPart4a {
    return SelectBuilderPart4a(db, columns, tableName, "", arrayOf(), orderBy)
  }

  fun limit(limit: Int): SelectBuilderPart4b {
    return SelectBuilderPart4b(db, columns, tableName, "", arrayOf(), limit.toString())
  }

  fun run(): Cursor {
    return db.query(
      SupportSQLiteQueryBuilder
        .builder(tableName)
        .columns(columns)
        .create()
    )
  }
}

class SelectBuilderPart3(
  private val db: SupportSQLiteDatabase,
  private val columns: Array<String>,
  private val tableName: String,
  private val where: String,
  private val whereArgs: Array<String>
) {
  fun orderBy(orderBy: String): SelectBuilderPart4a {
    return SelectBuilderPart4a(db, columns, tableName, where, whereArgs, orderBy)
  }

  fun limit(limit: Int): SelectBuilderPart4b {
    return SelectBuilderPart4b(db, columns, tableName, where, whereArgs, limit.toString())
  }

  fun limit(limit: String): SelectBuilderPart4b {
    return SelectBuilderPart4b(db, columns, tableName, where, whereArgs, limit)
  }

  fun limit(limit: Int, offset: Int): SelectBuilderPart4b {
    return SelectBuilderPart4b(db, columns, tableName, where, whereArgs, "$offset,$limit")
  }

  fun run(): Cursor {
    return db.query(
      SupportSQLiteQueryBuilder
        .builder(tableName)
        .columns(columns)
        .selection(where, whereArgs)
        .create()
    )
  }
}

class SelectBuilderPart4a(
  private val db: SupportSQLiteDatabase,
  private val columns: Array<String>,
  private val tableName: String,
  private val where: String,
  private val whereArgs: Array<String>,
  private val orderBy: String
) {
  fun limit(limit: Int): SelectBuilderPart5 {
    return SelectBuilderPart5(db, columns, tableName, where, whereArgs, orderBy, limit.toString())
  }

  fun limit(limit: String): SelectBuilderPart5 {
    return SelectBuilderPart5(db, columns, tableName, where, whereArgs, orderBy, limit)
  }

  fun limit(limit: Int, offset: Int): SelectBuilderPart5 {
    return SelectBuilderPart5(db, columns, tableName, where, whereArgs, orderBy, "$offset,$limit")
  }

  fun run(): Cursor {
    return db.query(
      SupportSQLiteQueryBuilder
        .builder(tableName)
        .columns(columns)
        .selection(where, whereArgs)
        .orderBy(orderBy)
        .create()
    )
  }
}

class SelectBuilderPart4b(
  private val db: SupportSQLiteDatabase,
  private val columns: Array<String>,
  private val tableName: String,
  private val where: String,
  private val whereArgs: Array<String>,
  private val limit: String
) {
  fun orderBy(orderBy: String): SelectBuilderPart5 {
    return SelectBuilderPart5(db, columns, tableName, where, whereArgs, orderBy, limit)
  }

  fun run(): Cursor {
    return db.query(
      SupportSQLiteQueryBuilder
        .builder(tableName)
        .columns(columns)
        .selection(where, whereArgs)
        .limit(limit)
        .create()
    )
  }
}

class SelectBuilderPart5(
  private val db: SupportSQLiteDatabase,
  private val columns: Array<String>,
  private val tableName: String,
  private val where: String,
  private val whereArgs: Array<String>,
  private val orderBy: String,
  private val limit: String
) {
  fun run(): Cursor {
    return db.query(
      SupportSQLiteQueryBuilder
        .builder(tableName)
        .columns(columns)
        .selection(where, whereArgs)
        .orderBy(orderBy)
        .limit(limit)
        .create()
    )
  }
}

class UpdateBuilderPart1(
  private val db: SupportSQLiteDatabase,
  private val tableName: String
) {
  fun values(values: ContentValues): UpdateBuilderPart2 {
    return UpdateBuilderPart2(db, tableName, values)
  }

  fun values(vararg values: Pair<String, Any?>): UpdateBuilderPart2 {
    return UpdateBuilderPart2(db, tableName, contentValuesOf(*values))
  }
}

class UpdateBuilderPart2(
  private val db: SupportSQLiteDatabase,
  private val tableName: String,
  private val values: ContentValues
) {
  fun where(where: String, vararg whereArgs: Any): UpdateBuilderPart3 {
    require(where.isNotBlank())
    return UpdateBuilderPart3(db, tableName, values, where, SqlUtil.buildArgs(*whereArgs))
  }

  fun where(where: String, whereArgs: Array<String>): UpdateBuilderPart3 {
    require(where.isNotBlank())
    return UpdateBuilderPart3(db, tableName, values, where, whereArgs)
  }
}

class UpdateBuilderPart3(
  private val db: SupportSQLiteDatabase,
  private val tableName: String,
  private val values: ContentValues,
  private val where: String,
  private val whereArgs: Array<String>
) {
  @JvmOverloads
  fun run(conflictStrategy: Int = SQLiteDatabase.CONFLICT_NONE): Int {
    return db.update(tableName, conflictStrategy, values, where, whereArgs)
  }
}

class UpdateAllBuilderPart1(
  private val db: SupportSQLiteDatabase,
  private val tableName: String
) {
  fun values(values: ContentValues): UpdateAllBuilderPart2 {
    return UpdateAllBuilderPart2(db, tableName, values)
  }

  fun values(vararg values: Pair<String, Any?>): UpdateAllBuilderPart2 {
    return UpdateAllBuilderPart2(db, tableName, contentValuesOf(*values))
  }
}

class UpdateAllBuilderPart2(
  private val db: SupportSQLiteDatabase,
  private val tableName: String,
  private val values: ContentValues
) {
  @JvmOverloads
  fun run(conflictStrategy: Int = SQLiteDatabase.CONFLICT_NONE): Int {
    return db.update(tableName, conflictStrategy, values, null, emptyArray<String>())
  }
}

class DeleteBuilderPart1(
  private val db: SupportSQLiteDatabase,
  private val tableName: String
) {
  fun where(where: String, vararg whereArgs: Any): DeleteBuilderPart2 {
    require(where.isNotBlank())
    return DeleteBuilderPart2(db, tableName, where, SqlUtil.buildArgs(*whereArgs))
  }

  fun where(where: String, whereArgs: Array<String>): DeleteBuilderPart2 {
    require(where.isNotBlank())
    return DeleteBuilderPart2(db, tableName, where, whereArgs)
  }
}

class DeleteBuilderPart2(
  private val db: SupportSQLiteDatabase,
  private val tableName: String,
  private val where: String,
  private val whereArgs: Array<String>
) {
  fun run(): Int {
    return db.delete(tableName, where, whereArgs)
  }
}

class ExistsBuilderPart1(
  private val db: SupportSQLiteDatabase,
  private val tableName: String
) {

  fun where(where: String, vararg whereArgs: Any): ExistsBuilderPart2 {
    return ExistsBuilderPart2(db, tableName, where, SqlUtil.buildArgs(*whereArgs))
  }

  fun where(where: String, whereArgs: Array<String>): ExistsBuilderPart2 {
    return ExistsBuilderPart2(db, tableName, where, whereArgs)
  }

  fun run(): Boolean {
    return db.query("SELECT EXISTS(SELECT 1 FROM $tableName)", null).use { cursor ->
      cursor.moveToFirst() && cursor.getInt(0) == 1
    }
  }
}

class ExistsBuilderPart2(
  private val db: SupportSQLiteDatabase,
  private val tableName: String,
  private val where: String,
  private val whereArgs: Array<String>
) {
  fun run(): Boolean {
    return db.query("SELECT EXISTS(SELECT 1 FROM $tableName WHERE $where)", SqlUtil.buildArgs(*whereArgs)).use { cursor ->
      cursor.moveToFirst() && cursor.getInt(0) == 1
    }
  }
}

class InsertBuilderPart1(
  private val db: SupportSQLiteDatabase,
  private val tableName: String
) {

  fun values(values: ContentValues): InsertBuilderPart2 {
    return InsertBuilderPart2(db, tableName, values)
  }
  fun values(vararg values: Pair<String, Any?>): InsertBuilderPart2 {
    return InsertBuilderPart2(db, tableName, contentValuesOf(*values))
  }
}

class InsertBuilderPart2(
  private val db: SupportSQLiteDatabase,
  private val tableName: String,
  private val values: ContentValues
) {
  fun run(conflictStrategy: Int = SQLiteDatabase.CONFLICT_IGNORE): Long {
    return db.insert(tableName, conflictStrategy, values)
  }
}

data class ForeignKeyConstraint(
  val table: String,
  val column: String,
  val dependsOnTable: String,
  val dependsOnColumn: String,
  val onDelete: String
)

data class Index(
  val name: String,
  val table: String,
  val columns: List<String>
)

data class CreateStatement(
  val name: String,
  val statement: String
)
