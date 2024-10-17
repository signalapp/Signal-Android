package org.signal.core.util

import android.database.Cursor
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
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

fun Cursor.requireLongOrNull(column: String): Long? {
  return this.getLongOrNull(this.getColumnIndexOrThrow(column))
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

fun <T> Cursor.requireObject(column: String, serializer: IntSerializer<T>): T {
  return serializer.deserialize(CursorUtil.requireInt(this, column))
}

@JvmOverloads
fun Cursor.readToSingleLong(defaultValue: Long = 0): Long {
  return readToSingleLongOrNull() ?: defaultValue
}

fun Cursor.readToSingleLongOrNull(): Long? {
  return use {
    if (it.moveToFirst()) {
      it.getLongOrNull(0)
    } else {
      null
    }
  }
}

fun <T> Cursor.readToSingleObject(serializer: BaseSerializer<T, Cursor, *>): T? {
  return use {
    if (it.moveToFirst()) {
      serializer.deserialize(it)
    } else {
      null
    }
  }
}

fun <T> Cursor.readToSingleObject(mapper: (Cursor) -> T): T? {
  return use {
    if (it.moveToFirst()) {
      mapper(it)
    } else {
      null
    }
  }
}

@JvmOverloads
fun Cursor.readToSingleInt(defaultValue: Int = 0): Int {
  return use {
    if (it.moveToFirst()) {
      it.getInt(0)
    } else {
      defaultValue
    }
  }
}

fun Cursor.readToSingleBoolean(defaultValue: Boolean = false): Boolean {
  return use {
    if (it.moveToFirst()) {
      it.getInt(0) != 0
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

@JvmOverloads
inline fun <K, V> Cursor.readToMap(predicate: (Pair<K, V>) -> Boolean = { true }, mapper: (Cursor) -> Pair<K, V>): Map<K, V> {
  return readToList(predicate, mapper).associate { it }
}

/**
 * Groups the cursor by the given key, and returns a map of keys to lists of values.
 */
inline fun <K, V> Cursor.groupBy(mapper: (Cursor) -> Pair<K, V>): Map<K, List<V>> {
  val map: MutableMap<K, MutableList<V>> = mutableMapOf()

  use {
    while (moveToNext()) {
      val pair = mapper(this)
      val list = map.getOrPut(pair.first) { mutableListOf() }
      list += pair.second
    }
  }

  return map
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

inline fun Cursor.forEach(operation: (Cursor) -> Unit) {
  use {
    while (moveToNext()) {
      operation(this)
    }
  }
}

inline fun Cursor.forEachIndexed(operation: (Int, Cursor) -> Unit) {
  use {
    var i = 0
    while (moveToNext()) {
      operation(i++, this)
    }
  }
}

fun Cursor.iterable(): Iterable<Cursor> {
  return CursorIterable(this)
}

fun Boolean.toInt(): Int = if (this) 1 else 0

/**
 * Renders the entire cursor row as a string.
 * Not necessarily used in the app, but very useful to have available when debugging.
 */
fun Cursor.rowToString(): String {
  val builder = StringBuilder()
  for (i in 0 until this.columnCount) {
    builder
      .append(this.getColumnName(i))
      .append("=")
      .append(this.getStringOrNull(i))

    if (i < this.columnCount - 1) {
      builder.append(", ")
    }
  }

  return builder.toString()
}

private class CursorIterable(private val cursor: Cursor) : Iterable<Cursor> {
  override fun iterator(): Iterator<Cursor> {
    return CursorIterator(cursor)
  }
}

private class CursorIterator(private val cursor: Cursor) : Iterator<Cursor> {
  override fun hasNext(): Boolean {
    return !cursor.isClosed && cursor.count > 0 && !cursor.isLast && !cursor.isAfterLast
  }

  override fun next(): Cursor {
    return if (cursor.moveToNext()) {
      cursor
    } else {
      throw NoSuchElementException()
    }
  }
}
