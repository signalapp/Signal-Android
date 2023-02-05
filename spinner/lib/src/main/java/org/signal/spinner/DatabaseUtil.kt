package org.signal.spinner

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import org.signal.core.util.SqlUtil
import org.signal.core.util.readToList
import org.signal.core.util.requireNonNullString
import org.signal.core.util.requireString

fun SupportSQLiteDatabase.getTableNames(): List<String> {
  val out = mutableListOf<String>()
  this.query("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name ASC").use { cursor ->
    while (cursor.moveToNext()) {
      out += cursor.getString(0)
    }
  }

  return out
}

fun SupportSQLiteDatabase.getTables(): Cursor {
  return this.query("SELECT * FROM sqlite_master WHERE type='table' ORDER BY name ASC")
}

fun SupportSQLiteDatabase.getIndexes(): Cursor {
  return this.query("SELECT * FROM sqlite_master WHERE type='index' ORDER BY name ASC")
}

fun SupportSQLiteDatabase.getTriggers(): Cursor {
  return this.query("SELECT * FROM sqlite_master WHERE type='trigger' ORDER BY name ASC")
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

fun SupportSQLiteDatabase.getTableRowCount(table: String): Int {
  return this.query("SELECT COUNT(*) FROM $table").use {
    if (it.moveToFirst()) {
      it.getInt(0)
    } else {
      0
    }
  }
}

data class ForeignKeyConstraint(
  val table: String,
  val column: String,
  val dependsOnTable: String,
  val dependsOnColumn: String,
  val onDelete: String
)
