package org.thoughtcrime.securesms.util

import androidx.annotation.VisibleForTesting
import com.annimon.stream.Collectors
import com.annimon.stream.Stream

object SqlUtil {
  /** The maximum number of arguments (i.e. question marks) allowed in a SQL statement.  */
  private const val MAX_QUERY_ARGS = 999

  @JvmStatic
  fun buildArgs(vararg objects: Any?): Array<String> {
    return objects.map {
      when (it) {
        null -> throw NullPointerException("Cannot have null arg!")
        else -> it.toString()
      }
    }.toTypedArray()
  }

  @JvmStatic
  fun buildArgs(argument: Long): Array<String> {
    return arrayOf(argument.toString())
  }

  /**
   * A convenient way of making queries in the form: WHERE [column] IN (?, ?, ..., ?)
   * Handles breaking it 
   */
  @JvmStatic
  fun buildCollectionQuery(column: String, values: Collection<Any?>): List<Query> {
    return buildCollectionQuery(column, values, MAX_QUERY_ARGS)
  }

  @VisibleForTesting
  @JvmStatic
  fun buildCollectionQuery(column: String, values: Collection<Any?>, maxSize: Int): List<Query> {
    require(!values.isEmpty()) { "Must have values!" }

    return values
      .chunked(maxSize)
      .map { batch -> buildSingleCollectionQuery(column, batch) }
  }

  /**
   * A convenient way of making queries in the form: WHERE [column] IN (?, ?, ..., ?)
   *
   * Important: Should only be used if you know the number of values is < 1000. Otherwise you risk creating a SQL statement this is too large.
   * Prefer [buildCollectionQuery] when possible.
   */
  @JvmStatic
  fun buildSingleCollectionQuery(column: String, values: Collection<Any?>): Query {
    require(!values.isEmpty()) { "Must have values!" }

    val query = StringBuilder()
    val args = arrayOfNulls<Any>(values.size)

    for ((i, value) in values.withIndex()) {
      query.append("?")
      args[i] = value
      if (i != values.size - 1) {
        query.append(", ")
      }
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
    return Stream.of(ListUtil.chunk(argList, batchSize))
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

  class Query(val where: String, val whereArgs: Array<String>)
}