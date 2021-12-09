package org.thoughtcrime.securesms.database

import android.database.Cursor
import org.thoughtcrime.securesms.util.CursorUtil
import org.whispersystems.libsignal.util.guava.Optional

fun Cursor.requireString(column: String): String? {
  return CursorUtil.requireString(this, column)
}

fun Cursor.requireNonNullString(column: String): String {
  return CursorUtil.requireString(this, column)!!
}

fun Cursor.optionalString(column: String): Optional<String> {
  return CursorUtil.getString(this, column)
}

fun Cursor.requireInt(column: String): Int {
  return CursorUtil.requireInt(this, column)
}

fun Cursor.optionalInt(column: String): Optional<Int> {
  return CursorUtil.getInt(this, column)
}

fun Cursor.requireFloat(column: String): Float {
  return CursorUtil.requireFloat(this, column)
}

fun Cursor.requireLong(column: String): Long {
  return CursorUtil.requireLong(this, column)
}

fun Cursor.requireBoolean(column: String): Boolean {
  return CursorUtil.requireInt(this, column) != 0
}

fun Cursor.optionalBoolean(column: String): Optional<Boolean> {
  return CursorUtil.getBoolean(this, column)
}

fun Cursor.requireBlob(column: String): ByteArray? {
  return CursorUtil.requireBlob(this, column)
}

fun Cursor.requireNonNullBlob(column: String): ByteArray {
  return CursorUtil.requireBlob(this, column)!!
}

fun Cursor.optionalBlob(column: String): Optional<ByteArray> {
  return CursorUtil.getBlob(this, column)
}

fun Cursor.isNull(column: String): Boolean {
  return CursorUtil.isNull(this, column)
}

fun Boolean.toInt(): Int = if (this) 1 else 0
