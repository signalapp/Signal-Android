package org.signal.core.util

import androidx.sqlite.db.SupportSQLiteDatabase

fun SupportSQLiteDatabase.getTableRowCount(table: String): Int {
  return this.query("SELECT COUNT(*) FROM $table").use {
    if (it.moveToFirst()) {
      it.getInt(0)
    } else {
      0
    }
  }
}