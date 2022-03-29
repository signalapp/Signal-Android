package org.signal.core.util

import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.ContentValues
import androidx.annotation.VisibleForTesting
import java.lang.NullPointerException
import java.lang.StringBuilder
import java.util.ArrayList
import java.util.LinkedList
import java.util.Locale
import java.util.stream.Collectors

object SqlUtil {
  /** The maximum number of arguments (i.e. question marks) allowed in a SQL statement.  */
  private const val MAX_QUERY_ARGS = 999

  @JvmStatic
  fun tableExists(db: SupportSQLiteDatabase, table: String): Boolean {
    db.query("SELECT name FROM sqlite_master WHERE type=? AND name=?", arrayOf("table", table)).use { cursor ->
      return cursor != null && cursor.moveToNext()
    }
  }

  @JvmStatic
  fun getAllTables(db: SupportSQLiteDatabase): List<String> {
    val tables: MutableList<String> = LinkedList()
    db.query("SELECT name FROM sqlite_master WHERE type=?", arrayOf("table")).use { cursor ->
      while (cursor.moveToNext()) {
        tables.add(cursor.getString(0))
      }
    }
    return tables
  }

  @JvmStatic
  fun isEmpty(db: SupportSQLiteDatabase, table: String): Boolean {
    db.query("SELECT COUNT(*) FROM $table", null).use { cursor ->
      return if (cursor.moveToFirst()) {
        cursor.getInt(0) == 0
      } else {
        true
      }
    }
  }

  @JvmStatic
  fun columnExists(db: SupportSQLiteDatabase, table: String, column: String): Boolean {
    db.query("PRAGMA table_info($table)", null).use { cursor ->
      val nameColumnIndex = cursor.getColumnIndexOrThrow("name")
      while (cursor.moveToNext()) {
        val name = cursor.getString(nameColumnIndex)
        if (name == column) {
          return true
        }
      }
    }
    return false
  }

  @JvmStatic
  fun buildArgs(vararg objects: Any?): Array<String> {
    return objects.map {
      when (it) {
        null -> throw NullPointerException("Cannot have null arg!")
        is DatabaseId -> (it as DatabaseId?)!!.serialize()
        else -> it.toString()
      }
    }.toTypedArray()
  }

  @JvmStatic
  fun buildArgs(argument: Long): Array<String> {
    return arrayOf(argument.toString())
  }

  /**
   * Returns an updated query and args pairing that will only update rows that would *actually*
   * change. In other words, if [SupportSQLiteDatabase.update]
   * returns > 0, then you know something *actually* changed.
   */
  @JvmStatic
  fun buildTrueUpdateQuery(
    selection: String,
    args: Array<String>,
    contentValues: ContentValues
  ): Query {
    val qualifier = StringBuilder()
    val valueSet = contentValues.valueSet()

    val fullArgs: MutableList<String> = ArrayList(args.size + valueSet.size)
    fullArgs.addAll(args)

    var i = 0
    for ((key, value) in valueSet) {
      if (value != null) {
        if (value is ByteArray) {
          qualifier.append("hex(").append(key).append(") != ? OR ").append(key).append(" IS NULL")
          fullArgs.add(Hex.toStringCondensed(value).toUpperCase(Locale.US))
        } else {
          qualifier.append(key).append(" != ? OR ").append(key).append(" IS NULL")
          fullArgs.add(value.toString())
        }
      } else {
        qualifier.append(key).append(" NOT NULL")
      }
      if (i != valueSet.size - 1) {
        qualifier.append(" OR ")
      }
      i++
    }

    return Query("($selection) AND ($qualifier)", fullArgs.toTypedArray())
  }

  @JvmStatic
  fun buildCollectionQuery(column: String, values: Collection<Any?>): Query {
    require(!values.isEmpty()) { "Must have values!" }

    val query = StringBuilder()
    val args = arrayOfNulls<Any>(values.size)
    var i = 0

    for (value in values) {
      query.append("?")
      args[i] = value
      if (i != values.size - 1) {
        query.append(", ")
      }
      i++
    }
    return Query("$column IN ($query)", buildArgs(*args))
  }

  @JvmStatic
  fun buildCustomCollectionQuery(query: String, argList: List<Array<String>>): List<Query> {
    return buildCustomCollectionQuery(query, argList, MAX_QUERY_ARGS)
  }

  @JvmStatic
  @VisibleForTesting
  fun buildCustomCollectionQuery(query: String, argList: List<Array<String>>, maxQueryArgs: Int): List<Query> {
    val batchSize: Int = maxQueryArgs / argList[0].size
    return ListUtil.chunk(argList, batchSize)
      .stream()
      .map { argBatch -> buildSingleCustomCollectionQuery(query, argBatch) }
      .collect(Collectors.toList())
  }

  private fun buildSingleCustomCollectionQuery(query: String, argList: List<Array<String>>): Query {
    val outputQuery = StringBuilder()
    val outputArgs: MutableList<String> = mutableListOf()

    var i = 0
    val len = argList.size

    while (i < len) {
      outputQuery.append("(").append(query).append(")")
      if (i < len - 1) {
        outputQuery.append(" OR ")
      }

      val args = argList[i]
      for (arg in args) {
        outputArgs += arg
      }

      i++
    }

    return Query(outputQuery.toString(), outputArgs.toTypedArray())
  }

  @JvmStatic
  fun buildQuery(where: String, vararg args: Any): Query {
    return Query(where, buildArgs(*args))
  }

  @JvmStatic
  fun appendArg(args: Array<String>, addition: String): Array<String> {
    return args.toMutableList().apply {
      add(addition)
    }.toTypedArray()
  }

  @JvmStatic
  fun buildBulkInsert(tableName: String, columns: Array<String>, contentValues: List<ContentValues>): List<Query> {
    return buildBulkInsert(tableName, columns, contentValues, MAX_QUERY_ARGS)
  }

  @JvmStatic
  @VisibleForTesting
  fun buildBulkInsert(tableName: String, columns: Array<String>, contentValues: List<ContentValues>, maxQueryArgs: Int): List<Query> {
    val batchSize = maxQueryArgs / columns.size

    return contentValues
      .chunked(batchSize)
      .map { batch: List<ContentValues> -> buildSingleBulkInsert(tableName, columns, batch) }
      .toList()
  }

  private fun buildSingleBulkInsert(tableName: String, columns: Array<String>, contentValues: List<ContentValues>): Query {
    val builder = StringBuilder()
    builder.append("INSERT INTO ").append(tableName).append(" (")

    for (i in columns.indices) {
      builder.append(columns[i])
      if (i < columns.size - 1) {
        builder.append(", ")
      }
    }

    builder.append(") VALUES ")

    val placeholder = StringBuilder()
    placeholder.append("(")

    for (i in columns.indices) {
      placeholder.append("?")
      if (i < columns.size - 1) {
        placeholder.append(", ")
      }
    }

    placeholder.append(")")

    var i = 0
    val len = contentValues.size
    while (i < len) {
      builder.append(placeholder)
      if (i < len - 1) {
        builder.append(", ")
      }
      i++
    }

    val query = builder.toString()
    val args: MutableList<String> = mutableListOf()

    for (values in contentValues) {
      for (column in columns) {
        val value = values[column]
        args += if (value != null) values[column].toString() else "null"
      }
    }

    return Query(query, args.toTypedArray())
  }

  class Query(val where: String, val whereArgs: Array<String>)
}