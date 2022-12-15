package org.signal.spinner

import android.app.Application
import android.content.ContentValues
import android.database.sqlite.SQLiteQueryBuilder
import androidx.sqlite.db.SupportSQLiteDatabase
import org.signal.core.util.logging.Log
import java.io.IOException

/**
 * A class to help initialize Spinner, our database debugging interface.
 */
object Spinner {
  internal const val KEY_PREFIX = "spinner"
  const val KEY_ENVIRONMENT = "$KEY_PREFIX:environment"

  private val TAG: String = Log.tag(Spinner::class.java)

  private lateinit var server: SpinnerServer

  fun init(application: Application, deviceInfo: Map<String, () -> String>, databases: Map<String, DatabaseConfig>, plugins: Map<String, Plugin>) {
    try {
      server = SpinnerServer(application, deviceInfo, databases, plugins)
      server.start()
    } catch (e: IOException) {
      Log.w(TAG, "Spinner server hit IO exception!", e)
    }
  }

  fun onSql(dbName: String, sql: String, args: Array<Any>?) {
    server.onSql(dbName, replaceQueryArgs(sql, args))
  }

  fun onQuery(dbName: String, distinct: Boolean, table: String, projection: Array<String>?, selection: String?, args: Array<Any>?, groupBy: String?, having: String?, orderBy: String?, limit: String?) {
    val queryString = SQLiteQueryBuilder.buildQueryString(distinct, table, projection, selection, groupBy, having, orderBy, limit)
    server.onSql(dbName, replaceQueryArgs(queryString, args))
  }

  fun onDelete(dbName: String, table: String, selection: String?, args: Array<Any>?) {
    var query = "DELETE FROM $table"
    if (selection != null) {
      query += " WHERE $selection"
      query = replaceQueryArgs(query, args)
    }

    server.onSql(dbName, query)
  }

  fun onUpdate(dbName: String, table: String, values: ContentValues, selection: String?, args: Array<Any>?) {
    val query = StringBuilder("UPDATE $table SET ")

    for (key in values.keySet()) {
      query.append("$key = ${values.get(key)}, ")
    }

    query.delete(query.length - 2, query.length)

    if (selection != null) {
      query.append(" WHERE ").append(selection)
    }

    var queryString = query.toString()

    if (args != null) {
      queryString = replaceQueryArgs(queryString, args)
    }

    server.onSql(dbName, queryString)
  }

  private fun replaceQueryArgs(query: String, args: Array<Any>?): String {
    if (args == null) {
      return query
    }

    val builder = StringBuilder()

    var i = 0
    var argIndex = 0
    while (i < query.length) {
      if (query[i] == '?' && argIndex < args.size) {
        builder.append("'").append(args[argIndex]).append("'")
        argIndex++
      } else {
        builder.append(query[i])
      }
      i++
    }

    return builder.toString()
  }

  data class DatabaseConfig(
    val db: () -> SupportSQLiteDatabase,
    val columnTransformers: List<ColumnTransformer> = emptyList()
  )
}
