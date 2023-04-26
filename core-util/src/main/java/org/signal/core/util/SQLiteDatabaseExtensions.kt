package org.signal.core.util

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.core.content.contentValuesOf
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteQueryBuilder
import org.intellij.lang.annotations.Language

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
 */
fun SupportSQLiteDatabase.update(tableName: String): UpdateBuilderPart1 {
  return UpdateBuilderPart1(this, tableName)
}

/**
 * Begins a DELETE statement with a helpful builder pattern.
 */
fun SupportSQLiteDatabase.delete(tableName: String): DeleteBuilderPart1 {
  return DeleteBuilderPart1(this, tableName)
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
  fun where(@Language("sql") where: String, vararg whereArgs: Any): SelectBuilderPart3 {
    return SelectBuilderPart3(db, columns, tableName, where, SqlUtil.buildArgs(*whereArgs))
  }

  fun where(where: String, whereArgs: Array<String>): SelectBuilderPart3 {
    return SelectBuilderPart3(db, columns, tableName, where, whereArgs)
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
  fun where(@Language("sql") where: String, vararg whereArgs: Any): UpdateBuilderPart3 {
    return UpdateBuilderPart3(db, tableName, values, where, SqlUtil.buildArgs(*whereArgs))
  }

  fun where(@Language("sql") where: String, whereArgs: Array<String>): UpdateBuilderPart3 {
    return UpdateBuilderPart3(db, tableName, values, where, whereArgs)
  }

  fun run(conflictStrategy: Int = SQLiteDatabase.CONFLICT_NONE): Int {
    return db.update(tableName, conflictStrategy, values, null, null)
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

class DeleteBuilderPart1(
  private val db: SupportSQLiteDatabase,
  private val tableName: String
) {
  fun where(@Language("sql") where: String, vararg whereArgs: Any): DeleteBuilderPart2 {
    return DeleteBuilderPart2(db, tableName, where, SqlUtil.buildArgs(*whereArgs))
  }

  fun where(@Language("sql") where: String, whereArgs: Array<String>): DeleteBuilderPart2 {
    return DeleteBuilderPart2(db, tableName, where, whereArgs)
  }

  fun run(): Int {
    return db.delete(tableName, null, null)
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

  fun where(@Language("sql") where: String, vararg whereArgs: Any): ExistsBuilderPart2 {
    return ExistsBuilderPart2(db, tableName, where, SqlUtil.buildArgs(*whereArgs))
  }

  fun where(@Language("sql") where: String, whereArgs: Array<String>): ExistsBuilderPart2 {
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
  fun run(conflictStrategy: Int = SQLiteDatabase.CONFLICT_NONE): Long {
    return db.insert(tableName, conflictStrategy, values)
  }
}
