package org.signal.core.util

import android.content.ContentValues
import android.text.TextUtils
import androidx.annotation.VisibleForTesting
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.LinkedList
import java.util.Locale
import java.util.stream.Collectors

object SqlUtil {
  /** The maximum number of arguments (i.e. question marks) allowed in a SQL statement.  */
  private const val MAX_QUERY_ARGS = 999

  @JvmField
  val COUNT = arrayOf("COUNT(*)")

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

  /**
   * Given a table, this will return a set of tables that it has a foreign key dependency on.
   */
  @JvmStatic
  fun getForeignKeyDependencies(db: SupportSQLiteDatabase, table: String): Set<String> {
    return db.query("PRAGMA foreign_key_list($table)")
      .readToSet{ cursor ->
        cursor.requireNonNullString("table")
      }
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
        is DatabaseId -> it.serialize()
        else -> it.toString()
      }
    }.toTypedArray()
  }

  @JvmStatic
  fun buildArgs(argument: Long): Array<String> {
    return arrayOf(argument.toString())
  }

  /**
   * Builds a case-insensitive GLOB pattern for fuzzy text queries. Works with all unicode
   * characters.
   *
   * Ex:
   * cat -> [cC][aA][tT]
   */
  @JvmStatic
  fun buildCaseInsensitiveGlobPattern(query: String): String {
    if (TextUtils.isEmpty(query)) {
      return "*"
    }

    val pattern = StringBuilder()
    var i = 0
    val len = query.codePointCount(0, query.length)
    while (i < len) {
      val point = StringUtil.codePointToString(query.codePointAt(i))
      pattern.append("[")
      pattern.append(point.lowercase(Locale.getDefault()))
      pattern.append(point.uppercase(Locale.getDefault()))
      pattern.append(getAccentuatedCharRegex(point.lowercase(Locale.getDefault())))
      pattern.append("]")
      i++
    }

    return "*$pattern*"
  }

  private fun getAccentuatedCharRegex(query: String): String {
    return when (query) {
      "a" -> "À-Åà-åĀ-ąǍǎǞ-ǡǺ-ǻȀ-ȃȦȧȺɐ-ɒḀḁẚẠ-ặ"
      "b" -> "ßƀ-ƅɃɓḂ-ḇ"
      "c" -> "çÇĆ-čƆ-ƈȻȼɔḈḉ"
      "d" -> "ÐðĎ-đƉ-ƍȡɖɗḊ-ḓ"
      "e" -> "È-Ëè-ëĒ-ěƎ-ƐǝȄ-ȇȨȩɆɇɘ-ɞḔ-ḝẸ-ệ"
      "f" -> "ƑƒḞḟ"
      "g" -> "Ĝ-ģƓǤ-ǧǴǵḠḡ"
      "h" -> "Ĥ-ħƕǶȞȟḢ-ḫẖ"
      "i" -> "Ì-Ïì-ïĨ-ıƖƗǏǐȈ-ȋɨɪḬ-ḯỈ-ị"
      "j" -> "ĴĵǰȷɈɉɟ"
      "k" -> "Ķ-ĸƘƙǨǩḰ-ḵ"
      "l" -> "Ĺ-łƚȴȽɫ-ɭḶ-ḽ"
      "m" -> "Ɯɯ-ɱḾ-ṃ"
      "n" -> "ÑñŃ-ŋƝƞǸǹȠȵɲ-ɴṄ-ṋ"
      "o" -> "Ò-ÖØò-öøŌ-őƟ-ơǑǒǪ-ǭǾǿȌ-ȏȪ-ȱṌ-ṓỌ-ợ"
      "p" -> "ƤƥṔ-ṗ"
      "q" -> ""
      "r" -> "Ŕ-řƦȐ-ȓɌɍṘ-ṟ"
      "s" -> "Ś-šƧƨȘșȿṠ-ṩ"
      "t" -> "Ţ-ŧƫ-ƮȚțȾṪ-ṱẗ"
      "u" -> "Ù-Üù-üŨ-ųƯ-ƱǓ-ǜȔ-ȗɄṲ-ṻỤ-ự"
      "v" -> "ƲɅṼ-ṿ"
      "w" -> "ŴŵẀ-ẉẘ"
      "x" -> "Ẋ-ẍ"
      "y" -> "ÝýÿŶ-ŸƔƳƴȲȳɎɏẎẏỲ-ỹỾỿẙ"
      "z" -> "Ź-žƵƶɀẐ-ẕ"
      "α" -> "\u0386\u0391\u03AC\u03B1\u1F00-\u1F0F\u1F70\u1F71\u1F80-\u1F8F\u1FB0-\u1FB4\u1FB6-\u1FBC"
      "ε" -> "\u0388\u0395\u03AD\u03B5\u1F10-\u1F15\u1F18-\u1F1D\u1F72\u1F73\u1FC8\u1FC9"
      "η" -> "\u0389\u0397\u03AE\u03B7\u1F20-\u1F2F\u1F74\u1F75\u1F90-\u1F9F\u1F20-\u1F2F\u1F74\u1F75\u1F90-\u1F9F\u1fc2\u1fc3\u1fc4\u1fc6\u1FC7\u1FCA\u1FCB\u1FCC"
      "ι" -> "\u038A\u0390\u0399\u03AA\u03AF\u03B9\u03CA\u1F30-\u1F3F\u1F76\u1F77\u1FD0-\u1FD3\u1FD6-\u1FDB"
      "ο" -> "\u038C\u039F\u03BF\u03CC\u1F40-\u1F45\u1F48-\u1F4D\u1F78\u1F79\u1FF8\u1FF9"
      "σ" -> "\u03A3\u03C2\u03C3"
      "ς" -> "\u03A3\u03C2\u03C3"
      "υ" -> "\u038E\u03A5\u03AB\u03C5\u03CB\u03CD\u1F50-\u1F57\u1F59\u1F5B\u1F5D\u1F5F\u1F7A\u1F7B\u1FE0-\u1FE3\u1FE6-\u1FEB"
      "ω" -> "\u038F\u03A9\u03C9\u03CE\u1F60-\u1F6F\u1F7C\u1F7D\u1FA0-\u1FAF\u1FF2-\u1FF4\u1FF6\u1FF7\u1FFA-\u1FFC"
      else -> ""
    }
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
          fullArgs.add(Hex.toStringCondensed(value).uppercase(Locale.US))
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

  /**
   * A convenient way of making queries in the form: WHERE [column] IN (?, ?, ..., ?)
   * Handles breaking it
   */
  @JvmOverloads
  @JvmStatic
  fun buildCollectionQuery(column: String, values: Collection<Any?>, prefix: String = "", maxSize: Int = MAX_QUERY_ARGS): List<Query> {
    require(!values.isEmpty()) { "Must have values!" }

    return values
      .chunked(maxSize)
      .map { batch -> buildSingleCollectionQuery(column, batch, prefix) }
  }

  /**
   * A convenient way of making queries in the form: WHERE [column] IN (?, ?, ..., ?)
   *
   * Important: Should only be used if you know the number of values is < 1000. Otherwise you risk creating a SQL statement this is too large.
   * Prefer [buildCollectionQuery] when possible.
   */
  @JvmOverloads
  @JvmStatic
  fun buildSingleCollectionQuery(column: String, values: Collection<Any?>, prefix: String = ""): Query {
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
    return Query("$prefix $column IN ($query)".trim(), buildArgs(*args))
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