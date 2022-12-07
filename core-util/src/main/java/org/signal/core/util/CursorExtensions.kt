package org.signal.core.util

import android.database.Cursor
import java.util.Optional

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

fun Cursor.optionalLong(column: String): Optional<Long> {
  return CursorUtil.getLong(this, column)
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

fun <T> Cursor.requireObject(column: String, serializer: LongSerializer<T>): T {
  return serializer.deserialize(CursorUtil.requireLong(this, column))
}

fun <T> Cursor.requireObject(column: String, serializer: StringSerializer<T>): T {
  return serializer.deserialize(CursorUtil.requireString(this, column))
}

@JvmOverloads
fun Cursor.readToSingleLong(defaultValue: Long = 0): Long {
  return use {
    if (it.moveToFirst()) {
      it.getLong(0)
    } else {
      defaultValue
    }
  }
}

@JvmOverloads
inline fun <T> Cursor.readToList(predicate: (T) -> Boolean = { true }, mapper: (Cursor) -> T): List<T> {
  val list = mutableListOf<T>()
  use {
    while (moveToNext()) {
      val record = mapper(this)
      if (predicate(record)) {
        list += mapper(this)
      }
    }
  }
  return list
}

inline fun <T> Cursor.readToSet(predicate: (T) -> Boolean = { true }, mapper: (Cursor) -> T): Set<T> {
  val set = mutableSetOf<T>()
  use {
    while (moveToNext()) {
      val record = mapper(this)
      if (predicate(record)) {
        set += mapper(this)
      }
    }
  }
  return set
}

inline fun <T> Cursor.firstOrNull(predicate: (T) -> Boolean = { true }, mapper: (Cursor) -> T): T? {
  use {
    while (moveToNext()) {
      val record = mapper(this)
      if (predicate(record)) {
        return record
      }
    }
  }
  return null
}

fun Boolean.toInt(): Int = if (this) 1 else 0
