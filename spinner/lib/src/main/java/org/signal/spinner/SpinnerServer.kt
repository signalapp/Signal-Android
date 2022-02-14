package org.signal.spinner

import android.content.Context
import android.database.Cursor
import android.util.Base64
import androidx.sqlite.db.SupportSQLiteDatabase
import com.github.jknack.handlebars.Handlebars
import com.github.jknack.handlebars.Template
import com.github.jknack.handlebars.helper.ConditionalHelpers
import fi.iki.elonen.NanoHTTPD
import org.signal.core.util.ExceptionUtil
import org.signal.core.util.logging.Log
import java.lang.IllegalArgumentException
import kotlin.math.max

/**
 * The workhorse of this lib. Handles all of our our web routing and response generation.
 *
 * In general, you add routes in [serve], and then build a response by creating a handlebars template (in the assets folder) and then passing in a data class
 * to [renderTemplate].
 */
internal class SpinnerServer(
  context: Context,
  private val deviceInfo: Spinner.DeviceInfo,
  private val databases: Map<String, SupportSQLiteDatabase>
) : NanoHTTPD(5000) {

  companion object {
    private val TAG = Log.tag(SpinnerServer::class.java)
  }

  private val handlebars: Handlebars = Handlebars(AssetTemplateLoader(context)).apply {
    registerHelper("eq", ConditionalHelpers.eq)
  }

  override fun serve(session: IHTTPSession): Response {
    if (session.method == Method.POST) {
      // Needed to populate session.parameters
      session.parseBody(mutableMapOf())
    }

    val dbParam: String = session.queryParam("db") ?: session.parameters["db"]?.toString() ?: databases.keys.first()
    val db: SupportSQLiteDatabase = databases[dbParam] ?: return internalError(IllegalArgumentException("Invalid db param!"))

    try {
      return when {
        session.method == Method.GET && session.uri == "/" -> getIndex(dbParam, db)
        session.method == Method.GET && session.uri == "/browse" -> getBrowse(dbParam, db)
        session.method == Method.POST && session.uri == "/browse" -> postBrowse(dbParam, db, session)
        session.method == Method.GET && session.uri == "/query" -> getQuery(dbParam, db)
        session.method == Method.POST && session.uri == "/query" -> postQuery(dbParam, db, session)
        else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_HTML, "Not found")
      }
    } catch (t: Throwable) {
      Log.e(TAG, t)
      return internalError(t)
    }
  }

  private fun getIndex(dbName: String, db: SupportSQLiteDatabase): Response {
    return renderTemplate(
      "overview",
      OverviewPageModel(
        deviceInfo = deviceInfo,
        database = dbName,
        databases = databases.keys.toList(),
        tables = db.getTables().toTableInfo(),
        indices = db.getIndexes().toIndexInfo(),
        triggers = db.getTriggers().toTriggerInfo(),
        queryResult = db.getTables().toQueryResult()
      )
    )
  }

  private fun getBrowse(dbName: String, db: SupportSQLiteDatabase): Response {
    return renderTemplate(
      "browse",
      BrowsePageModel(
        deviceInfo = deviceInfo,
        database = dbName,
        databases = databases.keys.toList(),
        tableNames = db.getTableNames()
      )
    )
  }

  private fun postBrowse(dbName: String, db: SupportSQLiteDatabase, session: IHTTPSession): Response {
    val table: String = session.parameters["table"]?.get(0).toString()
    val query = "select * from $table"

    return renderTemplate(
      "browse",
      BrowsePageModel(
        deviceInfo = deviceInfo,
        database = dbName,
        databases = databases.keys.toList(),
        tableNames = db.getTableNames(),
        table = table,
        queryResult = db.query(query).toQueryResult()
      )
    )
  }

  private fun getQuery(dbName: String, db: SupportSQLiteDatabase): Response {
    return renderTemplate(
      "query",
      QueryPageModel(
        deviceInfo = deviceInfo,
        database = dbName,
        databases = databases.keys.toList(),
        query = ""
      )
    )
  }

  private fun postQuery(dbName: String, db: SupportSQLiteDatabase, session: IHTTPSession): Response {
    val action: String = session.parameters["action"]?.get(0).toString()
    val rawQuery: String = session.parameters["query"]?.get(0).toString()
    val query = if (action == "analyze") "EXPLAIN QUERY PLAN $rawQuery" else rawQuery
    val startTime = System.currentTimeMillis()

    return renderTemplate(
      "query",
      QueryPageModel(
        deviceInfo = deviceInfo,
        database = dbName,
        databases = databases.keys.toList(),
        query = rawQuery,
        queryResult = db.query(query).toQueryResult(startTime)
      )
    )
  }

  private fun internalError(throwable: Throwable): Response {
    val stackTrace = ExceptionUtil.convertThrowableToString(throwable)
      .split("\n")
      .map { it.trim() }
      .mapIndexed { index, s -> if (index == 0) s else "&nbsp;&nbsp;$s" }
      .joinToString("<br />")

    return renderTemplate("error", stackTrace)
  }

  private fun renderTemplate(assetName: String, model: Any): Response {
    val template: Template = handlebars.compile(assetName)
    val output: String = template.apply(model)
    return newFixedLengthResponse(output)
  }

  private fun Cursor.toQueryResult(queryStartTime: Long = 0): QueryResult {
    val numColumns = this.columnCount

    val columns = mutableListOf<String>()
    for (i in 0 until numColumns) {
      columns += getColumnName(i)
    }

    var timeOfFirstRow = 0L
    val rows = mutableListOf<List<String>>()
    while (moveToNext()) {
      if (timeOfFirstRow == 0L) {
        timeOfFirstRow = System.currentTimeMillis()
      }

      val row = mutableListOf<String>()
      for (i in 0 until numColumns) {
        val data: String? = when (getType(i)) {
          Cursor.FIELD_TYPE_BLOB -> Base64.encodeToString(getBlob(i), 0)
          else -> getString(i)
        }
        row += data ?: "null"
      }
      rows += row
    }

    if (timeOfFirstRow == 0L) {
      timeOfFirstRow = System.currentTimeMillis()
    }

    return QueryResult(
      columns = columns,
      rows = rows,
      timeToFirstRow = max(timeOfFirstRow - queryStartTime, 0),
      timeToReadRows = max(System.currentTimeMillis() - timeOfFirstRow, 0)
    )
  }

  private fun Cursor.toTableInfo(): List<TableInfo> {
    val tables = mutableListOf<TableInfo>()

    while (moveToNext()) {
      val name = getString(getColumnIndexOrThrow("name"))
      tables += TableInfo(
        name = name ?: "null",
        sql = getString(getColumnIndexOrThrow("sql"))?.formatAsSqlCreationStatement(name) ?: "null"
      )
    }

    return tables
  }

  private fun Cursor.toIndexInfo(): List<IndexInfo> {
    val indices = mutableListOf<IndexInfo>()

    while (moveToNext()) {
      indices += IndexInfo(
        name = getString(getColumnIndexOrThrow("name")) ?: "null",
        sql = getString(getColumnIndexOrThrow("sql")) ?: "null"
      )
    }

    return indices
  }

  private fun Cursor.toTriggerInfo(): List<TriggerInfo> {
    val indices = mutableListOf<TriggerInfo>()

    while (moveToNext()) {
      indices += TriggerInfo(
        name = getString(getColumnIndexOrThrow("name")) ?: "null",
        sql = getString(getColumnIndexOrThrow("sql")) ?: "null"
      )
    }

    return indices
  }

  /** Takes a SQL table creation statement and formats it using HTML */
  private fun String.formatAsSqlCreationStatement(name: String): String {
    val fields = substring(indexOf("(") + 1, this.length - 1).split(",")
    val fieldStrings = fields.map { s -> "&nbsp;&nbsp;${s.trim()},<br>" }.toMutableList()

    if (fieldStrings.isNotEmpty()) {
      fieldStrings[fieldStrings.lastIndex] = "&nbsp;&nbsp;${fields.last().trim()}<br>"
    }

    return "CREATE TABLE $name (<br/>" +
      fieldStrings.joinToString("") +
      ")"
  }

  private fun IHTTPSession.queryParam(name: String): String? {
    if (queryParameterString == null) {
      return null
    }

    val params: Map<String, String> = queryParameterString
      .split("&")
      .mapNotNull { part ->
        val parts = part.split("=")
        if (parts.size == 2) {
          parts[0] to parts[1]
        } else {
          null
        }
      }
      .toMap()

    return params[name]
  }

  data class OverviewPageModel(
    val deviceInfo: Spinner.DeviceInfo,
    val database: String,
    val databases: List<String>,
    val tables: List<TableInfo>,
    val indices: List<IndexInfo>,
    val triggers: List<TriggerInfo>,
    val queryResult: QueryResult? = null
  )

  data class BrowsePageModel(
    val deviceInfo: Spinner.DeviceInfo,
    val database: String,
    val databases: List<String>,
    val tableNames: List<String>,
    val table: String? = null,
    val queryResult: QueryResult? = null
  )

  data class QueryPageModel(
    val deviceInfo: Spinner.DeviceInfo,
    val database: String,
    val databases: List<String>,
    val query: String = "",
    val queryResult: QueryResult? = null
  )

  data class QueryResult(
    val columns: List<String>,
    val rows: List<List<String>>,
    val rowCount: Int = rows.size,
    val timeToFirstRow: Long,
    val timeToReadRows: Long,
  )

  data class TableInfo(
    val name: String,
    val sql: String
  )

  data class IndexInfo(
    val name: String,
    val sql: String
  )

  data class TriggerInfo(
    val name: String,
    val sql: String
  )
}
