package org.signal.spinner

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase

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

fun SupportSQLiteDatabase.getTableRowCount(table: String): Int {
  return this.query("SELECT COUNT(*) FROM $table").use {
    if (it.moveToFirst()) {
      it.getInt(0)
    } else {
      0
    }
  }
}
